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
    private static final long TIME_SCRUB_INCREMENT_MS = 10;
    private static final long MAX_PREDICTION_TIME_MS = 30000;
    private static final double PREDICTION_FLOAT_TOLERANCE = 1e-9;
    private static final double DIRECT_COLLISION_FORCE_MAGNITUDE = 0.6;
    private static final double IMPACT_WAVE_RADIUS = 180.0;
    private static final double IMPACT_WAVE_MAX_NOISE = 1.0;
    private static final double DIRECT_COLLISION_NOISE_PER_PACKET = 1.0;
    public static final Color DEFAULT_WIRING_COLOR = Color.LIGHT_GRAY;
    public static final Color VALID_WIRING_COLOR_TARGET = new Color(0, 200, 0);
    public static final Color INVALID_WIRING_COLOR = new Color(220, 0, 0);
    private static final int SPATIAL_GRID_CELL_SIZE = 60; 
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
    private static final Random predictionRandom = new Random();
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
    private final Set<Pair<Integer,Integer>> activelyCollidingPairs = new HashSet<>();
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
        showHUD = true;
        hudTimer.restart();
        updatePrediction();
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
        if (!validateNetwork()) {
            game.playSoundEffect("error");
            return;
        }
        java.lang.System.out.println("Network validated. Starting ACTUAL simulation...");
        simulationStarted = true;
        gameRunning = true;
        gamePaused = false;
        lastTickTime = java.lang.System.nanoTime();
        viewedTimeMs = 0;
        simulationTimeElapsedMs = 0;
        activelyCollidingPairs.clear();
        synchronized(predictedPacketStates){ predictedPacketStates.clear();}
        gameLoopTimer.start();
        game.playSoundEffect("level_start");
        repaint();
    }
    private boolean validateNetwork() { List<System> currentSystems; List<Wire> currentWires; synchronized(systems) { currentSystems = new ArrayList<>(systems); } synchronized(wires) { currentWires = new ArrayList<>(wires); } if (!GraphUtils.isNetworkConnected(currentSystems, currentWires)) { JOptionPane.showMessageDialog(this, "Network Validation Failed:\nAll systems must be connected.", "Network Not Connected", JOptionPane.WARNING_MESSAGE); return false; } if (!GraphUtils.areEssentialPortsConnected(currentSystems)) { JOptionPane.showMessageDialog(this, "Network Validation Failed:\nAll Source outputs and Sink inputs must be connected.", "Unconnected Essential Ports", JOptionPane.WARNING_MESSAGE); return false; } java.lang.System.out.println("Validation Pass: Network configuration is valid."); return true; }
    public void stopSimulation() { if (gameRunning || gameLoopTimer.isRunning()) { java.lang.System.out.println("<<< Stopping simulation loop. >>>"); gameRunning = false; gamePaused = false; gameLoopTimer.stop(); deactivateAtar(); atarTimer.stop(); deactivateAiryaman(); airyamanTimer.stop(); repaint(); } }
    public void pauseGame(boolean pause) { if (!simulationStarted || gameOver || levelComplete) return; if (pause && !gamePaused) { gamePaused = true; gameLoopTimer.stop(); if(atarTimer.isRunning()) atarTimer.stop(); if(airyamanTimer.isRunning()) airyamanTimer.stop(); java.lang.System.out.println("|| Game Paused."); repaint(); } else if (!pause && gamePaused) { gamePaused = false; lastTickTime = java.lang.System.nanoTime(); gameLoopTimer.start(); if(atarActive && !atarTimer.isRunning()) atarTimer.start(); if(airyamanActive && !airyamanTimer.isRunning()) airyamanTimer.start(); java.lang.System.out.println(">> Game Resumed."); repaint(); } }
    private void gameTick() {
        if (!gameRunning || gamePaused || gameOver || levelComplete || !simulationStarted) {
            if (gameOver || levelComplete) {
                stopSimulation();
            }
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
        long currentTimeMillis = java.lang.System.currentTimeMillis();
        processPacketBuffers();
        synchronized (systems) {
            List<System> systemsSnapshot = new ArrayList<>(systems);
            for (System s : systemsSnapshot) {
                if (s != null && s.isReferenceSystem() && s.hasOutputPorts()) {
                    s.attemptPacketGeneration(this, currentTimeMillis);
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
                        game.playSoundEffect("collision");
                        p1.addNoise(DIRECT_COLLISION_NOISE_PER_PACKET);
                        p2.addNoise(DIRECT_COLLISION_NOISE_PER_PACKET);
                        Point2D.Double p1Pos = p1.getPositionDouble();
                        Point2D.Double p2Pos = p2.getPositionDouble();
                        if (p1Pos != null && p2Pos != null) {
                            double dx = p1Pos.getX() - p2Pos.getX();
                            double dy = p1Pos.getY() - p2Pos.getY();
                            double dist = Math.sqrt(dx * dx + dy * dy);
                            if (dist > 1e-6) {
                                double forceDirX = dx / dist;
                                double forceDirY = dy / dist;
                                applyPerpendicularForce(p1, forceDirX, forceDirY, DIRECT_COLLISION_FORCE_MAGNITUDE);
                                applyPerpendicularForce(p2, -forceDirX, -forceDirY, DIRECT_COLLISION_FORCE_MAGNITUDE);
                            }
                        }
                        if (!atarActive) {
                            Point impactCenter = calculateImpactCenter(p1Pos, p2Pos);
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
    private void applyPerpendicularForce(Packet packet, double forceDirX, double forceDirY, double forceMagnitude) {
        Wire wire = packet.getCurrentWire();
        if (wire == null) return;
        Point2D.Double wireDir = wire.getDirectionVector();
        if (wireDir == null) return;
        double perpX = -wireDir.y;
        double perpY = wireDir.x;
        double dotProduct = forceDirX * perpX + forceDirY * perpY;
        if (dotProduct < 0) {
            perpX = -perpX;
            perpY = -perpY;
            dotProduct = -dotProduct;
        }
        Point2D.Double appliedForce = new Point2D.Double(
                perpX * forceMagnitude * dotProduct,
                perpY * forceMagnitude * dotProduct
        );
        packet.applyPerpendicularImpactForce(appliedForce);
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
            if (p == null || p.isMarkedForRemoval() || p.getCurrentSystem() != null || p == ignore1 || p == ignore2) continue;
            Point2D pPos = p.getPositionDouble();
            if (pPos == null) continue;
            double distSq = center.distanceSq(pPos);
            if (distSq < waveRadiusSq && distSq > 1e-6) {
                double distance = Math.sqrt(distSq);
                double normalizedDistance = distance / IMPACT_WAVE_RADIUS;
                double noiseToAdd = IMPACT_WAVE_MAX_NOISE * (1.0 - normalizedDistance);
                noiseToAdd = Math.max(0.0, noiseToAdd);
                if (noiseToAdd > 0) {
                    p.addNoise(noiseToAdd);
                }
            }
        }
    }
    private void checkEndConditions() {
        if (gameOver || levelComplete || !simulationStarted) return;
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
                    game.playSoundEffect("game_over");
                    java.lang.System.out.println("--- GAME OVER (Checked at End) --- Loss: " + String.format("%.1f%% >= %.1f%%", gameState.getPacketLossPercentage(), PACKET_LOSS_GAME_OVER_THRESHOLD));
                    SwingUtilities.invokeLater(() -> showEndLevelDialog(false));
                } else {
                    levelComplete = true;
                    game.playSoundEffect("level_complete");
                    java.lang.System.out.println("--- LEVEL " + currentLevel + " COMPLETE --- Loss: " + String.format("%.1f%%", gameState.getPacketLossPercentage()));
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
            message.append(String.format("\nReason: Packet loss exceeded %.1f%%.", PACKET_LOSS_GAME_OVER_THRESHOLD));
        }
        message.append("\n\n--- Results ---");
        message.append("\nPackets Delivered: ").append(totalPacketsSuccessfullyDelivered);
        message.append("\nPackets Generated: ").append(gameState.getTotalPacketsGeneratedCount());
        message.append("\nPackets Lost: ").append(gameState.getTotalPacketsLostCount());
        message.append("\nPacket Units Lost: ").append(gameState.getTotalPacketLossUnits()).append(" units (").append(String.format("%.1f%%", gameState.getPacketLossPercentage())).append(")");
        message.append("\nTotal Coins (Overall): ").append(gameState.getCoins());
        message.append("\nRemaining Wire Length: ").append(gameState.getRemainingWireLength());
        if (simulationStarted || gameOver || levelComplete) {
            message.append("\nSimulation Time: ").append(String.format("%.2f s", simulationTimeElapsedMs / 1000.0));
        }
        List<String> optionsList = new ArrayList<>();
        String nextLevelOption = null;
        String retryOption = "Retry Level " + currentLevel;
        String menuOption = "Main Menu";
        int nextLevelNumber = currentLevel + 1;
        boolean nextLevelExists = nextLevelNumber <= gameState.getMaxLevels();
        boolean nextLevelUnlocked = nextLevelExists && gameState.isLevelUnlocked(currentLevel);
        if (success) {
            if (nextLevelUnlocked) {
                nextLevelOption = "Next Level (" + nextLevelNumber + ")";
                optionsList.add(nextLevelOption);
                optionsList.add(retryOption);
                optionsList.add(menuOption);
            } else if (!nextLevelExists) {
                message.append("\n\nAll levels completed!");
                optionsList.add(retryOption);
                optionsList.add(menuOption);
            } else {
                java.lang.System.err.println("Warning: Level " + currentLevel + " complete, but next level "+nextLevelNumber+" (index " + currentLevel + ") is not reported as unlocked.");
                optionsList.add(retryOption);
                optionsList.add(menuOption);
            }
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
            java.lang.System.out.println("End level dialog closed or invalid choice, returning to menu.");
        } else {
            selectedOption = options[choice].toString();
        }
        if (selectedOption.equals(menuOption)) {
            game.returnToMenu();
        } else if (selectedOption.equals(retryOption)) {
            game.startGame();
        } else if (nextLevelOption != null && selectedOption.equals(nextLevelOption)) {
            game.setLevel(nextLevelNumber);
            game.startGame();
        } else {
            java.lang.System.err.println("Warning: Unknown option selected in end level dialog: " + selectedOption + ". Returning to menu.");
            game.returnToMenu();
        }
    }
    public void addPacket(Packet packet) { if (packet != null) { synchronized (packetsToAdd) { packetsToAdd.add(packet); } gameState.recordPacketGeneration(packet); } }
    public void packetLost(Packet packet) { if (packet != null && !packet.isMarkedForRemoval()) { packet.markForRemoval(); synchronized (packetsToRemove) { packetsToRemove.add(packet); } gameState.increasePacketLoss(packet); if (!game.isMuted()) game.playSoundEffect("packet_loss"); String reason = "Unknown"; if (packet.getNoise() >= packet.getSize()) { reason = "Noise"; } if(reason.equals("Unknown")) { if (packet.getCurrentSystem() != null && packet.getCurrentSystem().isReferenceSystem() && !packet.getCurrentSystem().hasOutputPorts()){ reason = "Sink Rejection"; } else { reason = "Queue Full / Routing Error / Off Wire"; } } java.lang.System.out.println("LOST Pkt " + packet.getId() + " (Reason: "+reason+"). Loss now: " + String.format("%.1f%%", gameState.getPacketLossPercentage())); } }
    public void packetSuccessfullyDelivered(Packet packet) { if (packet != null && !packet.isMarkedForRemoval()) { packet.markForRemoval(); synchronized (packetsToRemove) { packetsToRemove.add(packet); } totalPacketsSuccessfullyDelivered++; if (!game.isMuted()) game.playSoundEffect("packet_success"); java.lang.System.out.println("DELIVERED Pkt " + packet.getId() + "! (Level Total: " + totalPacketsSuccessfullyDelivered + ")"); } }
    public void addRoutingCoins(Packet packet) { if (packet != null) gameState.addCoins(packet.getBaseCoinValue()); }
    public void activateAtar() { if (!atarActive) { atarActive = true; game.playSoundEffect("powerup_activate"); java.lang.System.out.println(">>> Atar Activated (No Impact Wave Noise)"); } else { java.lang.System.out.println(">>> Atar Refreshed"); } atarTimer.restart(); repaint(); }
    private void deactivateAtar() { if (atarActive) { atarActive = false; game.playSoundEffect("powerup_deactivate"); java.lang.System.out.println("<<< Atar Deactivated"); repaint(); } atarTimer.stop(); }
    public void activateAiryaman() { if (!airyamanActive) { airyamanActive = true; game.playSoundEffect("powerup_activate"); java.lang.System.out.println(">>> Airyaman Activated (No Collision Effects)"); } else { java.lang.System.out.println(">>> Airyaman Refreshed"); } airyamanTimer.restart(); repaint(); }
    private void deactivateAiryaman() { if (airyamanActive) { airyamanActive = false; game.playSoundEffect("powerup_deactivate"); java.lang.System.out.println("<<< Airyaman Deactivated"); repaint(); } airyamanTimer.stop(); }
    public void activateAnahita() { game.playSoundEffect("powerup_activate_special"); int count = 0; List<Packet> snapshot; synchronized (packets) { snapshot = new ArrayList<>(packets); } for (Packet p : snapshot) { if (p != null && !p.isMarkedForRemoval() && p.getNoise() > 0) { p.resetNoise(); count++; } } java.lang.System.out.println(">>> Anahita Activated! Reset noise for " + count + " packet(s)."); repaint(); }
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
            game.playSoundEffect("error");
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
            return false;
        }
        int wireLength = (int) Math.round(startPort.getPosition().distance(endPort.getPosition()));
        if (wireLength <= 0) {
            java.lang.System.err.println("Wire creation failed: Zero length.");
            return false;
        }
        if (gameState.useWire(wireLength)) {
            try {
                Wire newWire = new Wire(startPort, endPort);
                synchronized (wires) {
                    wires.add(newWire);
                }
                java.lang.System.out.println("Wire created: " + newWire.getId() + " Len: " + wireLength + ". Rem: " + gameState.getRemainingWireLength());
                game.playSoundEffect("wire_connect");
                updatePrediction();
                return true;
            } catch (IllegalArgumentException | IllegalStateException e) {
                java.lang.System.err.println("Wire creation failed in constructor: " + e.getMessage());
                gameState.returnWire(wireLength);
                JOptionPane.showMessageDialog(this.game, "Cannot create wire:\n" + e.getMessage(), "Wiring Error", JOptionPane.WARNING_MESSAGE);
                return false;
            }
        } else {
            JOptionPane.showMessageDialog(this.game,
                    "Not enough wire! Need: " + wireLength + ", Have: " + gameState.getRemainingWireLength(),
                    "Insufficient Wire", JOptionPane.WARNING_MESSAGE);
            game.playSoundEffect("error");
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
                game.playSoundEffect("error");
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
            wireToDelete.destroy();
            game.playSoundEffect("wire_delete");
            java.lang.System.out.println("Wire " + wireToDelete.getId() + " deleted. Ret: " + returnedLength + ". Rem: " + gameState.getRemainingWireLength());
            updatePrediction();
            repaint();
        } else {
            java.lang.System.err.println("Warn: Failed to remove wire " + wireToDelete.getId());
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
            List<Wire> wiresSnapshot = new ArrayList<>(wires);
            for (Wire w : wiresSnapshot) {
                if (w != null && Objects.equals(w.getStartPort(), outputPort)) return w;
            }
        }
        return null;
    }
    public boolean isWireOccupied(Wire wire) {
        if (wire == null || !simulationStarted) return false;
        synchronized (packets) {
            List<Packet> currentPackets = new ArrayList<>(packets);
            for (Packet p : currentPackets) {
                if (p != null && !p.isMarkedForRemoval() && Objects.equals(p.getCurrentWire(), wire)) {
                    return true;
                }
            }
        }
        return false;
    }
    public void toggleHUD() {
        showHUD = !showHUD;
        if (showHUD) hudTimer.restart();
        else hudTimer.stop();
        repaint();
    }
    public void incrementViewedTime() {
        if (!simulationStarted) {
            viewedTimeMs = Math.min(MAX_PREDICTION_TIME_MS, viewedTimeMs + TIME_SCRUB_INCREMENT_MS);
            updatePrediction();
        }
    }
    public void decrementViewedTime() {
        if (!simulationStarted) {
            viewedTimeMs = Math.max(0, viewedTimeMs - TIME_SCRUB_INCREMENT_MS);
            updatePrediction();
        }
    }
    private void updatePrediction() {
        if (simulationStarted) return;
        List<PacketSnapshot> newPrediction = predictNetworkStateDetailed(this.viewedTimeMs);
        synchronized(predictedPacketStates) {
            predictedPacketStates.clear();
            predictedPacketStates.addAll(newPrediction);
        }
        repaint();
    }
    private List<PacketSnapshot> predictNetworkStateDetailed(long targetTimeMs) {
        List<PacketSnapshot> snapshots = new ArrayList<>();
        List<System.PredictedPacketInfo> potentialPacketsInfo = new ArrayList<>();
        synchronized (systems) {
            List<System> currentSystems = new ArrayList<>(systems);
            for (System s : currentSystems) {
                if (s != null && s.isReferenceSystem() && s.hasOutputPorts()) potentialPacketsInfo.addAll(s.predictGeneratedPackets(targetTimeMs));
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
                java.lang.System.err.println("Pred Err: Invalid start port Pkt " + packetInfo.futurePacketId);
                snapshots.add(new PacketSnapshot(packetInfo.futurePacketId, packetInfo.shape, null, PredictedPacketStatus.LOST, 0.0));
                continue;
            }
            Point currentPosition = new Point(currentPort.getPosition());
            Wire currentWire = findWireFromPort(currentPort);
            double progress = 0.0;
            PredictedPacketStatus currentStatus = PredictedPacketStatus.ON_WIRE;
            int currentSystemId = -1;
            if (currentWire == null) {
                snapshots.add(new PacketSnapshot(packetInfo.futurePacketId, packetInfo.shape, currentPosition, PredictedPacketStatus.LOST, 0.0));
                continue;
            }
            NetworkEnums.PacketShape packetShapeEnum = packetInfo.shape;
            NetworkEnums.PortShape exitPortShape = currentPort.getShape();
            NetworkEnums.PortShape requiredPortShapeForPacket = Port.getShapeEnum(packetShapeEnum);
            boolean compatibleExit = (requiredPortShapeForPacket != null && (exitPortShape == requiredPortShapeForPacket || exitPortShape == NetworkEnums.PortShape.ANY));
            double currentSpeed; boolean isAccelerating; double targetSpeed;
            if (packetShapeEnum == NetworkEnums.PacketShape.SQUARE) {
                currentSpeed = compatibleExit ? (Packet.BASE_SPEED_MAGNITUDE * Packet.SQUARE_COMPATIBLE_SPEED_FACTOR) : Packet.BASE_SPEED_MAGNITUDE;
                isAccelerating = false; targetSpeed = currentSpeed;
            } else if (packetShapeEnum == NetworkEnums.PacketShape.TRIANGLE) {
                currentSpeed = Packet.BASE_SPEED_MAGNITUDE; isAccelerating = !compatibleExit;
                targetSpeed = isAccelerating ? Packet.MAX_SPEED_MAGNITUDE : currentSpeed;
            } else {
                currentSpeed = Packet.BASE_SPEED_MAGNITUDE; isAccelerating = false; targetSpeed = currentSpeed;
            }
            double currentSimSpeedPerMs = currentSpeed / GAME_TICK_MS;
            while (currentSimTime < targetTimeMs) {
                if (currentWire == null) break;
                double timeRemMs = targetTimeMs - currentSimTime;
                double wireLen = currentWire.getLength();
                if (wireLen < PREDICTION_FLOAT_TOLERANCE) progress = 1.0;
                double distRem = wireLen * (1.0 - progress);
                if (isAccelerating) { currentSimSpeedPerMs = (currentSpeed + targetSpeed) / 2.0 / GAME_TICK_MS; }
                else { currentSimSpeedPerMs = currentSpeed / GAME_TICK_MS; }
                if (currentSimSpeedPerMs < PREDICTION_FLOAT_TOLERANCE / GAME_TICK_MS) break;
                double timeToFinishMs = (wireLen > PREDICTION_FLOAT_TOLERANCE) ? (distRem / currentSimSpeedPerMs) : 0.0;
                if (timeToFinishMs <= timeRemMs + PREDICTION_FLOAT_TOLERANCE) {
                    currentSimTime += timeToFinishMs; progress = 1.0; Port endPort = currentWire.getEndPort();
                    if (endPort == null || endPort.getPosition() == null) { currentStatus = PredictedPacketStatus.LOST; currentPosition = (currentWire.getEndPort() != null && currentWire.getEndPort().getPosition() != null) ? currentWire.getEndPort().getPosition() : currentPosition; currentWire = null; break; }
                    currentPosition.setLocation(endPort.getPosition()); System nextSystem = endPort.getParentSystem();
                    if (nextSystem == null) { currentStatus = PredictedPacketStatus.LOST; currentWire = null; break; }
                    if (nextSystem.isReferenceSystem() && !nextSystem.hasOutputPorts()) {
                        boolean compat = false; NetworkEnums.PortShape req = Port.getShapeEnum(packetInfo.shape);
                        if (req != null) {
                            synchronized(nextSystem.getInputPorts()) {
                                for (Port p : nextSystem.getInputPorts()) {
                                    if (p != null && (p.getShape() == req || p.getShape() == NetworkEnums.PortShape.ANY)) { compat = true; break; }
                                }
                            }
                        }
                        currentStatus = compat ? PredictedPacketStatus.DELIVERED : PredictedPacketStatus.LOST; currentWire = null; break;
                    } else {
                        Port nextOut = findPredictedNextPort(packetInfo.shape, nextSystem);
                        if (nextOut != null && nextOut.getPosition() != null) {
                            Wire nextW = findWireFromPort(nextOut);
                            if (nextW != null) {
                                currentWire = nextW; currentPort = nextOut; currentPosition.setLocation(currentPort.getPosition()); progress = 0.0; currentStatus = PredictedPacketStatus.ON_WIRE;
                                exitPortShape = currentPort.getShape(); requiredPortShapeForPacket = Port.getShapeEnum(packetShapeEnum); compatibleExit = (requiredPortShapeForPacket != null && (exitPortShape == requiredPortShapeForPacket || exitPortShape == NetworkEnums.PortShape.ANY));
                                if (packetShapeEnum == NetworkEnums.PacketShape.SQUARE) { currentSpeed = compatibleExit ? (Packet.BASE_SPEED_MAGNITUDE * Packet.SQUARE_COMPATIBLE_SPEED_FACTOR) : Packet.BASE_SPEED_MAGNITUDE; isAccelerating = false; targetSpeed = currentSpeed; }
                                else if (packetShapeEnum == NetworkEnums.PacketShape.TRIANGLE) { currentSpeed = Packet.BASE_SPEED_MAGNITUDE; isAccelerating = !compatibleExit; targetSpeed = isAccelerating ? Packet.MAX_SPEED_MAGNITUDE : currentSpeed; }
                                else { currentSpeed = Packet.BASE_SPEED_MAGNITUDE; isAccelerating = false; targetSpeed = currentSpeed; }
                            } else { currentStatus = PredictedPacketStatus.STALLED_AT_NODE; currentPosition.setLocation(nextSystem.getPosition()); currentSystemId = nextSystem.getId(); currentWire = null; break; }
                        } else { currentStatus = PredictedPacketStatus.STALLED_AT_NODE; currentPosition.setLocation(nextSystem.getPosition()); currentSystemId = nextSystem.getId(); currentWire = null; break; }
                    }
                } else {
                    double distToMove = currentSimSpeedPerMs * timeRemMs;
                    if (wireLen > PREDICTION_FLOAT_TOLERANCE) { progress += (distToMove / wireLen); }
                    progress = Math.max(0.0, Math.min(progress, 1.0));
                    Point newPos = currentWire.getPointAtProgress(progress);
                    if (newPos != null) currentPosition = newPos; else java.lang.System.err.println("Pred Warn: Cannot get pos Pkt " + packetInfo.futurePacketId);
                    currentSimTime = targetTimeMs; currentStatus = PredictedPacketStatus.ON_WIRE; break;
                }
            }
            if (currentStatus == PredictedPacketStatus.STALLED_AT_NODE) { snapshots.add(new PacketSnapshot(packetInfo.futurePacketId, packetInfo.shape, currentPosition, currentStatus, currentSystemId)); }
            else { snapshots.add(new PacketSnapshot(packetInfo.futurePacketId, packetInfo.shape, currentPosition, currentStatus, progress)); }
        }
        return snapshots;
    }
    private Port findPredictedNextPort(NetworkEnums.PacketShape packetShape, System node) {
        if (node == null || (node.isReferenceSystem() && !node.hasOutputPorts())) return null;
        if (node.isReferenceSystem() && node.hasOutputPorts()) { java.lang.System.err.println("Prediction Warning: Packet reached a Source system ("+node.getId()+") during prediction. Invalid route."); return null; }
        List<Port> compatiblePorts = new ArrayList<>();
        List<Port> anyPorts = new ArrayList<>();
        NetworkEnums.PortShape requiredShape = Port.getShapeEnum(packetShape);
        if (requiredShape == null) return null;
        synchronized(node.getOutputPorts()) {
            List<Port> currentOutputPorts = new ArrayList<>(node.getOutputPorts());
            for (Port p : currentOutputPorts) {
                if (p != null && p.isConnected()) {
                    if (p.getShape() == requiredShape) compatiblePorts.add(p);
                    else if (p.getShape() == NetworkEnums.PortShape.ANY) anyPorts.add(p);
                }
            }
        }
        if (!compatiblePorts.isEmpty()) { Collections.shuffle(compatiblePorts, predictionRandom); return compatiblePorts.get(0); }
        if (!anyPorts.isEmpty()) { Collections.shuffle(anyPorts, predictionRandom); return anyPorts.get(0); }
        return null;
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
    public long getMaxPredictionTimeMs() { return MAX_PREDICTION_TIME_MS; }
    public long getSimulationTimeElapsedMs() { return simulationTimeElapsedMs; }
}