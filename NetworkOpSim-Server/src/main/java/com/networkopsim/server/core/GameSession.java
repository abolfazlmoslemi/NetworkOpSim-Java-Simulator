// FILE: /NetworkOpSim-Multiplayer/NetworkOpSim-Server/src/main/java/com/networkopsim/server/core/GameSession.java
// ================================================================================

package com.networkopsim.server.core;

import com.networkopsim.core.game.logic.GameEngine;
import com.networkopsim.core.game.model.core.Packet;
import com.networkopsim.core.game.model.core.Port;
import com.networkopsim.core.game.model.core.System;
import com.networkopsim.core.game.model.core.Wire;
import com.networkopsim.core.game.model.state.GameState;
import com.networkopsim.core.game.utils.GraphUtils;
import com.networkopsim.server.persistence.LeaderboardManager;
import com.networkopsim.shared.dto.*;
import com.networkopsim.shared.model.NetworkEnums;
import com.networkopsim.shared.net.ServerResponse;
import com.networkopsim.shared.net.UserCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;

public class GameSession implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(GameSession.class);
    private static final int GAME_TICK_MS = 16;
    private static final int TICKS_PER_SECOND = 1000 / GAME_TICK_MS;
    private static final int NETWORK_UPDATE_RATE_MS = 50;
    private static final long SABOTAGE_DURATION_MS = 10000;
    private static final int SABOTAGE_COST = 10;

    private static final long INITIAL_BUILD_DURATION_MS = 120000;
    private static final long OVERTIME_BUILD_DURATION_MS = 30000;
    private static final long SYSTEM_COOLDOWN_MS = 15000;
    private static final long PACKET_COOLDOWN_MS = 8000;
    private static final long SIMULATION_DURATION_MS = 120000;

    private final int sessionId;
    private final Server server;
    private final List<ClientHandler> players = new ArrayList<>();
    private final BlockingQueue<SessionCommand> commandQueue = new LinkedBlockingQueue<>();
    private final GameEngine gameEngine;
    private final GameState gameState;
    private final LeaderboardManager leaderboardManager;
    private volatile boolean isRunning = true;

    private enum GamePhase { PRE_BUILD, OVERTIME_BUILD, SIMULATION_RUNNING, GAME_OVER }
    private volatile GamePhase currentPhase = GamePhase.PRE_BUILD;
    private long phaseStartTime;
    private volatile boolean player1Ready = false;
    private volatile boolean player2Ready = false;
    private final Random penaltyRandom = new Random();

    private volatile boolean player1InStore = false;
    private volatile boolean player2InStore = false;
    private volatile int pausedByPlayerId = 0;

    private static class SessionCommand {
        final UserCommand command;
        final ClientHandler source;
        SessionCommand(UserCommand command, ClientHandler source) { this.command = command; this.source = source; }
    }

    private static class WavePacketInfo {
        final NetworkEnums.PacketType type;
        final NetworkEnums.PacketShape shape;
        final long minTimeMs;
        final long cooldownMs;
        long lastSpawnTimeMs = -1;

        WavePacketInfo(NetworkEnums.PacketType type, NetworkEnums.PacketShape shape, long minTimeMs, long cooldownMs) {
            this.type = type;
            this.shape = shape;
            this.minTimeMs = minTimeMs;
            this.cooldownMs = cooldownMs;
        }
    }

    private final List<WavePacketInfo> availableWavePackets = List.of(
            new WavePacketInfo(NetworkEnums.PacketType.NORMAL, NetworkEnums.PacketShape.SQUARE, 0, 7000),
            new WavePacketInfo(NetworkEnums.PacketType.NORMAL, NetworkEnums.PacketShape.TRIANGLE, 5000, 7000),
            new WavePacketInfo(NetworkEnums.PacketType.WOBBLE, NetworkEnums.PacketShape.CIRCLE, 25000, 18000),
            new WavePacketInfo(NetworkEnums.PacketType.BULK, NetworkEnums.PacketShape.CIRCLE, 50000, 30000)
    );

    public GameSession(int sessionId, Server server, int level) {
        this.sessionId = sessionId;
        this.server = server;
        this.gameState = new GameState();
        this.gameEngine = new GameEngine(gameState, level);
        this.gameEngine.initializeLevel(level);
        this.leaderboardManager = new LeaderboardManager();
    }

    public void addPlayer(ClientHandler player) {
        if (players.size() < 2) {
            players.add(player);
            player.setGameSession(this, players.size());
        }
    }

    public void removePlayer(ClientHandler player) {
        players.remove(player);
        log.info("Player {} (Client {}) left session {}.", player.getPlayerId(), player.getClientId(), sessionId);
        if (players.isEmpty()) {
            shutdown();
        } else {
            ClientHandler remainingPlayer = players.get(0);
            remainingPlayer.sendResponse(ServerResponse.info("Your opponent has disconnected. You win!"));
            this.currentPhase = GamePhase.GAME_OVER;
        }
    }

    public void queueCommand(UserCommand command, ClientHandler source) {
        commandQueue.add(new SessionCommand(command, source));
    }

    @Override
    public void run() {
        log.info("Game session {} is starting with {} players.", sessionId, players.size());
        for (ClientHandler player : players) {
            player.sendResponse(ServerResponse.success("Match found! The game is starting."));
            player.setInGame(true);
        }
        this.phaseStartTime = java.lang.System.currentTimeMillis();

        long lastTickTime = java.lang.System.nanoTime();
        long lastNetworkUpdateTime = java.lang.System.currentTimeMillis();
        double nsPerTick = 1_000_000_000.0 / TICKS_PER_SECOND;
        double delta = 0;

        while (isRunning && !players.isEmpty()) {
            long now = java.lang.System.nanoTime();
            delta += (now - lastTickTime) / nsPerTick;
            lastTickTime = now;

            boolean shouldProcessTick = false;
            while (delta >= 1) {
                tick();
                delta -= 1;
                shouldProcessTick = true;
            }

            if (shouldProcessTick) {
                if (java.lang.System.currentTimeMillis() - lastNetworkUpdateTime >= NETWORK_UPDATE_RATE_MS) {
                    broadcastGameState();
                    lastNetworkUpdateTime = java.lang.System.currentTimeMillis();
                }
            }

            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                isRunning = false;
                Thread.currentThread().interrupt();
            }
        }
        shutdown();
    }

    private void tick() {
        processCommands();
        long currentTime = java.lang.System.currentTimeMillis();

        switch (currentPhase) {
            case PRE_BUILD: updatePreBuildPhase(currentTime); break;
            case OVERTIME_BUILD: updateOvertimeBuildPhase(currentTime); break;
            case SIMULATION_RUNNING:
                if (gameEngine.isSimulationRunning() && !gameEngine.isSimulationPaused()) {
                    gameEngine.gameTick(GAME_TICK_MS);
                    applyAmmoRewards();
                    manageAutomaticWaves();
                }
                if (gameEngine.getSimulationTimeElapsedMs() >= SIMULATION_DURATION_MS) {
                    this.currentPhase = GamePhase.GAME_OVER;
                    gameEngine.stopSimulation();
                    determineWinnerAndShutdown();
                }
                break;
            case GAME_OVER:
                break;
        }
    }


    private void updatePreBuildPhase(long currentTime) {
        if (player1Ready && player2Ready) {
            startSimulationPhase();
            return;
        }
        if (currentTime - phaseStartTime > INITIAL_BUILD_DURATION_MS) {
            log.info("Session {}: Initial build time over. Entering overtime.", sessionId);
            this.currentPhase = GamePhase.OVERTIME_BUILD;
            this.phaseStartTime = currentTime;
        }
    }

    private void updateOvertimeBuildPhase(long currentTime) {
        if (player1Ready && player2Ready) {
            startSimulationPhase();
            return;
        }
        long overtimeElapsed = currentTime - phaseStartTime;
        if (!player1Ready) applyOvertimePenalty(1, overtimeElapsed);
        if (!player2Ready) applyOvertimePenalty(2, overtimeElapsed);

        if (overtimeElapsed > OVERTIME_BUILD_DURATION_MS) {
            log.info("Session {}: Overtime finished. Evaluating winner based on ready status.", sessionId);
            handleOvertimeEnd();
        }
    }

    private void handleOvertimeEnd() {
        String message;
        if (player1Ready && !player2Ready) {
            message = "Player 2 failed to ready up. Player 1 wins by default!";
            log.info("Session {}: {}", sessionId, message);
        } else if (!player1Ready && player2Ready) {
            message = "Player 1 failed to ready up. Player 2 wins by default!";
            log.info("Session {}: {}", sessionId, message);
        } else if (!player1Ready && !player2Ready) {
            message = "Neither player was ready. The match is a draw!";
            log.info("Session {}: {}", sessionId, message);
        } else {
            startSimulationPhase();
            return;
        }

        this.currentPhase = GamePhase.GAME_OVER;
        broadcastToAll(ServerResponse.info(message));
        shutdown();
    }

    private void broadcastToAll(ServerResponse response) {
        for (ClientHandler player : players) {
            player.sendResponse(response);
        }
    }

    private void applyOvertimePenalty(int playerId, long overtimeElapsedMs) {
        long lastPenaltyTime = gameState.getLastPenaltyTime(playerId);
        long penaltyInterval;
        if (overtimeElapsedMs <= 10000) {
            penaltyInterval = 2000;
            if (overtimeElapsedMs > lastPenaltyTime + penaltyInterval) {
                int opponentId = (playerId == 1) ? 2 : 1;
                List<System> opponentSystems = gameEngine.getSystems().stream().filter(s -> s.isControllable() && s.getOwnerId() == opponentId).toList();
                if (!opponentSystems.isEmpty()) {
                    System targetSystem = opponentSystems.get(penaltyRandom.nextInt(opponentSystems.size()));
                    NetworkEnums.PacketType[] penaltyPackets = {NetworkEnums.PacketType.TROJAN, NetworkEnums.PacketType.WOBBLE};
                    NetworkEnums.PacketType chosenPacket = penaltyPackets[penaltyRandom.nextInt(penaltyPackets.length)];
                    targetSystem.addAmmo(playerId, chosenPacket, 1);
                    log.info("Wrath of Penia: Player {} penalized. Player {} gets 1 {} ammo on system {}.", playerId, playerId, chosenPacket, targetSystem.getId());
                }
                gameState.setLastPenaltyTime(playerId, lastPenaltyTime + penaltyInterval);
            }
        } else if (overtimeElapsedMs <= 20000) {
            penaltyInterval = 1000;
            if (overtimeElapsedMs > lastPenaltyTime + penaltyInterval) {
                gameState.increaseCooldownModifier(playerId, 0.01);
                log.info("Wrath of Aergia: Player {} cooldown modifier increased.", playerId);
                gameState.setLastPenaltyTime(playerId, lastPenaltyTime + penaltyInterval);
            }
        } else if (overtimeElapsedMs <= 30000) {
            penaltyInterval = 1000;
            if (overtimeElapsedMs > lastPenaltyTime + penaltyInterval) {
                gameState.increaseGlobalPacketSpeedModifier(0.03);
                log.info("Wrath of Penia II: Player {} penalized. Global packet speed increased.", playerId);
                gameState.setLastPenaltyTime(playerId, lastPenaltyTime + penaltyInterval);
            }
        }
    }

    private void startSimulationPhase() {
        log.info("Session {}: Both players are ready. Starting simulation.", sessionId);
        this.currentPhase = GamePhase.SIMULATION_RUNNING;
        this.phaseStartTime = java.lang.System.currentTimeMillis();
        gameEngine.getSystems().stream()
                .filter(s -> s.getSystemType() == NetworkEnums.SystemType.SOURCE && !s.isControllable())
                .forEach(s -> s.configureGenerator(0, 99999, NetworkEnums.PacketType.NORMAL));
        gameEngine.startSimulation();
    }

    private void processCommands() {
        SessionCommand sessionCommand;
        while ((sessionCommand = commandQueue.poll()) != null) {
            UserCommand command = sessionCommand.command;
            ClientHandler source = sessionCommand.source;

            if (currentPhase != GamePhase.SIMULATION_RUNNING && (command.getType() == UserCommand.CommandType.PAUSE_SIMULATION || command.getType() == UserCommand.CommandType.RESUME_SIMULATION)) {
                continue;
            }

            switch (command.getType()) {
                case CREATE_WIRE: handleCreateWire(source, (int) command.getArgs()[0], (int) command.getArgs()[1]); break;
                case DELETE_WIRE: handleDeleteWire(source, (int) command.getArgs()[0]); break;
                case ADD_RELAY_POINT: handleAddRelayPoint(source, (int) command.getArgs()[0], (Point) command.getArgs()[1]); break;
                case MOVE_RELAY_POINT: handleMoveRelayPoint(source, (int) command.getArgs()[0], (int) command.getArgs()[1], (Point) command.getArgs()[2]); break;
                case PLAYER_READY: handlePlayerReady(source); break;
                case PAUSE_SIMULATION: handlePause(source); break;
                case RESUME_SIMULATION: handleResume(source); break;
                case PLAYER_IN_STORE: handlePlayerInStore(source, (boolean) command.getArgs()[0]); break;
                case CONTROL_REFERENCE_SYSTEM:
                    if (command.getArgs().length == 1) handleSabotage(source, (int) command.getArgs()[0]);
                    else if (command.getArgs().length >= 2) handlePacketDeployment(source, (int) command.getArgs()[0], (NetworkEnums.PacketType) command.getArgs()[1]);
                    break;
                case PURCHASE_ITEM:
                    gameEngine.purchaseItem(source.getPlayerId(), (String) command.getArgs()[0]);
                    break;
                default: log.warn("Session {} received an unhandled command type: {}", sessionId, command.getType());
            }
        }
    }

    private String validatePlayerNetwork(int playerId) {
        List<System> allSystems = gameEngine.getSystems();
        List<Wire> allWires = gameEngine.getWires();

        if (!GraphUtils.areAllSystemPortsConnected(allSystems)) {
            return "Not all systems have their ports connected.";
        }

        if (!GraphUtils.isNetworkConnected(allSystems, allWires)) {
            return "The entire network must be fully connected.";
        }

        for (Wire wire : allWires) {
            if (checkWireCollision(wire, allSystems)) {
                return "One or more wires are colliding with a system.";
            }
        }

        return null;
    }

    private Port findPortById(int portId) {
        for (System system : gameEngine.getSystems()) {
            for (Port p : system.getAllPorts()) {
                if (p.getId() == portId) return p;
            }
        }
        return null;
    }

    private void handleCreateWire(ClientHandler source, int startPortId, int endPortId) {
        Port startPort = findPortById(startPortId);
        Port endPort = findPortById(endPortId);
        if (startPort == null || endPort == null || startPort.isConnected() || endPort.isConnected() || startPort.getType() != NetworkEnums.PortType.OUTPUT || endPort.getType() != NetworkEnums.PortType.INPUT) {
            source.sendResponse(ServerResponse.error("Invalid or already connected ports."));
            return;
        }
        try {
            Wire newWire = new Wire(startPort, endPort, source.getPlayerId());
            gameEngine.getWires().add(newWire);
        } catch (Exception e) {
            source.sendResponse(ServerResponse.error("Failed to create wire: " + e.getMessage()));
        }
    }

    private void handleDeleteWire(ClientHandler source, int wireId) {
        Wire wireToRemove = gameEngine.getWires().stream()
                .filter(w -> w.getId() == wireId && w.getOwnerId() == source.getPlayerId())
                .findFirst().orElse(null);
        if (wireToRemove != null) {
            gameEngine.getWires().remove(wireToRemove);
            wireToRemove.destroy();
        } else {
            source.sendResponse(ServerResponse.error("Wire not found or you don't own it."));
        }
    }

    private void handleAddRelayPoint(ClientHandler source, int wireId, Point position) {
        Wire wire = gameEngine.getWires().stream()
                .filter(w -> w.getId() == wireId && w.getOwnerId() == source.getPlayerId())
                .findFirst().orElse(null);

        if (wire == null) {
            source.sendResponse(ServerResponse.error("Wire not found or you don't own it."));
            return;
        }
        if (wire.getRelayPointsCount() >= Wire.MAX_RELAY_POINTS) {
            source.sendResponse(ServerResponse.error("Maximum relay points reached for this wire."));
            return;
        }
        if (!gameState.spendCoins(source.getPlayerId(), 1)) {
            source.sendResponse(ServerResponse.error("Not enough coins."));
            return;
        }

        wire.addRelayPoint(new Point2D.Double(position.x, position.y));
        source.sendResponse(ServerResponse.success("Relay point added."));
    }

    private void handleMoveRelayPoint(ClientHandler source, int wireId, int relayIndex, Point newPosition) {
        Wire wire = gameEngine.getWires().stream()
                .filter(w -> w.getId() == wireId && w.getOwnerId() == source.getPlayerId())
                .findFirst().orElse(null);

        if (wire == null) {
            source.sendResponse(ServerResponse.error("Wire not found or you don't own it."));
            return;
        }

        if (relayIndex < 0 || relayIndex >= wire.getRelayPointsCount()) {
            source.sendResponse(ServerResponse.error("Invalid relay point index."));
            return;
        }

        wire.moveRelayPoint(relayIndex, new Point2D.Double(newPosition.x, newPosition.y));
    }


    private void handlePlayerReady(ClientHandler player) {
        if (currentPhase != GamePhase.PRE_BUILD && currentPhase != GamePhase.OVERTIME_BUILD) return;

        String validationError = validatePlayerNetwork(player.getPlayerId());
        if (validationError != null) {
            player.sendResponse(ServerResponse.error("Cannot ready up: " + validationError));
            return;
        }

        if (player.getPlayerId() == 1) player1Ready = true; else if (player.getPlayerId() == 2) player2Ready = true;
        log.info("Session {}: Player {} is ready.", sessionId, player.getPlayerId());
    }

    private void handlePause(ClientHandler source) {
        if (!gameEngine.isSimulationPaused()) {
            pausedByPlayerId = source.getPlayerId();
            gameEngine.setPaused(true);
            log.info("Session {}: Game paused by Player {}", sessionId, pausedByPlayerId);
        }
    }

    private void handleResume(ClientHandler source) {
        if (gameEngine.isSimulationPaused()) {
            boolean p1Store = player1InStore;
            boolean p2Store = player2InStore;

            if (pausedByPlayerId == source.getPlayerId() && ! (source.getPlayerId() == 1 ? p1Store : p2Store) ) {
                if ((source.getPlayerId() == 1 && p2Store) || (source.getPlayerId() == 2 && p1Store)) {
                    return;
                }
                gameEngine.setPaused(false);
                pausedByPlayerId = 0;
                log.info("Session {}: Game resumed by Player {}", sessionId, source.getPlayerId());
            }
        }
    }

    private void handlePlayerInStore(ClientHandler source, boolean isInStore) {
        if (source.getPlayerId() == 1) {
            player1InStore = isInStore;
        } else {
            player2InStore = isInStore;
        }
        log.info("Session {}: Player {} in-store status set to {}", sessionId, source.getPlayerId(), isInStore);
        if (!isInStore && !player1InStore && !player2InStore && gameEngine.isSimulationPaused()) {
            gameEngine.setPaused(false);
            pausedByPlayerId = 0;
            log.info("Session {}: All players left store, game automatically resumed.", sessionId);
        }
    }

    private void handlePacketDeployment(ClientHandler source, int systemId, NetworkEnums.PacketType packetType) {
        if (packetType == NetworkEnums.PacketType.SECRET) {
            source.sendResponse(ServerResponse.error("Secret packets cannot be deployed manually."));
            return;
        }

        System system = gameEngine.getSystems().stream().filter(s -> s.getId() == systemId && s.isControllable()).findFirst().orElse(null);
        if (system == null) { source.sendResponse(ServerResponse.error("Invalid or not a controllable system.")); return; }
        long currentTime = gameEngine.getSimulationTimeElapsedMs();
        int playerId = source.getPlayerId();
        if (system.getSystemCooldownRemaining(currentTime) > 0) { source.sendResponse(ServerResponse.error("System is on cooldown.")); return; }
        if (system.getPacketCooldownRemaining(playerId, packetType, currentTime) > 0) { source.sendResponse(ServerResponse.error("That packet type is on cooldown.")); return; }
        if (system.getAmmoCount(playerId, packetType) <= 0) { source.sendResponse(ServerResponse.error("No ammo of that type left.")); return; }
        int opponentId = (playerId == 1) ? 2 : 1;
        Port outputPort = null;
        Wire outputWire = null;
        for (Port p : system.getOutputPorts()) {
            Wire w = gameEngine.findWireFromPort(p);
            if (w != null && !gameEngine.isWireOccupied(w, false)) {
                System destSystem = w.getEndPort().getParentSystem();
                if (destSystem != null && (destSystem.getOwnerId() == opponentId || destSystem.getOwnerId() == 0)) {
                    outputPort = p; outputWire = w; break;
                }
            }
        }
        if (outputWire == null) { source.sendResponse(ServerResponse.error("No available path into opponent's network.")); return; }
        system.useAmmo(playerId, packetType);
        system.setSystemCooldown(SYSTEM_COOLDOWN_MS, currentTime);
        system.setPacketCooldown(playerId, packetType, PACKET_COOLDOWN_MS, currentTime, gameState.getCooldownModifier(playerId));
        Packet deployedPacket = new Packet(Port.getPacketShapeFromPortShapeStatic(outputPort.getShape()), outputPort.getX(), outputPort.getY(), packetType);
        deployedPacket.setOwnerId(playerId);
        deployedPacket.setWire(outputWire, true);
        gameEngine.addPacketInternal(deployedPacket, false);
        source.sendResponse(ServerResponse.success(packetType.name() + " deployed!"));
    }

    private void handleSabotage(ClientHandler saboteur, int targetSystemId) {
        if (!gameState.spendCoins(saboteur.getPlayerId(), SABOTAGE_COST)) { saboteur.sendResponse(ServerResponse.error("Not enough coins to sabotage!")); return; }
        System targetSystem = gameEngine.getSystems().stream().filter(s -> s.getId() == targetSystemId).findFirst().orElse(null);
        if (targetSystem == null || !targetSystem.isReferenceSystem() || targetSystem.getOwnerId() == saboteur.getPlayerId()) {
            saboteur.sendResponse(ServerResponse.error("Invalid sabotage target.")); gameState.addCoins(saboteur.getPlayerId(), SABOTAGE_COST); return;
        }
        boolean success = targetSystem.applySabotage(saboteur.getPlayerId(), SABOTAGE_DURATION_MS, gameEngine.getSimulationTimeElapsedMs());
        if (success) { saboteur.sendResponse(ServerResponse.success("Sabotage successful!")); }
        else { saboteur.sendResponse(ServerResponse.error("System is already under an effect.")); gameState.addCoins(saboteur.getPlayerId(), SABOTAGE_COST); }
    }

    private void broadcastGameState() {
        if (players.isEmpty()) return;
        GameStateDTO dto = createGameStateDTO();
        for (ClientHandler player : new ArrayList<>(players)) {
            try { player.sendGameState(dto); } catch (IOException e) { log.error("Failed to send game state to client {}. Removing from session.", player.getClientId(), e); removePlayer(player); }
        }
    }

    private GameStateDTO createGameStateDTO() {
        long currentTime = java.lang.System.currentTimeMillis();
        long simTime = gameEngine.getSimulationTimeElapsedMs();
        List<System> systems = gameEngine.getSystems();
        List<Wire> wires = gameEngine.getWires();

        List<SystemDTO> systemDTOs = systems.stream().map(s -> mapSystemToDTO(s, simTime)).collect(Collectors.toList());
        List<WireDTO> wireDTOs = wires.stream().map(w -> mapWireToDTO(w, systems)).collect(Collectors.toList());
        List<PacketDTO> packetDTOs = gameEngine.getPacketsForRendering().stream().map(this::mapPacketToDTO).collect(Collectors.toList());
        long buildTimeRemaining = 0;
        if (currentPhase == GamePhase.PRE_BUILD) buildTimeRemaining = Math.max(0, INITIAL_BUILD_DURATION_MS - (currentTime - phaseStartTime));
        else if (currentPhase == GamePhase.OVERTIME_BUILD) buildTimeRemaining = -Math.max(0, OVERTIME_BUILD_DURATION_MS - (currentTime - phaseStartTime));

        return new GameStateDTO(systemDTOs, wireDTOs, packetDTOs, new ArrayList<>(), simTime,
                gameState.getRemainingWireLength(), gameState.getCurrentSelectedLevel(),
                gameEngine.isSimulationRunning(), gameEngine.isSimulationPaused(),
                currentPhase == GamePhase.GAME_OVER, false,
                gameState.getPlayer1Coins(), gameState.getPlayer1TotalPacketsGeneratedCount(),
                gameState.getPlayer1TotalPacketsLostCount(), gameState.getPlayer1PacketLossPercentage(),
                gameState.getPlayer2Coins(), gameState.getPlayer2TotalPacketsGeneratedCount(),
                gameState.getPlayer2TotalPacketsLostCount(), gameState.getPlayer2PacketLossPercentage(),
                0, false, false, false,
                currentPhase.name(), buildTimeRemaining, player1Ready, player2Ready,
                player1InStore, player2InStore, pausedByPlayerId);
    }

    private SystemDTO mapSystemToDTO(System system, long currentSimTime) {
        if (system == null) return null;
        List<SystemDTO.PortDTO> portDTOs = new ArrayList<>();
        system.getAllPorts().forEach(p -> portDTOs.add(mapPortToDTO(p)));

        long systemCooldown = 0;
        Map<Integer, Map<NetworkEnums.PacketType, Integer>> ammo = new HashMap<>();
        Map<Integer, Map<NetworkEnums.PacketType, Long>> cooldowns = new HashMap<>();

        if (system.isControllable()) {
            systemCooldown = system.getSystemCooldownRemaining(currentSimTime);
            ammo = system.getAllAmmo();
            for (Integer playerId : ammo.keySet()) {
                cooldowns.put(playerId, system.getAllPacketCooldownsForPlayer(playerId, currentSimTime));
            }
        }

        return new SystemDTO(
                system.getId(), system.getX(), system.getY(), system.getSystemType(), portDTOs,
                system.getQueueSize(), System.QUEUE_CAPACITY, system.isDisabled(), system.isIndicatorOn(),
                system.getPacketTypeToGenerate(), system.getCurrentBulkOperationId(), system.areAllPortsConnected(),
                system.getOwnerId(), system.getSabotagedForPlayerId(), system.isControllable(), systemCooldown, ammo, cooldowns
        );
    }

    private SystemDTO.PortDTO mapPortToDTO(Port port) {
        if (port == null) return null;
        return new SystemDTO.PortDTO(port.getId(), port.getType(), port.getShape(), port.getPosition(), port.getParentSystem().getId());
    }

    private WireDTO mapWireToDTO(Wire wire, List<System> allSystems) {
        if (wire == null) return null;
        List<Point2D.Double> relayPoints = wire.getFullPathPoints().stream().skip(1).limit(Math.max(0, wire.getFullPathPoints().size() - 2)).collect(Collectors.toList());
        boolean isColliding = checkWireCollision(wire, allSystems);
        return new WireDTO(wire.getId(), wire.getStartPort().getPosition(), wire.getEndPort().getPosition(), relayPoints, wire.getBulkPacketTraversals(), Wire.MAX_BULK_TRAVERSALS, wire.isDestroyed(), wire.getOwnerId(), isColliding);
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

    private PacketDTO mapPacketToDTO(Packet packet) {
        if (packet == null) return null;
        return new PacketDTO(packet.getId(), packet.getShape(), packet.getPacketType(), packet.getSize(), packet.getNoise(), packet.getVisualPosition(), packet.getIdealPosition(), packet.getAngle(), packet.getVelocity(), packet.isReversing(), packet.isUpgradedSecret(), packet.getBulkParentId(), packet.getOwnerId());
    }

    private void determineWinnerAndShutdown() {
        if (!isRunning) return;

        int p1TotalGenerated = gameState.getPlayer1TotalPacketsGeneratedCount();
        int p1Lost = gameState.getPlayer1TotalPacketsLostCount();
        int p1Successful = p1TotalGenerated - p1Lost;

        int p2TotalGenerated = gameState.getPlayer2TotalPacketsGeneratedCount();
        int p2Lost = gameState.getPlayer2TotalPacketsLostCount();
        int p2Successful = p2TotalGenerated - p2Lost;

        double p1FinalScore = p1Successful - (p1Lost * 1.5);
        double p2FinalScore = p2Successful - (p2Lost * 1.5);

        String resultMessage;
        if (Math.abs(p1FinalScore - p2FinalScore) < 0.1) {
            resultMessage = String.format("Draw! Final Scores: Player 1 (%.1f), Player 2 (%.1f)", p1FinalScore, p2FinalScore);
        } else if (p1FinalScore > p2FinalScore) {
            resultMessage = String.format("Player 1 Wins! Final Scores: P1 (%.1f), P2 (%.1f)", p1FinalScore, p2FinalScore);
        } else {
            resultMessage = String.format("Player 2 Wins! Final Scores: P2 (%.1f), P1 (%.1f)", p2FinalScore, p1FinalScore);
        }

        log.info("Session {}: Game over. {}", sessionId, resultMessage);

        long finalTime = gameEngine.getSimulationTimeElapsedMs();
        int p1Xp = gameState.getPlayer1Coins() + (p1Successful * 10) - (p1Lost * 2);
        int p2Xp = gameState.getPlayer2Coins() + (p2Successful * 10) - (p2Lost * 2);

        if (players.size() >= 1) { // Check to prevent errors if a player disconnects
            if(players.get(0).getPlayerId() == 1) {
                leaderboardManager.updateScore(players.get(0).getUsername(), gameEngine.getCurrentLevel(), finalTime, p1Xp);
                if (players.size() == 2) leaderboardManager.updateScore(players.get(1).getUsername(), gameEngine.getCurrentLevel(), finalTime, p2Xp);
            } else {
                leaderboardManager.updateScore(players.get(0).getUsername(), gameEngine.getCurrentLevel(), finalTime, p2Xp);
                if (players.size() == 2) leaderboardManager.updateScore(players.get(1).getUsername(), gameEngine.getCurrentLevel(), finalTime, p1Xp);
            }
        }

        ServerResponse finalResponse = ServerResponse.info(resultMessage);
        broadcastToAll(finalResponse);

        shutdown();
    }

    private void applyAmmoRewards() {
        int p1Rewards = gameState.consumePendingAmmoReward(1);
        if (p1Rewards > 0) {
            addRewardAmmoToPlayer(1, p1Rewards);
        }

        int p2Rewards = gameState.consumePendingAmmoReward(2);
        if (p2Rewards > 0) {
            addRewardAmmoToPlayer(2, p2Rewards);
        }
    }

    private void addRewardAmmoToPlayer(int rewardedPlayerId, int amount) {
        List<System> controllableSystems = gameEngine.getSystems().stream()
                .filter(s -> s.isControllable() && s.getOwnerId() == rewardedPlayerId)
                .toList();

        if (!controllableSystems.isEmpty()) {
            System targetSystem = controllableSystems.get(penaltyRandom.nextInt(controllableSystems.size()));
            NetworkEnums.PacketType rewardType = NetworkEnums.PacketType.TROJAN;
            targetSystem.addAmmo(rewardedPlayerId, rewardType, amount);
            log.info("Feedback Loop: Player {} received {} {} ammo on system {}.", rewardedPlayerId, amount, rewardType, targetSystem.getId());
        }
    }

    private void manageAutomaticWaves() {
        long currentSimTime = gameEngine.getSimulationTimeElapsedMs();

        List<System> uncontrollableSources = gameEngine.getSystems().stream()
                .filter(s -> s.getSystemType() == NetworkEnums.SystemType.SOURCE && !s.isControllable())
                .toList();

        if (uncontrollableSources.isEmpty()) return;

        System source = uncontrollableSources.get(penaltyRandom.nextInt(uncontrollableSources.size()));

        List<Port> availablePorts = source.getOutputPorts().stream()
                .filter(p -> {
                    Wire w = gameEngine.findWireFromPort(p);
                    return w != null && !gameEngine.isWireOccupied(w, false);
                }).toList();

        if (availablePorts.isEmpty()) return;
        Port port = availablePorts.get(penaltyRandom.nextInt(availablePorts.size()));
        Wire wire = gameEngine.findWireFromPort(port);

        if (wire == null) return;

        List<WavePacketInfo> spawnCandidates = new ArrayList<>();
        for (WavePacketInfo info : availableWavePackets) {
            if (currentSimTime >= info.minTimeMs && (info.lastSpawnTimeMs == -1 || currentSimTime >= info.lastSpawnTimeMs + info.cooldownMs)) {
                spawnCandidates.add(info);
            }
        }

        if (!spawnCandidates.isEmpty()) {
            WavePacketInfo packetToSpawn = spawnCandidates.get(penaltyRandom.nextInt(spawnCandidates.size()));

            Packet newPacket = new Packet(packetToSpawn.shape, port.getX(), port.getY(), packetToSpawn.type);

            int ownerId = (source.getAlternatingOwnerTurn() == 0) ? 1 : 2;
            newPacket.setOwnerId(ownerId);
            source.toggleAlternatingOwnerTurn();

            newPacket.setWire(wire, true);
            gameEngine.addPacketInternal(newPacket, false);

            packetToSpawn.lastSpawnTimeMs = currentSimTime;

            log.info("Automatic Wave: Spawning {} for Player {} from System {}", packetToSpawn.type, ownerId, source.getId());
        }
    }


    public void shutdown() {
        if (!isRunning) return;
        isRunning = false;
        log.info("Shutting down game session {}.", sessionId);
        for (ClientHandler player : new ArrayList<>(players)) {
            player.sendResponse(ServerResponse.info("Game session has ended."));
            player.setInGame(false);
            player.setGameSession(null, 0);
        }
        players.clear();
        server.removeSession(sessionId);
    }
}