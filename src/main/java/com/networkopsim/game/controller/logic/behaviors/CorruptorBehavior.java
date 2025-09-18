// ===== File: CorruptorBehavior.java (FINAL - Corrected loss counting) =====

package com.networkopsim.game.controller.logic.behaviors;

import com.networkopsim.game.controller.logic.GameEngine;
import com.networkopsim.game.model.core.*;
import com.networkopsim.game.model.enums.NetworkEnums;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CorruptorBehavior extends AbstractSystemBehavior {

    private static final double TROJAN_CONVERSION_CHANCE = 0.15;
    private static final double CORRUPTOR_NOISE_ADDITION = 1.0;

    @Override
    public void receivePacket(com.networkopsim.game.model.core.System system, Packet packet, GameEngine gameEngine, boolean isPredictionRun, boolean enteredCompatibly) {
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
        if (com.networkopsim.game.model.core.System.getGlobalRandom().nextDouble() < TROJAN_CONVERSION_CHANCE) {
            packet.setPacketType(NetworkEnums.PacketType.TROJAN);
        }
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

    protected void handleDestructiveArrival(com.networkopsim.game.model.core.System system, Packet packet, GameEngine gameEngine, boolean isPredictionRun) {
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

    private Port findIncompatibleOutputPort(com.networkopsim.game.model.core.System system, Packet packet, GameEngine gameEngine, boolean isPredictionRun) {
        List<Port> incompatibleEmptyPorts = new ArrayList<>();
        NetworkEnums.PortShape requiredPacketShape = Port.getShapeEnum(packet.getShape());
        List<Port> shuffledPorts;
        synchronized (system.getOutputPorts()) {
            shuffledPorts = new ArrayList<>(system.getOutputPorts());
        }
        Collections.shuffle(shuffledPorts, com.networkopsim.game.model.core.System.getGlobalRandom());
        for (Port port : shuffledPorts) {
            if (port != null && port.isConnected()) {
                Wire wire = gameEngine.findWireFromPort(port);
                if (wire != null && !gameEngine.isWireOccupied(wire, isPredictionRun) && port.getShape() != requiredPacketShape) {
                    com.networkopsim.game.model.core.System destinationSystem = wire.getEndPort().getParentSystem();
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