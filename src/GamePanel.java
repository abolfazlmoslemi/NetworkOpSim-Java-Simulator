// ===== File: GamePanel.java =====

// === GamePanel.java ===
// FILE: GamePanel.java
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.Objects;
import java.util.Random;
import java.awt.geom.Point2D;
import java.awt.geom.Line2D;

public class GamePanel extends JPanel {
    // ... (Pair class remains the same) ...
    private static class Pair<T, U> {
        final T first;
        final U second;
        Pair(T first, U second) {
            this.first = first;
            this.second = second;
        }
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Pair<?, ?> pair = (Pair<?, ?>) o;
            return Objects.equals(first, pair.first) &&
                    Objects.equals(second, pair.second);
        }
        @Override
        public int hashCode() {
            return Objects.hash(first, second);
        }
        @Override
        public String toString() {
            return "Pair{" + "first=" + first + ", second=" + second + '}';
        }
    }

    private static final int GAME_TICK_MS = 16;
    private static final int HUD_DISPLAY_TIME_MS = 7000;
    private static final int ATAR_DURATION_MS = 10000;
    private static final int AIRYAMAN_DURATION_MS = 5000;
    private static final Color BACKGROUND_COLOR = new Color(15, 15, 20);
    private static final double MAX_DELTA_TIME_SEC = 0.1;
    private static final double PACKET_LOSS_GAME_OVER_THRESHOLD = 50.0;
    private static final long TIME_SCRUB_INCREMENT_MS = 100;
    private static final double PREDICTION_FLOAT_TOLERANCE = 1e-9;
    private static final double DIRECT_COLLISION_NOISE_PER_PACKET = 1.0;
    private static final double IMPACT_WAVE_RADIUS = 180.0;
    private static final double IMPACT_WAVE_MAX_NOISE = 1.0;

    public static final Color DEFAULT_WIRING_COLOR = Color.LIGHT_GRAY;
    public static final Color VALID_WIRING_COLOR_TARGET = new Color(0, 200, 0);
    public static final Color INVALID_WIRING_COLOR = new Color(220, 0, 0);
    private static final int SPATIAL_GRID_CELL_SIZE = 60;

    private static final long LEVEL_1_TIME_LIMIT_MS = 2 * 60 * 1000;
    private static final long LEVEL_2_TIME_LIMIT_MS = 4 * 60 * 1000;

    private final NetworkGame game;
    private final GameState gameState;
    private final GameRenderer gameRenderer;
    private final GameInputHandler gameInputHandler;
    private volatile boolean gameRunning = false;
    private volatile boolean gamePaused = false;
    private volatile boolean simulationStarted = false;
    private volatile boolean levelComplete = false;
    private volatile boolean gameOver = false;
    private int currentLevel = 1;
    private long lastTickTime = 0;
    private final Timer gameLoopTimer;
    private volatile long viewedTimeMs = 0;
    private final List<PacketSnapshot> predictedPacketStates = Collections.synchronizedList(new ArrayList<>());

    private final List<System> systems = Collections.synchronizedList(new ArrayList<>());
    private final List<Wire> wires = Collections.synchronizedList(new ArrayList<>());
    private final List<Packet> packets = Collections.synchronizedList(new ArrayList<>());
    private final List<Packet> packetsToAdd = Collections.synchronizedList(new ArrayList<>());
    private final List<Packet> packetsToRemove = Collections.synchronizedList(new ArrayList<>());
    private volatile boolean atarActive = false;
    private volatile boolean airyamanActive = false;
    private final Timer atarTimer;
    private final Timer airyamanTimer;
    private Port selectedOutputPort = null;
    private final Point mouseDragPos = new Point();
    private boolean wireDrawingMode = false;
    private Color currentWiringColor = DEFAULT_WIRING_COLOR;
    private boolean showHUD = true;
    private final Timer hudTimer;
    private int totalPacketsSuccessfullyDelivered = 0;
    private volatile long simulationTimeElapsedMs = 0;
    private volatile long currentLevelTimeLimitMs = 0;
    private volatile long maxPredictionTimeForScrubbingMs = 0;

    private final Set<Pair<Integer,Integer>> activelyCollidingPairs = new HashSet<>();
    private volatile boolean networkValidatedForPrediction = false; // Master flag for network validity
    private static final long PREDICTION_SEED = 12345L;

    public GamePanel(NetworkGame game) {
        this.game = Objects.requireNonNull(game, "NetworkGame instance cannot be null");
        this.gameState = Objects.requireNonNull(game.getGameState(), "GameState cannot be null");
        this.gameRenderer = new GameRenderer(this, gameState);
        this.gameInputHandler = new GameInputHandler(this, game);
        setBackground(BACKGROUND_COLOR);
        setPreferredSize(new Dimension(NetworkGame.WINDOW_WIDTH, NetworkGame.WINDOW_HEIGHT));
        setFocusable(true);
        addKeyListener(gameInputHandler);
        addMouseListener(gameInputHandler);
        addMouseMotionListener(gameInputHandler);
        ToolTipManager.sharedInstance().registerComponent(this);
        hudTimer = new Timer(HUD_DISPLAY_TIME_MS, e -> { showHUD = false; repaint(); });
        hudTimer.setRepeats(false);
        atarTimer = new Timer(ATAR_DURATION_MS, e -> deactivateAtar());
        atarTimer.setRepeats(false);
        airyamanTimer = new Timer(AIRYAMAN_DURATION_MS, e -> deactivateAiryaman());
        airyamanTimer.setRepeats(false);
        gameLoopTimer = new Timer(GAME_TICK_MS, e -> gameTick());
        gameLoopTimer.setRepeats(true);
    }

    public void initializeLevel(int level) {
        java.lang.System.out.println("Initializing GamePanel for Level " + level);
        stopSimulation();
        gameState.resetForLevel();
        this.currentLevel = level;
        this.networkValidatedForPrediction = false; // Reset validation state for the new level

        if (this.currentLevel == 1) {
            this.currentLevelTimeLimitMs = LEVEL_1_TIME_LIMIT_MS;
        } else if (this.currentLevel == 2) {
            this.currentLevelTimeLimitMs = LEVEL_2_TIME_LIMIT_MS;
        } else {
            this.currentLevelTimeLimitMs = LEVEL_1_TIME_LIMIT_MS;
            java.lang.System.out.println("Warning: Time limit not explicitly set for level " + this.currentLevel + ". Defaulting to " + (this.currentLevelTimeLimitMs / 1000) + "s.");
        }
        this.maxPredictionTimeForScrubbingMs = this.currentLevelTimeLimitMs;
        java.lang.System.out.println("Level " + this.currentLevel + " time limit: " + (this.currentLevelTimeLimitMs / 1000) + "s. Scrubbing limit: " + (this.maxPredictionTimeForScrubbingMs / 1000) + "s.");

        System.resetGlobalRandomSeed(PREDICTION_SEED);

        totalPacketsSuccessfullyDelivered = 0;
        levelComplete = false;
        gameOver = false;
        simulationStarted = false;
        wireDrawingMode = false;
        selectedOutputPort = null;
        currentWiringColor = DEFAULT_WIRING_COLOR;
        setCursor(Cursor.getDefaultCursor());
        viewedTimeMs = 0;
        simulationTimeElapsedMs = 0;
        activelyCollidingPairs.clear();
        clearLevelElements();
        deactivateAtar(); atarTimer.stop();
        deactivateAiryaman(); airyamanTimer.stop();

        LevelLoader.LevelLayout layout = LevelLoader.loadLevel(level, gameState, game);
        if (layout == null) {
            java.lang.System.err.println("Level loading failed for level " + level + ". Returning to menu.");
            game.returnToMenu();
            return;
        }
        synchronized (systems) { systems.clear(); systems.addAll(layout.systems); }
        synchronized (wires) { wires.clear(); wires.addAll(layout.wires); }
        this.currentLevel = layout.levelNumber;

        // Perform initial validation silently (without showing "Network Ready" message)
        String initialErrorMessage = getNetworkValidationErrorMessage();
        this.networkValidatedForPrediction = (initialErrorMessage == null);
        if (this.networkValidatedForPrediction) {
            java.lang.System.out.println("Level " + this.currentLevel + " loaded. Initial state: VALID for prediction.");
        } else {
            java.lang.System.out.println("Level " + this.currentLevel + " loaded. Initial state: INVALID for prediction. Reason: " + (initialErrorMessage != null ? initialErrorMessage : "Unknown"));
        }
        updatePrediction(); // Update prediction display based on initial state

        showHUD = true;
        hudTimer.restart();
        repaint();
        java.lang.System.out.println("Level " + this.currentLevel + " initialized. In Time Scrubbing mode.");
        java.lang.System.out.println("Use Left/Right Arrows to scrub time, Enter to start simulation.");
        SwingUtilities.invokeLater(this::requestFocusInWindow);
    }

    private void clearLevelElements() {
        synchronized (packets) { packets.clear(); }
        synchronized (packetsToAdd) { packetsToAdd.clear(); }
        synchronized (packetsToRemove) { packetsToRemove.clear(); }
        synchronized (predictedPacketStates) { predictedPacketStates.clear(); }
        synchronized (wires) {
            for (Wire w : wires) if (w != null) w.destroy();
            wires.clear();
        }
        synchronized (systems) { systems.clear(); }
    }

    public void attemptStartSimulation() {
        if (simulationStarted && gamePaused) {
            pauseGame(false);
            return;
        }
        if (simulationStarted || gameOver || levelComplete) return;

        // Get current validation state for starting simulation
        String validationMessage = getNetworkValidationErrorMessage();
        if (validationMessage != null) { // If network is NOT valid
            if (!game.isMuted()) game.playSoundEffect("error");
            JOptionPane.showMessageDialog(this,
                    "Network Validation Failed:\n" + validationMessage,
                    "Network Not Ready", JOptionPane.WARNING_MESSAGE);
            // Ensure our flag and predictions are up-to-date if dialog shown
            if (this.networkValidatedForPrediction) { // If flag thought it was valid, correct it
                this.networkValidatedForPrediction = false;
                updatePrediction(); // Clear predictions
            }
            return;
        }
        // If we reach here, network IS valid for simulation start.
        this.networkValidatedForPrediction = true; // Ensure flag is true

        java.lang.System.out.println("Network validated. Starting ACTUAL simulation...");
        System.resetGlobalRandomSeed(PREDICTION_SEED);

        // networkValidatedForPrediction is already true from above
        simulationStarted = true;
        gameRunning = true;
        gamePaused = false;
        lastTickTime = java.lang.System.nanoTime();
        viewedTimeMs = 0;
        simulationTimeElapsedMs = 0;
        activelyCollidingPairs.clear();
        synchronized(predictedPacketStates){ predictedPacketStates.clear();}
        gameLoopTimer.start();
        repaint();
    }

    private String getNetworkValidationErrorMessage() {
        List<System> currentSystems;
        List<Wire> currentWires;
        synchronized(systems) { currentSystems = new ArrayList<>(systems); }
        synchronized(wires) { currentWires = new ArrayList<>(wires); }
        if (!GraphUtils.isNetworkConnected(currentSystems, currentWires)) {
            return "All systems must be part of a single connected network.";
        }
        if (!GraphUtils.areAllSystemPortsConnected(currentSystems)) {
            return "All ports on every system (Sources, Nodes, Sinks) must be connected.";
        }
        return null;
    }

    /**
     * Validates the network and updates the prediction state.
     * Shows a "Network is fully connected" message if the network transitions
     * from an invalid to a valid state due to user action.
     * @return true if the network is currently valid for prediction, false otherwise.
     */
    private boolean validateAndSetPredictionFlag() {
        boolean oldState = this.networkValidatedForPrediction;
        String errorMessage = getNetworkValidationErrorMessage();
        boolean newStateIsValid = (errorMessage == null);

        this.networkValidatedForPrediction = newStateIsValid; // Update master state

        if (newStateIsValid && !oldState) { // If it *became* valid
            game.showTemporaryMessage("Network is fully connected and ready!", new Color(0,150,0), 2500);
            java.lang.System.out.println("VALIDATION (Flag Setter): Network became VALID for prediction.");
        } else if (!newStateIsValid && oldState) { // If it *became* invalid
            java.lang.System.out.println("VALIDATION (Flag Setter): Network became INVALID. Reason: " + (errorMessage != null ? errorMessage : "Unknown"));
        } else if (!newStateIsValid) { // If it's still invalid
            java.lang.System.out.println("VALIDATION (Flag Setter): Network remains INVALID. Reason: " + (errorMessage != null ? errorMessage : "Unknown"));
        }
        // If newStateIsValid && oldState (still valid), no message.

        updatePrediction(); // Update visuals based on the new validation status
        return this.networkValidatedForPrediction;
    }


    public void stopSimulation() {
        if (gameRunning || gameLoopTimer.isRunning()) {
            java.lang.System.out.println("<<< Stopping simulation loop. >>>");
        }
        gameRunning = false;
        gamePaused = false;
        if(gameLoopTimer.isRunning()) gameLoopTimer.stop();
        if(atarTimer.isRunning()) { deactivateAtar(); atarTimer.stop(); }
        if(airyamanTimer.isRunning()) { deactivateAiryaman(); airyamanTimer.stop(); }

        if (!simulationStarted) { // If we were in pre-sim mode (time scrubbing)
            // When simulation stops AND it was never started (i.e., user exits pre-sim mode),
            // re-evaluate the current network state for prediction display.
            // No "Network Ready" message here, as it's not a direct user wiring action.
            String currentError = getNetworkValidationErrorMessage();
            this.networkValidatedForPrediction = (currentError == null);
            updatePrediction();
        }
        repaint();
    }

    public void pauseGame(boolean pause) {
        if (!simulationStarted || gameOver || levelComplete) return;
        if (pause && !gamePaused) {
            gamePaused = true;
            if(gameLoopTimer.isRunning()) gameLoopTimer.stop();
            if(atarTimer.isRunning()) atarTimer.stop();
            if(airyamanTimer.isRunning()) airyamanTimer.stop();
            java.lang.System.out.println("|| Game Paused. SimTime: " + simulationTimeElapsedMs + "ms");
            repaint();
        } else if (!pause && gamePaused) {
            gamePaused = false;
            lastTickTime = java.lang.System.nanoTime();
            gameLoopTimer.start();
            if(atarActive && !atarTimer.isRunning()) atarTimer.start();
            if(airyamanActive && !airyamanTimer.isRunning()) airyamanTimer.start();
            java.lang.System.out.println(">> Game Resumed. SimTime: " + simulationTimeElapsedMs + "ms");
            repaint();
        }
    }

    private void gameTick() {
        if (!gameRunning || gamePaused || !simulationStarted) {
            return;
        }
        if (gameOver || levelComplete) {
            stopSimulation();
            return;
        }
        long currentTimeNano = java.lang.System.nanoTime();
        if (lastTickTime == 0) {
            lastTickTime = currentTimeNano;
            return;
        }
        double deltaTime = (currentTimeNano - lastTickTime) / 1_000_000_000.0;
        lastTickTime = currentTimeNano;
        deltaTime = Math.min(deltaTime, MAX_DELTA_TIME_SEC);
        if (!gamePaused && simulationStarted) {
            simulationTimeElapsedMs += (long)(deltaTime * 1000.0);
        }
        long currentTimeMillisForGeneration = java.lang.System.currentTimeMillis();
        processPacketBuffers();
        synchronized (systems) {
            List<System> systemsSnapshot = new ArrayList<>(systems);
            for (System s : systemsSnapshot) {
                if (s != null && s.isReferenceSystem() && s.hasOutputPorts()) {
                    s.attemptPacketGeneration(this, currentTimeMillisForGeneration);
                }
            }
        }
        List<Packet> currentPacketsSnapshot;
        synchronized (packets) {
            currentPacketsSnapshot = new ArrayList<>(packets);
        }
        for (Packet p : currentPacketsSnapshot) {
            if (p != null && !p.isMarkedForRemoval()) {
                p.update(this, airyamanActive);
            }
        }
        synchronized (systems) {
            List<System> systemsSnapshot = new ArrayList<>(systems);
            for (System s : systemsSnapshot) {
                if (s != null && !s.isReferenceSystem()) {
                    s.processQueue(this);
                }
            }
        }
        synchronized (packets) {
            currentPacketsSnapshot = new ArrayList<>(packets);
        }
        if (!airyamanActive) {
            detectAndHandleCollisionsBroadPhase(currentPacketsSnapshot);
        }
        processPacketBuffers();
        checkEndConditions();
        repaint();
    }

    private void processPacketBuffers() {
        if (!packetsToRemove.isEmpty()) {
            synchronized (packetsToRemove) {
                synchronized (packets) {
                    packets.removeAll(packetsToRemove);
                }
                packetsToRemove.clear();
            }
        }
        if (!packetsToAdd.isEmpty()) {
            synchronized (packetsToAdd) {
                synchronized (packets) {
                    packets.addAll(packetsToAdd);
                }
                packetsToAdd.clear();
            }
        }
    }

    private void detectAndHandleCollisionsBroadPhase(List<Packet> packetSnapshot) {
        if (packetSnapshot.isEmpty()) return;
        Map<Point, List<Packet>> spatialGrid = new HashMap<>();
        Set<Pair<Integer, Integer>> currentTickCollisions = new HashSet<>();
        Set<Pair<Integer, Integer>> checkedPairsThisTick = new HashSet<>();
        for (Packet p : packetSnapshot) {
            if (p == null || p.isMarkedForRemoval() || p.getCurrentSystem() != null || p.getCurrentWire() == null) continue;
            Point2D.Double pos = p.getPositionDouble();
            if (pos == null) continue;
            int cellX = (int) (pos.x / SPATIAL_GRID_CELL_SIZE);
            int cellY = (int) (pos.y / SPATIAL_GRID_CELL_SIZE);
            Point cellKey = new Point(cellX, cellY);
            spatialGrid.computeIfAbsent(cellKey, k -> new ArrayList<>()).add(p);
        }
        for (Map.Entry<Point, List<Packet>> entry : spatialGrid.entrySet()) {
            Point cellKey = entry.getKey();
            List<Packet> packetsInCell = entry.getValue();
            checkCollisionsInList(packetsInCell, packetsInCell, currentTickCollisions, checkedPairsThisTick, packetSnapshot);
            int[] dx = {1, 1, 0, -1};
            int[] dy = {0, 1, 1,  1};
            for (int i = 0; i < dx.length; i++) {
                Point neighborCellKey = new Point(cellKey.x + dx[i], cellKey.y + dy[i]);
                List<Packet> packetsInNeighborCell = spatialGrid.get(neighborCellKey);
                if (packetsInNeighborCell != null && !packetsInNeighborCell.isEmpty()) {
                    checkCollisionsInList(packetsInCell, packetsInNeighborCell, currentTickCollisions, checkedPairsThisTick, packetSnapshot);
                }
            }
        }
        Set<Pair<Integer, Integer>> pairsToRemoveFromActive = new HashSet<>(activelyCollidingPairs);
        pairsToRemoveFromActive.removeAll(currentTickCollisions);
        activelyCollidingPairs.removeAll(pairsToRemoveFromActive);
        activelyCollidingPairs.addAll(currentTickCollisions);
    }

    private void checkCollisionsInList(List<Packet> list1, List<Packet> list2,
                                       Set<Pair<Integer, Integer>> currentTickCollisions,
                                       Set<Pair<Integer, Integer>> checkedPairsThisTick,
                                       List<Packet> fullPacketSnapshotForImpactWave) {
        for (Packet p1 : list1) {
            if (p1 == null || p1.isMarkedForRemoval() || p1.getCurrentSystem() != null || p1.getCurrentWire() == null) continue;
            for (Packet p2 : list2) {
                if (p2 == null || p2.isMarkedForRemoval() || p2.getCurrentSystem() != null || p2.getCurrentWire() == null) continue;
                if (p1.getId() == p2.getId()) continue;
                Pair<Integer, Integer> currentPair = makeOrderedPair(p1.getId(), p2.getId());
                if (checkedPairsThisTick.contains(currentPair)) continue;
                if (p1.collidesWith(p2)) {
                    currentTickCollisions.add(currentPair);
                    if (!activelyCollidingPairs.contains(currentPair)) {
                        if (!game.isMuted()) game.playSoundEffect("collision");
                        p1.addNoise(DIRECT_COLLISION_NOISE_PER_PACKET);
                        p2.addNoise(DIRECT_COLLISION_NOISE_PER_PACKET);
                        Point2D.Double p1VisPos = p1.getPositionDouble();
                        Point2D.Double p2VisPos = p2.getPositionDouble();
                        if (p1VisPos != null && p2VisPos != null) {
                            double forceDirP1X = p1VisPos.x - p2VisPos.x;
                            double forceDirP1Y = p1VisPos.y - p2VisPos.y;
                            p1.setVisualOffsetDirectionFromForce(new Point2D.Double(forceDirP1X, forceDirP1Y));
                            double forceDirP2X = p2VisPos.x - p1VisPos.x;
                            double forceDirP2Y = p2VisPos.y - p1VisPos.y;
                            p2.setVisualOffsetDirectionFromForce(new Point2D.Double(forceDirP2X, forceDirP2Y));
                        }
                        if (!atarActive) {
                            Point impactCenter = calculateImpactCenter(p1.getPositionDouble(), p2.getPositionDouble());
                            if (impactCenter != null) {
                                handleImpactWaveNoise(impactCenter, fullPacketSnapshotForImpactWave, p1, p2);
                            }
                        }
                    }
                }
                checkedPairsThisTick.add(currentPair);
            }
        }
    }

    private Point calculateImpactCenter(Point2D.Double p1Pos, Point2D.Double p2Pos) {
        if (p1Pos != null && p2Pos != null) { return new Point((int)Math.round((p1Pos.getX() + p2Pos.getX()) / 2.0), (int)Math.round((p1Pos.getY() + p2Pos.getY()) / 2.0));
        } else if (p1Pos != null) { return new Point((int)Math.round(p1Pos.x), (int)Math.round(p1Pos.y));
        } else if (p2Pos != null) { return new Point((int)Math.round(p2Pos.x), (int)Math.round(p2Pos.y)); }
        return null;
    }

    private Pair<Integer, Integer> makeOrderedPair(int id1, int id2) {
        return (id1 < id2) ? new Pair<>(id1, id2) : new Pair<>(id2, id1);
    }

    private void handleImpactWaveNoise(Point center, List<Packet> snapshot, Packet ignore1, Packet ignore2) {
        double waveRadiusSq = IMPACT_WAVE_RADIUS * IMPACT_WAVE_RADIUS;
        for (Packet p : snapshot) {
            if (p == null || p.isMarkedForRemoval() || p.getCurrentSystem() != null || p == ignore1 || p == ignore2 || p.getCurrentWire() == null) continue;
            Point2D.Double pVisPos = p.getPositionDouble();
            if (pVisPos == null) continue;
            double distSq = center.distanceSq(pVisPos);
            if (distSq < waveRadiusSq && distSq > 1e-6) {
                double distance = Math.sqrt(distSq);
                double normalizedDistance = distance / IMPACT_WAVE_RADIUS;
                double noiseAmount = IMPACT_WAVE_MAX_NOISE * (1.0 - normalizedDistance);
                noiseAmount = Math.max(0.0, noiseAmount);
                if (noiseAmount > 0) {
                    double forceDirX = pVisPos.x - center.x;
                    double forceDirY = pVisPos.y - center.y;
                    p.setVisualOffsetDirectionFromForce(new Point2D.Double(forceDirX, forceDirY));
                    p.addNoise(noiseAmount);
                }
            }
        }
    }

    private void handleEndOfLevelByTimeLimit() {
        if (gameOver || levelComplete) return;
        java.lang.System.out.println("Level time limit reached: " + (currentLevelTimeLimitMs / 1000) + "s for Level " + currentLevel);
        stopSimulation();
        int packetsInLoopOrTransit = 0;
        synchronized (packets) {
            List<Packet> remainingPackets = new ArrayList<>(packets);
            for (Packet p : remainingPackets) {
                if (p != null && !p.isMarkedForRemoval()) {
                    gameState.increasePacketLoss(p);
                    packetsInLoopOrTransit++;
                }
            }
        }
        if (packetsInLoopOrTransit > 0) {
            java.lang.System.out.println(packetsInLoopOrTransit + " packets were still in transit or loop and counted as lost due to time limit.");
        }
        boolean lostTooMany = gameState.getPacketLossPercentage() >= PACKET_LOSS_GAME_OVER_THRESHOLD;
        if (lostTooMany) {
            gameOver = true;
            if (!game.isMuted()) game.playSoundEffect("game_over");
            java.lang.System.out.println("--- GAME OVER (Time Limit) --- Loss: " + String.format("%.1f%% >= %.1f%%", gameState.getPacketLossPercentage(), PACKET_LOSS_GAME_OVER_THRESHOLD));
            SwingUtilities.invokeLater(() -> showEndLevelDialog(false));
        } else {
            levelComplete = true;
            if (!game.isMuted()) game.playSoundEffect("level_complete");
            java.lang.System.out.println("--- LEVEL " + currentLevel + " ENDED BY TIME LIMIT (SUCCESS) --- Loss: " + String.format("%.1f%%", gameState.getPacketLossPercentage()));
            int nextLevelIndex = currentLevel;
            if (nextLevelIndex < gameState.getMaxLevels()) {
                gameState.unlockLevel(nextLevelIndex);
            } else {
                java.lang.System.out.println("Final level completed by time limit!");
            }
            SwingUtilities.invokeLater(() -> showEndLevelDialog(true));
        }
    }

    private void checkEndConditions() {
        if (gameOver || levelComplete || !simulationStarted) return;
        if (simulationTimeElapsedMs >= currentLevelTimeLimitMs) {
            handleEndOfLevelByTimeLimit();
            return;
        }
        boolean allSourcesFinishedGenerating = true;
        int sourcesChecked = 0;
        boolean sourcesHadPacketsToGenerate = false;
        synchronized (systems) {
            List<System> systemsSnapshot = new ArrayList<>(systems);
            for (System s : systemsSnapshot) {
                if (s != null && s.isReferenceSystem() && s.hasOutputPorts()) {
                    sourcesChecked++;
                    if (s.getTotalPacketsToGenerate() > 0) {
                        sourcesHadPacketsToGenerate = true;
                        if (s.getPacketsGeneratedCount() < s.getTotalPacketsToGenerate()) {
                            allSourcesFinishedGenerating = false;
                            break;
                        }
                    }
                }
            }
        }
        if (sourcesChecked == 0 || !sourcesHadPacketsToGenerate) {
            allSourcesFinishedGenerating = true;
        }
        if (!allSourcesFinishedGenerating) {
            return;
        }
        boolean packetsOnWireEmpty;
        boolean addBufferEmpty;
        boolean removeBufferEmpty;
        boolean queuesAreEmpty = true;
        synchronized (packets) { packetsOnWireEmpty = packets.isEmpty(); }
        synchronized (packetsToAdd) { addBufferEmpty = packetsToAdd.isEmpty(); }
        synchronized (packetsToRemove) { removeBufferEmpty = packetsToRemove.isEmpty(); }
        synchronized(systems) {
            List<System> systemsSnapshot = new ArrayList<>(systems);
            for(System s : systemsSnapshot) {
                if (s != null && !s.isReferenceSystem() && s.getQueueSize() > 0) {
                    queuesAreEmpty = false;
                    break;
                }
            }
        }
        if (allSourcesFinishedGenerating && packetsOnWireEmpty && addBufferEmpty && removeBufferEmpty && queuesAreEmpty) {
            if (!levelComplete && !gameOver) {
                stopSimulation();
                boolean lostTooMany = gameState.getPacketLossPercentage() >= PACKET_LOSS_GAME_OVER_THRESHOLD;
                if (lostTooMany) {
                    gameOver = true;
                    if (!game.isMuted()) game.playSoundEffect("game_over");
                    java.lang.System.out.println("--- GAME OVER (All Packets Processed, High Loss) --- Loss: " + String.format("%.1f%% >= %.1f%%", gameState.getPacketLossPercentage(), PACKET_LOSS_GAME_OVER_THRESHOLD));
                    SwingUtilities.invokeLater(() -> showEndLevelDialog(false));
                } else {
                    levelComplete = true;
                    if (!game.isMuted()) game.playSoundEffect("level_complete");
                    java.lang.System.out.println("--- LEVEL " + currentLevel + " COMPLETE (All Packets Processed) --- Loss: " + String.format("%.1f%%", gameState.getPacketLossPercentage()));
                    int nextLevelIndex = currentLevel;
                    if (nextLevelIndex < gameState.getMaxLevels()) {
                        gameState.unlockLevel(nextLevelIndex);
                    } else {
                        java.lang.System.out.println("Final level completed!");
                    }
                    SwingUtilities.invokeLater(() -> showEndLevelDialog(true));
                }
            }
        }
    }

    private void showEndLevelDialog(boolean success) {
        String title = success ? "Level " + currentLevel + " Complete!" : "Game Over!";
        StringBuilder message = new StringBuilder();
        message.append(success ? "Congratulations!" : "Simulation Failed!");
        message.append("\nLevel ").append(currentLevel).append(success ? " passed." : " failed.");
        if (!success) {
            if (simulationTimeElapsedMs >= currentLevelTimeLimitMs && gameState.getPacketLossPercentage() < PACKET_LOSS_GAME_OVER_THRESHOLD) {
                message.append(String.format("\nReason: Time limit of %.0f seconds reached.", currentLevelTimeLimitMs / 1000.0));
                message.append(String.format("\nPacket loss (%.1f%%) was acceptable, but time ran out.", gameState.getPacketLossPercentage()));
            } else if (simulationTimeElapsedMs >= currentLevelTimeLimitMs) {
                message.append(String.format("\nReason: Time limit of %.0f seconds reached AND packet loss (%.1f%%) exceeded %.1f%%.",
                        currentLevelTimeLimitMs / 1000.0, gameState.getPacketLossPercentage(), PACKET_LOSS_GAME_OVER_THRESHOLD));
            }
            else if (gameState.getPacketLossPercentage() >= PACKET_LOSS_GAME_OVER_THRESHOLD) {
                message.append(String.format("\nReason: Packet loss (%.1f%%) exceeded %.1f%%.", gameState.getPacketLossPercentage(), PACKET_LOSS_GAME_OVER_THRESHOLD));
            }
        }
        message.append("\n\n--- Results ---");
        message.append("\nPackets Delivered: ").append(totalPacketsSuccessfullyDelivered);
        message.append("\nPackets Generated: ").append(gameState.getTotalPacketsGeneratedCount());
        message.append("\nPackets Lost: ").append(gameState.getTotalPacketsLostCount());
        message.append("\nPacket Units Lost: ").append(gameState.getTotalPacketLossUnits()).append(" units (").append(String.format("%.1f%%", gameState.getPacketLossPercentage())).append(")");
        message.append("\nTotal Coins (Overall): ").append(gameState.getCoins());
        message.append("\nRemaining Wire Length: ").append(gameState.getRemainingWireLength());
        message.append("\nSimulation Time: ").append(String.format("%.2f / %.0f s", simulationTimeElapsedMs / 1000.0, currentLevelTimeLimitMs / 1000.0));
        List<String> optionsList = new ArrayList<>();
        String nextLevelOption = null;
        String retryOption = "Retry Level " + currentLevel;
        String menuOption = "Main Menu";
        int nextLevelNumber = currentLevel + 1;
        boolean nextLevelExists = nextLevelNumber <= gameState.getMaxLevels();
        boolean nextLevelUnlocked = nextLevelExists && gameState.isLevelUnlocked(currentLevel); // Check if *current* level completion unlocks next
        if (success) {
            if (nextLevelUnlocked) { // This should be true if success and next level exists
                nextLevelOption = "Next Level (" + nextLevelNumber + ")";
                optionsList.add(nextLevelOption);
            } else if (!nextLevelExists) { // currentLevel was the max level
                message.append("\n\nAll levels completed!");
            }
            optionsList.add(retryOption);
            optionsList.add(menuOption);
        } else { // Failed
            optionsList.add(retryOption);
            optionsList.add(menuOption);
        }
        Object[] options = optionsList.toArray();
        if (options.length == 0) options = new Object[]{menuOption}; // Fallback
        int choice = JOptionPane.showOptionDialog(this.game,
                message.toString(), title, JOptionPane.DEFAULT_OPTION,
                success ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE, null, options, options[0]);
        String selectedOption;
        if (choice == JOptionPane.CLOSED_OPTION || choice < 0 || choice >= options.length) {
            selectedOption = menuOption;
        } else {
            selectedOption = options[choice].toString();
        }
        if (selectedOption.equals(menuOption)) {
            game.returnToMenu();
        } else if (selectedOption.equals(retryOption)) {
            game.setLevel(currentLevel); // Ensure current level is re-selected
            game.startGame();
        } else if (nextLevelOption != null && selectedOption.equals(nextLevelOption)) {
            game.setLevel(nextLevelNumber);
            game.startGame();
        } else { // Should not happen if options are correctly managed
            game.returnToMenu();
        }
    }

    public void addPacket(Packet packet) { if (packet != null) { synchronized (packetsToAdd) { packetsToAdd.add(packet); } gameState.recordPacketGeneration(packet); } }

    public void packetLost(Packet packet) {
        if (packet != null && !packet.isMarkedForRemoval()) {
            packet.markForRemoval();
            synchronized (packetsToRemove) { packetsToRemove.add(packet); }
            gameState.increasePacketLoss(packet);
            if (!game.isMuted()) game.playSoundEffect("packet_loss");
            String reason = "Unknown";
            if (packet.getNoise() >= packet.getSize()) { reason = "Noise"; }
            if(reason.equals("Unknown")) {
                if (packet.getCurrentSystem() != null && packet.getCurrentSystem().isReferenceSystem() && !packet.getCurrentSystem().hasOutputPorts()){
                    reason = "Sink Rejection (Unexpected)";
                } else {
                    reason = "Queue Full / Routing Error / Off Wire";
                }
            }
            java.lang.System.out.println("LOST Pkt " + packet.getId() + " (Reason: "+reason+"). Loss now: " + String.format("%.1f%%", gameState.getPacketLossPercentage()));
        }
    }

    public void packetSuccessfullyDelivered(Packet packet) {
        if (packet != null && !packet.isMarkedForRemoval()) {
            packet.markForRemoval();
            synchronized (packetsToRemove) { packetsToRemove.add(packet); }
            totalPacketsSuccessfullyDelivered++;
            java.lang.System.out.println("DELIVERED Pkt " + packet.getId() + "! (Level Total: " + totalPacketsSuccessfullyDelivered + ")");
        }
    }
    public void addRoutingCoins(Packet packet) { if (packet != null) gameState.addCoins(packet.getBaseCoinValue()); }
    public void activateAtar() { if (!atarActive) { atarActive = true; java.lang.System.out.println(">>> Atar Activated (No Impact Wave Noise)"); } else { java.lang.System.out.println(">>> Atar Refreshed"); } atarTimer.restart(); repaint(); }
    private void deactivateAtar() { if (atarActive) { atarActive = false; java.lang.System.out.println("<<< Atar Deactivated"); repaint(); } atarTimer.stop(); }
    public void activateAiryaman() { if (!airyamanActive) { airyamanActive = true; java.lang.System.out.println(">>> Airyaman Activated (No Collision Effects)"); } else { java.lang.System.out.println(">>> Airyaman Refreshed"); } airyamanTimer.restart(); repaint(); }
    private void deactivateAiryaman() { if (airyamanActive) { airyamanActive = false; java.lang.System.out.println("<<< Airyaman Deactivated"); repaint(); } airyamanTimer.stop(); }
    public void activateAnahita() { int count = 0; List<Packet> snapshot; synchronized (packets) { snapshot = new ArrayList<>(packets); } for (Packet p : snapshot) { if (p != null && !p.isMarkedForRemoval() && p.getNoise() > 0) { p.resetNoise(); count++; } } java.lang.System.out.println(">>> Anahita Activated! Reset noise for " + count + " packet(s)."); repaint(); }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        gameRenderer.render(g);
    }

    public void startWiringMode(Port startPort, Point currentMousePos) {
        if (!wireDrawingMode && startPort != null && !simulationStarted &&
                startPort.getType() == NetworkEnums.PortType.OUTPUT && !startPort.isConnected()) {
            this.selectedOutputPort = startPort;
            this.mouseDragPos.setLocation(currentMousePos);
            this.wireDrawingMode = true;
            this.currentWiringColor = DEFAULT_WIRING_COLOR;
            setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
            repaint();
        } else if (simulationStarted) {
            if(!game.isMuted()) game.playSoundEffect("error");
            java.lang.System.out.println("Cannot start wiring: Simulation has started.");
        }
    }

    public void updateDragPos(Point currentMousePos) {
        if (wireDrawingMode) {
            this.mouseDragPos.setLocation(currentMousePos);
        }
    }

    public void updateWiringPreview(Point currentMousePos) {
        if (!wireDrawingMode || selectedOutputPort == null || selectedOutputPort.getPosition() == null) {
            this.currentWiringColor = DEFAULT_WIRING_COLOR;
            repaint(); // repaint to clear any previous wiring line color
            return;
        }
        Point startPos = selectedOutputPort.getPosition();
        double wireLength = startPos.distance(currentMousePos);
        if (gameState.getRemainingWireLength() < wireLength) {
            this.currentWiringColor = INVALID_WIRING_COLOR;
            repaint();
            return;
        }
        Port targetPort = findPortAt(currentMousePos);
        if (targetPort != null) {
            if (Objects.equals(targetPort.getParentSystem(), selectedOutputPort.getParentSystem())) {
                this.currentWiringColor = INVALID_WIRING_COLOR;
            } else if (targetPort.getType() != NetworkEnums.PortType.INPUT || targetPort.isConnected()) {
                this.currentWiringColor = INVALID_WIRING_COLOR;
            } else {
                this.currentWiringColor = VALID_WIRING_COLOR_TARGET;
            }
        } else {
            this.currentWiringColor = DEFAULT_WIRING_COLOR;
        }
        repaint();
    }

    public boolean attemptWireCreation(Port startPort, Port endPort) {
        if (simulationStarted) {
            java.lang.System.err.println("Wire creation failed: Cannot create wires during simulation.");
            return false;
        }
        if (startPort == null || endPort == null || startPort.getPosition() == null || endPort.getPosition() == null) {
            java.lang.System.err.println("Wire creation failed: Start or end port is null or has no position.");
            return false;
        }
        if (startPort.getType() != NetworkEnums.PortType.OUTPUT ||
                endPort.getType() != NetworkEnums.PortType.INPUT ||
                startPort.isConnected() || endPort.isConnected() ||
                Objects.equals(startPort.getParentSystem(), endPort.getParentSystem())) {
            java.lang.System.err.println("Wire creation failed: Logical preconditions not met (type, connection, same parent).");
            return false;
        }
        int wireLength = (int) Math.round(startPort.getPosition().distance(endPort.getPosition()));
        if (wireLength <= 0) {
            java.lang.System.err.println("Wire creation failed: Zero or negative length.");
            return false;
        }
        if (gameState.useWire(wireLength)) {
            try {
                Wire newWire = new Wire(startPort, endPort);
                synchronized (wires) {
                    wires.add(newWire);
                }
                java.lang.System.out.println("Wire created: " + newWire.getId() + " Len: " + wireLength + ". Rem: " + gameState.getRemainingWireLength());
                if (!game.isMuted()) game.playSoundEffect("wire_connect");
                validateAndSetPredictionFlag(); // Validate and update prediction (may show "Network Ready")
                return true;
            } catch (IllegalArgumentException | IllegalStateException e) {
                java.lang.System.err.println("Wire creation failed in constructor: " + e.getMessage());
                gameState.returnWire(wireLength); // Return wire if constructor fails
                JOptionPane.showMessageDialog(this.game, "Cannot create wire:\n" + e.getMessage(), "Wiring Error", JOptionPane.WARNING_MESSAGE);
                if (!game.isMuted()) game.playSoundEffect("error");
                validateAndSetPredictionFlag(); // Re-validate even on failure as port states might be involved
                return false;
            }
        } else {
            JOptionPane.showMessageDialog(this.game,
                    "Not enough wire! Need: " + wireLength + ", Have: " + gameState.getRemainingWireLength(),
                    "Insufficient Wire", JOptionPane.WARNING_MESSAGE);
            if (!game.isMuted()) game.playSoundEffect("error");
            return false;
        }
    }

    public void cancelWiring() {
        if(wireDrawingMode) {
            selectedOutputPort = null;
            wireDrawingMode = false;
            currentWiringColor = DEFAULT_WIRING_COLOR;
            setCursor(Cursor.getDefaultCursor());
            repaint();
        }
    }

    public void deleteWireRequest(Wire wireToDelete) {
        if (wireToDelete == null || simulationStarted) {
            if(simulationStarted) {
                if(!game.isMuted()) game.playSoundEffect("error");
                java.lang.System.out.println("Cannot delete wire: Sim started.");
            }
            return;
        }
        boolean removed;
        synchronized (wires) {
            removed = wires.remove(wireToDelete);
        }
        if (removed) {
            int returnedLength = (int) Math.round(wireToDelete.getLength());
            gameState.returnWire(returnedLength);
            wireToDelete.destroy(); // This disconnects ports
            java.lang.System.out.println("Wire " + wireToDelete.getId() + " deleted. Ret: " + returnedLength + ". Rem: " + gameState.getRemainingWireLength());
            if (!game.isMuted()) game.playSoundEffect("wire_disconnect"); // Sound for successful deletion
            validateAndSetPredictionFlag(); // Re-validate network state
        } else {
            java.lang.System.err.println("Warn: Failed to remove wire " + wireToDelete.getId() + " (not found in list).");
        }
    }

    public Port findPortAt(Point p) {
        if (p == null) return null;
        List<System> systemsSnapshot;
        synchronized (this.systems) {
            systemsSnapshot = new ArrayList<>(this.systems);
        }
        for (System s : systemsSnapshot) {
            if (s != null) {
                Port port = s.getPortAt(p);
                if (port != null) return port;
            }
        }
        return null;
    }

    public Wire findWireAt(Point p) {
        if (p == null) return null;
        final double CLICK_THRESHOLD = 10.0;
        final double CLICK_THRESHOLD_SQ = CLICK_THRESHOLD * CLICK_THRESHOLD;
        Wire closestWire = null;
        double minDistanceSq = Double.MAX_VALUE;
        List<Wire> wiresSnapshot;
        synchronized (this.wires) {
            wiresSnapshot = new ArrayList<>(this.wires);
        }
        for (Wire w : wiresSnapshot) {
            if (w == null || w.getStartPort() == null || w.getEndPort() == null ||
                    w.getStartPort().getPosition() == null || w.getEndPort().getPosition() == null) {
                continue;
            }
            Point start = w.getStartPort().getPosition();
            Point end = w.getEndPort().getPosition();
            double distSq = Line2D.ptSegDistSq(start.x, start.y, end.x, end.y, p.x, p.y);
            if (distSq < minDistanceSq) {
                minDistanceSq = distSq;
                closestWire = w;
            }
        }
        return (closestWire != null && minDistanceSq < CLICK_THRESHOLD_SQ) ? closestWire : null;
    }

    public Wire findWireFromPort(Port outputPort) {
        if (outputPort == null || outputPort.getType() != NetworkEnums.PortType.OUTPUT) return null;
        synchronized (wires) {
            List<Wire> wiresSnapshot = new ArrayList<>(wires); // Create a snapshot for iteration
            for (Wire w : wiresSnapshot) {
                if (w != null && Objects.equals(w.getStartPort(), outputPort)) return w;
            }
        }
        return null;
    }

    public boolean isWireOccupied(Wire wire) {
        if (wire == null || !simulationStarted) return false; // Can't be occupied if sim not started or wire is null
        synchronized (packets) {
            List<Packet> currentPackets = new ArrayList<>(packets); // Snapshot
            for (Packet p : currentPackets) {
                if (p != null && !p.isMarkedForRemoval() && Objects.equals(p.getCurrentWire(), wire)) {
                    return true; // Found a packet on this wire
                }
            }
        }
        return false; // No packets found on this wire
    }

    public void toggleHUD() {
        showHUD = !showHUD;
        if (showHUD) hudTimer.restart();
        else hudTimer.stop();
        repaint();
    }

    public void incrementViewedTime() {
        if (!simulationStarted) {
            // Check if network is currently valid based on the flag
            if (!this.networkValidatedForPrediction) {
                String validationMessage = getNetworkValidationErrorMessage(); // Get a fresh message for dialog
                if(!game.isMuted()) game.playSoundEffect("error");
                JOptionPane.showMessageDialog(this,
                        "Cannot scrub time:\n" + (validationMessage != null ? validationMessage : "Network is not fully connected."),
                        "Network Not Ready for Scrubbing", JOptionPane.WARNING_MESSAGE);
                return; // Do not proceed with scrubbing
            }
            // If we are here, networkValidatedForPrediction is true.
            viewedTimeMs = Math.min(maxPredictionTimeForScrubbingMs, viewedTimeMs + TIME_SCRUB_INCREMENT_MS);
            updatePrediction(); // This updates prediction display based on new time
        }
    }

    public void decrementViewedTime() {
        if (!simulationStarted) {
            // Check if network is currently valid based on the flag
            if (!this.networkValidatedForPrediction) {
                String validationMessage = getNetworkValidationErrorMessage(); // Get a fresh message for dialog
                if(!game.isMuted()) game.playSoundEffect("error");
                JOptionPane.showMessageDialog(this,
                        "Cannot scrub time:\n" + (validationMessage != null ? validationMessage : "Network is not fully connected."),
                        "Network Not Ready for Scrubbing", JOptionPane.WARNING_MESSAGE);
                return; // Do not proceed with scrubbing
            }
            // If we are here, networkValidatedForPrediction is true.
            viewedTimeMs = Math.max(0, viewedTimeMs - TIME_SCRUB_INCREMENT_MS);
            updatePrediction(); // This updates prediction display based on new time
        }
    }


    /**
     * Updates the prediction display. This should be called whenever the
     * viewedTimeMs changes or the network structure (potentially affecting validation) changes.
     */
    private void updatePrediction() {
        if (simulationStarted) {
            synchronized(predictedPacketStates) {
                if (!predictedPacketStates.isEmpty()) {
                    predictedPacketStates.clear();
                    // Repaint will happen naturally or by game tick
                }
            }
            return; // No predictions if simulation is running
        }

        // If network is not validated for prediction, clear existing predictions
        if (!networkValidatedForPrediction) {
            synchronized(predictedPacketStates) {
                if (!predictedPacketStates.isEmpty()) {
                    predictedPacketStates.clear();
                }
            }
            repaint(); // Repaint to show cleared predictions
            return;
        }

        // If network IS validated, generate new predictions
        Random predictionRunRandom = new Random(PREDICTION_SEED); // Consistent seed for prediction
        List<PacketSnapshot> newPrediction = predictNetworkStateDetailed(this.viewedTimeMs, predictionRunRandom);

        synchronized(predictedPacketStates) {
            predictedPacketStates.clear();
            predictedPacketStates.addAll(newPrediction);
        }
        repaint(); // Repaint to show new predictions
    }


    private List<PacketSnapshot> predictNetworkStateDetailed(long targetTimeMs, Random predictionRunRandomInstance) {
        List<PacketSnapshot> snapshots = new ArrayList<>();
        List<System.PredictedPacketInfo> potentialPacketsInfo = new ArrayList<>();
        synchronized (systems) {
            List<System> currentSystems = new ArrayList<>(systems);
            for (System s : currentSystems) {
                if (s != null && s.isReferenceSystem() && s.hasOutputPorts()) {
                    potentialPacketsInfo.addAll(s.predictGeneratedPackets(maxPredictionTimeForScrubbingMs));
                }
            }
        }
        for (System.PredictedPacketInfo packetInfo : potentialPacketsInfo) {
            long currentSimTime = packetInfo.generationTimeMs;
            Port currentPort = packetInfo.sourcePort;
            if (currentSimTime > targetTimeMs) {
                snapshots.add(new PacketSnapshot(packetInfo.futurePacketId, packetInfo.shape));
                continue;
            }
            if (currentPort == null || currentPort.getPosition() == null) {
                // This case implies a source port somehow became invalid post-generation prediction
                // or was never properly initialized in the prediction.
                snapshots.add(new PacketSnapshot(packetInfo.futurePacketId, packetInfo.shape, null, PredictedPacketStatus.LOST, 0.0));
                java.lang.System.err.println("Prediction Error: PktInfo " + packetInfo.futurePacketId + " has null source port or position.");
                continue;
            }
            Point currentPosition = new Point(currentPort.getPosition()); // Start at source port's position
            Wire currentWire = findWireFromPort(currentPort); // Find wire connected to this source port
            double progress = 0.0; // Progress along the currentWire
            PredictedPacketStatus currentStatus = PredictedPacketStatus.ON_WIRE;
            int currentSystemId = -1; // Only relevant if STALLED_AT_NODE or QUEUED

            // Initial state check: Is there even a wire connected to the source port?
            if (currentWire == null) {
                currentStatus = PredictedPacketStatus.STALLED_AT_NODE; // Stalled at the source system itself
                currentSystemId = currentPort.getParentSystem() != null ? currentPort.getParentSystem().getId() : -2; // Use -2 for unknown parent
                snapshots.add(new PacketSnapshot(packetInfo.futurePacketId, packetInfo.shape, currentPosition, currentStatus, currentSystemId));
                java.lang.System.out.println("Prediction: Pkt " + packetInfo.futurePacketId + " STALLED at source " + currentSystemId + " (no wire).");
                continue;
            }

            // Determine packet speed and acceleration based on shape and exit port compatibility
            NetworkEnums.PacketShape packetShapeEnum = packetInfo.shape;
            NetworkEnums.PortShape exitPortShape = currentPort.getShape();
            NetworkEnums.PortShape requiredPortShapeForPacket = Port.getShapeEnum(packetShapeEnum);
            boolean compatibleExit = (requiredPortShapeForPacket != null && (exitPortShape == requiredPortShapeForPacket ));

            double currentSpeed; boolean isAccelerating; double targetSpeed;
            if (packetShapeEnum == NetworkEnums.PacketShape.SQUARE) {
                currentSpeed = compatibleExit ? (Packet.BASE_SPEED_MAGNITUDE * Packet.SQUARE_COMPATIBLE_SPEED_FACTOR) : Packet.BASE_SPEED_MAGNITUDE;
                isAccelerating = false; targetSpeed = currentSpeed;
            } else if (packetShapeEnum == NetworkEnums.PacketShape.TRIANGLE) {
                currentSpeed = Packet.BASE_SPEED_MAGNITUDE; isAccelerating = !compatibleExit;
                targetSpeed = isAccelerating ? Packet.MAX_SPEED_MAGNITUDE : Packet.BASE_SPEED_MAGNITUDE;
            } else { // Fallback for any other shapes, though not expected by current Packet enum
                currentSpeed = Packet.BASE_SPEED_MAGNITUDE; isAccelerating = false; targetSpeed = currentSpeed;
            }
            double currentSimSpeedPerMs = currentSpeed / GAME_TICK_MS; // Average speed per ms for discrete simulation step

            // Simulate packet movement until targetTimeMs is reached or packet is delivered/lost/stalled
            while (currentSimTime < targetTimeMs) {
                if (currentWire == null) { // Should be caught earlier, but safety break
                    java.lang.System.err.println("Prediction Error: Pkt " + packetInfo.futurePacketId + " lost wire mid-path.");
                    currentStatus = PredictedPacketStatus.LOST; // Or STALLED if position known
                    break;
                }

                double timeRemMsInPredictionWindowForPacket = targetTimeMs - currentSimTime;
                if (timeRemMsInPredictionWindowForPacket <= PREDICTION_FLOAT_TOLERANCE) break; // Effectively at target time

                double wireLen = currentWire.getLength();
                if (wireLen < PREDICTION_FLOAT_TOLERANCE) { // Effectively zero-length wire
                    progress = 1.0; // Consider it traversed instantly
                }

                double distRemOnWire = wireLen * (1.0 - progress);
                double effectiveSpeedForSimTick = currentSpeed; // Base speed for this tick
                if(isAccelerating) { // Simplified acceleration: use average if accelerating, or update speed incrementally
                    // For simplicity in prediction, let's assume it instantly reaches target or averages.
                    // A more complex prediction would step through acceleration.
                    // Let's use a simple model: if accelerating, it might take some time to reach target.
                    // For now, assume it moves at currentSpeed, and if it finishes wire, then update speed.
                    // Or, if timeToFinish < timeToAccelerate, use average. This is complex for simple prediction.
                    // Let's stick to currentSpeed for the segment, and update speed if it transitions.
                    // Alternative: If accelerating, assume it reaches targetSpeed quickly for prediction.
                    effectiveSpeedForSimTick = (currentSpeed + targetSpeed) / 2.0; // More realistic average during accel
                }
                currentSimSpeedPerMs = effectiveSpeedForSimTick / GAME_TICK_MS;


                if (currentSimSpeedPerMs < PREDICTION_FLOAT_TOLERANCE / GAME_TICK_MS && distRemOnWire > PREDICTION_FLOAT_TOLERANCE) {
                    currentStatus = PredictedPacketStatus.STALLED_AT_NODE; // Stuck due to zero speed
                    // Update position to current progress on wire
                    Point newPos = currentWire.getPointAtProgress(progress);
                    if (newPos != null) currentPosition = newPos;
                    // Determine which system it's stalled AT (either source or destination of current wire)
                    currentSystemId = currentWire.getEndPort() != null && currentWire.getEndPort().getParentSystem() != null ?
                            currentWire.getEndPort().getParentSystem().getId() :
                            (currentWire.getStartPort() != null && currentWire.getStartPort().getParentSystem() != null ?
                                    currentWire.getStartPort().getParentSystem().getId() : -3);
                    java.lang.System.out.println("Prediction: Pkt " + packetInfo.futurePacketId + " STALLED at " + currentSystemId + " (zero speed).");
                    break;
                }

                double timeToFinishMsOnWire = (wireLen > PREDICTION_FLOAT_TOLERANCE && currentSimSpeedPerMs > PREDICTION_FLOAT_TOLERANCE / GAME_TICK_MS) ?
                        (distRemOnWire / currentSimSpeedPerMs) : 0.0;

                if (timeToFinishMsOnWire <= timeRemMsInPredictionWindowForPacket + PREDICTION_FLOAT_TOLERANCE) {
                    // Packet reaches end of current wire within the remaining prediction time window
                    currentSimTime += timeToFinishMsOnWire;
                    progress = 1.0;
                    Port endPort = currentWire.getEndPort();

                    if (endPort == null || endPort.getPosition() == null) {
                        currentStatus = PredictedPacketStatus.LOST;
                        currentPosition = (currentWire.getEndPort() != null && currentWire.getEndPort().getPosition() != null) ? currentWire.getEndPort().getPosition() : currentPosition;
                        java.lang.System.err.println("Prediction Error: Pkt " + packetInfo.futurePacketId + " reached invalid end port on Wire " + currentWire.getId());
                        currentWire = null; break;
                    }
                    currentPosition.setLocation(endPort.getPosition());
                    System nextSystem = endPort.getParentSystem();

                    if (nextSystem == null) { // Should not happen if endPort is valid
                        currentStatus = PredictedPacketStatus.LOST;
                        java.lang.System.err.println("Prediction Error: Pkt " + packetInfo.futurePacketId + " reached end port with no parent system.");
                        currentWire = null; break;
                    }

                    if (nextSystem.isReferenceSystem() && !nextSystem.hasOutputPorts()) { // Reached a Sink
                        currentStatus = PredictedPacketStatus.DELIVERED;
                        java.lang.System.out.println("Prediction: Pkt " + packetInfo.futurePacketId + " DELIVERED to Sink " + nextSystem.getId());
                        currentWire = null; break; // End of path
                    } else { // Reached a Node or a Source (which is an error path for prediction)
                        if (nextSystem.isReferenceSystem() && nextSystem.hasOutputPorts()){
                            currentStatus = PredictedPacketStatus.LOST; // Error: packet routed to a source system
                            currentSystemId = nextSystem.getId();
                            java.lang.System.err.println("Prediction Error: Pkt " + packetInfo.futurePacketId + " incorrectly routed to Source system " + currentSystemId + ".");
                            currentWire = null; break;
                        }

                        Port nextOutPort = findPredictedNextPort(packetInfo.shape, nextSystem, predictionRunRandomInstance);
                        if (nextOutPort != null && nextOutPort.getPosition() != null) {
                            Wire nextW = findWireFromPort(nextOutPort);
                            if (nextW != null) { // Found a valid onward wire
                                currentWire = nextW;
                                currentPort = nextOutPort; // The new source port for the next segment
                                currentPosition.setLocation(currentPort.getPosition());
                                progress = 0.0;
                                currentStatus = PredictedPacketStatus.ON_WIRE; // Continue on new wire

                                // Update speed/acceleration for the new wire segment
                                exitPortShape = currentPort.getShape();
                                requiredPortShapeForPacket = Port.getShapeEnum(packetShapeEnum); // Re-check, though packet shape is constant
                                compatibleExit = (requiredPortShapeForPacket != null && (exitPortShape == requiredPortShapeForPacket ));

                                if (packetShapeEnum == NetworkEnums.PacketShape.SQUARE) {
                                    currentSpeed = compatibleExit ? (Packet.BASE_SPEED_MAGNITUDE * Packet.SQUARE_COMPATIBLE_SPEED_FACTOR) : Packet.BASE_SPEED_MAGNITUDE;
                                    isAccelerating = false; targetSpeed = currentSpeed;
                                } else if (packetShapeEnum == NetworkEnums.PacketShape.TRIANGLE) {
                                    currentSpeed = Packet.BASE_SPEED_MAGNITUDE; isAccelerating = !compatibleExit;
                                    targetSpeed = isAccelerating ? Packet.MAX_SPEED_MAGNITUDE : Packet.BASE_SPEED_MAGNITUDE;
                                }
                                // currentSimSpeedPerMs will be recalculated at start of loop
                            } else { // Next port found, but no wire connected to it (should be caught by graph validation)
                                currentStatus = PredictedPacketStatus.STALLED_AT_NODE;
                                currentPosition.setLocation(nextSystem.getPosition()); // Center of the node
                                currentSystemId = nextSystem.getId();
                                java.lang.System.out.println("Prediction: Pkt " + packetInfo.futurePacketId + " STALLED at Node " + currentSystemId + " (next port " + nextOutPort.getId() + " has no wire).");
                                currentWire = null; break;
                            }
                        } else { // No suitable output port found at the node
                            currentStatus = PredictedPacketStatus.STALLED_AT_NODE;
                            currentPosition.setLocation(nextSystem.getPosition()); // Center of the node
                            currentSystemId = nextSystem.getId();
                            java.lang.System.out.println("Prediction: Pkt " + packetInfo.futurePacketId + " STALLED at Node " + currentSystemId + " (no suitable output port).");
                            currentWire = null; break;
                        }
                    }
                } else {
                    // Packet does not reach end of wire within the time window, calculate intermediate position
                    double distToMove = currentSimSpeedPerMs * timeRemMsInPredictionWindowForPacket;
                    if (wireLen > PREDICTION_FLOAT_TOLERANCE) {
                        progress += (distToMove / wireLen);
                    }
                    progress = Math.max(0.0, Math.min(progress, 1.0)); // Clamp progress

                    Point newPos = currentWire.getPointAtProgress(progress);
                    if (newPos != null) currentPosition = newPos;
                    else java.lang.System.err.println("Prediction Warn: Cannot get position for Pkt " + packetInfo.futurePacketId + " on Wire " + (currentWire != null ? currentWire.getId() : "null") + " at progress " + String.format("%.2f", progress) );

                    currentSimTime = targetTimeMs; // Consumed all remaining time in this window
                    currentStatus = PredictedPacketStatus.ON_WIRE;
                    break; // Exit loop as we've reached targetTimeMs for this packet
                }
            } // End of while(currentSimTime < targetTimeMs)

            // Add snapshot based on final status
            if (currentStatus == PredictedPacketStatus.STALLED_AT_NODE) {
                snapshots.add(new PacketSnapshot(packetInfo.futurePacketId, packetInfo.shape, currentPosition, currentStatus, currentSystemId));
            } else { // ON_WIRE, DELIVERED, LOST
                snapshots.add(new PacketSnapshot(packetInfo.futurePacketId, packetInfo.shape, currentPosition, currentStatus, progress));
            }
        }
        return snapshots;
    }


    private Port findPredictedNextPort(NetworkEnums.PacketShape packetShape, System node, Random randomForPrediction) {
        if (node == null || (node.isReferenceSystem() && !node.hasOutputPorts())) return null; // Should not route to Sinks this way
        if (node.isReferenceSystem() && node.hasOutputPorts()) {
            java.lang.System.err.println("Prediction Warning: Packet ("+packetShape+") reached a Source system ("+node.getId()+") during prediction path. This implies an invalid route where a packet is sent back to a source.");
            return null; // Invalid path
        }

        List<Port> compatiblePorts = new ArrayList<>();
        List<Port> otherNonAnyConnectedPorts = new ArrayList<>(); // For non-matching shapes

        NetworkEnums.PortShape requiredShape = Port.getShapeEnum(packetShape);
        if (requiredShape == null) {
            java.lang.System.err.println("Prediction Error: Cannot determine required port shape for packet shape " + packetShape);
            return null; // Cannot route if packet shape is unknown
        }

        // Synchronize access to outputPorts if modifying System class or its port lists concurrently
        // For prediction, we usually work with a snapshot or assume no concurrent modification.
        // If System.getOutputPorts() returns a synchronized list or a copy, direct iteration is fine.
        // Assuming getOutputPorts() provides a safe list for iteration:
        List<Port> currentOutputPorts = node.getOutputPorts(); // Get a safe copy or unmodifiable list

        for (Port p : currentOutputPorts) {
            if (p != null && p.isConnected()) { // Only consider connected ports
                // Check if a wire is actually connected from this port (findWireFromPort does this)
                if (findWireFromPort(p) == null) continue; // Port claims connected, but no wire found by GamePanel

                if (p.getShape() == requiredShape) {
                    compatiblePorts.add(p);
                } else {
                    // We only consider specific shapes for routing, ANY is not a routable shape here
                    // This logic aligns with how System.findAvailableOutputPort prioritizes
                    if (p.getShape() == NetworkEnums.PortShape.SQUARE || p.getShape() == NetworkEnums.PortShape.TRIANGLE) {
                        otherNonAnyConnectedPorts.add(p);
                    }
                }
            }
        }

        if (!compatiblePorts.isEmpty()) {
            Collections.shuffle(compatiblePorts, randomForPrediction);
            return compatiblePorts.get(0);
        }
        // If no compatible ports, try other specific-shaped (non-ANY) ports
        if (!otherNonAnyConnectedPorts.isEmpty()) {
            Collections.shuffle(otherNonAnyConnectedPorts, randomForPrediction);
            return otherNonAnyConnectedPorts.get(0);
        }

        return null; // No suitable connected output port found
    }


    public NetworkGame getGame() { return game; }
    public GameState getGameState() { return gameState; }
    public List<System> getSystems() { return Collections.unmodifiableList(systems); }
    public List<Wire> getWires() { return Collections.unmodifiableList(wires); }
    public List<Packet> getPackets() { return Collections.unmodifiableList(packets); }
    public List<PacketSnapshot> getPredictedPacketStates() { return predictedPacketStates; }
    public boolean isGameRunning() { return gameRunning; }
    public boolean isGamePaused() { return gamePaused; }
    public boolean isSimulationStarted() { return simulationStarted; }
    public boolean isLevelComplete() { return levelComplete; }
    public boolean isGameOver() { return gameOver; }
    public int getCurrentLevel() { return currentLevel; }
    public boolean isWireDrawingMode() { return wireDrawingMode; }
    public Port getSelectedOutputPort() { return selectedOutputPort; }
    public Point getMouseDragPos() { return mouseDragPos; }
    public Color getCurrentWiringColor() { return currentWiringColor; }
    public boolean isShowHUD() { return showHUD; }
    public boolean isAtarActive() { return atarActive; }
    public boolean isAiryamanActive() { return airyamanActive; }
    public long getViewedTimeMs() { return viewedTimeMs; }
    public long getMaxPredictionTimeMs() { return maxPredictionTimeForScrubbingMs; }
    public long getSimulationTimeElapsedMs() { return simulationTimeElapsedMs; }
    public long getCurrentLevelTimeLimitMs() { return currentLevelTimeLimitMs; }
    public boolean isNetworkValidatedForPrediction() { return networkValidatedForPrediction; }
}