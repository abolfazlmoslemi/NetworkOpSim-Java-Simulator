// FILE: /NetworkOpSim-Multiplayer/NetworkOpSim-Core/src/main/java/com/networkopsim/core/game/model/core/System.java
// ================================================================================

package com.networkopsim.core.game.model.core;

import com.networkopsim.core.game.logic.GameEngine;
import com.networkopsim.core.game.logic.SystemBehavior;
import com.networkopsim.core.game.logic.behaviors.*;
import com.networkopsim.shared.model.NetworkEnums;

import java.awt.Point;
import java.awt.geom.Point2D;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class System implements Serializable {
    private static final long serialVersionUID = 5L; // <<<--- VERSION BUMPED ---<<<
    public static final int SYSTEM_WIDTH = 80;
    public static final int SYSTEM_HEIGHT = 60;
    public static final int QUEUE_CAPACITY = 5;
    private static int nextId = 0;
    private static Random globalRandom = new Random();
    private final int id;
    private int x, y;
    private final boolean isReferenceSystem;
    private boolean indicatorOn = false;
    private final List<Port> inputPorts = Collections.synchronizedList(new ArrayList<>());
    private final List<Port> outputPorts = Collections.synchronizedList(new ArrayList<>());
    public final Queue<Packet> packetQueue = new LinkedList<>();
    private volatile boolean isDisabled = false;
    private volatile long disabledUntil = 0;
    private int packetsToGenerateConfig = 0;
    private int packetsGeneratedThisRun = 0;
    private int generationFrequencyMillisConfig = 2000;
    private long lastGenerationTimeThisRun = -1;
    private NetworkEnums.PacketType packetTypeToGenerate = NetworkEnums.PacketType.NORMAL;
    private NetworkEnums.PacketShape packetShapeToGenerate = null;
    public static final double ANTITROJAN_SCAN_RADIUS = 150.0;
    private static final long ANTITROJAN_COOLDOWN_MS = 8000;
    private long antiTrojanCooldownUntil = 0;
    private boolean vpnIsActive = true;
    private final Map<Integer, List<Packet>> mergingPackets = new ConcurrentHashMap<>();
    private final Map<Integer, Long> mergeGroupArrivalTimes = new ConcurrentHashMap<>();
    private volatile int currentBulkOperationId = -1;
    private volatile int entryWireIdInUse = -1;
    private transient SystemBehavior behavior;
    private final NetworkEnums.SystemType systemType;

    // Fields for Multiplayer & Controllable Systems
    private int ownerId = 0;
    private volatile long sabotageDisabledUntil = 0;
    private volatile int sabotagedForPlayerId = 0;
    private boolean isControllable = false;
    private volatile long systemCooldownUntil = 0;
    private final Map<Integer, Map<NetworkEnums.PacketType, Integer>> ammoStock = new ConcurrentHashMap<>();
    private final Map<Integer, Map<NetworkEnums.PacketType, Long>> playerPacketCooldowns = new ConcurrentHashMap<>();
    private int alternatingOwnerTurn = 0; // 0 for P1's turn, 1 for P2's turn


    public System(int x, int y, NetworkEnums.SystemType type) {
        this.id = nextId++;
        this.x = x;
        this.y = y;
        this.systemType = type;
        this.behavior = createBehaviorForType(type);
        this.isReferenceSystem = (type == NetworkEnums.SystemType.SOURCE || type == NetworkEnums.SystemType.SINK);
        resetForNewRun();
    }

    public void receivePacket(Packet packet, GameEngine gameEngine, boolean isPredictionRun, boolean enteredCompatibly) {
        if (packet == null || gameEngine == null) return;

        if (this.sabotagedForPlayerId != 0 && this.sabotagedForPlayerId != packet.getOwnerId()) {
        } else if (this.sabotagedForPlayerId == packet.getOwnerId()) {
            gameEngine.packetLostInternal(packet, isPredictionRun);
            return;
        }

        if (this.isDisabled) {
            boolean isReversible = List.of(NetworkEnums.PacketType.MESSENGER, NetworkEnums.PacketType.NORMAL, NetworkEnums.PacketType.SECRET).contains(packet.getPacketType());
            if (isReversible) {
                packet.reverseDirection(gameEngine);
            } else {
                gameEngine.packetLostInternal(packet, isPredictionRun);
            }
            return;
        }

        double maxSafeSpeed = gameEngine.getGameState().getMaxSafeEntrySpeed();
        if (!isReferenceSystem && packet.getCurrentSpeedMagnitude() > maxSafeSpeed) {
            this.isDisabled = true;
            this.disabledUntil = gameEngine.getSimulationTimeElapsedMs() + GameEngine.SYSTEM_DISABLE_DURATION_MS;

            if (packet.isVolumetric() || (packet.getPacketType() == NetworkEnums.PacketType.MESSENGER && packet.getBulkParentId() != -1)) {
                gameEngine.packetLostInternal(packet, isPredictionRun);
            } else {
                boolean isReversible = List.of(NetworkEnums.PacketType.MESSENGER, NetworkEnums.PacketType.NORMAL, NetworkEnums.PacketType.SECRET).contains(packet.getPacketType());
                if (isReversible) {
                    packet.reverseDirection(gameEngine);
                } else {
                    gameEngine.packetLostInternal(packet, isPredictionRun);
                }
            }
            return;
        }

        packet.setCurrentSystem(this);

        if(!this.isReferenceSystem) {
            gameEngine.addRoutingCoinsInternal(packet, isPredictionRun);
        }

        getBehavior().receivePacket(this, packet, gameEngine, isPredictionRun, enteredCompatibly);
    }

    public boolean applySabotage(int saboteurPlayerId, long durationMs, long currentTimeMs) {
        if (sabotageDisabledUntil > currentTimeMs) {
            return false;
        }
        this.sabotagedForPlayerId = (saboteurPlayerId == 1) ? 2 : 1;
        this.sabotageDisabledUntil = currentTimeMs + durationMs;
        return true;
    }

    // --- Methods for Controllable Systems ---

    public void setControllable(boolean controllable) {
        if (isReferenceSystem) {
            this.isControllable = controllable;
        }
    }

    public boolean isControllable() {
        return isControllable;
    }

    public void addAmmo(int forPlayerId, NetworkEnums.PacketType packetType, int count) {
        if (!isControllable || count <= 0) return;
        ammoStock.computeIfAbsent(forPlayerId, k -> new ConcurrentHashMap<>())
                .merge(packetType, count, Integer::sum);
    }

    public void useAmmo(int playerId, NetworkEnums.PacketType packetType) {
        if (!isControllable) return;
        Map<NetworkEnums.PacketType, Integer> playerAmmo = ammoStock.get(playerId);
        if (playerAmmo != null) {
            playerAmmo.computeIfPresent(packetType, (k, v) -> v > 0 ? v - 1 : 0);
        }
    }

    public int getAmmoCount(int playerId, NetworkEnums.PacketType packetType) {
        return ammoStock.getOrDefault(playerId, Collections.emptyMap()).getOrDefault(packetType, 0);
    }

    public Map<NetworkEnums.PacketType, Integer> getAllAmmoForPlayer(int playerId) {
        return ammoStock.getOrDefault(playerId, Collections.emptyMap());
    }

    public Map<Integer, Map<NetworkEnums.PacketType, Integer>> getAllAmmo() {
        return ammoStock;
    }

    public void setSystemCooldown(long durationMs, long currentTimeMs) {
        this.systemCooldownUntil = currentTimeMs + durationMs;
    }

    public long getSystemCooldownRemaining(long currentTimeMs) {
        return Math.max(0, systemCooldownUntil - currentTimeMs);
    }

    public void setPacketCooldown(int playerId, NetworkEnums.PacketType type, long durationMs, long currentTimeMs, double modifier) {
        long finalDuration = (long)(durationMs * modifier);
        playerPacketCooldowns.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>())
                .put(type, currentTimeMs + finalDuration);
    }

    public long getPacketCooldownRemaining(int playerId, NetworkEnums.PacketType type, long currentTimeMs) {
        Map<NetworkEnums.PacketType, Long> cooldowns = playerPacketCooldowns.get(playerId);
        if (cooldowns == null) return 0;
        return Math.max(0, cooldowns.getOrDefault(type, 0L) - currentTimeMs);
    }

    public Map<NetworkEnums.PacketType, Long> getAllPacketCooldownsForPlayer(int playerId, long currentTimeMs) {
        Map<NetworkEnums.PacketType, Long> playerCooldownsMap = new HashMap<>();
        Map<NetworkEnums.PacketType, Long> cooldownEndTimes = this.playerPacketCooldowns.get(playerId);
        if (cooldownEndTimes != null) {
            cooldownEndTimes.forEach((packetType, endTime) -> {
                playerCooldownsMap.put(packetType, Math.max(0, endTime - currentTimeMs));
            });
        }
        return playerCooldownsMap;
    }

    // --- End of Controllable System Methods ---

    public static void resetGlobalRandomSeed(long seed) { globalRandom = new Random(seed); }
    public static Random getGlobalRandom() { return globalRandom; }
    public static void resetGlobalId() { nextId = 0; }
    private static SystemBehavior createBehaviorForType(NetworkEnums.SystemType type) { switch (type) { case SOURCE: return new SourceBehavior(); case SINK: return new SinkBehavior(); case SPY: return new SpyBehavior(); case CORRUPTOR: return new CorruptorBehavior(); case VPN: return new VpnBehavior(); case DISTRIBUTOR: return new DistributorBehavior(); case MERGER: return new MergerBehavior(); case NODE: case ANTITROJAN: default: return new NodeBehavior(type); } }

    public void resetForNewRun() {
        synchronized (packetQueue) { packetQueue.clear(); }
        this.packetsGeneratedThisRun = 0;
        this.lastGenerationTimeThisRun = -1;
        this.antiTrojanCooldownUntil = 0;
        this.vpnIsActive = true;
        this.mergingPackets.clear();
        this.mergeGroupArrivalTimes.clear();
        this.currentBulkOperationId = -1;
        this.isDisabled = false;
        this.disabledUntil = 0;
        this.entryWireIdInUse = -1;
        this.sabotageDisabledUntil = 0;
        this.sabotagedForPlayerId = 0;
        this.systemCooldownUntil = 0;
        this.ammoStock.clear();
        this.playerPacketCooldowns.clear();
        this.alternatingOwnerTurn = 0;
    }

    public List<Port> getAllPorts() {
        List<Port> allPorts = new ArrayList<>();
        synchronized (inputPorts) {
            allPorts.addAll(inputPorts);
        }
        synchronized (outputPorts) {
            allPorts.addAll(outputPorts);
        }
        return allPorts;
    }
    public int getEntryWireIdInUse() { return entryWireIdInUse; }
    public void setEntryWireIdInUse(int wireId) { this.entryWireIdInUse = wireId; }
    public void reinitializeBehavior() { if (this.behavior == null) { this.behavior = createBehaviorForType(this.systemType); } }
    public void processQueue(GameEngine gameEngine, boolean isPredictionRun) { getBehavior().processQueue(this, gameEngine, isPredictionRun); }
    public void attemptPacketGeneration(GameEngine gameEngine, long currentSimTimeMs, boolean isPredictionRun) { getBehavior().attemptPacketGeneration(this, gameEngine, currentSimTimeMs, isPredictionRun); }

    public void updateSystemState(long currentTimeMs, GameEngine gameEngine) {
        if (isDisabled && currentTimeMs >= disabledUntil) { isDisabled = false; disabledUntil = 0; }
        if (sabotagedForPlayerId != 0 && currentTimeMs >= sabotageDisabledUntil) { sabotagedForPlayerId = 0; sabotageDisabledUntil = 0; }
    }

    public void updateAntiTrojan(GameEngine gameEngine, boolean isPredictionRun) {
        if (getSystemType() != NetworkEnums.SystemType.ANTITROJAN) return;
        long currentTime = gameEngine.getSimulationTimeElapsedMs();
        if (currentTime < antiTrojanCooldownUntil) return;
        Packet targetTrojan = null;
        for (Packet p : gameEngine.getPacketsForRendering()) {
            if (p != null && p.getPacketType() == NetworkEnums.PacketType.TROJAN) {
                Point2D.Double pVisPos = p.getVisualPosition();
                if (pVisPos != null && this.getPosition().distanceSq(pVisPos) < ANTITROJAN_SCAN_RADIUS * ANTITROJAN_SCAN_RADIUS) {
                    targetTrojan = p;
                    break;
                }
            }
        }
        if (targetTrojan != null) {
            // --- MODIFIED LOGIC FOR ANTITROJAN ---
            if (targetTrojan.isConvertedTrojan()) {
                targetTrojan.revertOwnerAndType(); // Reverts owner and type
            } else {
                targetTrojan.transformToMessenger(); // Default behavior for non-converted trojans
            }
            // --- END OF MODIFIED LOGIC ---
            this.antiTrojanCooldownUntil = currentTime + ANTITROJAN_COOLDOWN_MS;
        }
    }

    public int getAlternatingOwnerTurn() {
        return alternatingOwnerTurn;
    }

    public void toggleAlternatingOwnerTurn() {
        this.alternatingOwnerTurn = 1 - this.alternatingOwnerTurn; // Toggles between 0 and 1
    }

    public SystemBehavior getBehavior() { if (behavior == null) { reinitializeBehavior(); } return behavior; }
    public NetworkEnums.SystemType getSystemType() { return systemType; }
    public int getId() { return id; }
    public int getX() { return x; }
    public int getY() { return y; }
    public Point getPosition() { return new Point(x + SYSTEM_WIDTH / 2, y + SYSTEM_HEIGHT / 2); }
    public List<Port> getInputPorts() { synchronized(inputPorts) { return Collections.unmodifiableList(new ArrayList<>(inputPorts)); } }
    public List<Port> getOutputPorts() { synchronized(outputPorts) { return Collections.unmodifiableList(new ArrayList<>(outputPorts)); } }
    public boolean isReferenceSystem() { return isReferenceSystem; }
    public boolean hasOutputPorts() { synchronized(outputPorts) { return !outputPorts.isEmpty(); } }
    public boolean hasInputPorts() { synchronized(inputPorts) { return !inputPorts.isEmpty(); } }
    public int getQueueSize() { synchronized(packetQueue) { return packetQueue.size(); } }
    public boolean isDisabled() { return isDisabled; }
    public boolean areAllPortsConnected() { synchronized(inputPorts) { for (Port p : inputPorts) if (p != null && !p.isConnected()) return false; } synchronized(outputPorts) { for (Port p : outputPorts) if (p != null && !p.isConnected()) return false; } return true; }
    public void setIndicator(boolean on) { this.indicatorOn = on; }
    public boolean isIndicatorOn() { return this.indicatorOn; }
    public void setPosition(int x, int y) { this.x = x; this.y = y; updateAllPortPositions(); }
    public void configureGenerator(int totalPackets, int frequencyMs, NetworkEnums.PacketType type) { if (systemType == NetworkEnums.SystemType.SOURCE) { this.packetsToGenerateConfig = totalPackets; this.generationFrequencyMillisConfig = Math.max(100, frequencyMs); this.packetTypeToGenerate = type; } }
    public int getPacketsGeneratedCount() { return packetsGeneratedThisRun; }
    public void incrementPacketsGenerated() { this.packetsGeneratedThisRun++; }
    public int getTotalPacketsToGenerate() { return packetsToGenerateConfig; }
    public long getLastGenerationTime() { return lastGenerationTimeThisRun; }
    public void setLastGenerationTime(long time) { this.lastGenerationTimeThisRun = time; }
    public int getGenerationFrequency() { return generationFrequencyMillisConfig; }
    public NetworkEnums.PacketType getPacketTypeToGenerate() { return packetTypeToGenerate; }
    public void setPacketShapeToGenerate(NetworkEnums.PacketShape shape) { this.packetShapeToGenerate = shape; }
    public NetworkEnums.PacketShape getPacketShapeToGenerate() { return packetShapeToGenerate; }
    public boolean isVpnActive() { return vpnIsActive; }
    public void setVpnActive(boolean active, GameEngine gameEngine) { if (this.systemType != NetworkEnums.SystemType.VPN || this.vpnIsActive == active) return; this.vpnIsActive = active; if (!active) { for (Packet p : gameEngine.getAllActivePackets()) { if (p != null && p.getPacketType() == NetworkEnums.PacketType.PROTECTED && p.getProtectedBySystemId() == this.getId()) { p.revertToOriginalType(); } } } }
    public long getAntiTrojanCooldownUntil() { return antiTrojanCooldownUntil; }
    public Map<Integer, List<Packet>> getMergingPackets() { return mergingPackets; }
    public Map<Integer, Long> getMergeGroupArrivalTimes() { return mergeGroupArrivalTimes; }
    public int getCurrentBulkOperationId() { return currentBulkOperationId; }
    public void setCurrentBulkOperationId(int id) { this.currentBulkOperationId = id; }
    public int getOwnerId() { return ownerId; }
    public void setOwnerId(int ownerId) { if(isReferenceSystem) this.ownerId = ownerId; }
    public int getSabotagedForPlayerId() { return sabotagedForPlayerId; }
    public void addPort(NetworkEnums.PortType type, NetworkEnums.PortShape shape) { if (shape == NetworkEnums.PortShape.ANY) throw new IllegalArgumentException("PortShape.ANY is not allowed."); int index; if (type == NetworkEnums.PortType.INPUT) { synchronized(inputPorts) { index = inputPorts.size(); inputPorts.add(new Port(this, type, shape, index)); } } else { synchronized(outputPorts) { index = outputPorts.size(); outputPorts.add(new Port(this, type, shape, index)); } } updateAllPortPositions(); }
    public void updateAllPortPositions() { int totalInput, totalOutput; synchronized (inputPorts) { totalInput = inputPorts.size(); } synchronized (outputPorts) { totalOutput = outputPorts.size(); } synchronized (inputPorts) { for (Port p : inputPorts) { if (p != null) p.updatePosition(this.x, this.y, totalInput, totalOutput); } } synchronized (outputPorts) { for (Port p : outputPorts) { if (p != null) p.updatePosition(this.x, this.y, totalInput, totalOutput); } } }
    @Override public boolean equals(Object o) { if (this == o) return true; if (o == null || getClass() != o.getClass()) return false; System system = (System) o; return id == system.id; }
    @Override public int hashCode() { return Objects.hash(id); }
    @Override public String toString() { return "System{id=" + id + ", type=" + systemType.name() + ", owner=" + ownerId + ", pos=(" + x + "," + y + ")" + ", Q=" + getQueueSize() + "/" + QUEUE_CAPACITY + (isDisabled ? ", DISABLED" : "") + (sabotagedForPlayerId > 0 ? ", SABOTAGED" : "") + '}'; }
}