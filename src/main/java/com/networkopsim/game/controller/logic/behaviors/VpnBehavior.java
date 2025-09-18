// ===== File: VpnBehavior.java (FINAL - Corrected loss counting) =====

package com.networkopsim.game.controller.logic.behaviors;

import com.networkopsim.game.controller.logic.GameEngine;
import com.networkopsim.game.model.core.Packet;
import com.networkopsim.game.model.core.System;
import com.networkopsim.game.model.enums.NetworkEnums;

public class VpnBehavior extends AbstractSystemBehavior {

    @Override
    public void receivePacket(System system, Packet packet, GameEngine gameEngine, boolean isPredictionRun, boolean enteredCompatibly) {
        if (packet.isVolumetric()) {
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
        return NetworkEnums.SystemType.VPN;
    }
}