// ===== File: VpnBehavior.java (Final Corrected to pass VPN ID) =====

package com.networkopsim.game.controller.logic.behaviors;

import com.networkopsim.game.controller.logic.GameEngine;
import com.networkopsim.game.model.core.Packet;
import com.networkopsim.game.model.core.System;
import com.networkopsim.game.model.enums.NetworkEnums;
import com.networkopsim.game.controller.logic.SystemBehavior;
/**
 * Behavior for VPN systems. Protects MESSENGER packets and upgrades SECRET packets if the VPN is active.
 */
public class VpnBehavior extends AbstractSystemBehavior {

    @Override
    public void receivePacket(System system, Packet packet, GameEngine gameEngine, boolean isPredictionRun, boolean enteredCompatibly) {
        if (system.isVpnActive()) {
            if (packet.getPacketType() == NetworkEnums.PacketType.MESSENGER) {
                // [MODIFIED] Pass the current VPN's ID when protecting the packet.
                packet.transformToProtected(system.getId());
            } else if (packet.getPacketType() == NetworkEnums.PacketType.SECRET) {
                packet.upgradeSecretPacket();
            }
        }
        // After potential transformation, route it normally.
        processOrQueuePacket(system, packet, gameEngine, isPredictionRun);
    }

    @Override
    public NetworkEnums.SystemType getSystemType() {
        return NetworkEnums.SystemType.VPN;
    }
}