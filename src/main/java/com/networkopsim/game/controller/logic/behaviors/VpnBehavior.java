// ===== File: VpnBehavior.java (FINAL - Corrected Build Errors) =====

package com.networkopsim.game.controller.logic.behaviors;

import com.networkopsim.game.controller.logic.GameEngine; // <-- FIX: IMPORT ADDED
import com.networkopsim.game.model.core.Packet;
import com.networkopsim.game.model.core.System;
import com.networkopsim.game.model.enums.NetworkEnums;

public class VpnBehavior extends AbstractSystemBehavior {

    @Override
    public void receivePacket(System system, Packet packet, GameEngine gameEngine, boolean isPredictionRun, boolean enteredCompatibly) {
        if (packet.getPacketType() == NetworkEnums.PacketType.BULK) {
            handleDestructiveArrival(system, packet, gameEngine, isPredictionRun);
            return;
        }

        if (system.isVpnActive()) {
            if (packet.getPacketType() == NetworkEnums.PacketType.MESSENGER) {
                packet.transformToProtected(system.getId());
            } else if (packet.getPacketType() == NetworkEnums.PacketType.SECRET) {
                packet.upgradeSecretPacket();
            }
        }
        processOrQueuePacket(system, packet, gameEngine, isPredictionRun);
    }

    protected void handleDestructiveArrival(System system, Packet packet, GameEngine gameEngine, boolean isPredictionRun) { // <-- FIX: Fully qualified System
        synchronized(system.packetQueue) {
            for(Packet p : system.packetQueue) {
                gameEngine.getGameState().increasePacketLoss(p);
                gameEngine.packetLostInternal(p, isPredictionRun);
            }
            system.packetQueue.clear();
        }
        gameEngine.getGameState().increasePacketLoss(packet);
        gameEngine.packetLostInternal(packet, isPredictionRun);
    }

    @Override
    public NetworkEnums.SystemType getSystemType() {
        return NetworkEnums.SystemType.VPN;
    }
}