// ===== Packet.java =====

package com.networkopsim.game;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.Objects;
// Removed: import java.util.Random; // No longer using Packet.randomInitialSide

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
    // private static final Random randomInitialSide = new Random(); // REMOVED FOR DETERMINISM
    private static int nextPacketId = 0;

    private final int id;
    private final NetworkEnums.PacketShape shape;
    private final int size; // Represents "units" of the packet, influences noise tolerance & coins
    private final int baseCoinValue;

    private Point2D.Double idealPosition; // Position on the center of the wire if no noise
    private Point2D.Double velocity;

    private Wire currentWire = null;
    private System currentSystem = null; // If packet is in a system's queue
    private double progressOnWire = 0.0; // Normalized progress [0.0, 1.0]
    private double noise = 0.0;
    private boolean markedForRemoval = false;
    private PredictedPacketStatus finalStatusForPrediction = null; // Used by prediction logic

    // Speed and acceleration dynamics
    private double currentSpeedMagnitude = 0.0;
    private double targetSpeedMagnitude = 0.0;
    private boolean isAccelerating = false;

    // For visual offset due to noise or collisions
    private Point2D.Double visualOffsetDirection = null; // Normalized direction perpendicular to wire
    private int initialOffsetSidePreference = 0; // 0 or 1, determined by ID for deterministic default offset

    public static void resetGlobalId() {
        nextPacketId = 0;
    }

    public Packet(NetworkEnums.PacketShape shape, double startX, double startY) {
        this.id = nextPacketId++;
        this.shape = Objects.requireNonNull(shape, "Packet shape cannot be null");
        this.idealPosition = new Point2D.Double(startX, startY);
        this.velocity = new Point2D.Double(0, 0);

        switch (shape) {
            case SQUARE:
                this.size = 2; // e.g., 2 units
                this.baseCoinValue = 1;
                break;
            case TRIANGLE:
                this.size = 3; // e.g., 3 units
                this.baseCoinValue = 2;
                break;
            default:
                throw new IllegalArgumentException("Unsupported PacketShape in constructor: " + shape);
        }
        // MODIFIED FOR DETERMINISM: Initial offset preference based on packet ID
        this.initialOffsetSidePreference = this.id % 2; // 0 for even IDs, 1 for odd IDs
    }

    public Point2D.Double getVelocity() {
        if (this.velocity == null) return new Point2D.Double(0,0); // Defensive
        return new Point2D.Double(this.velocity.x, this.velocity.y);
    }

    public void setWire(Wire wire, boolean compatiblePortExit) {
        this.currentWire = Objects.requireNonNull(wire, "Cannot set a null wire for packet " + id);
        this.progressOnWire = 0.0;
        this.currentSystem = null; // Packet is on a wire, not in a system's queue

        // Reset prediction status if it was terminal or system-bound, now it's on a wire
        if (this.finalStatusForPrediction == PredictedPacketStatus.QUEUED ||
                this.finalStatusForPrediction == PredictedPacketStatus.STALLED_AT_NODE ||
                this.finalStatusForPrediction == PredictedPacketStatus.DELIVERED || // Should not happen if already delivered
                this.finalStatusForPrediction == PredictedPacketStatus.LOST) {       // Should not happen if already lost
            this.finalStatusForPrediction = null;
        }

        updateIdealPositionAndVelocity(); // Initial setup

        double initialSpeed;
        if (this.shape == NetworkEnums.PacketShape.SQUARE) {
            initialSpeed = compatiblePortExit ? BASE_SPEED_MAGNITUDE * SQUARE_COMPATIBLE_SPEED_FACTOR : BASE_SPEED_MAGNITUDE;
            this.isAccelerating = false; // Squares do not accelerate on wires
            this.targetSpeedMagnitude = initialSpeed;
        } else if (this.shape == NetworkEnums.PacketShape.TRIANGLE) {
            initialSpeed = BASE_SPEED_MAGNITUDE; // Triangles always start at base speed when entering a wire
            this.isAccelerating = !compatiblePortExit; // Accelerate if exiting a non-compatible port
            this.targetSpeedMagnitude = this.isAccelerating ? MAX_SPEED_MAGNITUDE : initialSpeed;
        } else { // Should not happen with current PacketShapes
            initialSpeed = BASE_SPEED_MAGNITUDE;
            this.isAccelerating = false;
            this.targetSpeedMagnitude = initialSpeed;
        }
        this.currentSpeedMagnitude = initialSpeed;
        updateIdealPositionAndVelocity(); // Update velocity with new speed
    }

    public void setCurrentSystem(System system) {
        this.currentSystem = system; // Can be null if packet leaves system (e.g., put on wire)

        // If it was ON_WIRE and now enters a system, its status will be updated by the system
        // (to QUEUED, STALLED, DELIVERED, or LOST).
        if (this.finalStatusForPrediction == PredictedPacketStatus.ON_WIRE) {
            this.finalStatusForPrediction = null;
        }

        if (system != null) { // Packet is now in a system's context
            this.currentWire = null;
            this.progressOnWire = 0.0;
            Point sysPosPoint = system.getPosition();
            this.idealPosition = (sysPosPoint != null) ? new Point2D.Double(sysPosPoint.x, sysPosPoint.y) : new Point2D.Double(0,0);
            this.velocity = new Point2D.Double(0, 0);
            this.currentSpeedMagnitude = 0.0;
            this.targetSpeedMagnitude = 0.0;
            this.isAccelerating = false;
            // The System's receivePacket logic will set the finalStatusForPrediction appropriately.
        }
    }


    public void update(GamePanel gamePanel, boolean isAiryamanActive, boolean isPredictionRun) {
        if (markedForRemoval || currentSystem != null) {
            return;
        }
        if (currentWire == null) {
            this.velocity = new Point2D.Double(0,0);
            this.currentSpeedMagnitude = 0.0;
            this.isAccelerating = false;
            if (this.finalStatusForPrediction != PredictedPacketStatus.LOST &&
                    this.finalStatusForPrediction != PredictedPacketStatus.DELIVERED) {
                setFinalStatusForPrediction(PredictedPacketStatus.LOST);
            }
            gamePanel.packetLostInternal(this, isPredictionRun);
            return;
        }

        // --- SPEED UPDATE ---
        if (isAccelerating) {
            if (currentSpeedMagnitude < targetSpeedMagnitude) {
                currentSpeedMagnitude = Math.min(targetSpeedMagnitude, currentSpeedMagnitude + TRIANGLE_ACCELERATION_RATE);
            } else { // Reached or exceeded target speed
                currentSpeedMagnitude = targetSpeedMagnitude;
                isAccelerating = false; // Stop accelerating
            }
        }

        // --- PROGRESS AND POSITION UPDATE ---
        double wireLength = currentWire.getLength();
        if (wireLength > 1e-6) {
            double distanceToTravel = currentSpeedMagnitude;
            progressOnWire += distanceToTravel / wireLength;
        } else {
            progressOnWire = 1.0; // Instantly traverse zero-length wires
        }
        progressOnWire = Math.max(0.0, Math.min(1.0, progressOnWire));
        updateIdealPositionAndVelocity(); // Update position and velocity vector based on new progress

        // --- CHECK END CONDITIONS ---
        if (!markedForRemoval && noise >= this.size) { // Packet size acts as noise tolerance
            setFinalStatusForPrediction(PredictedPacketStatus.LOST);
            gamePanel.packetLostInternal(this, isPredictionRun);
            return;
        }

        if (progressOnWire >= 1.0 - 1e-9) { // Reached end of wire (with tolerance)
            Port endPort = currentWire.getEndPort();
            if (endPort == null || endPort.getPosition() == null) { // End port is invalid
                setFinalStatusForPrediction(PredictedPacketStatus.LOST);
                gamePanel.packetLostInternal(this, isPredictionRun);
                return;
            }
            // Snap to end port position before delivering
            this.idealPosition = endPort.getPrecisePosition();

            System targetSystem = endPort.getParentSystem();
            if (targetSystem != null) {
                targetSystem.receivePacket(this, gamePanel, isPredictionRun); // Deliver to next system
            } else { // End port is not connected to a system
                setFinalStatusForPrediction(PredictedPacketStatus.LOST);
                gamePanel.packetLostInternal(this, isPredictionRun);
            }
        }
    }

    private void updateIdealPositionAndVelocity() {
        if (currentWire == null) return;
        Wire.PathInfo pathInfo = currentWire.getPathInfoAtProgress(progressOnWire);
        if (pathInfo != null) {
            this.idealPosition = pathInfo.position;
            this.velocity = new Point2D.Double(
                    pathInfo.direction.x * currentSpeedMagnitude,
                    pathInfo.direction.y * currentSpeedMagnitude
            );
        } else {
            this.idealPosition = currentWire.getStartPort().getPrecisePosition();
            this.velocity = new Point2D.Double(0,0);
        }
    }

    public void setVisualOffsetDirectionFromForce(Point2D.Double forceDirection) {
        if (currentWire == null) { // Cannot set offset if not on a wire
            if (this.visualOffsetDirection == null) this.visualOffsetDirection = new Point2D.Double(0,1); // Default upwards
            return;
        }
        Wire.PathInfo pathInfo = currentWire.getPathInfoAtProgress(this.progressOnWire);
        if (pathInfo == null) { // Should not happen for a valid wire
            if (this.visualOffsetDirection == null) this.visualOffsetDirection = new Point2D.Double(0,1); // Default upwards
            return;
        }
        Point2D.Double wireDir = pathInfo.direction;

        if (forceDirection == null || (forceDirection.x == 0 && forceDirection.y == 0)) {
            if (this.visualOffsetDirection == null) {
                this.visualOffsetDirection = new Point2D.Double(-wireDir.y, wireDir.x);
                if (this.initialOffsetSidePreference == 1) { // Flip if preference is 1
                    this.visualOffsetDirection.x *= -1;
                    this.visualOffsetDirection.y *= -1;
                }
                // Normalize
                double mag = Math.hypot(this.visualOffsetDirection.x, this.visualOffsetDirection.y);
                if (mag > 1e-6) {
                    this.visualOffsetDirection.x /= mag;
                    this.visualOffsetDirection.y /= mag;
                } else { // Fallback if wireDir was (0,0)
                    this.visualOffsetDirection = new Point2D.Double(0,1);
                }
            }
            return;
        }

        // Project force onto the perpendicular of the wire's direction
        Point2D.Double perp1 = new Point2D.Double(-wireDir.y, wireDir.x); // One perpendicular direction
        double dotProductWithPerp1 = (forceDirection.x * perp1.x) + (forceDirection.y * perp1.y);

        if (Math.abs(dotProductWithPerp1) < 1e-6) { // Force is parallel to wire or zero
            if (this.visualOffsetDirection == null) {
                this.visualOffsetDirection = new Point2D.Double(-wireDir.y, wireDir.x);
                if (this.initialOffsetSidePreference == 1) {
                    this.visualOffsetDirection.x *= -1; this.visualOffsetDirection.y *= -1;
                }
            }
        } else if (dotProductWithPerp1 > 0) { // Force is generally in the direction of perp1
            this.visualOffsetDirection = perp1;
        } else { // Force is generally in the direction of -perp1 (which is perp2)
            this.visualOffsetDirection = new Point2D.Double(-perp1.x, -perp1.y);
        }

        // Normalize the chosen direction
        double mag = Math.hypot(this.visualOffsetDirection.x, this.visualOffsetDirection.y);
        if (mag > 1e-6) {
            this.visualOffsetDirection.x /= mag;
            this.visualOffsetDirection.y /= mag;
        } else { // Fallback if something went wrong (e.g., wireDir was (0,0) initially)
            // Re-initialize to default preferred perpendicular
            this.visualOffsetDirection = new Point2D.Double(-wireDir.y, wireDir.x);
            if (this.initialOffsetSidePreference == 1) {
                this.visualOffsetDirection.x *= -1; this.visualOffsetDirection.y *= -1;
            }
            double m = Math.hypot(this.visualOffsetDirection.x, this.visualOffsetDirection.y);
            if (m > 1e-6) {
                this.visualOffsetDirection.x /= m; this.visualOffsetDirection.y /= m;
            } else {
                this.visualOffsetDirection = new Point2D.Double(0,1); // Absolute fallback
            }
        }
    }


    private Point2D.Double calculateCurrentVisualOffset() {
        if (noise == 0 || currentWire == null) {
            return new Point2D.Double(0, 0);
        }

        if (this.visualOffsetDirection == null) { // Initialize if not set by force
            Wire.PathInfo pathInfo = currentWire.getPathInfoAtProgress(this.progressOnWire);
            if (pathInfo == null) return new Point2D.Double(0,0);
            Point2D.Double wireDir = pathInfo.direction;

            this.visualOffsetDirection = new Point2D.Double(-wireDir.y, wireDir.x);
            if (this.initialOffsetSidePreference == 1) {
                this.visualOffsetDirection.x *= -1;
                this.visualOffsetDirection.y *= -1;
            }
            double mag = Math.hypot(this.visualOffsetDirection.x, this.visualOffsetDirection.y);
            if (mag > 1e-6) {
                this.visualOffsetDirection.x /= mag;
                this.visualOffsetDirection.y /= mag;
            } else {
                this.visualOffsetDirection = new Point2D.Double(0,1);
            }
        }

        double halfDrawSize = getDrawSize() / 2.0;
        double maxPossibleOffsetToVertex = 0;

        if (shape == NetworkEnums.PacketShape.SQUARE) {
            maxPossibleOffsetToVertex = halfDrawSize * Math.sqrt(2);
        } else if (shape == NetworkEnums.PacketShape.TRIANGLE) {
            maxPossibleOffsetToVertex = halfDrawSize * 1.15;
        } else {
            maxPossibleOffsetToVertex = halfDrawSize;
        }

        double noiseRatio = Math.min(noise / (double)this.size, 1.0); // Noise ratio 0 to 1, relative to packet's own size
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

        if (currentWire != null && noise > 0.05) { // Show ideal marker if noise is significant
            g2d.setColor(IDEAL_POSITION_MARKER_COLOR);
            int markerHalf = IDEAL_POSITION_MARKER_SIZE / 2;
            int idealXInt = (int)Math.round(idealPosition.x);
            int idealYInt = (int)Math.round(idealPosition.y);
            g2d.drawLine(idealXInt - markerHalf, idealYInt, idealXInt + markerHalf, idealYInt);
            g2d.drawLine(idealXInt, idealYInt - markerHalf, idealXInt, idealYInt + markerHalf);
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
            Point2D.Double directionForRotation = getVelocity();

            if (shape == NetworkEnums.PacketShape.TRIANGLE && directionForRotation != null && Math.hypot(directionForRotation.x, directionForRotation.y) > 0.01) {
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
                        path.moveTo(visualPosition.x, drawY);
                        path.lineTo(drawX + drawSize, drawY + drawSize);
                        path.lineTo(drawX, drawY + drawSize);
                        path.closePath();
                        g2d.fill(path);
                        break;
                    default:
                        g2d.fillOval(drawX, drawY, drawSize, drawSize);
                        break;
                }
            }

            if (noise > 0.05) { // Only draw noise overlay if significant
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
            if (noise > 0.05) { // Only draw noise text if significant
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
    }

    public void markForRemoval() { this.markedForRemoval = true; }
    public void setFinalStatusForPrediction(PredictedPacketStatus status) { this.finalStatusForPrediction = status; }
    public PredictedPacketStatus getFinalStatusForPrediction() { return this.finalStatusForPrediction; }

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
            this.noise = Math.min(this.size, this.noise + amount);
        }
    }

    public void resetNoise() {
        if (!markedForRemoval && this.noise > 0) {
            this.noise = 0.0;
            this.visualOffsetDirection = null; // Reset offset direction
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
        double r1 = this.getDrawSize() / 2.0;
        double r2 = other.getDrawSize() / 2.0;
        double combinedRadius = r1 + r2;
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
        Point2D.Double currentVel = getVelocity();
        String velStr = (currentVel != null) ? String.format("%.1f,%.1f (Mag:%.2f)", currentVel.x, currentVel.y, currentSpeedMagnitude) : "null";

        return String.format("Packet{ID:%d, SHP:%s, SZ:%d, N:%.1f/%d, V:%d, Ideal:(%s) Vel:(%s) Accel:%b FinalPred:%s St:%s}",
                id, shape, size, noise, this.size, baseCoinValue, idealPosStr, velStr, isAccelerating,
                (finalStatusForPrediction != null ? finalStatusForPrediction.name() : "N/A"), status);
    }
}