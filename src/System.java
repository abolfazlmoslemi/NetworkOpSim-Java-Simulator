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
            boolean shapeMatch = false;
            NetworkEnums.PortShape requiredPortShape = Port.getShapeEnum(packet.getShape());
            if (requiredPortShape != null) {
                synchronized(inputPorts) {
                    for (Port p : inputPorts) {
                        
                        if (p != null && (p.getShape() == requiredPortShape)) {
                            shapeMatch = true;
                            break;
                        }
                    }
                }
            } else {
                java.lang.System.err.println("Sink " + id + ": Could not determine required port shape for Packet " + packet.getId() + " shape (" + packet.getShape() + ")");
            }
            if (shapeMatch) {
                gamePanel.packetSuccessfullyDelivered(packet);
            } else {
                java.lang.System.out.println("Sink " + id + ": Packet " + packet.getId() + " (" + packet.getShape() + ") rejected due to shape mismatch. LOST.");
                gamePanel.packetLost(packet);
            }
        } else if (!isReferenceSystem) { 
            processOrQueuePacket(packet, gamePanel);
        } else { 
            java.lang.System.err.println("ERROR: Source System " + id + " received a packet unexpectedly! Packet " + packet.getId() + " LOST.");
            gamePanel.packetLost(packet);
        }
    }

    
    private void processOrQueuePacket(Packet packet, GamePanel gamePanel) {
        Port outputPort = findAvailableOutputPort(packet, gamePanel); 
        if (outputPort != null) {
            Wire outputWire = gamePanel.findWireFromPort(outputPort);
            if (outputWire != null) {
                NetworkEnums.PortShape requiredPortShape = Port.getShapeEnum(packet.getShape());
                boolean compatibleExit = (requiredPortShape != null && outputPort.getShape() == requiredPortShape);
                packet.setWire(outputWire, compatibleExit); 
            } else {
                java.lang.System.err.println("CRITICAL ERROR: System " + id + ": Output port " + outputPort + " reported available but GamePanel found no wire! Pkt " + packet.getId() + " LOST.");
                gamePanel.packetLost(packet);
            }
        } else {
            
            synchronized (packetQueue) {
                if (packetQueue.size() < QUEUE_CAPACITY) {
                    packetQueue.offer(packet);
                } else {
                    java.lang.System.out.println("System " + id + ": Queue full! Pkt " + packet.getId() + " LOST.");
                    gamePanel.packetLost(packet);
                }
            }
        }
    }

    
    public void processQueue(GamePanel gamePanel) {
        if (!isReferenceSystem) { 
            Packet packet = null;
            synchronized (packetQueue) { 
                if (!packetQueue.isEmpty()) {
                    packet = packetQueue.peek();
                }
            }
            if (packet == null) return; 

            if (packet.isMarkedForRemoval()){ 
                synchronized(packetQueue){ packetQueue.poll(); }
                return;
            }

            Port outputPort = findAvailableOutputPort(packet, gamePanel); 
            if (outputPort != null) {
                Wire outputWire = gamePanel.findWireFromPort(outputPort);
                if (outputWire != null) {
                    NetworkEnums.PortShape requiredPortShape = Port.getShapeEnum(packet.getShape());
                    boolean compatibleExit = (requiredPortShape != null && outputPort.getShape() == requiredPortShape);
                    Packet sentPacket = null;
                    synchronized(packetQueue){ 
                        if (!packetQueue.isEmpty() && packetQueue.peek().equals(packet)) {
                            sentPacket = packetQueue.poll();
                        }
                    }
                    if (sentPacket != null) { 
                        sentPacket.setWire(outputWire, compatibleExit);
                        flashIndicator();
                    } else {
                        
                        java.lang.System.err.println("System " + id + " queue processing: Packet mismatch or queue became empty between peek and poll. Pkt "+ packet.getId() +" remains.");
                    }
                } else {
                    
                    java.lang.System.err.println("CRITICAL ERROR: System " + id + ": Queue processing found available port " + outputPort + " but GamePanel has no wire! Pkt " + packet.getId() + " remains queued.");
                }
            }
            
        }
    }

    
    private Port findAvailableOutputPort(Packet packet, GamePanel gamePanel) {
        if (packet == null || gamePanel == null) return null;
        List<Port> compatibleEmptyPorts = new ArrayList<>();
        List<Port> otherEmptyPorts = new ArrayList<>(); 
        NetworkEnums.PortShape requiredPortShape = Port.getShapeEnum(packet.getShape());
        if (requiredPortShape == null) {
            java.lang.System.err.println("System " + id + ": Cannot find output port for packet " + packet.getId() + ", unknown target PortShape for PacketShape " + packet.getShape());
            return null;
        }

        
        synchronized(outputPorts) {
            List<Port> shuffledPorts = new ArrayList<>(outputPorts); 
            Collections.shuffle(shuffledPorts, random); 

            for (Port port : shuffledPorts) {
                if (port != null && port.isConnected()) {
                    Wire wire = gamePanel.findWireFromPort(port);
                    
                    if (wire != null && !gamePanel.isWireOccupied(wire)) {
                        
                        if (port.getShape() == requiredPortShape) {
                            compatibleEmptyPorts.add(port);
                        }
                        
                        else {
                            otherEmptyPorts.add(port);
                        }
                    }
                }
            }
        } 

        
        if (!compatibleEmptyPorts.isEmpty()) {
            return compatibleEmptyPorts.get(0); 
        }
        if (!otherEmptyPorts.isEmpty()) {
            return otherEmptyPorts.get(0); 
        }
        return null; 
    }

    
    public void configureGenerator(int totalPackets, int frequencyMs) {
        if (isReferenceSystem && hasOutputPorts()) { 
            this.packetsToGenerate = Math.max(0, totalPackets);
            this.generationFrequencyMillis = Math.max(200, frequencyMs); 
            this.packetsGenerated = 0;
            this.lastGenerationTime = -1; 
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
        
        if (!firstGenerationDone) {
            if (lastGenerationTime == -1) lastGenerationTime = currentTimeMillis;
            if (currentTimeMillis - lastGenerationTime < generationFrequencyMillis) return;
            firstGenerationDone = true;
        } else {
            if (currentTimeMillis - lastGenerationTime < generationFrequencyMillis) return;
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
            lastGenerationTime = currentTimeMillis; 
            Collections.shuffle(availablePorts, random); 
            Port chosenPort = availablePorts.get(0);
            Wire outputWire = gamePanel.findWireFromPort(chosenPort); 
            NetworkEnums.PacketShape shapeToGenerate = getPacketShapeFromPortShapeStatic(chosenPort.getShape());

            if (shapeToGenerate != null && outputWire != null) {
                Packet newPacket = new Packet(shapeToGenerate, chosenPort.getX(), chosenPort.getY());
                NetworkEnums.PortShape requiredPortShape = Port.getShapeEnum(newPacket.getShape());
                boolean compatibleExit = (requiredPortShape != null && chosenPort.getShape() == requiredPortShape);
                newPacket.setWire(outputWire, compatibleExit); 
                gamePanel.addPacket(newPacket);             
                packetsGenerated++;
                flashIndicator();
                
                
            } else {
                
                java.lang.System.err.println("System " + id + " generation failed: Cannot map port shape " + chosenPort.getShape() + " or wire missing after check. Retrying next tick.");
                lastGenerationTime = -1; 
            }
        }
        
    }

    
    public List<PredictedPacketInfo> predictGeneratedPackets(long upToTimeMs) {
        List<PredictedPacketInfo> predicted = new ArrayList<>();
        if (!isReferenceSystem || !hasOutputPorts() || packetsToGenerate <= 0 || generationFrequencyMillis <= 0) {
            return predicted; 
        }

        long currentGenTime = generationFrequencyMillis; 
        int predictedPacketIndex = 0; 

        while (predictedPacketIndex < packetsToGenerate && currentGenTime <= upToTimeMs) {
            
            List<Port> connectedPorts = new ArrayList<>();
            synchronized(outputPorts) {
                
                for (Port p : outputPorts) {
                    if (p != null && p.isConnected()) {
                        connectedPorts.add(p);
                    }
                }
            }

            
            if (connectedPorts.isEmpty()) {
                break;
            }

            
            
            Port chosenPort = connectedPorts.get(predictedPacketIndex % connectedPorts.size());
            NetworkEnums.PacketShape shapeToGenerate = getPacketShapeFromPortShapeStatic(chosenPort.getShape());
            

            if (shapeToGenerate != null) {
                
                int futurePacketId = -1 - predictedPacketIndex; 
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

    
    public static NetworkEnums.PacketShape getPacketShapeFromPortShapeStatic(NetworkEnums.PortShape portShape) {
        if (portShape == null) return null;
        switch (portShape) {
            case SQUARE:   return NetworkEnums.PacketShape.SQUARE;
            case TRIANGLE: return NetworkEnums.PacketShape.TRIANGLE;
            case ANY:
                
                return random.nextBoolean() ? NetworkEnums.PacketShape.SQUARE : NetworkEnums.PacketShape.TRIANGLE;
            default:
                java.lang.System.err.println("Warning: Unknown PortShape encountered: " + portShape);
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
