// FILE: /NetworkOpSim-Multiplayer/NetworkOpSim-Server/src/main/java/com/networkopsim/server/game/logic/behaviors/NodeBehavior.java
// ================================================================================

package com.networkopsim.core.game.logic.behaviors;

import com.networkopsim.core.game.logic.GameEngine;
import com.networkopsim.core.game.model.core.Packet;
import com.networkopsim.core.game.model.core.System;
import com.networkopsim.shared.model.NetworkEnums;

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
                gameEngine.packetLostInternal(p, isPredictionRun);
            }
            system.packetQueue.clear();
        }
        processOrQueuePacket(system, packet, gameEngine, isPredictionRun);
    }

    protected void handleDestructiveArrival(System system, Packet packet, GameEngine gameEngine, boolean isPredictionRun) {
        synchronized(system.packetQueue) {
            for(Packet p : system.packetQueue) {
                gameEngine.packetLostInternal(p, isPredictionRun);
            }
            system.packetQueue.clear();
        }
        gameEngine.packetLostInternal(packet, isPredictionRun);
    }

    @Override
    public NetworkEnums.SystemType getSystemType() {
        return this.systemType;
    }
}