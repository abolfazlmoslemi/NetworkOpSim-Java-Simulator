// ===== File: System.java (Final Corrected for Destination Port Effect) =====

package com.networkopsim.game.model.core;

import com.networkopsim.game.controller.logic.GameEngine;
import com.networkopsim.game.controller.logic.SystemBehavior;
import com.networkopsim.game.controller.logic.behaviors.*;
import com.networkopsim.game.model.enums.NetworkEnums;
import java.awt.*;
import java.awt.geom.Point2D;
import java.io.Serializable;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Map;
import java.util.HashMap;
import java.util.Queue;
import java.util.Objects;
import java.util.Random;

public class System implements Serializable {
    private static final long serialVersionUID = 2L;
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
    private final Map<Integer, List<Packet>> mergingPackets = new HashMap<>();

    private transient SystemBehavior behavior;
    private final NetworkEnums.SystemType systemType;

    public static void resetGlobalRandomSeed(long seed) { globalRandom = new Random(seed); }
    public static Random getGlobalRandom() { return globalRandom; }
    public static void resetGlobalId() { nextId = 0; }

    public System(int x, int y, NetworkEnums.SystemType type) {
        this.id = nextId++;
        this.x = x;
        this.y = y;
        this.systemType = type;
        this.behavior = createBehaviorForType(type);
        this.isReferenceSystem = (type == NetworkEnums.SystemType.SOURCE || type == NetworkEnums.SystemType.SINK);
        resetForNewRun();
    }

    private static SystemBehavior createBehaviorForType(NetworkEnums.SystemType type) {
        switch (type) {
            case SOURCE: return new SourceBehavior();
            case SINK: return new SinkBehavior();
            case SPY: return new SpyBehavior();
            case CORRUPTOR: return new CorruptorBehavior();
            case VPN: return new VpnBehavior();
            case DISTRIBUTOR: return new DistributorBehavior();
            case MERGER: return new MergerBehavior();
            case NODE:
            case ANTITROJAN:
            default: return new NodeBehavior(type);
        }
    }

    public void reinitializeBehavior() {
        if (this.behavior == null) {
            this.behavior = createBehaviorForType(this.systemType);
        }
    }

    public void receivePacket(Packet packet, GameEngine gameEngine, boolean isPredictionRun, boolean enteredCompatibly) {
        if (packet == null || gameEngine == null) return;

        boolean isReversible = List.of(
                NetworkEnums.PacketType.MESSENGER,
                NetworkEnums.PacketType.NORMAL,
                NetworkEnums.PacketType.SECRET,
                NetworkEnums.PacketType.BULK
        ).contains(packet.getPacketType());

        if (this.isDisabled) {
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
            if (!isPredictionRun && !gameEngine.getGame().isMuted()) {
                gameEngine.getGame().playSoundEffect("system_shutdown");
            }
            if (isReversible) {
                packet.reverseDirection(gameEngine);
            } else {
                gameEngine.packetLostInternal(packet, isPredictionRun);
            }
            return;
        }

        // [MODIFIED] Set the flag for incompatible entry BEFORE processing, so it can be used for exit speed logic.
        if (packet.getPacketType() == NetworkEnums.PacketType.MESSENGER && getSystemType() == NetworkEnums.SystemType.NODE) {
            packet.setEnteredViaIncompatiblePort(!enteredCompatibly);
        }

        if (packet.getPacketType() == NetworkEnums.PacketType.BULK){
            synchronized(packetQueue) { for(Packet p : packetQueue) { gameEngine.packetLostInternal(p, isPredictionRun); } packetQueue.clear(); }
            if(packet.getCurrentWire() != null) { Port entryPort = packet.getCurrentWire().getEndPort(); entryPort.randomizeShape(); }
        }
        packet.setCurrentSystem(this);
        gameEngine.addRoutingCoinsInternal(packet, isPredictionRun);
        getBehavior().receivePacket(this, packet, gameEngine, isPredictionRun, enteredCompatibly);
    }

    public void processQueue(GameEngine gameEngine, boolean isPredictionRun) { getBehavior().processQueue(this, gameEngine, isPredictionRun); }
    public void attemptPacketGeneration(GameEngine gameEngine, long currentSimTimeMs, boolean isPredictionRun) { getBehavior().attemptPacketGeneration(this, gameEngine, currentSimTimeMs, isPredictionRun); }

    public void updateSystemState(long currentTimeMs, GameEngine gameEngine) {
        if (isDisabled && currentTimeMs >= disabledUntil) {
            isDisabled = false;
            disabledUntil = 0;
            if (!isReferenceSystem && !gameEngine.getGame().isMuted()) {
                gameEngine.getGame().playSoundEffect("system_reboot");
            }
        }
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
                    targetTrojan = p; break;
                }
            }
        }
        if (targetTrojan != null) {
            targetTrojan.setPacketType(NetworkEnums.PacketType.MESSENGER);
            this.antiTrojanCooldownUntil = currentTime + ANTITROJAN_COOLDOWN_MS;
            if (!isPredictionRun && !gameEngine.getGame().isMuted()) { gameEngine.getGame().playSoundEffect("ui_confirm"); }
        }
    }

    // --- Getters and Setters ---
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
    public boolean areAllPortsConnected() {
        synchronized(inputPorts) { for (Port p : inputPorts) if (p != null && !p.isConnected()) return false; }
        synchronized(outputPorts) { for (Port p : outputPorts) if (p != null && !p.isConnected()) return false; }
        return true;
    }
    public void setIndicator(boolean on) {
        this.indicatorOn = on;
    }
    public boolean isIndicatorOn() {
        return this.indicatorOn;
    }
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

    public void setVpnActive(boolean active, GameEngine gameEngine) {
        if (this.systemType != NetworkEnums.SystemType.VPN || this.vpnIsActive == active) return;
        this.vpnIsActive = active;
        if (!active) {
            for (Packet p : gameEngine.getAllActivePackets()) {
                if (p != null && p.getPacketType() == NetworkEnums.PacketType.PROTECTED && p.getProtectedBySystemId() == this.getId()) {
                    p.revertToOriginalType();
                }
            }
        }
    }

    public long getAntiTrojanCooldownUntil() { return antiTrojanCooldownUntil; }
    public Map<Integer, List<Packet>> getMergingPackets() { return mergingPackets; }
    public void resetForNewRun() { synchronized (packetQueue) { packetQueue.clear(); } this.packetsGeneratedThisRun = 0; this.lastGenerationTimeThisRun = -1; this.antiTrojanCooldownUntil = 0; this.vpnIsActive = true; this.mergingPackets.clear(); this.isDisabled = false; this.disabledUntil = 0; }
    public void addPort(NetworkEnums.PortType type, NetworkEnums.PortShape shape) { if (shape == NetworkEnums.PortShape.ANY) throw new IllegalArgumentException("PortShape.ANY is not allowed."); int index; if (type == NetworkEnums.PortType.INPUT) { synchronized(inputPorts) { index = inputPorts.size(); inputPorts.add(new Port(this, type, shape, index)); } } else { synchronized(outputPorts) { index = outputPorts.size(); outputPorts.add(new Port(this, type, shape, index)); } } updateAllPortPositions(); }
    public void updateAllPortPositions() { int totalInput, totalOutput; synchronized (inputPorts) { totalInput = inputPorts.size(); } synchronized (outputPorts) { totalOutput = outputPorts.size(); } synchronized (inputPorts) { for (Port p : inputPorts) { if (p != null) p.updatePosition(this.x, this.y, totalInput, totalOutput); } } synchronized (outputPorts) { for (Port p : outputPorts) { if (p != null) p.updatePosition(this.x, this.y, totalInput, totalOutput); } } }
    public Port getPortAt(Point p) { synchronized (outputPorts) { for (Port port : outputPorts) if (port != null && port.contains(p)) return port; } synchronized (inputPorts) { for (Port port : inputPorts) if (port != null && port.contains(p)) return port; } return null; }
    @Override public boolean equals(Object o) { if (this == o) return true; if (o == null || getClass() != o.getClass()) return false; System system = (System) o; return id == system.id; }
    @Override public int hashCode() { return Objects.hash(id); }
    @Override public String toString() { return "System{id=" + id + ", type=" + systemType.name() + ", pos=(" + x + "," + y + ")" + ", Q=" + getQueueSize() + "/" + QUEUE_CAPACITY + (isDisabled ? ", DISABLED" : "") + '}'; }
}