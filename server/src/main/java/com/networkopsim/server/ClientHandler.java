// ===== File: ClientHandler.java (FINAL COMPLETE VERSION) =====
// ===== MODULE: server =====

package com.networkopsim.server;

import com.networkopsim.game.controller.logic.GameEngine;
import com.networkopsim.game.model.core.Port;
import com.networkopsim.game.model.core.Wire;
import com.networkopsim.game.net.ClientAction;
import com.networkopsim.game.net.GameStateUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Point;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.util.Optional;
import java.util.stream.Stream;
import java.awt.geom.Point2D;


public class ClientHandler implements Runnable {
  private static final Logger logger = LoggerFactory.getLogger(ClientHandler.class);
  private final Socket socket;
  private final GameServer server;
  private ObjectOutputStream out;
  private ObjectInputStream in;
  private volatile boolean running = true;

  public ClientHandler(Socket socket, GameServer server) {
    this.socket = socket;
    this.server = server;
  }

  @Override
  public void run() {
    try {
      out = new ObjectOutputStream(socket.getOutputStream());
      in = new ObjectInputStream(socket.getInputStream());

      while (running) {
        try {
          ClientAction action = (ClientAction) in.readObject();
          handleClientAction(action);
        } catch (ClassNotFoundException e) {
          logger.warn("Received unknown object from client {}", socket.getInetAddress());
        } catch (SocketException | java.io.EOFException e) {
          logger.info("Client {} connection was closed.", socket.getInetAddress());
          running = false;
        }
      }
    } catch (IOException e) {
      if (running) {
        logger.error("IO Error with client handler for {}", socket.getInetAddress(), e);
      }
    } finally {
      close();
      server.removeClient(this);
    }
  }

  private void handleClientAction(ClientAction action) {
    GameEngine engine = server.getGameEngine();
    logger.debug("Received action from client: {}", action.type);

    synchronized (engine) {
      switch (action.type) {
        case INITIALIZE_LEVEL:
          server.reinitializeGameForLevel(action.getInt(0));
          break;
        case START_SIMULATION:
          engine.startSimulation();
          break;
        case PAUSE_SIMULATION:
          engine.setPaused(true);
          break;
        case RESUME_SIMULATION:
          engine.setPaused(false);
          break;
        case RETURN_TO_MENU:
          // For now, server just stops simulation. A more complex system might handle rooms.
          engine.stopSimulation();
          break;
        case CREATE_WIRE:
          handleCreateWire(engine, action);
          break;
        case DELETE_WIRE:
          handleDeleteWire(engine, action);
          break;
        case ADD_RELAY_POINT:
          handleAddRelayPoint(engine, action);
          break;
        case DELETE_RELAY_POINT:
          // This is more complex and might be simplified for now.
          logger.warn("Delete Relay Point action received but not implemented on server.");
          break;
        case BUY_ITEM:
          handleBuyItem(engine, action);
          break;
        default:
          logger.warn("Unhandled client action type: {}", action.type);
      }
    }
    server.broadcastState();
  }

  private void handleCreateWire(GameEngine engine, ClientAction action) {
    int startPortId = action.getInt(0);
    int endPortId = action.getInt(1);
    Optional<Port> startPortOpt = findPortById(engine, startPortId);
    Optional<Port> endPortOpt = findPortById(engine, endPortId);

    if (startPortOpt.isPresent() && endPortOpt.isPresent()) {
      Port startPort = startPortOpt.get();
      Port endPort = endPortOpt.get();

      // Server-side validation
      if (!startPort.isConnected() && !endPort.isConnected()) {
        int wireLength = (int) Math.round(startPort.getPosition().distance(endPort.getPosition()));
        if (engine.getGameState().getRemainingWireLength() >= wireLength) {
          try {
            Wire newWire = new Wire(startPort, endPort);
            engine.getWires().add(newWire);
            engine.getGameState().useWire(wireLength);
            logger.info("Wire created between ports {} and {}", startPortId, endPortId);
          } catch (Exception e) {
            logger.warn("Server-side wire creation failed: {}", e.getMessage());
          }
        } else {
          logger.warn("Client requested to create wire with insufficient length.");
        }
      } else {
        logger.warn("Client requested to create wire on already connected ports.");
      }
    }
  }

  private void handleDeleteWire(GameEngine engine, ClientAction action) {
    int wireId = action.getInt(0);
    Optional<Wire> wireOpt = engine.getWires().stream().filter(w -> w.getId() == wireId).findFirst();
    if (wireOpt.isPresent()) {
      Wire wireToDelete = wireOpt.get();
      engine.getWires().remove(wireToDelete);
      int returnedLength = (int) Math.round(wireToDelete.getLength());
      engine.getGameState().returnWire(returnedLength);
      wireToDelete.destroy();
      logger.info("Wire {} deleted by client request.", wireId);
    }
  }

  private void handleAddRelayPoint(GameEngine engine, ClientAction action) {
    int wireId = action.getInt(0);
    Point point = action.getPoint(1);
    Optional<Wire> wireOpt = engine.getWires().stream().filter(w -> w.getId() == wireId).findFirst();
    if(wireOpt.isPresent()) {
      Wire wire = wireOpt.get();
      // TODO: Add cost and max relay point checks from original GamePanel
      wire.addRelayPoint(new Point2D.Double(point.x, point.y));
    }
  }

  private void handleBuyItem(GameEngine engine, ClientAction action) {
    String itemName = action.getString(0);
    // TODO: Implement item logic on the server. This requires tracking active powerups
    // in GameState or GameEngine.
    logger.info("Client requested to buy item: {}", itemName);
  }

  private Optional<Port> findPortById(GameEngine engine, int portId) {
    return engine.getSystems().stream()
            .flatMap(s -> Stream.concat(s.getInputPorts().stream(), s.getOutputPorts().stream()))
            .filter(p -> p.getId() == portId)
            .findFirst();
  }

  public void sendUpdate(GameStateUpdate update) {
    if (!running || socket.isClosed() || out == null) return;
    try {
      out.writeObject(update);
      out.flush();
      out.reset();
    } catch (SocketException e) {
      close();
    } catch (IOException e) {
      logger.error("Failed to send update to client {}", socket.getInetAddress());
      close();
    }
  }

  public Socket getSocket() {
    return socket;
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
  }
}