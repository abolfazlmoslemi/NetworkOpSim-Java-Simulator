package com.networkopsim.game.controller.logic.behaviors;

import com.networkopsim.game.controller.logic.GameEngine;
import com.networkopsim.game.controller.logic.SystemBehavior;
import com.networkopsim.game.model.core.Packet;
import com.networkopsim.game.model.core.Port;
import com.networkopsim.game.model.core.System;
import com.networkopsim.game.model.core.Wire;
import com.networkopsim.game.model.enums.NetworkEnums;
import com.networkopsim.game.model.state.PredictedPacketStatus;
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
                    dispatchPacket(sentPacket, outputWire, outputPort, gameEngine);
                }
            }
        }
    }

    protected void dispatchPacket(Packet packet, Wire wire, Port port, GameEngine gameEngine) {
        boolean compatibleExit = (packet.isVolumetric() ||
                packet.getPacketType() == NetworkEnums.PacketType.SECRET ||
                packet.getPacketType() == NetworkEnums.PacketType.MESSENGER ||
                (Port.getShapeEnum(packet.getShape()) == port.getShape()));

        gameEngine.setPacketOnWire(packet, wire, compatibleExit); // Centralized logic in GameEngine

        if (packet.getFinalStatusForPrediction() == PredictedPacketStatus.QUEUED) {
            packet.setFinalStatusForPrediction(null);
        }
    }

    protected void queuePacket(System system, Packet packet, GameEngine gameEngine, boolean isPredictionRun) {
        synchronized (system.packetQueue) {
            boolean canQueue =
                    system.getSystemType() == NetworkEnums.SystemType.DISTRIBUTOR ||
                            system.packetQueue.size() < System.QUEUE_CAPACITY ||
                            (packet.isVolumetric() && system.getSystemType() == NetworkEnums.SystemType.NODE);

            if (canQueue) {
                packet.setCurrentSystem(system);
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

    protected void processOrQueuePacket(System system, Packet packet, GameEngine gameEngine, boolean isPredictionRun) {
        Port outputPort = findAvailableOutputPort(system, packet, gameEngine, isPredictionRun);
        if (outputPort != null) {
            Wire outputWire = gameEngine.findWireFromPort(outputPort);
            if (outputWire != null) {
                dispatchPacket(packet, outputWire, outputPort, gameEngine);
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
                    if (wire != null && !gameEngine.isWireOccupied(wire, isPredictionRun)) {
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
        if (packet.isVolumetric() || packet.getPacketType() == NetworkEnums.PacketType.SECRET || packet.getPacketType() == NetworkEnums.PacketType.MESSENGER) {
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