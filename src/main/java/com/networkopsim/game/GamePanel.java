// ==== GamePanel.java ====

package com.networkopsim.game;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.Objects;
import java.awt.geom.Point2D;
import java.awt.geom.Line2D;

public class GamePanel extends JPanel {
    // Pair class
    private static class Pair<T, U> {
        final T first;
        final U second;
        Pair(T first, U second) { this.first = first; this.second = second; }
        @Override public boolean equals(Object o) { if (this == o) return true; if (o == null || getClass() != o.getClass()) return false; Pair<?, ?> pair = (Pair<?, ?>) o; return Objects.equals(first, pair.first) && Objects.equals(second, pair.second); }
        @Override public int hashCode() { return Objects.hash(first, second); }
        @Override public String toString() { return "Pair{" + "first=" + first + ", second=" + second + '}'; }
    }

    private static final int GAME_TICK_MS = 16;
    private static final int HUD_DISPLAY_TIME_MS = 7000;
    private static final int ATAR_DURATION_MS = 10000;
    private static final int AIRYAMAN_DURATION_MS = 5000;
    private static final Color BACKGROUND_COLOR = new Color(15, 15, 20);
    private static final double PACKET_LOSS_GAME_OVER_THRESHOLD = 50.0;
    private static final long TIME_SCRUB_INCREMENT_MS = 1000;
    private static final double DIRECT_COLLISION_NOISE_PER_PACKET = 1.0;
    private static final double IMPACT_WAVE_RADIUS = 180.0;
    private static final double IMPACT_WAVE_MAX_NOISE = 1.0;

    public static final Color DEFAULT_WIRING_COLOR = Color.LIGHT_GRAY;
    public static final Color VALID_WIRING_COLOR_TARGET = new Color(0, 200, 0);
    public static final Color INVALID_WIRING_COLOR = new Color(220, 0, 0);
    private static final int SPATIAL_GRID_CELL_SIZE = 60;

    private static final long LEVEL_1_TIME_LIMIT_MS = 2 * 60 * 1000;
    private static final long LEVEL_2_TIME_LIMIT_MS = 4 * 60 * 1000;

    private static final int RELAY_COST = 1;


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
    private final Timer gameLoopTimer;

    private volatile long viewedTimeMs = 0;
    private final List<PacketSnapshot> predictedPacketStates = Collections.synchronizedList(new ArrayList<>());
    private volatile PredictionRunStats displayedPredictionStats = new PredictionRunStats(0,0,0,0,0);
    private List<Packet> tempPredictionRunGeneratedPackets = new ArrayList<>();

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

    private boolean relayPointDragMode = false;
    private Wire.RelayPoint draggedRelayPoint = null;
    private int preDragWireLength = 0;

    private int totalPacketsSuccessfullyDelivered = 0;
    private volatile long simulationTimeElapsedMs = 0;
    private volatile long currentLevelTimeLimitMs = 0;
    private volatile long maxPredictionTimeForScrubbingMs = 0;
    private int finalRemainingWireLengthAtLevelEnd = -1;

    private final Set<Pair<Integer,Integer>> activelyCollidingPairs = new HashSet<>();
    private volatile boolean networkValidatedForPrediction = false;
    private static final long PREDICTION_SEED = 12345L;

    private int predictionRun_totalPacketsGeneratedCount = 0;
    private int predictionRun_totalPacketsLostCount = 0;
    private int predictionRun_totalPacketUnitsGenerated = 0;
    private int predictionRun_totalPacketUnitsLost = 0;

    private static final boolean PREDICTION_ATAR_ACTIVE = false;
    private static final boolean PREDICTION_AIRYAMAN_ACTIVE = false;


    public static class PredictionRunStats {
        public final long atTimeMs;
        public final int totalPacketsGenerated;
        public final int totalPacketsLost;
        public final double packetLossPercentage;
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
        stopSimulation();
        LevelLoader.LevelLayout layout = LevelLoader.loadLevel(level, gameState, game);
        if (layout == null) {
            game.returnToMenu();
            return;
        }
        gameState.resetForNewLevel();
        this.currentLevel = layout.levelNumber;
        this.networkValidatedForPrediction = false;
        this.finalRemainingWireLengthAtLevelEnd = -1;
        if (this.currentLevel == 1) this.currentLevelTimeLimitMs = LEVEL_1_TIME_LIMIT_MS;
        else if (this.currentLevel == 2) this.currentLevelTimeLimitMs = LEVEL_2_TIME_LIMIT_MS;
        else this.currentLevelTimeLimitMs = LEVEL_1_TIME_LIMIT_MS;
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
        relayPointDragMode = false;
        draggedRelayPoint = null;
        selectedOutputPort = null;
        currentWiringColor = DEFAULT_WIRING_COLOR;
        setCursor(Cursor.getDefaultCursor());
        viewedTimeMs = 0;
        simulationTimeElapsedMs = 0;
        activelyCollidingPairs.clear();
        clearLevelElements();
        deactivateAtar();
        atarTimer.stop();
        deactivateAiryaman();
        airyamanTimer.stop();
        synchronized (systems) {
            systems.clear();
            systems.addAll(layout.systems);
        }
        synchronized (wires) {
            wires.clear();
            wires.addAll(layout.wires);
        }
        validateAndSetPredictionFlag();
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
        displayedPredictionStats = new PredictionRunStats(0, 0, 0, 0, 0);
    }

    public void attemptStartSimulation() {
        if (simulationStarted && gamePaused) {
            pauseGame(false);
            return;
        }
        if (simulationStarted || gameOver || levelComplete) return;

        validateAndSetPredictionFlag();
        String validationMessage = getNetworkValidationErrorMessage();
        if (validationMessage != null) {
            if (!game.isMuted()) game.playSoundEffect("error");
            JOptionPane.showMessageDialog(this, "Network Validation Failed:\n" + validationMessage, "Network Not Ready", JOptionPane.WARNING_MESSAGE);
            return;
        }
        this.networkValidatedForPrediction = true;
        gameState.resetForSimulationAttemptOnly();
        Packet.resetGlobalId();
        System.resetGlobalRandomSeed(PREDICTION_SEED);
        synchronized (systems) {
            List<System> sortedSystems = new ArrayList<>(systems);
            sortedSystems.sort(Comparator.comparingInt(System::getId));
            for (System s : sortedSystems) {
                if (s != null) s.resetForNewRun();
            }
        }
        synchronized (packets) {
            packets.clear();
        }
        synchronized (packetsToAdd) {
            packetsToAdd.clear();
        }
        synchronized (packetsToRemove) {
            packetsToRemove.clear();
        }
        activelyCollidingPairs.clear();
        totalPacketsSuccessfullyDelivered = 0;
        this.finalRemainingWireLengthAtLevelEnd = -1;
        simulationStarted = true;
        gameRunning = true;
        gamePaused = false;
        viewedTimeMs = 0;
        simulationTimeElapsedMs = 0;
        synchronized (predictedPacketStates) {
            predictedPacketStates.clear();
        }
        displayedPredictionStats = new PredictionRunStats(0, 0, 0, 0, 0);
        gameLoopTimer.start();
        repaint();
    }

    private String getNetworkValidationErrorMessage() {
        List<System> currentSystemsSnapshot;
        List<Wire> currentWiresSnapshot;
        synchronized (systems) {
            currentSystemsSnapshot = new ArrayList<>(systems);
        }
        synchronized (wires) {
            currentWiresSnapshot = new ArrayList<>(wires);
        }
        if (!GraphUtils.isNetworkConnected(currentSystemsSnapshot, currentWiresSnapshot)) {
            return "All systems must be part of a single connected network.";
        }
        if (!GraphUtils.areAllSystemPortsConnected(currentSystemsSnapshot)) {
            return "All ports on every system (Sources, Nodes, Sinks) must be connected.";
        }
        long totalWireLength = 0;
        for (Wire wire : currentWiresSnapshot) {
            if (isWireIntersectingAnySystem(wire, currentSystemsSnapshot)) {
                return "A wire connection (Wire ID: " + wire.getId() + ") is illegally passing through a system's body.";
            }
            totalWireLength += wire.getLength();
        }
        if (totalWireLength > gameState.getMaxWireLengthForLevel()) {
            return "Total wire length exceeds the budget for this level.";
        }
        return null;
    }

    /**
     * MODIFIED: Simplified logic. A wire is only invalid if it intersects a system
     * that is NOT its start or end system. Crossing its own parent systems is now allowed.
     *
     * @param wire The wire to check.
     * @param allSystems A snapshot of all systems on the panel.
     * @return true if the wire illegally intersects a third-party system's body.
     */
    public boolean isWireIntersectingAnySystem(Wire wire, List<System> allSystems) {
        if (wire == null) return false;
        List<Point2D.Double> fullPath = wire.getFullPathPoints();
        if (fullPath.size() < 2) return false;

        System startSystem = wire.getStartPort().getParentSystem();
        System endSystem = wire.getEndPort().getParentSystem();

        for (int i = 0; i < fullPath.size() - 1; i++) {
            Line2D.Double segment = new Line2D.Double(fullPath.get(i), fullPath.get(i + 1));
            for (System sys : allSystems) {
                if (sys == null || sys.equals(startSystem) || sys.equals(endSystem)) {
                    // Skip check if the system is the wire's own start or end system.
                    continue;
                }

                Rectangle sysBounds = new Rectangle(sys.getX(), sys.getY(), System.SYSTEM_WIDTH, System.SYSTEM_HEIGHT);
                if (segment.intersects(sysBounds)) {
                    // Intersection with a third-party system is always illegal.
                    return true;
                }
            }
        }
        return false;
    }

    private void validateAndSetPredictionFlag() {
        String errorMessage = getNetworkValidationErrorMessage();
        boolean newStateIsValid = (errorMessage == null);

        if (this.networkValidatedForPrediction != newStateIsValid && newStateIsValid) {
            game.showTemporaryMessage("Network is fully connected and ready!", new Color(0, 150, 0), 2500);
        }
        this.networkValidatedForPrediction = newStateIsValid;

        updatePrediction();
        repaint();
    }

    public void stopSimulation() {
        gameRunning = false;
        gamePaused = false;
        if (gameLoopTimer.isRunning()) gameLoopTimer.stop();
        if (atarTimer.isRunning()) {
            deactivateAtar();
            atarTimer.stop();
        }
        if (airyamanTimer.isRunning()) {
            deactivateAiryaman();
            airyamanTimer.stop();
        }
        if (!simulationStarted) {
            validateAndSetPredictionFlag();
        }
        repaint();
    }

    public void pauseGame(boolean pause) {
        if (!simulationStarted || gameOver || levelComplete) return;
        if (pause && !gamePaused) {
            gamePaused = true;
            if (gameLoopTimer.isRunning()) gameLoopTimer.stop();
            if (atarTimer.isRunning()) atarTimer.stop();
            if (airyamanTimer.isRunning()) airyamanTimer.stop();
            repaint();
        } else if (!pause && gamePaused) {
            gamePaused = false;
            gameLoopTimer.start();
            if (atarActive && !atarTimer.isRunning()) atarTimer.start();
            if (airyamanActive && !airyamanTimer.isRunning()) airyamanTimer.start();
            repaint();
        }
    }

    private void gameTick() {
        if (!gameRunning || gamePaused || !simulationStarted) return;
        if (gameOver || levelComplete) {
            stopSimulation();
            return;
        }
        long elapsedThisTickMs = GAME_TICK_MS;
        simulationTimeElapsedMs += elapsedThisTickMs;
        runSimulationTickLogic(elapsedThisTickMs, false, simulationTimeElapsedMs, atarActive, airyamanActive);
        checkEndConditions();
        repaint();
    }

    private void runSimulationTickLogic(long tickDurationMsForMovement, boolean isPredictionRun, long currentTotalSimTimeMs, boolean currentAtarActive, boolean currentAiryamanActive) {
        processPacketBuffersInternal();
        List<System> systemsSnapshotSorted;
        synchronized (systems) {
            systemsSnapshotSorted = new ArrayList<>(systems);
            systemsSnapshotSorted.sort(Comparator.comparingInt(System::getId));
        }
        for (System s : systemsSnapshotSorted) {
            if (s != null && s.isReferenceSystem() && s.hasOutputPorts()) {
                s.attemptPacketGeneration(this, currentTotalSimTimeMs, isPredictionRun);
            }
        }
        processPacketBuffersInternal();
        List<Packet> currentPacketsSnapshotSorted;
        synchronized (packets) {
            currentPacketsSnapshotSorted = new ArrayList<>(packets);
            currentPacketsSnapshotSorted.sort(Comparator.comparingInt(Packet::getId));
        }
        for (Packet p : currentPacketsSnapshotSorted) {
            if (p != null && !p.isMarkedForRemoval()) {
                p.update(this, currentAiryamanActive, isPredictionRun);
            }
        }
        processPacketBuffersInternal();
        for (System s : systemsSnapshotSorted) {
            if (s != null && !s.isReferenceSystem()) {
                s.processQueue(this, isPredictionRun);
            }
        }
        processPacketBuffersInternal();
        if (!currentAiryamanActive) {
            detectAndHandleCollisionsBroadPhaseInternal(currentPacketsSnapshotSorted, currentAtarActive, isPredictionRun);
        }
        processPacketBuffersInternal();
    }

    private void processPacketBuffersInternal() {
        if (!packetsToRemove.isEmpty()) {
            synchronized (packetsToRemove) {
                packetsToRemove.sort(Comparator.comparingInt(Packet::getId));
                synchronized (packets) {
                    packets.removeAll(packetsToRemove);
                }
                packetsToRemove.clear();
            }
        }
        if (!packetsToAdd.isEmpty()) {
            synchronized (packetsToAdd) {
                packetsToAdd.sort(Comparator.comparingInt(Packet::getId));
                synchronized (packets) {
                    packets.addAll(packetsToAdd);
                }
                packetsToAdd.clear();
            }
        }
    }

    public void addPacketInternal(Packet packet, boolean isPredictionRun) {
        if (packet != null) {
            synchronized (packetsToAdd) {
                packetsToAdd.add(packet);
            }
            if (isPredictionRun) {
                predictionRun_totalPacketsGeneratedCount++;
                predictionRun_totalPacketUnitsGenerated += packet.getSize();
                synchronized (tempPredictionRunGeneratedPackets) {
                    tempPredictionRunGeneratedPackets.add(packet);
                }
            } else {
                gameState.recordPacketGeneration(packet);
            }
        }
    }

    public void packetLostInternal(Packet packet, boolean isPredictionRun) {
        if (packet != null && !packet.isMarkedForRemoval()) {
            packet.markForRemoval();
            packet.setFinalStatusForPrediction(PredictedPacketStatus.LOST);
            synchronized (packetsToRemove) {
                packetsToRemove.add(packet);
            }
            if (isPredictionRun) {
                predictionRun_totalPacketsLostCount++;
                predictionRun_totalPacketUnitsLost += packet.getSize();
            } else {
                gameState.increasePacketLoss(packet);
                if (!game.isMuted()) game.playSoundEffect("packet_loss");
            }
        }
    }

    public void packetSuccessfullyDeliveredInternal(Packet packet, boolean isPredictionRun) {
        if (packet != null && !packet.isMarkedForRemoval()) {
            packet.markForRemoval();
            packet.setFinalStatusForPrediction(PredictedPacketStatus.DELIVERED);
            synchronized (packetsToRemove) {
                packetsToRemove.add(packet);
            }
            if (!isPredictionRun) {
                totalPacketsSuccessfullyDelivered++;
            }
        }
    }

    public void addRoutingCoinsInternal(Packet packet, boolean isPredictionRun) {
        if (packet != null && !isPredictionRun) {
            gameState.addCoins(packet.getBaseCoinValue());
        }
    }

    private void detectAndHandleCollisionsBroadPhaseInternal(List<Packet> packetSnapshotSortedById, boolean currentAtarActive, boolean isPredictionRun) {
        if (packetSnapshotSortedById.isEmpty()) return;
        Map<Point, List<Packet>> spatialGrid = new HashMap<>();
        Set<Pair<Integer, Integer>> currentTickCollisions = new HashSet<>();
        Set<Pair<Integer, Integer>> checkedPairsThisTick = new HashSet<>();
        for (Packet p : packetSnapshotSortedById) {
            if (p == null || p.isMarkedForRemoval() || p.getCurrentSystem() != null || p.getCurrentWire() == null)
                continue;
            Point2D.Double pos = p.getPositionDouble();
            if (pos == null) continue;
            int cellX = (int) (pos.x / SPATIAL_GRID_CELL_SIZE);
            int cellY = (int) (pos.y / SPATIAL_GRID_CELL_SIZE);
            Point cellKey = new Point(cellX, cellY);
            spatialGrid.computeIfAbsent(cellKey, k -> new ArrayList<>()).add(p);
        }
        List<Point> sortedCellKeys = new ArrayList<>(spatialGrid.keySet());
        sortedCellKeys.sort((p1, p2) -> {
            int cmp = Integer.compare(p1.x, p2.x);
            return (cmp == 0) ? Integer.compare(p1.y, p2.y) : cmp;
        });
        for (Point cellKey : sortedCellKeys) {
            List<Packet> packetsInCell = spatialGrid.get(cellKey);
            if (packetsInCell == null) continue;
            checkCollisionsInListInternal(packetsInCell, packetsInCell, currentTickCollisions, checkedPairsThisTick, packetSnapshotSortedById, currentAtarActive, isPredictionRun);
            int[] dx = {1, 1, 0, -1};
            int[] dy = {0, 1, 1, 1};
            for (int i = 0; i < dx.length; i++) {
                Point neighborCellKey = new Point(cellKey.x + dx[i], cellKey.y + dy[i]);
                List<Packet> packetsInNeighborCell = spatialGrid.get(neighborCellKey);
                if (packetsInNeighborCell != null && !packetsInNeighborCell.isEmpty()) {
                    checkCollisionsInListInternal(packetsInCell, packetsInNeighborCell, currentTickCollisions, checkedPairsThisTick, packetSnapshotSortedById, currentAtarActive, isPredictionRun);
                }
            }
        }
        Set<Pair<Integer, Integer>> pairsToRemoveFromActive = new HashSet<>(activelyCollidingPairs);
        pairsToRemoveFromActive.removeAll(currentTickCollisions);
        activelyCollidingPairs.removeAll(pairsToRemoveFromActive);
        activelyCollidingPairs.addAll(currentTickCollisions);
    }

    private void checkCollisionsInListInternal(List<Packet> list1, List<Packet> list2, Set<Pair<Integer, Integer>> currentTickCollisions, Set<Pair<Integer, Integer>> checkedPairsThisTick, List<Packet> fullPacketSnapshotForImpactWave, boolean currentAtarActive, boolean isPredictionRun) {
        for (Packet p1 : list1) {
            if (p1 == null || p1.isMarkedForRemoval() || p1.getCurrentSystem() != null || p1.getCurrentWire() == null)
                continue;
            for (Packet p2 : list2) {
                if (p2 == null || p2.isMarkedForRemoval() || p2.getCurrentSystem() != null || p2.getCurrentWire() == null)
                    continue;
                if (p1.getId() == p2.getId() || (list1 == list2 && p1.getId() >= p2.getId())) continue;
                Pair<Integer, Integer> currentPair = makeOrderedPair(p1.getId(), p2.getId());
                if (checkedPairsThisTick.contains(currentPair)) continue;
                if (p1.collidesWith(p2)) {
                    currentTickCollisions.add(currentPair);
                    if (!activelyCollidingPairs.contains(currentPair)) {
                        if (!isPredictionRun && !game.isMuted()) game.playSoundEffect("collision");
                        p1.addNoise(DIRECT_COLLISION_NOISE_PER_PACKET);
                        p2.addNoise(DIRECT_COLLISION_NOISE_PER_PACKET);
                        Point2D.Double p1VisPos = p1.getPositionDouble();
                        Point2D.Double p2VisPos = p2.getPositionDouble();
                        if (p1VisPos != null && p2VisPos != null) {
                            p1.setVisualOffsetDirectionFromForce(new Point2D.Double(p1VisPos.x - p2VisPos.x, p1VisPos.y - p2VisPos.y));
                            p2.setVisualOffsetDirectionFromForce(new Point2D.Double(p2VisPos.x - p1VisPos.x, p2VisPos.y - p1VisPos.y));
                        }
                        if (!currentAtarActive) {
                            Point impactCenter = calculateImpactCenter(p1.getPositionDouble(), p2.getPositionDouble());
                            if (impactCenter != null) {
                                handleImpactWaveNoiseInternal(impactCenter, fullPacketSnapshotForImpactWave, p1, p2);
                            }
                        }
                    }
                }
                checkedPairsThisTick.add(currentPair);
            }
        }
    }

    private Point calculateImpactCenter(Point2D.Double p1Pos, Point2D.Double p2Pos) {
        if (p1Pos != null && p2Pos != null)
            return new Point((int) Math.round((p1Pos.getX() + p2Pos.getX()) / 2.0), (int) Math.round((p1Pos.getY() + p2Pos.getY()) / 2.0));
        else if (p1Pos != null) return new Point((int) Math.round(p1Pos.x), (int) Math.round(p1Pos.y));
        else if (p2Pos != null) return new Point((int) Math.round(p2Pos.x), (int) Math.round(p2Pos.y));
        return null;
    }

    private Pair<Integer, Integer> makeOrderedPair(int id1, int id2) {
        return (id1 < id2) ? new Pair<>(id1, id2) : new Pair<>(id2, id1);
    }

    private void handleImpactWaveNoiseInternal(Point center, List<Packet> snapshotSortedById, Packet ignore1, Packet ignore2) {
        double waveRadiusSq = IMPACT_WAVE_RADIUS * IMPACT_WAVE_RADIUS;
        for (Packet p : snapshotSortedById) {
            if (p == null || p.isMarkedForRemoval() || p.getCurrentSystem() != null || p == ignore1 || p == ignore2 || p.getCurrentWire() == null)
                continue;
            Point2D.Double pVisPos = p.getPositionDouble();
            if (pVisPos == null) continue;
            double distSq = center.distanceSq(pVisPos);
            if (distSq < waveRadiusSq && distSq > 1e-6) {
                double distance = Math.sqrt(distSq);
                double normalizedDistance = distance / IMPACT_WAVE_RADIUS;
                double noiseAmount = IMPACT_WAVE_MAX_NOISE * (1.0 - normalizedDistance);
                noiseAmount = Math.max(0.0, noiseAmount);
                if (noiseAmount > 0) {
                    p.setVisualOffsetDirectionFromForce(new Point2D.Double(pVisPos.x - center.x, pVisPos.y - center.y));
                    p.addNoise(noiseAmount);
                }
            }
        }
    }

    private void handleEndOfLevelByTimeLimit() {
        if (gameOver || levelComplete) return;
        this.finalRemainingWireLengthAtLevelEnd = gameState.getRemainingWireLength();
        stopSimulation();
        List<Packet> remainingPacketsSorted;
        synchronized (packets) {
            remainingPacketsSorted = new ArrayList<>(packets);
            remainingPacketsSorted.sort(Comparator.comparingInt(Packet::getId));
        }
        for (Packet p : remainingPacketsSorted)
            if (p != null && !p.isMarkedForRemoval()) gameState.increasePacketLoss(p);
        List<System> systemsSnapshotSorted;
        synchronized (systems) {
            systemsSnapshotSorted = new ArrayList<>(systems);
            systemsSnapshotSorted.sort(Comparator.comparingInt(System::getId));
        }
        for (System s : systemsSnapshotSorted) {
            if (s != null && !s.isReferenceSystem()) {
                List<Packet> queueSnapshot = new ArrayList<>(s.packetQueue);
                queueSnapshot.sort(Comparator.comparingInt(Packet::getId));
                for (Packet p : queueSnapshot) if (p != null && !p.isMarkedForRemoval()) gameState.increasePacketLoss(p);
                s.packetQueue.clear();
            }
        }
        boolean lostTooMany = gameState.getPacketLossPercentage() >= PACKET_LOSS_GAME_OVER_THRESHOLD;
        if (lostTooMany) {
            gameOver = true;
            if (!game.isMuted()) game.playSoundEffect("game_over");
        } else {
            levelComplete = true;
            if (!game.isMuted()) game.playSoundEffect("level_complete");
            if (currentLevel < gameState.getMaxLevels()) gameState.unlockLevel(currentLevel);
        }
        SwingUtilities.invokeLater(() -> showEndLevelDialog(!lostTooMany));
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
        List<System> systemsSnapshotSorted;
        synchronized (systems) {
            systemsSnapshotSorted = new ArrayList<>(systems);
            systemsSnapshotSorted.sort(Comparator.comparingInt(System::getId));
        }
        for (System s : systemsSnapshotSorted) {
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
        if (sourcesChecked == 0 || !sourcesHadPacketsToGenerate) allSourcesFinishedGenerating = true;
        if (!allSourcesFinishedGenerating) return;
        boolean packetsOnWireOrBuffersEmpty;
        boolean queuesAreEmpty = true;
        synchronized (packets) {
            synchronized (packetsToAdd) {
                synchronized (packetsToRemove) {
                    packetsOnWireOrBuffersEmpty = packets.isEmpty() && packetsToAdd.isEmpty();
                }
            }
        }
        for (System s : systemsSnapshotSorted)
            if (s != null && !s.isReferenceSystem() && s.getQueueSize() > 0) {
                queuesAreEmpty = false;
                break;
            }
        if (allSourcesFinishedGenerating && packetsOnWireOrBuffersEmpty && queuesAreEmpty) {
            if (!levelComplete && !gameOver) {
                this.finalRemainingWireLengthAtLevelEnd = gameState.getRemainingWireLength();
                stopSimulation();
                boolean lostTooMany = gameState.getPacketLossPercentage() >= PACKET_LOSS_GAME_OVER_THRESHOLD;
                if (lostTooMany) {
                    gameOver = true;
                    if (!game.isMuted()) game.playSoundEffect("game_over");
                } else {
                    levelComplete = true;
                    if (!game.isMuted()) game.playSoundEffect("level_complete");
                    if (currentLevel < gameState.getMaxLevels()) gameState.unlockLevel(currentLevel);
                }
                SwingUtilities.invokeLater(() -> showEndLevelDialog(!lostTooMany));
            }
        }
    }

    private void showEndLevelDialog(boolean success) {
        String title = success ? "Level " + currentLevel + " Complete!" : "Game Over!";
        StringBuilder message = new StringBuilder();
        message.append(success ? "Congratulations!" : "Simulation Failed!").append("\nLevel ").append(currentLevel).append(success ? " passed." : " failed.");
        if (!success) {
            if (simulationTimeElapsedMs >= currentLevelTimeLimitMs && gameState.getPacketLossPercentage() < PACKET_LOSS_GAME_OVER_THRESHOLD) {
                message.append(String.format("\nReason: Time limit of %.0f seconds reached.", currentLevelTimeLimitMs / 1000.0)).append(String.format("\nPacket loss (%.1f%%) was acceptable, but time ran out.", gameState.getPacketLossPercentage()));
            } else if (simulationTimeElapsedMs >= currentLevelTimeLimitMs) {
                message.append(String.format("\nReason: Time limit of %.0f seconds reached AND packet loss (%.1f%%) exceeded %.1f%%.", currentLevelTimeLimitMs / 1000.0, gameState.getPacketLossPercentage(), PACKET_LOSS_GAME_OVER_THRESHOLD));
            } else if (gameState.getPacketLossPercentage() >= PACKET_LOSS_GAME_OVER_THRESHOLD) {
                message.append(String.format("\nReason: Packet loss (%.1f%%) exceeded %.1f%%.", gameState.getPacketLossPercentage(), PACKET_LOSS_GAME_OVER_THRESHOLD));
            }
        }
        message.append("\n\n--- Results ---").append("\nPackets Delivered: ").append(totalPacketsSuccessfullyDelivered).append("\nPackets Generated: ").append(gameState.getTotalPacketsGeneratedCount()).append("\nPackets Lost: ").append(gameState.getTotalPacketsLostCount()).append("\nPacket Units Lost: ").append(gameState.getTotalPacketLossUnits()).append(" units (").append(String.format("%.1f%%", gameState.getPacketLossPercentage())).append(")");
        message.append("\nTotal Coins (Overall): ").append(gameState.getCoins());
        if (this.finalRemainingWireLengthAtLevelEnd != -1) {
            message.append("\nRemaining Wire Length: ").append(this.finalRemainingWireLengthAtLevelEnd);
        } else {
            message.append("\nRemaining Wire Length: ").append(gameState.getRemainingWireLength());
        }
        message.append("\nSimulation Time: ").append(String.format("%.2f / %.0f s", simulationTimeElapsedMs / 1000.0, currentLevelTimeLimitMs / 1000.0));
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
            } else if (!nextLevelExists) message.append("\n\nAll levels completed!");
            optionsList.add(retryOption);
            optionsList.add(menuOption);
        } else {
            optionsList.add(retryOption);
            optionsList.add(menuOption);
        }
        Object[] options = optionsList.toArray();
        if (options.length == 0) options = new Object[]{menuOption};
        int choice = JOptionPane.showOptionDialog(this.game, message.toString(), title, JOptionPane.DEFAULT_OPTION, success ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE, null, options, options[0]);
        String selectedOption = (choice == JOptionPane.CLOSED_OPTION || choice < 0 || choice >= options.length) ? menuOption : options[choice].toString();
        if (selectedOption.equals(menuOption)) game.returnToMenu();
        else if (selectedOption.equals(retryOption)) {
            game.setLevel(currentLevel);
            game.startGame();
        } else if (nextLevelOption != null && selectedOption.equals(nextLevelOption)) {
            game.setLevel(nextLevelNumber);
            game.startGame();
        } else game.returnToMenu();
    }

    public void activateAtar() { if (!atarActive) { atarActive = true;} atarTimer.restart(); repaint(); }
    private void deactivateAtar() { if (atarActive) { atarActive = false; repaint(); } atarTimer.stop(); }
    public void activateAiryaman() {  if (!airyamanActive) { airyamanActive = true;} airyamanTimer.restart(); repaint(); }
    private void deactivateAiryaman() { if (airyamanActive) { airyamanActive = false; repaint(); } airyamanTimer.stop(); }
    public void activateAnahita() { List<Packet> currentSimPackets; synchronized (packets) { currentSimPackets = new ArrayList<>(packets); } for (Packet p : currentSimPackets) { if (p != null && !p.isMarkedForRemoval() && p.getNoise() > 0) p.resetNoise(); } repaint(); }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        gameRenderer.render(g);
    }

    public void startWiringMode(Port startPort, Point currentMousePos) { if (!wireDrawingMode && startPort != null && !simulationStarted && startPort.getType() == NetworkEnums.PortType.OUTPUT && !startPort.isConnected()) { this.selectedOutputPort = startPort; this.mouseDragPos.setLocation(currentMousePos); this.wireDrawingMode = true; this.currentWiringColor = DEFAULT_WIRING_COLOR; setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR)); repaint(); } else if (simulationStarted) { if(!game.isMuted()) game.playSoundEffect("error"); } }

    public void updateDragPos(Point currentMousePos) { if (wireDrawingMode) this.mouseDragPos.setLocation(currentMousePos); }

    public void updateWiringPreview(Point currentMousePos) { if (!wireDrawingMode || selectedOutputPort == null || selectedOutputPort.getPosition() == null) { this.currentWiringColor = DEFAULT_WIRING_COLOR; repaint(); return; } Point startPos = selectedOutputPort.getPosition(); double wireLength = startPos.distance(currentMousePos); if (gameState.getRemainingWireLength() < wireLength) { this.currentWiringColor = INVALID_WIRING_COLOR; repaint(); return; } Port targetPort = findPortAt(currentMousePos); if (targetPort != null) { if (Objects.equals(targetPort.getParentSystem(), selectedOutputPort.getParentSystem()) || targetPort.getType() != NetworkEnums.PortType.INPUT || targetPort.isConnected()) { this.currentWiringColor = INVALID_WIRING_COLOR; } else { this.currentWiringColor = VALID_WIRING_COLOR_TARGET; } } else { this.currentWiringColor = DEFAULT_WIRING_COLOR; } repaint(); }

    public boolean attemptWireCreation(Port startPort, Port endPort) {
        if (simulationStarted) return false;
        if (startPort == null || endPort == null || startPort.getPosition() == null || endPort.getPosition() == null || startPort.getType() != NetworkEnums.PortType.OUTPUT || endPort.getType() != NetworkEnums.PortType.INPUT || startPort.isConnected() || endPort.isConnected() || Objects.equals(startPort.getParentSystem(), endPort.getParentSystem())) return false;

        int wireLength = (int) Math.round(startPort.getPosition().distance(endPort.getPosition()));
        if (wireLength <= 0) return false;

        if (gameState.getRemainingWireLength() >= wireLength) {
            gameState.useWire(wireLength);
            try {
                Wire newWire = new Wire(startPort, endPort);
                synchronized (wires) {
                    wires.add(newWire);
                }
                if (!game.isMuted()) game.playSoundEffect("wire_connect");
                validateAndSetPredictionFlag();
                return true;
            } catch (Exception e) {
                gameState.returnWire(wireLength);
                JOptionPane.showMessageDialog(this.game, "Cannot create wire:\n" + e.getMessage(), "Wiring Error", JOptionPane.WARNING_MESSAGE);
                if (!game.isMuted()) game.playSoundEffect("error");
                validateAndSetPredictionFlag();
                return false;
            }
        } else {
            JOptionPane.showMessageDialog(this.game, "Not enough wire! Need: " + wireLength + ", Have: " + gameState.getRemainingWireLength(), "Insufficient Wire", JOptionPane.WARNING_MESSAGE);
            if (!game.isMuted()) game.playSoundEffect("error");
            return false;
        }
    }

    public void cancelWiring() { if(wireDrawingMode) { selectedOutputPort = null; wireDrawingMode = false; currentWiringColor = DEFAULT_WIRING_COLOR; setCursor(Cursor.getDefaultCursor()); repaint(); } }

    public void deleteWireRequest(Wire wireToDelete) { if (wireToDelete == null || simulationStarted) { if(simulationStarted) if(!game.isMuted()) game.playSoundEffect("error"); return; } boolean removed; synchronized (wires) { removed = wires.remove(wireToDelete); } if (removed) { int returnedLength = (int) Math.round(wireToDelete.getLength()); gameState.returnWire(returnedLength); wireToDelete.destroy(); if (!game.isMuted()) game.playSoundEffect("wire_disconnect"); validateAndSetPredictionFlag(); } }

    public Port findPortAt(Point p) { if (p == null) return null; List<System> systemsSnapshot; synchronized (this.systems) { systemsSnapshot = new ArrayList<>(this.systems); } for (System s : systemsSnapshot) { if (s != null) { Port port = s.getPortAt(p); if (port != null) return port; } } return null; }

    public Wire findWireAt(Point p, double clickThreshold) { if (p == null) return null; double clickThresholdSq = clickThreshold * clickThreshold; Wire closestWire = null; double minDistanceSq = Double.MAX_VALUE; List<Wire> wiresSnapshot; synchronized (this.wires) { wiresSnapshot = new ArrayList<>(this.wires); } for (Wire w : wiresSnapshot) { if (w == null) continue; List<Point2D.Double> path = w.getFullPathPoints(); if (path.size() < 2) continue; for (int i = 0; i < path.size() - 1; i++) { double distSq = Line2D.ptSegDistSq(path.get(i).x, path.get(i).y, path.get(i+1).x, path.get(i+1).y, p.x, p.y); if (distSq < minDistanceSq) { minDistanceSq = distSq; closestWire = w; } } } return (closestWire != null && minDistanceSq < clickThresholdSq) ? closestWire : null; }

    public Wire findWireFromPort(Port outputPort) { if (outputPort == null || outputPort.getType() != NetworkEnums.PortType.OUTPUT) return null; synchronized (wires) { List<Wire> wiresSnapshot = new ArrayList<>(wires); for (Wire w : wiresSnapshot) { if (w != null && Objects.equals(w.getStartPort(), outputPort)) return w; } } return null; }

    public boolean isWireOccupied(Wire wire, boolean isPredictionRun) { if (wire == null) return false; synchronized (packets) { List<Packet> currentPacketsList = new ArrayList<>(packets); for (Packet p : currentPacketsList) { if (p != null && !p.isMarkedForRemoval() && Objects.equals(p.getCurrentWire(), wire)) { return true; } } } return false; }

    public void toggleHUD() { showHUD = !showHUD; if (showHUD) hudTimer.restart(); else hudTimer.stop(); repaint(); }

    public void incrementViewedTime() { if (!simulationStarted) { if (!this.networkValidatedForPrediction) { String validationMessage = getNetworkValidationErrorMessage(); if(!game.isMuted()) game.playSoundEffect("error"); JOptionPane.showMessageDialog(this, "Cannot scrub time:\n" + (validationMessage != null ? validationMessage : "Network is not fully connected."), "Network Not Ready for Scrubbing", JOptionPane.WARNING_MESSAGE); return; } if (viewedTimeMs < maxPredictionTimeForScrubbingMs) viewedTimeMs = Math.min(maxPredictionTimeForScrubbingMs, viewedTimeMs + TIME_SCRUB_INCREMENT_MS); updatePrediction(); } }

    public void decrementViewedTime() { if (!simulationStarted) { if (!this.networkValidatedForPrediction) { String validationMessage = getNetworkValidationErrorMessage(); if(!game.isMuted()) game.playSoundEffect("error"); JOptionPane.showMessageDialog(this, "Cannot scrub time:\n" + (validationMessage != null ? validationMessage : "Network is not fully connected."), "Network Not Ready for Scrubbing", JOptionPane.WARNING_MESSAGE); return; } viewedTimeMs = Math.max(0, viewedTimeMs - TIME_SCRUB_INCREMENT_MS); updatePrediction(); } }

    private void updatePrediction() { if (simulationStarted || !networkValidatedForPrediction) { synchronized(predictedPacketStates) { predictedPacketStates.clear(); } synchronized(tempPredictionRunGeneratedPackets) { tempPredictionRunGeneratedPackets.clear(); } displayedPredictionStats = new PredictionRunStats(0,0,0,0,0); repaint(); return; } runFastPredictionSimulation(this.viewedTimeMs); }

    private void runFastPredictionSimulation(long targetTimeMs) { Packet.resetGlobalId(); System.resetGlobalId(); Port.resetGlobalId(); System.resetGlobalRandomSeed(PREDICTION_SEED); synchronized(systems) { List<System> sortedSystems = new ArrayList<>(systems); sortedSystems.sort(Comparator.comparingInt(System::getId)); for (System s : sortedSystems) if (s != null) s.resetForNewRun(); } synchronized(packets) { packets.clear(); } synchronized(packetsToAdd) { packetsToAdd.clear(); } synchronized(packetsToRemove) { packetsToRemove.clear(); } activelyCollidingPairs.clear(); synchronized(tempPredictionRunGeneratedPackets) { tempPredictionRunGeneratedPackets.clear(); } predictionRun_totalPacketsGeneratedCount = 0; predictionRun_totalPacketsLostCount = 0; predictionRun_totalPacketUnitsGenerated = 0; predictionRun_totalPacketUnitsLost = 0; long currentInternalSimTime = 0; final long predictionTickDuration = GAME_TICK_MS; long actualTargetTimeMs = Math.min(targetTimeMs, this.maxPredictionTimeForScrubbingMs); while (currentInternalSimTime < actualTargetTimeMs) { long timeStepForTick = Math.min(predictionTickDuration, actualTargetTimeMs - currentInternalSimTime); if (timeStepForTick <= 0) break; runSimulationTickLogic(timeStepForTick, true, currentInternalSimTime, PREDICTION_ATAR_ACTIVE, PREDICTION_AIRYAMAN_ACTIVE); currentInternalSimTime += timeStepForTick; } processPacketBuffersInternal(); synchronized(predictedPacketStates) { predictedPacketStates.clear(); synchronized(tempPredictionRunGeneratedPackets) { tempPredictionRunGeneratedPackets.sort(Comparator.comparingInt(Packet::getId)); } for (Packet p : tempPredictionRunGeneratedPackets) { if (p == null) continue; PredictedPacketStatus statusToSnapshot = p.getFinalStatusForPrediction(); if (statusToSnapshot != null) { predictedPacketStates.add(new PacketSnapshot(p, statusToSnapshot)); } else { if (p.getCurrentSystem() != null) { predictedPacketStates.add(new PacketSnapshot(p, PredictedPacketStatus.QUEUED)); } else if (p.getCurrentWire() != null) { if (!p.isMarkedForRemoval()) { predictedPacketStates.add(new PacketSnapshot(p, PredictedPacketStatus.ON_WIRE)); } else { p.setFinalStatusForPrediction(PredictedPacketStatus.LOST); predictedPacketStates.add(new PacketSnapshot(p, PredictedPacketStatus.LOST)); } } else { predictedPacketStates.add(new PacketSnapshot(p, PredictedPacketStatus.STALLED_AT_NODE)); } } } } this.displayedPredictionStats = new PredictionRunStats(actualTargetTimeMs, predictionRun_totalPacketsGeneratedCount, predictionRun_totalPacketsLostCount, predictionRun_totalPacketUnitsGenerated, predictionRun_totalPacketUnitsLost); synchronized(packets) { packets.clear(); } synchronized(packetsToAdd) { packetsToAdd.clear(); } synchronized(packetsToRemove) { packetsToRemove.clear(); } activelyCollidingPairs.clear(); }

    public List<Packet> getPacketsForRendering() { List<Packet> packetsToRender = new ArrayList<>(); if (simulationStarted) { synchronized (packets) { for (Packet p : packets) if (p != null && !p.isMarkedForRemoval() && p.getCurrentSystem() == null) packetsToRender.add(p); } packetsToRender.sort(Comparator.comparingInt(Packet::getId)); } return packetsToRender; }

    public Wire.RelayPoint findRelayPointAt(Point p) { if (p == null) return null; List<Wire> wiresSnapshot; synchronized(this.wires) { wiresSnapshot = new ArrayList<>(this.wires); } for (Wire w : wiresSnapshot) { Wire.RelayPoint relay = w.getRelayPointAt(p); if (relay != null) return relay; } return null; }

    public void addRelayPointRequest(Wire wire, Point p) {
        if (wire == null || simulationStarted) return;
        if (wire.getRelayPointsCount() >= Wire.MAX_RELAY_POINTS) {
            game.showTemporaryMessage("Maximum relay points (3) reached for this wire.", Color.ORANGE, 2000);
            if (!game.isMuted()) game.playSoundEffect("error");
            return;
        }

        if (!gameState.spendCoins(RELAY_COST)) {
            game.showTemporaryMessage("Not enough coins to add a relay point! Cost: " + RELAY_COST, Color.RED, 2500);
            if (!game.isMuted()) game.playSoundEffect("error");
            return;
        }

        int oldLength = (int)Math.round(wire.getLength());
        wire.addRelayPoint(new Point2D.Double(p.x, p.y));
        int newLength = (int)Math.round(wire.getLength());
        int deltaLength = newLength - oldLength;

        if (deltaLength > 0) {
            gameState.useWire(deltaLength);
        } else if (deltaLength < 0) {
            gameState.returnWire(-deltaLength);
        }

        if (!game.isMuted()) game.playSoundEffect("ui_confirm");
        validateAndSetPredictionFlag();
        repaint();
    }

    public void deleteRelayPointRequest(Wire.RelayPoint relayPoint) {
        if (relayPoint == null || simulationStarted) return;
        Wire parentWire = relayPoint.getParentWire();
        int oldLength = (int)Math.round(parentWire.getLength());
        parentWire.removeRelayPoint(relayPoint);
        int newLength = (int)Math.round(parentWire.getLength());
        int returnedLength = oldLength - newLength;
        gameState.returnWire(returnedLength);

        gameState.addCoins(RELAY_COST);

        if (!game.isMuted()) game.playSoundEffect("wire_disconnect");
        validateAndSetPredictionFlag();
        repaint();
    }

    public void startRelayPointDrag(Wire.RelayPoint relayPoint) { if (relayPoint == null || simulationStarted) return; this.relayPointDragMode = true; this.draggedRelayPoint = relayPoint; this.draggedRelayPoint.setDragged(true); this.preDragWireLength = (int)Math.round(relayPoint.getParentWire().getLength()); setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR)); repaint(); }

    public void updateDraggedRelayPointPosition(Point newPos) { if (!relayPointDragMode || draggedRelayPoint == null) return; draggedRelayPoint.setPosition(new Point2D.Double(newPos.x, newPos.y)); draggedRelayPoint.getParentWire().recalculateLength(); validateAndSetPredictionFlag(); repaint(); }

    public void stopRelayPointDrag() { if (!relayPointDragMode || draggedRelayPoint == null) return; int finalLength = (int)Math.round(draggedRelayPoint.getParentWire().getLength()); int deltaLength = finalLength - preDragWireLength; if (deltaLength > 0) { gameState.useWire(deltaLength); } else { gameState.returnWire(-deltaLength); } draggedRelayPoint.setDragged(false); draggedRelayPoint = null; relayPointDragMode = false; preDragWireLength = 0; setCursor(Cursor.getDefaultCursor()); validateAndSetPredictionFlag(); repaint(); }

    public void cancelRelayPointDrag() { if (!relayPointDragMode || draggedRelayPoint == null) return; draggedRelayPoint.revertToLastPosition(); draggedRelayPoint.getParentWire().recalculateLength(); stopRelayPointDrag(); }

    public void clearAllHoverStates() { synchronized(wires) { for (Wire w : wires) if (w != null) w.clearHoverStates(); } }

    // --- Standard Getters ---
    public NetworkGame getGame() { return game; }
    public GameState getGameState() { return gameState; }
    public PredictionRunStats getDisplayedPredictionStats() { return displayedPredictionStats; }
    public List<System> getSystems() { return Collections.unmodifiableList(new ArrayList<>(systems)); }
    public List<Wire> getWires() { return Collections.unmodifiableList(new ArrayList<>(wires)); }
    public List<PacketSnapshot> getPredictedPacketStates() { synchronized(predictedPacketStates) { List<PacketSnapshot> sortedSnapshots = new ArrayList<>(predictedPacketStates); sortedSnapshots.sort(Comparator.comparingInt(PacketSnapshot::getOriginalPacketId)); return Collections.unmodifiableList(sortedSnapshots); } }
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
    public boolean isRelayPointDragMode() { return relayPointDragMode; }
}