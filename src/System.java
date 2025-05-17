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
    private static final int INDICATOR_FLASH_MS = 250;
    private static int nextId = 0;
    private static final Random random = new Random();
    private final int id;
    private int x, y;
    private final boolean isReferenceSystem;
    private boolean indicatorOn = false;
    private final List<Port> inputPorts = Collections.synchronizedList(new ArrayList<>());
    private final List<Port> outputPorts = Collections.synchronizedList(new ArrayList<>());
    private final Queue<Packet> packetQueue = new LinkedList<>();
    private int packetsToGenerate = 0;
    private int packetsGenerated = 0;
    private int generationFrequencyMillis = 2000;
    private long lastGenerationTime = -1;
    private boolean firstGenerationDone = false;
    private final javax.swing.Timer indicatorTimer;

    public static class PredictedPacketInfo {
        public final int futurePacketId;
        public final NetworkEnums.PacketShape shape;
        public final long generationTimeMs;
        public final Port sourcePort;
        public PredictedPacketInfo(int futurePacketId, NetworkEnums.PacketShape shape, long generationTimeMs, Port sourcePort) {
            this.futurePacketId = futurePacketId;
            this.shape = Objects.requireNonNull(shape, "Predicted packet shape cannot be null");
            this.generationTimeMs = generationTimeMs;
            this.sourcePort = Objects.requireNonNull(sourcePort, "Predicted source port cannot be null");
        }
        @Override
        public String toString() {
            return "PredictedPacketInfo{" +
                    "futureId=" + futurePacketId +
                    ", shape=" + shape +
                    ", genTimeMs=" + generationTimeMs +
                    ", sourcePortId=" + (sourcePort != null ? sourcePort.getId() : "null") +
                    '}';
        }
    }

    public System(int x, int y, boolean isReference) {
        this.id = nextId++;
        this.x = x;
        this.y = y;
        this.isReferenceSystem = isReference;
        this.indicatorTimer = new javax.swing.Timer(INDICATOR_FLASH_MS, e -> indicatorOn = false);
        this.indicatorTimer.setRepeats(false);
    }

    public void addPort(NetworkEnums.PortType type, NetworkEnums.PortShape shape) {
        // Ensure no ANY ports are added if that's the new rule
        if (shape == NetworkEnums.PortShape.ANY) {
            throw new IllegalArgumentException("PortShape.ANY is not allowed in this configuration.");
        }
        Port newPort;
        int index = (type == NetworkEnums.PortType.INPUT) ? inputPorts.size() : outputPorts.size();
        if (type == NetworkEnums.PortType.INPUT) {
            synchronized(inputPorts) {
                newPort = new Port(this, type, shape, index);
                inputPorts.add(newPort);
            }
        } else {
            synchronized(outputPorts) {
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

    public void receivePacket(Packet packet, GamePanel gamePanel) {
        if (packet == null || gamePanel == null) return;
        flashIndicator();
        packet.setCurrentSystem(this);
        gamePanel.addRoutingCoins(packet);

        if (isReferenceSystem && !hasOutputPorts()) {
            gamePanel.packetSuccessfullyDelivered(packet);
        } else if (!isReferenceSystem) {
            processOrQueuePacket(packet, gamePanel);
        } else {
            java.lang.System.err.println("ERROR: Source System " + id + " received a packet unexpectedly! Packet " + packet.getId() + " LOST.");
            gamePanel.packetLost(packet);
        }
    }

    private void processOrQueuePacket(Packet packet, GamePanel gamePanel) {
        Port outputPort = findAvailableOutputPort(packet, gamePanel);
        if (outputPort != null) { // A path is found (either compatible or non-compatible but empty)
            Wire outputWire = gamePanel.findWireFromPort(outputPort);
            if (outputWire != null) {
                NetworkEnums.PortShape packetRequiredPortShape = Port.getShapeEnum(packet.getShape());
                boolean compatibleExit = (packetRequiredPortShape != null &&
                        outputPort.getShape() == packetRequiredPortShape); // ANY is removed
                packet.setWire(outputWire, compatibleExit);
            } else { // Should not happen if findAvailableOutputPort works correctly with gamePanel.isWireOccupied
                java.lang.System.err.println("CRITICAL ERROR: System " + id + ": Output port " + outputPort + " reported available but GamePanel found no wire! Pkt " + packet.getId() + " LOST (queued instead).");
                // Fallback to queueing if wire somehow disappears
                queuePacket(packet, gamePanel);
            }
        } else { // No empty output path found, queue the packet
            queuePacket(packet, gamePanel);
        }
    }

    private void queuePacket(Packet packet, GamePanel gamePanel) {
        synchronized (packetQueue) {
            if (packetQueue.size() < QUEUE_CAPACITY) {
                packetQueue.offer(packet);
            } else {
                java.lang.System.out.println("System " + id + ": Queue full! Pkt " + packet.getId() + " LOST.");
                gamePanel.packetLost(packet);
            }
        }
    }


    public void processQueue(GamePanel gamePanel) {
        if (isReferenceSystem) return; // Only Nodes process queues

        Packet packetToProcess = null;
        synchronized (packetQueue) {
            if (!packetQueue.isEmpty()) {
                packetToProcess = packetQueue.peek();
            }
        }

        if (packetToProcess == null) return;

        if (packetToProcess.isMarkedForRemoval()) {
            synchronized (packetQueue) {
                packetQueue.poll(); // Remove already marked packet
            }
            return;
        }

        Port outputPort = findAvailableOutputPort(packetToProcess, gamePanel);
        if (outputPort != null) { // An output path is available
            Wire outputWire = gamePanel.findWireFromPort(outputPort);
            if (outputWire != null) {
                Packet sentPacket = null;
                synchronized (packetQueue) { // Ensure the packet is still the one we planned to send
                    if (!packetQueue.isEmpty() && packetQueue.peek().equals(packetToProcess)) {
                        sentPacket = packetQueue.poll();
                    }
                }

                if (sentPacket != null) {
                    NetworkEnums.PortShape packetRequiredPortShape = Port.getShapeEnum(sentPacket.getShape());
                    boolean compatibleExit = (packetRequiredPortShape != null &&
                            outputPort.getShape() == packetRequiredPortShape);
                    sentPacket.setWire(outputWire, compatibleExit);
                    flashIndicator();
                } else {
                    // Packet was removed or changed between peek and poll, or queue became empty.
                    // The next tick will re-evaluate.
                    java.lang.System.err.println("System " + id + " queue processing: Packet " + packetToProcess.getId() + " changed/removed before send.");
                }
            } else {
                // This case should be rare if findAvailableOutputPort is accurate.
                java.lang.System.err.println("CRITICAL ERROR: System " + id + ": Queue processing found available port " + outputPort + " but GamePanel has no wire! Pkt " + packetToProcess.getId() + " remains queued.");
            }
        }
        // If outputPort is null, packet remains in queue for next tick.
    }

    private Port findAvailableOutputPort(Packet packet, GamePanel gamePanel) {
        if (packet == null || gamePanel == null) return null;

        List<Port> compatibleEmptyPorts = new ArrayList<>();
        List<Port> nonCompatibleEmptyPorts = new ArrayList<>();

        NetworkEnums.PortShape requiredPacketShape = Port.getShapeEnum(packet.getShape());
        if (requiredPacketShape == null) {
            java.lang.System.err.println("System " + id + ": Cannot determine required port shape for Packet " + packet.getId());
            return null; // Cannot route if packet shape is unknown
        }

        synchronized(outputPorts) {
            List<Port> shuffledPorts = new ArrayList<>(outputPorts);
            Collections.shuffle(shuffledPorts, random); // Randomize to distribute load

            for (Port port : shuffledPorts) {
                if (port != null && port.isConnected()) {
                    Wire wire = gamePanel.findWireFromPort(port);
                    // Check if wire exists and is NOT occupied
                    if (wire != null && !gamePanel.isWireOccupied(wire)) {
                        if (port.getShape() == requiredPacketShape) {
                            compatibleEmptyPorts.add(port);
                        } else {
                            // Since ANY ports are removed, all other shapes are non-compatible
                            nonCompatibleEmptyPorts.add(port);
                        }
                    }
                }
            }
        }

        // Priority 1: Compatible and empty ports
        if (!compatibleEmptyPorts.isEmpty()) {
            return compatibleEmptyPorts.get(0); // Return the first (randomized) compatible empty port
        }

        // Priority 2: Non-compatible but empty ports
        if (!nonCompatibleEmptyPorts.isEmpty()) {
            return nonCompatibleEmptyPorts.get(0); // Return the first (randomized) non-compatible empty port
        }

        return null; // No empty output port (neither compatible nor non-compatible) found
    }


    public void configureGenerator(int totalPackets, int frequencyMs) {
        if (isReferenceSystem && hasOutputPorts()) {
            this.packetsToGenerate = Math.max(0, totalPackets);
            this.generationFrequencyMillis = Math.max(200, frequencyMs);
            this.packetsGenerated = 0;
            this.lastGenerationTime = -1; // Reset for the new configuration
            this.firstGenerationDone = false;
            java.lang.System.out.println("SOURCE System " + id + " configured: Generate " + this.packetsToGenerate + " packets, frequency=" + this.generationFrequencyMillis + "ms");
        } else {
            java.lang.System.err.println("Warning: Cannot configure generator for " + (isReferenceSystem ? "Sink" : "non-reference") + " System " + id);
            this.packetsToGenerate = 0;
        }
    }

    public void attemptPacketGeneration(GamePanel gamePanel, long currentTimeMillis) {
        if (!isReferenceSystem || !hasOutputPorts() || packetsGenerated >= packetsToGenerate || packetsToGenerate <= 0) {
            return;
        }

        if (lastGenerationTime == -1) { // First packet generation attempt for this configuration
            lastGenerationTime = currentTimeMillis; // Set initial time
            // For the very first packet, allow it to generate immediately if conditions met below.
            // For subsequent packets, the frequency check will apply.
        } else if (currentTimeMillis - lastGenerationTime < generationFrequencyMillis) {
            return; // Not time yet for the next packet
        }


        List<Port> availablePorts = new ArrayList<>();
        synchronized(outputPorts) {
            for (Port port : outputPorts) {
                if (port != null && port.isConnected()) {
                    Wire wire = gamePanel.findWireFromPort(port);
                    if (wire != null && !gamePanel.isWireOccupied(wire)) {
                        availablePorts.add(port);
                    }
                }
            }
        }

        if (!availablePorts.isEmpty()) {
            lastGenerationTime = currentTimeMillis; // Update time *before* successful generation
            Collections.shuffle(availablePorts, random);
            Port chosenPort = availablePorts.get(0);
            Wire outputWire = gamePanel.findWireFromPort(chosenPort); // Re-fetch, should be same
            NetworkEnums.PacketShape shapeToGenerate = getPacketShapeFromPortShapeStatic(chosenPort.getShape());

            if (shapeToGenerate != null && outputWire != null) {
                Packet newPacket = new Packet(shapeToGenerate, chosenPort.getX(), chosenPort.getY());
                NetworkEnums.PortShape packetRequiredPortShape = Port.getShapeEnum(newPacket.getShape());
                // Compatibility check for speed setting (ANY ports removed, so direct match needed for compatible speed)
                boolean compatibleExit = (packetRequiredPortShape != null &&
                        chosenPort.getShape() == packetRequiredPortShape);
                newPacket.setWire(outputWire, compatibleExit);
                gamePanel.addPacket(newPacket);
                packetsGenerated++;
                flashIndicator();
            } else {
                // This case implies an issue like port shape not mapping or wire disappearing,
                // which should be rare. If it occurs, we don't update lastGenerationTime,
                // allowing another attempt sooner on the next tick.
                java.lang.System.err.println("System " + id + " generation failed post-check: Shape " + chosenPort.getShape() + " or wire issue. Retrying.");
            }
        }
        // If no available ports, lastGenerationTime is not updated, so it will try again next suitable tick.
    }


    public List<PredictedPacketInfo> predictGeneratedPackets(long upToTimeMs) {
        List<PredictedPacketInfo> predicted = new ArrayList<>();
        if (!isReferenceSystem || !hasOutputPorts() || packetsToGenerate <= 0 || generationFrequencyMillis <= 0) {
            return predicted;
        }

        List<Port> connectedOutputPorts = new ArrayList<>();
        synchronized(outputPorts) {
            for (Port p : outputPorts) {
                if (p != null && p.isConnected()) {
                    connectedOutputPorts.add(p);
                }
            }
        }
        if (connectedOutputPorts.isEmpty()) {
            return predicted;
        }

        long currentGenTime = generationFrequencyMillis;
        int predictedPacketIndex = 0;

        while (predictedPacketIndex < packetsToGenerate && currentGenTime <= upToTimeMs) {
            Port chosenPort = connectedOutputPorts.get(predictedPacketIndex % connectedOutputPorts.size());
            NetworkEnums.PacketShape shapeToGenerate = getPacketShapeFromPortShapeStatic(chosenPort.getShape());

            if (shapeToGenerate != null) {
                int futurePacketId = -1000 - predictedPacketIndex;
                predicted.add(new PredictedPacketInfo(futurePacketId, shapeToGenerate, currentGenTime, chosenPort));
            } else {
                java.lang.System.err.println("Prediction Error: System " + id + " couldn't determine packet shape for predicted generation from port " + chosenPort.getShape());
            }
            predictedPacketIndex++;
            currentGenTime += generationFrequencyMillis;
        }
        return predicted;
    }


    public void draw(Graphics2D g2d) {
        Color bodyColor = isReferenceSystem ? new Color(90, 90, 90) : new Color(60, 80, 130);
        g2d.setColor(bodyColor);
        g2d.fillRect(x, y, SYSTEM_WIDTH, SYSTEM_HEIGHT);
        g2d.setColor(Color.LIGHT_GRAY);
        g2d.setStroke(new BasicStroke(1));
        g2d.drawRect(x, y, SYSTEM_WIDTH, SYSTEM_HEIGHT);
        int indicatorSize = 8;
        int indicatorX = x + SYSTEM_WIDTH / 2 - indicatorSize / 2;
        int indicatorY = y - indicatorSize - 3;
        g2d.setColor(indicatorOn ? Color.GREEN.brighter() : new Color(0, 100, 0));
        g2d.fillOval(indicatorX, indicatorY, indicatorSize, indicatorSize);
        g2d.setColor(Color.DARK_GRAY);
        g2d.drawOval(indicatorX, indicatorY, indicatorSize, indicatorSize);
        synchronized(inputPorts) { for (Port p : inputPorts) if(p!=null) p.draw(g2d); }
        synchronized(outputPorts) { for (Port p : outputPorts) if(p!=null) p.draw(g2d); }
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

    private void flashIndicator() {
        if (!indicatorTimer.isRunning()) {
            indicatorOn = true;
        }
        indicatorTimer.restart();
    }

    // Removed PortShape.ANY logic as it's no longer used for port creation
    public static NetworkEnums.PacketShape getPacketShapeFromPortShapeStatic(NetworkEnums.PortShape portShape) {
        if (portShape == null) return null;
        switch (portShape) {
            case SQUARE:   return NetworkEnums.PacketShape.SQUARE;
            case TRIANGLE: return NetworkEnums.PacketShape.TRIANGLE;
            // case ANY: Should not happen if ANY ports are disallowed.
            //    return random.nextBoolean() ? NetworkEnums.PacketShape.SQUARE : NetworkEnums.PacketShape.TRIANGLE;
            default:
                java.lang.System.err.println("Warning: Unknown PortShape encountered in getPacketShapeFromPortShapeStatic: " + portShape);
                return null; // Or throw an exception
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
    public Point getPosition() { return new Point(x + SYSTEM_WIDTH / 2, y + SYSTEM_HEIGHT / 2); }
    public int getPacketsGeneratedCount() { return packetsGenerated; }
    public int getTotalPacketsToGenerate() { return packetsToGenerate; }
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
                ", Q=" + qSize + "/" + QUEUE_CAPACITY + ", Gen=" + packetsGenerated + "/" + packetsToGenerate + '}';
    }
}