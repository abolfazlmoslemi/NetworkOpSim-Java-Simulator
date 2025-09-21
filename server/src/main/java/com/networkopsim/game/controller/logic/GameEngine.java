// ===== File: GameEngine.java (FINAL COMPLETE SERVER VERSION) =====
// ===== MODULE: server =====

package com.networkopsim.game.controller.logic;

import com.networkopsim.game.controller.logic.behaviors.*;
import com.networkopsim.game.model.core.Packet;
import com.networkopsim.game.model.core.Port;
import com.networkopsim.game.model.core.System;
import com.networkopsim.game.model.core.Wire;
import com.networkopsim.game.model.enums.NetworkEnums;
import com.networkopsim.game.model.state.GameState;
import com.networkopsim.game.model.state.PredictedPacketStatus;
// [FIXED] Corrected import path for LevelLoader
import com.networkopsim.server.utils.LevelLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.awt.geom.Point2D;
import java.awt.*;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class GameEngine {
  private static final Logger logger = LoggerFactory.getLogger(GameEngine.class);
  public static final long SYSTEM_DISABLE_DURATION_MS = 3500;
  private static final double IMPACT_WAVE_RADIUS = 180.0;
  private static final double IMPACT_WAVE_MAX_NOISE = 1.0;
  private static final double DIRECT_COLLISION_NOISE_PER_PACKET = 1.0;
  private static final double IMPACT_WAVE_TORQUE_FACTOR = 5.0;
  private static final int SPATIAL_GRID_CELL_SIZE = 60;

  // --- Power-up Durations ---
  private static final int ATAR_DURATION_MS = 10000;
  private static final int AIRYAMAN_DURATION_MS = 5000;
  private static final int SPEED_LIMITER_DURATION_MS = 15000;

  private GameState gameState;
  private int currentLevel;
  private List<System> systems = new ArrayList<>();
  private List<Wire> wires = new ArrayList<>();
  private List<Packet> packets = new ArrayList<>();
  private volatile long simulationTimeElapsedMs = 0;
  private volatile boolean simulationRunning = false;
  private volatile boolean simulationPaused = false;

  // --- Power-up State ---
  private long atarEndTime = 0;
  private long airyamanEndTime = 0;
  private long speedLimiterEndTime = 0;

  private int totalPacketsSuccessfullyDelivered = 0;
  private final List<Packet> packetsToAdd = new ArrayList<>();
  private final List<Packet> packetsToRemove = new ArrayList<>();
  private final List<Wire> wiresToRemove = new ArrayList<>();
  private final List<Wire> wiresUsedByBulkPacketsThisTick = new ArrayList<>();
  private final Map<Integer, Set<Integer>> bulkPartTracker = new ConcurrentHashMap<>();
  private final Set<Pair<Integer, Integer>> activelyCollidingPairs = new HashSet<>();
  private final Map<NetworkEnums.SystemType, SystemBehavior> behaviorMap;

  private static class Pair<T, U> implements Serializable {
    final T first;
    final U second;
    Pair(T first, U second) { this.first = first; this.second = second; }
    @Override public boolean equals(Object o) { if (this == o) return true; if (o == null || getClass() != o.getClass()) return false; Pair<?, ?> pair = (Pair<?, ?>) o; return Objects.equals(first, pair.first) && Objects.equals(second, pair.second); }
    @Override public int hashCode() { return Objects.hash(first, second); }
  }

  public GameEngine(Object ignored, GameState gameState, List<System> systems, List<Wire> wires, List<Packet> packets) {
    this.gameState = gameState;
    if (systems != null) this.systems = systems;
    if (wires != null) this.wires = wires;
    if (packets != null) this.packets = packets;

    behaviorMap = new EnumMap<>(NetworkEnums.SystemType.class);
    behaviorMap.put(NetworkEnums.SystemType.SOURCE, new SourceBehavior());
    behaviorMap.put(NetworkEnums.SystemType.SINK, new SinkBehavior());
    behaviorMap.put(NetworkEnums.SystemType.SPY, new SpyBehavior());
    behaviorMap.put(NetworkEnums.SystemType.CORRUPTOR, new CorruptorBehavior());
    behaviorMap.put(NetworkEnums.SystemType.VPN, new VpnBehavior());
    behaviorMap.put(NetworkEnums.SystemType.DISTRIBUTOR, new DistributorBehavior());
    behaviorMap.put(NetworkEnums.SystemType.MERGER, new MergerBehavior());
    behaviorMap.put(NetworkEnums.SystemType.NODE, new NodeBehavior(NetworkEnums.SystemType.NODE));
    behaviorMap.put(NetworkEnums.SystemType.ANTITROJAN, new NodeBehavior(NetworkEnums.SystemType.ANTITROJAN));
  }

  public SystemBehavior getBehaviorForSystem(System system) {
    return behaviorMap.get(system.getSystemType());
  }

  public void gameTick(long tickDurationMs) {
    if (!simulationRunning || simulationPaused) return;
    simulationTimeElapsedMs += tickDurationMs;
    runSimulationTickLogic(false, simulationTimeElapsedMs, isAtarActive(), isAiryamanActive(), isSpeedLimiterActive());
  }

  public void runSimulationTickLogic(boolean isPredictionRun, long currentTotalSimTimeMs, boolean isAtarActive, boolean isAiryamanActive, boolean isSpeedLimiterActive) {
    updatePowerUpStates();
    updateActiveWireEffects();

    for (System s : systems) {
      updateSystemState(s, currentTotalSimTimeMs);
      if (s.getSystemType() == NetworkEnums.SystemType.SOURCE) {
        getBehaviorForSystem(s).attemptPacketGeneration(s, this, currentTotalSimTimeMs, isPredictionRun);
      }
    }
    processBuffersInternal();
    for (Packet p : new ArrayList<>(packets)) {
      applyWireEffectsToPacket(p);
      updatePacket(p, isAiryamanActive, isSpeedLimiterActive, isPredictionRun);
    }
    processBuffersInternal();
    for (System s : systems) {
      if (s.getSystemType() != NetworkEnums.SystemType.SOURCE) {
        getBehaviorForSystem(s).processQueue(s, this, isPredictionRun);
      }
    }
    processBuffersInternal();
    for (System s : systems) {
      if (s.getSystemType() == NetworkEnums.SystemType.ANTITROJAN) {
        updateAntiTrojan(s, isPredictionRun);
      }
    }
    if (!isAiryamanActive()) {
      detectAndHandleCollisions(isPredictionRun, isAtarActive());
    }
    processWireDestruction(isPredictionRun);
    processBuffersInternal();
  }

  // --- Power-up Activation & State Methods ---
  public void activateAtar() { atarEndTime = simulationTimeElapsedMs + ATAR_DURATION_MS; }
  public void activateAiryaman() { airyamanEndTime = simulationTimeElapsedMs + AIRYAMAN_DURATION_MS; }
  public void activateAnahita() { for (Packet p : getAllActivePackets()) { if (p != null && p.getNoise() > 0) p.revertToOriginalType(); } }
  public void activateSpeedLimiter() { speedLimiterEndTime = simulationTimeElapsedMs + SPEED_LIMITER_DURATION_MS; }
  public void activateEmergencyBrake() { for (Packet p : getAllActivePackets()) { if (p != null && p.getCurrentWire() != null) p.setCurrentSpeedMagnitude(Packet.BASE_SPEED_MAGNITUDE); } }

  public boolean isAtarActive() { return simulationTimeElapsedMs < atarEndTime; }
  public boolean isAiryamanActive() { return simulationTimeElapsedMs < airyamanEndTime; }
  public boolean isSpeedLimiterActive() { return simulationTimeElapsedMs < speedLimiterEndTime; }

  private void updatePowerUpStates() {
    if (atarEndTime > 0 && simulationTimeElapsedMs >= atarEndTime) atarEndTime = 0;
    if (airyamanEndTime > 0 && simulationTimeElapsedMs >= airyamanEndTime) airyamanEndTime = 0;
    if (speedLimiterEndTime > 0 && simulationTimeElapsedMs >= speedLimiterEndTime) speedLimiterEndTime = 0;
  }

  private void updateActiveWireEffects() {
    gameState.getActiveWireEffects().removeIf(effect -> simulationTimeElapsedMs >= effect.expiryTime);
  }

  private void applyWireEffectsToPacket(Packet packet) {
    for (GameState.ActiveWireEffect effect : gameState.getActiveWireEffects()) {
      if (packet.getCurrentWire() != null && effect.parentWireId == packet.getCurrentWire().getId()) {
        Point2D.Double idealPos = packet.getIdealPosition();
        if (idealPos != null && effect.position.distanceSq(idealPos) < 30 * 30) {
          if (effect.type == GameState.InteractiveMode.AERGIA_PLACEMENT) {
            packet.setAccelerating(false);
          } else if (effect.type == GameState.InteractiveMode.ELIPHAS_PLACEMENT) {
            packet.addNoise(-0.1); // Slowly reduces noise
          }
        }
      }
    }
  }

  private void updateSystemState(System system, long currentTimeMs) {
    if (system.isDisabled() && currentTimeMs >= system.getDisabledUntil()) {
      system.setDisabled(false);
      system.setDisabledUntil(0);
      logger.info("System {} has been re-enabled.", system.getId());
    }
  }

  private void updatePacket(Packet packet, boolean isAiryamanActive, boolean isSpeedLimiterActive, boolean isPredictionRun) {
    if (packet.isMarkedForRemoval() || packet.getCurrentSystem() != null || packet.isGhost()) return;

    Wire currentWire = packet.getCurrentWire();
    if (currentWire == null) {
      packetLostInternal(packet, isPredictionRun);
      return;
    }

    packet.incrementTimeOnWire(16);
    if (packet.getTimeOnCurrentWireMs() > Packet.WIRE_TIMEOUT_MS) {
      packetLostInternal(packet, isPredictionRun);
      return;
    }

    float currentAngularVelocity = packet.getAngularVelocity();
    if (Math.abs(currentAngularVelocity) > 0.001f) {
      packet.setAngle(packet.getAngle() + currentAngularVelocity);
      packet.setAngularVelocity(currentAngularVelocity * 0.98f);
    } else {
      packet.setAngularVelocity(0);
    }

    if (packet.isAccelerating() && !isSpeedLimiterActive) {
      double newSpeed = Math.min(packet.getTargetSpeedMagnitude(), packet.getCurrentSpeedMagnitude() + Packet.TRIANGLE_ACCELERATION_RATE);
      packet.setCurrentSpeedMagnitude(newSpeed);
      if (newSpeed >= packet.getTargetSpeedMagnitude()) packet.setAccelerating(false);
    } else if (packet.isDecelerating()) {
      double newSpeed = Math.max(packet.getTargetSpeedMagnitude(), packet.getCurrentSpeedMagnitude() - Packet.MESSENGER_DECELERATION_RATE);
      packet.setCurrentSpeedMagnitude(newSpeed);
      if (newSpeed <= packet.getTargetSpeedMagnitude()) packet.setDecelerating(false);
    } else if (packet.getPacketType() == NetworkEnums.PacketType.WOBBLE) {
      if (packet.getNoise() < packet.getSize() / 2.0) packet.addNoise(Packet.WOBBLE_SELF_NOISE_RATE);
    }

    double wireLength = currentWire.getLength();
    double progress = packet.getProgressOnWire();
    if (wireLength > 1e-6) {
      progress += (packet.isReversing() ? -1 : 1) * packet.getCurrentSpeedMagnitude() / wireLength;
    } else {
      progress = packet.isReversing() ? 0.0 : 1.0;
    }
    packet.setProgressOnWire(Math.max(0.0, Math.min(1.0, progress)));

    updatePacketPositionAndVelocity(packet);
    packet.updateHitbox();

    if (!packet.isMarkedForRemoval() && packet.getNoise() >= packet.getSize()) {
      packetLostInternal(packet, isPredictionRun);
      return;
    }

    if (!packet.isReversing() && packet.getProgressOnWire() >= 1.0 - 1e-9) {
      handlePacketArrival(packet, currentWire.getEndPort(), isPredictionRun);
    } else if (packet.isReversing() && packet.getProgressOnWire() <= 1e-9) {
      packet.setReversing(false);
      handlePacketArrival(packet, currentWire.getStartPort(), isPredictionRun);
    }
  }

  private void updatePacketPositionAndVelocity(Packet packet) {
    Wire currentWire = packet.getCurrentWire();
    if (currentWire == null) return;
    Wire.PathInfo pathInfo = currentWire.getPathInfoAtProgress(packet.getProgressOnWire());
    if (pathInfo != null) {
      packet.setIdealPosition(pathInfo.position);
      double dx = pathInfo.direction.x * packet.getCurrentSpeedMagnitude();
      double dy = pathInfo.direction.y * packet.getCurrentSpeedMagnitude();
      packet.setVelocity(new Point2D.Double(packet.isReversing() ? -dx : dx, packet.isReversing() ? -dy : dy));
    }
  }

  private void handlePacketArrival(Packet packet, Port destinationPort, boolean isPredictionRun) {
    if (destinationPort == null || destinationPort.getParentSystem() == null) {
      packetLostInternal(packet, isPredictionRun);
      return;
    }
    packet.setIdealPosition(destinationPort.getPrecisePosition());
    System targetSystem = destinationPort.getParentSystem();

    if (packet.isVolumetric() && !isPredictionRun) {
      logBulkPacketWireUsage(packet.getCurrentWire());
    }

    boolean enteredCompatibly = packet.getPacketType() == NetworkEnums.PacketType.MESSENGER ||
            packet.getPacketType() == NetworkEnums.PacketType.PROTECTED ||
            packet.getPacketType() == NetworkEnums.PacketType.SECRET ||
            (Port.getShapeEnum(packet.getShape()) == destinationPort.getShape());

    double maxSafeSpeed = getGameState().getMaxSafeEntrySpeed();
    if (!targetSystem.isReferenceSystem() && packet.getCurrentSpeedMagnitude() > maxSafeSpeed) {
      targetSystem.setDisabled(true);
      targetSystem.setDisabledUntil(getSimulationTimeElapsedMs() + SYSTEM_DISABLE_DURATION_MS);

      if (packet.isVolumetric() || (packet.getPacketType() == NetworkEnums.PacketType.MESSENGER && packet.getBulkParentId() != -1)) {
        packetLostInternal(packet, isPredictionRun);
      } else {
        boolean isReversible = List.of(NetworkEnums.PacketType.MESSENGER, NetworkEnums.PacketType.NORMAL, NetworkEnums.PacketType.SECRET).contains(packet.getPacketType());
        if (isReversible) {
          reversePacketDirection(packet);
        } else {
          packetLostInternal(packet, isPredictionRun);
        }
      }
      return;
    }

    packet.setCurrentSystem(targetSystem);
    if(!targetSystem.isReferenceSystem()) {
      addRoutingCoinsInternal(packet, isPredictionRun);
    }

    SystemBehavior behavior = getBehaviorForSystem(targetSystem);
    behavior.receivePacket(targetSystem, packet, this, isPredictionRun, enteredCompatibly);
  }

  public void reversePacketDirection(Packet packet) {
    if (packet.isReversing()) return;
    packet.setReversing(true);
    packet.setAccelerating(false);
    packet.setDecelerating(false);
    logger.debug("Packet {} is reversing direction.", packet.getId());
  }

  public void setPacketOnWire(Packet packet, Wire wire, boolean compatiblePortExit) {
    packet.setWire(wire, compatiblePortExit);
    double initialSpeed;

    switch(packet.getPacketType()) {
      case BULK: case WOBBLE:
        double threshold = wire.getRelayPointsCount() > 0 ? Packet.LONG_WIRE_THRESHOLD * 0.75 : Packet.LONG_WIRE_THRESHOLD;
        boolean isLongWire = wire.getLength() >= threshold;
        packet.setAccelerating(isLongWire);
        initialSpeed = Packet.BASE_SPEED_MAGNITUDE;
        packet.setTargetSpeedMagnitude(isLongWire ? Packet.MAX_SPEED_MAGNITUDE : Packet.BASE_SPEED_MAGNITUDE);
        break;
      case SECRET:
        initialSpeed = Packet.BASE_SPEED_MAGNITUDE;
        packet.setTargetSpeedMagnitude(initialSpeed);
        break;
      case MESSENGER:
        initialSpeed = Packet.BASE_SPEED_MAGNITUDE;
        if (packet.enteredViaIncompatiblePort()) {
          initialSpeed *= Packet.MESSENGER_INCOMPATIBLE_SPEED_BOOST;
        }
        packet.setAccelerating(true);
        packet.setTargetSpeedMagnitude(Packet.MAX_SPEED_MAGNITUDE);
        break;
      default: // NORMAL, PROTECTED, TROJAN
        if (packet.getShape() == NetworkEnums.PacketShape.SQUARE) {
          initialSpeed = compatiblePortExit ? Packet.BASE_SPEED_MAGNITUDE * Packet.SQUARE_COMPATIBLE_SPEED_FACTOR : Packet.BASE_SPEED_MAGNITUDE;
          packet.setTargetSpeedMagnitude(initialSpeed);
        } else if (packet.getShape() == NetworkEnums.PacketShape.TRIANGLE) {
          initialSpeed = Packet.BASE_SPEED_MAGNITUDE;
          packet.setAccelerating(!compatiblePortExit);
          packet.setTargetSpeedMagnitude(packet.isAccelerating() ? Packet.MAX_SPEED_MAGNITUDE : initialSpeed);
        } else { // CIRCLE
          initialSpeed = Packet.BASE_SPEED_MAGNITUDE;
          packet.setTargetSpeedMagnitude(initialSpeed);
        }
        break;
    }
    packet.setCurrentSpeedMagnitude(initialSpeed);
    packet.setEnteredViaIncompatiblePort(false);
    updatePacketPositionAndVelocity(packet);
    packet.updateHitbox();
  }

  public void teleportPacketToWire(Packet packet, Wire wire) {
    packet.setWire(wire, false);
    packet.setProgressOnWire(0.0);
    packet.setReversing(false);
    packet.setAccelerating(false);
    packet.setDecelerating(false);
    packet.setEnteredViaIncompatiblePort(false);
    packet.setCurrentSpeedMagnitude(Packet.BASE_SPEED_MAGNITUDE);
    packet.setTargetSpeedMagnitude(Packet.BASE_SPEED_MAGNITUDE);
    updatePacketPositionAndVelocity(packet);
  }

  private void updateAntiTrojan(System system, boolean isPredictionRun) {
    long currentTime = getSimulationTimeElapsedMs();
    if (currentTime < system.getAntiTrojanCooldownUntil()) return;

    Packet targetTrojan = null;
    for (Packet p : getPacketsForRendering()) {
      if (p != null && p.getPacketType() == NetworkEnums.PacketType.TROJAN) {
        Point2D.Double pVisPos = p.getVisualPosition();
        if (pVisPos != null && system.getPosition().distanceSq(pVisPos) < System.ANTITROJAN_SCAN_RADIUS * System.ANTITROJAN_SCAN_RADIUS) {
          targetTrojan = p;
          break;
        }
      }
    }
    if (targetTrojan != null) {
      targetTrojan.setPacketType(NetworkEnums.PacketType.MESSENGER);
      targetTrojan.setSize(1);
      system.setAntiTrojanCooldownUntil(currentTime + 8000L);
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

    if (p1.getPacketType() == NetworkEnums.PacketType.MESSENGER && p1.getShape() == NetworkEnums.PacketShape.CIRCLE) reversePacketDirection(p1);
    if (p2.getPacketType() == NetworkEnums.PacketType.MESSENGER && p2.getShape() == NetworkEnums.PacketShape.CIRCLE) reversePacketDirection(p2);

    if (!isAtarActive) {
      handleImpactWave(new Point((int)((p1VisPos.x + p2VisPos.x)/2), (int)((p1VisPos.y + p2VisPos.y)/2)), p1, p2);
    }
  }

  public void removePacketFromWorld(Packet packet) {
    if (packet != null && !packetsToRemove.contains(packet)) {
      packetsToRemove.add(packet);
    }
  }

  public void reintroducePacketToWorld(Packet packet) {
    if (packet != null && !packetsToAdd.contains(packet)) {
      packetsToAdd.add(packet);
    }
  }

  public boolean isWireBlockedByDistributor(Wire wire) {
    if (wire == null) return false;
    for (System s : systems) {
      if (s.getSystemType() == NetworkEnums.SystemType.DISTRIBUTOR) {
        if (s.getEntryWireIdInUse() == wire.getId()) {
          return true;
        }
      }
    }
    return false;
  }

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
    return remainingParts != null && remainingParts.isEmpty();
  }

  public void packetLostInternal(Packet packet, boolean isPredictionRun) {
    if (packet != null && !packet.isMarkedForRemoval()) {
      if (!isPredictionRun) logger.warn("Lost: {}", packet);
      packet.markForRemoval();
      packet.setFinalStatusForPrediction(PredictedPacketStatus.LOST);
      packetsToRemove.add(packet);
      if (packet.getBulkParentId() != -1) {
        resolveBulkPart(packet.getBulkParentId(), packet.getId());
      }
      if (!isPredictionRun) {
        gameState.increasePacketLoss(packet);
      }
    }
  }

  public void addPacketInternal(Packet packet, boolean isPredictionRun) {
    if (packet != null) {
      if (!isPredictionRun) {
        logger.debug("Generated: {}", packet);
        gameState.recordPacketGeneration(packet);
      }
      packetsToAdd.add(packet);
    }
  }

  public void packetSuccessfullyDeliveredInternal(Packet packet, boolean isPredictionRun) {
    if (packet != null && !packet.isMarkedForRemoval()) {
      if (!isPredictionRun) logger.info("Delivered: {}", packet);
      packet.markForRemoval();
      packet.setFinalStatusForPrediction(PredictedPacketStatus.DELIVERED);
      packetsToRemove.add(packet);
      if (!isPredictionRun) {
        totalPacketsSuccessfullyDelivered++;
      }
    }
  }

  public boolean initializeLevel(int level) {
    logger.info("GameEngine: Initializing level {}.", level);
    stopAndCleanupLevel();
    LevelLoader.LevelLayout layout = LevelLoader.loadLevel(level, gameState);
    if (layout == null) {
      logger.error("GameEngine: Failed to load level layout for level {}.", level);
      return false;
    }
    gameState.resetForNewLevel();
    this.currentLevel = layout.levelNumber;
    Packet.resetGlobalId();
    System.resetGlobalId();
    Port.resetGlobalId();
    System.resetGlobalRandomSeed(12345L);
    systems.addAll(layout.systems);
    wires.clear();
    bulkPartTracker.clear();
    rebuildTransientReferences();
    return true;
  }

  public void startSimulation() {
    logger.info("GameEngine: Starting simulation for level {}.", currentLevel);
    simulationRunning = true;
    simulationPaused = false;
    gameState.resetForSimulationAttemptOnly();
    Packet.resetGlobalId();
    System.resetGlobalRandomSeed(12345L);
    for (System s : systems) {
      s.resetForNewRun();
    }
    clearSimulationElements();
  }

  public void stopSimulation() {
    logger.info("GameEngine: Stopping simulation timers and flags.");
    simulationRunning = false;
    simulationPaused = false;
  }

  public void stopAndCleanupLevel() {
    logger.info("GameEngine: Stopping simulation and cleaning up level state.");
    stopSimulation();
    systems.clear();
    wires.clear();
    clearSimulationElements();
  }

  private void clearSimulationElements() {
    packets.clear();
    packetsToAdd.clear();
    packetsToRemove.clear();
    wiresToRemove.clear();
    wiresUsedByBulkPacketsThisTick.clear();
    activelyCollidingPairs.clear();
    simulationTimeElapsedMs = 0;
    totalPacketsSuccessfullyDelivered = 0;
    bulkPartTracker.clear();
  }

  public void setPaused(boolean pause) {
    if (!simulationRunning) return;
    if (pause && !simulationPaused) {
      logger.info("GameEngine: Simulation paused.");
      simulationPaused = true;
    } else if (!pause && simulationPaused) {
      logger.info("GameEngine: Simulation resumed.");
      simulationPaused = false;
    }
  }

  public void setSimulationTimeElapsedMs(long time) {
    this.simulationTimeElapsedMs = time;
  }

  public void setSimulationRunning(boolean running) {
    this.simulationRunning = running;
  }

  private void processBuffersInternal() {
    if (!packetsToRemove.isEmpty()) {
      packets.removeAll(packetsToRemove);
      packetsToRemove.clear();
    }
    if (!packetsToAdd.isEmpty()) {
      packets.addAll(packetsToAdd);
      packetsToAdd.clear();
    }
    if (!wiresToRemove.isEmpty()) {
      for (Wire w : wiresToRemove) {
        if(this.wires.remove(w)){
          w.destroy();
          logger.warn("Wire {} destroyed.", w.getId());
        }
      }
      wiresToRemove.clear();
    }
  }

  private void processWireDestruction(boolean isPredictionRun) {
    if(isPredictionRun) return;
    if (!wiresUsedByBulkPacketsThisTick.isEmpty()) {
      for (Wire w : wiresUsedByBulkPacketsThisTick) {
        w.recordBulkPacketTraversal();
        if (w.isDestroyed() && !wiresToRemove.contains(w)) wiresToRemove.add(w);
      }
      wiresUsedByBulkPacketsThisTick.clear();
    }
  }

  public void addRoutingCoinsInternal(Packet packet, boolean isPredictionRun) {
    if (packet != null && !isPredictionRun) {
      gameState.addCoins(packet.getBaseCoinValue());
    }
  }

  private void detectAndHandleCollisions(boolean isPredictionRun, boolean isAtarActive) {
    if (packets.isEmpty()) return;
    Map<Point, List<Packet>> spatialGrid = new HashMap<>();
    for (Packet p : packets) {
      if (p.getCurrentSystem() == null) {
        Point2D.Double pos = p.getVisualPosition();
        if(pos == null) continue;
        int cellX = (int) (pos.x / SPATIAL_GRID_CELL_SIZE);
        int cellY = (int) (pos.y / SPATIAL_GRID_CELL_SIZE);
        spatialGrid.computeIfAbsent(new Point(cellX, cellY), k -> new ArrayList<>()).add(p);
      }
    }
    Set<Pair<Integer, Integer>> currentTickCollisions = new HashSet<>();
    Set<Pair<Integer, Integer>> checkedPairsThisTick = new HashSet<>();
    for (List<Packet> cellPackets : spatialGrid.values()) {
      for (int i = 0; i < cellPackets.size(); i++) {
        for (int j = i + 1; j < cellPackets.size(); j++) {
          Packet p1 = cellPackets.get(i);
          Packet p2 = cellPackets.get(j);
          Pair<Integer, Integer> currentPair = new Pair<>(Math.min(p1.getId(), p2.getId()), Math.max(p1.getId(), p2.getId()));
          if (checkedPairsThisTick.contains(currentPair)) continue;
          if (p1.collidesWith(p2)) {
            currentTickCollisions.add(currentPair);
            if (!activelyCollidingPairs.contains(currentPair)) {
              handleCollision(p1, p2, isPredictionRun, isAtarActive);
            }
          }
          checkedPairsThisTick.add(currentPair);
        }
      }
    }
    activelyCollidingPairs.removeIf(pair -> !currentTickCollisions.contains(pair));
    activelyCollidingPairs.addAll(currentTickCollisions);
  }

  private void handleImpactWave(Point center, Packet ignore1, Packet ignore2) {
    double waveRadiusSq = IMPACT_WAVE_RADIUS * IMPACT_WAVE_RADIUS;
    for (Packet p : packets) {
      if (p == ignore1 || p == ignore2 || p.getCurrentSystem() != null) continue;
      Point2D.Double pVisPos = p.getVisualPosition();
      if(pVisPos == null) continue;
      double distSq = center.distanceSq(pVisPos);
      if (distSq < waveRadiusSq && distSq > 1e-6) {
        double distance = Math.sqrt(distSq);
        double normalizedDistance = distance / IMPACT_WAVE_RADIUS;
        double noiseAmount = IMPACT_WAVE_MAX_NOISE * (1.0 - normalizedDistance);
        Point2D.Double forceDirection = new Point2D.Double(pVisPos.x - center.x, pVisPos.y - center.y);
        p.setVisualOffsetDirectionFromForce(forceDirection);
        p.addNoise(noiseAmount);
      }
    }
  }

  public void rebuildTransientReferences() {
    Map<Integer, System> systemMap = systems.stream().collect(Collectors.toMap(System::getId, s -> s));
    Map<Integer, Wire> wireMap = this.wires.stream().collect(Collectors.toMap(Wire::getId, w -> w));
    for (Wire w : this.wires) w.rebuildTransientReferences(systemMap);
    for (Packet p : packets) p.rebuildTransientReferences(systemMap, wireMap);
  }

  @SuppressWarnings("unchecked")
  public <T extends Serializable> T deepCopy(T original) {
    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ObjectOutputStream oos = new ObjectOutputStream(baos);
      oos.writeObject(original);
      oos.close();
      ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
      ObjectInputStream ois = new ObjectInputStream(bais);
      return (T) ois.readObject();
    } catch (Exception e) {
      logger.error("Deep copy failed for object of type {}", original != null ? original.getClass().getName() : "null", e);
      return null;
    }
  }

  public List<System> getSystemsDeepCopy() { return deepCopy(new ArrayList<>(systems)); }
  public List<Wire> getWiresDeepCopy() { return deepCopy(new ArrayList<>(wires)); }
  public List<Packet> getPacketsDeepCopy() { return deepCopy(new ArrayList<>(packets)); }
  public GameState getGameStateDeepCopy() { return deepCopy(gameState); }
  public GameState getGameState() { return gameState; }
  public void setGameState(GameState gameState) { this.gameState = gameState; }
  public List<System> getSystems() { return systems; }
  public List<Wire> getWires() { return wires; }
  public List<Packet> getPackets() { return packets; }
  public void setSystems(List<System> systems) { this.systems = systems; }
  public void setWires(List<Wire> wires) { this.wires = wires; }
  public void setPackets(List<Packet> packets) { this.packets = packets; }
  public boolean isSimulationRunning() { return simulationRunning; }
  public boolean isSimulationPaused() { return simulationPaused; }
  public long getSimulationTimeElapsedMs() { return simulationTimeElapsedMs; }
  public int getTotalPacketsSuccessfullyDelivered() { return totalPacketsSuccessfullyDelivered; }
  public List<Packet> getPacketsForRendering() { return packets.stream().filter(p -> p != null && p.getCurrentSystem() == null && !p.isGhost()).collect(Collectors.toList()); }
  public List<Packet> getAllActivePackets() { List<Packet> all = new ArrayList<>(packets); for(System s : systems) { all.addAll(s.packetQueue); } return all; }
  public void logBulkPacketWireUsage(Wire wire) { if (wire != null && !wiresUsedByBulkPacketsThisTick.contains(wire)) wiresUsedByBulkPacketsThisTick.add(wire); }
  public Wire findWireFromPort(Port outputPort) { if (outputPort == null || outputPort.getType() != NetworkEnums.PortType.OUTPUT) return null; for (Wire w : wires) { if (w.getStartPort().equals(outputPort)) return w; } return null; }
  public boolean isWireOccupied(Wire wire, boolean isPredictionRun) { if (wire == null) return false; for (Packet p : packets) { if (p.getCurrentWire() != null && p.getCurrentWire().equals(wire)) { return true; } } return false; }
}