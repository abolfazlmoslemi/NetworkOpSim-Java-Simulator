// ===== File: GamePanel.java (FINAL - Adjusted level unlock logic) =====

package com.networkopsim.game.view.panels;

import com.networkopsim.game.controller.core.NetworkGame;
import com.networkopsim.game.controller.input.GameInputHandler;
import com.networkopsim.game.controller.logic.GameEngine;
import com.networkopsim.game.model.core.Packet;
import com.networkopsim.game.model.core.Port;
import com.networkopsim.game.model.core.System;
import com.networkopsim.game.model.core.Wire;
import com.networkopsim.game.model.enums.NetworkEnums;
import com.networkopsim.game.model.state.GameState;
import com.networkopsim.game.model.state.PacketSnapshot;
import com.networkopsim.game.model.state.PredictedPacketStatus;
import com.networkopsim.game.utils.GameStateManager;
import com.networkopsim.game.utils.GraphUtils;
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
import java.util.Objects;

public class GamePanel extends JPanel {
    private static final Logger logger = LoggerFactory.getLogger(GamePanel.class);

    // --- Constants ---
    private static final int AUTOSAVE_INTERVAL_MS = 10000;
    private static final int GAME_TICK_MS = 16;
    private static final int HUD_DISPLAY_TIME_MS = 7000;
    private static final int ATAR_DURATION_MS = 10000;
    private static final int AIRYAMAN_DURATION_MS = 5000;
    private static final int SPEED_LIMITER_DURATION_MS = 15000;
    private static final Color BACKGROUND_COLOR = new Color(15, 15, 20);
    private static final long TIME_SCRUB_INCREMENT_MS = 1000;
    public static final Color DEFAULT_WIRING_COLOR = Color.LIGHT_GRAY;
    public static final Color VALID_WIRING_COLOR_TARGET = new Color(0, 200, 0);
    public static final Color INVALID_WIRING_COLOR = new Color(220, 0, 0);
    private static final int RELAY_COST = 1;
    private static final long AERGIA_DURATION_MS = 20000;
    private static final long AERGIA_COOLDOWN_MS = 10000;
    private static final double AERGIA_EFFECT_RADIUS = 30.0;
    private static final long ELIPHAS_DURATION_MS = 30000;
    private static final double ELIPHAS_EFFECT_RADIUS = 40.0;
    private static final double SISYPHUS_DRAG_RADIUS = 150.0;
    private static final double PACKET_LOSS_GAME_OVER_THRESHOLD = 50.0;
    private static final long LEVEL_1_TIME_LIMIT_MS = 2 * 60 * 1000;
    private static final long LEVEL_2_TIME_LIMIT_MS = 4 * 60 * 1000;
    private static final long PREDICTION_SEED = 12345L;

    // --- Core Components ---
    private final NetworkGame game;
    private GameState gameState;
    private final GameEngine gameEngine;
    private final GameRenderer gameRenderer;
    private final GameInputHandler gameInputHandler;

    // --- Timers ---
    private final Timer gameLoopTimer;
    private final Timer autosaveTimer;
    private final Timer hudTimer;
    private final Timer atarTimer, airyamanTimer, speedLimiterTimer;

    // --- Game State Flags ---
    private volatile boolean levelComplete = false;
    private volatile boolean gameOver = false;
    private volatile boolean showHUD = true;
    private volatile boolean atarActive = false;
    private volatile boolean airyamanActive = false;
    private volatile boolean isSpeedLimiterActive = false;

    // --- Interactive UI State ---
    public enum InteractiveMode { NONE, AERGIA_PLACEMENT, SISYPHUS_DRAG, ELIPHAS_PLACEMENT }
    private volatile InteractiveMode currentInteractiveMode = InteractiveMode.NONE;
    private final List<ActiveWireEffect> activeWireEffects = Collections.synchronizedList(new ArrayList<>());
    private long aergiaCooldownUntil = 0;
    private System sisyphusDraggedSystem = null;
    private Point sisyphusDragStartPos = null;
    private int sisyphusPreDragWireLength = 0;
    private boolean sisyphusMoveIsValid = true;
    private Port selectedOutputPort = null;
    private final Point mouseDragPos = new Point();
    private boolean wireDrawingMode = false;
    private Color currentWiringColor = DEFAULT_WIRING_COLOR;
    private boolean relayPointDragMode = false;
    private Wire.RelayPoint draggedRelayPoint = null;
    private int preDragWireLength = 0;

    // --- Prediction (Time Scrubbing) State ---
    private volatile long viewedTimeMs = 0;
    private volatile long currentLevelTimeLimitMs = 0;
    private volatile boolean networkValidatedForPrediction = false;
    private final PredictionContext predictionContext = new PredictionContext();
    private PredictionRunStats displayedPredictionStats = new PredictionRunStats(0,0,0,0,0);
    private final List<PacketSnapshot> predictedPacketStates = Collections.synchronizedList(new ArrayList<>());

    private int finalRemainingWireLengthAtLevelEnd = -1;

    // --- Inner Classes for State Management ---
    public static class ActiveWireEffect implements Serializable {
        public final InteractiveMode type; public final Point2D.Double position; public final transient Wire parentWire; public final long expiryTime;
        public ActiveWireEffect(InteractiveMode type, Point2D.Double pos, Wire wire, long expiry) { this.type = type; this.position = pos; this.parentWire = wire; this.expiryTime = expiry; }
    }
    public static class PredictionRunStats {
        public final long atTimeMs; public final int totalPacketsGenerated; public final int totalPacketsLost; public final double packetLossPercentage; public final int totalPacketUnitsGenerated; public final int totalPacketUnitsLost;
        public PredictionRunStats(long time, int genCount, int lostCount, int unitsGen, int unitsLost) {
            this.atTimeMs = time; this.totalPacketsGenerated = genCount; this.totalPacketsLost = lostCount; this.totalPacketUnitsGenerated = unitsGen; this.totalPacketUnitsLost = unitsLost;
            if (unitsGen <= 0) { this.packetLossPercentage = 0.0; }
            else { this.packetLossPercentage = Math.min(100.0, Math.max(0.0, ((double) Math.min(unitsLost, unitsGen) / unitsGen) * 100.0)); }
        }
    }
    public class PredictionContext {
        private final List<Packet> tempGeneratedPackets = new ArrayList<>();
        private int totalGeneratedCount = 0; private int totalLostCount = 0; private int totalUnitsGenerated = 0; private int totalUnitsLost = 0;
        public void reset() { tempGeneratedPackets.clear(); totalGeneratedCount = 0; totalLostCount = 0; totalUnitsGenerated = 0; totalUnitsLost = 0; }
        public void registerGeneratedPacket(Packet p) { tempGeneratedPackets.add(p); totalGeneratedCount++; totalUnitsGenerated += p.getSize(); }
        public void registerLostPacket(Packet p) { totalLostCount++; totalUnitsLost += p.getSize(); }
        public List<Packet> getTempGeneratedPackets() { return tempGeneratedPackets; }
        public PredictionRunStats getStats(long atTime) { return new PredictionRunStats(atTime, totalGeneratedCount, totalLostCount, totalUnitsGenerated, totalUnitsLost); }
    }

    public GamePanel(NetworkGame game) {
        this.game = game;
        this.gameState = game.getGameState();
        this.gameEngine = new GameEngine(game, this);
        this.gameRenderer = new GameRenderer(this, this.gameEngine, this.gameState);
        this.gameInputHandler = new GameInputHandler(this, game);

        setBackground(BACKGROUND_COLOR);
        setPreferredSize(new Dimension(NetworkGame.WINDOW_WIDTH, NetworkGame.WINDOW_HEIGHT));
        setFocusable(true);
        addKeyListener(gameInputHandler);
        addMouseListener(gameInputHandler);
        addMouseMotionListener(gameInputHandler);
        ToolTipManager.sharedInstance().registerComponent(this);

        hudTimer = new Timer(HUD_DISPLAY_TIME_MS, e -> { showHUD = false; repaint(); }); hudTimer.setRepeats(false);
        atarTimer = new Timer(ATAR_DURATION_MS, e -> deactivateAtar()); atarTimer.setRepeats(false);
        airyamanTimer = new Timer(AIRYAMAN_DURATION_MS, e -> deactivateAiryaman()); airyamanTimer.setRepeats(false);
        speedLimiterTimer = new Timer(SPEED_LIMITER_DURATION_MS, e -> deactivateSpeedLimiter()); speedLimiterTimer.setRepeats(false);
        gameLoopTimer = new Timer(GAME_TICK_MS, e -> gameTick()); gameLoopTimer.setRepeats(true);
        autosaveTimer = new Timer(AUTOSAVE_INTERVAL_MS, e -> { if (isGameRunning() && !isGamePaused() && !isLevelComplete() && !isGameOver()) { GameStateManager.saveGameState(this); } }); autosaveTimer.setRepeats(true);
    }

    public void initializeLevel(int level) {
        stopSimulation();

        if (gameEngine.initializeLevel(level)) {
            this.gameState = gameEngine.getGameState();
            this.gameRenderer.setGameState(this.gameState);

            this.networkValidatedForPrediction = false;
            this.finalRemainingWireLengthAtLevelEnd = -1;
            if (level == 1) this.currentLevelTimeLimitMs = LEVEL_1_TIME_LIMIT_MS;
            else if (level == 2) this.currentLevelTimeLimitMs = LEVEL_2_TIME_LIMIT_MS;
            else this.currentLevelTimeLimitMs = 240 * 1000;

            levelComplete = false;
            gameOver = false;
            viewedTimeMs = 0;

            cancelAllInteractiveModes();

            deactivateAtar();
            deactivateAiryaman();
            deactivateSpeedLimiter();
            aergiaCooldownUntil = 0;
            activeWireEffects.clear();

            validateAndSetPredictionFlag();
            showHUD = true;
            hudTimer.restart();

            repaint();
            SwingUtilities.invokeLater(this::requestFocusInWindow);
        } else {
            game.returnToMenu();
        }
    }

    public void attemptStartSimulation() {
        if (isSimulationStarted()) return;
        validateAndSetPredictionFlag();
        String validationMessage = getNetworkValidationErrorMessage();
        if (validationMessage != null) { logger.warn("Network validation failed: {}", validationMessage); if (!game.isMuted()) game.playSoundEffect("error"); JOptionPane.showMessageDialog(this, "Network Validation Failed:\n" + validationMessage, "Network Not Ready", JOptionPane.WARNING_MESSAGE); return; }
        gameEngine.startSimulation();
        gameLoopTimer.start();
        autosaveTimer.start();
        repaint();
    }

    public void stopSimulation() {
        if (!isGameRunning() && !isSimulationStarted()) return;
        gameEngine.stopSimulation();
        if (gameLoopTimer.isRunning()) gameLoopTimer.stop(); if (autosaveTimer.isRunning()) autosaveTimer.stop(); if (atarTimer.isRunning()) { deactivateAtar(); atarTimer.stop(); } if (airyamanTimer.isRunning()) { deactivateAiryaman(); airyamanTimer.stop(); } if (speedLimiterTimer.isRunning()) { deactivateSpeedLimiter(); speedLimiterTimer.stop(); }
        if (levelComplete || gameOver) { GameStateManager.deleteSaveFile(); }
        if (!isSimulationStarted()) { validateAndSetPredictionFlag(); }
        repaint();
    }

    public void loadFromSaveData(GameStateManager.SaveData saveData) {
        stopSimulation();
        gameEngine.loadFromSaveData(saveData);
        this.gameState = gameEngine.getGameState();
        this.gameRenderer.setGameState(this.gameState);
        game.showTemporaryMessage("Game Loaded. Resuming in 3 seconds...", Color.GREEN, 3000);
        new Timer(3000, e -> {
            pauseGame(false);
            gameLoopTimer.start();
            autosaveTimer.start();
        }) {{setRepeats(false);}}.start();
        repaint(); requestFocusInWindow();
    }

    private void gameTick() {
        if (!isGameRunning() || isGamePaused()) return;
        if (gameOver || levelComplete) { stopSimulation(); return; }
        gameEngine.gameTick(GAME_TICK_MS);
        updateActiveEffects();
        checkEndConditions();
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) { super.paintComponent(g); gameRenderer.render(g); }

    public void pauseGame(boolean pause) {
        if (!isSimulationStarted() || gameOver || levelComplete) return;
        if (pause && !isGamePaused()) {
            gameEngine.setPaused(true);
            if (atarTimer.isRunning()) atarTimer.stop();
            if (airyamanTimer.isRunning()) airyamanTimer.stop();
            if (speedLimiterTimer.isRunning()) speedLimiterTimer.stop();
            repaint();
        } else if (!pause && isGamePaused()) {
            gameEngine.setPaused(false);
            if (atarActive) atarTimer.start();
            if (airyamanActive) airyamanTimer.start();
            if (isSpeedLimiterActive) speedLimiterTimer.start();
            repaint();
        }
    }

    public String getNetworkValidationErrorMessage() {
        List<System> currentSystemsSnapshot = gameEngine.getSystems();
        List<Wire> currentWiresSnapshot = gameEngine.getWires();
        if (!GraphUtils.isNetworkConnected(currentSystemsSnapshot, currentWiresSnapshot)) { return "All systems must be part of a single connected network."; }
        if (!GraphUtils.areAllSystemPortsConnected(currentSystemsSnapshot)) { return "All ports on every system must be connected."; }
        long totalWireLength = 0;
        for (Wire wire : currentWiresSnapshot) {
            if (isWireIntersectingAnySystem(wire, currentSystemsSnapshot)) { return "A wire (ID: " + wire.getId() + ") is passing through a system's body."; }
            totalWireLength += wire.getLength();
        }
        if (totalWireLength > gameState.getMaxWireLengthForLevel()) { return "Total wire length exceeds the budget."; }
        return null;
    }

    public boolean isWireIntersectingAnySystem(Wire wire, List<System> allSystems) {
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

    public void validateAndSetPredictionFlag() {
        String errorMessage = getNetworkValidationErrorMessage();
        boolean newStateIsValid = (errorMessage == null);
        if (this.networkValidatedForPrediction != newStateIsValid && newStateIsValid) {
            game.showTemporaryMessage("Network is fully connected and ready!", new Color(0, 150, 0), 2500);
        }
        this.networkValidatedForPrediction = newStateIsValid;
        updatePrediction();
        repaint();
    }

    private void checkEndConditions() {
        if (gameOver || levelComplete || !isSimulationStarted()) return;
        if (getSimulationTimeElapsedMs() >= currentLevelTimeLimitMs) { handleEndOfLevel(); return; }
        boolean allSourcesFinished = true;
        for (System s : getSystems()) {
            if (s.getSystemType() == NetworkEnums.SystemType.SOURCE && s.getTotalPacketsToGenerate() > 0) {
                if (s.getPacketsGeneratedCount() < s.getTotalPacketsToGenerate()) {
                    allSourcesFinished = false; break;
                }
            }
        }
        if (!allSourcesFinished) return;
        boolean packetsRemaining = !gameEngine.getPackets().isEmpty();
        boolean queuesEmpty = true;
        for(System s: getSystems()) { if(s.getQueueSize() > 0) { queuesEmpty = false; break; } }
        if (allSourcesFinished && !packetsRemaining && queuesEmpty) { handleEndOfLevel(); }
    }

    private void handleEndOfLevel() {
        if (gameOver || levelComplete) return;
        this.finalRemainingWireLengthAtLevelEnd = gameState.getRemainingWireLength();
        stopSimulation();
        for (Packet p : gameEngine.getPackets()) { if (p != null) gameState.increasePacketLoss(p); }
        for (System s : getSystems()) {
            for(Packet p : s.packetQueue) { if (p != null) gameState.increasePacketLoss(p); }
            s.packetQueue.clear();
        }
        boolean lostTooMany = gameState.getPacketLossPercentage() >= PACKET_LOSS_GAME_OVER_THRESHOLD;
        if (lostTooMany) {
            gameOver = true; if (!game.isMuted()) game.playSoundEffect("game_over");
        } else {
            levelComplete = true; if (!game.isMuted()) game.playSoundEffect("level_complete");
            // [MODIFIED] Unlock the *next* level if it's within bounds.
            int currentLevelIndex = getCurrentLevel() - 1; // Levels are 1-based, indices are 0-based
            int nextLevelIndex = currentLevelIndex + 1;
            if (nextLevelIndex < gameState.getMaxLevels()) {
                gameState.unlockLevel(nextLevelIndex);
            }
        }
        SwingUtilities.invokeLater(() -> showEndLevelDialog(!lostTooMany));
    }

    private void showEndLevelDialog(boolean success) {
        String title = success ? "Level " + getCurrentLevel() + " Complete!" : "Game Over!";
        StringBuilder message = new StringBuilder();
        message.append(success ? "Congratulations!" : "Simulation Failed!").append("\nLevel ").append(getCurrentLevel()).append(success ? " passed." : " failed.");
        message.append("\n\n--- Results ---")
                .append("\nPackets Delivered: ").append(gameEngine.getTotalPacketsSuccessfullyDelivered())
                .append("\nPackets Generated: ").append(gameState.getTotalPacketsGeneratedCount())
                .append("\nPackets Lost: ").append(gameState.getTotalPacketsLostCount())
                .append("\nPacket Units Lost: ").append(gameState.getTotalPacketLossUnits()).append(" units (").append(String.format("%.1f%%", gameState.getPacketLossPercentage())).append(")")
                .append("\nTotal Coins (Overall): ").append(gameState.getCoins())
                .append("\nRemaining Wire Length: ").append(finalRemainingWireLengthAtLevelEnd != -1 ? finalRemainingWireLengthAtLevelEnd : gameState.getRemainingWireLength())
                .append("\nSimulation Time: ").append(String.format("%.2f / %.0f s", getSimulationTimeElapsedMs() / 1000.0, currentLevelTimeLimitMs / 1000.0));

        List<String> optionsList = new ArrayList<>();
        int nextLevel = getCurrentLevel() + 1;
        if (success && nextLevel <= gameState.getMaxLevels()) { optionsList.add("Next Level (" + nextLevel + ")"); }
        optionsList.add("Retry Level");
        optionsList.add("Main Menu");
        Object[] options = optionsList.toArray();
        int choice = JOptionPane.showOptionDialog(this.game, message.toString(), title, JOptionPane.DEFAULT_OPTION, success ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE, null, options, options[0]);
        String selectedOption = (choice >= 0 && choice < options.length) ? options[choice].toString() : "Main Menu";
        if (selectedOption.startsWith("Next Level")) { game.setLevel(nextLevel); game.startGame(); }
        else if (selectedOption.equals("Retry Level")) { game.setLevel(getCurrentLevel()); game.startGame(); }
        else { game.returnToMenu(); }
    }

    private void updatePrediction() {
        if (isSimulationStarted() || !networkValidatedForPrediction) {
            predictedPacketStates.clear();
            displayedPredictionStats = new PredictionRunStats(0,0,0,0,0);
            repaint();
            return;
        }

        // Create copies of the state FIRST.
        GameState gameStateCopy = getGameStateDeepCopy();
        List<System> systemsCopy = getSystemsDeepCopy();
        List<Wire> wiresCopy = getWiresDeepCopy();
        List<Packet> packetsCopy = new ArrayList<>(); // Prediction always starts with an empty packet list.

        // The copied GameState must be reset to a clean state for the simulation to start correctly.
        gameStateCopy.resetForSimulationAttemptOnly();

        // Use the new, isolated constructor for the prediction engine.
        GameEngine predictionEngine = new GameEngine(this, gameStateCopy, systemsCopy, wiresCopy, packetsCopy);

        // The transient references MUST be rebuilt after deep copying.
        predictionEngine.rebuildTransientReferences();

        predictionContext.reset();
        System.resetGlobalRandomSeed(PREDICTION_SEED);

        // Also reset the static packet ID counter for a clean prediction run.
        Packet.resetGlobalId();

        for(System s : predictionEngine.getSystems()) s.resetForNewRun();

        long internalTime = 0;
        while (internalTime <= viewedTimeMs) {
            predictionEngine.runSimulationTickLogic(true, internalTime, false, false, false);
            internalTime += GAME_TICK_MS;
        }

        predictedPacketStates.clear();
        for (Packet p : predictionContext.getTempGeneratedPackets()) {
            PredictedPacketStatus status = p.getFinalStatusForPrediction();
            if (status == null) {
                if(p.getCurrentSystem() != null) status = PredictedPacketStatus.QUEUED;
                else if(p.getCurrentWire() != null) status = PredictedPacketStatus.ON_WIRE;
                else status = PredictedPacketStatus.STALLED_AT_NODE;
            }
            predictedPacketStates.add(new PacketSnapshot(p, status));
        }
        displayedPredictionStats = predictionContext.getStats(viewedTimeMs);
        repaint();
    }

    public void startWiringMode(Port startPort, Point currentMousePos) { if (!wireDrawingMode && startPort != null && !isSimulationStarted() && startPort.getType() == NetworkEnums.PortType.OUTPUT && !startPort.isConnected()) { logger.debug("Starting wire drawing from port {}.", startPort.getId()); this.selectedOutputPort = startPort; this.mouseDragPos.setLocation(currentMousePos); this.wireDrawingMode = true; this.currentWiringColor = DEFAULT_WIRING_COLOR; setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR)); repaint(); } else if (isSimulationStarted()) { if(!game.isMuted()) game.playSoundEffect("error"); } }
    public void updateDragPos(Point currentMousePos) { if (wireDrawingMode) this.mouseDragPos.setLocation(currentMousePos); }
    public void updateWiringPreview(Point currentMousePos) { if (!wireDrawingMode || selectedOutputPort == null || selectedOutputPort.getPosition() == null) { this.currentWiringColor = DEFAULT_WIRING_COLOR; repaint(); return; } Point startPos = selectedOutputPort.getPosition(); double wireLength = startPos.distance(currentMousePos); if (gameState.getRemainingWireLength() < wireLength) { this.currentWiringColor = INVALID_WIRING_COLOR; repaint(); return; } Port targetPort = findPortAt(currentMousePos); if (targetPort != null) { if (Objects.equals(targetPort.getParentSystem(), selectedOutputPort.getParentSystem()) || targetPort.getType() != NetworkEnums.PortType.INPUT || targetPort.isConnected()) { this.currentWiringColor = INVALID_WIRING_COLOR; } else { this.currentWiringColor = VALID_WIRING_COLOR_TARGET; } } else { this.currentWiringColor = DEFAULT_WIRING_COLOR; } repaint(); }
    public boolean attemptWireCreation(Port startPort, Port endPort) { if (isSimulationStarted()) return false; if (startPort == null || endPort == null || startPort.getPosition() == null || endPort.getPosition() == null || startPort.getType() != NetworkEnums.PortType.OUTPUT || endPort.getType() != NetworkEnums.PortType.INPUT || startPort.isConnected() || endPort.isConnected() || Objects.equals(startPort.getParentSystem(), endPort.getParentSystem())) return false; int wireLength = (int) Math.round(startPort.getPosition().distance(endPort.getPosition())); if (wireLength <= 0) return false; if (gameState.getRemainingWireLength() >= wireLength) { gameState.useWire(wireLength); try { Wire newWire = new Wire(startPort, endPort); gameEngine.getWires().add(newWire); if (!game.isMuted()) game.playSoundEffect("wire_connect"); validateAndSetPredictionFlag(); return true; } catch (Exception e) { gameState.returnWire(wireLength); JOptionPane.showMessageDialog(this.game, "Cannot create wire:\n" + e.getMessage(), "Wiring Error", JOptionPane.WARNING_MESSAGE); if (!game.isMuted()) game.playSoundEffect("error"); validateAndSetPredictionFlag(); return false; } } else { JOptionPane.showMessageDialog(this.game, "Not enough wire! Need: " + wireLength + ", Have: " + gameState.getRemainingWireLength(), "Insufficient Wire", JOptionPane.WARNING_MESSAGE); if (!game.isMuted()) game.playSoundEffect("error"); return false; } }
    public void cancelWiring() { if(wireDrawingMode) { logger.debug("Wire drawing cancelled."); selectedOutputPort = null; wireDrawingMode = false; currentWiringColor = DEFAULT_WIRING_COLOR; setCursor(Cursor.getDefaultCursor()); repaint(); } }
    public void deleteWireRequest(Wire wireToDelete) { if (wireToDelete == null || isSimulationStarted()) { if(isSimulationStarted()) if(!game.isMuted()) game.playSoundEffect("error"); return; } if (gameEngine.getWires().remove(wireToDelete)) { int returnedLength = (int) Math.round(wireToDelete.getLength()); gameState.returnWire(returnedLength); wireToDelete.destroy(); if (!game.isMuted()) game.playSoundEffect("wire_disconnect"); validateAndSetPredictionFlag(); } }
    public void addRelayPointRequest(Wire wire, Point p) { if (wire == null || isSimulationStarted()) return; if (wire.getRelayPointsCount() >= Wire.MAX_RELAY_POINTS) { game.showTemporaryMessage("Maximum relay points (3) reached for this wire.", Color.ORANGE, 2000); if (!game.isMuted()) game.playSoundEffect("error"); return; } if (!gameState.spendCoins(RELAY_COST)) { game.showTemporaryMessage("Not enough coins to add a relay point! Cost: " + RELAY_COST, Color.RED, 2500); if (!game.isMuted()) game.playSoundEffect("error"); return; } int oldLength = (int)Math.round(wire.getLength()); wire.addRelayPoint(new Point2D.Double(p.x, p.y)); int newLength = (int)Math.round(wire.getLength()); int deltaLength = newLength - oldLength; if (deltaLength > 0) gameState.useWire(deltaLength); else if (deltaLength < 0) gameState.returnWire(-deltaLength); if (!game.isMuted()) game.playSoundEffect("ui_confirm"); validateAndSetPredictionFlag(); repaint(); }
    public void deleteRelayPointRequest(Wire.RelayPoint relayPoint) { if (relayPoint == null || isSimulationStarted()) return; Wire parentWire = relayPoint.getParentWire(); int oldLength = (int)Math.round(parentWire.getLength()); parentWire.removeRelayPoint(relayPoint); int newLength = (int)Math.round(parentWire.getLength()); gameState.returnWire(oldLength - newLength); gameState.addCoins(RELAY_COST); if (!game.isMuted()) game.playSoundEffect("wire_disconnect"); validateAndSetPredictionFlag(); repaint(); }
    public void startRelayPointDrag(Wire.RelayPoint relayPoint) { if (relayPoint == null || isSimulationStarted()) return; this.relayPointDragMode = true; this.draggedRelayPoint = relayPoint; this.draggedRelayPoint.setDragged(true); this.preDragWireLength = (int)Math.round(relayPoint.getParentWire().getLength()); setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR)); repaint(); }
    public void updateDraggedRelayPointPosition(Point newPos) { if (!relayPointDragMode || draggedRelayPoint == null) return; draggedRelayPoint.setPosition(new Point2D.Double(newPos.x, newPos.y)); draggedRelayPoint.getParentWire().recalculateLength(); validateAndSetPredictionFlag(); repaint(); }
    public void stopRelayPointDrag() { if (!relayPointDragMode || draggedRelayPoint == null) return; int finalLength = (int)Math.round(draggedRelayPoint.getParentWire().getLength()); int deltaLength = finalLength - preDragWireLength; if (deltaLength > 0) gameState.useWire(deltaLength); else gameState.returnWire(-deltaLength); draggedRelayPoint.setDragged(false); draggedRelayPoint = null; relayPointDragMode = false; preDragWireLength = 0; setCursor(Cursor.getDefaultCursor()); validateAndSetPredictionFlag(); repaint(); }
    public void cancelRelayPointDrag() { if (!relayPointDragMode || draggedRelayPoint == null) return; draggedRelayPoint.revertToLastPosition(); draggedRelayPoint.getParentWire().recalculateLength(); stopRelayPointDrag(); }
    public void activateAtar() { if (!atarActive) { atarActive = true; } atarTimer.restart(); repaint(); }
    private void deactivateAtar() { if (atarActive) { atarActive = false; repaint(); } atarTimer.stop(); }
    public void activateAiryaman() { if (!airyamanActive) { airyamanActive = true; } airyamanTimer.restart(); repaint(); }
    private void deactivateAiryaman() { if (airyamanActive) { airyamanActive = false; repaint(); } airyamanTimer.stop(); }
    public void activateAnahita() { for (Packet p : gameEngine.getPackets()) { if (p.getNoise() > 0) p.resetNoise(); } repaint(); }
    public void activateSpeedLimiter() { if (!isSpeedLimiterActive) { isSpeedLimiterActive = true; } speedLimiterTimer.restart(); repaint(); }
    private void deactivateSpeedLimiter() { if (isSpeedLimiterActive) { isSpeedLimiterActive = false; repaint(); } speedLimiterTimer.stop(); }
    public void activateEmergencyBrake() { for (Packet p : gameEngine.getPackets()) { if (p.getCurrentWire() != null) p.setCurrentSpeedMagnitude(Packet.BASE_SPEED_MAGNITUDE); } repaint(); }
    public void enterAergiaPlacementMode() { if (currentInteractiveMode != InteractiveMode.NONE) cancelAllInteractiveModes(); currentInteractiveMode = InteractiveMode.AERGIA_PLACEMENT; setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)); game.showTemporaryMessage("Scroll of Aergia: Click on a wire to place the effect.", Color.CYAN, 5000); pauseGame(false); }
    public void enterSisyphusDragMode() { if (currentInteractiveMode != InteractiveMode.NONE) cancelAllInteractiveModes(); currentInteractiveMode = InteractiveMode.SISYPHUS_DRAG; setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)); game.showTemporaryMessage("Scroll of Sisyphus: Click and drag a non-reference system to move it.", Color.MAGENTA, 5000); pauseGame(false); }
    public void enterEliphasPlacementMode() { if (currentInteractiveMode != InteractiveMode.NONE) cancelAllInteractiveModes(); currentInteractiveMode = InteractiveMode.ELIPHAS_PLACEMENT; setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)); game.showTemporaryMessage("Scroll of Eliphas: Click on a wire to place the realignment field.", Color.ORANGE, 5000); pauseGame(false); }
    public void placeAergiaEffect(Wire wire, Point point) { activeWireEffects.add(new ActiveWireEffect(InteractiveMode.AERGIA_PLACEMENT, new Point2D.Double(point.x, point.y), wire, getSimulationTimeElapsedMs() + AERGIA_DURATION_MS)); aergiaCooldownUntil = getSimulationTimeElapsedMs() + AERGIA_COOLDOWN_MS; currentInteractiveMode = InteractiveMode.NONE; setCursor(Cursor.getDefaultCursor()); if (!game.isMuted()) game.playSoundEffect("ui_confirm"); repaint(); }
    public void placeEliphasEffect(Wire wire, Point point) { activeWireEffects.add(new ActiveWireEffect(InteractiveMode.ELIPHAS_PLACEMENT, new Point2D.Double(point.x, point.y), wire, getSimulationTimeElapsedMs() + ELIPHAS_DURATION_MS)); currentInteractiveMode = InteractiveMode.NONE; setCursor(Cursor.getDefaultCursor()); if (!game.isMuted()) game.playSoundEffect("ui_confirm"); repaint(); }
    public void startSisyphusDrag(System system, Point point) { sisyphusDraggedSystem = system; sisyphusDragStartPos = system.getPosition(); this.sisyphusPreDragWireLength = getWires().stream().mapToInt(w -> (int)w.getLength()).sum(); setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR)); repaint(); }
    public void updateSisyphusDrag(Point point) { if (sisyphusDraggedSystem == null) return; double distFromStart = point.distance(sisyphusDragStartPos); if (distFromStart > SISYPHUS_DRAG_RADIUS) { double angle = Math.atan2(point.y - sisyphusDragStartPos.y, point.x - sisyphusDragStartPos.x); point.x = (int) (sisyphusDragStartPos.x + SISYPHUS_DRAG_RADIUS * Math.cos(angle)); point.y = (int) (sisyphusDragStartPos.y + SISYPHUS_DRAG_RADIUS * Math.sin(angle)); } sisyphusDraggedSystem.setPosition(point.x - System.SYSTEM_WIDTH / 2, point.y - System.SYSTEM_HEIGHT / 2); revalidateSystemMove(); repaint(); }
    public void stopSisyphusDrag() { if (sisyphusDraggedSystem == null) return; revalidateSystemMove(); if (sisyphusMoveIsValid) { int newTotalWireLength = getWires().stream().mapToInt(w -> (int)w.getLength()).sum(); int deltaLength = newTotalWireLength - sisyphusPreDragWireLength; if (deltaLength > 0) gameState.useWire(deltaLength); else gameState.returnWire(-deltaLength); if (!game.isMuted()) game.playSoundEffect("ui_confirm"); } else { sisyphusDraggedSystem.setPosition(sisyphusDragStartPos.x - System.SYSTEM_WIDTH/2, sisyphusDragStartPos.y - System.SYSTEM_HEIGHT/2); recalculateAllWireLengths(); if (!game.isMuted()) game.playSoundEffect("error"); } sisyphusDraggedSystem = null; sisyphusDragStartPos = null; currentInteractiveMode = InteractiveMode.NONE; setCursor(Cursor.getDefaultCursor()); repaint(); }

    public void cancelAllInteractiveModes() {
        logger.info("Cancelling all interactive modes. Current mode: {}", currentInteractiveMode);
        if (sisyphusDraggedSystem != null) {
            sisyphusDraggedSystem.setPosition(sisyphusDragStartPos.x - System.SYSTEM_WIDTH / 2, sisyphusDragStartPos.y - System.SYSTEM_HEIGHT / 2);
            recalculateAllWireLengths();
            int totalWireLengthAfterRevert = getWires().stream().mapToInt(w -> (int) w.getLength()).sum();
            gameState.setRemainingWireLength(gameState.getMaxWireLengthForLevel() - totalWireLengthAfterRevert);
        }
        sisyphusDraggedSystem = null;
        sisyphusDragStartPos = null;
        currentInteractiveMode = InteractiveMode.NONE;
        setCursor(Cursor.getDefaultCursor());
        repaint();
    }

    public void toggleHUD() { showHUD = !showHUD; if (showHUD) hudTimer.restart(); else hudTimer.stop(); repaint(); }
    public void incrementViewedTime() { if (!isSimulationStarted()) { if (!this.networkValidatedForPrediction) { String validationMessage = getNetworkValidationErrorMessage(); if(!game.isMuted()) game.playSoundEffect("error"); JOptionPane.showMessageDialog(this, "Cannot scrub time:\n" + (validationMessage != null ? validationMessage : "Network is not fully connected."), "Network Not Ready for Scrubbing", JOptionPane.WARNING_MESSAGE); return; } if (viewedTimeMs < currentLevelTimeLimitMs) viewedTimeMs = Math.min(currentLevelTimeLimitMs, viewedTimeMs + TIME_SCRUB_INCREMENT_MS); updatePrediction(); } }
    public void decrementViewedTime() { if (!isSimulationStarted()) { if (!this.networkValidatedForPrediction) { String validationMessage = getNetworkValidationErrorMessage(); if(!game.isMuted()) game.playSoundEffect("error"); JOptionPane.showMessageDialog(this, "Cannot scrub time:\n" + (validationMessage != null ? validationMessage : "Network is not fully connected."), "Network Not Ready for Scrubbing", JOptionPane.WARNING_MESSAGE); return; } viewedTimeMs = Math.max(0, viewedTimeMs - TIME_SCRUB_INCREMENT_MS); updatePrediction(); } }
    private void updateActiveEffects() { synchronized(activeWireEffects) { activeWireEffects.removeIf(effect -> getSimulationTimeElapsedMs() >= effect.expiryTime); } }
    public void applyWireEffectsToPacket(Packet packet) { synchronized(activeWireEffects) { for (ActiveWireEffect effect : activeWireEffects) { if (Objects.equals(effect.parentWire, packet.getCurrentWire())) { Point2D.Double idealPos = packet.getIdealPosition(); if (idealPos != null && effect.position.distanceSq(idealPos) < AERGIA_EFFECT_RADIUS * AERGIA_EFFECT_RADIUS) { if(effect.type == InteractiveMode.AERGIA_PLACEMENT) packet.nullifyAcceleration(); else if (effect.type == InteractiveMode.ELIPHAS_PLACEMENT) packet.realignToWire(); } } } } }
    public Port findPortAt(Point p) { for(System s : getSystems()) { Port port = s.getPortAt(p); if (port != null) return port; } return null; }
    public Wire findWireAt(Point p, double clickThreshold) { if (p == null) return null; double clickThresholdSq = clickThreshold * clickThreshold; Wire closestWire = null; double minDistanceSq = Double.MAX_VALUE; for (Wire w : getWires()) { if (w == null) continue; List<Point2D.Double> path = w.getFullPathPoints(); for (int i = 0; i < path.size() - 1; i++) { double distSq = Line2D.ptSegDistSq(path.get(i).x, path.get(i).y, path.get(i+1).x, path.get(i+1).y, p.x, p.y); if (distSq < minDistanceSq) { minDistanceSq = distSq; closestWire = w; } } } return (closestWire != null && minDistanceSq < clickThresholdSq) ? closestWire : null; }
    public System findSystemAt(Point p) { for(System s : getSystems()) { if (new Rectangle(s.getX(), s.getY(), System.SYSTEM_WIDTH, System.SYSTEM_HEIGHT).contains(p)) return s; } return null; }
    public Wire.RelayPoint findRelayPointAt(Point p) { if (p == null) return null; for (Wire w : getWires()) { Wire.RelayPoint relay = w.getRelayPointAt(p); if (relay != null) return relay; } return null; }
    public void clearAllHoverStates() { for(Wire w : getWires()) w.clearHoverStates(); }
    private void recalculateAllWireLengths() { for (Wire w : getWires()) w.recalculateLength(); }
    private void revalidateSystemMove() { recalculateAllWireLengths(); int newTotalWireLength = getWires().stream().mapToInt(w -> (int)w.getLength()).sum(); boolean intersectionFound = false; for (Wire w : getWires()) { if (isWireIntersectingAnySystem(w, getSystems())) { intersectionFound = true; break; } } int deltaLength = newTotalWireLength - sisyphusPreDragWireLength; sisyphusMoveIsValid = deltaLength <= gameState.getRemainingWireLength() && !intersectionFound; }
    @SuppressWarnings("unchecked") private <T extends Serializable> T deepCopy(T original) { try { java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream(); java.io.ObjectOutputStream oos = new java.io.ObjectOutputStream(baos); oos.writeObject(original); oos.close(); java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(baos.toByteArray()); java.io.ObjectInputStream ois = new java.io.ObjectInputStream(bais); return (T) ois.readObject(); } catch (Exception e) { logger.error("Deep copy failed", e); return null; } }
    public List<System> getSystemsDeepCopy() { return deepCopy(new ArrayList<>(getSystems())); }
    public List<Wire> getWiresDeepCopy() { return deepCopy(new ArrayList<>(getWires())); }
    public List<Packet> getPacketsDeepCopy() { return deepCopy(new ArrayList<>(getPackets())); }
    public GameState getGameStateDeepCopy() { return deepCopy(gameState); }
    public boolean isGameRunning() { return gameEngine.isSimulationRunning(); }
    public boolean isGamePaused() { return gameEngine.isSimulationPaused(); }
    public boolean isSimulationStarted() { return gameEngine.isSimulationRunning() || gameEngine.isSimulationPaused(); }
    public boolean isLevelComplete() { return levelComplete; }
    public boolean isGameOver() { return gameOver; }
    public int getCurrentLevel() { return gameState.getCurrentSelectedLevel(); }
    public List<System> getSystems() { return gameEngine.getSystems(); }
    public List<Wire> getWires() { return gameEngine.getWires(); }
    public List<Packet> getPackets() { return gameEngine.getPackets(); }
    public List<PacketSnapshot> getPredictedPacketStates() { return predictedPacketStates; }
    public PredictionRunStats getDisplayedPredictionStats() { return displayedPredictionStats; }
    public long getSimulationTimeElapsedMs() { return gameEngine.getSimulationTimeElapsedMs(); }
    public boolean isWireDrawingMode() { return wireDrawingMode; }
    public Port getSelectedOutputPort() { return selectedOutputPort; }
    public Point getMouseDragPos() { return mouseDragPos; }
    public Color getCurrentWiringColor() { return currentWiringColor; }
    public boolean isShowHUD() { return showHUD; }
    public boolean isAtarActive() { return atarActive; }
    public boolean isAiryamanActive() { return airyamanActive; }
    public boolean isSpeedLimiterActive() { return isSpeedLimiterActive; }
    public long getViewedTimeMs() { return viewedTimeMs; }
    public long getCurrentLevelTimeLimitMs() { return currentLevelTimeLimitMs; }
    public boolean isNetworkValidatedForPrediction() { return networkValidatedForPrediction; }
    public boolean isRelayPointDragMode() { return relayPointDragMode; }
    public InteractiveMode getCurrentInteractiveMode() { return currentInteractiveMode; }
    public List<ActiveWireEffect> getActiveWireEffects() { return activeWireEffects; }
    public boolean isAergiaOnCooldown() { return getSimulationTimeElapsedMs() < aergiaCooldownUntil; }
    public long getAergiaCooldownTimeRemaining() { return Math.max(0, aergiaCooldownUntil - getSimulationTimeElapsedMs()); }
    public System getSisyphusDraggedSystem() { return sisyphusDraggedSystem; }
    public Point getSisyphusDragStartPos() { return sisyphusDragStartPos; }
    public double getSisyphusDragRadius() { return SISYPHUS_DRAG_RADIUS; }
    public boolean isSisyphusMoveValid() { return sisyphusMoveIsValid; }
    public GameEngine getGameEngine() { return this.gameEngine; }
    public PredictionContext getPredictionContext() { return this.predictionContext; }
    public long getPredictionSeed() { return PREDICTION_SEED; }
    public NetworkGame getGame() { return this.game; }
}