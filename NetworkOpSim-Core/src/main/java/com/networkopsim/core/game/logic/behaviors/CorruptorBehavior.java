// FILE: /NetworkOpSim-Multiplayer/NetworkOpSim-Server/src/main/java/com/networkopsim/server/game/logic/behaviors/CorruptorBehavior.java
// ================================================================================

package com.networkopsim.core.game.logic.behaviors;

import com.networkopsim.core.game.logic.GameEngine;
import com.networkopsim.core.game.model.core.Packet;
import com.networkopsim.core.game.model.core.Port;
import com.networkopsim.core.game.model.core.Wire;
import com.networkopsim.core.game.model.core.System;
import com.networkopsim.shared.model.NetworkEnums;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CorruptorBehavior extends AbstractSystemBehavior {

    private static final double TROJAN_CONVERSION_CHANCE = 0.15;
    private static final double CORRUPTOR_NOISE_ADDITION = 1.0;

    @Override
    public void receivePacket(System system, Packet packet, GameEngine gameEngine, boolean isPredictionRun, boolean enteredCompatibly) {
        if (packet.isVolumetric()) {
            handleDestructiveArrival(system, packet, gameEngine, isPredictionRun);
            return;
        }

        if (packet.getPacketType() == NetworkEnums.PacketType.PROTECTED) {
            packet.revertToOriginalType();
            processOrQueuePacket(system, packet, gameEngine, isPredictionRun);
            return;
        }

        if (packet.getNoise() < 0.01) {
            packet.addNoise(CORRUPTOR_NOISE_ADDITION);
        }

        // --- MODIFIED LOGIC FOR TROJAN CONVERSION ---
        if (packet.getPacketType() != NetworkEnums.PacketType.TROJAN && System.getGlobalRandom().nextDouble() < TROJAN_CONVERSION_CHANCE) {
            int currentOwner = packet.getOwnerId();
            // Only convert packets that belong to a player (owner 1 or 2)
            if (currentOwner == 1 || currentOwner == 2) {
                packet.setOriginalOwnerId(currentOwner); // Store original owner
                int newOwner = (currentOwner == 1) ? 2 : 1;
                packet.setOwnerId(newOwner); // Swap owner
                packet.setConvertedTrojan(true); // Mark as a converted trojan
            }
            packet.setPacketType(NetworkEnums.PacketType.TROJAN);
        }
        // --- END OF MODIFIED LOGIC ---

        Port outputPort = findIncompatibleOutputPort(system, packet, gameEngine, isPredictionRun);
        if (outputPort == null) {
            outputPort = findAvailableOutputPort(system, packet, gameEngine, isPredictionRun);
        }

        if (outputPort != null) {
            Wire outputWire = gameEngine.findWireFromPort(outputPort);
            if (outputWire != null) {
                dispatchPacket(packet, outputWire, outputPort);
            } else {
                queuePacket(system, packet, gameEngine, isPredictionRun);
            }
        } else {
            queuePacket(system, packet, gameEngine, isPredictionRun);
        }
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

    private Port findIncompatibleOutputPort(System system, Packet packet, GameEngine gameEngine, boolean isPredictionRun) {
        List<Port> incompatibleEmptyPorts = new ArrayList<>();
        NetworkEnums.PortShape requiredPacketShape = Port.getShapeEnum(packet.getShape());
        List<Port> shuffledPorts;
        synchronized (system.getOutputPorts()) {
            shuffledPorts = new ArrayList<>(system.getOutputPorts());
        }
        Collections.shuffle(shuffledPorts, System.getGlobalRandom());
        for (Port port : shuffledPorts) {
            if (port != null && port.isConnected()) {
                Wire wire = gameEngine.findWireFromPort(port);
                if (wire != null && !gameEngine.isWireOccupied(wire, isPredictionRun) && port.getShape() != requiredPacketShape) {
                    System destinationSystem = wire.getEndPort().getParentSystem();
                    if (destinationSystem != null && !destinationSystem.isDisabled()) {
                        incompatibleEmptyPorts.add(port);
                    }
                }
            }
        }
        return incompatibleEmptyPorts.isEmpty() ? null : incompatibleEmptyPorts.get(0);
    }

    @Override
    public NetworkEnums.SystemType getSystemType() {
        return NetworkEnums.SystemType.CORRUPTOR;
    }
}