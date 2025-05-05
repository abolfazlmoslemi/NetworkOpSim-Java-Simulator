import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Line2D;
import java.util.Objects;
import java.util.Random; 
public class Packet {
    public static final double BASE_SPEED_MAGNITUDE = 2.1;
    public static final int BASE_DRAW_SIZE = 8;
    public static final double SQUARE_COMPATIBLE_SPEED_FACTOR = 0.5;
    private static final double TRIANGLE_ACCELERATION_RATE = 0.06;
    public static final double MAX_SPEED_MAGNITUDE = 4.2;
    private static final double MAX_PERPENDICULAR_DEVIATION = 10.0; 
    private static final double VISUAL_OFFSET_DAMPING = 0.88; 
    private static final double NOISE_TO_OFFSET_FACTOR = 0.1; 
    private static final double COLLISION_RADIUS_FACTOR = 1.0;
    private static final Font NOISE_FONT = new Font("Arial", Font.PLAIN, 9);
    private static final Color NOISE_TEXT_COLOR = Color.WHITE;
    private static final Color IDEAL_POSITION_MARKER_COLOR = new Color(255, 255, 255, 60);
    private static final int IDEAL_POSITION_MARKER_SIZE = 4;
    private static final Random random = new Random(); 
    private static int nextId = 0;
    private final int id;
    private final NetworkEnums.PacketShape shape;
    private final int size;
    private final int baseCoinValue;
    private Point2D.Double idealPosition;  
    private Point2D.Double visualOffset;   
    private Point2D.Double velocity;       
    private Wire currentWire = null;
    private System currentSystem = null;
    private double progressOnWire = 0.0;
    private double noise = 0.0;
    private boolean markedForRemoval = false;
    private double currentSpeedMagnitude = 0.0;
    private double targetSpeedMagnitude = 0.0;
    private boolean isAccelerating = false;
    public Packet(NetworkEnums.PacketShape shape, double startX, double startY) {
        this.id = nextId++;
        this.shape = Objects.requireNonNull(shape, "Packet shape cannot be null");
        this.idealPosition = new Point2D.Double(startX, startY);
        this.visualOffset = new Point2D.Double(0, 0); 
        this.velocity = new Point2D.Double(0, 0);
        this.isAccelerating = false;
        this.currentSpeedMagnitude = 0.0;
        this.targetSpeedMagnitude = 0.0;
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
    public void setWire(Wire wire, boolean compatibleExit) {
        this.currentWire = Objects.requireNonNull(wire, "Cannot set a null wire for packet " + id);
        this.progressOnWire = 0.0;
        this.currentSystem = null;
        this.visualOffset = new Point2D.Double(0, 0); 
        Port startPort = wire.getStartPort();
        if (startPort == null || startPort.getPosition() == null) {
            java.lang.System.err.println("WARN: Packet " + id + " assigned to wire " + wire.getId() + " with invalid start port/position! Resetting state.");
            this.idealPosition = new Point2D.Double(0, 0); this.velocity = new Point2D.Double(0, 0); this.currentSpeedMagnitude = 0.0; this.targetSpeedMagnitude = 0.0; this.isAccelerating = false; return;
        }
        Point startPos = startPort.getPosition();
        this.idealPosition = new Point2D.Double(startPos.x, startPos.y); 
        double initialSpeedMagnitude;
        if (this.shape == NetworkEnums.PacketShape.SQUARE) {
            initialSpeedMagnitude = compatibleExit ? BASE_SPEED_MAGNITUDE * SQUARE_COMPATIBLE_SPEED_FACTOR : BASE_SPEED_MAGNITUDE;
            this.isAccelerating = false; this.targetSpeedMagnitude = initialSpeedMagnitude;
        } else if (this.shape == NetworkEnums.PacketShape.TRIANGLE) {
            initialSpeedMagnitude = BASE_SPEED_MAGNITUDE; this.isAccelerating = !compatibleExit; this.targetSpeedMagnitude = isAccelerating ? MAX_SPEED_MAGNITUDE : initialSpeedMagnitude;
        } else {
            java.lang.System.err.println("WARN: Packet " + id + " has unhandled shape " + this.shape + ". Defaulting speed.");
            initialSpeedMagnitude = BASE_SPEED_MAGNITUDE; this.isAccelerating = false; this.targetSpeedMagnitude = initialSpeedMagnitude;
        }
        this.currentSpeedMagnitude = initialSpeedMagnitude;
        Point2D.Double dir = wire.getDirectionVector();
        if (dir == null) {
            java.lang.System.err.println("WARN: Packet " + id + " could not get direction vector for wire " + wire.getId() + ". Velocity zero.");
            this.velocity = new Point2D.Double(0,0); this.currentSpeedMagnitude = 0.0; this.targetSpeedMagnitude = 0.0; this.isAccelerating = false;
        } else { this.velocity = new Point2D.Double(dir.x * this.currentSpeedMagnitude, dir.y * this.currentSpeedMagnitude); }
    }
    public void setCurrentSystem(System system) {
        this.currentSystem = system;
        if (system != null) {
            this.currentWire = null; this.progressOnWire = 0.0; Point sysPos = system.getPosition();
            this.idealPosition = (sysPos != null) ? new Point2D.Double(sysPos.x, sysPos.y) : new Point2D.Double(0,0);
            this.visualOffset = new Point2D.Double(0, 0); 
            this.velocity = new Point2D.Double(0, 0); this.currentSpeedMagnitude = 0.0; this.targetSpeedMagnitude = 0.0; this.isAccelerating = false;
        }
    }
    public void applyPerpendicularImpactForce(Point2D.Double forceVector) {
        if (markedForRemoval || currentSystem != null || forceVector == null || currentWire == null) return;
        visualOffset.x += forceVector.x;
        visualOffset.y += forceVector.y;
        capVisualOffsetMagnitude();
    }
    public void update(GamePanel gamePanel, boolean isAiryamanActive) {
        if (markedForRemoval || currentSystem != null) { return; }
        if (currentWire == null) {
            this.velocity = new Point2D.Double(0,0);
            this.currentSpeedMagnitude = 0.0;
            this.isAccelerating = false;
            visualOffset.x *= VISUAL_OFFSET_DAMPING;
            visualOffset.y *= VISUAL_OFFSET_DAMPING;
            if (Math.hypot(visualOffset.x, visualOffset.y) < 0.1) {
                visualOffset.setLocation(0,0);
            }
            return;
        }
        Point2D.Double wireDir = currentWire.getDirectionVector();
        if (wireDir == null) { 
            java.lang.System.err.println("WARN: Packet " + id + " lost wire direction in update. Halting movement.");
            velocity = new Point2D.Double(0,0);
            currentSpeedMagnitude = 0;
            isAccelerating = false;
        } else {
            if (isAccelerating) {
                if (currentSpeedMagnitude < targetSpeedMagnitude) {
                    currentSpeedMagnitude = Math.min(targetSpeedMagnitude, currentSpeedMagnitude + TRIANGLE_ACCELERATION_RATE);
                } else {
                    isAccelerating = false; 
                    currentSpeedMagnitude = targetSpeedMagnitude; 
                }
            }
            velocity = new Point2D.Double(wireDir.x * currentSpeedMagnitude, wireDir.y * currentSpeedMagnitude);
        }
        double distanceMoved = currentSpeedMagnitude; 
        double wireLength = currentWire.getLength();
        if (wireLength > 1e-6) {
            progressOnWire += (distanceMoved / wireLength);
        } else {
            progressOnWire = 1.0; 
        }
        progressOnWire = Math.max(0.0, Math.min(1.0, progressOnWire));
        Point idealPoint = currentWire.getPointAtProgress(progressOnWire);
        if (idealPoint != null) {
            idealPosition.setLocation(idealPoint);
        } else {
            java.lang.System.err.println("WARN: Packet " + id + " could not get ideal position from wire " + currentWire.getId() + " at progress " + progressOnWire);
        }
        visualOffset.x *= VISUAL_OFFSET_DAMPING;
        visualOffset.y *= VISUAL_OFFSET_DAMPING;
        if (Math.hypot(visualOffset.x, visualOffset.y) < 0.1) {
            visualOffset.setLocation(0,0);
        } else {
            capVisualOffsetMagnitude();
        }
        if (!markedForRemoval && noise >= this.size) {
            java.lang.System.out.println("Packet " + id + " lost due to excessive noise (Noise: " + String.format("%.1f", noise) + " >= Size: " + size + ").");
            gamePanel.packetLost(this);
            return;
        }
        if (progressOnWire >= 1.0 - 1e-9) {
            Port endPort = currentWire.getEndPort();
            if (endPort == null || endPort.getPosition() == null) {
                java.lang.System.err.println("CRITICAL ERROR: Packet " + id + " reached end of wire " + currentWire.getId() + " but end port/position invalid! LOST.");
                gamePanel.packetLost(this);
                return;
            }
            idealPosition.setLocation(endPort.getPosition());
            visualOffset.setLocation(0, 0); 
            System targetSystem = endPort.getParentSystem();
            Wire previousWire = this.currentWire;
            this.currentWire = null;
            this.velocity = new Point2D.Double(0,0);
            this.currentSpeedMagnitude = 0.0;
            this.isAccelerating = false;
            this.progressOnWire = 0.0; 
            if (targetSystem != null) {
                targetSystem.receivePacket(this, gamePanel);
            } else {
                java.lang.System.err.println("ERROR: Packet " + id + " arrived at end of wire " + (previousWire != null ? previousWire.getId() : "UNKNOWN") + " but target system is null! LOST.");
                gamePanel.packetLost(this);
            }
        }
    }
    private void capVisualOffsetMagnitude() {
        double currentOffsetMag = Math.hypot(visualOffset.x, visualOffset.y);
        if (currentOffsetMag > MAX_PERPENDICULAR_DEVIATION) {
            double scale = MAX_PERPENDICULAR_DEVIATION / currentOffsetMag;
            visualOffset.x *= scale;
            visualOffset.y *= scale;
        }
    }
    public void draw(Graphics2D g2d) {
        if (markedForRemoval || currentSystem != null || idealPosition == null) {
            return;
        }
        if (currentWire != null) { 
            g2d.setColor(IDEAL_POSITION_MARKER_COLOR);
            int markerHalf = IDEAL_POSITION_MARKER_SIZE / 2;
            int idealX = (int)Math.round(idealPosition.x);
            int idealY = (int)Math.round(idealPosition.y);
            g2d.drawLine(idealX - markerHalf, idealY, idealX + markerHalf, idealY);
            g2d.drawLine(idealX, idealY - markerHalf, idealX, idealY + markerHalf);
        }
        Point2D.Double visualPosition = new Point2D.Double(
                idealPosition.x + visualOffset.x,
                idealPosition.y + visualOffset.y
        );
        int drawSize = getDrawSize();
        int halfSize = drawSize / 2;
        int drawX = (int) Math.round(visualPosition.x - halfSize);
        int drawY = (int) Math.round(visualPosition.y - halfSize);
        Color packetColor = Port.getColorFromShape(shape);
        g2d.setColor(packetColor);
        Path2D path = null;
        AffineTransform oldTransform = g2d.getTransform();
        boolean transformed = false;
        try {
            if (shape == NetworkEnums.PacketShape.TRIANGLE && Math.hypot(velocity.x, velocity.y) > 0.1) {
                g2d.translate(visualPosition.x, visualPosition.y);
                g2d.rotate(Math.atan2(velocity.y, velocity.x));
                path = new Path2D.Double();
                path.moveTo(halfSize, 0); 
                path.lineTo(-halfSize, -halfSize);
                path.lineTo(-halfSize, halfSize);
                path.closePath();
                g2d.fill(path);
                transformed = true;
            } else {
                switch (shape) {
                    case SQUARE:
                        g2d.fillRect(drawX, drawY, drawSize, drawSize);
                        break;
                    case TRIANGLE: 
                        path = new Path2D.Double();
                        path.moveTo(visualPosition.x + halfSize, visualPosition.y); 
                        path.lineTo(visualPosition.x - halfSize, visualPosition.y - halfSize);
                        path.lineTo(visualPosition.x - halfSize, visualPosition.y + halfSize);
                        path.closePath();
                        g2d.fill(path);
                        break;
                    default:
                        g2d.fillOval(drawX, drawY, drawSize, drawSize);
                        break;
                }
            }
            if (noise > 0) {
                float noiseRatio = (size > 0) ? Math.min(1.0f, (float) (noise / this.size)) : 0.0f;
                int alpha = Math.min(255, 40 + (int) (noiseRatio * 180));
                Color noiseColor = new Color(255, 0, 0, alpha);
                Composite originalComposite = g2d.getComposite();
                g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, noiseRatio * 0.6f + 0.1f));
                g2d.setColor(noiseColor);
                if (transformed && path != null) { 
                    g2d.fill(path);
                } else { 
                    switch (shape) {
                        case SQUARE:
                            g2d.fillRect(drawX, drawY, drawSize, drawSize);
                            break;
                        case TRIANGLE:
                            if (path != null) g2d.fill(path); 
                            else g2d.fillOval(drawX, drawY, drawSize, drawSize); 
                            break;
                        default:
                            g2d.fillOval(drawX, drawY, drawSize, drawSize);
                            break;
                    }
                }
                g2d.setComposite(originalComposite);
            }
        } finally {
            if (transformed) {
                g2d.setTransform(oldTransform); 
            }
            String noiseString = String.format("N:%.1f", noise);
            g2d.setFont(NOISE_FONT);
            g2d.setColor(NOISE_TEXT_COLOR);
            FontMetrics fm = g2d.getFontMetrics();
            int textWidth = fm.stringWidth(noiseString);
            int textX = (int) Math.round(visualPosition.x - textWidth / 2.0);
            int textY = drawY - fm.getDescent() - 1; 
            g2d.drawString(noiseString, textX, textY);
        }
    }
    public void markForRemoval() {
        this.markedForRemoval = true;
    }
    public int getId() { return id; }
    public NetworkEnums.PacketShape getShape() { return shape; }
    public int getSize() { return size; }
    public int getBaseCoinValue() { return baseCoinValue; }
    public Point getPosition() {
        if (idealPosition == null) return new Point();
        return new Point(
                (int)Math.round(idealPosition.x + visualOffset.x),
                (int)Math.round(idealPosition.y + visualOffset.y)
        );
    }
    public Point2D.Double getPositionDouble() {
        if (idealPosition == null) return new Point2D.Double();
        return new Point2D.Double(
                idealPosition.x + visualOffset.x,
                idealPosition.y + visualOffset.y
        );
    }
    public Point2D.Double getIdealPositionDouble() {
        return (idealPosition != null) ? new Point2D.Double(idealPosition.x, idealPosition.y) : new Point2D.Double();
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
        if (amount > 0 && !markedForRemoval ) { 
            this.noise = Math.min(this.size, this.noise + amount);
        }
    }
    public void resetNoise() {
        if (!markedForRemoval && this.noise > 0) {
            this.noise = 0.0;
        }
    }
    public boolean collidesWith(Packet other) {
        if (this == other || other == null) return false;
        Point2D.Double thisPos = this.getPositionDouble();
        Point2D.Double otherPos = other.getPositionDouble();
        if (thisPos == null || otherPos == null) return false; 
        if (this.currentSystem != null || other.currentSystem != null || this.markedForRemoval || other.markedForRemoval) {
            return false;
        }
        double distSq = thisPos.distanceSq(otherPos);
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
        String idealPosStr = (idealPosition != null) ? String.format("%.1f,%.1f", idealPosition.x, idealPosition.y) : "null";
        String visualOffsetStr = (visualOffset != null) ? String.format("%.1f,%.1f", visualOffset.x, visualOffset.y) : "null";
        String velStr = (velocity != null) ? String.format("%.1f,%.1f (Mag:%.2f)", velocity.x, velocity.y, currentSpeedMagnitude) : "null";
        return String.format("Packet{ID:%d, SHP:%s, SZ:%d, N:%.1f/%.1f, V:%d, Ideal:(%s) Offset:(%s) Vel:(%s) Accel:%b St:%s}",
                id, shape, size, noise, (double)size, baseCoinValue, idealPosStr, visualOffsetStr, velStr, isAccelerating, status);
    }
}