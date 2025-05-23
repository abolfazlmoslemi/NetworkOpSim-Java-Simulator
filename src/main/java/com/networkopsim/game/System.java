// ===== System.java =====

package com.networkopsim.game;
import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Objects;
import java.util.Random; // Keep this import

public class System {
    public static final int SYSTEM_WIDTH = 80;
    public static final int SYSTEM_HEIGHT = 60;
    private static final int QUEUE_CAPACITY = 5;
    private static int nextId = 0;
    private static Random globalRandom = new Random(); // Static instance for deterministic choices

    private final int id;
    private int x, y;
    private final boolean isReferenceSystem;
    private boolean indicatorOn = false;
    private final List<Port> inputPorts = Collections.synchronizedList(new ArrayList<>());
    private final List<Port> outputPorts = Collections.synchronizedList(new ArrayList<>());
    public final Queue<Packet> packetQueue = new LinkedList<>(); // Not synchronized, access should be controlled

    private int packetsToGenerateConfig = 0;
    private int packetsGeneratedThisRun = 0;
    private int generationFrequencyMillisConfig = 2000;
    private long lastGenerationTimeThisRun = -1;


    public static void resetGlobalRandomSeed(long seed) {
        globalRandom = new Random(seed);
        // java.lang.System.out.println("System.globalRandom re-seeded with: " + seed);
    }

    // This getter might be useful if other parts of the simulation need deterministic random choices
    // based on the same seed (e.g., initial packet offset side preference in Packet.java, though that's now ID-based).
    public static Random getGlobalRandom() {
        return globalRandom;
    }

    public static void resetGlobalId() {
        nextId = 0;
    }


    public System(int x, int y, boolean isReference) {
        this.id = nextId++;
        this.x = x;
        this.y = y;
        this.isReferenceSystem = isReference;
        resetForNewRun(); // Initialize run-specific state
    }

    public void resetForNewRun() {
        synchronized (packetQueue) { // Ensure thread-safe access if queue could be modified elsewhere
            packetQueue.clear();
        }
        this.packetsGeneratedThisRun = 0;
        this.lastGenerationTimeThisRun = -1; // Reset to allow first packet generation based on frequency
    }

    public void addPort(NetworkEnums.PortType type, NetworkEnums.PortShape shape) {
        if (shape == NetworkEnums.PortShape.ANY) {
            throw new IllegalArgumentException("PortShape.ANY is not allowed for system ports in this configuration.");
        }
        Port newPort;
        int index; // Index within its type (input/output)
        if (type == NetworkEnums.PortType.INPUT) {
            synchronized(inputPorts) {
                index = inputPorts.size();
                newPort = new Port(this, type, shape, index);
                inputPorts.add(newPort);
            }
        } else { // OUTPUT
            synchronized(outputPorts) {
                index = outputPorts.size();
                newPort = new Port(this, type, shape, index);
                outputPorts.add(newPort);
            }
        }
        updateAllPortPositions(); // Recalculate positions for all ports
    }

    public void updateAllPortPositions() {
        int totalInput;
        int totalOutput;
        // Get counts outside synchronized blocks to avoid nested synchronization if Port.updatePosition synchronizes
        synchronized (inputPorts) { totalInput = inputPorts.size(); }
        synchronized (outputPorts) { totalOutput = outputPorts.size(); }

        synchronized (inputPorts) {
            for (int i = 0; i < inputPorts.size(); i++) {
                Port p = inputPorts.get(i);
                if (p != null) {
                    // Pass current total counts for accurate calculation
                    p.updatePosition(this.x, this.y, totalInput, totalOutput);
                }
            }
        }
        synchronized (outputPorts) {
            for (int i = 0; i < outputPorts.size(); i++) {
                Port p = outputPorts.get(i);
                if (p != null) {
                    p.updatePosition(this.x, this.y, totalInput, totalOutput);
                }
            }
        }
    }

    public Port getPortAt(Point p) {
        // Iterate over copies to avoid CME if ports could be modified, though less likely here
        List<Port> currentOutputPorts;
        List<Port> currentInputPorts;
        synchronized (outputPorts) { currentOutputPorts = new ArrayList<>(outputPorts); }
        synchronized (inputPorts) { currentInputPorts = new ArrayList<>(inputPorts); }

        for (Port port : currentOutputPorts) {
            if (port != null && port.contains(p)) return port;
        }
        for (Port port : currentInputPorts) {
            if (port != null && port.contains(p)) return port;
        }
        return null;
    }

    public void receivePacket(Packet packet, GamePanel gamePanel, boolean isPredictionRun) {
        if (packet == null || gamePanel == null) return;
        packet.setCurrentSystem(this); // Packet is now "in" this system
        gamePanel.addRoutingCoinsInternal(packet, isPredictionRun); // Add coins if it's a live run

        if (isReferenceSystem && !hasOutputPorts()) { // This is a Sink system (reference and no outputs)
            packet.setFinalStatusForPrediction(PredictedPacketStatus.DELIVERED);
            gamePanel.packetSuccessfullyDeliveredInternal(packet, isPredictionRun);
        } else if (!isReferenceSystem) { // This is a regular Node system
            processOrQueuePacket(packet, gamePanel, isPredictionRun);
        } else { // This is a Source system (reference and has outputs), it should not receive packets
            // java.lang.System.err.println("ERROR: Source System " + id + " received a packet unexpectedly! Packet " + packet.getId() + " LOST.");
            packet.setFinalStatusForPrediction(PredictedPacketStatus.LOST);
            gamePanel.packetLostInternal(packet, isPredictionRun);
        }
    }

    private void processOrQueuePacket(Packet packet, GamePanel gamePanel, boolean isPredictionRun) {
        // Attempt to find an immediate output port for the packet
        Port outputPort = findAvailableOutputPort(packet, gamePanel, isPredictionRun);
        if (outputPort != null) { // An available port was found
            Wire outputWire = gamePanel.findWireFromPort(outputPort);
            if (outputWire != null) { // And it's connected to a wire
                NetworkEnums.PortShape packetRequiredPortShape = Port.getShapeEnum(packet.getShape());
                boolean compatibleExit = (packetRequiredPortShape != null &&
                        outputPort.getShape() == packetRequiredPortShape);
                packet.setWire(outputWire, compatibleExit); // Send packet on its way
                // If packet was predicted as QUEUED and now sent, its status is no longer QUEUED.
                if (packet.getFinalStatusForPrediction() == PredictedPacketStatus.QUEUED) {
                    packet.setFinalStatusForPrediction(null); // Will become ON_WIRE or other status based on its travel
                }
            } else {
                // This should be rare if findAvailableOutputPort checks for connected wires.
                // If port selected but no wire, implies an issue or a very transient state.
                // java.lang.System.err.println("WARN: System " + id + ": Output port " + outputPort + " available but no wire found! Pkt " + packet.getId() + " queued.");
                packet.setFinalStatusForPrediction(PredictedPacketStatus.STALLED_AT_NODE); // Or QUEUED, as it can't leave
                queuePacket(packet, gamePanel, isPredictionRun);
            }
        } else { // No immediate output port available
            packet.setFinalStatusForPrediction(PredictedPacketStatus.QUEUED); // Mark for prediction
            queuePacket(packet, gamePanel, isPredictionRun);
        }
    }

    private void queuePacket(Packet packet, GamePanel gamePanel, boolean isPredictionRun) {
        synchronized (packetQueue) {
            if (packetQueue.size() < QUEUE_CAPACITY) {
                packetQueue.offer(packet);
                // Prediction status is typically set to QUEUED before calling this, but confirm.
                if(packet.getFinalStatusForPrediction() != PredictedPacketStatus.LOST) { // Don't override if already deemed lost
                    packet.setFinalStatusForPrediction(PredictedPacketStatus.QUEUED);
                }
            } else { // Queue is full
                // java.lang.System.out.println("System " + id + ": Queue full! Pkt " + packet.getId() + " LOST.");
                packet.setFinalStatusForPrediction(PredictedPacketStatus.LOST); // Lost due to full queue
                gamePanel.packetLostInternal(packet, isPredictionRun);
            }
        }
    }

    public void processQueue(GamePanel gamePanel, boolean isPredictionRun) {
        if (isReferenceSystem) return; // Sources/Sinks don't process queues this way

        Packet packetToProcess;
        synchronized (packetQueue) {
            if (packetQueue.isEmpty()) return;
            packetToProcess = packetQueue.peek(); // Look at the packet without removing
        }

        if (packetToProcess == null) return; // Should not happen if queue not empty, but defensive

        if (packetToProcess.isMarkedForRemoval()) { // Clean up if somehow a removed packet is still in queue
            synchronized (packetQueue) {
                packetQueue.poll(); // Remove it
            }
            return;
        }

        Port outputPort = findAvailableOutputPort(packetToProcess, gamePanel, isPredictionRun);
        if (outputPort != null) { // Found an available port
            Wire outputWire = gamePanel.findWireFromPort(outputPort);
            if (outputWire != null) { // And it's connected
                Packet sentPacket = null;
                synchronized (packetQueue) {
                    // Re-check: ensure the packet at head is still the one we planned for and remove it
                    if (!packetQueue.isEmpty() && packetQueue.peek().equals(packetToProcess)) {
                        sentPacket = packetQueue.poll();
                    }
                }

                if (sentPacket != null) { // Successfully dequeued
                    NetworkEnums.PortShape packetRequiredPortShape = Port.getShapeEnum(sentPacket.getShape());
                    boolean compatibleExit = (packetRequiredPortShape != null &&
                            outputPort.getShape() == packetRequiredPortShape);
                    sentPacket.setWire(outputWire, compatibleExit);
                    // Clear QUEUED status if it was set for prediction
                    if (sentPacket.getFinalStatusForPrediction() == PredictedPacketStatus.QUEUED) {
                        sentPacket.setFinalStatusForPrediction(null);
                    }
                }
                // If sentPacket is null, another thread/process might have modified the queue. The packet remains.
            } else {
                // Port was available, but no wire. Packet remains queued. Prediction status is QUEUED.
                if(packetToProcess.getFinalStatusForPrediction() != PredictedPacketStatus.LOST){
                    packetToProcess.setFinalStatusForPrediction(PredictedPacketStatus.QUEUED);
                }
            }
        } else {
            // No output port available for the packet at the head of the queue. It remains queued.
            // Ensure its prediction status reflects this.
            if(packetToProcess.getFinalStatusForPrediction() != PredictedPacketStatus.LOST){
                packetToProcess.setFinalStatusForPrediction(PredictedPacketStatus.QUEUED);
            }
        }
    }

    private Port findAvailableOutputPort(Packet packet, GamePanel gamePanel, boolean isPredictionRun) {
        if (packet == null || gamePanel == null) return null;

        List<Port> compatibleEmptyPorts = new ArrayList<>();
        List<Port> nonCompatibleEmptyPorts = new ArrayList<>();

        NetworkEnums.PortShape requiredPacketShape = Port.getShapeEnum(packet.getShape());
        if (requiredPacketShape == null) {
            // java.lang.System.err.println("System " + id + ": Cannot determine required port shape for Packet " + packet.getId() + ". Cannot find output port.");
            return null;
        }

        List<Port> currentOutputPortsSnapshot;
        synchronized(outputPorts) {
            currentOutputPortsSnapshot = new ArrayList<>(outputPorts); // Create a snapshot for safe iteration
        }

        // Shuffle the snapshot of ports using the globalRandom for deterministic selection order
        // This is crucial for determinism if multiple ports are equally viable.
        Collections.shuffle(currentOutputPortsSnapshot, globalRandom);

        for (Port port : currentOutputPortsSnapshot) {
            if (port != null && port.isConnected()) {
                Wire wire = gamePanel.findWireFromPort(port);
                // Wire must exist and not be occupied
                if (wire != null && !gamePanel.isWireOccupied(wire, isPredictionRun)) {
                    if (port.getShape() == requiredPacketShape) {
                        compatibleEmptyPorts.add(port);
                    } else {
                        nonCompatibleEmptyPorts.add(port);
                    }
                }
            }
        }

        // Prefer compatible, empty ports. Since the list was shuffled, taking the first is deterministic.
        if (!compatibleEmptyPorts.isEmpty()) {
            return compatibleEmptyPorts.get(0);
        }
        // Fallback to non-compatible, empty ports.
        if (!nonCompatibleEmptyPorts.isEmpty()) {
            return nonCompatibleEmptyPorts.get(0);
        }
        return null; // No suitable port found
    }

    public void configureGenerator(int totalPackets, int frequencyMs) {
        if (isReferenceSystem && hasOutputPorts()) { // Only Source systems can generate
            this.packetsToGenerateConfig = Math.max(0, totalPackets);
            this.generationFrequencyMillisConfig = Math.max(100, frequencyMs); // Min frequency 100ms
        } else {
            this.packetsToGenerateConfig = 0; // Non-sources or sinks cannot generate
        }
    }

    public void attemptPacketGeneration(GamePanel gamePanel, long currentSimTimeMs, boolean isPredictionRun) {
        if (!isReferenceSystem || !hasOutputPorts() || packetsGeneratedThisRun >= packetsToGenerateConfig || packetsToGenerateConfig <= 0) {
            return;
        }

        if (lastGenerationTimeThisRun == -1) { // First packet generation attempt for this run
            // Set to allow generation if currentSimTimeMs is >= 0 (or effectively, the first eligible tick)
            lastGenerationTimeThisRun = currentSimTimeMs - generationFrequencyMillisConfig;
        }

        if (currentSimTimeMs - lastGenerationTimeThisRun < generationFrequencyMillisConfig) {
            return; // Not time to generate yet
        }

        List<Port> availablePorts = new ArrayList<>();
        List<Port> currentOutputPortsSnapshot;
        synchronized(outputPorts) {
            currentOutputPortsSnapshot = new ArrayList<>(outputPorts);
        }
        // Shuffle for deterministic port choice if multiple are available
        Collections.shuffle(currentOutputPortsSnapshot, globalRandom);

        for (Port port : currentOutputPortsSnapshot) {
            if (port != null && port.isConnected()) {
                Wire wire = gamePanel.findWireFromPort(port);
                if (wire != null && !gamePanel.isWireOccupied(wire, isPredictionRun)) {
                    availablePorts.add(port); // Port is connected, wire exists and is free
                }
            }
        }

        if (!availablePorts.isEmpty()) {
            // At this point, a packet *will* be generated if a suitable port is chosen.
            // So, update the last generation time.
            long previousLastGenTime = lastGenerationTimeThisRun; // Store for potential rollback
            lastGenerationTimeThisRun = currentSimTimeMs;

            // availablePorts is already shuffled. Take the first one.
            Port chosenPort = availablePorts.get(0);
            Wire outputWire = gamePanel.findWireFromPort(chosenPort); // Should still be valid
            NetworkEnums.PacketShape shapeToGenerate = getPacketShapeFromPortShapeStatic(chosenPort.getShape());

            if (shapeToGenerate != null && outputWire != null) {
                Packet newPacket = new Packet(shapeToGenerate, chosenPort.getX(), chosenPort.getY());
                NetworkEnums.PortShape packetRequiredPortShape = Port.getShapeEnum(newPacket.getShape());
                boolean compatibleExit = (packetRequiredPortShape != null &&
                        chosenPort.getShape() == packetRequiredPortShape);

                newPacket.setWire(outputWire, compatibleExit);
                gamePanel.addPacketInternal(newPacket, isPredictionRun);
                packetsGeneratedThisRun++;
            } else {
                // Should not happen if chosenPort and shapeToGenerate logic is correct.
                // Rollback lastGenerationTime to allow re-attempt sooner if generation failed unexpectedly.
                lastGenerationTimeThisRun = previousLastGenTime;
                // java.lang.System.err.println("System " + id + ": Failed to generate packet despite available port. Port: " + chosenPort + ", Shape: " + shapeToGenerate);
            }
        }
        // If no available ports, do nothing, wait for next opportunity. lastGenerationTimeThisRun is not updated.
    }

    private boolean areAllMyPortsConnected() {
        // Use snapshots for thread safety during iteration
        List<Port> currentInputPorts, currentOutputPorts;
        synchronized(inputPorts) { currentInputPorts = new ArrayList<>(inputPorts); }
        synchronized(outputPorts) { currentOutputPorts = new ArrayList<>(outputPorts); }

        for (Port p : currentInputPorts) {
            if (p != null && !p.isConnected()) {
                return false;
            }
        }
        for (Port p : currentOutputPorts) {
            if (p != null && !p.isConnected()) {
                return false;
            }
        }
        return true;
    }

    public void draw(Graphics2D g2d) {
        this.indicatorOn = areAllMyPortsConnected();
        Color bodyColor = isReferenceSystem ? new Color(90, 90, 90) : new Color(60, 80, 130);
        g2d.setColor(bodyColor);
        g2d.fillRect(x, y, SYSTEM_WIDTH, SYSTEM_HEIGHT);
        g2d.setColor(Color.LIGHT_GRAY);
        g2d.setStroke(new BasicStroke(1));
        g2d.drawRect(x, y, SYSTEM_WIDTH, SYSTEM_HEIGHT);

        int indicatorSize = 8;
        int indicatorX = x + SYSTEM_WIDTH / 2 - indicatorSize / 2;
        int indicatorY = y - indicatorSize - 3;
        g2d.setColor(indicatorOn ? Color.GREEN.brighter() : new Color(100, 0, 0));
        g2d.fillOval(indicatorX, indicatorY, indicatorSize, indicatorSize);
        g2d.setColor(Color.DARK_GRAY);
        g2d.drawOval(indicatorX, indicatorY, indicatorSize, indicatorSize);

        // Draw ports using snapshots for safety, though modifications during draw are unlikely
        List<Port> currentInputPorts, currentOutputPorts;
        synchronized(inputPorts) { currentInputPorts = new ArrayList<>(inputPorts); }
        synchronized(outputPorts) { currentOutputPorts = new ArrayList<>(outputPorts); }
        for (Port p : currentInputPorts) if(p!=null) p.draw(g2d);
        for (Port p : currentOutputPorts) if(p!=null) p.draw(g2d);

        int currentQueueSize;
        synchronized (packetQueue) { currentQueueSize = packetQueue.size(); }
        if (!isReferenceSystem && currentQueueSize > 0) {
            g2d.setColor(Color.ORANGE);
            g2d.setFont(new Font("Arial", Font.BOLD, 11));
            String queueText = "Q:" + currentQueueSize;
            FontMetrics fm = g2d.getFontMetrics();
            int textWidth = fm.stringWidth(queueText);
            int textX = x + SYSTEM_WIDTH - textWidth - 5;
            int textY = y + SYSTEM_HEIGHT - 5;
            g2d.drawString(queueText, textX, textY);
        }
    }


    public static NetworkEnums.PacketShape getPacketShapeFromPortShapeStatic(NetworkEnums.PortShape portShape) {
        if (portShape == null) return null;
        switch (portShape) {
            case SQUARE:   return NetworkEnums.PacketShape.SQUARE;
            case TRIANGLE: return NetworkEnums.PacketShape.TRIANGLE;
            case ANY:      // ANY shape ports cannot determine a specific packet shape to generate.
                // This is now prevented by an exception in addPort for ANY.
            default:
                // java.lang.System.err.println("Warning: Unknown or ANY PortShape encountered in getPacketShapeFromPortShapeStatic: " + portShape);
                return null;
        }
    }

    // --- Getters ---
    public int getId() { return id; }
    public int getX() { return x; }
    public int getY() { return y; }

    // Return unmodifiable copies for safe external access
    public List<Port> getInputPorts() {
        synchronized(inputPorts) {
            return Collections.unmodifiableList(new ArrayList<>(inputPorts));
        }
    }
    public List<Port> getOutputPorts() {
        synchronized(outputPorts) {
            return Collections.unmodifiableList(new ArrayList<>(outputPorts));
        }
    }

    public boolean isReferenceSystem() { return isReferenceSystem; }
    public boolean hasOutputPorts() {
        synchronized(outputPorts) { // Ensure visibility of updates to outputPorts
            return !outputPorts.isEmpty();
        }
    }
    public boolean hasInputPorts() {
        synchronized(inputPorts) {
            return !inputPorts.isEmpty();
        }
    }
    public Point getPosition() { return new Point(x + SYSTEM_WIDTH / 2, y + SYSTEM_HEIGHT / 2); }
    public int getPacketsGeneratedCount() { return packetsGeneratedThisRun; }
    public int getTotalPacketsToGenerate() { return packetsToGenerateConfig; }
    public int getQueueSize() {
        synchronized(packetQueue) {
            return packetQueue.size();
        }
    }

    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
        updateAllPortPositions();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        System system = (System) o;
        return id == system.id; // Systems are equal if their IDs are equal
    }

    @Override
    public int hashCode() { return Objects.hash(id); } // Hash based on ID for consistency

    @Override
    public String toString() {
        String typeStr = isReferenceSystem ? (hasOutputPorts() ? "Source" : "Sink") : "Node";
        int qSize;
        synchronized(packetQueue){ qSize = packetQueue.size(); }
        return "System{id=" + id + ", type=" + typeStr + ", pos=(" + x + "," + y + ")" +
                ", Q=" + qSize + "/" + QUEUE_CAPACITY +
                ", GenConf=" + packetsToGenerateConfig + ", GenRun=" + packetsGeneratedThisRun +
                '}';
    }
}