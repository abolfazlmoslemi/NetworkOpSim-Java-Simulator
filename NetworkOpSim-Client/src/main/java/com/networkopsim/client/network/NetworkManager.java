// FILE: /NetworkOpSim-Multiplayer/NetworkOpSim-Client/src/main/java/com/networkopsim/client/network/NetworkManager.java
// ================================================================================

package com.networkopsim.client.network;

import com.networkopsim.client.core.NetworkGame;
import com.networkopsim.shared.dto.GameStateDTO;
import com.networkopsim.shared.net.ServerResponse;
import com.networkopsim.shared.net.UserCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Manages the network connection to the server for the client.
 * Handles sending commands and receiving game state updates.
 */
public class NetworkManager {
    private static final Logger log = LoggerFactory.getLogger(NetworkManager.class);
    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 26263;

    private final NetworkGame game;
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;

    private volatile ConnectionStatus status = ConnectionStatus.DISCONNECTED;
    private volatile GameStateDTO latestGameState = null;

    private final BlockingQueue<UserCommand> commandQueue = new LinkedBlockingQueue<>();
    private Thread listenerThread;
    private Thread senderThread;

    public NetworkManager(NetworkGame game) {
        this.game = game;
    }

    public void connect() {
        if (status == ConnectionStatus.CONNECTED || status == ConnectionStatus.CONNECTING) {
            return;
        }
        status = ConnectionStatus.CONNECTING;
        game.updateUIsForConnectionStatus();
        log.info("Attempting to connect to server at {}:{}", SERVER_ADDRESS, SERVER_PORT);

        new Thread(() -> {
            try {
                socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
                out = new ObjectOutputStream(socket.getOutputStream());
                in = new ObjectInputStream(socket.getInputStream());
                status = ConnectionStatus.CONNECTED;
                log.info("Successfully connected to the server.");

                startCommunicationThreads();
                game.onConnectionEstablished();

            } catch (IOException e) {
                log.error("Failed to connect to the server: {}", e.getMessage());
                handleDisconnection();
                game.onConnectionFailed();
            }
        }, "Client-Connect-Thread").start();
    }

    public void disconnect() {
        if (status == ConnectionStatus.DISCONNECTED) return;
        log.info("Disconnecting from server...");
        handleDisconnection();
        game.onDisconnected();
    }

    private void handleDisconnection() {
        status = ConnectionStatus.DISCONNECTED;
        try {
            if (listenerThread != null) listenerThread.interrupt();
            if (senderThread != null) senderThread.interrupt();
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            log.warn("Error while closing socket resources: {}", e.getMessage());
        } finally {
            socket = null;
            out = null;
            in = null;
            listenerThread = null;
            senderThread = null;
            commandQueue.clear();
            latestGameState = null;
        }
    }

    private void startCommunicationThreads() {
        listenerThread = new Thread(this::listenToServer, "Client-Listener-Thread");
        listenerThread.setDaemon(true);
        listenerThread.start();

        senderThread = new Thread(this::sendCommands, "Client-Sender-Thread");
        senderThread.setDaemon(true);
        senderThread.start();
    }

    private void listenToServer() {
        try {
            while (status == ConnectionStatus.CONNECTED && !Thread.currentThread().isInterrupted()) {
                Object receivedObject = in.readObject();
                if (receivedObject instanceof GameStateDTO) {
                    latestGameState = (GameStateDTO) receivedObject;
                } else if (receivedObject instanceof ServerResponse) {
                    game.handleServerResponse((ServerResponse) receivedObject);
                } else {
                    log.warn("Received unknown object type from server: {}", receivedObject.getClass().getSimpleName());
                }
            }
        } catch (SocketException e) {
            // This is expected when the socket is closed intentionally
            log.info("Socket closed, listener thread is stopping gracefully.");
        } catch (IOException | ClassNotFoundException e) {
            if (status == ConnectionStatus.CONNECTED) {
                log.error("Connection lost while listening to server: {}", e.getMessage());
                disconnect();
            }
        }
    }

    private void sendCommands() {
        try {
            while (status == ConnectionStatus.CONNECTED && !Thread.currentThread().isInterrupted()) {
                UserCommand command = commandQueue.take(); // Blocks until a command is available
                if (out != null) {
                    out.writeObject(command);
                    out.flush();
                }
            }
        } catch (InterruptedException e) {
            log.info("Sender thread interrupted, shutting down.");
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            if (status == ConnectionStatus.CONNECTED) {
                log.error("Failed to send command, connection may be lost.", e);
                disconnect();
            }
        }
    }

    public void sendCommand(UserCommand command) {
        if (status != ConnectionStatus.CONNECTED) {
            log.warn("Cannot send command, not connected to server. Command: {}", command.getType());
            return;
        }
        if (!commandQueue.offer(command)) {
            log.error("Could not add command to the send queue. Queue might be full.");
        }
    }

    public ConnectionStatus getStatus() {
        return status;
    }

    public GameStateDTO getLatestGameState() {
        return latestGameState;
    }
}