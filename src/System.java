// FILE: System.java
// ===== System.java =====
// FILE: System.java
import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Objects;
import java.util.Random;

public class System {
    public static final int SYSTEM_WIDTH = 80;
    public static final int SYSTEM_HEIGHT = 60;
    private static final int QUEUE_CAPACITY = 5;
    private static int nextId = 0;
    private static Random globalRandom = new Random();

    private final int id;
    private int x, y;
    private final boolean isReferenceSystem;
    private boolean indicatorOn = false;
    private final List<Port> inputPorts = Collections.synchronizedList(new ArrayList<>());
    private final List<Port> outputPorts = Collections.synchronizedList(new ArrayList<>());
    public final Queue<Packet> packetQueue = new LinkedList<>();

    private int packetsToGenerateConfig = 0;
    private int packetsGeneratedThisRun = 0;
    private int generationFrequencyMillisConfig = 2000;
    private long lastGenerationTimeThisRun = -1;


    public static void resetGlobalRandomSeed(long seed) {
        globalRandom = new Random(seed);
        // java.lang.System.out.println("System.globalRandom seeded with: " + seed);
    }

    public static Random getGlobalRandom() { // Added for Packet to access for deterministic choices if needed
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
        resetForNewRun();
    }

    public void resetForNewRun() {
        synchronized (packetQueue) {
            packetQueue.clear();
        }
        this.packetsGeneratedThisRun = 0;
        this.lastGenerationTimeThisRun = -1; // Reset to allow first packet generation immediately if conditions met
    }

    public void addPort(NetworkEnums.PortType type, NetworkEnums.PortShape shape) {
        if (shape == NetworkEnums.PortShape.ANY) {
            throw new IllegalArgumentException("PortShape.ANY is not allowed in this configuration.");
        }
        Port newPort;
        int index;
        if (type == NetworkEnums.PortType.INPUT) {
            synchronized(inputPorts) {
                index = inputPorts.size();
                newPort = new Port(this, type, shape, index);
                inputPorts.add(newPort);
            }
        } else {
            synchronized(outputPorts) {
                index = outputPorts.size();
                newPort = new Port(this, type, shape, index);
                outputPorts.add(newPort);
            }
        }
        updateAllPortPositions();
    }

    public void updateAllPortPositions() {
        int totalInput;
        int totalOutput;
        synchronized (inputPorts) { totalInput = inputPorts.size(); }
        synchronized (outputPorts) { totalOutput = outputPorts.size(); }
        synchronized (inputPorts) {
            for (int i = 0; i < inputPorts.size(); i++) {
                Port p = inputPorts.get(i);
                if (p != null) {
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
        synchronized (outputPorts) {
            for (Port port : outputPorts) {
                if (port != null && port.contains(p)) return port;
            }
        }
        synchronized (inputPorts) {
            for (Port port : inputPorts) {
                if (port != null && port.contains(p)) return port;
            }
        }
        return null;
    }

    public void receivePacket(Packet packet, GamePanel gamePanel, boolean isPredictionRun) {
        if (packet == null || gamePanel == null) return;
        packet.setCurrentSystem(this);
        gamePanel.addRoutingCoinsInternal(packet, isPredictionRun);

        if (isReferenceSystem && !hasOutputPorts()) { // This is a Sink system
            packet.setFinalStatusForPrediction(PredictedPacketStatus.DELIVERED);
            gamePanel.packetSuccessfullyDeliveredInternal(packet, isPredictionRun);
        } else if (!isReferenceSystem) { // This is a regular Node system
            processOrQueuePacket(packet, gamePanel, isPredictionRun);
        } else { // This is a Source system, it should not receive packets
            // java.lang.System.err.println("ERROR: Source System " + id + " received a packet unexpectedly! Packet " + packet.getId() + " LOST.");
            packet.setFinalStatusForPrediction(PredictedPacketStatus.LOST);
            gamePanel.packetLostInternal(packet, isPredictionRun);
        }
    }

    private void processOrQueuePacket(Packet packet, GamePanel gamePanel, boolean isPredictionRun) {
        Port outputPort = findAvailableOutputPort(packet, gamePanel, isPredictionRun);
        if (outputPort != null) {
            Wire outputWire = gamePanel.findWireFromPort(outputPort);
            if (outputWire != null) {
                NetworkEnums.PortShape packetRequiredPortShape = Port.getShapeEnum(packet.getShape());
                boolean compatibleExit = (packetRequiredPortShape != null &&
                        outputPort.getShape() == packetRequiredPortShape);
                packet.setWire(outputWire, compatibleExit);
                // Packet is now on wire, no longer QUEUED for prediction purposes if it was
                if (packet.getFinalStatusForPrediction() == PredictedPacketStatus.QUEUED) {
                    packet.setFinalStatusForPrediction(null); // Let its state be ON_WIRE
                }
            } else {
                // This case should be rare if findAvailableOutputPort implies a connectable wire
                // java.lang.System.err.println("CRITICAL ERROR: System " + id + ": Output port " + outputPort + " reported available but GamePanel found no wire! Pkt " + packet.getId() + " (fallback to queue).");
                packet.setFinalStatusForPrediction(PredictedPacketStatus.STALLED_AT_NODE); // Or QUEUED
                queuePacket(packet, gamePanel, isPredictionRun);
            }
        } else {
            // No output port available, so it must be queued or if queue full, potentially lost (handled by queuePacket)
            packet.setFinalStatusForPrediction(PredictedPacketStatus.QUEUED);
            queuePacket(packet, gamePanel, isPredictionRun);
        }
    }

    private void queuePacket(Packet packet, GamePanel gamePanel, boolean isPredictionRun) {
        synchronized (packetQueue) {
            if (packetQueue.size() < QUEUE_CAPACITY) {
                packetQueue.offer(packet);
                // If it's successfully queued, its status for prediction is QUEUED.
                // This is often set before calling queuePacket, but reiterated here for clarity.
                packet.setFinalStatusForPrediction(PredictedPacketStatus.QUEUED);
            } else {
                // java.lang.System.out.println("System " + id + ": Queue full! Pkt " + packet.getId() + " LOST.");
                packet.setFinalStatusForPrediction(PredictedPacketStatus.LOST); // Lost due to full queue
                gamePanel.packetLostInternal(packet, isPredictionRun);
            }
        }
    }

    public void processQueue(GamePanel gamePanel, boolean isPredictionRun) {
        if (isReferenceSystem) return; // Reference systems (Sources/Sinks) don't process queues this way

        Packet packetToProcess = null;
        synchronized (packetQueue) {
            if (!packetQueue.isEmpty()) {
                packetToProcess = packetQueue.peek();
            }
        }

        if (packetToProcess == null) return;

        if (packetToProcess.isMarkedForRemoval()) { // Should not happen if logic is correct elsewhere
            synchronized (packetQueue) {
                packetQueue.poll(); // Remove it if it's marked
            }
            return;
        }

        Port outputPort = findAvailableOutputPort(packetToProcess, gamePanel, isPredictionRun);
        if (outputPort != null) {
            Wire outputWire = gamePanel.findWireFromPort(outputPort);
            if (outputWire != null) {
                Packet sentPacket = null;
                synchronized (packetQueue) {
                    // Double check it's still the same packet at the head of the queue
                    if (!packetQueue.isEmpty() && packetQueue.peek().equals(packetToProcess)) {
                        sentPacket = packetQueue.poll(); // Successfully dequeued
                    }
                }

                if (sentPacket != null) {
                    NetworkEnums.PortShape packetRequiredPortShape = Port.getShapeEnum(sentPacket.getShape());
                    boolean compatibleExit = (packetRequiredPortShape != null &&
                            outputPort.getShape() == packetRequiredPortShape);
                    sentPacket.setWire(outputWire, compatibleExit);
                    // Packet is now on wire, clear any QUEUED prediction status
                    if (sentPacket.getFinalStatusForPrediction() == PredictedPacketStatus.QUEUED) {
                        sentPacket.setFinalStatusForPrediction(null);
                    }
                }
            } else {
                // Output port was found, but no wire. Packet remains queued, status is QUEUED.
                if(packetToProcess.getFinalStatusForPrediction() != PredictedPacketStatus.LOST) {
                    packetToProcess.setFinalStatusForPrediction(PredictedPacketStatus.QUEUED);
                }
            }
        } else {
            // No output port available, packet remains queued. Ensure its status reflects this.
            if(packetToProcess.getFinalStatusForPrediction() != PredictedPacketStatus.LOST) {
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
            // java.lang.System.err.println("System " + id + ": Cannot determine required port shape for Packet " + packet.getId());
            return null; // Cannot find a port if packet shape is unknown
        }

        synchronized(outputPorts) {
            List<Port> shuffledPorts = new ArrayList<>(outputPorts);
            // Use the globalRandom for deterministic shuffling during prediction
            Collections.shuffle(shuffledPorts, globalRandom);


            for (Port port : shuffledPorts) {
                if (port != null && port.isConnected()) { // Port must be connected to a wire
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
        }

        if (!compatibleEmptyPorts.isEmpty()) {
            return compatibleEmptyPorts.get(0); // Prefer compatible, empty ports
        }
        if (!nonCompatibleEmptyPorts.isEmpty()) {
            return nonCompatibleEmptyPorts.get(0); // Fallback to non-compatible, empty ports
        }
        return null; // No suitable port found
    }

    public void configureGenerator(int totalPackets, int frequencyMs) {
        if (isReferenceSystem && hasOutputPorts()) { // Only Source systems can generate
            this.packetsToGenerateConfig = Math.max(0, totalPackets);
            this.generationFrequencyMillisConfig = Math.max(100, frequencyMs); // Min frequency
            // java.lang.System.out.println("SOURCE System " + id + " configured: Generate " + this.packetsToGenerateConfig + " packets, frequency=" + this.generationFrequencyMillisConfig + "ms");
        } else {
            this.packetsToGenerateConfig = 0; // Non-sources or sinks cannot generate
        }
    }

    public void attemptPacketGeneration(GamePanel gamePanel, long currentSimTimeMs, boolean isPredictionRun) {
        // Pre-conditions for generation
        if (!isReferenceSystem || !hasOutputPorts() || packetsGeneratedThisRun >= packetsToGenerateConfig || packetsToGenerateConfig <= 0) {
            return;
        }

        // Initialize lastGenerationTime for the first packet or if reset
        if (lastGenerationTimeThisRun == -1) {
            // To allow the first packet to generate at or near time 0 if conditions are met
            lastGenerationTimeThisRun = currentSimTimeMs - generationFrequencyMillisConfig;
        }

        // Check if enough time has passed since the last generation
        if (currentSimTimeMs - lastGenerationTimeThisRun < generationFrequencyMillisConfig) {
            return;
        }

        // Find available output ports
        List<Port> availablePorts = new ArrayList<>();
        synchronized(outputPorts) {
            for (Port port : outputPorts) {
                if (port != null && port.isConnected()) {
                    Wire wire = gamePanel.findWireFromPort(port);
                    // Check if wire exists and is not occupied
                    if (wire != null && !gamePanel.isWireOccupied(wire, isPredictionRun)) {
                        availablePorts.add(port);
                    }
                }
            }
        }

        if (!availablePorts.isEmpty()) {
            lastGenerationTimeThisRun = currentSimTimeMs; // Update last generation time *before* attempting to send

            // Shuffle available ports for potentially random selection (if multiple are suitable)
            // Uses the globalRandom for deterministic behavior in prediction runs
            Collections.shuffle(availablePorts, globalRandom);
            Port chosenPort = availablePorts.get(0); // Pick the first one after shuffling

            Wire outputWire = gamePanel.findWireFromPort(chosenPort); // Should exist based on above check
            NetworkEnums.PacketShape shapeToGenerate = getPacketShapeFromPortShapeStatic(chosenPort.getShape());

            if (shapeToGenerate != null && outputWire != null) {
                Packet newPacket = new Packet(shapeToGenerate, chosenPort.getX(), chosenPort.getY());
                NetworkEnums.PortShape packetRequiredPortShape = Port.getShapeEnum(newPacket.getShape());
                boolean compatibleExit = (packetRequiredPortShape != null &&
                        chosenPort.getShape() == packetRequiredPortShape);

                newPacket.setWire(outputWire, compatibleExit);
                gamePanel.addPacketInternal(newPacket, isPredictionRun); // Add to game
                packetsGeneratedThisRun++;
            } else {
                // If packet couldn't be created (e.g. shape mismatch, though getPacketShapeFromPortShapeStatic should handle)
                // or wire somehow disappeared, slightly rewind lastGenerationTime to allow re-attempt soon.
                lastGenerationTimeThisRun = currentSimTimeMs - generationFrequencyMillisConfig + 1; // Allow retry sooner
            }
        }
        // If no available ports, do nothing, wait for next opportunity
    }

    private boolean areAllMyPortsConnected() {
        synchronized (inputPorts) {
            for (Port p : inputPorts) {
                if (p != null && !p.isConnected()) {
                    return false;
                }
            }
        }
        synchronized (outputPorts) {
            for (Port p : outputPorts) {
                if (p != null && !p.isConnected()) {
                    return false;
                }
            }
        }
        return true;
    }

    public void draw(Graphics2D g2d) {
        this.indicatorOn = areAllMyPortsConnected(); // Update indicator status based on port connectivity
        Color bodyColor = isReferenceSystem ? new Color(90, 90, 90) : new Color(60, 80, 130);
        g2d.setColor(bodyColor);
        g2d.fillRect(x, y, SYSTEM_WIDTH, SYSTEM_HEIGHT);
        g2d.setColor(Color.LIGHT_GRAY);
        g2d.setStroke(new BasicStroke(1));
        g2d.drawRect(x, y, SYSTEM_WIDTH, SYSTEM_HEIGHT);

        // Draw connection indicator
        int indicatorSize = 8;
        int indicatorX = x + SYSTEM_WIDTH / 2 - indicatorSize / 2;
        int indicatorY = y - indicatorSize - 3; // Position above the system
        g2d.setColor(indicatorOn ? Color.GREEN.brighter() : new Color(100, 0, 0)); // Green if all connected, Red otherwise
        g2d.fillOval(indicatorX, indicatorY, indicatorSize, indicatorSize);
        g2d.setColor(Color.DARK_GRAY);
        g2d.drawOval(indicatorX, indicatorY, indicatorSize, indicatorSize);

        // Draw ports
        synchronized(inputPorts) { for (Port p : inputPorts) if(p!=null) p.draw(g2d); }
        synchronized(outputPorts) { for (Port p : outputPorts) if(p!=null) p.draw(g2d); }

        // Draw queue size if it's a Node and queue is not empty
        int currentQueueSize;
        synchronized (packetQueue) { currentQueueSize = packetQueue.size(); }
        if (!isReferenceSystem && currentQueueSize > 0) {
            g2d.setColor(Color.ORANGE);
            g2d.setFont(new Font("Arial", Font.BOLD, 11));
            String queueText = "Q:" + currentQueueSize;
            FontMetrics fm = g2d.getFontMetrics();
            int textWidth = fm.stringWidth(queueText);
            int textX = x + SYSTEM_WIDTH - textWidth - 5; // Bottom-right corner
            int textY = y + SYSTEM_HEIGHT - 5;
            g2d.drawString(queueText, textX, textY);
        }
    }


    public static NetworkEnums.PacketShape getPacketShapeFromPortShapeStatic(NetworkEnums.PortShape portShape) {
        if (portShape == null) return null;
        switch (portShape) {
            case SQUARE:   return NetworkEnums.PacketShape.SQUARE;
            case TRIANGLE: return NetworkEnums.PacketShape.TRIANGLE;
            //ANY shape ports cannot determine a specific packet shape to generate.
            //This should be handled by game logic (e.g. not allowing generation from ANY port).
            case ANY:
            default:
                // java.lang.System.err.println("Warning: Unknown or ANY PortShape encountered in getPacketShapeFromPortShapeStatic: " + portShape);
                return null;
        }
    }

    public int getId() { return id; }
    public int getX() { return x; }
    public int getY() { return y; }
    public List<Port> getInputPorts() { synchronized(inputPorts){ return Collections.unmodifiableList(new ArrayList<>(inputPorts)); } }
    public List<Port> getOutputPorts() { synchronized(outputPorts){ return Collections.unmodifiableList(new ArrayList<>(outputPorts)); } }
    public boolean isReferenceSystem() { return isReferenceSystem; }
    public boolean hasOutputPorts() { synchronized(outputPorts){ return !outputPorts.isEmpty(); } }
    public boolean hasInputPorts() { synchronized(inputPorts){ return !inputPorts.isEmpty(); } }
    public Point getPosition() { return new Point(x + SYSTEM_WIDTH / 2, y + SYSTEM_HEIGHT / 2); } // Center of the system
    public int getPacketsGeneratedCount() { return packetsGeneratedThisRun; }
    public int getTotalPacketsToGenerate() { return packetsToGenerateConfig; }
    public int getQueueSize() { synchronized(packetQueue){ return packetQueue.size(); } }

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
        return id == system.id;
    }

    @Override
    public int hashCode() { return Objects.hash(id); }

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