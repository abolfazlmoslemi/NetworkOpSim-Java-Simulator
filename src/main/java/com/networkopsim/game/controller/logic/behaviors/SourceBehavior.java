// ===== File: SourceBehavior.java (FINAL - Checks Global Distributor Busy Flag) =====

package com.networkopsim.game.controller.logic.behaviors;

import com.networkopsim.game.controller.logic.GameEngine;
import com.networkopsim.game.model.core.Packet;
import com.networkopsim.game.model.core.Port;
import com.networkopsim.game.model.core.System;
import com.networkopsim.game.model.core.Wire;
import com.networkopsim.game.model.enums.NetworkEnums;
import com.networkopsim.game.model.state.GameState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SourceBehavior extends AbstractSystemBehavior {

    @Override
    public void receivePacket(System system, Packet packet, GameEngine gameEngine, boolean isPredictionRun, boolean enteredCompatibly) {
        gameEngine.packetSuccessfullyDeliveredInternal(packet, isPredictionRun);
    }

    @Override
    public void attemptPacketGeneration(System system, GameEngine gameEngine, long currentSimTimeMs, boolean isPredictionRun) {
        if (!system.hasOutputPorts()) { return; }
        if (system.getTotalPacketsToGenerate() > 0 && system.getPacketsGeneratedCount() >= system.getTotalPacketsToGenerate()) { return; }
        if (system.getLastGenerationTime() == -1) { system.setLastGenerationTime(currentSimTimeMs - system.getGenerationFrequency()); }
        if (currentSimTimeMs - system.getLastGenerationTime() < system.getGenerationFrequency()) { return; }

        // [CRITICAL CHECK] Before attempting to generate, check the global Distributor busy flag.
        // If a distributor is busy, no source can generate any packet.
        GameState gameState = gameEngine.getGameState();
        if (gameState.isDistributorBusy()) {
            return;
        }

        List<Port> availablePorts = new ArrayList<>();
        synchronized (system.getOutputPorts()) {
            for (Port port : system.getOutputPorts()) {
                if (port != null && port.isConnected()) {
                    Wire wire = gameEngine.findWireFromPort(port);
                    if (wire != null && !gameEngine.isWireOccupied(wire, isPredictionRun)) {
                        System destinationSystem = wire.getEndPort().getParentSystem();
                        if (destinationSystem != null && !destinationSystem.isDisabled()) {
                            availablePorts.add(port);
                        }
                    }
                }
            }
        }

        if (availablePorts.isEmpty()) { return; }

        Collections.shuffle(availablePorts, System.getGlobalRandom());
        Port chosenPort = availablePorts.get(0);
        Wire outputWire = gameEngine.findWireFromPort(chosenPort);
        NetworkEnums.PacketShape shapeToGenerate;
        if (system.getPacketShapeToGenerate() != null) {
            shapeToGenerate = system.getPacketShapeToGenerate();
        } else {
            shapeToGenerate = Port.getPacketShapeFromPortShapeStatic(chosenPort.getShape());
        }

        if (shapeToGenerate == null) { return; }

        system.setLastGenerationTime(currentSimTimeMs);
        Packet newPacket = new Packet(shapeToGenerate, chosenPort.getX(), chosenPort.getY(), system.getPacketTypeToGenerate());
        newPacket.setWire(outputWire, true);
        gameEngine.addPacketInternal(newPacket, isPredictionRun);
        if (system.getTotalPacketsToGenerate() != -1) {
            system.incrementPacketsGenerated();
        }
    }

    @Override
    public NetworkEnums.SystemType getSystemType() {
        return NetworkEnums.SystemType.SOURCE;
    }
}