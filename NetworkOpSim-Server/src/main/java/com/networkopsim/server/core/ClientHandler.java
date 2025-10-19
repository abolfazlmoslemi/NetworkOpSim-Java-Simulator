// FILE: /NetworkOpSim-Multiplayer/NetworkOpSim-Server/src/main/java/com/networkopsim/server/core/ClientHandler.java
// ================================================================================

package com.networkopsim.server.core;

import com.networkopsim.server.handling.ExceptionHandlingProxy;
import com.networkopsim.server.persistence.LeaderboardManager;
import com.networkopsim.server.persistence.PlayerManager;
import com.networkopsim.server.persistence.PlayerProfile;
import com.networkopsim.shared.dto.GameStateDTO;
import com.networkopsim.shared.dto.LeaderboardDTO;
import com.networkopsim.shared.net.OfflineResult;
import com.networkopsim.shared.net.ServerResponse;
import com.networkopsim.shared.net.UserCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;
import java.net.Socket;

public class ClientHandler implements Runnable, IClientCommandHandler {
    private static final Logger log = LoggerFactory.getLogger(ClientHandler.class);

    private final Socket socket;
    private final Server server;
    private final int clientId;
    private ObjectOutputStream out;
    private ObjectInputStream in;

    private volatile boolean isRunning = true;
    private String username = "Player";
    private PlayerProfile playerProfile;
    private final PlayerManager playerManager;
    private final LeaderboardManager leaderboardManager;

    // Multiplayer state
    private GameSession gameSession;
    private int playerIdInSession; // 1 or 2
    private volatile boolean isInGame = false;
    private final Object outputStreamLock = new Object();

    private IClientCommandHandler commandProcessorProxy;

    public ClientHandler(Socket socket, Server server, int clientId) {
        this.socket = socket;
        this.server = server;
        this.clientId = clientId;
        this.playerManager = new PlayerManager();
        this.leaderboardManager = new LeaderboardManager();
    }

    @Override
    public void run() {
        try {
            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());

            this.commandProcessorProxy = ExceptionHandlingProxy.createProxy(
                    this,
                    response -> this.sendResponse((ServerResponse) response),
                    "com.networkopsim.server.handling",
                    IClientCommandHandler.class
            );

            while (isRunning) {
                try {
                    UserCommand command = (UserCommand) in.readObject();
                    try {
                        commandProcessorProxy.processCommand(command);
                    } catch (Exception e) {
                        log.error("An unexpected exception escaped the proxy handler.", e);
                        sendResponse(ServerResponse.error("A critical, unhandled server error occurred."));
                    }
                } catch (ClassNotFoundException e) {
                    log.warn("Received an unknown object type from client {}: {}", clientId, e.getMessage());
                } catch (IOException e) {
                    if (isRunning) {
                        log.info("Client {} disconnected: {}", clientId, e.getMessage());
                    }
                    break;
                }
            }
        } catch (IOException e) {
            if (isRunning) {
                log.info("Client {} connection failed: {}", clientId, e.getMessage());
            }
        } finally {
            shutdown();
        }
    }

    @Override
    public void processCommand(UserCommand command) throws Exception {
        if (command == null) return;

        if (isInGame && gameSession != null) {
            gameSession.queueCommand(command, this);
            return;
        }

        switch (command.getType()) {
            case SET_USERNAME:
                handleSetUsername(command);
                break;
            case GET_LEADERBOARD:
                handleGetLeaderboard();
                break;
            case SUBMIT_OFFLINE_RESULT:
                handleOfflineResult(command);
                break;
            case JOIN_MATCHMAKING_QUEUE:
                if (command.getArgs().length > 0 && command.getArgs()[0] instanceof Integer) {
                    int level = (Integer) command.getArgs()[0];
                    server.addToWaitingQueue(this, level);
                } else {
                    sendResponse(ServerResponse.error("Invalid matchmaking request: Level not specified."));
                }
                break;
            case LEAVE_MATCHMAKING_QUEUE:
                server.removeFromAnyWaitingQueue(this);
                sendResponse(ServerResponse.info("You have left the matchmaking queue."));
                break;
            default:
                log.warn("Client {} sent an unhandled command outside of a game session: {}", clientId, command.getType());
        }
    }

    private void handleSetUsername(UserCommand command) {
        this.username = (String) command.getArgs()[0];
        this.playerProfile = playerManager.getOrCreatePlayer(this.username);
        if (this.playerProfile != null) {
            log.info("Client {} set username to {} and loaded profile.", clientId, this.username);
            sendResponse(ServerResponse.success("Username set and profile loaded: " + this.username));
        } else {
            log.error("Failed to load or create profile for user {}", this.username);
            sendResponse(ServerResponse.error("Server error: Could not access player profile."));
        }
    }

    private void handleGetLeaderboard() {
        if (this.username != null && !this.username.isEmpty()) {
            LeaderboardDTO data = leaderboardManager.getLeaderboardData(this.username);
            sendResponse(ServerResponse.success("Leaderboard data retrieved.", data));
        } else {
            sendResponse(ServerResponse.error("Username not set. Cannot fetch leaderboard."));
        }
    }

    private void handleOfflineResult(UserCommand command) {
        if (command.getArgs().length > 0 && command.getArgs()[0] instanceof OfflineResult) {
            OfflineResult result = (OfflineResult) command.getArgs()[0];
            log.info("Received offline result from client {} for user '{}', level {}", clientId, result.getUsername(), result.getLevel());

            if (!result.getUsername().equals(this.username)) {
                sendResponse(ServerResponse.error("Offline result username does not match current user."));
                return;
            }

            boolean isValid = true;

            if (isValid) {
                leaderboardManager.updateScore(
                        result.getUsername(),
                        result.getLevel(),
                        result.getFinalTimeMillis(),
                        result.getFinalXp()
                );
                sendResponse(ServerResponse.success("Offline result for level " + result.getLevel() + " processed."));
            } else {
                log.warn("Offline result validation failed for user {}.", result.getUsername());
                sendResponse(ServerResponse.error("Offline result for level " + result.getLevel() + " failed validation."));
            }
        }
    }

    public void sendGameState(GameStateDTO dto) throws IOException {
        if (out == null || !isRunning) return;

        try {
            Field field = GameStateDTO.class.getDeclaredField("localPlayerId");
            field.setAccessible(true);
            field.set(dto, this.playerIdInSession);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            log.error("FATAL: Could not set localPlayerId on GameStateDTO via reflection!", e);
        }

        synchronized (outputStreamLock) {
            out.writeObject(dto);
            out.reset();
        }
    }

    public void sendResponse(ServerResponse response) {
        if (out == null || !isRunning) return;
        try {
            synchronized (outputStreamLock) {
                out.writeObject(response);
                out.flush();
            }
        } catch (IOException e) {
            log.error("Failed to send response to client {}", clientId, e);
        }
    }

    private void shutdown() {
        if (!isRunning) return;
        isRunning = false;
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            log.warn("Error while closing socket for client {}", clientId, e);
        }
        server.removeClient(clientId);
        log.info("Client handler for {} shut down.", clientId);
    }

    public String getUsername() { return username; }
    public int getClientId() { return clientId; }
    public int getPlayerId() { return playerIdInSession; }
    public GameSession getGameSession() { return gameSession; }
    public void setInGame(boolean inGame) { this.isInGame = inGame; }
    public void setGameSession(GameSession session, int playerId) {
        this.gameSession = session;
        this.playerIdInSession = playerId;
    }
}