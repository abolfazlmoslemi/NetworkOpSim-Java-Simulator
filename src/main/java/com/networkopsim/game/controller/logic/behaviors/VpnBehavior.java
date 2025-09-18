// ===== File: VpnBehavior.java (FINAL - Now protects NORMAL packets of any shape) =====

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
            // [MODIFIED] Now, both MESSENGER and NORMAL packets are transformed into PROTECTED packets.
            // This ensures Square packets also get the lock appearance and protected behavior.
            if (packet.getPacketType() == NetworkEnums.PacketType.MESSENGER || packet.getPacketType() == NetworkEnums.PacketType.NORMAL) {
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
                gameEngine.packetLostInternal(p, isPredictionRun);
            }
            system.packetQueue.clear();
        }
        gameEngine.packetLostInternal(packet, isPredictionRun);
    }

    @Override
    public NetworkEnums.SystemType getSystemType() {
        return NetworkEnums.SystemType.VPN;
    }
}