// ===== File: SpyBehavior.java (FINAL - Corrected Destruction Logic) =====

package com.networkopsim.game.controller.logic.behaviors;

import com.networkopsim.game.controller.logic.GameEngine;
import com.networkopsim.game.model.core.Packet;
import com.networkopsim.game.model.core.Port;
import com.networkopsim.game.model.core.System;
import com.networkopsim.game.model.core.Wire;
import com.networkopsim.game.model.enums.NetworkEnums;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class SpyBehavior extends AbstractSystemBehavior {

    @Override
    public void receivePacket(System system, Packet packet, GameEngine gameEngine, boolean isPredictionRun, boolean enteredCompatibly) {
        // [CORRECTED LOGIC] Spy is a "destructive" system for BULK packets.
        if (packet.getPacketType() == NetworkEnums.PacketType.BULK) {
            handleDestructiveArrival(system, packet, gameEngine, isPredictionRun);
            return;
        }

        // --- Standard Spy logic for non-BULK packets ---
        if (packet.getPacketType() == NetworkEnums.PacketType.PROTECTED) {
            packet.revertToOriginalType();
            processOrQueuePacket(system, packet, gameEngine, isPredictionRun);
            return;
        }
        if (packet.getPacketType() == NetworkEnums.PacketType.SECRET) {
            gameEngine.packetLostInternal(packet, isPredictionRun);
            return;
        }
        List<System> otherSpySystems = gameEngine.getSystems().stream()
                .filter(s -> s != null && s.getSystemType() == NetworkEnums.SystemType.SPY && s.getId() != system.getId())
                .collect(Collectors.toList());
        if (otherSpySystems.isEmpty()) {
            processOrQueuePacket(system, packet, gameEngine, isPredictionRun);
            return;
        }
        Collections.shuffle(otherSpySystems, System.getGlobalRandom());
        Port targetPort = null;
        Wire targetWire = null;
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
            packet.teleportToWire(targetWire);
        } else {
            processOrQueuePacket(system, packet, gameEngine, isPredictionRun);
        }
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
        return NetworkEnums.SystemType.SPY;
    }
}