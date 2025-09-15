package com.networkopsim.game.controller.logic.behaviors;

import com.networkopsim.game.controller.logic.GameEngine;
import com.networkopsim.game.model.core.Packet;
import com.networkopsim.game.model.core.Port;
import com.networkopsim.game.model.core.System;
import com.networkopsim.game.model.core.Wire;
import com.networkopsim.game.model.enums.NetworkEnums;
import com.networkopsim.game.controller.logic.SystemBehavior;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Behavior for SPY systems. Destroys SECRET packets and teleports other packets
 * to a random available output port of another SPY system.
 */
public class SpyBehavior extends AbstractSystemBehavior {

    @Override
    public void receivePacket(System system, Packet packet, GameEngine gameEngine, boolean isPredictionRun, boolean enteredCompatibly) {
        if (packet.getPacketType() == NetworkEnums.PacketType.PROTECTED) {
            packet.revertToOriginalType();
        }

        if (packet.getPacketType() == NetworkEnums.PacketType.SECRET) {
            gameEngine.packetLostInternal(packet, isPredictionRun);
            return;
        }

        List<System> otherSpySystems = gameEngine.getSystems().stream()
                .filter(s -> s != null && s.getSystemType() == NetworkEnums.SystemType.SPY && s.getId() != system.getId())
                .collect(Collectors.toList());

        if (otherSpySystems.isEmpty()) {
            gameEngine.packetLostInternal(packet, isPredictionRun);
            return;
        }

        Collections.shuffle(otherSpySystems, System.getGlobalRandom());
        Port targetPort = null;
        Wire targetWire = null;

        // Find a valid exit from any of the other spy systems
        for (System otherSpy : otherSpySystems) {
            targetPort = findAvailableOutputPort(otherSpy, packet, gameEngine, isPredictionRun);
            if (targetPort != null) {
                targetWire = gameEngine.findWireFromPort(targetPort);
                if (targetWire != null) {
                    break;
                }
            }
        }

        if (targetWire != null) {
            // Use the special teleport method to bypass normal port exit speed calculations
            packet.teleportToWire(targetWire);
        } else {
            // If no valid exit could be found on any spy system, the packet is lost.
            gameEngine.packetLostInternal(packet, isPredictionRun);
        }
    }

    @Override
    public NetworkEnums.SystemType getSystemType() {
        return NetworkEnums.SystemType.SPY;
    }
}