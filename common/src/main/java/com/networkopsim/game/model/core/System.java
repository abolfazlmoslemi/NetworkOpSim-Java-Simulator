// ===== File: System.java (FINAL REVISED for 'common' module) =====
// ===== MODULE: common =====

package com.networkopsim.game.model.core;

import com.networkopsim.game.model.enums.NetworkEnums;
import java.awt.*;
import java.io.Serializable;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

public class System implements Serializable {
    private static final long serialVersionUID = 3L; // Version updated due to behavior removal
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
    private final NetworkEnums.SystemType systemType;

    public System(int x, int y, NetworkEnums.SystemType type) {
        this.id = nextId++;
        this.x = x;
        this.y = y;
        this.systemType = type;
        this.isReferenceSystem = (type == NetworkEnums.SystemType.SOURCE || type == NetworkEnums.SystemType.SINK);
        resetForNewRun();
    }

    public static void resetGlobalRandomSeed(long seed) { globalRandom = new Random(seed); }
    public static Random getGlobalRandom() { return globalRandom; }
    public static void resetGlobalId() { nextId = 0; }

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
    }

    // --- GETTERS & SETTERS (for server-side modification) ---
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
    public void setDisabled(boolean disabled) { this.isDisabled = disabled; }
    public long getDisabledUntil() { return disabledUntil; }
    public void setDisabledUntil(long time) { this.disabledUntil = time; }
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
    public void setVpnActive(boolean active) { this.vpnIsActive = active; }
    public long getAntiTrojanCooldownUntil() { return antiTrojanCooldownUntil; }
    public void setAntiTrojanCooldownUntil(long time) { this.antiTrojanCooldownUntil = time; }
    public Map<Integer, List<Packet>> getMergingPackets() { return mergingPackets; }
    public Map<Integer, Long> getMergeGroupArrivalTimes() { return mergeGroupArrivalTimes; }
    public int getCurrentBulkOperationId() { return currentBulkOperationId; }
    public void setCurrentBulkOperationId(int id) { this.currentBulkOperationId = id; }
    public int getEntryWireIdInUse() { return entryWireIdInUse; }
    public void setEntryWireIdInUse(int wireId) { this.entryWireIdInUse = wireId; }

    public void addPort(NetworkEnums.PortType type, NetworkEnums.PortShape shape) {
        if (shape == NetworkEnums.PortShape.ANY) throw new IllegalArgumentException("PortShape.ANY is not allowed.");
        int index;
        if (type == NetworkEnums.PortType.INPUT) {
            synchronized(inputPorts) {
                index = inputPorts.size();
                inputPorts.add(new Port(this, type, shape, index));
            }
        } else {
            synchronized(outputPorts) {
                index = outputPorts.size();
                outputPorts.add(new Port(this, type, shape, index));
            }
        }
        updateAllPortPositions();
    }

    public void updateAllPortPositions() {
        int totalInput, totalOutput;
        synchronized (inputPorts) { totalInput = inputPorts.size(); }
        synchronized (outputPorts) { totalOutput = outputPorts.size(); }
        synchronized (inputPorts) { for (Port p : inputPorts) { if (p != null) p.updatePosition(this.x, this.y, totalInput, totalOutput); } }
        synchronized (outputPorts) { for (Port p : outputPorts) { if (p != null) p.updatePosition(this.x, this.y, totalInput, totalOutput); } }
    }

    public Port getPortAt(Point p) {
        synchronized (outputPorts) { for (Port port : outputPorts) if (port != null && port.contains(p)) return port; }
        synchronized (inputPorts) { for (Port port : inputPorts) if (port != null && port.contains(p)) return port; }
        return null;
    }

    @Override public boolean equals(Object o) { if (this == o) return true; if (o == null || getClass() != o.getClass()) return false; System system = (System) o; return id == system.id; }
    @Override public int hashCode() { return Objects.hash(id); }
    @Override public String toString() { return "System{id=" + id + ", type=" + systemType.name() + ", pos=(" + x + "," + y + ")" + ", Q=" + getQueueSize() + "/" + QUEUE_CAPACITY + (isDisabled ? ", DISABLED" : "") + '}'; }
}