// FILE: PacketSnapshot.java
import java.awt.Point;
import java.util.Objects;

/**
 * Stores the predicted state of a packet at a specific time during the
 * pre-simulation time scrubbing phase.
 */
public class PacketSnapshot {
    private final int originalPacketId;             // ID of the packet this snapshot represents (or a future ID)
    private final NetworkEnums.PacketShape shape;   // Shape of the packet
    private final Point position;                   // Predicted position (null if NOT_YET_GENERATED)
    private final PredictedPacketStatus status;     // Predicted status (e.g., ON_WIRE, DELIVERED)
    private final double progressOnWire;            // Predicted progress [0,1] if status is ON_WIRE
    private final int systemIdIfStalledOrQueued;    // ID of the system if status is STALLED_AT_NODE or QUEUED

    /** Constructor for packets not yet generated at the viewed time. */
    public PacketSnapshot(int originalPacketId, NetworkEnums.PacketShape shape) {
        this.originalPacketId = originalPacketId;
        this.shape = Objects.requireNonNull(shape, "PacketSnapshot shape cannot be null");
        this.status = PredictedPacketStatus.NOT_YET_GENERATED;
        this.position = null; // No position yet
        this.progressOnWire = 0.0;
        this.systemIdIfStalledOrQueued = -1; // Not applicable
    }

    /** Constructor for packets predicted ON_WIRE, DELIVERED, or LOST (with position). */
    public PacketSnapshot(int originalPacketId, NetworkEnums.PacketShape shape, Point position, PredictedPacketStatus status, double progressOnWire) {
        // Validate status vs constructor usage
        if (status == PredictedPacketStatus.QUEUED || status == PredictedPacketStatus.STALLED_AT_NODE || status == PredictedPacketStatus.NOT_YET_GENERATED) {
            throw new IllegalArgumentException("Incorrect constructor used for status: " + status + ". Use the systemId constructor or the no-position constructor.");
        }
        if ((status == PredictedPacketStatus.ON_WIRE || status == PredictedPacketStatus.DELIVERED || status == PredictedPacketStatus.LOST) && position == null) {
            // For LOST status, position might be the last known valid position or the target port's position. Null is problematic.
            // Let's require a position for LOST as well, representing where it was lost.
            throw new IllegalArgumentException("Position cannot be null for status: " + status);
        }

        this.originalPacketId = originalPacketId;
        this.shape = Objects.requireNonNull(shape, "PacketSnapshot shape cannot be null");
        this.position = (position != null) ? new Point(position) : null; // Defensive copy
        this.status = status;
        // Ensure progress is valid for ON_WIRE, default otherwise
        this.progressOnWire = (status == PredictedPacketStatus.ON_WIRE)
                ? Math.max(0.0, Math.min(1.0, progressOnWire)) // Clamp progress
                : (status == PredictedPacketStatus.DELIVERED ? 1.0 : 0.0); // Default progress for non-wire states
        this.systemIdIfStalledOrQueued = -1; // Not applicable for these statuses
    }

    /** Constructor for packets predicted STALLED_AT_NODE or QUEUED (at a system location). */
    public PacketSnapshot(int originalPacketId, NetworkEnums.PacketShape shape, Point systemPosition, PredictedPacketStatus status, int systemId) {
        // Validate status vs constructor usage
        if (status != PredictedPacketStatus.STALLED_AT_NODE && status != PredictedPacketStatus.QUEUED) {
            throw new IllegalArgumentException("Incorrect constructor used for status: " + status + ". Use the position/progress constructor or the no-position constructor.");
        }
        if (systemPosition == null) {
            throw new IllegalArgumentException("System position cannot be null for status: " + status);
        }
        if (systemId < 0) {
            throw new IllegalArgumentException("System ID must be non-negative for status: " + status);
        }

        this.originalPacketId = originalPacketId;
        this.shape = Objects.requireNonNull(shape, "PacketSnapshot shape cannot be null");
        this.position = new Point(systemPosition); // Position is the system's center
        this.status = status;
        this.progressOnWire = 0.0; // No progress on wire when stalled/queued
        this.systemIdIfStalledOrQueued = systemId;
    }


    // --- Getters ---
    public int getOriginalPacketId() { return originalPacketId; }
    public NetworkEnums.PacketShape getShape() { return shape; }
    public Point getPosition() { return (position != null) ? new Point(position) : null; } // Return copy
    public PredictedPacketStatus getStatus() { return status; }
    public double getProgressOnWire() { return progressOnWire; }
    public int getSystemIdIfStalledOrQueued() { return systemIdIfStalledOrQueued; }

    /** Calculates draw size based on packet shape using Packet constants. */
    public int getDrawSize() {
        int sizeUnits;
        switch (shape) {
            case SQUARE:   sizeUnits = 2; break;
            case TRIANGLE: sizeUnits = 3; break;
            default:       sizeUnits = 1; break; // Fallback
        }
        // Assumes Packet.BASE_DRAW_SIZE and scaling factor are accessible or replicated here
        // Ideally, Packet class would expose this calculation statically or via an instance.
        // Using the logic directly from Packet for now:
        return Packet.BASE_DRAW_SIZE + (sizeUnits * 2);
    }

    @Override
    public String toString() {
        String systemInfo = "";
        if (status == PredictedPacketStatus.STALLED_AT_NODE || status == PredictedPacketStatus.QUEUED) {
            systemInfo = ", systemId=" + systemIdIfStalledOrQueued;
        }
        return "PacketSnapshot{" +
                "pktId=" + originalPacketId +
                ", shape=" + shape +
                ", pos=" + (position != null ? position.x + "," + position.y : "null") +
                ", status=" + status +
                (status == PredictedPacketStatus.ON_WIRE ? (", prog=" + String.format("%.2f", progressOnWire)) : "") +
                systemInfo +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PacketSnapshot that = (PacketSnapshot) o;
        // Compare all fields for equality
        return originalPacketId == that.originalPacketId &&
                Double.compare(that.progressOnWire, progressOnWire) == 0 &&
                systemIdIfStalledOrQueued == that.systemIdIfStalledOrQueued &&
                shape == that.shape &&
                Objects.equals(position, that.position) && // Uses Point's equals
                status == that.status;
    }

    @Override
    public int hashCode() {
        return Objects.hash(originalPacketId, shape, position, status, progressOnWire, systemIdIfStalledOrQueued);
    }
}