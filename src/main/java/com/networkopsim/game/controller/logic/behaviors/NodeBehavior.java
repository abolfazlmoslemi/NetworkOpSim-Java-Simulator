// ===== File: NodeBehavior.java (FINAL - Implements correct BULK Passthrough) =====

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
        // [SCENARIO 2] If a BULK packet arrives at a NODE or ANTITROJAN system.
        if (packet.getPacketType() == NetworkEnums.PacketType.BULK) {
            // 1. Destroy all packets currently in this system's queue.
            synchronized(system.packetQueue) {
                for(Packet p : system.packetQueue) {
                    // Use the main increasePacketLoss which correctly handles non-bulk packets.
                    gameEngine.getGameState().increasePacketLoss(p);
                    gameEngine.packetLostInternal(p, isPredictionRun);
                }
                system.packetQueue.clear();
            }

            // 2. Immediately try to pass the BULK packet through.
            // The BULK packet itself is NOT lost.
            processOrQueuePacket(system, packet, gameEngine, isPredictionRun);
        } else {
            // Standard behavior for all other packets.
            processOrQueuePacket(system, packet, gameEngine, isPredictionRun);
        }
    }

    @Override
    public NetworkEnums.SystemType getSystemType() {
        return this.systemType;
    }
}