// ===== File: DistributorBehavior.java (FINAL - Corrected unit generation counting) =====

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
        if (!packet.isVolumetric()) {
            processOrQueuePacket(system, packet, gameEngine, isPredictionRun);
            return;
        }

        GameState gameState = gameEngine.getGameState();

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
            // [MODIFIED] The call to recordBulkPartsGeneration has been removed.
            // Unit generation is now correctly handled at the source.
        }

        List<Packet> messengerParts = new ArrayList<>();
        for (int i = 0; i < numParts; i++) {
            Packet part = new Packet(NetworkEnums.PacketShape.CIRCLE, system.getX(), system.getY(), NetworkEnums.PacketType.MESSENGER);
            part.configureAsBulkPart(packet.getId(), numParts);
            messengerParts.add(part);
        }

        gameEngine.registerBulkParts(packet.getId(), messengerParts);
        gameEngine.removePacketFromWorld(packet);
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