// ===== File: GamePanel.java (FINAL COMPLETE VERSION for Client) =====
// ===== MODULE: client =====

package com.networkopsim.game.view.panels;

import com.networkopsim.game.controller.core.NetworkGame;
import com.networkopsim.game.controller.input.GameInputHandler;
import com.networkopsim.game.model.core.Packet;
import com.networkopsim.game.model.core.Port;
import com.networkopsim.game.model.core.System;
import com.networkopsim.game.model.core.Wire;
import com.networkopsim.game.model.enums.NetworkEnums;
import com.networkopsim.game.model.state.GameState;
import com.networkopsim.game.net.ClientAction;
import com.networkopsim.game.net.GameStateUpdate;
import com.networkopsim.game.view.rendering.GameRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class GamePanel extends JPanel {
  private static final Logger logger = LoggerFactory.getLogger(GamePanel.class);

  private static final int GAME_TICK_MS = 16;
  private static final Color BACKGROUND_COLOR = new Color(15, 15, 20);
  public static final Color DEFAULT_WIRING_COLOR = Color.LIGHT_GRAY;
  public static final Color VALID_WIRING_COLOR_TARGET = new Color(0, 200, 0);
  public static final Color INVALID_WIRING_COLOR = new Color(220, 0, 0);

  private final NetworkGame game;
  private GameState gameState;
  private final GameRenderer gameRenderer;
  private final GameInputHandler gameInputHandler;
  private final Timer renderTimer;

  private List<System> systems = Collections.synchronizedList(new ArrayList<>());
  private List<Wire> wires = Collections.synchronizedList(new ArrayList<>());
  private List<Packet> packets = Collections.synchronizedList(new ArrayList<>());
  private long simulationTimeElapsedMs = 0;
  private volatile boolean isSimulationRunning = false;
  private volatile boolean isSimulationPaused = false;
  private volatile boolean levelComplete = false;
  private volatile boolean gameOver = false;
  private volatile boolean isConnected = false;

  private volatile boolean showHUD = true;
  private Port selectedOutputPort = null;
  private final Point mouseDragPos = new Point();
  private boolean wireDrawingMode = false;
  private Color currentWiringColor = DEFAULT_WIRING_COLOR;

  public enum InteractiveMode { NONE } // Simplified for now
  private volatile InteractiveMode currentInteractiveMode = InteractiveMode.NONE;

  public GamePanel(NetworkGame game) {
    this.game = game;
    this.gameState = game.getGameState();
    this.gameRenderer = new GameRenderer(this, null, this.gameState);
    this.gameInputHandler = new GameInputHandler(this, game);

    setBackground(BACKGROUND_COLOR);
    setPreferredSize(new Dimension(NetworkGame.WINDOW_WIDTH, NetworkGame.WINDOW_HEIGHT));
    setFocusable(true);
    addKeyListener(gameInputHandler);
    addMouseListener(gameInputHandler);
    addMouseMotionListener(gameInputHandler);
    ToolTipManager.sharedInstance().registerComponent(this);

    renderTimer = new Timer(GAME_TICK_MS, e -> repaint());
  }

  public void initializeForNetworkGame(int level) {
    this.systems.clear();
    this.wires.clear();
    this.packets.clear();
    this.simulationTimeElapsedMs = 0;
    this.isSimulationRunning = false;
    this.isSimulationPaused = false;
    this.levelComplete = false;
    this.gameOver = false;
    this.isConnected = false;
    cancelAllInteractiveModes();
    if (!renderTimer.isRunning()) {
      renderTimer.start();
    }
    repaint();
  }

  public void updateStateFromServer(GameStateUpdate update) {
    this.gameState = update.gameState;
    this.systems = Collections.synchronizedList(update.systems);
    this.wires = Collections.synchronizedList(update.wires);
    this.packets = Collections.synchronizedList(update.packets);
    this.simulationTimeElapsedMs = update.simulationTimeMs;
    this.isSimulationRunning = update.isSimulationRunning;
    this.isSimulationPaused = update.isSimulationPaused;
    this.levelComplete = update.isLevelComplete;
    this.gameOver = update.isGameOver;

    rebuildTransientReferences();
    this.gameRenderer.setGameState(this.gameState);
    this.gameRenderer.setServerState(this.systems, this.wires, this.packets);
  }

  private void rebuildTransientReferences() {
    if (systems == null || wires == null) return;
    Map<Integer, System> systemMap = systems.stream().collect(Collectors.toMap(System::getId, s -> s));
    for (Wire w : wires) {
      w.rebuildTransientReferences(systemMap);
      for(Wire.RelayPoint rp : w.getRelayPoints()) {
        rp.setParentWire(w);
      }
    }
  }

  public void setConnectionStatus(boolean connected) {
    this.isConnected = connected;
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    if (!isConnected && !isSimulationStarted()) {
      drawConnectionMessage(g, "Connecting to server...");
    } else {
      gameRenderer.render(g);
    }
  }

  private void drawConnectionMessage(Graphics g, String message) {
    Graphics2D g2d = (Graphics2D) g;
    g2d.setColor(getBackground());
    g2d.fillRect(0, 0, getWidth(), getHeight());
    g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    g2d.setColor(Color.WHITE);
    g2d.setFont(new Font("Arial", Font.BOLD, 24));
    FontMetrics fm = g2d.getFontMetrics();
    int textWidth = fm.stringWidth(message);
    g2d.drawString(message, (getWidth() - textWidth) / 2, getHeight() / 2);
  }

  public void attemptStartSimulation() {
    if (game.getGameClient() != null) {
      game.getGameClient().sendAction(new ClientAction(ClientAction.ActionType.START_SIMULATION));
    }
  }

  public void pauseGame(boolean pause) {
    if (game.getGameClient() != null) {
      ClientAction.ActionType type = pause ? ClientAction.ActionType.PAUSE_SIMULATION : ClientAction.ActionType.RESUME_SIMULATION;
      game.getGameClient().sendAction(new ClientAction(type));
    }
  }

  public void attemptWireCreation(Port startPort, Port endPort) {
    if (game.getGameClient() != null) {
      game.getGameClient().sendAction(new ClientAction(ClientAction.ActionType.CREATE_WIRE, startPort.getId(), endPort.getId()));
    }
  }

  public void deleteWireRequest(Wire wireToDelete) {
    if (game.getGameClient() != null && wireToDelete != null) {
      game.getGameClient().sendAction(new ClientAction(ClientAction.ActionType.DELETE_WIRE, wireToDelete.getId()));
    }
  }

  public void addRelayPointRequest(Wire wire, Point p) {
    if(game.getGameClient() != null && wire != null) {
      game.getGameClient().sendAction(new ClientAction(ClientAction.ActionType.ADD_RELAY_POINT, wire.getId(), p));
    }
  }

  public void deleteRelayPointRequest(Wire.RelayPoint relayPoint) {
    if (game.getGameClient() != null && relayPoint != null) {
      Point2D.Double pos = relayPoint.getPosition();
      Point simplePos = new Point((int)pos.x, (int)pos.y);
      game.getGameClient().sendAction(new ClientAction(ClientAction.ActionType.DELETE_RELAY_POINT, relayPoint.getParentWire().getId(), simplePos));
    }
  }

  public void startWiringMode(Port startPort, Point currentMousePos) { if (!wireDrawingMode && startPort != null && !isSimulationStarted() && startPort.getType() == NetworkEnums.PortType.OUTPUT && !startPort.isConnected()) { this.selectedOutputPort = startPort; this.mouseDragPos.setLocation(currentMousePos); this.wireDrawingMode = true; this.currentWiringColor = DEFAULT_WIRING_COLOR; setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR)); repaint(); } }
  public void updateDragPos(Point currentMousePos) { if (wireDrawingMode) this.mouseDragPos.setLocation(currentMousePos); }
  public void updateWiringPreview(Point currentMousePos) { if (!wireDrawingMode || selectedOutputPort == null || selectedOutputPort.getPosition() == null) { this.currentWiringColor = DEFAULT_WIRING_COLOR; repaint(); return; } Point startPos = selectedOutputPort.getPosition(); double wireLength = startPos.distance(currentMousePos); if (gameState != null && gameState.getRemainingWireLength() < wireLength) { this.currentWiringColor = INVALID_WIRING_COLOR; repaint(); return; } Port targetPort = findPortAt(currentMousePos); if (targetPort != null) { if (Objects.equals(targetPort.getParentSystem(), selectedOutputPort.getParentSystem()) || targetPort.getType() != NetworkEnums.PortType.INPUT || targetPort.isConnected()) { this.currentWiringColor = INVALID_WIRING_COLOR; } else { this.currentWiringColor = VALID_WIRING_COLOR_TARGET; } } else { this.currentWiringColor = DEFAULT_WIRING_COLOR; } repaint(); }
  public void cancelWiring() { if(wireDrawingMode) { selectedOutputPort = null; wireDrawingMode = false; currentWiringColor = DEFAULT_WIRING_COLOR; setCursor(Cursor.getDefaultCursor()); repaint(); } }
  public Port findPortAt(Point p) { for(System s : systems) { Port port = s.getPortAt(p); if (port != null) return port; } return null; }
  public Wire findWireAt(Point p, double clickThreshold) { if (p == null) return null; double clickThresholdSq = clickThreshold * clickThreshold; Wire closestWire = null; double minDistanceSq = Double.MAX_VALUE; for (Wire w : wires) { if (w == null) continue; List<Point2D.Double> path = w.getFullPathPoints(); for (int i = 0; i < path.size() - 1; i++) { double distSq = Line2D.ptSegDistSq(path.get(i).x, path.get(i).y, path.get(i+1).x, path.get(i+1).y, p.x, p.y); if (distSq < minDistanceSq) { minDistanceSq = distSq; closestWire = w; } } } return (closestWire != null && minDistanceSq < clickThresholdSq) ? closestWire : null; }
  public void clearAllHoverStates() { for(Wire w : wires) w.clearHoverStates(); }
  public void cancelAllInteractiveModes() { this.currentInteractiveMode = InteractiveMode.NONE; /* any other UI cleanup */ }
  public void toggleHUD() { showHUD = !showHUD; repaint(); }

  // GETTERS FOR UI STATE
  public List<System> getSystems() { return systems; }
  public List<Wire> getWires() { return wires; }
  public List<Packet> getPackets() { return packets; }
  public GameState getGameState() { return gameState; }
  public NetworkGame getGame() { return game; }
  public long getSimulationTimeElapsedMs() { return simulationTimeElapsedMs; }
  public boolean isGameRunning() { return isSimulationRunning; }
  public boolean isGamePaused() { return isSimulationPaused; }
  public boolean isSimulationStarted() { return isSimulationRunning || isSimulationPaused; }
  public boolean isLevelComplete() { return levelComplete; }
  public boolean isGameOver() { return gameOver; }
  public boolean isWireDrawingMode() { return wireDrawingMode; }
  public Port getSelectedOutputPort() { return selectedOutputPort; }
  public Point getMouseDragPos() { return mouseDragPos; }
  public Color getCurrentWiringColor() { return currentWiringColor; }
  public boolean isShowHUD() { return showHUD; }
  public InteractiveMode getCurrentInteractiveMode() { return currentInteractiveMode; }
}