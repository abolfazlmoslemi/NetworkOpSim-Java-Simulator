// ===== File: NodeBehavior.java (FINAL - Corrected BULK Passthrough Lifecycle) =====

package com.networkopsim.game.controller.logic.behaviors;

import com.networkopsim.game.controller.logic.GameEngine;
import com.networkopsim.game.model.core.Packet;
import com.networkopsim.game.model.core.System;
import com.networkopsim.game.model.enums.NetworkEnums;

public class NodeBehavior extends AbstractSystemBehavior {

    private final NetworkEnums.SystemType systemType;

    public NodeBehavior(NetworkEnums.SystemType type) {
        if (type != NetworkEnums.SystemType.NODE && type != NetworkEnums.SystemType.ANTITROJAN) {
            throw new IllegalArgumentException("NodeBehavior is only for NODE and ANTITROJAN system types.");
        }
        this.systemType = type;
    }

    @Override
    public void receivePacket(System system, Packet packet, GameEngine gameEngine, boolean isPredictionRun, boolean enteredCompatibly) {
        // The packet's currentSystem is set to this system right before this method is called.
        // This stops the packet's update() loop. We are now responsible for its fate.

        if (packet.getPacketType() == NetworkEnums.PacketType.BULK) {
            if (this.systemType == NetworkEnums.SystemType.NODE) {
                // NODE allows passthrough.
                handleBulkPassthrough(system, packet, gameEngine, isPredictionRun);
            } else {
                // ANTITROJAN is destructive.
                handleDestructiveArrival(system, packet, gameEngine, isPredictionRun);
            }
        } else {
            // Standard behavior for all other packets.
            processOrQueuePacket(system, packet, gameEngine, isPredictionRun);
        }
    }

    protected void handleBulkPassthrough(System system, Packet packet, GameEngine gameEngine, boolean isPredictionRun) {
        // 1. Destroy all packets currently in this system's queue.
        synchronized(system.packetQueue) {
            for(Packet p : system.packetQueue) {
                gameEngine.getGameState().increasePacketLoss(p);
                gameEngine.packetLostInternal(p, isPredictionRun);
            }
            system.packetQueue.clear();
        }

        // 2. The packet is now "owned" by this system. We try to find an exit.
        // The processOrQueuePacket method will either dispatch it immediately (setting its
        // currentSystem back to null and assigning a new wire) or queue it.
        // Since the packet is no longer on a wire, it won't be double-processed.
        processOrQueuePacket(system, packet, gameEngine, isPredictionRun);
    }

    protected void handleDestructiveArrival(System system, Packet packet, GameEngine gameEngine, boolean isPredictionRun) {
        synchronized(system.packetQueue) {
            for(Packet p : system.packetQueue) {
                gameEngine.getGameState().increasePacketLoss(p);
                gameEngine.packetLostInternal(p, isPredictionRun);
            }
            system.packetQueue.clear();
        }
        // Also destroy the BULK packet itself.
        gameEngine.getGameState().increasePacketLoss(packet);
        gameEngine.packetLostInternal(packet, isPredictionRun);
    }

    @Override
    public NetworkEnums.SystemType getSystemType() {
        return this.systemType;
    }
}