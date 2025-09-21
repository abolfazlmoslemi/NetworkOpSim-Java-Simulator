// ===== File: GameServer.java (FINAL CORRECTED with fixed connection logic) =====
// ===== MODULE: server =====

package com.networkopsim.server;

import com.networkopsim.game.controller.logic.GameEngine;
import com.networkopsim.game.model.core.Packet;
import com.networkopsim.game.model.core.System;
import com.networkopsim.game.model.enums.NetworkEnums;
import com.networkopsim.game.model.state.GameState;
import com.networkopsim.game.net.GameStateUpdate;
import com.networkopsim.server.utils.GameStateManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class GameServer implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(GameServer.class);
    private static final int GAME_TICK_MS = 16;
    private static final long AUTOSAVE_INTERVAL_MS = 10000;
    private static final double PACKET_LOSS_GAME_OVER_THRESHOLD = 50.0;

    private final int port;
    private ServerSocket serverSocket;
    private final List<ClientHandler> clients = new CopyOnWriteArrayList<>();
    private volatile boolean running = true;

    private GameEngine gameEngine;
    private boolean levelComplete = false;
    private boolean gameOver = false;
    private long lastAutosaveTime = 0;

    public GameServer(int port) {
        this.port = port;
        initializeWithSaveDataOrNew();
    }

    private void initializeWithSaveDataOrNew() {
        GameStateManager.SaveData saveData = GameStateManager.loadGameState();
        if (saveData != null) {
            logger.info("Valid saved game data found. Initializing server with saved state.");
            this.gameEngine = new GameEngine(null, saveData.gameState, saveData.systems, saveData.wires, saveData.packets);
            this.gameEngine.setSimulationTimeElapsedMs(saveData.simulationTimeElapsedMs);
            this.gameEngine.setSimulationRunning(true);
            this.gameEngine.setPaused(true);
            this.gameEngine.rebuildTransientReferences();
        } else {
            logger.info("No save file found. Initializing with a fresh GameState.");
            this.gameEngine = new GameEngine(null, new GameState(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        }
    }

    @Override
    public void run() {
        try {
            serverSocket = new ServerSocket(port);
            logger.info("Server is listening on port {}", port);

            Thread gameLoopThread = new Thread(this::gameLoop);
            gameLoopThread.setName("GameLoopThread");
            gameLoopThread.start();

            while (running) {
                Socket clientSocket = serverSocket.accept();
                logger.info("New client connected: {}", clientSocket.getInetAddress());
                ClientHandler clientHandler = new ClientHandler(clientSocket, this);
                clients.add(clientHandler);
                new Thread(clientHandler, "ClientHandler-" + clientSocket.getInetAddress()).start();
                // [REMOVED] Do NOT send an update immediately. Wait for the client's first action.
                // clientHandler.sendUpdate(createStateUpdate());
            }
        } catch (IOException e) {
            if (running) {
                logger.error("Server error: Could not listen on port " + port, e);
            }
        } finally {
            stop();
        }
    }

    private void gameLoop() {
        long lastTickTime = java.lang.System.currentTimeMillis();
        lastAutosaveTime = lastTickTime;

        while (running) {
            long currentTime = java.lang.System.currentTimeMillis();
            long elapsedTime = currentTime - lastTickTime;

            if (elapsedTime >= GAME_TICK_MS) {
                if (gameEngine.isSimulationRunning() && !gameEngine.isSimulationPaused()) {
                    gameEngine.gameTick(GAME_TICK_MS);
                    checkEndConditions();
                }

                broadcastState();

                if (currentTime - lastAutosaveTime > AUTOSAVE_INTERVAL_MS) {
                    if (gameEngine.isSimulationRunning() && !gameEngine.isSimulationPaused() && !levelComplete && !gameOver) {
                        GameStateManager.saveGameState(
                                gameEngine.getGameState(), gameEngine.getSystems(), gameEngine.getWires(),
                                gameEngine.getPackets(), gameEngine.getSimulationTimeElapsedMs()
                        );
                    }
                    lastAutosaveTime = currentTime;
                }
                lastTickTime = currentTime;
            }

            try {
                Thread.sleep(Math.max(0, GAME_TICK_MS - (java.lang.System.currentTimeMillis() - lastTickTime)));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                running = false;
            }
        }
    }

    private void checkEndConditions() {
        if (gameOver || levelComplete || !gameEngine.isSimulationRunning()) return;

        if (gameEngine.getGameState().getCurrentLevelTimeLimitMs() > 0 &&
                gameEngine.getSimulationTimeElapsedMs() >= gameEngine.getGameState().getCurrentLevelTimeLimitMs()) {
            handleEndOfLevel();
            return;
        }

        boolean allSourcesFinished = true;
        for (System s : gameEngine.getSystems()) {
            if (s.getSystemType() == NetworkEnums.SystemType.SOURCE && s.getTotalPacketsToGenerate() > 0) {
                if (s.getPacketsGeneratedCount() < s.getTotalPacketsToGenerate()) {
                    allSourcesFinished = false;
                    break;
                }
            }
        }
        if (!allSourcesFinished) return;

        boolean packetsRemaining = !gameEngine.getPackets().isEmpty();
        boolean queuesEmpty = gameEngine.getSystems().stream().allMatch(s -> s.getQueueSize() == 0);

        if (allSourcesFinished && !packetsRemaining && queuesEmpty) {
            handleEndOfLevel();
        }
    }

    private void handleEndOfLevel() {
        if (gameOver || levelComplete) return;

        gameEngine.stopSimulation();
        for (Packet p : gameEngine.getPackets()) { if (p != null) gameEngine.getGameState().increasePacketLoss(p); }
        for (System s : gameEngine.getSystems()) {
            for(Packet p : s.packetQueue) { if (p != null) gameEngine.getGameState().increasePacketLoss(p); }
            s.packetQueue.clear();
        }

        boolean lostTooMany = gameEngine.getGameState().getPacketLossPercentage() >= PACKET_LOSS_GAME_OVER_THRESHOLD;
        if (lostTooMany) {
            gameOver = true;
            logger.info("Level failed due to high packet loss.");
        } else {
            levelComplete = true;
            logger.info("Level completed successfully.");
            int currentLevelIndex = gameEngine.getGameState().getCurrentSelectedLevel() - 1;
            int nextLevelIndex = currentLevelIndex + 1;
            if (nextLevelIndex < gameEngine.getGameState().getMaxLevels()) {
                gameEngine.getGameState().unlockLevel(nextLevelIndex);
            }
        }
        GameStateManager.deleteSaveFile();
        broadcastState();
    }

    public void broadcastState() {
        if (clients.isEmpty()) return;
        GameStateUpdate update = createStateUpdate();
        for (ClientHandler client : clients) {
            client.sendUpdate(update);
        }
    }

    private GameStateUpdate createStateUpdate() {
        return new GameStateUpdate(
                gameEngine.getGameStateDeepCopy(),
                gameEngine.getSystemsDeepCopy(),
                gameEngine.getWiresDeepCopy(),
                gameEngine.getPacketsDeepCopy(),
                gameEngine.getSimulationTimeElapsedMs(),
                this.gameOver,
                this.levelComplete,
                gameEngine.isSimulationRunning(),
                gameEngine.isSimulationPaused(),
                gameEngine.isAtarActive(),
                gameEngine.isAiryamanActive(),
                gameEngine.isSpeedLimiterActive(),
                gameEngine.getGameState().getCurrentInteractiveMode(),
                gameEngine.getGameState().getActiveWireEffects()
        );
    }

    public synchronized GameEngine getGameEngine() {
        return gameEngine;
    }

    public synchronized void reinitializeGameForLevel(int level) {
        logger.info("Server is re-initializing game state for level {}.", level);
        this.levelComplete = false;
        this.gameOver = false;
        GameState newGameState = new GameState();
        this.gameEngine = new GameEngine(null, newGameState, new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        this.gameEngine.initializeLevel(level);
        GameStateManager.deleteSaveFile();
    }

    public void removeClient(ClientHandler client) {
        clients.remove(client);
        logger.info("Client disconnected: {}. Remaining clients: {}", client.getSocket().getInetAddress(), clients.size());
    }

    public void stop() {
        running = false;
        try {
            for (ClientHandler client : clients) {
                client.close();
            }
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            logger.error("Error while stopping the server", e);
        }
        logger.info("Server has been stopped.");
    }
}