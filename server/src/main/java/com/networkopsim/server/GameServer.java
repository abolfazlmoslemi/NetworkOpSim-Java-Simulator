// ===== File: GameServer.java (NEW - Core server logic) =====
// ===== MODULE: server =====

package com.networkopsim.server;

import com.networkopsim.game.controller.logic.GameEngine;
import com.networkopsim.game.model.core.Packet;
import com.networkopsim.game.model.core.System;
import com.networkopsim.game.model.core.Wire;
import com.networkopsim.game.model.state.GameState;
import com.networkopsim.game.net.GameStateUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

public class GameServer implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(GameServer.class);
    private static final int GAME_TICK_MS = 16;

    private final int port;
    private ServerSocket serverSocket;
    private final List<ClientHandler> clients = new CopyOnWriteArrayList<>();
    private volatile boolean running = true;

    private GameEngine gameEngine;

    public GameServer(int port) {
        this.port = port;
        // GameEngine on the server doesn't need a GamePanel or NetworkGame.
        // We initialize it with a fresh GameState and empty lists.
        // The state will be populated when a client requests to initialize a level.
        this.gameEngine = new GameEngine(null, new GameState(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
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
        while (running) {
            long currentTime = java.lang.System.currentTimeMillis();
            long elapsedTime = currentTime - lastTickTime;

            if (elapsedTime >= GAME_TICK_MS) {
                // The main game logic tick happens here
                gameEngine.gameTick(GAME_TICK_MS);

                // After the tick, broadcast the new state to all clients
                broadcastState();
                lastTickTime = currentTime;
            }

            try {
                // A small sleep to prevent the loop from consuming 100% CPU
                Thread.sleep(Math.max(0, GAME_TICK_MS - (java.lang.System.currentTimeMillis() - lastTickTime)));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                running = false;
            }
        }
    }

    public void broadcastState() {
        if (clients.isEmpty()) return;

        // Create deep copies to ensure thread safety when sending over the network
        GameState gameStateCopy = gameEngine.getGameStateDeepCopy();
        List<System> systemsCopy = gameEngine.getSystemsDeepCopy();
        List<Wire> wiresCopy = gameEngine.getWiresDeepCopy();
        // Packets are tricky. We need to filter for rendering and then copy.
        List<Packet> packetsCopy = gameEngine.getPackets().stream()
                .map(p -> gameEngine.deepCopy(p))
                .collect(Collectors.toList());

        GameStateUpdate update = new GameStateUpdate(
                gameStateCopy, systemsCopy, wiresCopy, packetsCopy,
                gameEngine.getSimulationTimeElapsedMs(),
                false, // gameOver and levelComplete logic will be handled by GameEngine later
                false,
                gameEngine.isSimulationRunning(),
                gameEngine.isSimulationPaused()
        );

        for (ClientHandler client : clients) {
            client.sendUpdate(update);
        }
    }

    public synchronized GameEngine getGameEngine() {
        return gameEngine;
    }

    // This method allows ClientHandlers to create a new GameEngine for a new level
    public synchronized void reinitializeGameForLevel(int level) {
        logger.info("Server is re-initializing game state for level {}.", level);
        GameState newGameState = new GameState();
        this.gameEngine = new GameEngine(null, newGameState, new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        this.gameEngine.initializeLevel(level);
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