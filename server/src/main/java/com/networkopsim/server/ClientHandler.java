// ===== File: ClientHandler.java (FINAL CORRECTED with Definitive Stream Initialization) =====
// ===== MODULE: server =====

package com.networkopsim.server;

import com.networkopsim.game.controller.logic.GameEngine;
import com.networkopsim.game.model.core.Port;
import com.networkopsim.game.model.core.System;
import com.networkopsim.game.model.core.Wire;
import com.networkopsim.game.model.state.GameState;
import com.networkopsim.game.net.ClientAction;
import com.networkopsim.game.net.GameStateUpdate;
import com.networkopsim.server.utils.GraphUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Point;
import java.awt.geom.Point2D;
import java.awt.geom.Line2D;
import java.awt.Rectangle;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

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
      // [DEFINITIVE FIX] Server creates its output stream first.
      out = new ObjectOutputStream(socket.getOutputStream());
      // Server creates its input stream second. This will block until the client
      // has created its output stream, completing the handshake.
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
          String validationError = getNetworkValidationErrorMessage(engine);
          if (validationError == null) {
            engine.startSimulation();
          } else {
            logger.warn("Start simulation rejected. Validation error: {}", validationError);
          }
          break;
        case PAUSE_SIMULATION:
          engine.setPaused(true);
          break;
        case RESUME_SIMULATION:
          engine.setPaused(false);
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
          handleDeleteRelayPoint(engine, action);
          break;
        case BUY_ITEM:
          handleBuyItem(engine, action);
          break;
        case PLACE_AERGIA_EFFECT:
          handlePlaceEffect(engine, action, GameState.InteractiveMode.AERGIA_PLACEMENT);
          break;
        case PLACE_ELIPHAS_EFFECT:
          handlePlaceEffect(engine, action, GameState.InteractiveMode.ELIPHAS_PLACEMENT);
          break;
        default:
          logger.warn("Unhandled client action type: {}", action.type);
      }
    }
    server.broadcastState();
  }

  private String getNetworkValidationErrorMessage(GameEngine engine) {
    List<System> systems = engine.getSystems();
    List<Wire> wires = engine.getWires();
    if (!GraphUtils.isNetworkConnected(systems, wires)) return "All systems must be part of a single connected network.";
    if (!GraphUtils.areAllSystemPortsConnected(systems)) return "All ports on every system must be connected.";
    long totalWireLength = 0;
    for (Wire wire : wires) {
      if (isWireIntersectingAnySystem(wire, systems)) return "A wire (ID: " + wire.getId() + ") is passing through a system's body.";
      totalWireLength += wire.getLength();
    }
    if (totalWireLength > engine.getGameState().getMaxWireLengthForLevel()) return "Total wire length exceeds the budget.";
    return null;
  }

  private boolean isWireIntersectingAnySystem(Wire wire, List<System> allSystems) {
    if (wire == null) return false;
    List<Point2D.Double> fullPath = wire.getFullPathPoints();
    if (fullPath.size() < 2) return false;
    System startSystem = wire.getStartPort().getParentSystem();
    System endSystem = wire.getEndPort().getParentSystem();
    for (int i = 0; i < fullPath.size() - 1; i++) {
      Line2D.Double segment = new Line2D.Double(fullPath.get(i), fullPath.get(i + 1));
      for (System sys : allSystems) {
        if (sys == null || sys.equals(startSystem) || sys.equals(endSystem)) continue;
        if (segment.intersects(new Rectangle(sys.getX(), sys.getY(), System.SYSTEM_WIDTH, System.SYSTEM_HEIGHT))) return true;
      }
    }
    return false;
  }

  private void handleCreateWire(GameEngine engine, ClientAction action) {
    int startPortId = action.getInt(0);
    int endPortId = action.getInt(1);
    Optional<Port> startPortOpt = findPortById(engine, startPortId);
    Optional<Port> endPortOpt = findPortById(engine, endPortId);

    if (startPortOpt.isPresent() && endPortOpt.isPresent()) {
      Port startPort = startPortOpt.get();
      Port endPort = endPortOpt.get();
      if (!startPort.isConnected() && !endPort.isConnected()) {
        int wireLength = (int) Math.round(startPort.getPosition().distance(endPort.getPosition()));
        if (engine.getGameState().getRemainingWireLength() >= wireLength) {
          try {
            Wire newWire = new Wire(startPort, endPort);
            engine.getWires().add(newWire);
            engine.getGameState().useWire(wireLength);
            logger.info("Wire created between ports {} and {}", startPortId, endPortId);
          } catch (Exception e) { logger.warn("Server-side wire creation failed: {}", e.getMessage()); }
        } else { logger.warn("Client requested to create wire with insufficient length."); }
      } else { logger.warn("Client requested to create wire on already connected ports."); }
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
      if (wire.getRelayPointsCount() < Wire.MAX_RELAY_POINTS && engine.getGameState().spendCoins(1)) {
        int oldLength = (int)Math.round(wire.getLength());
        wire.addRelayPoint(new Point2D.Double(point.x, point.y));
        int newLength = (int)Math.round(wire.getLength());
        int deltaLength = newLength - oldLength;
        if (deltaLength > 0) engine.getGameState().useWire(deltaLength);
        else if (deltaLength < 0) engine.getGameState().returnWire(-deltaLength);
      }
    }
  }

  private void handleDeleteRelayPoint(GameEngine engine, ClientAction action) {
    int wireId = action.getInt(0);
    Point point = action.getPoint(1);
    Optional<Wire> wireOpt = engine.getWires().stream().filter(w -> w.getId() == wireId).findFirst();
    if(wireOpt.isPresent()) {
      Wire wire = wireOpt.get();
      Optional<Wire.RelayPoint> relayOpt = wire.getRelayPoints().stream()
              .filter(rp -> rp.getPosition().distanceSq(point.x, point.y) < Wire.RELAY_CLICK_RADIUS * Wire.RELAY_CLICK_RADIUS)
              .findFirst();

      if (relayOpt.isPresent()) {
        Wire.RelayPoint relayToDelete = relayOpt.get();
        int oldLength = (int)Math.round(wire.getLength());
        wire.removeRelayPoint(relayToDelete);
        int newLength = (int)Math.round(wire.getLength());
        engine.getGameState().returnWire(oldLength - newLength);
        engine.getGameState().addCoins(1);
      }
    }
  }

  private void handleBuyItem(GameEngine engine, ClientAction action) {
    String itemName = action.getString(0);
    logger.info("Client requested to buy item: {}", itemName);
    GameState gs = engine.getGameState();

    switch(itemName) {
      case "O' Atar": if (gs.spendCoins(3)) engine.activateAtar(); break;
      case "O' Airyaman": if (gs.spendCoins(4)) engine.activateAiryaman(); break;
      case "O' Anahita": if (gs.spendCoins(5)) engine.activateAnahita(); break;
      case "Speed Limiter": if (gs.spendCoins(7)) engine.activateSpeedLimiter(); break;
      case "Emergency Brake": if (gs.spendCoins(8)) engine.activateEmergencyBrake(); break;
      case "Scroll of Aergia":
        if (gs.spendCoins(10) && gs.getAergiaCooldownUntil() <= engine.getSimulationTimeElapsedMs()) {
          gs.setCurrentInteractiveMode(GameState.InteractiveMode.AERGIA_PLACEMENT);
        }
        break;
      case "Scroll of Eliphas":
        if (gs.spendCoins(20)) {
          gs.setCurrentInteractiveMode(GameState.InteractiveMode.ELIPHAS_PLACEMENT);
        }
        break;
      case "Scroll of Sisyphus":
        if (gs.spendCoins(15)) {
          gs.setCurrentInteractiveMode(GameState.InteractiveMode.SISYPHUS_DRAG);
        }
        break;
    }
  }

  private void handlePlaceEffect(GameEngine engine, ClientAction action, GameState.InteractiveMode mode) {
    GameState gs = engine.getGameState();
    if (gs.getCurrentInteractiveMode() != mode) {
      logger.warn("Client tried to place {} effect while not in the correct mode.", mode.name());
      return;
    }

    int wireId = action.getInt(0);
    Point point = action.getPoint(1);

    long duration = (mode == GameState.InteractiveMode.AERGIA_PLACEMENT) ? 20000L : 30000L;
    long expiry = engine.getSimulationTimeElapsedMs() + duration;

    gs.getActiveWireEffects().add(new GameState.ActiveWireEffect(mode, new Point2D.Double(point.x, point.y), wireId, expiry));

    if (mode == GameState.InteractiveMode.AERGIA_PLACEMENT) {
      gs.setAergiaCooldownUntil(engine.getSimulationTimeElapsedMs() + 10000L);
    }

    gs.setCurrentInteractiveMode(GameState.InteractiveMode.NONE);
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

  public Socket getSocket() { return socket; }

  public void close() {
    if (!running) return;
    running = false;
    try {
      if (socket != null && !socket.isClosed()) socket.close();
    } catch (IOException e) { /* Suppress */ }
  }
}