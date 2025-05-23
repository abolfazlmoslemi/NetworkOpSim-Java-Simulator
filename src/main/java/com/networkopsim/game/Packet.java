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
    private final int size;
    private final int baseCoinValue;

    private Point2D.Double idealPosition;
    private Point2D.Double velocity;

    private Wire currentWire = null;
    private System currentSystem = null;
    private double progressOnWire = 0.0;
    private double noise = 0.0;
    private boolean markedForRemoval = false;
    private PredictedPacketStatus finalStatusForPrediction = null;

    private double currentSpeedMagnitude = 0.0;
    private double targetSpeedMagnitude = 0.0;
    private boolean isAccelerating = false;

    private Point2D.Double visualOffsetDirection = null;
    private int initialOffsetSidePreference = 0; // Now determined by ID

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
        // MODIFIED FOR DETERMINISM: Initial offset preference based on packet ID
        this.initialOffsetSidePreference = this.id % 2;
    }

    public Point2D.Double getVelocity() {
        if (this.velocity == null) return new Point2D.Double(0,0);
        return new Point2D.Double(this.velocity.x, this.velocity.y);
    }

    public void setWire(Wire wire, boolean compatiblePortExit) {
        this.currentWire = Objects.requireNonNull(wire, "Cannot set a null wire for packet " + id);
        this.progressOnWire = 0.0;
        this.currentSystem = null; // Packet is on a wire, not in a system
        // this.finalStatusForPrediction = null; // Reset only if it was definitively terminal.
        // If it was QUEUED, and now put on wire, status should clear.
        if (this.finalStatusForPrediction == PredictedPacketStatus.QUEUED ||
                this.finalStatusForPrediction == PredictedPacketStatus.STALLED_AT_NODE) {
            this.finalStatusForPrediction = null;
        }


        Port startPort = wire.getStartPort();
        if (startPort == null || startPort.getPosition() == null) {
            // java.lang.System.err.println("WARN: Packet " + id + " assigned to wire " + wire.getId() + " with invalid start port/position! Resetting state.");
            this.idealPosition = new Point2D.Double(0, 0); this.velocity = new Point2D.Double(0, 0); this.currentSpeedMagnitude = 0.0; this.targetSpeedMagnitude = 0.0; this.isAccelerating = false; return;
        }
        Point startPosPoint = startPort.getPosition();
        this.idealPosition = new Point2D.Double(startPosPoint.x, startPosPoint.y);

        double initialSpeed;
        if (this.shape == NetworkEnums.PacketShape.SQUARE) {
            initialSpeed = compatiblePortExit ? BASE_SPEED_MAGNITUDE * SQUARE_COMPATIBLE_SPEED_FACTOR : BASE_SPEED_MAGNITUDE;
            this.isAccelerating = false;
            this.targetSpeedMagnitude = initialSpeed;
        } else if (this.shape == NetworkEnums.PacketShape.TRIANGLE) {
            initialSpeed = BASE_SPEED_MAGNITUDE; // Triangles start at base speed
            this.isAccelerating = !compatiblePortExit; // Accelerate if exiting non-compatible port
            this.targetSpeedMagnitude = this.isAccelerating ? MAX_SPEED_MAGNITUDE : initialSpeed;
        } else { // Should not happen with current PacketShapes
            initialSpeed = BASE_SPEED_MAGNITUDE;
            this.isAccelerating = false;
            this.targetSpeedMagnitude = initialSpeed;
        }
        this.currentSpeedMagnitude = initialSpeed;

        Point2D.Double dir = wire.getDirectionVector();
        if (dir == null) { // Should not happen if wire is valid
            this.velocity = new Point2D.Double(0,0); this.currentSpeedMagnitude = 0.0; this.targetSpeedMagnitude = 0.0; this.isAccelerating = false;
        } else {
            this.velocity = new Point2D.Double(dir.x * this.currentSpeedMagnitude, dir.y * this.currentSpeedMagnitude);
        }
    }

    public void setCurrentSystem(System system) {
        this.currentSystem = system; // Can be null if packet leaves system (e.g., put on wire)
        // this.finalStatusForPrediction = null; // Reset only if it was terminal.
        // If it was ON_WIRE and enters system, it becomes QUEUED/STALLED.
        if (this.finalStatusForPrediction == PredictedPacketStatus.ON_WIRE) {
            this.finalStatusForPrediction = null; // Will be set to QUEUED or STALLED by system logic
        }

        if (system != null) {
            this.currentWire = null; // No longer on a wire
            this.progressOnWire = 0.0;
            Point sysPosPoint = system.getPosition(); // Typically center of system for visualization when queued
            this.idealPosition = (sysPosPoint != null) ? new Point2D.Double(sysPosPoint.x, sysPosPoint.y) : new Point2D.Double(0,0);
            this.velocity = new Point2D.Double(0, 0); // No velocity when in a system's queue
            this.currentSpeedMagnitude = 0.0;
            this.targetSpeedMagnitude = 0.0;
            this.isAccelerating = false;
            // System's receivePacket logic will set finalStatusForPrediction to QUEUED, STALLED, or DELIVERED/LOST.
        }
    }


    public void update(GamePanel gamePanel, boolean isAiryamanActive, boolean isPredictionRun) {
        if (markedForRemoval || currentSystem != null) { // Packets in systems are processed by the system's queue logic
            return;
        }
        if (currentWire == null) { // Packet is not in a system and not on a wire - should be marked for removal or error
            this.velocity = new Point2D.Double(0,0);
            this.currentSpeedMagnitude = 0.0;
            this.isAccelerating = false;
            if (!isPredictionRun && !this.markedForRemoval) { // Avoid spamming console during prediction
                // java.lang.System.err.println("Packet " + id + " has no wire and not in system. Marking as LOST.");
            }
            // Ensure it's properly handled as lost if this state is reached unexpectedly
            if (this.finalStatusForPrediction != PredictedPacketStatus.LOST &&
                    this.finalStatusForPrediction != PredictedPacketStatus.DELIVERED) {
                setFinalStatusForPrediction(PredictedPacketStatus.LOST);
            }
            gamePanel.packetLostInternal(this, isPredictionRun);
            return;
        }

        // Update speed if accelerating (for TRIANGLE packets from non-compatible ports)
        Point2D.Double wireDir = currentWire.getDirectionVector();
        if (wireDir == null) { // Should not happen for a valid wire
            velocity = new Point2D.Double(0,0); currentSpeedMagnitude = 0; isAccelerating = false;
        } else {
            if (isAccelerating) {
                if (currentSpeedMagnitude < targetSpeedMagnitude) {
                    currentSpeedMagnitude = Math.min(targetSpeedMagnitude, currentSpeedMagnitude + TRIANGLE_ACCELERATION_RATE);
                } else {
                    currentSpeedMagnitude = targetSpeedMagnitude; // Reached target speed
                    isAccelerating = false;
                }
            }
            // Update velocity vector based on current speed and wire direction
            velocity.x = wireDir.x * currentSpeedMagnitude;
            velocity.y = wireDir.y * currentSpeedMagnitude;
        }


        // Update ideal position based on velocity
        idealPosition.x += velocity.x;
        idealPosition.y += velocity.y;

        // Update progress on wire
        double wireLength = currentWire.getLength();
        if (wireLength > 1e-6) { // Avoid division by zero for very short wires
            Point2D.Double wireStartPos = currentWire.getStartPort().getPrecisePosition();
            if (wireStartPos != null) {
                double distFromStart = idealPosition.distance(wireStartPos);
                progressOnWire = distFromStart / wireLength;
            } else { // Should not happen if start port is valid
                progressOnWire = 1.0; // Assume end of wire if start position is invalid
            }
        } else {
            progressOnWire = 1.0; // Effectively at the end of a zero-length wire
        }
        progressOnWire = Math.max(0.0, Math.min(1.0, progressOnWire)); // Clamp progress

        // Check for loss due to excessive noise
        if (!markedForRemoval && noise >= this.size) {
            setFinalStatusForPrediction(PredictedPacketStatus.LOST);
            gamePanel.packetLostInternal(this, isPredictionRun);
            return; // Packet is lost, no further updates
        }

        // Check if packet reached the end of the wire
        if (progressOnWire >= 1.0 - 1e-9) { // Use a small tolerance for floating point comparisons
            Port endPort = currentWire.getEndPort();
            if (endPort == null || endPort.getPosition() == null) { // End port is invalid
                setFinalStatusForPrediction(PredictedPacketStatus.LOST);
                gamePanel.packetLostInternal(this, isPredictionRun);
                return;
            }
            Point endPortPoint = endPort.getPosition();
            idealPosition.setLocation(endPortPoint.x, endPortPoint.y); // Snap to end port position

            System targetSystem = endPort.getParentSystem();
            if (targetSystem != null) {
                targetSystem.receivePacket(this, gamePanel, isPredictionRun); // Deliver to next system
            } else { // End port is not connected to a system (should not happen with valid wires)
                setFinalStatusForPrediction(PredictedPacketStatus.LOST);
                gamePanel.packetLostInternal(this, isPredictionRun);
            }
        }
    }


    public void setVisualOffsetDirectionFromForce(Point2D.Double forceDirection) {
        if (currentWire == null || forceDirection == null || (forceDirection.x == 0 && forceDirection.y == 0)) {
            // If no force or no wire, try to set a default perpendicular based on initial preference
            if (this.visualOffsetDirection == null && currentWire != null) {
                Point2D.Double wireDir = currentWire.getDirectionVector();
                if (wireDir != null) {
                    // Initial perpendicular: (-dy, dx)
                    this.visualOffsetDirection = new Point2D.Double(-wireDir.y, wireDir.x);
                    // Apply preference (0 or 1)
                    if (this.initialOffsetSidePreference == 1) { // if 1, flip to (dy, -dx)
                        this.visualOffsetDirection.x *= -1;
                        this.visualOffsetDirection.y *= -1;
                    }
                    // Normalize
                    double mag = Math.hypot(this.visualOffsetDirection.x, this.visualOffsetDirection.y);
                    if (mag > 1e-6) {
                        this.visualOffsetDirection.x /= mag;
                        this.visualOffsetDirection.y /= mag;
                    } else { // Should not happen if wireDir is non-zero
                        this.visualOffsetDirection = new Point2D.Double(0,1); // Default upwards
                    }
                }
            }
            return;
        }

        Point2D.Double wireDir = currentWire.getDirectionVector();
        if (wireDir == null) { // Should not happen for a valid wire
            if (this.visualOffsetDirection == null) this.visualOffsetDirection = new Point2D.Double(0,1); // Default upwards
            return;
        }

        // Project force onto the perpendicular of the wire's direction
        // Perpendicular 1: (-wireDir.y, wireDir.x)
        // Perpendicular 2: (wireDir.y, -wireDir.x)
        Point2D.Double perp1 = new Point2D.Double(-wireDir.y, wireDir.x); // One perpendicular direction

        // Dot product of force with perp1. Sign determines which perpendicular is closer to force.
        double dotProductWithPerp1 = (forceDirection.x * perp1.x) + (forceDirection.y * perp1.y);

        if (dotProductWithPerp1 >= 0) { // Force is generally in the direction of perp1 or obtuse to it
            this.visualOffsetDirection = perp1;
        } else { // Force is generally in the direction of -perp1 (which is perp2)
            this.visualOffsetDirection = new Point2D.Double(-perp1.x, -perp1.y);
        }
        // Normalization happens in calculateCurrentVisualOffset if needed, or here if preferred
        double mag = Math.hypot(this.visualOffsetDirection.x, this.visualOffsetDirection.y);
        if (mag > 1e-6) {
            this.visualOffsetDirection.x /= mag;
            this.visualOffsetDirection.y /= mag;
        } else {
            // This case means wireDir was (0,0) or force was parallel to wireDir (dot product would be 0 with perpendiculars)
            // Fallback to initial preference if this happens
            Point2D.Double wireD = currentWire.getDirectionVector(); // Re-fetch, though should be same
            this.visualOffsetDirection = new Point2D.Double(-wireD.y, wireD.x);
            if (this.initialOffsetSidePreference == 1) {
                this.visualOffsetDirection.x *= -1;
                this.visualOffsetDirection.y *= -1;
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
        if (noise == 0 || currentWire == null) { // No offset if no noise or not on a wire
            return new Point2D.Double(0, 0);
        }

        // Ensure visualOffsetDirection is initialized (perpendicular to wire, respecting preference)
        if (this.visualOffsetDirection == null) {
            Point2D.Double wireDir = currentWire.getDirectionVector();
            if (wireDir == null) return new Point2D.Double(0,0); // Cannot determine offset

            // Default perpendicular based on initial preference
            this.visualOffsetDirection = new Point2D.Double(-wireDir.y, wireDir.x); // (-dy, dx)
            if (this.initialOffsetSidePreference == 1) { // if 1, flip to (dy, -dx)
                this.visualOffsetDirection.x *= -1;
                this.visualOffsetDirection.y *= -1;
            }
            // Normalize
            double mag = Math.hypot(this.visualOffsetDirection.x, this.visualOffsetDirection.y);
            if (mag > 1e-6) {
                this.visualOffsetDirection.x /= mag;
                this.visualOffsetDirection.y /= mag;
            } else { // Should only happen if wireDir is (0,0)
                this.visualOffsetDirection = new Point2D.Double(0,1); // Default upwards
            }
        }

        // Calculate max possible offset (e.g., to a vertex of the shape)
        double halfDrawSize = getDrawSize() / 2.0;
        double maxPossibleOffsetToVertex = 0;

        // These are simplified approximations of "how far out" the shape's corner could be
        if (shape == NetworkEnums.PacketShape.SQUARE) {
            maxPossibleOffsetToVertex = halfDrawSize * Math.sqrt(2); // Distance to corner
        } else if (shape == NetworkEnums.PacketShape.TRIANGLE) {
            // For an equilateral triangle, distance from centroid to vertex is 2/3 of median.
            // If halfDrawSize is related to side length 's' (e.g. s = drawSize),
            // then median m = s * sqrt(3)/2. Centroid to vertex = (2/3)m.
            // Or, if halfDrawSize is approx the apothem (inradius), then circumradius is 2*apothem.
            // Let's use a factor that's a bit more than halfDrawSize.
            maxPossibleOffsetToVertex = halfDrawSize * 1.15; // Approximate
        } else {
            maxPossibleOffsetToVertex = halfDrawSize; // For circular/default
        }


        // Noise ratio: 0 to 1
        double noiseRatio = Math.min(noise / (double)size, 1.0);
        // Current offset magnitude based on noise ratio and max possible offset
        double currentOffsetMagnitude = maxPossibleOffsetToVertex * noiseRatio;

        return new Point2D.Double(
                this.visualOffsetDirection.x * currentOffsetMagnitude,
                this.visualOffsetDirection.y * currentOffsetMagnitude
        );
    }

    public void draw(Graphics2D g2d) {
        if (markedForRemoval || currentSystem != null || idealPosition == null) {
            // Do not draw if removed, in a system (system draws its queue), or no position
            return;
        }

        Point2D.Double calculatedOffset = calculateCurrentVisualOffset();
        Point2D.Double visualPosition = new Point2D.Double(
                idealPosition.x + calculatedOffset.x,
                idealPosition.y + calculatedOffset.y
        );

        // Draw ideal position marker only if there's noise (actual position deviates)
        // and it's on a wire (not in a system)
        if (currentWire != null && noise > 0) { // Changed from noise == 0
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
        Color packetColor = Port.getColorFromShape(shape); // Get color based on packet shape
        g2d.setColor(packetColor);

        Path2D path = null; // For triangle shape
        AffineTransform oldTransform = g2d.getTransform(); // Save current transform
        boolean transformed = false; // Flag if g2d transform was changed

        try {
            // Determine direction for rotation (for TRIANGLE shape)
            Point2D.Double directionForRotation = null;
            if (currentWire != null) { // Packet must be on a wire
                Point2D.Double currentVel = getVelocity(); // Prioritize actual velocity
                if (currentVel != null && Math.hypot(currentVel.x, currentVel.y) > 0.01) { // If moving significantly
                    directionForRotation = currentVel;
                } else if (currentWire.getDirectionVector() != null) { // Fallback to wire direction if not moving
                    directionForRotation = currentWire.getDirectionVector();
                }
            }

            if (shape == NetworkEnums.PacketShape.TRIANGLE && directionForRotation != null) {
                // Apply rotation for triangle
                g2d.translate(visualPosition.x, visualPosition.y); // Move origin to packet center
                g2d.rotate(Math.atan2(directionForRotation.y, directionForRotation.x)); // Rotate
                // Define triangle path (points relative to new origin at (0,0))
                path = new Path2D.Double();
                path.moveTo(halfSize, 0);                    // Tip pointing in direction of travel
                path.lineTo(-halfSize, -halfSize);  // Bottom-left corner
                path.lineTo(-halfSize, halfSize);   // Top-left corner
                path.closePath();
                g2d.fill(path);
                transformed = true; // Mark that transform was applied
            } else {
                // Draw other shapes without rotation
                switch (shape) {
                    case SQUARE:
                        g2d.fillRect(drawX, drawY, drawSize, drawSize);
                        break;
                    case TRIANGLE: // Fallback if no directionForRotation (e.g. on a zero-length wire)
                        path = new Path2D.Double();
                        path.moveTo(visualPosition.x, drawY); // Top point
                        path.lineTo(drawX + drawSize, drawY + drawSize); // Bottom-right
                        path.lineTo(drawX, drawY + drawSize); // Bottom-left
                        path.closePath();
                        g2d.fill(path);
                        break;
                    default: // Should not happen with current enum
                        g2d.fillOval(drawX, drawY, drawSize, drawSize); // Default to circle
                        break;
                }
            }

            // Draw noise effect overlay if noise is present
            if (noise > 0) {
                float noiseRatio = Math.min(1.0f, (float) (noise / this.size)); // Noise level 0.0 to 1.0
                int alpha = Math.min(255, 60 + (int) (noiseRatio * 195)); // Alpha increases with noise
                Color noiseEffectColor = new Color(200, 0, 0, alpha); // Reddish overlay

                Composite originalComposite = g2d.getComposite(); // Save current composite
                // Apply alpha blending for the noise effect
                g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, noiseRatio * 0.5f + 0.15f));
                g2d.setColor(noiseEffectColor);

                // Redraw the shape with noise color & alpha
                if (transformed && path != null) { // If triangle was rotated
                    g2d.fill(path);
                } else { // For non-rotated shapes or fallback triangle
                    if (shape == NetworkEnums.PacketShape.SQUARE) {
                        g2d.fillRect(drawX, drawY, drawSize, drawSize);
                    } else if (shape == NetworkEnums.PacketShape.TRIANGLE && path != null) {
                        g2d.fill(path); // Use pre-defined path if available
                    } else { // Fallback for other shapes or if triangle path wasn't made
                        g2d.fillOval(drawX, drawY, drawSize, drawSize);
                    }
                }
                g2d.setComposite(originalComposite); // Restore original composite
            }
        } finally {
            if (transformed) {
                g2d.setTransform(oldTransform); // Restore original transform if it was changed
            }
            // Draw noise level text above the packet
            String noiseString = String.format("N:%.1f", noise);
            g2d.setFont(NOISE_FONT);
            g2d.setColor(NOISE_TEXT_COLOR);
            FontMetrics fm = g2d.getFontMetrics();
            int textWidth = fm.stringWidth(noiseString);
            // Position text centered above the visual representation of the packet
            int textX = (int) Math.round(visualPosition.x - textWidth / 2.0);
            int textY = drawY - fm.getDescent() - 1; // drawY is top of packet, move up for text
            g2d.drawString(noiseString, textX, textY);
        }
    }

    public void markForRemoval() { this.markedForRemoval = true; }
    public void setFinalStatusForPrediction(PredictedPacketStatus status) { this.finalStatusForPrediction = status; }
    public PredictedPacketStatus getFinalStatusForPrediction() { return this.finalStatusForPrediction; }

    public int getId() { return id; }
    public NetworkEnums.PacketShape getShape() { return shape; }
    public int getSize() { return size; }
    public int getBaseCoinValue() { return baseCoinValue; }

    public Point getPosition() { // Returns the visual position as java.awt.Point
        if (idealPosition == null) return new Point(); // Default if no ideal position
        Point2D.Double offset = calculateCurrentVisualOffset();
        return new Point(
                (int)Math.round(idealPosition.x + offset.x),
                (int)Math.round(idealPosition.y + offset.y)
        );
    }

    public Point2D.Double getPositionDouble() { // Returns the visual position as Point2D.Double
        if (idealPosition == null) return new Point2D.Double(); // Default
        Point2D.Double offset = calculateCurrentVisualOffset();
        return new Point2D.Double(
                idealPosition.x + offset.x,
                idealPosition.y + offset.y
        );
    }
    public Point2D.Double getIdealPositionDouble() { // Returns the ideal (non-offset) position
        return (idealPosition != null) ? new Point2D.Double(idealPosition.x, idealPosition.y) : new Point2D.Double();
    }

    public Wire getCurrentWire() { return currentWire; }
    public double getProgressOnWire() { return progressOnWire; }
    public double getNoise() { return noise; }
    public boolean isMarkedForRemoval() { return markedForRemoval; }
    public System getCurrentSystem() { return currentSystem; }
    public int getDrawSize() { return BASE_DRAW_SIZE + (size * 2); } // Size for drawing depends on packet's 'size' property

    public void addNoise(double amount) {
        if (amount > 0 && !markedForRemoval) {
            double oldNoise = this.noise;
            this.noise = Math.min(this.size, this.noise + amount); // Noise capped by packet size
            // If noise increased and visual offset direction wasn't set, initialize it
            // (initialOffsetSidePreference is now deterministic, so this won't use Random)
            if (this.noise > oldNoise && this.visualOffsetDirection == null && currentWire != null) {
                // visualOffsetDirection will be calculated on next call to calculateCurrentVisualOffset or setVisualOffsetDirectionFromForce
            }
        }
    }

    public void resetNoise() {
        if (!markedForRemoval && this.noise > 0) {
            this.noise = 0.0;
            this.visualOffsetDirection = null; // Reset offset direction, will be recalculated if noise reappears
        }
    }

    public boolean collidesWith(Packet other) {
        if (this == other || other == null) return false; // Cannot collide with self or null
        Point2D.Double thisPos = this.getPositionDouble(); // Use visual position for collision
        Point2D.Double otherPos = other.getPositionDouble();

        if (thisPos == null || otherPos == null) return false; // Cannot collide if positions are unknown
        // No collision if either packet is in a system or marked for removal
        if (this.currentSystem != null || other.currentSystem != null || this.markedForRemoval || other.markedForRemoval) {
            return false;
        }

        // Simple circular collision detection based on draw size
        double distSq = thisPos.distanceSq(otherPos);
        double r1 = this.getDrawSize() / 2.0;
        double r2 = other.getDrawSize() / 2.0;
        double combinedRadius = r1 + r2;
        double collisionThreshold = combinedRadius * COLLISION_RADIUS_FACTOR; // Apply factor
        double collisionThresholdSq = collisionThreshold * collisionThreshold;

        return distSq < collisionThresholdSq; // True if distance squared is less than threshold squared
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Packet packet = (Packet) o;
        return id == packet.id; // Packets are equal if their IDs are equal
    }

    @Override
    public int hashCode() {
        return Objects.hash(id); // Hash based on ID
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
                id, shape, size, noise, size, baseCoinValue, idealPosStr, velStr, isAccelerating,
                (finalStatusForPrediction != null ? finalStatusForPrediction.name() : "N/A"), status);
    }
}