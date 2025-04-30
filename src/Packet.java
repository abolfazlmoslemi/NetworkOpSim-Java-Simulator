import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Line2D; 
import java.util.Objects;
public class Packet {
    public static final double BASE_SPEED_MAGNITUDE = 1.8; 
    public static final int BASE_DRAW_SIZE = 8;           
    private static final double ACCELERATION_FACTOR = 0.04; 
    private static final double MAX_SPEED = 4.0;            
    private static final double IMPACT_FORCE_DAMPING = 0.85; 
    private static final double MAX_DEVIATION_DISTANCE = 8.0; 
    private static final double COLLISION_RADIUS_FACTOR = 0.7; 
    private static int nextId = 0; 
    private final int id;
    private final NetworkEnums.PacketShape shape; 
    private final int size;              
    private final int baseCoinValue;     
    private Point2D.Double position;     
    private Point2D.Double velocity;     
    private Wire currentWire = null;     
    private System currentSystem = null; 
    private double progressOnWire = 0.0; 
    private double noise = 0.0;          
    private boolean markedForRemoval = false; 
    public Packet(NetworkEnums.PacketShape shape, double startX, double startY) { 
        this.id = nextId++;
        this.shape = Objects.requireNonNull(shape, "Packet shape cannot be null");
        this.position = new Point2D.Double(startX, startY);
        this.velocity = new Point2D.Double(0, 0); 
        switch (shape) {
            case SQUARE:
                this.size = 2;
                this.baseCoinValue = 1;
                break;
            case TRIANGLE:
                this.size = 3;
                this.baseCoinValue = 2;
                break;
            default:
                throw new IllegalArgumentException("Unsupported PacketShape in constructor: " + shape);
        }
    }
    public void setWire(Wire wire) {
        this.currentWire = Objects.requireNonNull(wire, "Cannot set a null wire for packet " + id);
        this.progressOnWire = 0.0; 
        this.currentSystem = null; 
        Port startPort = wire.getStartPort();
        if (startPort != null && startPort.getPosition() != null) {
            Point startPos = startPort.getPosition();
            this.position = new Point2D.Double(startPos.x, startPos.y); 
            Point2D.Double dir = wire.getDirectionVector();
            this.velocity = new Point2D.Double(dir.x * BASE_SPEED_MAGNITUDE, dir.y * BASE_SPEED_MAGNITUDE);
        } else {
            java.lang.System.err.println("WARN: Packet " + id + " assigned to wire " + wire.getId() + " with invalid start port/position! Position reset to (0,0), velocity zero.");
            this.position = new Point2D.Double(0, 0); 
            this.velocity = new Point2D.Double(0, 0); 
        }
    }
    public void setCurrentSystem(System system) { 
        this.currentSystem = system; 
        if (system != null) {
            this.currentWire = null;
            this.progressOnWire = 0.0;
            Point sysPos = system.getPosition(); 
            this.position = (sysPos != null) ? new Point2D.Double(sysPos.x, sysPos.y) : new Point2D.Double(0,0);
            this.velocity = new Point2D.Double(0, 0); 
        }
    }
    public void applyImpactForce(Point2D.Double forceVector) {
        if (markedForRemoval || currentSystem != null || forceVector == null) return; 
        double dampFactor = IMPACT_FORCE_DAMPING; 
        double forceFactor = 1.0 - dampFactor;   
        double newVelX = velocity.x * dampFactor + forceVector.x * forceFactor;
        double newVelY = velocity.y * dampFactor + forceVector.y * forceFactor;
        this.velocity = new Point2D.Double(newVelX, newVelY);
    }
    public void update(GamePanel gamePanel, boolean isAiryamanActive) {
        if (markedForRemoval || currentSystem != null || currentWire == null) {
            return;
        }
        position.x += velocity.x;
        position.y += velocity.y;
        Port startPort = currentWire.getStartPort();
        Port endPort = currentWire.getEndPort();
        if (startPort == null || endPort == null || startPort.getPosition() == null || endPort.getPosition() == null) {
            java.lang.System.err.println("Packet " + id + " on invalid wire " + currentWire.getId() + ". LOST."); 
            gamePanel.packetLost(this);
            markForRemoval();
            return;
        }
        Point2D wireStart = startPort.getPosition(); 
        Point2D wireEnd = endPort.getPosition();
        double distSqFromWire = Line2D.ptSegDistSq(wireStart.getX(), wireStart.getY(),
                wireEnd.getX(), wireEnd.getY(),
                position.x, position.y);
        if (distSqFromWire > MAX_DEVIATION_DISTANCE * MAX_DEVIATION_DISTANCE) {
            java.lang.System.out.println("Packet " + id + " deviated from wire " + currentWire.getId() + " (DistSq: " + String.format("%.1f", distSqFromWire) + "). LOST.");
            gamePanel.packetLost(this);
            markForRemoval();
            return; 
        }
        Point2D.Double dir = currentWire.getDirectionVector();
        double wireLength = currentWire.getLength();
        double projectedDist = 0;
        if(wireStart != null) {
            projectedDist = (position.x - wireStart.getX()) * dir.x + (position.y - wireStart.getY()) * dir.y;
        }
        progressOnWire = (wireLength > 1e-6) ? Math.max(0.0, Math.min(1.0, projectedDist / wireLength)) : 1.0;
        if (progressOnWire >= 1.0) {
            progressOnWire = 1.0; 
            if (endPort == null || endPort.getPosition() == null) {
                java.lang.System.err.println("CRITICAL ERROR: Packet " + id + " reached end of wire " + currentWire.getId() + " but end port is invalid! Packet LOST.");
                gamePanel.packetLost(this);
                markForRemoval();
                return;
            }
            Point endPos = endPort.getPosition();
            position = new Point2D.Double(endPos.x, endPos.y);
            System targetSystem = endPort.getParentSystem(); 
            Wire previousWire = this.currentWire; 
            this.currentWire = null;
            this.velocity = new Point2D.Double(0,0); 
            if (targetSystem != null) {
                targetSystem.receivePacket(this, gamePanel); 
            } else {
                java.lang.System.err.println("ERROR: Packet " + id + " arrived at end of wire "
                        + (previousWire != null ? previousWire.getId() : "UNKNOWN") + " but target system is null! Packet LOST.");
                gamePanel.packetLost(this);
                markForRemoval();
            }
        }
        else if (!markedForRemoval && noise >= this.size) {
            gamePanel.packetLost(this);
            markForRemoval();
        }
    }
    public void draw(Graphics2D g2d) {
        if (markedForRemoval || currentSystem != null || position == null) {
            return;
        }
        int drawSize = getDrawSize(); 
        int halfSize = drawSize / 2;
        int drawX = (int) Math.round(position.x - halfSize);
        int drawY = (int) Math.round(position.y - halfSize);
        Color packetColor = Port.getColorFromShape(shape); 
        g2d.setColor(packetColor);
        Path2D path = null; 
        AffineTransform oldTransform = g2d.getTransform(); 
        boolean transformed = false; 
        try {
            switch (shape) {
                case SQUARE:
                    g2d.fillRect(drawX, drawY, drawSize, drawSize);
                    break;
                case TRIANGLE:
                    if (Math.hypot(velocity.x, velocity.y) > 1e-6) {
                        g2d.translate(position.x, position.y);
                        g2d.rotate(Math.atan2(velocity.y, velocity.x)); 
                        g2d.translate(-position.x, -position.y);
                        transformed = true;
                    }
                    path = new Path2D.Double();
                    path.moveTo(position.x + halfSize, position.y);             
                    path.lineTo(position.x - halfSize, position.y + halfSize);  
                    path.lineTo(position.x - halfSize, position.y - halfSize);  
                    path.closePath();
                    g2d.fill(path); 
                    break;
                default:
                    g2d.fillOval(drawX, drawY, drawSize, drawSize); 
                    break;
            }
            if (noise > 0) {
                float noiseRatio = (size > 0) ? Math.min(1.0f, (float) (noise / this.size)) : 0.0f;
                int alpha = Math.min(255, 40 + (int) (noiseRatio * 180)); 
                Color noiseColor = new Color(255, 0, 0, alpha); 
                Composite originalComposite = g2d.getComposite(); 
                g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, noiseRatio * 0.6f)); 
                g2d.setColor(noiseColor);
                switch (shape) {
                    case SQUARE: g2d.fillRect(drawX, drawY, drawSize, drawSize); break;
                    case TRIANGLE: if (path != null) g2d.fill(path); break; 
                    default: g2d.fillOval(drawX, drawY, drawSize, drawSize); break; 
                }
                g2d.setComposite(originalComposite); 
            }
        } finally {
            if (transformed) {
                g2d.setTransform(oldTransform);
            }
        }
    }
    public void markForRemoval() { this.markedForRemoval = true; }
    public int getId() { return id; }
    public NetworkEnums.PacketShape getShape() { return shape; }
    public int getSize() { return size; }
    public int getBaseCoinValue() { return baseCoinValue; }
    public Point getPosition() {
        return new Point((int)Math.round(position.x), (int)Math.round(position.y));
    }
    public Point2D.Double getPositionDouble() {
        return new Point2D.Double(position.x, position.y);
    }
    public Wire getCurrentWire() { return currentWire; }
    public double getProgressOnWire() { return progressOnWire; }
    public double getNoise() { return noise; }
    public boolean isMarkedForRemoval() { return markedForRemoval; }
    public System getCurrentSystem() { return currentSystem; } 
    public int getDrawSize() {
        return BASE_DRAW_SIZE + (size * 2); 
    }
    public void addNoise(double amount) {
        if (amount > 0 && !markedForRemoval) {
            this.noise = Math.min(this.size, this.noise + amount); 
        }
    }
    public void resetNoise() {
        if (!markedForRemoval && this.noise > 0) {
            this.noise = 0.0;
        }
    }
    public boolean collidesWith(Packet other) {
        if (this == other || other == null || this.position == null || other.position == null) return false;
        if (this.currentWire == null || other.currentWire == null) return false; 
        if (this.currentSystem != null || other.currentSystem != null) return false; 
        if (this.markedForRemoval || other.markedForRemoval) return false; 
        if (!Objects.equals(this.currentWire, other.currentWire)) { 
            return false;
        }
        double distSq = this.position.distanceSq(other.position);
        double combinedRadius = (this.getDrawSize() + other.getDrawSize()) / 2.0;
        double collisionThreshold = combinedRadius * COLLISION_RADIUS_FACTOR; 
        double collisionThresholdSq = collisionThreshold * collisionThreshold; 
        return distSq < collisionThresholdSq; 
    }
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Packet packet = (Packet) o;
        return id == packet.id; 
    }
    @Override
    public int hashCode() {
        return Objects.hash(id); 
    }
    @Override
    public String toString() {
        String status;
        if (markedForRemoval) status = "REMOVED";
        else if (currentSystem != null) status = "QUEUED(Sys:" + currentSystem.getId() + ")";
        else if (currentWire != null) status = String.format("ON_WIRE(W:%d %.1f%%)", currentWire.getId(), progressOnWire * 100);
        else status = "IDLE/INIT"; 
        String posStr = String.format("%.1f,%.1f", position.x, position.y);
        String velStr = String.format("%.1f,%.1f", velocity.x, velocity.y);
        return String.format("Packet{ID:%d, SHP:%s, SZ:%d, N:%.1f, V:%d, Pos:(%s) Vel:(%s) St:%s}",
                id, shape, size, noise, baseCoinValue, posStr, velStr, status);
    }
}