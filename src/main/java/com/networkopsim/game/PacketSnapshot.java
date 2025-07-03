package com.networkopsim.game;
import java.awt.Point;
import java.awt.geom.Point2D;
import java.util.Objects;

/**
 * Stores the visual state of a packet as determined by the fast prediction simulation
 * at a specific viewed time. This is primarily for rendering purposes.
 */
public class PacketSnapshot {
    private final int originalPacketId;
    private final NetworkEnums.PacketShape shape;
    private final Point visualPosition; // This is java.awt.Point
    private final PredictedPacketStatus status;
    private final double progressOnWire;
    private final int systemIdIfStalledOrQueued;
    private final double noiseLevel;
    private final Point2D.Double idealPosition; // This is java.awt.geom.Point2D.Double
    private final Point2D.Double rotationDirection; // This is java.awt.geom.Point2D.Double

    public PacketSnapshot(Packet packet, PredictedPacketStatus status) {
        Objects.requireNonNull(packet, "Source packet for snapshot cannot be null");
        this.originalPacketId = packet.getId();
        this.shape = packet.getShape();
        this.visualPosition = packet.getPosition(); // Packet.getPosition() returns Point
        this.idealPosition = packet.getIdealPositionDouble(); // Packet.getIdealPositionDouble() returns Point2D.Double
        this.status = status;
        this.noiseLevel = packet.getNoise();

        if (status == PredictedPacketStatus.ON_WIRE) {
            this.progressOnWire = packet.getProgressOnWire();
            this.systemIdIfStalledOrQueued = -1;
        } else if (status == PredictedPacketStatus.QUEUED || status == PredictedPacketStatus.STALLED_AT_NODE) {
            this.progressOnWire = 0.0;
            this.systemIdIfStalledOrQueued = (packet.getCurrentSystem() != null) ? packet.getCurrentSystem().getId() : -2;
        } else {
            this.progressOnWire = (status == PredictedPacketStatus.DELIVERED) ? 1.0 : 0.0;
            this.systemIdIfStalledOrQueued = -1;
        }

        // === CORRECTED LOGIC FOR ROTATION ===
        if (packet.getShape() == NetworkEnums.PacketShape.TRIANGLE) {
            Point2D.Double packetVelocity = packet.getVelocity(); // Get the packet's current velocity.

            // The velocity is the most accurate representation of the packet's direction.
            // If the velocity has a meaningful magnitude, use it for rotation.
            if (packetVelocity != null && Math.hypot(packetVelocity.x, packetVelocity.y) > 0.01) {
                this.rotationDirection = packetVelocity;
            } else {
                // If the packet is not moving, there's no inherent direction. No rotation is best.
                this.rotationDirection = null;
            }
        } else {
            this.rotationDirection = null;
        }
    }

    public PacketSnapshot(int futurePacketId, NetworkEnums.PacketShape shape) {
        this.originalPacketId = futurePacketId;
        this.shape = Objects.requireNonNull(shape, "PacketSnapshot shape cannot be null for future packet");
        this.status = PredictedPacketStatus.NOT_YET_GENERATED;
        this.visualPosition = null;
        this.idealPosition = null;
        this.progressOnWire = 0.0;
        this.systemIdIfStalledOrQueued = -1;
        this.noiseLevel = 0.0;
        this.rotationDirection = null;
    }

    // --- Getters ---
    public int getOriginalPacketId() { return originalPacketId; }
    public NetworkEnums.PacketShape getShape() { return shape; }
    public Point getVisualPosition() { return (visualPosition != null) ? new Point(visualPosition) : null; }
    public Point2D.Double getIdealPosition() {
        return (idealPosition != null) ? new Point2D.Double(idealPosition.x, idealPosition.y) : null;
    }
    public PredictedPacketStatus getStatus() { return status; }
    public double getProgressOnWire() { return progressOnWire; }
    public int getSystemIdIfStalledOrQueued() { return systemIdIfStalledOrQueued; }
    public double getNoiseLevel() { return noiseLevel; }
    public Point2D.Double getRotationDirection() {
        return (rotationDirection != null) ? new Point2D.Double(rotationDirection.x, rotationDirection.y) : null;
    }

    public int getDrawSize() {
        int sizeUnits;
        switch (shape) {
            case SQUARE:   sizeUnits = 2; break;
            case TRIANGLE: sizeUnits = 3; break;
            default:       sizeUnits = 1; break;
        }
        return Packet.BASE_DRAW_SIZE + (sizeUnits * 2);
    }

    @Override
    public String toString() {
        String systemInfo = "";
        if (status == PredictedPacketStatus.STALLED_AT_NODE || status == PredictedPacketStatus.QUEUED) {
            systemInfo = ", systemId=" + systemIdIfStalledOrQueued;
        }
        String posStr = (visualPosition != null) ? visualPosition.x + "," + visualPosition.y : "null";
        String idealPosStr = (idealPosition != null) ? String.format("%.1f,%.1f", idealPosition.x, idealPosition.y) : "null";

        return "PacketSnapshot{" +
                "pktId=" + originalPacketId +
                ", shape=" + shape +
                ", visPos=" + posStr +
                ", idealPos=" + idealPosStr +
                ", status=" + status +
                (status == PredictedPacketStatus.ON_WIRE ? (", prog=" + String.format("%.2f", progressOnWire)) : "") +
                systemInfo +
                ", noise=" + String.format("%.1f", noiseLevel) +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PacketSnapshot that = (PacketSnapshot) o;
        return originalPacketId == that.originalPacketId &&
                Double.compare(that.progressOnWire, progressOnWire) == 0 &&
                systemIdIfStalledOrQueued == that.systemIdIfStalledOrQueued &&
                Double.compare(that.noiseLevel, noiseLevel) == 0 &&
                shape == that.shape &&
                Objects.equals(visualPosition, that.visualPosition) &&
                Objects.equals(idealPosition, that.idealPosition) &&
                status == that.status &&
                Objects.equals(rotationDirection, that.rotationDirection);
    }

    @Override
    public int hashCode() {
        return Objects.hash(originalPacketId, shape, visualPosition, idealPosition, status, progressOnWire, systemIdIfStalledOrQueued, noiseLevel, rotationDirection);
    }
}