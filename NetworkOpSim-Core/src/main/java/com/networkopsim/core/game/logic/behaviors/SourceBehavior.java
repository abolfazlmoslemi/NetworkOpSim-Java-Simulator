// FILE: /NetworkOpSim-Multiplayer/NetworkOpSim-Core/src/main/java/com/networkopsim/core/game/logic/behaviors/SourceBehavior.java
// ================================================================================

package com.networkopsim.core.game.logic.behaviors;

import com.networkopsim.core.game.logic.GameEngine;
import com.networkopsim.core.game.model.core.Packet;
import com.networkopsim.core.game.model.core.Port;
import com.networkopsim.core.game.model.core.System;
import com.networkopsim.core.game.model.core.Wire;
import com.networkopsim.core.game.model.state.GameState;
import com.networkopsim.shared.model.NetworkEnums;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SourceBehavior extends AbstractSystemBehavior {

    @Override
    public void receivePacket(System system, Packet packet, GameEngine gameEngine, boolean isPredictionRun, boolean enteredCompatibly) {
        // If a packet reaches a source system that is not its own, it's considered lost.
        // If it reaches its own source, it's considered delivered/resolved.
        if (system.getOwnerId() != 0 && packet.getOwnerId() != system.getOwnerId()) {
            gameEngine.packetLostInternal(packet, isPredictionRun);
        } else {
            gameEngine.packetSuccessfullyDeliveredInternal(packet, isPredictionRun);
        }
    }

    @Override
    public void attemptPacketGeneration(System system, GameEngine gameEngine, long currentSimTimeMs, boolean isPredictionRun) {
        if (!system.hasOutputPorts()) { return; }
        if (system.getTotalPacketsToGenerate() > 0 && system.getPacketsGeneratedCount() >= system.getTotalPacketsToGenerate()) { return; }
        if (system.getLastGenerationTime() == -1) { system.setLastGenerationTime(currentSimTimeMs - system.getGenerationFrequency()); }
        if (currentSimTimeMs - system.getLastGenerationTime() < system.getGenerationFrequency()) { return; }

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

        // --- MODIFIED LOGIC FOR PACKET TYPE GENERATION ---
        NetworkEnums.PacketType typeToGenerate = system.getPacketTypeToGenerate();

        // If the system is uncontrollable, it has a chance to generate a SECRET packet.
        if (!system.isControllable() && System.getGlobalRandom().nextDouble() < 0.15) { // 15% chance
            typeToGenerate = NetworkEnums.PacketType.SECRET;
        }
        // --- END OF MODIFIED LOGIC ---

        Packet newPacket = new Packet(shapeToGenerate, chosenPort.getX(), chosenPort.getY(), typeToGenerate);

        if (system.isControllable()) {
            // Controllable sources always generate packets for their owner.
            newPacket.setOwnerId(system.getOwnerId());
        } else if (system.isReferenceSystem()) {
            // Non-controllable reference systems alternate packet ownership for game balance.
            int ownerIdToSet = (system.getAlternatingOwnerTurn() == 0) ? 1 : 2;
            newPacket.setOwnerId(ownerIdToSet);
            system.toggleAlternatingOwnerTurn(); // Switch turn for the next packet
        } else {
            // For any other case (e.g., future system types), default to neutral.
            newPacket.setOwnerId(0);
        }

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