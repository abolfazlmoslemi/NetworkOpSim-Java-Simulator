// ===== File: GameEngine.java (FINAL - With Bulk Part Tracking System) =====

package com.networkopsim.game.controller.logic;

import com.networkopsim.game.controller.core.NetworkGame;
import com.networkopsim.game.model.core.Packet;
import com.networkopsim.game.model.core.Port;
import com.networkopsim.game.model.core.Wire;
import com.networkopsim.game.model.enums.NetworkEnums;
import com.networkopsim.game.model.state.GameState;
import com.networkopsim.game.model.state.PredictedPacketStatus;
import com.networkopsim.game.utils.GameStateManager;
import com.networkopsim.game.view.panels.GamePanel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.geom.Point2D;
import java.awt.*;
import java.io.Serializable;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class GameEngine {
    private static final Logger logger = LoggerFactory.getLogger(GameEngine.class);

    // ... (Constants are unchanged) ...
    public static final long SYSTEM_DISABLE_DURATION_MS = 3500;
    private static final double IMPACT_WAVE_RADIUS = 180.0;
    private static final double IMPACT_WAVE_MAX_NOISE = 1.0;
    private static final double DIRECT_COLLISION_NOISE_PER_PACKET = 1.0;
    private static final double IMPACT_WAVE_TORQUE_FACTOR = 5.0;

    private final NetworkGame game;
    private GameState gameState;
    private final GamePanel gamePanel;
    private int currentLevel;
    private List<com.networkopsim.game.model.core.System> systems = new ArrayList<>();
    private List<Wire> wires = new ArrayList<>();
    private List<Packet> packets = new ArrayList<>();
    private volatile long simulationTimeElapsedMs = 0;
    private volatile boolean simulationRunning = false;
    private volatile boolean simulationPaused = false;
    private int totalPacketsSuccessfullyDelivered = 0;
    private final List<Packet> packetsToAdd = new ArrayList<>();
    private final List<Packet> packetsToRemove = new ArrayList<>();
    private final List<Wire> wiresToRemove = new ArrayList<>();
    private final List<Wire> wiresUsedByBulkPacketsThisTick = new ArrayList<>();

    // [NEW] Central tracking system for bulk packet parts.
    // Key: bulkParentId, Value: Set of IDs of unresolved messenger parts.
    private final Map<Integer, Set<Integer>> bulkPartTracker = new ConcurrentHashMap<>();

    private final Set<Pair<Integer, Integer>> activelyCollidingPairs = new HashSet<>();
    private static final int SPATIAL_GRID_CELL_SIZE = 60;

    private static class Pair<T, U> implements Serializable { /* ... Body unchanged ... */
        final T first; final U second;
        Pair(T first, U second) { this.first = first; this.second = second; }
        @Override public boolean equals(Object o) { if (this == o) return true; if (o == null || getClass() != o.getClass()) return false; Pair<?, ?> pair = (Pair<?, ?>) o; return Objects.equals(first, pair.first) && Objects.equals(second, pair.second); }
        @Override public int hashCode() { return Objects.hash(first, second); }
    }

    public GameEngine(NetworkGame game, GamePanel gamePanel) { this.game = game; this.gamePanel = gamePanel; this.gameState = game.getGameState(); }

    public void packetLostInternal(Packet packet, boolean isPredictionRun) {
        if (packet != null && !packet.isMarkedForRemoval()) {
            if (!isPredictionRun) logger.warn("Lost: {}", packet);
            packet.markForRemoval();
            packet.setFinalStatusForPrediction(PredictedPacketStatus.LOST);
            packetsToRemove.add(packet);

            // [MODIFIED] If a lost packet is a bulk part, update the tracker.
            if (packet.getBulkParentId() != -1) {
                resolveBulkPart(packet.getBulkParentId(), packet.getId());
            }

            if (isPredictionRun) {
                gamePanel.getPredictionContext().registerLostPacket(packet);
            } else {
                // Let GameState handle the loss calculation logic.
                gameState.increasePacketLoss(packet);
                if (!game.isMuted()) game.playSoundEffect("packet_loss");
            }
        }
    }

    // --- Bulk Part Tracking Methods ---

    public void registerBulkParts(int bulkParentId, List<Packet> parts) {
        Set<Integer> partIds = parts.stream().map(Packet::getId).collect(Collectors.toSet());
        bulkPartTracker.put(bulkParentId, Collections.synchronizedSet(partIds));
    }

    public void resolveBulkPart(int bulkParentId, int partId) {
        Set<Integer> remainingParts = bulkPartTracker.get(bulkParentId);
        if (remainingParts != null) {
            remainingParts.remove(partId);
        }
    }

    public boolean areAllPartsResolved(int bulkParentId) {
        Set<Integer> remainingParts = bulkPartTracker.get(bulkParentId);
        // If the entry exists and the set is empty, all parts are resolved.
        return remainingParts != null && remainingParts.isEmpty();
    }

    // ... (Rest of the file is unchanged, only packetLostInternal and new methods are modified/added) ...
    public void runSimulationTickLogic(boolean isPredictionRun, long currentTotalSimTimeMs, boolean isAtarActive, boolean isAiryamanActive, boolean isSpeedLimiterActive) { if (!isPredictionRun) { UIManager.put("game.time.ms", currentTotalSimTimeMs); } for (com.networkopsim.game.model.core.System s : systems) { s.updateSystemState(currentTotalSimTimeMs, this); if (s.getSystemType() == NetworkEnums.SystemType.SOURCE) { s.attemptPacketGeneration(this, currentTotalSimTimeMs, isPredictionRun); } } processBuffersInternal(); for (Packet p : new ArrayList<>(packets)) { if (!isPredictionRun) { gamePanel.applyWireEffectsToPacket(p); } p.update(this, isAiryamanActive, isSpeedLimiterActive, isPredictionRun); } processBuffersInternal(); for (com.networkopsim.game.model.core.System s : systems) { if (s.getSystemType() != NetworkEnums.SystemType.SOURCE) { s.processQueue(this, isPredictionRun); } } processBuffersInternal(); for (com.networkopsim.game.model.core.System s : systems) { if (s.getSystemType() == NetworkEnums.SystemType.ANTITROJAN) { s.updateAntiTrojan(this, isPredictionRun); } } if (!isAiryamanActive) { detectAndHandleCollisions(isPredictionRun, isAtarActive); } processWireDestruction(isPredictionRun); processBuffersInternal(); }
    public boolean isWireBlockedByDistributor(Wire wire) { if (wire == null) return false; for (com.networkopsim.game.model.core.System s : systems) { if (s.getSystemType() == NetworkEnums.SystemType.DISTRIBUTOR) { if (s.getEntryWireIdInUse() == wire.getId()) { return true; } } } return false; }
    public void reintroducePacketToWorld(Packet packet) { if (packet != null && !packetsToAdd.contains(packet)) { packetsToAdd.add(packet); } }
    public void addPacketInternal(Packet packet, boolean isPredictionRun) { if (packet != null) { if (!isPredictionRun) logger.debug("Generated: {}", packet); packetsToAdd.add(packet); if (isPredictionRun) { gamePanel.getPredictionContext().registerGeneratedPacket(packet); } else { gameState.recordPacketGeneration(packet); } } }
    public void packetSuccessfullyDeliveredInternal(Packet packet, boolean isPredictionRun) { if (packet != null && !packet.isMarkedForRemoval()) { if (!isPredictionRun) logger.info("Delivered: {}", packet); packet.markForRemoval(); packet.setFinalStatusForPrediction(PredictedPacketStatus.DELIVERED); packetsToRemove.add(packet); if (!isPredictionRun) { totalPacketsSuccessfullyDelivered++; } } }
    public boolean initializeLevel(int level) { logger.info("GameEngine: Initializing level {}.", level); stopAndCleanupLevel(); com.networkopsim.game.utils.LevelLoader.LevelLayout layout = com.networkopsim.game.utils.LevelLoader.loadLevel(level, gameState, game); if (layout == null) { logger.error("GameEngine: Failed to load level layout for level {}.", level); return false; } gameState.resetForNewLevel(); this.currentLevel = layout.levelNumber; Packet.resetGlobalId(); com.networkopsim.game.model.core.System.resetGlobalId(); Port.resetGlobalId(); com.networkopsim.game.model.core.System.resetGlobalRandomSeed(gamePanel.getPredictionSeed()); systems.addAll(layout.systems); wires.addAll(layout.wires); bulkPartTracker.clear(); return true; }
    public void startSimulation() { logger.info("GameEngine: Starting simulation for level {}.", currentLevel); simulationRunning = true; simulationPaused = false; gameState.resetForSimulationAttemptOnly(); Packet.resetGlobalId(); com.networkopsim.game.model.core.System.resetGlobalRandomSeed(gamePanel.getPredictionSeed()); for (com.networkopsim.game.model.core.System s : systems) { s.resetForNewRun(); } clearSimulationElements(); }
    public void stopSimulation() { logger.info("GameEngine: Stopping simulation timers and flags."); simulationRunning = false; simulationPaused = false; }
    public void stopAndCleanupLevel() { logger.info("GameEngine: Stopping simulation and cleaning up level state."); stopSimulation(); systems.clear(); wires.clear(); clearSimulationElements(); }
    private void clearSimulationElements() { packets.clear(); packetsToAdd.clear(); packetsToRemove.clear(); wiresToRemove.clear(); wiresUsedByBulkPacketsThisTick.clear(); activelyCollidingPairs.clear(); simulationTimeElapsedMs = 0; totalPacketsSuccessfullyDelivered = 0; bulkPartTracker.clear(); }
    public void setPaused(boolean pause) { if (!simulationRunning) return; if (pause && !simulationPaused) { logger.info("GameEngine: Simulation paused."); simulationPaused = true; } else if (!pause && simulationPaused) { logger.info("GameEngine: Simulation resumed."); simulationPaused = false; } }
    public void gameTick(long tickDurationMs) { if (!simulationRunning || simulationPaused) return; simulationTimeElapsedMs += tickDurationMs; runSimulationTickLogic(false, simulationTimeElapsedMs, gamePanel.isAtarActive(), gamePanel.isAiryamanActive(), gamePanel.isSpeedLimiterActive()); }
    public void loadFromSaveData(GameStateManager.SaveData saveData) { logger.info("GameEngine: Loading state from save data. Time: {}ms", saveData.simulationTimeElapsedMs); stopAndCleanupLevel(); this.gameState = saveData.gameState; this.simulationTimeElapsedMs = saveData.simulationTimeElapsedMs; this.systems = new ArrayList<>(saveData.systems); this.wires = new ArrayList<>(saveData.wires); this.packets = new ArrayList<>(saveData.packets); rebuildTransientReferences(); simulationRunning = true; simulationPaused = true; }
    private void processBuffersInternal() { if (!packetsToRemove.isEmpty()) { packets.removeAll(packetsToRemove); packetsToRemove.clear(); } if (!packetsToAdd.isEmpty()) { packets.addAll(packetsToAdd); packetsToAdd.clear(); } if (!wiresToRemove.isEmpty()) { for (Wire w : wiresToRemove) { if(wires.remove(w)){ w.destroy(); logger.warn("Wire {} destroyed.", w.getId()); if (!game.isMuted()) game.playSoundEffect("wire_disconnect"); } } wiresToRemove.clear(); gamePanel.validateAndSetPredictionFlag(); } }
    private void processWireDestruction(boolean isPredictionRun) { if(isPredictionRun) return; if (!wiresUsedByBulkPacketsThisTick.isEmpty()) { for (Wire w : wiresUsedByBulkPacketsThisTick) { w.recordBulkPacketTraversal(); if (w.isDestroyed() && !wiresToRemove.contains(w)) wiresToRemove.add(w); } wiresUsedByBulkPacketsThisTick.clear(); } }
    public void addRoutingCoinsInternal(Packet packet, boolean isPredictionRun) { if (packet != null && !isPredictionRun) { gameState.addCoins(packet.getBaseCoinValue()); } }
    private void detectAndHandleCollisions(boolean isPredictionRun, boolean isAtarActive) { if (packets.isEmpty()) return; Map<Point, List<Packet>> spatialGrid = new HashMap<>(); for (Packet p : packets) { if (p.getCurrentSystem() == null) { Point2D.Double pos = p.getVisualPosition(); if(pos == null) continue; int cellX = (int) (pos.x / SPATIAL_GRID_CELL_SIZE); int cellY = (int) (pos.y / SPATIAL_GRID_CELL_SIZE); spatialGrid.computeIfAbsent(new Point(cellX, cellY), k -> new ArrayList<>()).add(p); } } Set<Pair<Integer, Integer>> currentTickCollisions = new HashSet<>(); Set<Pair<Integer, Integer>> checkedPairsThisTick = new HashSet<>(); for (List<Packet> cellPackets : spatialGrid.values()) { for (int i = 0; i < cellPackets.size(); i++) { for (int j = i + 1; j < cellPackets.size(); j++) { Packet p1 = cellPackets.get(i); Packet p2 = cellPackets.get(j); Pair<Integer, Integer> currentPair = new Pair<>(Math.min(p1.getId(), p2.getId()), Math.max(p1.getId(), p2.getId())); if (checkedPairsThisTick.contains(currentPair)) continue; if (p1.collidesWith(p2)) { currentTickCollisions.add(currentPair); if (!activelyCollidingPairs.contains(currentPair)) { handleCollision(p1, p2, isPredictionRun, isAtarActive); } } checkedPairsThisTick.add(currentPair); } } } activelyCollidingPairs.removeIf(pair -> !currentTickCollisions.contains(pair)); activelyCollidingPairs.addAll(currentTickCollisions); }
    private void handleCollision(Packet p1, Packet p2, boolean isPredictionRun, boolean isAtarActive) { if (!isPredictionRun) { logger.info("Collision between Packet {} and {}", p1.getId(), p2.getId()); if (!game.isMuted()) game.playSoundEffect("collision"); } p1.addNoise(DIRECT_COLLISION_NOISE_PER_PACKET); p2.addNoise(DIRECT_COLLISION_NOISE_PER_PACKET); Point2D.Double p1VisPos = p1.getVisualPosition(); Point2D.Double p2VisPos = p2.getVisualPosition(); p1.setVisualOffsetDirectionFromForce(new Point2D.Double(p1VisPos.x - p2VisPos.x, p1VisPos.y - p2VisPos.y)); p2.setVisualOffsetDirectionFromForce(new Point2D.Double(p2VisPos.x - p1VisPos.x, p2VisPos.y - p1VisPos.y)); if (p1.getPacketType() == NetworkEnums.PacketType.MESSENGER && p1.getShape() == NetworkEnums.PacketShape.CIRCLE) p1.reverseDirection(this); if (p2.getPacketType() == NetworkEnums.PacketType.MESSENGER && p2.getShape() == NetworkEnums.PacketShape.CIRCLE) p2.reverseDirection(this); if (!isAtarActive) { handleImpactWave(new Point((int)((p1VisPos.x + p2VisPos.x)/2), (int)((p1VisPos.y + p2VisPos.y)/2)), p1, p2); } }
    private void handleImpactWave(Point center, Packet ignore1, Packet ignore2) { double waveRadiusSq = IMPACT_WAVE_RADIUS * IMPACT_WAVE_RADIUS; for (Packet p : packets) { if (p == ignore1 || p == ignore2 || p.getCurrentSystem() != null) continue; Point2D.Double pVisPos = p.getVisualPosition(); if(pVisPos == null) continue; double distSq = center.distanceSq(pVisPos); if (distSq < waveRadiusSq && distSq > 1e-6) { double distance = Math.sqrt(distSq); double normalizedDistance = distance / IMPACT_WAVE_RADIUS; double noiseAmount = IMPACT_WAVE_MAX_NOISE * (1.0 - normalizedDistance); Point2D.Double forceDirection = new Point2D.Double(pVisPos.x - center.x, pVisPos.y - center.y); p.setVisualOffsetDirectionFromForce(forceDirection); p.addNoise(noiseAmount); double torqueMagnitude = IMPACT_WAVE_TORQUE_FACTOR * (1.0 - normalizedDistance); p.applyTorque(forceDirection, torqueMagnitude); } } }
    public void rebuildTransientReferences() { Map<Integer, com.networkopsim.game.model.core.System> systemMap = systems.stream().collect(Collectors.toMap(com.networkopsim.game.model.core.System::getId, s -> s)); Map<Integer, Wire> wireMap = wires.stream().collect(Collectors.toMap(Wire::getId, w -> w)); for (com.networkopsim.game.model.core.System s : systems) s.reinitializeBehavior(); for (Wire w : wires) w.rebuildTransientReferences(systemMap); for (Packet p : packets) p.rebuildTransientReferences(systemMap, wireMap); }
    public NetworkGame getGame() { return game; }
    public GamePanel getGamePanel() { return gamePanel; }
    public GameState getGameState() { return gameState; }
    public void setGameState(GameState gameState) { this.gameState = gameState; }
    public List<com.networkopsim.game.model.core.System> getSystems() { return systems; }
    public List<Wire> getWires() { return wires; }
    public List<Packet> getPackets() { return packets; }
    public void setSystems(List<com.networkopsim.game.model.core.System> systems) { this.systems = systems; }
    public void setWires(List<Wire> wires) { this.wires = wires; }
    public void setPackets(List<Packet> packets) { this.packets = packets; }
    public boolean isSimulationRunning() { return simulationRunning; }
    public boolean isSimulationPaused() { return simulationPaused; }
    public long getSimulationTimeElapsedMs() { return simulationTimeElapsedMs; }
    public int getTotalPacketsSuccessfullyDelivered() { return totalPacketsSuccessfullyDelivered; }
    public List<Packet> getPacketsForRendering() { return packets.stream().filter(p -> p != null && p.getCurrentSystem() == null && !p.isGhost()).collect(Collectors.toList()); }
    public List<Packet> getAllActivePackets() { List<Packet> all = new ArrayList<>(packets); for(com.networkopsim.game.model.core.System s : systems) { all.addAll(s.packetQueue); } return all; }
    public void logBulkPacketWireUsage(Wire wire) { if (wire != null && !wiresUsedByBulkPacketsThisTick.contains(wire)) wiresUsedByBulkPacketsThisTick.add(wire); }
    public Wire findWireFromPort(Port outputPort) { if (outputPort == null || outputPort.getType() != NetworkEnums.PortType.OUTPUT) return null; for (Wire w : wires) { if (w.getStartPort().equals(outputPort)) return w; } return null; }
    public boolean isWireOccupied(Wire wire, boolean isPredictionRun) { if (wire == null) return false; for (Packet p : packets) { if (p.getCurrentWire() != null && p.getCurrentWire().equals(wire)) { return true; } } return false; }
}