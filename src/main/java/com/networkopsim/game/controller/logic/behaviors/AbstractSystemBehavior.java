// ===== File: AbstractSystemBehavior.java (Final Corrected Version 2) =====

package com.networkopsim.game.controller.logic.behaviors;

import com.networkopsim.game.controller.logic.GameEngine;
import com.networkopsim.game.controller.logic.SystemBehavior;
import com.networkopsim.game.model.core.Packet;
import com.networkopsim.game.model.core.Port;
import com.networkopsim.game.model.core.Wire;
import com.networkopsim.game.model.enums.NetworkEnums;
import com.networkopsim.game.model.state.PredictedPacketStatus;

import java.util.Collections;
import java.util.List;
import java.util.ArrayList;

/**
 * An abstract base class for SystemBehavior implementations to reduce code duplication.
 * It provides default implementations for common actions like queuing packets and finding output ports.
 */
public abstract class AbstractSystemBehavior implements SystemBehavior {

    @Override
    public void processQueue(com.networkopsim.game.model.core.System system, GameEngine gameEngine, boolean isPredictionRun) {
        if (system.isReferenceSystem() || system.isDisabled()) return;

        Packet packetToProcess;
        synchronized (system.packetQueue) {
            if (system.packetQueue.isEmpty()) return;
            packetToProcess = system.packetQueue.peek();
        }

        if (packetToProcess == null || packetToProcess.isMarkedForRemoval()) {
            if (packetToProcess != null) {
                synchronized (system.packetQueue) {
                    system.packetQueue.poll();
                }
            }
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
                    boolean compatibleExit = (sentPacket.getPacketType() == NetworkEnums.PacketType.SECRET) ||
                            (sentPacket.getPacketType() == NetworkEnums.PacketType.BULK) ||
                            (sentPacket.getPacketType() == NetworkEnums.PacketType.MESSENGER) ||
                            (Port.getShapeEnum(sentPacket.getShape()) == outputPort.getShape());
                    sentPacket.setWire(outputWire, compatibleExit);
                    if (sentPacket.getFinalStatusForPrediction() == PredictedPacketStatus.QUEUED) {
                        sentPacket.setFinalStatusForPrediction(null);
                    }
                }
            } else {
                if (packetToProcess.getFinalStatusForPrediction() != PredictedPacketStatus.LOST) {
                    packetToProcess.setFinalStatusForPrediction(PredictedPacketStatus.QUEUED);
                }
            }
        } else {
            if (packetToProcess.getFinalStatusForPrediction() != PredictedPacketStatus.LOST) {
                packetToProcess.setFinalStatusForPrediction(PredictedPacketStatus.QUEUED);
            }
        }
    }

    protected void queuePacket(com.networkopsim.game.model.core.System system, Packet packet, GameEngine gameEngine, boolean isPredictionRun) {
        synchronized (system.packetQueue) {
            if (system.packetQueue.size() < com.networkopsim.game.model.core.System.QUEUE_CAPACITY) {
                system.packetQueue.offer(packet);
                if (packet.getFinalStatusForPrediction() != PredictedPacketStatus.LOST) {
                    packet.setFinalStatusForPrediction(PredictedPacketStatus.QUEUED);
                }
            } else {
                packet.setFinalStatusForPrediction(PredictedPacketStatus.LOST);
                gameEngine.packetLostInternal(packet, isPredictionRun);
            }
        }
    }

    protected void processOrQueuePacket(com.networkopsim.game.model.core.System system, Packet packet, GameEngine gameEngine, boolean isPredictionRun) {
        Port outputPort = findAvailableOutputPort(system, packet, gameEngine, isPredictionRun);
        if (outputPort != null) {
            Wire outputWire = gameEngine.findWireFromPort(outputPort);
            if (outputWire != null) {
                boolean compatibleExit = packet.getPacketType() != NetworkEnums.PacketType.SECRET &&
                        (packet.getPacketType() == NetworkEnums.PacketType.BULK ||
                                packet.getPacketType() == NetworkEnums.PacketType.MESSENGER ||
                                (Port.getShapeEnum(packet.getShape()) == outputPort.getShape()));
                packet.setWire(outputWire, compatibleExit);
                if (packet.getFinalStatusForPrediction() == PredictedPacketStatus.QUEUED) {
                    packet.setFinalStatusForPrediction(null);
                }
            } else {
                packet.setFinalStatusForPrediction(PredictedPacketStatus.STALLED_AT_NODE);
                queuePacket(system, packet, gameEngine, isPredictionRun);
            }
        } else {
            packet.setFinalStatusForPrediction(PredictedPacketStatus.QUEUED);
            queuePacket(system, packet, gameEngine, isPredictionRun);
        }
    }

    protected Port findAvailableOutputPort(com.networkopsim.game.model.core.System system, Packet packet, GameEngine gameEngine, boolean isPredictionRun) {
        if (packet == null || gameEngine == null) return null;
        List<Port> candidatePorts = new ArrayList<>();
        synchronized (system.getOutputPorts()) {
            for (Port port : system.getOutputPorts()) {
                if (port != null && port.isConnected()) {
                    Wire wire = gameEngine.findWireFromPort(port);
                    if (wire != null && !gameEngine.isWireOccupied(wire, isPredictionRun)) {
                        com.networkopsim.game.model.core.System destinationSystem = wire.getEndPort().getParentSystem();
                        if (destinationSystem != null && !destinationSystem.isDisabled()) {
                            candidatePorts.add(port);
                        }
                    }
                }
            }
        }
        if (candidatePorts.isEmpty()) return null;
        // CORRECTED: Call the static method on your System class, not the instance
        Collections.shuffle(candidatePorts, com.networkopsim.game.model.core.System.getGlobalRandom());
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
    public void attemptPacketGeneration(com.networkopsim.game.model.core.System system, GameEngine gameEngine, long currentSimTimeMs, boolean isPredictionRun) {
        // Default empty implementation
    }
}