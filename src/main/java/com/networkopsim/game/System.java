// ===== File: System.java =====

package com.networkopsim.game;
import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Objects;
import java.util.Random;
import java.util.stream.Collectors;

public class System {
    // --- Fields ---
    public static final int SYSTEM_WIDTH = 80;
    public static final int SYSTEM_HEIGHT = 60;
    private static final int QUEUE_CAPACITY = 5;
    private static int nextId = 0;
    private static Random globalRandom = new Random();

    private final int id;
    private int x, y;

    private final NetworkEnums.SystemType systemType;
    private final boolean isReferenceSystem;
    private boolean indicatorOn = false;
    private final List<Port> inputPorts = Collections.synchronizedList(new ArrayList<>());
    private final List<Port> outputPorts = Collections.synchronizedList(new ArrayList<>());
    public final Queue<Packet> packetQueue = new LinkedList<>();

    private int packetsToGenerateConfig = 0;
    private int packetsGeneratedThisRun = 0;
    private int generationFrequencyMillisConfig = 2000;
    private long lastGenerationTimeThisRun = -1;
    private NetworkEnums.PacketType packetTypeToGenerate = NetworkEnums.PacketType.NORMAL;

    private static final double TROJAN_CONVERSION_CHANCE = 0.15;
    private static final double CORRUPTOR_NOISE_ADDITION = 1.0;
    public static final double ANTITROJAN_SCAN_RADIUS = 150.0;
    private static final long ANTITROJAN_COOLDOWN_MS = 8000;
    private long antiTrojanCooldownUntil = 0;
    private boolean vpnIsActive = true;

    // --- Static Methods ---
    public static void resetGlobalRandomSeed(long seed) { globalRandom = new Random(seed); }
    public static Random getGlobalRandom() { return globalRandom; }
    public static void resetGlobalId() { nextId = 0; }
    public static NetworkEnums.PacketShape getPacketShapeFromPortShapeStatic(NetworkEnums.PortShape portShape) {
        if (portShape == null) return null;
        switch (portShape) {
            case SQUARE:   return NetworkEnums.PacketShape.SQUARE;
            case TRIANGLE: return NetworkEnums.PacketShape.TRIANGLE;
            case CIRCLE:   return NetworkEnums.PacketShape.CIRCLE;
            case ANY: default: return null;
        }
    }

    // --- Constructors ---
    public System(int x, int y, NetworkEnums.SystemType type) {
        this.id = nextId++; this.x = x; this.y = y; this.systemType = type;
        this.isReferenceSystem = (type == NetworkEnums.SystemType.SOURCE || type == NetworkEnums.SystemType.SINK);
        resetForNewRun();
    }
    public System(int x, int y, boolean isReference) {
        this(x, y, isReference ? NetworkEnums.SystemType.SOURCE : NetworkEnums.SystemType.NODE);
    }

    // --- Getters & Simple Helper Methods (Ordered to prevent compile errors) ---
    public NetworkEnums.SystemType getSystemType() { return systemType; }
    public int getId() { return id; }
    public int getX() { return x; }
    public int getY() { return y; }
    public List<Port> getInputPorts() { synchronized(inputPorts) { return Collections.unmodifiableList(new ArrayList<>(inputPorts)); } }
    public List<Port> getOutputPorts() { synchronized(outputPorts) { return Collections.unmodifiableList(new ArrayList<>(outputPorts)); } }
    public boolean isReferenceSystem() { return isReferenceSystem; }
    public boolean hasOutputPorts() { synchronized(outputPorts) { return !outputPorts.isEmpty(); } }
    public boolean hasInputPorts() { synchronized(inputPorts) { return !inputPorts.isEmpty(); } }
    public Point getPosition() { return new Point(x + SYSTEM_WIDTH / 2, y + SYSTEM_HEIGHT / 2); }
    public int getPacketsGeneratedCount() { return packetsGeneratedThisRun; }
    public int getTotalPacketsToGenerate() { return packetsToGenerateConfig; }
    public int getQueueSize() { synchronized(packetQueue) { return packetQueue.size(); } }
    public long getAntiTrojanCooldownUntil() { return antiTrojanCooldownUntil; }
    private boolean areAllMyPortsConnected() {
        synchronized(inputPorts) { for (Port p : inputPorts) if (p != null && !p.isConnected()) return false; }
        synchronized(outputPorts) { for (Port p : outputPorts) if (p != null && !p.isConnected()) return false; }
        return true;
    }

    // --- Setters & Configurators ---
    public void setPosition(int x, int y) { this.x = x; this.y = y; updateAllPortPositions(); }
    public void setVpnActive(boolean active, GamePanel gamePanel) {
        if (this.systemType != NetworkEnums.SystemType.VPN || this.vpnIsActive == active) return;
        this.vpnIsActive = active;
        if (!active) {
            List<Packet> allPackets = new ArrayList<>(gamePanel.getPacketsForRendering());
            allPackets.addAll(getAllPacketsInQueues(gamePanel.getSystems()));
            for (Packet p : allPackets) { if (p != null && p.getPacketType() == NetworkEnums.PacketType.PROTECTED) p.revertToOriginalType(); }
        }
    }
    public void configureGenerator(int totalPackets, int frequencyMs) {
        if (systemType == NetworkEnums.SystemType.SOURCE && hasOutputPorts()) {
            this.packetsToGenerateConfig = Math.max(0, totalPackets);
            this.generationFrequencyMillisConfig = Math.max(100, frequencyMs);
            this.packetTypeToGenerate = NetworkEnums.PacketType.NORMAL;
        } else { this.packetsToGenerateConfig = 0; }
    }
    public void configureGenerator(int totalPackets, int frequencyMs, NetworkEnums.PacketType type) {
        configureGenerator(totalPackets, frequencyMs);
        if (systemType == NetworkEnums.SystemType.SOURCE) {
            this.packetTypeToGenerate = type;
        }
    }

    // --- Core Logic Methods ---
    public void resetForNewRun() {
        synchronized (packetQueue) { packetQueue.clear(); }
        this.packetsGeneratedThisRun = 0; this.lastGenerationTimeThisRun = -1;
        this.antiTrojanCooldownUntil = 0; this.vpnIsActive = true;
    }

    public void receivePacket(Packet packet, GamePanel gamePanel, boolean isPredictionRun) {
        if (packet == null || gamePanel == null) return;
        if (packet.getPacketType() == NetworkEnums.PacketType.PROTECTED) {
            if (systemType == NetworkEnums.SystemType.SPY || systemType == NetworkEnums.SystemType.CORRUPTOR) {
                packet.revertToOriginalType();
                processOrQueuePacket(packet, gamePanel, isPredictionRun);
                return;
            }
        }
        packet.setCurrentSystem(this);
        gamePanel.addRoutingCoinsInternal(packet, isPredictionRun);
        switch (systemType) {
            case SINK: gamePanel.packetSuccessfullyDeliveredInternal(packet, isPredictionRun); break;
            case SOURCE: gamePanel.packetLostInternal(packet, isPredictionRun); break;
            case SPY: handleSpyLogic(packet, gamePanel, isPredictionRun); break;
            case CORRUPTOR: handleCorruptorLogic(packet, gamePanel, isPredictionRun); break;
            case VPN: handleVpnLogic(packet, gamePanel, isPredictionRun); break;
            case NODE: case ANTITROJAN: default:
                processOrQueuePacket(packet, gamePanel, isPredictionRun);
                break;
        }
    }

    public void updateAntiTrojan(GamePanel gamePanel, boolean isPredictionRun) {
        long currentTime = gamePanel.getSimulationTimeElapsedMs();
        if (getSystemType() != NetworkEnums.SystemType.ANTITROJAN || currentTime < antiTrojanCooldownUntil) return;
        List<Packet> packetsOnWires = gamePanel.getPacketsForRendering(); Packet targetTrojan = null;
        for (Packet p : packetsOnWires) {
            if (p != null && p.getPacketType() == NetworkEnums.PacketType.TROJAN) {
                if (this.getPosition().distanceSq(p.getPosition()) < ANTITROJAN_SCAN_RADIUS * ANTITROJAN_SCAN_RADIUS) {
                    targetTrojan = p; break;
                }
            }
        }
        if (targetTrojan != null) {
            targetTrojan.setPacketType(NetworkEnums.PacketType.MESSENGER); this.antiTrojanCooldownUntil = currentTime + ANTITROJAN_COOLDOWN_MS;
            if (!isPredictionRun && !gamePanel.getGame().isMuted()) gamePanel.getGame().playSoundEffect("ui_confirm");
        }
    }

    public void processQueue(GamePanel gamePanel, boolean isPredictionRun) {
        if (isReferenceSystem()) return;
        Packet packetToProcess;
        synchronized (packetQueue) { if (packetQueue.isEmpty()) return; packetToProcess = packetQueue.peek(); }
        if (packetToProcess == null || packetToProcess.isMarkedForRemoval()) { if(packetToProcess != null) synchronized (packetQueue) { packetQueue.poll(); } return; }
        Port outputPort = findAvailableOutputPort(packetToProcess, gamePanel, isPredictionRun);
        if (outputPort != null) {
            Wire outputWire = gamePanel.findWireFromPort(outputPort);
            if (outputWire != null) {
                Packet sentPacket;
                synchronized (packetQueue) { sentPacket = packetQueue.poll(); }
                if (sentPacket != null) {
                    boolean compatibleExit = (sentPacket.getPacketType() == NetworkEnums.PacketType.SECRET) || (Port.getShapeEnum(sentPacket.getShape()) == outputPort.getShape());
                    sentPacket.setWire(outputWire, compatibleExit);
                    if (sentPacket.getFinalStatusForPrediction() == PredictedPacketStatus.QUEUED) sentPacket.setFinalStatusForPrediction(null);
                }
            } else { if(packetToProcess.getFinalStatusForPrediction() != PredictedPacketStatus.LOST) packetToProcess.setFinalStatusForPrediction(PredictedPacketStatus.QUEUED); }
        } else { if(packetToProcess.getFinalStatusForPrediction() != PredictedPacketStatus.LOST) packetToProcess.setFinalStatusForPrediction(PredictedPacketStatus.QUEUED); }
    }

    public void attemptPacketGeneration(GamePanel gamePanel, long currentSimTimeMs, boolean isPredictionRun) {
        if (systemType != NetworkEnums.SystemType.SOURCE || !hasOutputPorts() || packetsGeneratedThisRun >= packetsToGenerateConfig || packetsToGenerateConfig <= 0) return;
        if (lastGenerationTimeThisRun == -1) lastGenerationTimeThisRun = currentSimTimeMs - generationFrequencyMillisConfig;
        if (currentSimTimeMs - lastGenerationTimeThisRun < generationFrequencyMillisConfig) return;

        List<Port> availablePorts = new ArrayList<>();
        synchronized(outputPorts) {
            List<Port> shuffledPorts = new ArrayList<>(outputPorts); Collections.shuffle(shuffledPorts, globalRandom);
            for (Port port : shuffledPorts) { if (port != null && port.isConnected()) { Wire wire = gamePanel.findWireFromPort(port); if (wire != null && !gamePanel.isWireOccupied(wire, isPredictionRun)) availablePorts.add(port); } }
        }

        if (!availablePorts.isEmpty()) {
            lastGenerationTimeThisRun = currentSimTimeMs; Port chosenPort = availablePorts.get(0); Wire outputWire = gamePanel.findWireFromPort(chosenPort);
            NetworkEnums.PacketShape shapeToGenerate = (packetTypeToGenerate == NetworkEnums.PacketType.SECRET) ? NetworkEnums.PacketShape.CIRCLE : getPacketShapeFromPortShapeStatic(chosenPort.getShape());
            if (shapeToGenerate != null && outputWire != null) {
                Packet newPacket = new Packet(shapeToGenerate, chosenPort.getX(), chosenPort.getY(), packetTypeToGenerate);
                boolean compatibleExit = (packetTypeToGenerate == NetworkEnums.PacketType.SECRET) || (Port.getShapeEnum(newPacket.getShape()) == chosenPort.getShape());
                newPacket.setWire(outputWire, compatibleExit); gamePanel.addPacketInternal(newPacket, isPredictionRun); packetsGeneratedThisRun++;
            } else { lastGenerationTimeThisRun -= generationFrequencyMillisConfig; }
        }
    }

    public void draw(Graphics2D g2d) {
        this.indicatorOn = areAllMyPortsConnected(); Color bodyColor;
        switch(systemType) {
            case SOURCE: case SINK: bodyColor = new Color(90, 90, 90); break;
            case SPY: bodyColor = new Color(130, 60, 130); break;
            case CORRUPTOR: bodyColor = new Color(150, 40, 40); break;
            case VPN: bodyColor = new Color(60, 130, 60); break;
            case ANTITROJAN: bodyColor = new Color(60, 130, 130); break;
            case NODE: default: bodyColor = new Color(60, 80, 130); break;
        }
        g2d.setColor(bodyColor); g2d.fillRect(x, y, SYSTEM_WIDTH, SYSTEM_HEIGHT);
        g2d.setColor(Color.LIGHT_GRAY); g2d.setStroke(new BasicStroke(1)); g2d.drawRect(x, y, SYSTEM_WIDTH, SYSTEM_HEIGHT);
        int indicatorSize = 8; int indicatorX = x + SYSTEM_WIDTH / 2 - indicatorSize / 2; int indicatorY = y - indicatorSize - 3;
        g2d.setColor(indicatorOn ? Color.GREEN.brighter() : new Color(100, 0, 0)); g2d.fillOval(indicatorX, indicatorY, indicatorSize, indicatorSize);
        g2d.setColor(Color.DARK_GRAY); g2d.drawOval(indicatorX, indicatorY, indicatorSize, indicatorSize);
        g2d.setFont(new Font("Arial", Font.BOLD, 12)); g2d.setColor(Color.WHITE); String typeInitial = systemType.name().substring(0,1); g2d.drawString(typeInitial, x + 5, y + 15);
        synchronized(inputPorts) { for (Port p : inputPorts) if(p!=null) p.draw(g2d); }
        synchronized(outputPorts) { for (Port p : outputPorts) if(p!=null) p.draw(g2d); }
        if (!isReferenceSystem() && getQueueSize() > 0) {
            g2d.setColor(Color.ORANGE); g2d.setFont(new Font("Arial", Font.BOLD, 11)); String queueText = "Q:" + getQueueSize();
            FontMetrics fm = g2d.getFontMetrics(); int textWidth = fm.stringWidth(queueText); int textX = x + SYSTEM_WIDTH - textWidth - 5; int textY = y + SYSTEM_HEIGHT - 5;
            g2d.drawString(queueText, textX, textY);
        }
        if(getSystemType() == NetworkEnums.SystemType.ANTITROJAN) {
            long currentTime = UIManager.get("game.time.ms") instanceof Long ? (Long)UIManager.get("game.time.ms") : 0;
            if (currentTime < antiTrojanCooldownUntil) {
                float cooldownProgress = 1.0f - ((float)(antiTrojanCooldownUntil - currentTime) / ANTITROJAN_COOLDOWN_MS);
                g2d.setColor(Color.RED); g2d.setStroke(new BasicStroke(3)); int barWidth = (int)(SYSTEM_WIDTH * cooldownProgress);
                g2d.drawLine(x, y + SYSTEM_HEIGHT + 5, x + barWidth, y + SYSTEM_HEIGHT + 5);
            } else {
                g2d.setColor(new Color(0, 255, 255, 40)); int radius = (int)ANTITROJAN_SCAN_RADIUS;
                g2d.fillOval(x + SYSTEM_WIDTH/2 - radius, y + SYSTEM_HEIGHT/2 - radius, radius*2, radius*2);
            }
        }
    }

    public void addPort(NetworkEnums.PortType type, NetworkEnums.PortShape shape) { if (shape == NetworkEnums.PortShape.ANY) throw new IllegalArgumentException("PortShape.ANY is not allowed."); int index; if (type == NetworkEnums.PortType.INPUT) { synchronized(inputPorts) { index = inputPorts.size(); inputPorts.add(new Port(this, type, shape, index)); } } else { synchronized(outputPorts) { index = outputPorts.size(); outputPorts.add(new Port(this, type, shape, index)); } } updateAllPortPositions(); }
    public void updateAllPortPositions() { int totalInput, totalOutput; synchronized (inputPorts) { totalInput = inputPorts.size(); } synchronized (outputPorts) { totalOutput = outputPorts.size(); } synchronized (inputPorts) { for (int i = 0; i < inputPorts.size(); i++) { Port p = inputPorts.get(i); if (p != null) p.updatePosition(this.x, this.y, totalInput, totalOutput); } } synchronized (outputPorts) { for (int i = 0; i < outputPorts.size(); i++) { Port p = outputPorts.get(i); if (p != null) p.updatePosition(this.x, this.y, totalInput, totalOutput); } } }
    public Port getPortAt(Point p) { synchronized (outputPorts) { for (Port port : outputPorts) if (port != null && port.contains(p)) return port; } synchronized (inputPorts) { for (Port port : inputPorts) if (port != null && port.contains(p)) return port; } return null; }

    // --- Private Helper Methods ---
    private void handleVpnLogic(Packet packet, GamePanel gamePanel, boolean isPredictionRun) {
        if (vpnIsActive) {
            if (packet.getPacketType() == NetworkEnums.PacketType.MESSENGER) packet.transformToProtected();
            else if (packet.getPacketType() == NetworkEnums.PacketType.SECRET) packet.upgradeSecretPacket();
        }
        processOrQueuePacket(packet, gamePanel, isPredictionRun);
    }
    private void handleSpyLogic(Packet packet, GamePanel gamePanel, boolean isPredictionRun) {
        if (packet.getPacketType() == NetworkEnums.PacketType.SECRET) { gamePanel.packetLostInternal(packet, isPredictionRun); return; }
        List<System> otherSpySystems = gamePanel.getSystems().stream().filter(s -> s != null && s.getSystemType() == NetworkEnums.SystemType.SPY && s.getId() != this.id).collect(Collectors.toList());
        if (otherSpySystems.isEmpty()) { gamePanel.packetLostInternal(packet, isPredictionRun); return; }
        Collections.shuffle(otherSpySystems, globalRandom); Port targetPort = otherSpySystems.get(0).findAvailableOutputPort(packet, gamePanel, isPredictionRun);
        if (targetPort != null) { Wire targetWire = gamePanel.findWireFromPort(targetPort); if(targetWire != null) { packet.setCurrentSystem(otherSpySystems.get(0)); packet.setWire(targetWire, Port.getShapeEnum(packet.getShape()) == targetPort.getShape()); } else { gamePanel.packetLostInternal(packet, isPredictionRun); } }
        else { gamePanel.packetLostInternal(packet, isPredictionRun); }
    }
    private void handleCorruptorLogic(Packet packet, GamePanel gamePanel, boolean isPredictionRun) {
        if (packet.getNoise() < 0.01) packet.addNoise(CORRUPTOR_NOISE_ADDITION);
        if (globalRandom.nextDouble() < TROJAN_CONVERSION_CHANCE) packet.setPacketType(NetworkEnums.PacketType.TROJAN);
        Port outputPort = findIncompatibleOutputPort(packet, gamePanel, isPredictionRun); if (outputPort == null) outputPort = findAvailableOutputPort(packet, gamePanel, isPredictionRun);
        if (outputPort != null) { Wire outputWire = gamePanel.findWireFromPort(outputPort); if (outputWire != null) packet.setWire(outputWire, Port.getShapeEnum(packet.getShape()) == outputPort.getShape()); else queuePacket(packet, gamePanel, isPredictionRun); }
        else queuePacket(packet, gamePanel, isPredictionRun);
    }
    private void processOrQueuePacket(Packet packet, GamePanel gamePanel, boolean isPredictionRun) {
        Port outputPort = findAvailableOutputPort(packet, gamePanel, isPredictionRun);
        if (outputPort != null) {
            Wire outputWire = gamePanel.findWireFromPort(outputPort);
            if (outputWire != null) {
                boolean compatibleExit = (packet.getPacketType() == NetworkEnums.PacketType.SECRET) || (Port.getShapeEnum(packet.getShape()) == outputPort.getShape());
                packet.setWire(outputWire, compatibleExit);
                if (packet.getFinalStatusForPrediction() == PredictedPacketStatus.QUEUED) packet.setFinalStatusForPrediction(null);
            } else { packet.setFinalStatusForPrediction(PredictedPacketStatus.STALLED_AT_NODE); queuePacket(packet, gamePanel, isPredictionRun); }
        } else { packet.setFinalStatusForPrediction(PredictedPacketStatus.QUEUED); queuePacket(packet, gamePanel, isPredictionRun); }
    }
    private void queuePacket(Packet packet, GamePanel gamePanel, boolean isPredictionRun) { synchronized (packetQueue) { if (packetQueue.size() < QUEUE_CAPACITY) { packetQueue.offer(packet); if(packet.getFinalStatusForPrediction() != PredictedPacketStatus.LOST) packet.setFinalStatusForPrediction(PredictedPacketStatus.QUEUED); } else { packet.setFinalStatusForPrediction(PredictedPacketStatus.LOST); gamePanel.packetLostInternal(packet, isPredictionRun); } } }
    private Port findAvailableOutputPort(Packet packet, GamePanel gamePanel, boolean isPredictionRun) {
        if (packet == null || gamePanel == null) return null;
        if (packet.getPacketType() == NetworkEnums.PacketType.SECRET) {
            synchronized(outputPorts) { List<Port> shuffledPorts = new ArrayList<>(outputPorts); Collections.shuffle(shuffledPorts, globalRandom); for (Port port : shuffledPorts) { if (port != null && port.isConnected()) { Wire wire = gamePanel.findWireFromPort(port); if (wire != null && !gamePanel.isWireOccupied(wire, isPredictionRun)) return port; } } }
            return null;
        }
        List<Port> compatibleEmptyPorts = new ArrayList<>(); List<Port> nonCompatibleEmptyPorts = new ArrayList<>(); NetworkEnums.PortShape requiredPacketShape = Port.getShapeEnum(packet.getShape());
        if (requiredPacketShape == null) return null;
        synchronized(outputPorts) { List<Port> shuffledPorts = new ArrayList<>(outputPorts); Collections.shuffle(shuffledPorts, globalRandom); for (Port port : shuffledPorts) { if (port != null && port.isConnected()) { Wire wire = gamePanel.findWireFromPort(port); if (wire != null && !gamePanel.isWireOccupied(wire, isPredictionRun)) { if (port.getShape() == requiredPacketShape) compatibleEmptyPorts.add(port); else nonCompatibleEmptyPorts.add(port); } } } }
        if (!compatibleEmptyPorts.isEmpty()) return compatibleEmptyPorts.get(0);
        if (!nonCompatibleEmptyPorts.isEmpty()) return nonCompatibleEmptyPorts.get(0);
        return null;
    }
    private Port findIncompatibleOutputPort(Packet packet, GamePanel gamePanel, boolean isPredictionRun) {
        List<Port> incompatibleEmptyPorts = new ArrayList<>(); NetworkEnums.PortShape requiredPacketShape = Port.getShapeEnum(packet.getShape());
        synchronized(outputPorts) { List<Port> shuffledPorts = new ArrayList<>(outputPorts); Collections.shuffle(shuffledPorts, globalRandom); for (Port port : shuffledPorts) { if (port != null && port.isConnected()) { Wire wire = gamePanel.findWireFromPort(port); if (wire != null && !gamePanel.isWireOccupied(wire, isPredictionRun) && port.getShape() != requiredPacketShape) incompatibleEmptyPorts.add(port); } } }
        return incompatibleEmptyPorts.isEmpty() ? null : incompatibleEmptyPorts.get(0);
    }
    private List<Packet> getAllPacketsInQueues(List<System> systems) { List<Packet> queuedPackets = new ArrayList<>(); for (System s : systems) if (s != null) synchronized(s.packetQueue) { queuedPackets.addAll(s.packetQueue); } return queuedPackets; }

    // --- Standard Overrides ---
    @Override public boolean equals(Object o) { if (this == o) return true; if (o == null || getClass() != o.getClass()) return false; System system = (System) o; return id == system.id; }
    @Override public int hashCode() { return Objects.hash(id); }
    @Override public String toString() { return "System{id=" + id + ", type=" + systemType.name() + ", pos=(" + x + "," + y + ")" + ", Q=" + getQueueSize() + "/" + QUEUE_CAPACITY + '}'; }
}