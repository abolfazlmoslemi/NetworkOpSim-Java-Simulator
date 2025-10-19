// FILE: /NetworkOpSim-Multiplayer/NetworkOpSim-Server/src/main/java/com/networkopsim/server/game/logic/behaviors/MergerBehavior.java
// ================================================================================

package com.networkopsim.core.game.logic.behaviors;

import com.networkopsim.core.game.logic.GameEngine;
import com.networkopsim.core.game.model.core.Packet;
import com.networkopsim.core.game.model.core.System;
import com.networkopsim.shared.model.NetworkEnums;

import java.util.ArrayList;
import java.util.List;

public class MergerBehavior extends AbstractSystemBehavior {

    @Override
    public void receivePacket(System system, Packet packet, GameEngine gameEngine, boolean isPredictionRun, boolean enteredCompatibly) {
        if (packet.isVolumetric()) {
            handleBulkPassthrough(system, packet, gameEngine, isPredictionRun);
            return;
        }

        if (packet.getPacketType() == NetworkEnums.PacketType.MESSENGER && packet.getBulkParentId() != -1) {
            int parentId = packet.getBulkParentId();

            system.getMergingPackets().computeIfAbsent(parentId, k -> new ArrayList<>()).add(packet);
            gameEngine.resolveBulkPart(parentId, packet.getId());
            gameEngine.packetLostInternal(packet, isPredictionRun); // The part is consumed here

            if (gameEngine.areAllPartsResolved(parentId)) {
                List<Packet> collectedParts = system.getMergingPackets().get(parentId);
                if (collectedParts != null && !collectedParts.isEmpty()) {
                    resolveMerge(system, gameEngine, isPredictionRun, parentId, collectedParts);
                } else {
                    if (!isPredictionRun) {
                        gameEngine.getGameState().resolveBulkPacket(parentId, 0);
                    }
                }
            }
        } else {
            processOrQueuePacket(system, packet, gameEngine, isPredictionRun);
        }
    }

    private void resolveMerge(System system, GameEngine gameEngine, boolean isPredictionRun, int parentId, List<Packet> collectedParts) {
        if (!isPredictionRun) {
            gameEngine.getGameState().resolveBulkPacket(parentId, collectedParts.size());
        }

        NetworkEnums.PacketType originalType = gameEngine.getGameState().getOriginalBulkPacketType(parentId);
        Packet newVolumetricPacket = new Packet(NetworkEnums.PacketShape.CIRCLE, system.getX(), system.getY(), originalType);
        newVolumetricPacket.setSize(collectedParts.size());

        queuePacket(system, newVolumetricPacket, gameEngine, isPredictionRun);

        system.getMergingPackets().remove(parentId);
    }

    @Override
    public void processQueue(System system, GameEngine gameEngine, boolean isPredictionRun) {
        super.processQueue(system, gameEngine, isPredictionRun);
    }

    protected void handleBulkPassthrough(System system, Packet packet, GameEngine gameEngine, boolean isPredictionRun) {
        synchronized(system.packetQueue) {
            for(Packet p : system.packetQueue) {
                gameEngine.getGameState().increasePacketLoss(p);
                gameEngine.packetLostInternal(p, isPredictionRun);
            }
            system.packetQueue.clear();
        }
        processOrQueuePacket(system, packet, gameEngine, isPredictionRun);
    }

    @Override
    public NetworkEnums.SystemType getSystemType() {
        return NetworkEnums.SystemType.MERGER;
    }
}