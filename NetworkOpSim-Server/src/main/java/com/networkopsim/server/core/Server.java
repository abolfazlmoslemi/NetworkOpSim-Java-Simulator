// FILE: /NetworkOpSim-Multiplayer/NetworkOpSim-Server/src/main/java/com/networkopsim/server/core/Server.java
// ================================================================================

package com.networkopsim.server.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The main class for the Network Operator Simulator server.
 * It listens for incoming client connections, pairs them up, and starts game sessions.
 */
public class Server {
    private static final Logger log = LoggerFactory.getLogger(Server.class);
    private static final int PORT = 26263;
    private static final int MAX_PLAYERS = 100;

    private final ExecutorService clientExecutor = Executors.newCachedThreadPool();
    private final ConcurrentHashMap<Integer, ClientHandler> activeClients = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, GameSession> activeSessions = new ConcurrentHashMap<>();
    private final AtomicInteger clientIdCounter = new AtomicInteger(0);
    private final AtomicInteger sessionIdCounter = new AtomicInteger(0);

    // Map of waiting queues, one for each level
    private final Map<Integer, Queue<ClientHandler>> waitingQueues = new ConcurrentHashMap<>();

    public void start() {
        log.info("Server is starting on port {}...", PORT);

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            log.info("Server started successfully. Waiting for clients...");

            while (!serverSocket.isClosed()) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    int clientId = clientIdCounter.incrementAndGet();
                    log.info("Client connected from {} with assigned ID {}", clientSocket.getInetAddress(), clientId);

                    if (activeClients.size() >= MAX_PLAYERS) {
                        log.warn("Max players reached. Rejecting connection from {}.", clientSocket.getInetAddress());
                        clientSocket.close();
                        continue;
                    }

                    ClientHandler clientHandler = new ClientHandler(clientSocket, this, clientId);
                    activeClients.put(clientId, clientHandler);
                    clientExecutor.submit(clientHandler);

                } catch (IOException e) {
                    log.error("Error accepting client connection", e);
                }
            }
        } catch (IOException e) {
            log.error("Could not start server on port {}", PORT, e);
        } finally {
            shutdown();
        }
    }

    /**
     * Adds a client to the matchmaking queue for a specific level and attempts to create a new game session.
     * @param client The ClientHandler waiting for a match.
     * @param level The level the client wants to play.
     */
    public synchronized void addToWaitingQueue(ClientHandler client, int level) {
        // Remove from any other queue first to prevent being in multiple queues
        removeFromAnyWaitingQueue(client);

        waitingQueues.computeIfAbsent(level, k -> new ConcurrentLinkedQueue<>()).add(client);
        log.info("Client {} added to matchmaking queue for level {}. Queue size: {}", client.getClientId(), level, waitingQueues.get(level).size());
        checkForMatch(level);
    }

    /**
     * Removes a client from any matchmaking queue they might be in.
     * @param client The ClientHandler to remove.
     */
    public synchronized void removeFromAnyWaitingQueue(ClientHandler client) {
        waitingQueues.values().forEach(queue -> {
            if (queue.remove(client)) {
                log.info("Client {} removed from a matchmaking queue.", client.getClientId());
            }
        });
    }

    /**
     * Checks the waiting queue for a specific level and starts a new GameSession if two players are available.
     * @param level The level to check for matches.
     */
    private synchronized void checkForMatch(int level) {
        Queue<ClientHandler> queue = waitingQueues.get(level);
        if (queue != null && queue.size() >= 2) {
            ClientHandler player1 = queue.poll();
            ClientHandler player2 = queue.poll();

            if (player1 != null && player2 != null) {
                log.info("Match found for level {}! Pairing Client {} and Client {}.", level, player1.getClientId(), player2.getClientId());
                int sessionId = sessionIdCounter.incrementAndGet();
                GameSession newSession = new GameSession(sessionId, this, level);

                newSession.addPlayer(player1);
                newSession.addPlayer(player2);

                activeSessions.put(sessionId, newSession);
                clientExecutor.submit(newSession); // Start the game session's own loop
            }
        }
    }

    /**
     * Removes a client from the active list, typically after disconnection.
     * Also removes them from any waiting queue if they were there.
     * @param clientId The ID of the client to remove.
     */
    public void removeClient(int clientId) {
        ClientHandler removedClient = activeClients.remove(clientId);
        if (removedClient != null) {
            removeFromAnyWaitingQueue(removedClient);
            GameSession session = removedClient.getGameSession();
            if (session != null) {
                session.removePlayer(removedClient);
            }
            log.info("Client {} has been fully removed from the server.", clientId);
        }
    }

    /**
     * Removes a completed or terminated game session.
     * @param sessionId The ID of the session to remove.
     */
    public void removeSession(int sessionId) {
        GameSession removedSession = activeSessions.remove(sessionId);
        if (removedSession != null) {
            log.info("Game session {} has ended and been removed.", sessionId);
        }
    }

    public void shutdown() {
        log.info("Shutting down server...");
        activeSessions.values().forEach(GameSession::shutdown);
        clientExecutor.shutdownNow();
        log.info("Server has been shut down.");
    }

    public static void main(String[] args) {
        Server server = new Server();
        server.start();
    }
}