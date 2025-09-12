package com.networkopsim.game.model.state;


import com.networkopsim.game.model.core.Packet;
import com.networkopsim.game.model.enums.NetworkEnums;
import com.networkopsim.game.model.state.PredictedPacketStatus;

// ===== File: PacketSnapshot.java =====
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
    private final Point visualPosition;
    private final PredictedPacketStatus status;
    private final double progressOnWire;
    private final int systemIdIfStalledOrQueued;
    private final double noiseLevel;
    private final Point2D.Double idealPosition;
    private final Point2D.Double rotationDirection;

    // --- NEW FIELD TO FIX THE COMPILE ERROR ---
    private final NetworkEnums.PacketType packetType;

    public PacketSnapshot(Packet packet, PredictedPacketStatus status) {
        Objects.requireNonNull(packet, "Source packet for snapshot cannot be null");
        this.originalPacketId = packet.getId();
        this.shape = packet.getShape();
        this.visualPosition = packet.getPosition();
        this.idealPosition = packet.getIdealPositionDouble();
        this.status = status;
        this.noiseLevel = packet.getNoise();

        // --- Initialize the new field ---
        this.packetType = packet.getPacketType();

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

        if (packet.getShape() == NetworkEnums.PacketShape.TRIANGLE) {
            Point2D.Double packetVelocity = packet.getVelocity();
            if (packetVelocity != null && Math.hypot(packetVelocity.x, packetVelocity.y) > 0.01) {
                this.rotationDirection = packetVelocity;
            } else {
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
        this.packetType = NetworkEnums.PacketType.NORMAL; // Default for future packets
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

    // --- NEW GETTER TO FIX THE COMPILE ERROR ---
    public NetworkEnums.PacketType getPacketType() {
        return packetType;
    }

    public int getDrawSize() {
        int sizeUnits;
        switch (shape) {
            case SQUARE:   sizeUnits = 2; break;
            case TRIANGLE: sizeUnits = 3; break;
            case CIRCLE:   sizeUnits = 1; break;
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
                ", type=" + packetType + // Added for debugging
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
                packetType == that.packetType && // Added to equals
                Objects.equals(visualPosition, that.visualPosition) &&
                Objects.equals(idealPosition, that.idealPosition) &&
                status == that.status &&
                Objects.equals(rotationDirection, that.rotationDirection);
    }

    @Override
    public int hashCode() {
        return Objects.hash(originalPacketId, shape, packetType, visualPosition, idealPosition, status, progressOnWire, systemIdIfStalledOrQueued, noiseLevel, rotationDirection);
    }
}

