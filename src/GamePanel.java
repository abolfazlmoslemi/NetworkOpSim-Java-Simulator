// ======= GamePanel.java =======

// FILE: GamePanel.java
// ===== GamePanel.java =====
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
// import java.util.Random; // Not directly used in GamePanel for simulation choices
import java.awt.geom.Point2D;
import java.awt.geom.Line2D;

public class GamePanel extends JPanel {
    // Pair class (remains the same)
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

    private static final int GAME_TICK_MS = 16; // Approx 60 FPS
    private static final int HUD_DISPLAY_TIME_MS = 7000;
    private static final int ATAR_DURATION_MS = 10000;
    private static final int AIRYAMAN_DURATION_MS = 5000;
    private static final Color BACKGROUND_COLOR = new Color(15, 15, 20);
    private static final double MAX_DELTA_TIME_SEC = 0.1; // Max frame time to prevent large jumps
    private static final double PACKET_LOSS_GAME_OVER_THRESHOLD = 50.0; // Percentage
    private static final long TIME_SCRUB_INCREMENT_MS = 1000; // 1 second increment for scrubbing
    // private static final double PREDICTION_FLOAT_TOLERANCE = 1e-9; // No longer explicitly used here
    private static final double DIRECT_COLLISION_NOISE_PER_PACKET = 1.0;
    private static final double IMPACT_WAVE_RADIUS = 180.0;
    private static final double IMPACT_WAVE_MAX_NOISE = 1.0;

    public static final Color DEFAULT_WIRING_COLOR = Color.LIGHT_GRAY;
    public static final Color VALID_WIRING_COLOR_TARGET = new Color(0, 200, 0);
    public static final Color INVALID_WIRING_COLOR = new Color(220, 0, 0);
    private static final int SPATIAL_GRID_CELL_SIZE = 60; // For collision detection optimization

    // Level-specific time limits
    private static final long LEVEL_1_TIME_LIMIT_MS = 2 * 60 * 1000; // 2 minutes
    private static final long LEVEL_2_TIME_LIMIT_MS = 4 * 60 * 1000; // 4 minutes

    private final NetworkGame game;
    private final GameState gameState;
    private final GameRenderer gameRenderer;
    private final GameInputHandler gameInputHandler;

    private volatile boolean gameRunning = false;
    private volatile boolean gamePaused = false;
    private volatile boolean simulationStarted = false; // True after "Start Simulation" is pressed
    private volatile boolean levelComplete = false;
    private volatile boolean gameOver = false;
    private int currentLevel = 1;
    private long lastTickTime = 0; // For calculating deltaTime
    private final Timer gameLoopTimer;

    // Time scrubbing and prediction state
    private volatile long viewedTimeMs = 0; // Current time being viewed in pre-simulation scrubbing
    private final List<PacketSnapshot> predictedPacketStates = Collections.synchronizedList(new ArrayList<>());
    private volatile PredictionRunStats displayedPredictionStats = new PredictionRunStats(0,0,0,0,0);
    private List<Packet> tempPredictionRunGeneratedPackets = new ArrayList<>(); // Stores all packets from one prediction run


    // Game elements
    private final List<System> systems = Collections.synchronizedList(new ArrayList<>());
    private final List<Wire> wires = Collections.synchronizedList(new ArrayList<>());
    private final List<Packet> packets = Collections.synchronizedList(new ArrayList<>()); // Active packets in simulation
    private final List<Packet> packetsToAdd = Collections.synchronizedList(new ArrayList<>());
    private final List<Packet> packetsToRemove = Collections.synchronizedList(new ArrayList<>());

    // Power-ups
    private volatile boolean atarActive = false;
    private volatile boolean airyamanActive = false;
    private final Timer atarTimer;
    private final Timer airyamanTimer;

    // UI and Interaction State
    private Port selectedOutputPort = null; // For wire drawing
    private final Point mouseDragPos = new Point(); // Current mouse position during wire drag
    private boolean wireDrawingMode = false;
    private Color currentWiringColor = DEFAULT_WIRING_COLOR;
    private boolean showHUD = true;
    private final Timer hudTimer; // Timer to auto-hide HUD

    // Level stats
    private int totalPacketsSuccessfullyDelivered = 0;
    private volatile long simulationTimeElapsedMs = 0; // Time elapsed in current live simulation run
    private volatile long currentLevelTimeLimitMs = 0;
    private volatile long maxPredictionTimeForScrubbingMs = 0; // Max time for prediction scrubbing (usually level time limit)
    private int finalRemainingWireLengthAtLevelEnd = -1; // Stores actual remaining wire at end of level

    // Collision detection optimization
    private final Set<Pair<Integer,Integer>> activelyCollidingPairs = new HashSet<>();
    private volatile boolean networkValidatedForPrediction = false; // If network is valid for prediction run
    private static final long PREDICTION_SEED = 12345L; // Seed for deterministic predictions

    // Accumulators for a single prediction run (reset each time runFastPredictionSimulation is called)
    private int predictionRun_totalPacketsGeneratedCount = 0;
    private int predictionRun_totalPacketsLostCount = 0;
    private int predictionRun_totalPacketUnitsGenerated = 0;
    private int predictionRun_totalPacketUnitsLost = 0;

    // Power-up states during prediction (currently assumed off for simplicity)
    private static final boolean PREDICTION_ATAR_ACTIVE = false;
    private static final boolean PREDICTION_AIRYAMAN_ACTIVE = false;


    public static class PredictionRunStats {
        public final long atTimeMs;
        public final int totalPacketsGenerated;
        public final int totalPacketsLost;
        public final double packetLossPercentage; // Based on units
        public final int totalPacketUnitsGenerated;
        public final int totalPacketUnitsLost;

        public PredictionRunStats(long time, int genCount, int lostCount, int unitsGen, int unitsLost) {
            this.atTimeMs = time;
            this.totalPacketsGenerated = genCount;
            this.totalPacketsLost = lostCount;
            this.totalPacketUnitsGenerated = unitsGen;
            this.totalPacketUnitsLost = unitsLost;

            if (unitsGen <= 0) {
                this.packetLossPercentage = 0.0;
            } else {
                // Ensure loss units don't exceed generated units for percentage calculation accuracy
                double actualLossUnitsForPercentage = Math.min(unitsLost, unitsGen);
                this.packetLossPercentage = Math.min(100.0, Math.max(0.0, ((double) actualLossUnitsForPercentage / unitsGen) * 100.0));
            }
        }
        @Override
        public String toString() {
            return String.format("PredStats{t=%dms, GenCnt=%d, LostCnt=%d, Loss%%=%.1f, UnitsGen=%d, UnitsLost=%d}",
                    atTimeMs, totalPacketsGenerated, totalPacketsLost, packetLossPercentage,
                    totalPacketUnitsGenerated, totalPacketUnitsLost);
        }
    }

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
        ToolTipManager.sharedInstance().registerComponent(this); // Enable tooltips

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
        stopSimulation(); // Stop any ongoing simulation or timers

        // Load level layout which sets max wire length in gameState
        LevelLoader.LevelLayout layout = LevelLoader.loadLevel(level, gameState, game);
        if (layout == null) { // Failed to load level
            game.returnToMenu();
            return;
        }
        // gameState.setMaxWireLengthForLevel has already been called in LevelLoader
        // Now, resetForNewLevel will use this maxWireLengthPerLevel to set remainingWireLength
        gameState.resetForNewLevel(); // Resets packet stats AND wire length to max for the level

        this.currentLevel = layout.levelNumber; // Actual level loaded (might be fallback)
        this.networkValidatedForPrediction = false; // Network needs re-validation
        this.finalRemainingWireLengthAtLevelEnd = -1; // Reset stored wire length for end dialog

        // Set time limits for the current level
        if (this.currentLevel == 1) this.currentLevelTimeLimitMs = LEVEL_1_TIME_LIMIT_MS;
        else if (this.currentLevel == 2) this.currentLevelTimeLimitMs = LEVEL_2_TIME_LIMIT_MS;
        else this.currentLevelTimeLimitMs = LEVEL_1_TIME_LIMIT_MS; // Default for unlisted levels
        this.maxPredictionTimeForScrubbingMs = this.currentLevelTimeLimitMs;

        Packet.resetGlobalId();
        System.resetGlobalId();
        Port.resetGlobalId();
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

        synchronized (systems) { systems.clear(); systems.addAll(layout.systems); }
        synchronized (wires) { wires.clear(); wires.addAll(layout.wires); }

        String initialErrorMessage = getNetworkValidationErrorMessage();
        this.networkValidatedForPrediction = (initialErrorMessage == null);
        updatePrediction();

        showHUD = true;
        hudTimer.restart();
        repaint();
        SwingUtilities.invokeLater(this::requestFocusInWindow);
    }

    private void clearLevelElements() {
        synchronized (packets) { packets.clear(); }
        synchronized (packetsToAdd) { packetsToAdd.clear(); }
        synchronized (packetsToRemove) { packetsToRemove.clear(); }
        synchronized (predictedPacketStates) { predictedPacketStates.clear(); }
        synchronized (tempPredictionRunGeneratedPackets) { tempPredictionRunGeneratedPackets.clear(); }

        // Wires and systems are cleared and re-added in initializeLevel
        // so no need to individually destroy/reset here if they are cleared from main lists.
        // However, if they were not cleared from main lists, this would be necessary:
        // synchronized (wires) {
        //     for (Wire w : wires) if (w != null) w.destroy();
        //     wires.clear();
        // }
        // synchronized (systems) {
        //     for (System s : systems) if (s != null) s.resetForNewRun();
        //     systems.clear();
        // }
        displayedPredictionStats = new PredictionRunStats(0,0,0,0,0);
    }

    public void attemptStartSimulation() {
        if (simulationStarted && gamePaused) {
            pauseGame(false);
            return;
        }
        if (simulationStarted || gameOver || levelComplete) return;

        String validationMessage = getNetworkValidationErrorMessage();
        if (validationMessage != null) {
            if (!game.isMuted()) game.playSoundEffect("error");
            JOptionPane.showMessageDialog(this, "Network Validation Failed:\n" + validationMessage, "Network Not Ready", JOptionPane.WARNING_MESSAGE);
            if (this.networkValidatedForPrediction) {
                this.networkValidatedForPrediction = false;
                updatePrediction();
            }
            return;
        }
        this.networkValidatedForPrediction = true;

        // Key Change: Call resetForSimulationAttemptOnly, which does NOT reset wire length.
        gameState.resetForSimulationAttemptOnly();

        Packet.resetGlobalId();
        System.resetGlobalRandomSeed(PREDICTION_SEED);
        synchronized(systems) { for (System s : systems) if (s != null) s.resetForNewRun(); }
        synchronized(packets){ packets.clear(); }
        synchronized(packetsToAdd){ packetsToAdd.clear(); }
        synchronized(packetsToRemove){ packetsToRemove.clear(); }
        activelyCollidingPairs.clear();
        totalPacketsSuccessfullyDelivered = 0;
        this.finalRemainingWireLengthAtLevelEnd = -1; // Reset for this new attempt

        simulationStarted = true;
        gameRunning = true;
        gamePaused = false;
        lastTickTime = 0;
        viewedTimeMs = 0;
        simulationTimeElapsedMs = 0;

        synchronized(predictedPacketStates){ predictedPacketStates.clear();}
        displayedPredictionStats = new PredictionRunStats(0,0,0,0,0);

        gameLoopTimer.start();
        repaint();
    }

    private String getNetworkValidationErrorMessage() {
        List<System> currentSystemsSnapshot;
        List<Wire> currentWiresSnapshot;
        synchronized(systems) { currentSystemsSnapshot = new ArrayList<>(systems); }
        synchronized(wires) { currentWiresSnapshot = new ArrayList<>(wires); }

        // Check 1: All systems must be part of a single connected graph.
        if (!GraphUtils.isNetworkConnected(currentSystemsSnapshot, currentWiresSnapshot)) {
            return "All systems must be part of a single connected network.";
        }
        // Check 2: All ports on every system must be connected.
        if (!GraphUtils.areAllSystemPortsConnected(currentSystemsSnapshot)) {
            return "All ports on every system (Sources, Nodes, Sinks) must be connected.";
        }
        return null; // No error
    }

    private boolean validateAndSetPredictionFlag() {
        boolean oldState = this.networkValidatedForPrediction;
        String errorMessage = getNetworkValidationErrorMessage();
        boolean newStateIsValid = (errorMessage == null);

        this.networkValidatedForPrediction = newStateIsValid;

        if (newStateIsValid && !oldState) { // Transitioned from invalid to valid
            game.showTemporaryMessage("Network is fully connected and ready!", new Color(0,150,0), 2500);
        }
        // Always update prediction display, which will clear if network is now invalid
        updatePrediction();
        return this.networkValidatedForPrediction;
    }


    public void stopSimulation() {
        gameRunning = false;
        gamePaused = false;
        if(gameLoopTimer.isRunning()) gameLoopTimer.stop();

        // Stop power-up timers if active
        if(atarTimer.isRunning()) { deactivateAtar(); atarTimer.stop(); }
        if(airyamanTimer.isRunning()) { deactivateAiryaman(); airyamanTimer.stop(); }

        // If simulation wasn't formally started (e.g., user exits from pre-sim phase),
        // ensure prediction state is correctly updated/cleared.
        if (!simulationStarted) {
            String currentError = getNetworkValidationErrorMessage();
            this.networkValidatedForPrediction = (currentError == null);
            updatePrediction(); // Update prediction display based on current network validity
        }
        repaint();
    }

    public void pauseGame(boolean pause) {
        if (!simulationStarted || gameOver || levelComplete) return; // Can't pause if not running or ended

        if (pause && !gamePaused) { // Pause the game
            gamePaused = true;
            if(gameLoopTimer.isRunning()) gameLoopTimer.stop();
            // Stop power-up timers but remember their state
            if(atarTimer.isRunning()) atarTimer.stop();
            if(airyamanTimer.isRunning()) airyamanTimer.stop();
            repaint(); // Redraw to show "PAUSED" overlay
        } else if (!pause && gamePaused) { // Resume the game
            gamePaused = false;
            lastTickTime = 0; // Reset lastTickTime to avoid large jump on resume
            gameLoopTimer.start();
            // Resume power-up timers if they were active
            if(atarActive && !atarTimer.isRunning()) atarTimer.start(); // restart uses remaining time
            if(airyamanActive && !airyamanTimer.isRunning()) airyamanTimer.start();
            repaint(); // Redraw to remove overlay
        }
    }


    private void gameTick() {
        if (!gameRunning || gamePaused || !simulationStarted) return;
        if (gameOver || levelComplete) { // If game ended, stop simulation
            stopSimulation();
            return;
        }

        long currentTimeNano = java.lang.System.nanoTime();
        if (lastTickTime == 0) { // First tick after start/resume
            lastTickTime = currentTimeNano;
            return;
        }
        double deltaTime = (currentTimeNano - lastTickTime) / 1_000_000_000.0; // Delta time in seconds
        lastTickTime = currentTimeNano;
        deltaTime = Math.min(deltaTime, MAX_DELTA_TIME_SEC); // Cap delta time

        long elapsedThisTickMs = (long)(deltaTime * 1000.0);
        if (elapsedThisTickMs <= 0) elapsedThisTickMs = 1; // Ensure at least 1ms progresses

        simulationTimeElapsedMs += elapsedThisTickMs; // Accumulate total simulation time

        // Run the core simulation logic for this tick
        runSimulationTickLogic(elapsedThisTickMs, false, simulationTimeElapsedMs, atarActive, airyamanActive);

        checkEndConditions(); // Check if level is complete or game over
        repaint();
    }

    private void runSimulationTickLogic(
            long tickDurationMsForMovement, // Not directly used for movement step, but for system timing
            boolean isPredictionRun,
            long currentTotalSimTimeMs,     // Used by systems for generation timing
            boolean currentAtarActive,
            boolean currentAiryamanActive) {

        // 1. Process packet buffers (add new, remove old)
        processPacketBuffersInternal();

        // 2. Attempt packet generation from Source systems
        synchronized (systems) {
            List<System> systemsSnapshot = new ArrayList<>(systems); // Avoid CME
            for (System s : systemsSnapshot) {
                if (s != null && s.isReferenceSystem() && s.hasOutputPorts()) { // If it's a Source
                    s.attemptPacketGeneration(this, currentTotalSimTimeMs, isPredictionRun);
                }
            }
        }
        processPacketBuffersInternal(); // Process packets added by generation

        // 3. Update active packets (movement, reaching end of wire)
        List<Packet> currentPacketsSnapshot;
        synchronized (packets) {
            currentPacketsSnapshot = new ArrayList<>(packets); // Avoid CME
        }
        for (Packet p : currentPacketsSnapshot) {
            if (p != null && !p.isMarkedForRemoval()) {
                p.update(this, currentAiryamanActive, isPredictionRun);
            }
        }
        processPacketBuffersInternal(); // Process packets that reached systems or got lost

        // 4. Process system queues (Nodes trying to send out queued packets)
        synchronized (systems) {
            List<System> systemsSnapshot = new ArrayList<>(systems); // Avoid CME
            for (System s : systemsSnapshot) {
                if (s != null && !s.isReferenceSystem()) { // Only Nodes process queues
                    s.processQueue(this, isPredictionRun);
                }
            }
        }
        processPacketBuffersInternal(); // Process packets sent from queues

        // 5. Detect and handle collisions (if Airyaman is not active)
        if (!currentAiryamanActive) {
            synchronized (packets) {
                currentPacketsSnapshot = new ArrayList<>(packets); // Get fresh list for collision
            }
            detectAndHandleCollisionsBroadPhaseInternal(currentPacketsSnapshot, currentAtarActive, isPredictionRun);
        }
        processPacketBuffersInternal(); // Process packets lost due to noise from collisions
    }

    private void processPacketBuffersInternal() {
        // Remove packets marked for removal
        if (!packetsToRemove.isEmpty()) {
            synchronized (packetsToRemove) {
                synchronized (packets) {
                    packets.removeAll(packetsToRemove);
                }
                packetsToRemove.clear();
            }
        }
        // Add newly created packets
        if (!packetsToAdd.isEmpty()) {
            synchronized (packetsToAdd) {
                synchronized (packets) {
                    packets.addAll(packetsToAdd);
                }
                packetsToAdd.clear();
            }
        }
    }

    public void addPacketInternal(Packet packet, boolean isPredictionRun) {
        if (packet != null) {
            synchronized (packetsToAdd) { packetsToAdd.add(packet); }
            if (isPredictionRun) {
                predictionRun_totalPacketsGeneratedCount++;
                predictionRun_totalPacketUnitsGenerated += packet.getSize();
                // Add to the temporary list for this specific prediction run
                synchronized(tempPredictionRunGeneratedPackets){
                    tempPredictionRunGeneratedPackets.add(packet);
                }
            } else { // Live game run
                gameState.recordPacketGeneration(packet);
            }
        }
    }


    public void packetLostInternal(Packet packet, boolean isPredictionRun) {
        if (packet != null && !packet.isMarkedForRemoval()) {
            packet.markForRemoval();
            packet.setFinalStatusForPrediction(PredictedPacketStatus.LOST); // Crucial for prediction
            synchronized (packetsToRemove) { packetsToRemove.add(packet); }

            if (isPredictionRun) {
                // Accumulate stats for this prediction run
                predictionRun_totalPacketsLostCount++;
                predictionRun_totalPacketUnitsLost += packet.getSize();
            } else { // Live game run
                gameState.increasePacketLoss(packet); // Update overall game state
                if (!game.isMuted()) game.playSoundEffect("packet_loss");
            }
        }
    }

    public void packetSuccessfullyDeliveredInternal(Packet packet, boolean isPredictionRun) {
        if (packet != null && !packet.isMarkedForRemoval()) {
            packet.markForRemoval();
            packet.setFinalStatusForPrediction(PredictedPacketStatus.DELIVERED); // Crucial for prediction
            synchronized (packetsToRemove) { packetsToRemove.add(packet); }

            if (!isPredictionRun) { // Live game run
                totalPacketsSuccessfullyDelivered++;
                // Coins are typically added when packet enters any non-source system,
                // so no separate coin logic here needed for "delivery" itself.
            }
            // For prediction, this packet will be snapshotted as DELIVERED.
            // Prediction stats (loss, generated) are not directly affected by successful delivery,
            // other than this packet *not* being counted as lost.
        }
    }

    public void addRoutingCoinsInternal(Packet packet, boolean isPredictionRun) {
        if (packet != null && !isPredictionRun) { // Only add coins in live game, not prediction
            gameState.addCoins(packet.getBaseCoinValue());
        }
    }

    private void detectAndHandleCollisionsBroadPhaseInternal(List<Packet> packetSnapshot, boolean currentAtarActive, boolean isPredictionRun) {
        if (packetSnapshot.isEmpty()) return;

        Map<Point, List<Packet>> spatialGrid = new HashMap<>();
        Set<Pair<Integer, Integer>> currentTickCollisions = new HashSet<>(); // Collisions found this tick
        Set<Pair<Integer, Integer>> checkedPairsThisTick = new HashSet<>();  // To avoid checking pairs twice

        // Populate spatial grid
        for (Packet p : packetSnapshot) {
            if (p == null || p.isMarkedForRemoval() || p.getCurrentSystem() != null || p.getCurrentWire() == null) continue;
            Point2D.Double pos = p.getPositionDouble();
            if (pos == null) continue;
            int cellX = (int) (pos.x / SPATIAL_GRID_CELL_SIZE);
            int cellY = (int) (pos.y / SPATIAL_GRID_CELL_SIZE);
            Point cellKey = new Point(cellX, cellY);
            spatialGrid.computeIfAbsent(cellKey, k -> new ArrayList<>()).add(p);
        }

        // Check for collisions within cells and with neighboring cells
        for (Map.Entry<Point, List<Packet>> entry : spatialGrid.entrySet()) {
            Point cellKey = entry.getKey();
            List<Packet> packetsInCell = entry.getValue();

            // Check collisions within the current cell
            checkCollisionsInListInternal(packetsInCell, packetsInCell, currentTickCollisions, checkedPairsThisTick, packetSnapshot, currentAtarActive, isPredictionRun);

            // Check collisions with neighboring cells (only in one direction to avoid duplicates)
            int[] dx = {1, 1, 0, -1}; // dx for right, bottom-right, bottom, bottom-left
            int[] dy = {0, 1, 1,  1}; // dy for right, bottom-right, bottom, bottom-left
            for (int i = 0; i < dx.length; i++) {
                Point neighborCellKey = new Point(cellKey.x + dx[i], cellKey.y + dy[i]);
                List<Packet> packetsInNeighborCell = spatialGrid.get(neighborCellKey);
                if (packetsInNeighborCell != null && !packetsInNeighborCell.isEmpty()) {
                    checkCollisionsInListInternal(packetsInCell, packetsInNeighborCell, currentTickCollisions, checkedPairsThisTick, packetSnapshot, currentAtarActive, isPredictionRun);
                }
            }
        }

        // Update activelyCollidingPairs set (for continuous collision effects/sounds)
        Set<Pair<Integer, Integer>> pairsToRemoveFromActive = new HashSet<>(activelyCollidingPairs);
        pairsToRemoveFromActive.removeAll(currentTickCollisions); // These were not colliding this tick but were before
        activelyCollidingPairs.removeAll(pairsToRemoveFromActive);
        activelyCollidingPairs.addAll(currentTickCollisions); // Add new/ongoing collisions
    }

    private void checkCollisionsInListInternal(List<Packet> list1, List<Packet> list2,
                                               Set<Pair<Integer, Integer>> currentTickCollisions,
                                               Set<Pair<Integer, Integer>> checkedPairsThisTick,
                                               List<Packet> fullPacketSnapshotForImpactWave,
                                               boolean currentAtarActive, boolean isPredictionRun) {
        for (Packet p1 : list1) {
            if (p1 == null || p1.isMarkedForRemoval() || p1.getCurrentSystem() != null || p1.getCurrentWire() == null) continue;
            for (Packet p2 : list2) {
                if (p2 == null || p2.isMarkedForRemoval() || p2.getCurrentSystem() != null || p2.getCurrentWire() == null) continue;
                if (p1.getId() == p2.getId()) continue; // Don't check packet against itself

                Pair<Integer, Integer> currentPair = makeOrderedPair(p1.getId(), p2.getId());
                if (checkedPairsThisTick.contains(currentPair)) continue; // Already checked this pair

                if (p1.collidesWith(p2)) {
                    currentTickCollisions.add(currentPair); // Mark as colliding this tick
                    if (!activelyCollidingPairs.contains(currentPair)) { // If this is a *new* collision (not ongoing)
                        if (!isPredictionRun && !game.isMuted()) game.playSoundEffect("collision");

                        // Apply direct collision noise
                        p1.addNoise(DIRECT_COLLISION_NOISE_PER_PACKET);
                        p2.addNoise(DIRECT_COLLISION_NOISE_PER_PACKET);

                        // Set visual offset based on collision direction
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

                        // Handle impact wave noise (if Atar power-up is not active)
                        if (!currentAtarActive) {
                            Point impactCenter = calculateImpactCenter(p1.getPositionDouble(), p2.getPositionDouble());
                            if (impactCenter != null) {
                                handleImpactWaveNoiseInternal(impactCenter, fullPacketSnapshotForImpactWave, p1, p2);
                            }
                        }
                    }
                }
                checkedPairsThisTick.add(currentPair); // Mark this pair as checked for this tick
            }
        }
    }

    private Point calculateImpactCenter(Point2D.Double p1Pos, Point2D.Double p2Pos) {
        if (p1Pos != null && p2Pos != null) {
            return new Point((int)Math.round((p1Pos.getX() + p2Pos.getX()) / 2.0),
                    (int)Math.round((p1Pos.getY() + p2Pos.getY()) / 2.0));
        } else if (p1Pos != null) { return new Point((int)Math.round(p1Pos.x), (int)Math.round(p1Pos.y)); }
        else if (p2Pos != null) { return new Point((int)Math.round(p2Pos.x), (int)Math.round(p2Pos.y)); }
        return null; // Should not happen if both packets are valid
    }

    private Pair<Integer, Integer> makeOrderedPair(int id1, int id2) {
        return (id1 < id2) ? new Pair<>(id1, id2) : new Pair<>(id2, id1);
    }

    private void handleImpactWaveNoiseInternal(Point center, List<Packet> snapshot, Packet ignore1, Packet ignore2) {
        double waveRadiusSq = IMPACT_WAVE_RADIUS * IMPACT_WAVE_RADIUS;
        for (Packet p : snapshot) {
            if (p == null || p.isMarkedForRemoval() || p.getCurrentSystem() != null ||
                    p == ignore1 || p == ignore2 || p.getCurrentWire() == null) continue;

            Point2D.Double pVisPos = p.getPositionDouble();
            if (pVisPos == null) continue;

            double distSq = center.distanceSq(pVisPos);
            if (distSq < waveRadiusSq && distSq > 1e-6) { // If within radius and not at exact center
                double distance = Math.sqrt(distSq);
                double normalizedDistance = distance / IMPACT_WAVE_RADIUS; // 0 (at center) to 1 (at edge)
                double noiseAmount = IMPACT_WAVE_MAX_NOISE * (1.0 - normalizedDistance); // More noise closer to center
                noiseAmount = Math.max(0.0, noiseAmount);

                if (noiseAmount > 0) {
                    // Set visual offset direction away from impact center
                    double forceDirX = pVisPos.x - center.x;
                    double forceDirY = pVisPos.y - center.y;
                    p.setVisualOffsetDirectionFromForce(new Point2D.Double(forceDirX, forceDirY));
                    p.addNoise(noiseAmount);
                }
            }
        }
    }


    private void handleEndOfLevelByTimeLimit() {
        if (gameOver || levelComplete) return; // Already ended

        this.finalRemainingWireLengthAtLevelEnd = gameState.getRemainingWireLength(); // Store wire length before stopping
        stopSimulation();

        // Any packets still in transit or queued are considered lost at time limit
        int packetsInLoopOrTransit = 0;
        synchronized (packets) {
            List<Packet> remainingPackets = new ArrayList<>(packets); // CME safety
            for (Packet p : remainingPackets) {
                if (p != null && !p.isMarkedForRemoval()) {
                    // These packets are now considered lost for scoring
                    gameState.increasePacketLoss(p); // This updates GameState's loss stats
                    packetsInLoopOrTransit++;
                }
            }
        }
        // Also consider packets in system queues as lost
        synchronized(systems) {
            for(System s : systems) {
                if(s != null && !s.isReferenceSystem()) { // Only Node systems have queues processed this way
                    while(s.getQueueSize() > 0) {
                        Packet p = s.packetQueue.poll(); // Directly access for cleanup
                        if(p != null && !p.isMarkedForRemoval()) {
                            gameState.increasePacketLoss(p);
                            packetsInLoopOrTransit++;
                        }
                    }
                }
            }
        }


        boolean lostTooMany = gameState.getPacketLossPercentage() >= PACKET_LOSS_GAME_OVER_THRESHOLD;
        if (lostTooMany) {
            gameOver = true;
            if (!game.isMuted()) game.playSoundEffect("game_over");
        } else {
            levelComplete = true;
            if (!game.isMuted()) game.playSoundEffect("level_complete");
            if (currentLevel < gameState.getMaxLevels()) { // currentLevel is 1-based
                gameState.unlockLevel(currentLevel); // Unlock next level (level parameter is 0-based index)
            }
        }
        SwingUtilities.invokeLater(() -> showEndLevelDialog(!lostTooMany)); // Success if not lostTooMany
    }


    private void checkEndConditions() {
        if (gameOver || levelComplete || !simulationStarted) return;

        // Condition 1: Time limit reached
        if (simulationTimeElapsedMs >= currentLevelTimeLimitMs) {
            handleEndOfLevelByTimeLimit();
            return;
        }

        // Condition 2: All packets generated and all processed (delivered or lost)
        boolean allSourcesFinishedGenerating = true;
        int sourcesChecked = 0;
        boolean sourcesHadPacketsToGenerate = false;

        synchronized (systems) {
            List<System> systemsSnapshot = new ArrayList<>(systems); // CME safety
            for (System s : systemsSnapshot) {
                if (s != null && s.isReferenceSystem() && s.hasOutputPorts()) { // It's a Source system
                    sourcesChecked++;
                    if (s.getTotalPacketsToGenerate() > 0) {
                        sourcesHadPacketsToGenerate = true;
                        if (s.getPacketsGeneratedCount() < s.getTotalPacketsToGenerate()) {
                            allSourcesFinishedGenerating = false; // This source is not done
                            break;
                        }
                    }
                }
            }
        }

        // If there are no sources or no sources configured to generate, they are "finished"
        if (sourcesChecked == 0 || !sourcesHadPacketsToGenerate) {
            allSourcesFinishedGenerating = true;
        }

        if (!allSourcesFinishedGenerating) {
            return; // Still generating, so level is not over yet
        }

        // All sources are done. Now check if all packets are processed.
        boolean packetsOnWireOrBuffersEmpty;
        boolean queuesAreEmpty = true;

        synchronized (packets) {
            synchronized(packetsToAdd) {
                synchronized(packetsToRemove) { // Although remove should be empty if processed
                    packetsOnWireOrBuffersEmpty = packets.isEmpty() && packetsToAdd.isEmpty();
                }
            }
        }

        synchronized(systems) {
            List<System> systemsSnapshot = new ArrayList<>(systems); // CME safety
            for(System s : systemsSnapshot) {
                if (s != null && !s.isReferenceSystem() && s.getQueueSize() > 0) { // Node system with items in queue
                    queuesAreEmpty = false;
                    break;
                }
            }
        }

        if (allSourcesFinishedGenerating && packetsOnWireOrBuffersEmpty && queuesAreEmpty) {
            // All generated packets have been processed (either delivered or lost)
            if (!levelComplete && !gameOver) { // Ensure this runs only once
                this.finalRemainingWireLengthAtLevelEnd = gameState.getRemainingWireLength(); // Store wire length
                stopSimulation();
                boolean lostTooMany = gameState.getPacketLossPercentage() >= PACKET_LOSS_GAME_OVER_THRESHOLD;
                if (lostTooMany) {
                    gameOver = true;
                    if (!game.isMuted()) game.playSoundEffect("game_over");
                } else {
                    levelComplete = true;
                    if (!game.isMuted()) game.playSoundEffect("level_complete");
                    if (currentLevel < gameState.getMaxLevels()) { // currentLevel is 1-based
                        gameState.unlockLevel(currentLevel); // Unlock next level (level param is 0-based index)
                    }
                }
                SwingUtilities.invokeLater(() -> showEndLevelDialog(!lostTooMany)); // Success if not lostTooMany
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
            } else if (simulationTimeElapsedMs >= currentLevelTimeLimitMs) { // Implies loss also high
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

        // Use the stored final wire length for display
        if (this.finalRemainingWireLengthAtLevelEnd != -1) {
            message.append("\nRemaining Wire Length: ").append(this.finalRemainingWireLengthAtLevelEnd);
        } else {
            // Fallback in case it wasn't set (e.g., if dialog shown through non-standard path)
            message.append("\nRemaining Wire Length: ").append(gameState.getRemainingWireLength());
        }

        message.append("\nSimulation Time: ").append(String.format("%.2f / %.0f s", simulationTimeElapsedMs / 1000.0, currentLevelTimeLimitMs / 1000.0));

        // Options for dialog
        List<String> optionsList = new ArrayList<>();
        String nextLevelOption = null;
        String retryOption = "Retry Level " + currentLevel;
        String menuOption = "Main Menu";

        int nextLevelNumber = currentLevel + 1;
        boolean nextLevelExists = nextLevelNumber <= gameState.getMaxLevels();
        boolean nextLevelIsUnlocked = success && nextLevelExists && gameState.isLevelUnlocked(currentLevel);

        if (success) {
            if (nextLevelIsUnlocked) {
                nextLevelOption = "Next Level (" + nextLevelNumber + ")";
                optionsList.add(nextLevelOption);
            } else if (!nextLevelExists) {
                message.append("\n\nAll levels completed!");
            }
            optionsList.add(retryOption);
            optionsList.add(menuOption);
        } else {
            optionsList.add(retryOption);
            optionsList.add(menuOption);
        }

        Object[] options = optionsList.toArray();
        if (options.length == 0) options = new Object[]{menuOption};

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
            game.setLevel(currentLevel);
            game.startGame();
        } else if (nextLevelOption != null && selectedOption.equals(nextLevelOption)) {
            game.setLevel(nextLevelNumber);
            game.startGame();
        } else {
            game.returnToMenu();
        }
    }

    public void activateAtar() { if (!atarActive) { atarActive = true;} atarTimer.restart(); repaint(); }
    private void deactivateAtar() { if (atarActive) { atarActive = false; repaint(); } atarTimer.stop(); }
    public void activateAiryaman() {  if (!airyamanActive) { airyamanActive = true;} airyamanTimer.restart(); repaint(); }
    private void deactivateAiryaman() { if (airyamanActive) { airyamanActive = false; repaint(); } airyamanTimer.stop(); }
    public void activateAnahita() { int count = 0; List<Packet> currentSimPackets; synchronized (packets) { currentSimPackets = new ArrayList<>(packets); } for (Packet p : currentSimPackets) { if (p != null && !p.isMarkedForRemoval() && p.getNoise() > 0) { p.resetNoise(); count++; } } repaint(); }

    @Override
    protected void paintComponent(Graphics g) { super.paintComponent(g); gameRenderer.render(g); }
    public void startWiringMode(Port startPort, Point currentMousePos) { if (!wireDrawingMode && startPort != null && !simulationStarted && startPort.getType() == NetworkEnums.PortType.OUTPUT && !startPort.isConnected()) { this.selectedOutputPort = startPort; this.mouseDragPos.setLocation(currentMousePos); this.wireDrawingMode = true; this.currentWiringColor = DEFAULT_WIRING_COLOR; setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR)); repaint(); } else if (simulationStarted) { if(!game.isMuted()) game.playSoundEffect("error"); } }
    public void updateDragPos(Point currentMousePos) { if (wireDrawingMode) { this.mouseDragPos.setLocation(currentMousePos); } }
    public void updateWiringPreview(Point currentMousePos) { if (!wireDrawingMode || selectedOutputPort == null || selectedOutputPort.getPosition() == null) { this.currentWiringColor = DEFAULT_WIRING_COLOR; repaint(); return; } Point startPos = selectedOutputPort.getPosition(); double wireLength = startPos.distance(currentMousePos); if (gameState.getRemainingWireLength() < wireLength) { this.currentWiringColor = INVALID_WIRING_COLOR; repaint(); return; } Port targetPort = findPortAt(currentMousePos); if (targetPort != null) { if (Objects.equals(targetPort.getParentSystem(), selectedOutputPort.getParentSystem())) { this.currentWiringColor = INVALID_WIRING_COLOR; } else if (targetPort.getType() != NetworkEnums.PortType.INPUT || targetPort.isConnected()) { this.currentWiringColor = INVALID_WIRING_COLOR; } else { this.currentWiringColor = VALID_WIRING_COLOR_TARGET; } } else { this.currentWiringColor = DEFAULT_WIRING_COLOR; } repaint(); }
    public boolean attemptWireCreation(Port startPort, Port endPort) { if (simulationStarted) { return false; } if (startPort == null || endPort == null || startPort.getPosition() == null || endPort.getPosition() == null) { return false; } if (startPort.getType() != NetworkEnums.PortType.OUTPUT || endPort.getType() != NetworkEnums.PortType.INPUT || startPort.isConnected() || endPort.isConnected() || Objects.equals(startPort.getParentSystem(), endPort.getParentSystem())) { return false; } int wireLength = (int) Math.round(startPort.getPosition().distance(endPort.getPosition())); if (wireLength <= 0) { return false; } if (gameState.useWire(wireLength)) { try { Wire newWire = new Wire(startPort, endPort); synchronized (wires) { wires.add(newWire); } if (!game.isMuted()) game.playSoundEffect("wire_connect"); validateAndSetPredictionFlag(); return true; } catch (IllegalArgumentException | IllegalStateException e) { gameState.returnWire(wireLength); JOptionPane.showMessageDialog(this.game, "Cannot create wire:\n" + e.getMessage(), "Wiring Error", JOptionPane.WARNING_MESSAGE); if (!game.isMuted()) game.playSoundEffect("error"); validateAndSetPredictionFlag(); return false; } } else { JOptionPane.showMessageDialog(this.game, "Not enough wire! Need: " + wireLength + ", Have: " + gameState.getRemainingWireLength(), "Insufficient Wire", JOptionPane.WARNING_MESSAGE); if (!game.isMuted()) game.playSoundEffect("error"); return false; } }
    public void cancelWiring() { if(wireDrawingMode) { selectedOutputPort = null; wireDrawingMode = false; currentWiringColor = DEFAULT_WIRING_COLOR; setCursor(Cursor.getDefaultCursor()); repaint(); } }
    public void deleteWireRequest(Wire wireToDelete) { if (wireToDelete == null || simulationStarted) { if(simulationStarted) { if(!game.isMuted()) game.playSoundEffect("error"); } return; } boolean removed; synchronized (wires) { removed = wires.remove(wireToDelete); } if (removed) { int returnedLength = (int) Math.round(wireToDelete.getLength()); gameState.returnWire(returnedLength); wireToDelete.destroy(); if (!game.isMuted()) game.playSoundEffect("wire_disconnect"); validateAndSetPredictionFlag(); } }
    public Port findPortAt(Point p) { if (p == null) return null; List<System> systemsSnapshot; synchronized (this.systems) { systemsSnapshot = new ArrayList<>(this.systems); } for (System s : systemsSnapshot) { if (s != null) { Port port = s.getPortAt(p); if (port != null) return port; } } return null; }
    public Wire findWireAt(Point p) { if (p == null) return null; final double CLICK_THRESHOLD = 10.0; final double CLICK_THRESHOLD_SQ = CLICK_THRESHOLD * CLICK_THRESHOLD; Wire closestWire = null; double minDistanceSq = Double.MAX_VALUE; List<Wire> wiresSnapshot; synchronized (this.wires) { wiresSnapshot = new ArrayList<>(this.wires); } for (Wire w : wiresSnapshot) { if (w == null || w.getStartPort() == null || w.getEndPort() == null || w.getStartPort().getPosition() == null || w.getEndPort().getPosition() == null) { continue; } Point start = w.getStartPort().getPosition(); Point end = w.getEndPort().getPosition(); double distSq = Line2D.ptSegDistSq(start.x, start.y, end.x, end.y, p.x, p.y); if (distSq < minDistanceSq) { minDistanceSq = distSq; closestWire = w; } } return (closestWire != null && minDistanceSq < CLICK_THRESHOLD_SQ) ? closestWire : null; }
    public Wire findWireFromPort(Port outputPort) { if (outputPort == null || outputPort.getType() != NetworkEnums.PortType.OUTPUT) return null; synchronized (wires) { List<Wire> wiresSnapshot = new ArrayList<>(wires); for (Wire w : wiresSnapshot) { if (w != null && Objects.equals(w.getStartPort(), outputPort)) return w; } } return null; }
    public boolean isWireOccupied(Wire wire, boolean isPredictionRun) { if (wire == null) return false; synchronized (packets) { List<Packet> currentPacketsList = new ArrayList<>(packets); for (Packet p : currentPacketsList) { if (p != null && !p.isMarkedForRemoval() && Objects.equals(p.getCurrentWire(), wire)) { return true; } } } return false; }
    public void toggleHUD() { showHUD = !showHUD; if (showHUD) hudTimer.restart(); else hudTimer.stop(); repaint(); }

    public void incrementViewedTime() {
        if (!simulationStarted) {
            if (!this.networkValidatedForPrediction) {
                String validationMessage = getNetworkValidationErrorMessage();
                if(!game.isMuted()) game.playSoundEffect("error");
                JOptionPane.showMessageDialog(this, "Cannot scrub time:\n" +
                                (validationMessage != null ? validationMessage : "Network is not fully connected."),
                        "Network Not Ready for Scrubbing", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (viewedTimeMs < maxPredictionTimeForScrubbingMs) { // Only increment if not already at max
                viewedTimeMs = Math.min(maxPredictionTimeForScrubbingMs, viewedTimeMs + TIME_SCRUB_INCREMENT_MS);
            }
            updatePrediction();
        }
    }
    public void decrementViewedTime() {
        if (!simulationStarted) {
            if (!this.networkValidatedForPrediction) {
                String validationMessage = getNetworkValidationErrorMessage();
                if(!game.isMuted()) game.playSoundEffect("error");
                JOptionPane.showMessageDialog(this, "Cannot scrub time:\n" +
                                (validationMessage != null ? validationMessage : "Network is not fully connected."),
                        "Network Not Ready for Scrubbing", JOptionPane.WARNING_MESSAGE);
                return;
            }
            viewedTimeMs = Math.max(0, viewedTimeMs - TIME_SCRUB_INCREMENT_MS);
            updatePrediction();
        }
    }

    private void updatePrediction() {
        if (simulationStarted) { // If live simulation is running, clear prediction data
            synchronized(predictedPacketStates) { predictedPacketStates.clear(); }
            synchronized(tempPredictionRunGeneratedPackets) { tempPredictionRunGeneratedPackets.clear(); }
            displayedPredictionStats = new PredictionRunStats(0,0,0,0,0);
            repaint();
            return;
        }
        if (!networkValidatedForPrediction) { // If network is invalid, clear prediction data
            synchronized(predictedPacketStates) { predictedPacketStates.clear(); }
            synchronized(tempPredictionRunGeneratedPackets) { tempPredictionRunGeneratedPackets.clear(); }
            displayedPredictionStats = new PredictionRunStats(0,0,0,0,0);
            repaint();
            return;
        }
        // Network is valid and not in live simulation, so run prediction
        runFastPredictionSimulation(this.viewedTimeMs);
        repaint();
    }

    private void runFastPredictionSimulation(long targetTimeMs) {
        // 1. Reset global states for deterministic run
        Packet.resetGlobalId();
        System.resetGlobalId(); // Ensure System IDs are reset if they affect behavior (they do for new System instances)
        Port.resetGlobalId();   // Ensure Port IDs are reset
        System.resetGlobalRandomSeed(PREDICTION_SEED); // CRUCIAL for determinism

        // 2. Reset GamePanel's simulation state lists
        synchronized(systems) { for (System s : systems) { if (s != null) s.resetForNewRun(); } }
        synchronized(packets) { packets.clear(); }
        synchronized(packetsToAdd) { packetsToAdd.clear(); }
        synchronized(packetsToRemove) { packetsToRemove.clear(); }
        activelyCollidingPairs.clear();
        synchronized(tempPredictionRunGeneratedPackets) { tempPredictionRunGeneratedPackets.clear(); }


        // 3. Reset prediction-specific accumulators for THIS run
        predictionRun_totalPacketsGeneratedCount = 0;
        predictionRun_totalPacketsLostCount = 0;
        predictionRun_totalPacketUnitsGenerated = 0;
        predictionRun_totalPacketUnitsLost = 0;

        // 4. Simulate tick by tick up to targetTimeMs
        long currentInternalSimTime = 0;
        final long predictionTickDuration = GAME_TICK_MS; // Use same tick duration as game for consistency

        // Ensure targetTimeMs does not exceed the maximum allowed prediction time
        long actualTargetTimeMs = Math.min(targetTimeMs, this.maxPredictionTimeForScrubbingMs);

        while (currentInternalSimTime < actualTargetTimeMs) {
            long timeStepForTick = Math.min(predictionTickDuration, actualTargetTimeMs - currentInternalSimTime);
            if (timeStepForTick <= 0) break; // Should not happen if loop condition is correct

            runSimulationTickLogic(timeStepForTick, true, currentInternalSimTime, PREDICTION_ATAR_ACTIVE, PREDICTION_AIRYAMAN_ACTIVE);
            currentInternalSimTime += timeStepForTick;
        }
        processPacketBuffersInternal(); // Final buffer processing after all ticks

        // 5. Create snapshots for all packets generated during this prediction run
        synchronized(predictedPacketStates) {
            predictedPacketStates.clear(); // Clear snapshots from any previous display

            for (Packet p : tempPredictionRunGeneratedPackets) { // Iterate ALL packets created in THIS prediction run
                if (p == null) continue;

                PredictedPacketStatus statusToSnapshot = p.getFinalStatusForPrediction();

                if (statusToSnapshot != null) {
                    // Packet has a definitive end status (DELIVERED, LOST, or STALLED_AT_NODE set by System logic)
                    predictedPacketStates.add(new PacketSnapshot(p, statusToSnapshot));
                } else {
                    // No definitive end status from packet logic, determine from current state
                    if (p.getCurrentSystem() != null) { // Is it held by a system (implies queued)?
                        predictedPacketStates.add(new PacketSnapshot(p, PredictedPacketStatus.QUEUED));
                    } else if (p.getCurrentWire() != null) { // Is it on a wire?
                        if (!p.isMarkedForRemoval()) { // And not somehow marked for removal without a final status
                            predictedPacketStates.add(new PacketSnapshot(p, PredictedPacketStatus.ON_WIRE));
                        } else {
                            // This is a logic gap: marked for removal but no final status.
                            // For prediction, assume LOST. This should ideally be caught by system logic.
                            // java.lang.System.err.println("Prediction: Packet " + p.getId() + " marked for removal but no final status. Assuming LOST.");
                            p.setFinalStatusForPrediction(PredictedPacketStatus.LOST); // Set it now
                            predictedPacketStates.add(new PacketSnapshot(p, PredictedPacketStatus.LOST));
                            // Note: This won't double-count in predictionRun_totalPacketUnitsLost if it was already counted.
                        }
                    } else {
                        // Not DELIVERED/LOST, not STALLED (by system), not in a System's queue, not on a Wire.
                        // This packet is in an ambiguous state. It was generated.
                        // Default to STALLED_AT_NODE as a fallback.
                        predictedPacketStates.add(new PacketSnapshot(p, PredictedPacketStatus.STALLED_AT_NODE));
                    }
                }
            }
        }

        // 6. Store the aggregated prediction stats for the HUD
        this.displayedPredictionStats = new PredictionRunStats(
                actualTargetTimeMs, // Use the time up to which simulation actually ran
                predictionRun_totalPacketsGeneratedCount,
                predictionRun_totalPacketsLostCount,
                predictionRun_totalPacketUnitsGenerated,
                predictionRun_totalPacketUnitsLost
        );

        // 7. Clean up simulation state lists for the *next* prediction run or actual game start.
        // (predictedPacketStates is kept for rendering. tempPredictionRunGeneratedPackets is cleared at start of next prediction)
        // Global ID/seed resets already happened at the beginning of this method.
        // System.resetForNewRun() already happened.
        synchronized(packets) { packets.clear(); }
        synchronized(packetsToAdd) { packetsToAdd.clear(); }
        synchronized(packetsToRemove) { packetsToRemove.clear(); }
        activelyCollidingPairs.clear();
    }

    public List<Packet> getPacketsForRendering() {
        List<Packet> packetsToRender = new ArrayList<>();
        if (simulationStarted) { // Only render from 'packets' list if live simulation is running
            synchronized (packets) {
                for (Packet p : packets) {
                    // Only render packets that are on a wire (not in a system's queue and not removed)
                    if (p != null && !p.isMarkedForRemoval() && p.getCurrentSystem() == null) {
                        packetsToRender.add(p);
                    }
                }
            }
        }
        // If not simulationStarted, rendering is handled by predictedPacketStates in GameRenderer
        return packetsToRender;
    }

    // --- Standard Getters ---
    public NetworkGame getGame() { return game; }
    public GameState getGameState() { return gameState; }
    public PredictionRunStats getDisplayedPredictionStats() { return displayedPredictionStats; }
    public List<System> getSystems() { return Collections.unmodifiableList(systems); } // For read-only access
    public List<Wire> getWires() { return Collections.unmodifiableList(wires); }     // For read-only access
    public List<PacketSnapshot> getPredictedPacketStates() {
        synchronized(predictedPacketStates) {
            return Collections.unmodifiableList(new ArrayList<>(predictedPacketStates)); // Return a copy for thread safety
        }
    }
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