// ===== File: System.java =====

package com.networkopsim.game;
import javax.swing.*;
import java.awt.*;
import java.awt.geom.Point2D;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Map;
import java.util.HashMap;
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

    // --- NEW: System Disabling Fields ---
    private volatile boolean isDisabled = false;
    private volatile long disabledUntil = 0;
    public static final long SYSTEM_DISABLE_DURATION_MS = 10000; // 10 seconds
    public static final double MAX_SAFE_ENTRY_SPEED = 4.0; // Max speed allowed before shutdown

    // Fields for Merger logic
    private final Map<Integer, List<Packet>> mergingPackets = new HashMap<>();


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
    public boolean isDisabled() { return isDisabled; } // NEW GETTER
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
        this.mergingPackets.clear();
        this.isDisabled = false; // NEW
        this.disabledUntil = 0; // NEW
    }

    // --- NEW: Method to update system state each tick ---
    public void updateSystemState(long currentTimeMs, GamePanel gamePanel) {
        // Re-enable the system if the disable duration has passed
        if (isDisabled && currentTimeMs >= disabledUntil) {
            isDisabled = false;
            disabledUntil = 0;
            if (!isReferenceSystem && !gamePanel.getGame().isMuted()) {
                gamePanel.getGame().playSoundEffect("system_reboot");
            }
        }
    }


    public void receivePacket(Packet packet, GamePanel gamePanel, boolean isPredictionRun) {
        if (packet == null || gamePanel == null) return;

        // --- NEW: System Disabled Logic ---
        if (this.isDisabled) {
            packet.reverseDirection(gamePanel); // Packet reverses course
            return;
        }

        // --- NEW: Over-speed Check ---
        if (!isReferenceSystem && packet.getCurrentSpeedMagnitude() > MAX_SAFE_ENTRY_SPEED) {
            this.isDisabled = true;
            this.disabledUntil = gamePanel.getSimulationTimeElapsedMs() + SYSTEM_DISABLE_DURATION_MS;
            if (!isPredictionRun && !gamePanel.getGame().isMuted()) {
                gamePanel.getGame().playSoundEffect("system_shutdown");
            }
            packet.reverseDirection(gamePanel); // Packet reverses course after causing shutdown
            return;
        }


        // --- BULK PACKET SPECIAL RULES (APPLY BEFORE ANYTHING ELSE) ---
        if(packet.getPacketType() == NetworkEnums.PacketType.BULK){
            // 1. Destroy all packets in the queue
            synchronized(packetQueue) {
                for(Packet p : packetQueue){
                    gamePanel.packetLostInternal(p, isPredictionRun);
                }
                packetQueue.clear();
            }
            // 2. Randomize the entry port's shape
            if(packet.getCurrentWire() != null) {
                Port entryPort = packet.getCurrentWire().getEndPort();
                entryPort.randomizeShape();
            }
        }

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
            case SINK:
                // Bulk packets reaching a sink are lost. Bit packets are lost.
                if(packet.getPacketType() == NetworkEnums.PacketType.BULK || packet.getPacketType() == NetworkEnums.PacketType.BIT){
                    gamePanel.packetLostInternal(packet, isPredictionRun);
                } else {
                    gamePanel.packetSuccessfullyDeliveredInternal(packet, isPredictionRun);
                }
                break;
            case SOURCE: gamePanel.packetLostInternal(packet, isPredictionRun); break;
            case SPY: handleSpyLogic(packet, gamePanel, isPredictionRun); break;
            case CORRUPTOR: handleCorruptorLogic(packet, gamePanel, isPredictionRun); break;
            case VPN: handleVpnLogic(packet, gamePanel, isPredictionRun); break;
            case DISTRIBUTOR: handleDistributorLogic(packet, gamePanel, isPredictionRun); break;
            case MERGER: handleMergerLogic(packet, gamePanel, isPredictionRun); break;
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
        if (isReferenceSystem() || isDisabled) return; // MODIFIED: Don't process queue if disabled
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
                    boolean compatibleExit = (sentPacket.getPacketType() == NetworkEnums.PacketType.SECRET) ||
                            (sentPacket.getPacketType() == NetworkEnums.PacketType.BULK) ||
                            (Port.getShapeEnum(sentPacket.getShape()) == outputPort.getShape());
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

            NetworkEnums.PacketShape shapeToGenerate;
            if (packetTypeToGenerate == NetworkEnums.PacketType.BULK || packetTypeToGenerate == NetworkEnums.PacketType.WOBBLE) {
                shapeToGenerate = NetworkEnums.PacketShape.CIRCLE;
            } else if (packetTypeToGenerate == NetworkEnums.PacketType.SECRET) {
                shapeToGenerate = NetworkEnums.PacketShape.CIRCLE;
            } else {
                shapeToGenerate = getPacketShapeFromPortShapeStatic(chosenPort.getShape());
            }

            if (shapeToGenerate != null && outputWire != null) {
                Packet newPacket = new Packet(shapeToGenerate, chosenPort.getX(), chosenPort.getY(), packetTypeToGenerate);
                boolean compatibleExit = (packetTypeToGenerate == NetworkEnums.PacketType.SECRET) ||
                        (packetTypeToGenerate == NetworkEnums.PacketType.BULK) ||
                        (Port.getShapeEnum(newPacket.getShape()) == chosenPort.getShape());
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
            case DISTRIBUTOR: bodyColor = new Color(160, 100, 40); break;
            case MERGER: bodyColor = new Color(40, 100, 160); break;
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

        // --- NEW: Draw Disabled State ---
        if (isDisabled) {
            Composite originalComposite = g2d.getComposite();
            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.6f));
            g2d.setColor(new Color(200, 0, 0));
            g2d.fillRect(x, y, SYSTEM_WIDTH, SYSTEM_HEIGHT);
            g2d.setComposite(originalComposite);

            g2d.setColor(Color.WHITE);
            g2d.setFont(new Font("Arial", Font.BOLD, 12));
            String disabledText = "DISABLED";
            FontMetrics fm = g2d.getFontMetrics();
            int textWidth = fm.stringWidth(disabledText);
            g2d.drawString(disabledText, x + (SYSTEM_WIDTH - textWidth) / 2, y + SYSTEM_HEIGHT / 2 + fm.getAscent() / 2);
        }
    }

    public void addPort(NetworkEnums.PortType type, NetworkEnums.PortShape shape) { if (shape == NetworkEnums.PortShape.ANY) throw new IllegalArgumentException("PortShape.ANY is not allowed."); int index; if (type == NetworkEnums.PortType.INPUT) { synchronized(inputPorts) { index = inputPorts.size(); inputPorts.add(new Port(this, type, shape, index)); } } else { synchronized(outputPorts) { index = outputPorts.size(); outputPorts.add(new Port(this, type, shape, index)); } } updateAllPortPositions(); }
    public void updateAllPortPositions() { int totalInput, totalOutput; synchronized (inputPorts) { totalInput = inputPorts.size(); } synchronized (outputPorts) { totalOutput = outputPorts.size(); } synchronized (inputPorts) { for (int i = 0; i < inputPorts.size(); i++) { Port p = inputPorts.get(i); if (p != null) p.updatePosition(this.x, this.y, totalInput, totalOutput); } } synchronized (outputPorts) { for (int i = 0; i < outputPorts.size(); i++) { Port p = outputPorts.get(i); if (p != null) p.updatePosition(this.x, this.y, totalInput, totalOutput); } } }
    public Port getPortAt(Point p) { synchronized (outputPorts) { for (Port port : outputPorts) if (port != null && port.contains(p)) return port; } synchronized (inputPorts) { for (Port port : inputPorts) if (port != null && port.contains(p)) return port; } return null; }

    // --- Private Helper Methods ---
    private void handleDistributorLogic(Packet packet, GamePanel gamePanel, boolean isPredictionRun) {
        if (packet.getPacketType() != NetworkEnums.PacketType.BULK) {
            processOrQueuePacket(packet, gamePanel, isPredictionRun);
            return;
        }

        int numBits = packet.getSize();
        if (numBits <= 0) { // Should not happen but good to check
            gamePanel.packetLostInternal(packet, isPredictionRun);
            return;
        }

        // Create all bit packets
        List<Packet> bitPackets = new ArrayList<>();
        for(int i = 0; i < numBits; i++){
            // Bits are represented by MESSENGER packets of size 1 with special IDs
            Packet bit = new Packet(NetworkEnums.PacketShape.CIRCLE, this.x, this.y, NetworkEnums.PacketType.BIT);
            bit.configureAsBit(packet.getId(), numBits);
            bitPackets.add(bit);
        }

        // Lose the original bulk packet
        gamePanel.packetLostInternal(packet, isPredictionRun);

        // Send out the new bit packets
        for(Packet bit : bitPackets){
            processOrQueuePacket(bit, gamePanel, isPredictionRun);
        }
    }

    private void handleMergerLogic(Packet packet, GamePanel gamePanel, boolean isPredictionRun) {
        if(packet.getPacketType() != NetworkEnums.PacketType.BIT){
            // Normal packets pass through
            processOrQueuePacket(packet, gamePanel, isPredictionRun);
            return;
        }

        int parentId = packet.getBulkParentId();
        if(parentId == -1){ // Should not happen
            gamePanel.packetLostInternal(packet, isPredictionRun);
            return;
        }

        // Add the bit to the merging map
        mergingPackets.computeIfAbsent(parentId, k -> new ArrayList<>()).add(packet);
        gamePanel.packetLostInternal(packet, isPredictionRun); // The bit is consumed

        // Check if the bulk packet is complete
        List<Packet> collectedBits = mergingPackets.get(parentId);
        if(collectedBits.size() >= packet.getTotalBitsInGroup()){
            // Re-form the bulk packet
            Packet newBulkPacket = new Packet(NetworkEnums.PacketShape.CIRCLE, this.x, this.y, NetworkEnums.PacketType.BULK);

            queuePacket(newBulkPacket, gamePanel, isPredictionRun);
            mergingPackets.remove(parentId); // Clean up
        }
    }
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
                boolean compatibleExit = (packet.getPacketType() == NetworkEnums.PacketType.SECRET) ||
                        (packet.getPacketType() == NetworkEnums.PacketType.BULK) ||
                        (Port.getShapeEnum(packet.getShape()) == outputPort.getShape());
                packet.setWire(outputWire, compatibleExit);
                if (packet.getFinalStatusForPrediction() == PredictedPacketStatus.QUEUED) packet.setFinalStatusForPrediction(null);
            } else { packet.setFinalStatusForPrediction(PredictedPacketStatus.STALLED_AT_NODE); queuePacket(packet, gamePanel, isPredictionRun); }
        } else { packet.setFinalStatusForPrediction(PredictedPacketStatus.QUEUED); queuePacket(packet, gamePanel, isPredictionRun); }
    }
    private void queuePacket(Packet packet, GamePanel gamePanel, boolean isPredictionRun) { synchronized (packetQueue) { if (packetQueue.size() < QUEUE_CAPACITY) { packetQueue.offer(packet); if(packet.getFinalStatusForPrediction() != PredictedPacketStatus.LOST) packet.setFinalStatusForPrediction(PredictedPacketStatus.QUEUED); } else { packet.setFinalStatusForPrediction(PredictedPacketStatus.LOST); gamePanel.packetLostInternal(packet, isPredictionRun); } } }
    private Port findAvailableOutputPort(Packet packet, GamePanel gamePanel, boolean isPredictionRun) {
        if (packet == null || gamePanel == null) return null;

        List<Port> candidatePorts = new ArrayList<>();
        // Find all connected, non-occupied ports leading to non-disabled systems
        synchronized(outputPorts) {
            for (Port port : outputPorts) {
                if (port != null && port.isConnected()) {
                    Wire wire = gamePanel.findWireFromPort(port);
                    if (wire != null && !gamePanel.isWireOccupied(wire, isPredictionRun)) {
                        System destinationSystem = wire.getEndPort().getParentSystem();
                        if (destinationSystem != null && !destinationSystem.isDisabled()) {
                            candidatePorts.add(port);
                        }
                    }
                }
            }
        }
        if (candidatePorts.isEmpty()) return null;
        Collections.shuffle(candidatePorts, globalRandom);

        // Bulk and Secret packets ignore shape compatibility
        if (packet.getPacketType() == NetworkEnums.PacketType.BULK || packet.getPacketType() == NetworkEnums.PacketType.SECRET) {
            return candidatePorts.get(0);
        }

        List<Port> compatiblePorts = new ArrayList<>();
        List<Port> nonCompatiblePorts = new ArrayList<>();
        NetworkEnums.PortShape requiredPacketShape = Port.getShapeEnum(packet.getShape());
        if (requiredPacketShape == null) return null;

        for(Port port : candidatePorts) {
            if (port.getShape() == requiredPacketShape) {
                compatiblePorts.add(port);
            } else {
                nonCompatiblePorts.add(port);
            }
        }

        if (!compatiblePorts.isEmpty()) return compatiblePorts.get(0);
        if (!nonCompatiblePorts.isEmpty()) return nonCompatiblePorts.get(0);
        return null;
    }
    private Port findIncompatibleOutputPort(Packet packet, GamePanel gamePanel, boolean isPredictionRun) {
        List<Port> incompatibleEmptyPorts = new ArrayList<>(); NetworkEnums.PortShape requiredPacketShape = Port.getShapeEnum(packet.getShape());
        synchronized(outputPorts) {
            List<Port> shuffledPorts = new ArrayList<>(outputPorts);
            Collections.shuffle(shuffledPorts, globalRandom);
            for (Port port : shuffledPorts) {
                if (port != null && port.isConnected()) {
                    Wire wire = gamePanel.findWireFromPort(port);
                    if (wire != null && !gamePanel.isWireOccupied(wire, isPredictionRun) && port.getShape() != requiredPacketShape) {
                        System destinationSystem = wire.getEndPort().getParentSystem();
                        if (destinationSystem != null && !destinationSystem.isDisabled()) {
                            incompatibleEmptyPorts.add(port);
                        }
                    }
                }
            }
        }
        return incompatibleEmptyPorts.isEmpty() ? null : incompatibleEmptyPorts.get(0);
    }
    private List<Packet> getAllPacketsInQueues(List<System> systems) { List<Packet> queuedPackets = new ArrayList<>(); for (System s : systems) if (s != null) synchronized(s.packetQueue) { queuedPackets.addAll(s.packetQueue); } return queuedPackets; }

    // --- Standard Overrides ---
    @Override public boolean equals(Object o) { if (this == o) return true; if (o == null || getClass() != o.getClass()) return false; System system = (System) o; return id == system.id; }
    @Override public int hashCode() { return Objects.hash(id); }
    @Override public String toString() { return "System{id=" + id + ", type=" + systemType.name() + ", pos=(" + x + "," + y + ")" + ", Q=" + getQueueSize() + "/" + QUEUE_CAPACITY + (isDisabled ? ", DISABLED" : "") + '}'; }
}