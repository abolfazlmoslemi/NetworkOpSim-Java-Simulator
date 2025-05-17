// FILE: Packet.java
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
// import java.awt.geom.Line2D;
import java.util.Objects;
import java.util.Random;

public class Packet {
    public static final double BASE_SPEED_MAGNITUDE = 2.1;
    public static final int BASE_DRAW_SIZE = 8;
    public static final double SQUARE_COMPATIBLE_SPEED_FACTOR = 0.5;
    private static final double TRIANGLE_ACCELERATION_RATE = 0.06;
    public static final double MAX_SPEED_MAGNITUDE = 4.2;

    private static final double COLLISION_RADIUS_FACTOR = 1.0;
    private static final Font NOISE_FONT = new Font("Arial", Font.PLAIN, 9);
    private static final Color NOISE_TEXT_COLOR = Color.WHITE;
    private static final Color IDEAL_POSITION_MARKER_COLOR = new Color(255, 255, 255, 60);
    private static final int IDEAL_POSITION_MARKER_SIZE = 4;
    private static final Random randomInitialSide = new Random();
    private static int nextId = 0;

    private final int id;
    private final NetworkEnums.PacketShape shape;
    private final int size; // Max noise value (e.g., 2 for Square, 3 for Triangle)
    private final int baseCoinValue;

    private Point2D.Double idealPosition;
    private Point2D.Double velocity;

    private Wire currentWire = null;
    private System currentSystem = null; // If not null, packet is in this system's queue
    private double progressOnWire = 0.0;
    private double noise = 0.0;
    private boolean markedForRemoval = false;

    private double currentSpeedMagnitude = 0.0;
    private double targetSpeedMagnitude = 0.0;
    private boolean isAccelerating = false;

    // --- Fields for Stable Visual Offset based on Noise and External Forces ---
    // Stores the normalized perpendicular direction relative to the wire for the current offset.
    // This is set by external events (collision, impact wave) or defaults if noise added without specific force.
    private Point2D.Double visualOffsetDirection = null;
    // Determines which side of the wire the offset occurs if no specific force dictates it.
    // It's set once when the packet gets noise for the first time on a wire without a preceding force.
    private int initialOffsetSidePreference = 0; // 0 or 1

    public Packet(NetworkEnums.PacketShape shape, double startX, double startY) {
        this.id = nextId++;
        this.shape = Objects.requireNonNull(shape, "Packet shape cannot be null");
        this.idealPosition = new Point2D.Double(startX, startY);
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
        this.initialOffsetSidePreference = randomInitialSide.nextInt(2);
    }

    public void setWire(Wire wire, boolean compatiblePortExit) {
        this.currentWire = Objects.requireNonNull(wire, "Cannot set a null wire for packet " + id);
        this.progressOnWire = 0.0;
        this.currentSystem = null;
        // visualOffsetDirection and noise are PERSISTED from previous state if packet moves between wires via a node
        // If it's a new packet or noise was reset, visualOffsetDirection might be null.
        // initialOffsetSidePreference is kept, but a new wire might mean a new random side if visualOffsetDirection becomes null and noise is then added.

        Port startPort = wire.getStartPort();
        if (startPort == null || startPort.getPosition() == null) {
            java.lang.System.err.println("WARN: Packet " + id + " assigned to wire " + wire.getId() + " with invalid start port/position! Resetting state.");
            this.idealPosition = new Point2D.Double(0, 0); this.velocity = new Point2D.Double(0, 0); this.currentSpeedMagnitude = 0.0; this.targetSpeedMagnitude = 0.0; this.isAccelerating = false; return;
        }
        Point startPos = startPort.getPosition();
        this.idealPosition = new Point2D.Double(startPos.x, startPos.y);

        double initialSpeed;
        if (this.shape == NetworkEnums.PacketShape.SQUARE) {
            initialSpeed = compatiblePortExit ? BASE_SPEED_MAGNITUDE * SQUARE_COMPATIBLE_SPEED_FACTOR : BASE_SPEED_MAGNITUDE;
            this.isAccelerating = false;
            this.targetSpeedMagnitude = initialSpeed;
        } else if (this.shape == NetworkEnums.PacketShape.TRIANGLE) {
            initialSpeed = BASE_SPEED_MAGNITUDE;
            this.isAccelerating = !compatiblePortExit;
            this.targetSpeedMagnitude = this.isAccelerating ? MAX_SPEED_MAGNITUDE : initialSpeed;
        } else {
            initialSpeed = BASE_SPEED_MAGNITUDE;
            this.isAccelerating = false;
            this.targetSpeedMagnitude = initialSpeed;
        }
        this.currentSpeedMagnitude = initialSpeed;
        Point2D.Double dir = wire.getDirectionVector();
        if (dir == null) {
            this.velocity = new Point2D.Double(0,0); this.currentSpeedMagnitude = 0.0; this.targetSpeedMagnitude = 0.0; this.isAccelerating = false;
        } else {
            this.velocity = new Point2D.Double(dir.x * this.currentSpeedMagnitude, dir.y * this.currentSpeedMagnitude);
        }
    }

    public void setCurrentSystem(System system) {
        this.currentSystem = system;
        if (system != null) {
            this.currentWire = null;
            this.progressOnWire = 0.0;
            Point sysPos = system.getPosition();
            this.idealPosition = (sysPos != null) ? new Point2D.Double(sysPos.x, sysPos.y) : new Point2D.Double(0,0);
            this.velocity = new Point2D.Double(0, 0);
            this.currentSpeedMagnitude = 0.0;
            this.targetSpeedMagnitude = 0.0;
            this.isAccelerating = false;
            // CRITICAL CHANGE: DO NOT RESET NOISE OR VISUAL OFFSET DIRECTION HERE
            // this.noise = 0; // REMOVED
            // this.visualOffsetDirection = null; // REMOVED
        }
    }

    public void update(GamePanel gamePanel, boolean isAiryamanActive) {
        if (markedForRemoval || currentSystem != null) { return; }
        if (currentWire == null) {
            this.velocity = new Point2D.Double(0,0);
            this.currentSpeedMagnitude = 0.0;
            this.isAccelerating = false;
            return;
        }

        Point2D.Double wireDir = currentWire.getDirectionVector();
        if (wireDir == null) {
            velocity = new Point2D.Double(0,0); currentSpeedMagnitude = 0; isAccelerating = false;
        } else {
            if (isAccelerating) {
                if (currentSpeedMagnitude < targetSpeedMagnitude) {
                    currentSpeedMagnitude = Math.min(targetSpeedMagnitude, currentSpeedMagnitude + TRIANGLE_ACCELERATION_RATE);
                } else {
                    currentSpeedMagnitude = targetSpeedMagnitude; isAccelerating = false;
                }
            }
            velocity.x = wireDir.x * currentSpeedMagnitude;
            velocity.y = wireDir.y * currentSpeedMagnitude;
        }

        idealPosition.x += velocity.x;
        idealPosition.y += velocity.y;

        double wireLength = currentWire.getLength();
        if (wireLength > 1e-6) {
            Point2D wireStartPos = currentWire.getStartPort().getPosition();
            if (wireStartPos != null) {
                double distFromStart = idealPosition.distance(wireStartPos);
                progressOnWire = distFromStart / wireLength;
            } else {
                progressOnWire = 1.0;
            }
        } else {
            progressOnWire = 1.0;
        }
        progressOnWire = Math.max(0.0, Math.min(1.0, progressOnWire));


        if (!markedForRemoval && noise >= this.size) {
            java.lang.System.out.println("Packet " + id + " lost due to excessive noise (Noise: " + String.format("%.1f", noise) + " >= Size: " + size + ").");
            gamePanel.packetLost(this);
            return;
        }

        if (progressOnWire >= 1.0 - 1e-9) {
            Port endPort = currentWire.getEndPort();
            if (endPort == null || endPort.getPosition() == null) {
                gamePanel.packetLost(this); return;
            }
            idealPosition.setLocation(endPort.getPosition());

            System targetSystem = endPort.getParentSystem();
            // Don't reset visualOffsetDirection or noise when just leaving wire to enter system
            // setCurrentSystem will handle what happens to packet state when it's IN a system.
            Wire oldWire = this.currentWire;
            this.currentWire = null; // Mark as off-wire for now
            this.velocity = new Point2D.Double(0,0);
            this.currentSpeedMagnitude = 0.0;
            this.isAccelerating = false;
            this.progressOnWire = 0.0;

            if (targetSystem != null) {
                targetSystem.receivePacket(this, gamePanel);
            } else {
                gamePanel.packetLost(this);
            }
        }
    }

    /**
     * Sets the preferred visual offset direction based on an external force.
     * The offset will be perpendicular to the wire, in the general direction of the force.
     * @param forceDirection A non-normalized vector indicating the direction of the external push/force.
     */
    public void setVisualOffsetDirectionFromForce(Point2D.Double forceDirection) {
        if (currentWire == null || forceDirection == null || (forceDirection.x == 0 && forceDirection.y == 0)) {
            // If no wire or no force, cannot determine a specific direction.
            // Keep existing visualOffsetDirection or let it be randomly assigned if null by addNoise.
            if (this.visualOffsetDirection == null) { // If no prior direction and no force, pick a random side
                Point2D.Double wireDir = currentWire != null ? currentWire.getDirectionVector() : new Point2D.Double(1,0);
                if (wireDir != null) {
                    this.visualOffsetDirection = new Point2D.Double(-wireDir.y, wireDir.x); // Default perpendicular
                    if (this.initialOffsetSidePreference == 1) {
                        this.visualOffsetDirection.x *= -1;
                        this.visualOffsetDirection.y *= -1;
                    }
                }
            }
            return;
        }

        Point2D.Double wireDir = currentWire.getDirectionVector();
        if (wireDir == null) { // Should not happen if currentWire is not null
            if (this.visualOffsetDirection == null) this.visualOffsetDirection = new Point2D.Double(0,1); // Default up
            return;
        }

        // Calculate the two perpendicular directions to the wire
        Point2D.Double perp1 = new Point2D.Double(-wireDir.y, wireDir.x);
        // Point2D.Double perp2 = new Point2D.Double(wireDir.y, -wireDir.x); // This is just -perp1

        // Determine which perpendicular direction is more aligned with the forceDirection
        double dotProductWithPerp1 = (forceDirection.x * perp1.x) + (forceDirection.y * perp1.y);

        if (dotProductWithPerp1 >= 0) {
            this.visualOffsetDirection = perp1; // Force is generally in the direction of perp1
        } else {
            this.visualOffsetDirection = new Point2D.Double(-perp1.x, -perp1.y); // Force is generally in the direction of -perp1
        }
        // Normalize it (though perp1 derived from normalized wireDir should already be normal)
        double mag = Math.hypot(this.visualOffsetDirection.x, this.visualOffsetDirection.y);
        if (mag > 1e-6) {
            this.visualOffsetDirection.x /= mag;
            this.visualOffsetDirection.y /= mag;
        } else { // Fallback if something went wrong
            this.visualOffsetDirection = perp1; // Or a default
        }
    }


    private Point2D.Double calculateCurrentVisualOffset() {
        if (noise == 0 || currentWire == null) {
            return new Point2D.Double(0, 0);
        }

        if (this.visualOffsetDirection == null) {
            // This can happen if noise was added while packet was not on a wire,
            // or if setVisualOffsetDirectionFromForce was never called with a valid force.
            // Default to a random perpendicular side.
            Point2D.Double wireDir = currentWire.getDirectionVector();
            if (wireDir == null) return new Point2D.Double(0,0); // Cannot determine offset

            this.visualOffsetDirection = new Point2D.Double(-wireDir.y, wireDir.x);
            if (this.initialOffsetSidePreference == 1) {
                this.visualOffsetDirection.x *= -1;
                this.visualOffsetDirection.y *= -1;
            }
        }

        double halfDrawSize = getDrawSize() / 2.0;
        double maxPossibleOffsetToVertex = 0;

        if (shape == NetworkEnums.PacketShape.SQUARE) {
            maxPossibleOffsetToVertex = halfDrawSize * Math.sqrt(2); // Distance to corner
        } else if (shape == NetworkEnums.PacketShape.TRIANGLE) {
            maxPossibleOffsetToVertex = halfDrawSize * 1.15; // Approx. distance to vertex
        }

        double noiseRatio = Math.min(noise / (double)size, 1.0);
        double currentOffsetMagnitude = maxPossibleOffsetToVertex * noiseRatio;

        return new Point2D.Double(
                this.visualOffsetDirection.x * currentOffsetMagnitude,
                this.visualOffsetDirection.y * currentOffsetMagnitude
        );
    }

    public void draw(Graphics2D g2d) {
        if (markedForRemoval || currentSystem != null || idealPosition == null) {
            return;
        }

        Point2D.Double calculatedOffset = calculateCurrentVisualOffset();
        Point2D.Double visualPosition = new Point2D.Double(
                idealPosition.x + calculatedOffset.x,
                idealPosition.y + calculatedOffset.y
        );

        if (currentWire != null && noise == 0) {
            g2d.setColor(IDEAL_POSITION_MARKER_COLOR);
            int markerHalf = IDEAL_POSITION_MARKER_SIZE / 2;
            int idealX = (int)Math.round(idealPosition.x);
            int idealY = (int)Math.round(idealPosition.y);
            g2d.drawLine(idealX - markerHalf, idealY, idealX + markerHalf, idealY);
            g2d.drawLine(idealX, idealY - markerHalf, idealX, idealY + markerHalf);
        }

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
            Point2D.Double directionForRotation = null;
            if(currentWire != null && currentWire.getDirectionVector() != null && Math.hypot(velocity.x, velocity.y) > 0.01) {
                directionForRotation = velocity;
            } else if (currentWire != null && currentWire.getDirectionVector() != null) {
                directionForRotation = currentWire.getDirectionVector();
            }

            if (shape == NetworkEnums.PacketShape.TRIANGLE && directionForRotation != null) {
                g2d.translate(visualPosition.x, visualPosition.y);
                g2d.rotate(Math.atan2(directionForRotation.y, directionForRotation.x));
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
                        if (directionForRotation != null) { // Should have been caught above, but defensive
                            g2d.translate(visualPosition.x, visualPosition.y);
                            g2d.rotate(Math.atan2(directionForRotation.y, directionForRotation.x));
                            path.moveTo(halfSize, 0); path.lineTo(-halfSize, -halfSize); path.lineTo(-halfSize, halfSize); path.closePath();
                            g2d.fill(path);
                            transformed = true;
                        } else {
                            path.moveTo(visualPosition.x, drawY); path.lineTo(drawX + drawSize, drawY + drawSize); path.lineTo(drawX, drawY + drawSize); path.closePath();
                            g2d.fill(path);
                        }
                        break;
                    default:
                        g2d.fillOval(drawX, drawY, drawSize, drawSize);
                        break;
                }
            }

            if (noise > 0) {
                float noiseRatio = Math.min(1.0f, (float) (noise / this.size));
                int alpha = Math.min(255, 60 + (int) (noiseRatio * 195));
                Color noiseEffectColor = new Color(200, 0, 0, alpha);
                Composite originalComposite = g2d.getComposite();
                g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, noiseRatio * 0.5f + 0.15f));
                g2d.setColor(noiseEffectColor);
                if (transformed && path != null) {
                    g2d.fill(path);
                } else {
                    if (shape == NetworkEnums.PacketShape.SQUARE) {
                        g2d.fillRect(drawX, drawY, drawSize, drawSize);
                    } else if (shape == NetworkEnums.PacketShape.TRIANGLE && path != null) {
                        g2d.fill(path);
                    } else {
                        g2d.fillOval(drawX, drawY, drawSize, drawSize);
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

    public void markForRemoval() { this.markedForRemoval = true; }
    public int getId() { return id; }
    public NetworkEnums.PacketShape getShape() { return shape; }
    public int getSize() { return size; }
    public int getBaseCoinValue() { return baseCoinValue; }

    public Point getPosition() {
        if (idealPosition == null) return new Point();
        Point2D.Double offset = calculateCurrentVisualOffset();
        return new Point(
                (int)Math.round(idealPosition.x + offset.x),
                (int)Math.round(idealPosition.y + offset.y)
        );
    }

    public Point2D.Double getPositionDouble() {
        if (idealPosition == null) return new Point2D.Double();
        Point2D.Double offset = calculateCurrentVisualOffset();
        return new Point2D.Double(
                idealPosition.x + offset.x,
                idealPosition.y + offset.y
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
    public int getDrawSize() { return BASE_DRAW_SIZE + (size * 2); }

    public void addNoise(double amount) {
        if (amount > 0 && !markedForRemoval) {
            double oldNoise = this.noise;
            this.noise = Math.min(this.size, this.noise + amount);

            // If noise actually increased AND visualOffsetDirection was not set by a specific force recently,
            // then (re-)set a random side preference.
            // The actual direction vector is calculated in calculateCurrentVisualOffset if null.
            if (this.noise > oldNoise && this.visualOffsetDirection == null && currentWire != null) {
                this.initialOffsetSidePreference = randomInitialSide.nextInt(2);
            }
        }
    }

    public void resetNoise() {
        if (!markedForRemoval && this.noise > 0) {
            this.noise = 0.0;
            this.visualOffsetDirection = null; // Clear the specific offset direction
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
        else if (currentWire != null) status = String.format("ON_WIRE(W:%d P:%.1f%%)", currentWire.getId(), progressOnWire * 100);
        else status = "IDLE/INIT";
        String idealPosStr = (idealPosition != null) ? String.format("%.1f,%.1f", idealPosition.x, idealPosition.y) : "null";
        String velStr = (velocity != null) ? String.format("%.1f,%.1f (Mag:%.2f)", velocity.x, velocity.y, currentSpeedMagnitude) : "null";
        return String.format("Packet{ID:%d, SHP:%s, SZ:%d, N:%.1f/%.1f, V:%d, Ideal:(%s) Vel:(%s) Accel:%b St:%s}",
                id, shape, size, noise, (double)size, baseCoinValue, idealPosStr, velStr, isAccelerating, status);
    }
}