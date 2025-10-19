// FILE: /NetworkOpSim-Multiplayer/NetworkOpSim-Client/src/main/java/com/networkopsim/client/view/panels/GamePanel.java
// ================================================================================

package com.networkopsim.client.view.panels;

import com.networkopsim.client.core.NetworkGame;
import com.networkopsim.client.input.GameInputHandler;
import com.networkopsim.client.offline.OfflineGameManager;
import com.networkopsim.client.view.rendering.GameRenderer;
import com.networkopsim.shared.dto.GameStateDTO;
import com.networkopsim.shared.dto.SystemDTO;
import com.networkopsim.shared.dto.SystemDTO.PortDTO;
import com.networkopsim.shared.dto.WireDTO;
import com.networkopsim.shared.model.NetworkEnums;
import com.networkopsim.shared.net.UserCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class GamePanel extends JPanel {
    private static final Logger log = LoggerFactory.getLogger(GamePanel.class);

    // Constants
    private static final int GAME_TICK_MS = 16;
    private static final Color BACKGROUND_COLOR = new Color(15, 15, 20);
    public static final Color DEFAULT_WIRING_COLOR = Color.LIGHT_GRAY;
    public static final Color VALID_WIRING_COLOR_TARGET = new Color(0, 200, 0);
    public static final Color INVALID_WIRING_COLOR = new Color(220, 0, 0);

    // Core Components
    private final NetworkGame game;
    private final GameRenderer gameRenderer;
    private final GameInputHandler gameInputHandler;
    private final Timer gameLoopTimer;

    // State Management
    private boolean isOfflineMode = false;
    private boolean isOnlineMultiplayer = false;
    private OfflineGameManager offlineGameManager;
    private boolean wantsToOpenStore = false;

    // Interactive UI State (Client-side only)
    private PortDTO selectedOutputPort = null;
    private final Point mouseDragPos = new Point();
    private boolean wireDrawingMode = false;
    private Color currentWiringColor = DEFAULT_WIRING_COLOR;

    private Point2D.Double draggedRelay = null;
    private WireDTO draggedWire = null;
    private int draggedRelayOriginalIndex = -1;

    private final JButton readyButton;
    private final JPopupMenu controllableSystemPopup;

    public GamePanel(NetworkGame game) {
        this.game = game;
        this.gameRenderer = new GameRenderer(this);
        this.gameInputHandler = new GameInputHandler(this, game);
        this.setLayout(null);

        setBackground(BACKGROUND_COLOR);
        setPreferredSize(new Dimension(NetworkGame.WINDOW_WIDTH, NetworkGame.WINDOW_HEIGHT));
        setFocusable(true);
        addKeyListener(gameInputHandler);
        addMouseListener(gameInputHandler);
        addMouseMotionListener(gameInputHandler);
        ToolTipManager.sharedInstance().registerComponent(this);

        readyButton = new JButton("READY");
        readyButton.setFont(new Font("Arial", Font.BOLD, 24));
        readyButton.setBackground(new Color(60, 150, 60));
        readyButton.setForeground(Color.BLACK);
        readyButton.setFocusPainted(false);
        readyButton.setBounds((NetworkGame.WINDOW_WIDTH - 200) / 2, NetworkGame.WINDOW_HEIGHT - 100, 200, 50);
        readyButton.setVisible(false);
        readyButton.addActionListener(e -> sendReadyCommand());
        add(readyButton);

        controllableSystemPopup = new JPopupMenu();

        gameLoopTimer = new Timer(GAME_TICK_MS, e -> gameTick());
        gameLoopTimer.setRepeats(true);
    }

    public void initializeLevelOnline(int level, boolean isMultiplayer) {
        log.info("Initializing GamePanel for ONLINE mode, level {}. Multiplayer: {}", level, isMultiplayer);
        this.isOfflineMode = false;
        this.isOnlineMultiplayer = isMultiplayer;
        this.offlineGameManager = null;
        resetPanelState();
        gameLoopTimer.start();
        requestFocusInWindow();
    }

    public void initializeLevelOffline(int level) {
        log.info("Initializing GamePanel for OFFLINE mode, level {}.", level);
        this.isOfflineMode = true;
        this.isOnlineMultiplayer = false;
        this.offlineGameManager = new OfflineGameManager(this);
        this.offlineGameManager.initializeLevel(level);
        resetPanelState();
        gameLoopTimer.start();
        requestFocusInWindow();
    }

    private void resetPanelState() {
        wireDrawingMode = false;
        selectedOutputPort = null;
        currentWiringColor = DEFAULT_WIRING_COLOR;
        wantsToOpenStore = false;
        endRelayDrag();
        readyButton.setVisible(false);
        readyButton.setEnabled(true);
        readyButton.setText("READY");
        controllableSystemPopup.setVisible(false);
        repaint();
    }

    private void gameTick() {
        if (isOfflineMode) {
            if (offlineGameManager != null && offlineGameManager.isSimulationRunning()) {
                offlineGameManager.gameTick(GAME_TICK_MS);
            }
        } else {
            GameStateDTO state = getDisplayState();
            if (state != null && wantsToOpenStore && state.isSimulationPaused()) {
                wantsToOpenStore = false; // Consume the request
                SwingUtilities.invokeLater(() -> game.showStore());
                game.getNetworkManager().sendCommand(UserCommand.playerInStore(true));
            }
            updateMultiplayerUI();
        }
        repaint();
    }

    private void updateMultiplayerUI() {
        GameStateDTO state = getDisplayState();
        if (state == null || !isOnlineMultiplayer) {
            readyButton.setVisible(false);
            return;
        }

        String phase = state.getGamePhase();
        if ("PRE_BUILD".equals(phase) || "OVERTIME_BUILD".equals(phase)) {
            readyButton.setVisible(true);
            if (state.isLocalPlayerReady()) {
                readyButton.setEnabled(false);
                readyButton.setText("WAITING...");
                readyButton.setBackground(Color.GRAY);
                readyButton.setForeground(Color.BLACK);
            } else {
                readyButton.setEnabled(true);
                readyButton.setText("READY");
                readyButton.setBackground(new Color(60, 150, 60));
                readyButton.setForeground(Color.BLACK);
            }
        } else {
            readyButton.setVisible(false);
        }
    }


    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        GameStateDTO stateToRender = getDisplayState();
        if (stateToRender != null) {
            gameRenderer.render(g, stateToRender);

            if (wireDrawingMode && selectedOutputPort != null) {
                drawWiringLine(g);
            }

            if (isRelayDragging()) {
                drawRelayDragPreview(g);
            }

        } else {
            g.setColor(Color.WHITE);
            g.setFont(new Font("Arial", Font.BOLD, 20));
            String msg = isOfflineMode ? "Loading Offline Level..." : "Waiting for game state from server...";
            FontMetrics fm = g.getFontMetrics();
            g.drawString(msg, (getWidth() - fm.stringWidth(msg)) / 2, getHeight() / 2);
        }
    }

    private void drawWiringLine(Graphics g) {
        Graphics2D g2d = (Graphics2D) g;
        if (selectedOutputPort.getPosition() == null || mouseDragPos == null) return;
        g2d.setColor(currentWiringColor);
        g2d.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, new float[]{9f, 5f}, 0.0f));
        g2d.drawLine(selectedOutputPort.getPosition().x, selectedOutputPort.getPosition().y, mouseDragPos.x, mouseDragPos.y);
    }

    private void drawRelayDragPreview(Graphics g) {
        Graphics2D g2d = (Graphics2D) g;
        g2d.setColor(Color.YELLOW);
        g2d.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, new float[]{5f, 5f}, 0.0f));
        List<Point2D.Double> path = draggedWire.getFullPathPoints();
        path.set(draggedRelayOriginalIndex + 1, new Point2D.Double(mouseDragPos.x, mouseDragPos.y));
        for (int i = 0; i < path.size() - 1; i++) {
            g2d.drawLine((int)path.get(i).x, (int)path.get(i).y, (int)path.get(i+1).x, (int)path.get(i+1).y);
        }
    }

    public GameStateDTO getDisplayState() {
        if (isOfflineMode) {
            return (offlineGameManager != null) ? offlineGameManager.getCurrentGameStateDTO() : null;
        } else {
            return game.getNetworkManager().getLatestGameState();
        }
    }

    public void attemptStartSimulation() {
        if (isOfflineMode) {
            if (offlineGameManager != null) {
                String validationError = offlineGameManager.getNetworkValidationErrorMessage();
                if (validationError == null) {
                    offlineGameManager.startSimulation();
                } else {
                    JOptionPane.showMessageDialog(this, "Network Validation Failed:\n" + validationError, "Network Not Ready", JOptionPane.WARNING_MESSAGE);
                }
            }
        } else {
            game.getNetworkManager().sendCommand(UserCommand.simpleCommand(UserCommand.CommandType.START_SIMULATION));
        }
    }

    private void sendReadyCommand() {
        if (isOnlineMultiplayer && !isOfflineMode) {
            log.info("Sending PLAYER_READY command to server.");
            game.getNetworkManager().sendCommand(UserCommand.simpleCommand(UserCommand.CommandType.PLAYER_READY));
        }
    }

    public void showControllableSystemMenu(SystemDTO system, Point location) {
        GameStateDTO state = getDisplayState();
        if (state == null || !system.isControllable() || !state.isSimulationRunning()) return;

        controllableSystemPopup.removeAll();
        int localPlayerId = state.getLocalPlayerId();

        long systemCooldown = system.getSystemCooldownRemainingMs();
        if (systemCooldown > 0) {
            JMenuItem cooldownItem = new JMenuItem(String.format("System Cooldown: %.1fs", systemCooldown / 1000.0));
            cooldownItem.setEnabled(false);
            controllableSystemPopup.add(cooldownItem);
        } else {
            Map<NetworkEnums.PacketType, Integer> myAmmo = system.getAllAmmoForPlayer(localPlayerId);
            boolean hasAmmo = false;
            for (Map.Entry<NetworkEnums.PacketType, Integer> entry : myAmmo.entrySet()) {
                NetworkEnums.PacketType packetType = entry.getKey();
                int count = entry.getValue();
                if (packetType == NetworkEnums.PacketType.SECRET) continue;
                if (count > 0) {
                    hasAmmo = true;
                    long packetCooldown = system.getPacketCooldownForPlayer(localPlayerId, packetType);
                    String text = String.format("%s (%d left)", packetType.name(), count);
                    JMenuItem menuItem = new JMenuItem(text);
                    if (packetCooldown > 0) {
                        menuItem.setText(String.format("%s (CD: %.1fs)", text, packetCooldown / 1000.0));
                        menuItem.setEnabled(false);
                    } else {
                        menuItem.addActionListener(e -> deployPacket(system.getId(), packetType));
                    }
                    controllableSystemPopup.add(menuItem);
                }
            }
            if (!hasAmmo) {
                JMenuItem noAmmoItem = new JMenuItem("No ammo available");
                noAmmoItem.setEnabled(false);
                controllableSystemPopup.add(noAmmoItem);
            }
        }
        controllableSystemPopup.show(this, location.x, location.y);
    }

    private void deployPacket(int systemId, NetworkEnums.PacketType packetType) {
        if (isOnlineMultiplayer && !isOfflineMode) {
            log.info("Requesting deployment of {} from system {}", packetType, systemId);
            game.getNetworkManager().sendCommand(UserCommand.deployPacketFromReference(systemId, packetType));
        }
    }

    public void pauseGame(boolean pause) {
        if (isOfflineMode) {
            if (offlineGameManager != null) {
                offlineGameManager.setPaused(pause);
            }
        } else {
            GameStateDTO state = getDisplayState();
            if (state == null) return;
            // Prevent unpausing if the opponent paused the game
            if (!pause && state.isSimulationPaused() && state.getPausedByPlayerId() != 0 && state.getPausedByPlayerId() != state.getLocalPlayerId()) {
                game.showTemporaryMessage("Cannot resume. Game was paused by your opponent.", Color.ORANGE, 3000);
                return;
            }
            UserCommand.CommandType type = pause ? UserCommand.CommandType.PAUSE_SIMULATION : UserCommand.CommandType.RESUME_SIMULATION;
            game.getNetworkManager().sendCommand(UserCommand.simpleCommand(type));
        }
    }

    public void requestOpenStore() {
        if (!isGameRunning()) return;

        if (isOfflineMode) {
            if (!isGamePaused()) {
                pauseGame(true);
            }
            SwingUtilities.invokeLater(game::showStore);
        } else {
            if (!isGamePaused()) {
                wantsToOpenStore = true;
                pauseGame(true);
            } else {
                wantsToOpenStore = false;
                SwingUtilities.invokeLater(game::showStore);
                game.getNetworkManager().sendCommand(UserCommand.playerInStore(true));
            }
        }
    }

    public void closeStore() {
        if (isOfflineMode) {
            if (isGamePaused()) {
                pauseGame(false);
            }
        } else {
            game.getNetworkManager().sendCommand(UserCommand.playerInStore(false));
            // Optional: immediately request unpause
            pauseGame(false);
        }
    }

    public void attemptWireCreation(PortDTO startPort, PortDTO endPort) {
        if (isOfflineMode) {
            if (offlineGameManager != null) {
                offlineGameManager.createWire(startPort.getId(), endPort.getId());
            }
        } else {
            game.getNetworkManager().sendCommand(UserCommand.createWire(startPort.getId(), endPort.getId()));
        }
    }

    public void deleteWireRequest(int wireId) {
        if (isOfflineMode) {
            if (offlineGameManager != null) {
                offlineGameManager.deleteWire(wireId);
            }
        } else {
            game.getNetworkManager().sendCommand(UserCommand.deleteWire(wireId));
        }
    }

    public void purchaseItem(String itemName) {
        if (isOfflineMode) {
            if (offlineGameManager != null) {
                offlineGameManager.purchaseItem(itemName);
            }
        } else {
            game.getNetworkManager().sendCommand(UserCommand.purchaseItem(itemName));
        }
    }

    public void addRelayPointRequest(int wireId, Point position) {
        if (isOfflineMode) {
            if (offlineGameManager != null) {
                offlineGameManager.addRelayPoint(wireId, position);
            }
        } else {
            game.getNetworkManager().sendCommand(UserCommand.addRelayPoint(wireId, position));
        }
    }

    public void moveRelayPointRequest(int wireId, int relayIndex, Point newPosition) {
        if (isOfflineMode) {
            if (offlineGameManager != null) {
                offlineGameManager.moveRelayPoint(wireId, relayIndex, newPosition);
            }
        } else {
            game.getNetworkManager().sendCommand(UserCommand.moveRelayPoint(wireId, relayIndex, newPosition));
        }
    }


    public void sabotageRequest(int targetSystemId) {
        if (!isOfflineMode && isOnlineMultiplayer) {
            log.info("Requesting sabotage on system ID {}", targetSystemId);
            game.getNetworkManager().sendCommand(UserCommand.controlReferenceSystem(targetSystemId));
        } else {
            log.warn("Sabotage request ignored in non-multiplayer mode.");
        }
    }

    public void stopSimulation(boolean isForcedDisconnect) {
        if (gameLoopTimer.isRunning()) {
            gameLoopTimer.stop();
        }
        if (isOfflineMode && offlineGameManager != null) {
            offlineGameManager.stopSimulation();
        } else {
            if (!isForcedDisconnect) {
                game.getNetworkManager().sendCommand(UserCommand.simpleCommand(UserCommand.CommandType.STOP_SIMULATION));
            }
        }
        log.info("Simulation and render loop stopped on GamePanel.");
    }

    public void startWiringMode(PortDTO startPort, Point currentMousePos) {
        if (!wireDrawingMode && startPort != null && startPort.getType() == NetworkEnums.PortType.OUTPUT && !isPortOccupied(startPort.getId())) {
            this.selectedOutputPort = startPort;
            this.mouseDragPos.setLocation(currentMousePos);
            this.wireDrawingMode = true;
            this.currentWiringColor = DEFAULT_WIRING_COLOR;
            setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
            repaint();
        }
    }

    public void updateWiringPreview(Point currentMousePos) {
        if (!wireDrawingMode || selectedOutputPort == null) return;
        this.mouseDragPos.setLocation(currentMousePos);

        GameStateDTO state = getDisplayState();
        if (state == null) return;

        PortDTO targetPort = findPortAt(currentMousePos);

        if (targetPort != null) {
            if (targetPort.getType() != NetworkEnums.PortType.INPUT ||
                    targetPort.getParentSystemId() == selectedOutputPort.getParentSystemId() ||
                    isPortOccupied(targetPort.getId())) {
                this.currentWiringColor = INVALID_WIRING_COLOR;
            } else {
                this.currentWiringColor = VALID_WIRING_COLOR_TARGET;
            }
        } else {
            this.currentWiringColor = DEFAULT_WIRING_COLOR;
        }
        repaint();
    }

    public void cancelWiring() {
        if (wireDrawingMode) {
            selectedOutputPort = null;
            wireDrawingMode = false;
            currentWiringColor = DEFAULT_WIRING_COLOR;
            setCursor(Cursor.getDefaultCursor());
            repaint();
        }
    }

    public void startRelayDrag(WireDTO wire, Point2D.Double relayPoint, int index) {
        if (isRelayDragging()) return;
        this.draggedWire = wire;
        this.draggedRelay = relayPoint;
        this.draggedRelayOriginalIndex = index;
        setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
    }

    public void updateRelayDrag(Point currentMousePos) {
        if (!isRelayDragging()) return;
        this.mouseDragPos.setLocation(currentMousePos);
        repaint();
    }

    public void endRelayDrag() {
        if (isRelayDragging()) {
            moveRelayPointRequest(draggedWire.getId(), draggedRelayOriginalIndex, mouseDragPos);
        }
        this.draggedWire = null;
        this.draggedRelay = null;
        this.draggedRelayOriginalIndex = -1;
        setCursor(Cursor.getDefaultCursor());
        repaint();
    }

    public boolean isRelayDragging() {
        return draggedWire != null && draggedRelay != null;
    }

    private boolean isPortOccupied(int portId) {
        GameStateDTO state = getDisplayState();
        if (state == null) return true;
        PortDTO port = findPortById(portId);
        if (port == null) return true;

        for (WireDTO wire : state.getWires()) {
            if (wire.getStartPortPosition().equals(port.getPosition()) ||
                    wire.getEndPortPosition().equals(port.getPosition())) {
                return true;
            }
        }
        return false;
    }

    private PortDTO findPortById(int portId) {
        GameStateDTO state = getDisplayState();
        if (state == null) return null;
        for (SystemDTO s : state.getSystems()) {
            for (PortDTO p : s.getPorts()) {
                if (p.getId() == portId) {
                    return p;
                }
            }
        }
        return null;
    }

    public PortDTO findPortAt(Point p) {
        GameStateDTO state = getDisplayState();
        if (state == null) return null;
        for (SystemDTO s : state.getSystems()) {
            for (SystemDTO.PortDTO port : s.getPorts()) {
                if (port.getPosition().distanceSq(p) < 12*12) {
                    return port;
                }
            }
        }
        return null;
    }

    public Point2D.Double findRelayPointAt(WireDTO wire, Point p, double clickThreshold) {
        if (wire == null) return null;
        for (Point2D.Double relay : wire.getRelayPoints()) {
            if (relay.distanceSq(p) < clickThreshold * clickThreshold) {
                return relay;
            }
        }
        return null;
    }

    public SystemDTO findSystemAt(Point p) {
        GameStateDTO state = getDisplayState();
        if (state == null) return null;
        for (SystemDTO s : state.getSystems()) {
            Rectangle bounds = new Rectangle(s.getX(), s.getY(), 80, 60);
            if (bounds.contains(p)) {
                return s;
            }
        }
        return null;
    }

    public WireDTO findWireAt(Point p, double clickThreshold) {
        GameStateDTO state = getDisplayState();
        if (p == null || state == null) return null;
        double clickThresholdSq = clickThreshold * clickThreshold;
        WireDTO closestWire = null;
        double minDistanceSq = Double.MAX_VALUE;
        for (WireDTO w : state.getWires()) {
            if (w == null) continue;
            List<Point2D.Double> path = w.getFullPathPoints();
            for (int i = 0; i < path.size() - 1; i++) {
                double distSq = Line2D.ptSegDistSq(path.get(i).x, path.get(i).y, path.get(i+1).x, path.get(i+1).y, p.x, p.y);
                if (distSq < minDistanceSq) {
                    minDistanceSq = distSq;
                    closestWire = w;
                }
            }
        }
        return (closestWire != null && minDistanceSq < clickThresholdSq) ? closestWire : null;
    }

    public NetworkGame getGame() { return game; }
    public boolean isOfflineMode() { return isOfflineMode; }
    public boolean isOnlineMultiplayer() { return isOnlineMultiplayer; }
    public boolean isWireDrawingMode() { return wireDrawingMode; }
    public PortDTO getSelectedOutputPort() { return selectedOutputPort; }
    public Point getMouseDragPos() { return mouseDragPos; }
    public boolean isGameRunning() { GameStateDTO s = getDisplayState(); return s != null && s.isSimulationRunning(); }
    public boolean isGamePaused() { GameStateDTO s = getDisplayState(); return s != null && s.isSimulationPaused(); }
    public Color getCurrentWiringColor() { return currentWiringColor; }
}