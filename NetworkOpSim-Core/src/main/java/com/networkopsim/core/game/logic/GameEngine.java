// FILE: /NetworkOpSim-Multiplayer/NetworkOpSim-Core/src/main/java/com/networkopsim/core/game/logic/GameEngine.java
// ================================================================================

package com.networkopsim.core.game.logic;

import com.networkopsim.core.game.model.core.Packet;
import com.networkopsim.core.game.model.core.Port;
import com.networkopsim.core.game.model.core.System;
import com.networkopsim.core.game.model.core.Wire;
import com.networkopsim.core.game.model.state.GameState;
import com.networkopsim.core.game.model.state.PredictedPacketStatus;
import com.networkopsim.core.game.utils.LevelLoader;
import com.networkopsim.shared.model.NetworkEnums;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Point;
import java.awt.geom.Point2D;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * The core logic engine for the game, running on the server.
 * This version is decoupled from any UI components.
 */
public class GameEngine {
    private static final Logger logger = LoggerFactory.getLogger(GameEngine.class);
    public static final long SYSTEM_DISABLE_DURATION_MS = 3500;
    private static final double IMPACT_WAVE_RADIUS = 180.0;
    private static final double IMPACT_WAVE_MAX_NOISE = 1.0;
    private static final double DIRECT_COLLISION_NOISE_PER_PACKET = 1.0;
    private static final double IMPACT_WAVE_TORQUE_FACTOR = 5.0;

    private final GameState gameState;
    private int currentLevel;
    private final List<System> systems = new ArrayList<>();
    private final List<Wire> wires = new ArrayList<>();
    private final List<Packet> packets = new ArrayList<>();
    private volatile long simulationTimeElapsedMs = 0;
    private volatile boolean simulationRunning = false;
    private volatile boolean simulationPaused = false;
    private int totalPacketsSuccessfullyDelivered = 0;

    // Buffers for concurrent modification
    private final List<Packet> packetsToAdd = Collections.synchronizedList(new ArrayList<>());
    private final List<Packet> packetsToRemove = Collections.synchronizedList(new ArrayList<>());
    private final List<Wire> wiresToRemove = Collections.synchronizedList(new ArrayList<>());
    private final List<Wire> wiresUsedByBulkPacketsThisTick = Collections.synchronizedList(new ArrayList<>());

    private final Map<Integer, Set<Integer>> bulkPartTracker = new ConcurrentHashMap<>();
    private final Set<Pair<Integer, Integer>> activelyCollidingPairs = Collections.synchronizedSet(new HashSet<>());
    private static final int SPATIAL_GRID_CELL_SIZE = 60;

    private static class Pair<T, U> implements Serializable {
        private static final long serialVersionUID = 1L;
        final T first; final U second;
        Pair(T first, U second) { this.first = first; this.second = second; }
        @Override public boolean equals(Object o) { if (this == o) return true; if (o == null || getClass() != o.getClass()) return false; Pair<?, ?> pair = (Pair<?, ?>) o; return Objects.equals(first, pair.first) && Objects.equals(second, pair.second); }
        @Override public int hashCode() { return Objects.hash(first, second); }
    }

    // Constructor for server-side use
    public GameEngine(GameState gameState, int level) {
        this.gameState = gameState;
        this.currentLevel = level;
    }

    // Constructor for offline/client use
    public GameEngine(GameState gameState) {
        this(gameState, 1);
    }

    public void purchaseItem(int playerId, String itemName) {
        // This is a simplified implementation. A more robust one would use a map of items.
        switch (itemName) {
            case "O' Anahita":
                if (gameState.spendCoins(playerId, 5)) {
                    getAllActivePackets().forEach(Packet::resetNoise);
                }
                break;
            case "Emergency Brake":
                if (gameState.spendCoins(playerId, 8)) {
                    getAllActivePackets().forEach(p -> p.setCurrentSpeedMagnitude(Packet.BASE_SPEED_MAGNITUDE));
                }
                break;
            // TODO: Implement other power-ups like Atar, Airyaman, Speed Limiter by setting flags in GameState
            default:
                logger.warn("Attempted to purchase unknown or unimplemented item: {}", itemName);
                break;
        }
    }


    private void handleCollision(Packet p1, Packet p2, boolean isPredictionRun, boolean isAtarActive) {
        if (!isPredictionRun) {
            logger.info("Collision between Packet {} and {}", p1.getId(), p2.getId());
        }
        p1.addNoise(DIRECT_COLLISION_NOISE_PER_PACKET);
        p2.addNoise(DIRECT_COLLISION_NOISE_PER_PACKET);
        Point2D.Double p1VisPos = p1.getVisualPosition();
        Point2D.Double p2VisPos = p2.getVisualPosition();
        p1.setVisualOffsetDirectionFromForce(new Point2D.Double(p1VisPos.x - p2VisPos.x, p1VisPos.y - p2VisPos.y));
        p2.setVisualOffsetDirectionFromForce(new Point2D.Double(p2VisPos.x - p1VisPos.x, p2VisPos.y - p1VisPos.y));

        if (p1.getPacketType() == NetworkEnums.PacketType.MESSENGER && p1.getShape() == NetworkEnums.PacketShape.CIRCLE) p1.reverseDirection(this);
        if (p2.getPacketType() == NetworkEnums.PacketType.MESSENGER && p2.getShape() == NetworkEnums.PacketShape.CIRCLE) p2.reverseDirection(this);

        if (!isAtarActive) {
            handleImpactWave(new Point((int)((p1VisPos.x + p2VisPos.x)/2), (int)((p1VisPos.y + p2VisPos.y)/2)), p1, p2);
        }
    }

    public void runSimulationTickLogic(boolean isPredictionRun, long currentTotalSimTimeMs, boolean isAtarActive, boolean isAiryamanActive, boolean isSpeedLimiterActive) {
        for (System s : systems) {
            s.updateSystemState(currentTotalSimTimeMs, this);
            if (s.getSystemType() == NetworkEnums.SystemType.SOURCE && (s.isControllable() || s.getTotalPacketsToGenerate() != 0)) {
                s.attemptPacketGeneration(this, currentTotalSimTimeMs, isPredictionRun);
            }
        }
        processBuffersInternal();

        double speedModifier = gameState.getGlobalPacketSpeedModifier();
        for (Packet p : new ArrayList<>(packets)) {
            p.update(this, isAiryamanActive, isSpeedLimiterActive, isPredictionRun, speedModifier);
        }

        processBuffersInternal();
        for (System s : systems) {
            if (s.getSystemType() != NetworkEnums.SystemType.SOURCE) {
                s.processQueue(this, isPredictionRun);
            }
        }
        processBuffersInternal();
        for (System s : systems) {
            if (s.getSystemType() == NetworkEnums.SystemType.ANTITROJAN) {
                s.updateAntiTrojan(this, isPredictionRun);
            }
        }
        if (!isAiryamanActive) {
            detectAndHandleCollisions(isPredictionRun, isAtarActive);
        }
        processWireDestruction(isPredictionRun);
        processBuffersInternal();
    }

    public void gameTick(long tickDurationMs) {
        if (!simulationRunning || simulationPaused) return;
        simulationTimeElapsedMs += tickDurationMs;
        runSimulationTickLogic(false, simulationTimeElapsedMs, false, false, false);
    }

    public void triggerSuccessfulDeliveryFeedback(Packet packet) {
        if (packet == null || packet.getOwnerId() == 0) return;
        gameState.recordSuccessfulDeliveryForFeedback(packet.getOwnerId());
    }

    public void removePacketFromWorld(Packet packet) { if (packet != null && !packetsToRemove.contains(packet)) { packetsToRemove.add(packet); } }
    public void reintroducePacketToWorld(Packet packet) { if (packet != null && !packetsToAdd.contains(packet)) { packetsToAdd.add(packet); } }
    public boolean isWireBlockedByDistributor(Wire wire) { if (wire == null) return false; for (System s : systems) { if (s.getSystemType() == NetworkEnums.SystemType.DISTRIBUTOR) { if (s.getEntryWireIdInUse() == wire.getId()) { return true; } } } return false; }
    public void registerBulkParts(int bulkParentId, List<Packet> parts) { Set<Integer> partIds = parts.stream().map(Packet::getId).collect(Collectors.toSet()); bulkPartTracker.put(bulkParentId, Collections.synchronizedSet(partIds)); }
    public void resolveBulkPart(int bulkParentId, int partId) { Set<Integer> remainingParts = bulkPartTracker.get(bulkParentId); if (remainingParts != null) { remainingParts.remove(partId); } }
    public boolean areAllPartsResolved(int bulkParentId) { Set<Integer> remainingParts = bulkPartTracker.get(bulkParentId); return remainingParts != null && remainingParts.isEmpty(); }
    public void packetLostInternal(Packet packet, boolean isPredictionRun) { if (packet != null && !packet.isMarkedForRemoval()) { if (!isPredictionRun) logger.warn("Lost: {}", packet); packet.markForRemoval(); packet.setFinalStatusForPrediction(PredictedPacketStatus.LOST); packetsToRemove.add(packet); if (packet.getBulkParentId() != -1) { resolveBulkPart(packet.getBulkParentId(), packet.getId()); } gameState.increasePacketLoss(packet); } }
    public void addPacketInternal(Packet packet, boolean isPredictionRun) { if (packet != null) { if (!isPredictionRun) logger.debug("Generated: {}", packet); packetsToAdd.add(packet); gameState.recordPacketGeneration(packet); } }
    public void packetSuccessfullyDeliveredInternal(Packet packet, boolean isPredictionRun) { if (packet != null && !packet.isMarkedForRemoval()) { if (!isPredictionRun) logger.info("Delivered: {}", packet); packet.markForRemoval(); packet.setFinalStatusForPrediction(PredictedPacketStatus.DELIVERED); packetsToRemove.add(packet); if (!isPredictionRun) { totalPacketsSuccessfullyDelivered++; } } }

    public boolean initializeLevel(int level) {
        logger.info("GameEngine: Initializing level {}.", level);
        stopAndCleanupLevel();
        this.currentLevel = level;
        LevelLoader.LevelLayout layout = LevelLoader.loadLevel(level, gameState);
        if (layout == null) {
            logger.error("GameEngine: Failed to load level layout for level {}.", level);
            return false;
        }
        gameState.resetForNewLevel();
        Packet.resetGlobalId();
        System.resetGlobalId();
        Port.resetGlobalId();
        System.resetGlobalRandomSeed(java.lang.System.currentTimeMillis());
        systems.addAll(layout.systems);
        wires.addAll(layout.wires);
        bulkPartTracker.clear();
        return true;
    }

    public void startSimulation() {
        logger.info("GameEngine: Starting simulation for level {}.", currentLevel);
        simulationRunning = true;
        simulationPaused = false;
        gameState.resetForSimulationAttemptOnly();
        Packet.resetGlobalId();
        System.resetGlobalRandomSeed(java.lang.System.currentTimeMillis());
        for (System s : systems) {
            s.resetForNewRun();
        }
        clearSimulationElements();
    }

    public void addRoutingCoinsInternal(Packet packet, boolean isPredictionRun) {
        if (packet != null && !isPredictionRun) {
            gameState.addCoins(packet.getOwnerId(), packet.getBaseCoinValue());
        }
    }

    public void stopSimulation() { logger.info("GameEngine: Stopping simulation timers and flags."); simulationRunning = false; simulationPaused = false; }
    public void stopAndCleanupLevel() { logger.info("GameEngine: Stopping simulation and cleaning up level state."); stopSimulation(); systems.clear(); wires.clear(); clearSimulationElements(); }
    private void clearSimulationElements() { packets.clear(); packetsToAdd.clear(); packetsToRemove.clear(); wiresToRemove.clear(); wiresUsedByBulkPacketsThisTick.clear(); activelyCollidingPairs.clear(); simulationTimeElapsedMs = 0; totalPacketsSuccessfullyDelivered = 0; bulkPartTracker.clear(); }
    public void setPaused(boolean pause) { if (!simulationRunning) return; if (pause && !simulationPaused) { logger.info("GameEngine: Simulation paused."); simulationPaused = true; } else if (!pause && simulationPaused) { logger.info("GameEngine: Simulation resumed."); simulationPaused = false; } }
    private void processBuffersInternal() { if (!packetsToRemove.isEmpty()) { packets.removeAll(packetsToRemove); packetsToRemove.clear(); } if (!packetsToAdd.isEmpty()) { packets.addAll(packetsToAdd); packetsToAdd.clear(); } if (!wiresToRemove.isEmpty()) { for (Wire w : wiresToRemove) { if(wires.remove(w)){ w.destroy(); logger.warn("Wire {} destroyed.", w.getId()); } } wiresToRemove.clear(); } }
    private void processWireDestruction(boolean isPredictionRun) { if(isPredictionRun) return; if (!wiresUsedByBulkPacketsThisTick.isEmpty()) { for (Wire w : wiresUsedByBulkPacketsThisTick) { w.recordBulkPacketTraversal(); if (w.isDestroyed() && !wiresToRemove.contains(w)) wiresToRemove.add(w); } wiresUsedByBulkPacketsThisTick.clear(); } }
    private void detectAndHandleCollisions(boolean isPredictionRun, boolean isAtarActive) { if (packets.isEmpty()) return; Map<Point, List<Packet>> spatialGrid = new HashMap<>(); for (Packet p : packets) { if (p.getCurrentSystem() == null) { Point2D.Double pos = p.getVisualPosition(); if(pos == null) continue; int cellX = (int) (pos.x / SPATIAL_GRID_CELL_SIZE); int cellY = (int) (pos.y / SPATIAL_GRID_CELL_SIZE); spatialGrid.computeIfAbsent(new Point(cellX, cellY), k -> new ArrayList<>()).add(p); } } Set<Pair<Integer, Integer>> currentTickCollisions = new HashSet<>(); Set<Pair<Integer, Integer>> checkedPairsThisTick = new HashSet<>(); for (List<Packet> cellPackets : spatialGrid.values()) { for (int i = 0; i < cellPackets.size(); i++) { for (int j = i + 1; j < cellPackets.size(); j++) { Packet p1 = cellPackets.get(i); Packet p2 = cellPackets.get(j); Pair<Integer, Integer> currentPair = new Pair<>(Math.min(p1.getId(), p2.getId()), Math.max(p1.getId(), p2.getId())); if (checkedPairsThisTick.contains(currentPair)) continue; if (p1.collidesWith(p2)) { currentTickCollisions.add(currentPair); if (!activelyCollidingPairs.contains(currentPair)) { handleCollision(p1, p2, isPredictionRun, isAtarActive); } } checkedPairsThisTick.add(currentPair); } } } activelyCollidingPairs.removeIf(pair -> !currentTickCollisions.contains(pair)); activelyCollidingPairs.addAll(currentTickCollisions); }
    private void handleImpactWave(Point center, Packet ignore1, Packet ignore2) { double waveRadiusSq = IMPACT_WAVE_RADIUS * IMPACT_WAVE_RADIUS; for (Packet p : packets) { if (p == ignore1 || p == ignore2 || p.getCurrentSystem() != null) continue; Point2D.Double pVisPos = p.getVisualPosition(); if(pVisPos == null) continue; double distSq = center.distanceSq(pVisPos); if (distSq < waveRadiusSq && distSq > 1e-6) { double distance = Math.sqrt(distSq); double normalizedDistance = distance / IMPACT_WAVE_RADIUS; double noiseAmount = IMPACT_WAVE_MAX_NOISE * (1.0 - normalizedDistance); Point2D.Double forceDirection = new Point2D.Double(pVisPos.x - center.x, pVisPos.y - center.y); p.setVisualOffsetDirectionFromForce(forceDirection); p.addNoise(noiseAmount); double torqueMagnitude = IMPACT_WAVE_TORQUE_FACTOR * (1.0 - normalizedDistance); p.applyTorque(forceDirection, torqueMagnitude); } } }
    public void rebuildTransientReferences() { Map<Integer, System> systemMap = systems.stream().collect(Collectors.toMap(System::getId, s -> s)); Map<Integer, Wire> wireMap = wires.stream().collect(Collectors.toMap(Wire::getId, w -> w)); for (System s : systems) s.reinitializeBehavior(); for (Wire w : wires) w.rebuildTransientReferences(systemMap); for (Packet p : packets) p.rebuildTransientReferences(systemMap, wireMap); }

    public GameState getGameState() { return gameState; }
    public int getCurrentLevel() { return currentLevel; }
    public List<System> getSystems() { return systems; }
    public List<Wire> getWires() { return wires; }
    public List<Packet> getPackets() { return packets; }
    public boolean isSimulationRunning() { return simulationRunning; }
    public boolean isSimulationPaused() { return simulationPaused; }
    public long getSimulationTimeElapsedMs() { return simulationTimeElapsedMs; }
    public List<Packet> getPacketsForRendering() { return packets.stream().filter(p -> p != null && p.getCurrentSystem() == null && !p.isGhost()).collect(Collectors.toList()); }
    public List<Packet> getAllActivePackets() { List<Packet> all = new ArrayList<>(packets); for(System s : systems) { all.addAll(s.packetQueue); } return all; }
    public void logBulkPacketWireUsage(Wire wire) { if (wire != null && !wiresUsedByBulkPacketsThisTick.contains(wire)) wiresUsedByBulkPacketsThisTick.add(wire); }
    public Wire findWireFromPort(Port outputPort) { if (outputPort == null || outputPort.getType() != NetworkEnums.PortType.OUTPUT) return null; for (Wire w : wires) { if (w.getStartPort().equals(outputPort)) return w; } return null; }
    public boolean isWireOccupied(Wire wire, boolean isPredictionRun) { if (wire == null) return false; for (Packet p : packets) { if (p.getCurrentWire() != null && p.getCurrentWire().equals(wire)) { return true; } } return false; }
}