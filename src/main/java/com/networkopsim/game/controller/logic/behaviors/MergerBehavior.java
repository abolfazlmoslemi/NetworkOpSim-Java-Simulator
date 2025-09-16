// ===== File: MergerBehavior.java (FINAL - Integrates with Part Tracker) =====

package com.networkopsim.game.controller.logic.behaviors;

import com.networkopsim.game.controller.logic.GameEngine;
import com.networkopsim.game.model.core.Packet;
import com.networkopsim.game.model.core.System;
import com.networkopsim.game.model.enums.NetworkEnums;
import java.util.ArrayList;
import java.util.List;

public class MergerBehavior extends AbstractSystemBehavior {

    // Timeout logic is now removed in favor of the more accurate tracking system.

    @Override
    public void receivePacket(System system, Packet packet, GameEngine gameEngine, boolean isPredictionRun, boolean enteredCompatibly) {
        if (packet.getPacketType() == NetworkEnums.PacketType.BULK) {
            handleBulkPassthrough(system, packet, gameEngine, isPredictionRun);
            return;
        }

        if (packet.getPacketType() == NetworkEnums.PacketType.MESSENGER && packet.getBulkParentId() != -1) {
            int parentId = packet.getBulkParentId();

            // Add the part to the merging collection.
            system.getMergingPackets().computeIfAbsent(parentId, k -> new ArrayList<>()).add(packet);

            // Mark this part as "resolved" in the central tracker.
            gameEngine.resolveBulkPart(parentId, packet.getId());

            // Consume the messenger part.
            gameEngine.packetLostInternal(packet, isPredictionRun);

            // [CRITICAL] Check if ALL parts for this bulk packet are now resolved (either arrived or lost).
            if (gameEngine.areAllPartsResolved(parentId)) {
                List<Packet> collectedParts = system.getMergingPackets().get(parentId);
                if (collectedParts != null && !collectedParts.isEmpty()) {
                    resolveMerge(system, gameEngine, isPredictionRun, parentId, collectedParts);
                } else {
                    // All parts were lost and none arrived here. Resolve with 0.
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

        Packet newBulkPacket = new Packet(NetworkEnums.PacketShape.CIRCLE, system.getX(), system.getY(), NetworkEnums.PacketType.BULK);
        newBulkPacket.setSize(collectedParts.size());

        queuePacket(system, newBulkPacket, gameEngine, isPredictionRun);

        system.getMergingPackets().remove(parentId);
    }

    // The processQueue no longer needs to check for timeouts.
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