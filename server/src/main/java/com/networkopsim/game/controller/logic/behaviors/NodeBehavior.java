// ===== File: NodeBehavior.java (FINAL - Corrected loss counting) =====

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
        if (packet.isVolumetric()) {
            if (this.systemType == NetworkEnums.SystemType.NODE) {
                handleBulkPassthrough(system, packet, gameEngine, isPredictionRun);
            } else { // ANTITROJAN is destructive.
                handleDestructiveArrival(system, packet, gameEngine, isPredictionRun);
            }
        } else {
            processOrQueuePacket(system, packet, gameEngine, isPredictionRun);
        }
    }

    protected void handleBulkPassthrough(System system, Packet packet, GameEngine gameEngine, boolean isPredictionRun) {
        synchronized(system.packetQueue) {
            for(Packet p : system.packetQueue) {
                // [MODIFIED] Loss is handled by packetLostInternal
                gameEngine.packetLostInternal(p, isPredictionRun);
            }
            system.packetQueue.clear();
        }
        processOrQueuePacket(system, packet, gameEngine, isPredictionRun);
    }

    protected void handleDestructiveArrival(System system, Packet packet, GameEngine gameEngine, boolean isPredictionRun) {
        synchronized(system.packetQueue) {
            for(Packet p : system.packetQueue) {
                // [MODIFIED] Loss is handled by packetLostInternal
                gameEngine.packetLostInternal(p, isPredictionRun);
            }
            system.packetQueue.clear();
        }
        // [MODIFIED] Removed direct call to increasePacketLoss.
        // packetLostInternal will handle the loss counting correctly.
        gameEngine.packetLostInternal(packet, isPredictionRun);
    }

    @Override
    public NetworkEnums.SystemType getSystemType() {
        return this.systemType;
    }
}