// ===== File: GameClient.java (FINAL CORRECTED with Definitive Stream Initialization) =====
// ===== MODULE: client =====

package com.networkopsim.client;

import com.networkopsim.game.net.ClientAction;
import com.networkopsim.game.net.GameStateUpdate;
import com.networkopsim.game.view.panels.GamePanel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;

public class GameClient implements Runnable {
  private static final Logger logger = LoggerFactory.getLogger(GameClient.class);
  private final String host;
  private final int port;
  private Socket socket;
  private ObjectOutputStream out;
  private ObjectInputStream in;
  private volatile boolean running = true;
  private final GamePanel gamePanel;
  private final int levelToLoad;

  public GameClient(String host, int port, GamePanel gamePanel, int levelToLoad) {
    this.host = host;
    this.port = port;
    this.gamePanel = gamePanel;
    this.levelToLoad = levelToLoad;
  }

  @Override
  public void run() {
    try {
      socket = new Socket(host, port);

      // [DEFINITIVE FIX] Client creates its input stream first. This will wait for the server's
      // output stream header before proceeding.
      in = new ObjectInputStream(socket.getInputStream());
      // Client creates its output stream second, completing the handshake.
      out = new ObjectOutputStream(socket.getOutputStream());

      logger.info("Successfully connected to game server at {}:{}", host, port);

      SwingUtilities.invokeLater(() -> gamePanel.setConnectionStatus(true));

      sendAction(new ClientAction(ClientAction.ActionType.INITIALIZE_LEVEL, levelToLoad));

      while (running) {
        try {
          GameStateUpdate update = (GameStateUpdate) in.readObject();
          SwingUtilities.invokeLater(() -> gamePanel.updateStateFromServer(update));
        } catch (ClassNotFoundException e) {
          logger.warn("Received an unknown object type from the server.");
        } catch (SocketException | java.io.EOFException e) {
          logger.warn("Connection to server lost (Socket closed).");
          running = false;
        }
      }
    } catch (UnknownHostException e) {
      logger.error("Server not found at {}:{}", host, port);
      SwingUtilities.invokeLater(() -> {
        JOptionPane.showMessageDialog(gamePanel.getGame(),
                "Could not find the server at " + host + ":" + port + ".\nPlease ensure the server is running and accessible.",
                "Connection Error", JOptionPane.ERROR_MESSAGE);
        gamePanel.getGame().returnToMenu();
      });
    } catch (IOException e) {
      logger.error("Lost connection to the server or could not connect.", e);
      SwingUtilities.invokeLater(() -> {
        JOptionPane.showMessageDialog(gamePanel.getGame(),
                "Lost connection to the server.", "Connection Error", JOptionPane.ERROR_MESSAGE);
        gamePanel.getGame().returnToMenu();
      });
    } finally {
      close();
      SwingUtilities.invokeLater(() -> gamePanel.setConnectionStatus(false));
    }
  }

  public void sendAction(ClientAction action) {
    if (out == null || !running) {
      logger.warn("Not connected to server. Cannot send action: {}", action.type);
      return;
    }
    try {
      out.writeObject(action);
      out.flush();
      out.reset(); // Very important to prevent caching issues with mutable objects
      logger.debug("Sent action to server: {}", action.type);
    } catch (IOException e) {
      logger.error("Failed to send action to server", e);
      close();
    }
  }

  public void close() {
    if (!running) return;
    running = false;
    try {
      if (socket != null && !socket.isClosed()) {
        socket.close();
      }
    } catch (IOException e) {
      // Suppress exception on close
    }
    logger.info("Client connection has been closed.");
  }
}