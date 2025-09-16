// ===== File: DistributorBehavior.java (FINAL - Uses Global Busy Flag) =====

package com.networkopsim.game.controller.logic.behaviors;

import com.networkopsim.game.controller.logic.GameEngine;
import com.networkopsim.game.model.core.Packet;
import com.networkopsim.game.model.core.System;
import com.networkopsim.game.model.enums.NetworkEnums;
import com.networkopsim.game.model.state.GameState;

import java.util.ArrayList;
import java.util.List;

public class DistributorBehavior extends AbstractSystemBehavior {

    @Override
    public void receivePacket(System system, Packet packet, GameEngine gameEngine, boolean isPredictionRun, boolean enteredCompatibly) {
        if (packet.getPacketType() != NetworkEnums.PacketType.BULK) {
            processOrQueuePacket(system, packet, gameEngine, isPredictionRun);
            return;
        }

        GameState gameState = gameEngine.getGameState();

        // If the distributor is already busy, the Source should have been blocked.
        // This check is a fallback; the packet should be lost if it gets here somehow.
        if (gameState.isDistributorBusy()) {
            gameEngine.getGameState().increasePacketLoss(packet);
            gameEngine.packetLostInternal(packet, isPredictionRun);
            return;
        }

        int numParts = packet.getSize();
        if (numParts <= 0) {
            gameEngine.packetLostInternal(packet, isPredictionRun);
            return;
        }

        if (!isPredictionRun) {
            gameState.registerActiveBulkPacket(packet);
            gameState.recordBulkPartsGeneration(packet.getId(), packet.getSize());
        }

        List<Packet> messengerParts = new ArrayList<>();
        for (int i = 0; i < numParts; i++) {
            Packet part = new Packet(NetworkEnums.PacketShape.CIRCLE, system.getX(), system.getY(), NetworkEnums.PacketType.MESSENGER);
            part.configureAsBulkPart(packet.getId(), numParts);
            messengerParts.add(part);
        }

        gameEngine.packetLostInternal(packet, isPredictionRun);

        // [CRITICAL] Set the global flag to TRUE, blocking all sources.
        gameState.setDistributorBusy(true);

        synchronized (system.packetQueue) {
            system.setCurrentBulkOperationId(packet.getId());
            for (Packet part : messengerParts) {
                queuePacket(system, part, gameEngine, isPredictionRun);
            }
        }
    }

    @Override
    public void processQueue(System system, GameEngine gameEngine, boolean isPredictionRun) {
        super.processQueue(system, gameEngine, isPredictionRun);

        synchronized(system.packetQueue) {
            if (system.packetQueue.isEmpty() && gameEngine.getGameState().isDistributorBusy()) {
                // [CRITICAL] The queue is empty, set the global flag to FALSE, unblocking sources.
                gameEngine.getGameState().setDistributorBusy(false);
                system.setCurrentBulkOperationId(-1);
            }
        }
    }

    @Override
    public NetworkEnums.SystemType getSystemType() {
        return NetworkEnums.SystemType.DISTRIBUTOR;
    }
}