// FILE: /NetworkOpSim-Multiplayer/NetworkOpSim-Client/src/main/java/com/networkopsim/client/offline/OfflineGameManager.java
// ================================================================================

package com.networkopsim.client.offline;

import com.networkopsim.client.view.panels.GamePanel;
import com.networkopsim.core.game.logic.GameEngine;
import com.networkopsim.core.game.model.core.Packet;
import com.networkopsim.core.game.model.core.Port;
import com.networkopsim.core.game.model.core.System;
import com.networkopsim.core.game.model.core.Wire;
import com.networkopsim.core.game.model.state.GameState;
import com.networkopsim.core.game.utils.GraphUtils;
import com.networkopsim.shared.dto.GameStateDTO;
import com.networkopsim.shared.dto.PacketDTO;
import com.networkopsim.shared.dto.SystemDTO;
import com.networkopsim.shared.dto.WireDTO;

import java.awt.Rectangle;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Manages the local instance of the game when the client is in offline mode.
 * It encapsulates a GameEngine and GameState, acting as a local server.
 */
public class OfflineGameManager {

    private final GamePanel gamePanel;
    private final GameEngine gameEngine;
    private final GameState gameState;
    private final ReplayRecorder replayRecorder;

    public OfflineGameManager(GamePanel gamePanel) {
        this.gamePanel = gamePanel;
        this.gameState = new GameState();
        this.gameEngine = new GameEngine(this.gameState);
        this.replayRecorder = new ReplayRecorder();
    }

    public void initializeLevel(int level) {
        gameEngine.initializeLevel(level);
    }

    public void gameTick(long tickDurationMs) {
        gameEngine.gameTick(tickDurationMs);
    }

    public void startSimulation() {
        gameEngine.startSimulation();
    }

    public void setPaused(boolean pause) {
        gameEngine.setPaused(pause);
    }

    public void stopSimulation() {
        gameEngine.stopSimulation();
    }

    public String getNetworkValidationErrorMessage() {
        List<System> systems = gameEngine.getSystems();
        List<Wire> wires = gameEngine.getWires();

        if (!GraphUtils.isNetworkConnected(systems, wires)) {
            return "All systems must be part of a single connected network.";
        }
        if (!GraphUtils.areAllSystemPortsConnected(systems)) {
            return "All ports on every system must be connected.";
        }
        for (Wire wire : wires) {
            if (checkWireCollision(wire, systems)) {
                return "At least one wire is intersecting with a system.";
            }
        }
        return null;
    }

    public void createWire(int startPortId, int endPortId) {
        Port startPort = findPortById(startPortId);
        Port endPort = findPortById(endPortId);
        if (startPort != null && endPort != null) {
            try {
                Wire newWire = new Wire(startPort, endPort, 0);
                gameEngine.getWires().add(newWire);
            } catch (Exception e) {
                // handle error locally
            }
        }
    }

    public void deleteWire(int wireId) {
        Wire wireToRemove = gameEngine.getWires().stream().filter(w -> w.getId() == wireId).findFirst().orElse(null);
        if (wireToRemove != null) {
            gameEngine.getWires().remove(wireToRemove);
            wireToRemove.destroy();
        }
    }

    public void addRelayPoint(int wireId, Point2D position) {
        Wire wire = gameEngine.getWires().stream().filter(w -> w.getId() == wireId).findFirst().orElse(null);
        if (wire != null) {
            if (wire.getRelayPointsCount() < Wire.MAX_RELAY_POINTS) {
                if (gameState.spendCoins(1, 1)) { // Assuming player 1 for offline
                    wire.addRelayPoint(new Point2D.Double(position.getX(), position.getY()));
                }
            }
        }
    }

    public void moveRelayPoint(int wireId, int relayIndex, Point2D newPosition) {
        Wire wire = gameEngine.getWires().stream().filter(w -> w.getId() == wireId).findFirst().orElse(null);
        if (wire != null) {
            if (relayIndex >= 0 && relayIndex < wire.getRelayPointsCount()) {
                wire.moveRelayPoint(relayIndex, new Point2D.Double(newPosition.getX(), newPosition.getY()));
            }
        }
    }

    public void purchaseItem(String itemName) {
        // Apply power-up logic directly on the local GameEngine
    }

    private Port findPortById(int portId) {
        for (System system : gameEngine.getSystems()) {
            for (Port p : system.getAllPorts()) if (p.getId() == portId) return p;
        }
        return null;
    }

    private boolean checkWireCollision(Wire wire, List<System> allSystems) {
        List<Point2D.Double> path = wire.getFullPathPoints();
        System startSystem = wire.getStartPort().getParentSystem();
        System endSystem = wire.getEndPort().getParentSystem();

        for (int i = 0; i < path.size() - 1; i++) {
            Line2D.Double segment = new Line2D.Double(path.get(i), path.get(i + 1));
            for (System system : allSystems) {
                if (system.getId() == startSystem.getId() || system.getId() == endSystem.getId()) {
                    continue;
                }
                Rectangle systemBounds = new Rectangle(system.getX(), system.getY(), System.SYSTEM_WIDTH, System.SYSTEM_HEIGHT);
                if (segment.intersects(systemBounds)) {
                    return true;
                }
            }
        }
        return false;
    }

    public GameStateDTO getCurrentGameStateDTO() {
        if (gameEngine == null) return null;
        List<System> systems = gameEngine.getSystems();
        List<Wire> wires = gameEngine.getWires();

        var systemDTOs = systems.stream().map(s -> {
            var portDTOs = new ArrayList<SystemDTO.PortDTO>();
            s.getAllPorts().forEach(p -> portDTOs.add(new SystemDTO.PortDTO(p.getId(), p.getType(), p.getShape(), p.getPosition(), s.getId())));

            return new SystemDTO(
                    s.getId(), s.getX(), s.getY(), s.getSystemType(), portDTOs, s.getQueueSize(), System.QUEUE_CAPACITY,
                    s.isDisabled(), s.isIndicatorOn(), s.getPacketTypeToGenerate(), s.getCurrentBulkOperationId(),
                    s.areAllPortsConnected(),
                    s.getOwnerId(), s.getSabotagedForPlayerId(), s.isControllable(), 0L,
                    Collections.emptyMap(), Collections.emptyMap()
            );
        }).collect(Collectors.toList());

        var wireDTOs = wires.stream().map(w -> {
            var relayPoints = w.getFullPathPoints().stream().skip(1).limit(w.getRelayPointsCount()).collect(Collectors.toList());
            boolean isColliding = checkWireCollision(w, systems);
            return new WireDTO(w.getId(), w.getStartPort().getPosition(), w.getEndPort().getPosition(), relayPoints,
                    w.getBulkPacketTraversals(), Wire.MAX_BULK_TRAVERSALS, w.isDestroyed(), w.getOwnerId(), isColliding);
        }).collect(Collectors.toList());

        var packetDTOs = gameEngine.getPacketsForRendering().stream().map(p -> new PacketDTO(
                p.getId(), p.getShape(), p.getPacketType(), p.getSize(), p.getNoise(), p.getVisualPosition(),
                p.getIdealPosition(), p.getAngle(), p.getVelocity(), p.isReversing(), p.isUpgradedSecret(), p.getBulkParentId(),
                p.getOwnerId()
        )).collect(Collectors.toList());

        String gamePhase = gameEngine.isSimulationRunning() ? "SIMULATION_RUNNING" : "PRE_BUILD";

        return new GameStateDTO(
                systemDTOs, wireDTOs, packetDTOs, new ArrayList<>(),
                gameEngine.getSimulationTimeElapsedMs(),
                gameState.getRemainingWireLength(),
                gameState.getCurrentSelectedLevel(),
                gameEngine.isSimulationRunning(),
                gameEngine.isSimulationPaused(),
                false, false,
                gameState.getPlayer1Coins(),
                gameState.getPlayer1TotalPacketsGeneratedCount(),
                gameState.getPlayer1TotalPacketsLostCount(),
                gameState.getPlayer1PacketLossPercentage(),
                0, 0, 0, 0.0,
                1, // localPlayerId is always 1 in offline
                false, false, false,
                gamePhase, 0L, false, false,
                false, false, 0 // <-- ADDED for isPlayer1InStore, isPlayer2InStore, pausedByPlayerId
        );
    }

    public boolean isSimulationRunning() {
        return gameEngine != null && gameEngine.isSimulationRunning();
    }
}