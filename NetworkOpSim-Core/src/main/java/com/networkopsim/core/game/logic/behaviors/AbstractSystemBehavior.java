// FILE: /NetworkOpSim-Multiplayer/NetworkOpSim-Core/src/main/java/com/networkopsim/core/game/logic/behaviors/AbstractSystemBehavior.java
// ================================================================================

package com.networkopsim.core.game.logic.behaviors;

import com.networkopsim.core.game.logic.GameEngine;
import com.networkopsim.core.game.logic.SystemBehavior;
import com.networkopsim.core.game.model.core.Packet;
import com.networkopsim.core.game.model.core.Port;
import com.networkopsim.core.game.model.core.Wire;
import com.networkopsim.core.game.model.core.System;
import com.networkopsim.core.game.model.state.PredictedPacketStatus;
import com.networkopsim.shared.model.NetworkEnums;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class AbstractSystemBehavior implements SystemBehavior {

    @Override
    public void processQueue(System system, GameEngine gameEngine, boolean isPredictionRun) {
        if (system.isReferenceSystem() || system.isDisabled()) return;

        Packet packetToProcess;
        synchronized (system.packetQueue) {
            if (system.packetQueue.isEmpty()) return;
            packetToProcess = system.packetQueue.peek();
        }

        if (packetToProcess == null) {
            synchronized (system.packetQueue) { system.packetQueue.poll(); }
            return;
        }

        Port outputPort = findAvailableOutputPort(system, packetToProcess, gameEngine, isPredictionRun);
        if (outputPort != null) {
            Wire outputWire = gameEngine.findWireFromPort(outputPort);
            if (outputWire != null) {
                Packet sentPacket;
                synchronized (system.packetQueue) {
                    sentPacket = system.packetQueue.poll();
                }

                if (sentPacket != null) {
                    gameEngine.reintroducePacketToWorld(sentPacket);
                    dispatchPacket(sentPacket, outputWire, outputPort);
                }
            }
        }
    }

    protected void dispatchPacket(Packet packet, Wire wire, Port port) {
        boolean compatibleExit = (packet.getPacketType() == NetworkEnums.PacketType.SECRET) ||
                (packet.getPacketType() == NetworkEnums.PacketType.BULK) ||
                (packet.getPacketType() == NetworkEnums.PacketType.MESSENGER) ||
                (Port.getShapeEnum(packet.getShape()) == port.getShape());

        packet.setWire(wire, compatibleExit);
        if (packet.getFinalStatusForPrediction() == PredictedPacketStatus.QUEUED) {
            packet.setFinalStatusForPrediction(null);
        }
    }

    protected void queuePacket(System system, Packet packet, GameEngine gameEngine, boolean isPredictionRun) {
        synchronized (system.packetQueue) {
            boolean canQueue =
                    system.getSystemType() == NetworkEnums.SystemType.DISTRIBUTOR ||
                            system.packetQueue.size() < System.QUEUE_CAPACITY ||
                            (packet.getPacketType() == NetworkEnums.PacketType.BULK && system.getSystemType() == NetworkEnums.SystemType.NODE);

            if (canQueue) {
                packet.setCurrentSystem(system);
                system.packetQueue.offer(packet);
                if (packet.getFinalStatusForPrediction() != PredictedPacketStatus.LOST) {
                    packet.setFinalStatusForPrediction(PredictedPacketStatus.QUEUED);
                }
            } else {
                packet.setFinalStatusForPrediction(PredictedPacketStatus.LOST);
                gameEngine.getGameState().increasePacketLoss(packet);
                gameEngine.packetLostInternal(packet, isPredictionRun);
            }
        }
    }

    protected void processOrQueuePacket(System system, Packet packet, GameEngine gameEngine, boolean isPredictionRun) {
        Port outputPort = findAvailableOutputPort(system, packet, gameEngine, isPredictionRun);
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

    protected Port findAvailableOutputPort(System system, Packet packet, GameEngine gameEngine, boolean isPredictionRun) {
        if (packet == null || gameEngine == null) return null;
        List<Port> candidatePorts = new ArrayList<>();
        synchronized (system.getOutputPorts()) {
            for (Port port : system.getOutputPorts()) {
                if (port != null && port.isConnected()) {
                    Wire wire = gameEngine.findWireFromPort(port);

                    // <<<--- LOGIC MODIFIED TO CHECK WIRE OWNERSHIP ---<<<
                    // A packet can only use its owner's wires or neutral wires (ownerId 0).
                    if (wire != null && (wire.getOwnerId() == 0 || wire.getOwnerId() == packet.getOwnerId()) && !gameEngine.isWireOccupied(wire, isPredictionRun)) {
                        System destinationSystem = wire.getEndPort().getParentSystem();
                        if (destinationSystem != null && !destinationSystem.isDisabled()) {
                            candidatePorts.add(port);
                        }
                    }
                }
            }
        }
        if (candidatePorts.isEmpty()) return null;
        Collections.shuffle(candidatePorts, System.getGlobalRandom());
        if (packet.getPacketType() == NetworkEnums.PacketType.BULK || packet.getPacketType() == NetworkEnums.PacketType.SECRET || packet.getPacketType() == NetworkEnums.PacketType.MESSENGER) {
            return candidatePorts.get(0);
        }
        List<Port> compatiblePorts = new ArrayList<>();
        List<Port> nonCompatiblePorts = new ArrayList<>();
        NetworkEnums.PortShape requiredPacketShape = Port.getShapeEnum(packet.getShape());
        if (requiredPacketShape == null) return null;
        for (Port port : candidatePorts) {
            if (port.getShape() == requiredPacketShape) {
                compatiblePorts.add(port);
            } else {
                nonCompatiblePorts.add(port);
            }
        }
        if (!compatiblePorts.isEmpty()) return compatiblePorts.get(0);
        if (!nonCompatiblePorts.isEmpty()) return nonCompatiblePorts.get(0);
        return null;
    }

    @Override
    public void attemptPacketGeneration(System system, GameEngine gameEngine, long currentSimTimeMs, boolean isPredictionRun) {
        // Default empty implementation
    }
}