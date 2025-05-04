import java.awt.Point;
import java.util.Objects;


public class PacketSnapshot {
    private final int originalPacketId;             
    private final NetworkEnums.PacketShape shape;   
    private final Point position;                   
    private final PredictedPacketStatus status;     
    private final double progressOnWire;            
    private final int systemIdIfStalledOrQueued;    

    
    public PacketSnapshot(int originalPacketId, NetworkEnums.PacketShape shape) {
        this.originalPacketId = originalPacketId;
        this.shape = Objects.requireNonNull(shape, "PacketSnapshot shape cannot be null");
        this.status = PredictedPacketStatus.NOT_YET_GENERATED;
        this.position = null; 
        this.progressOnWire = 0.0;
        this.systemIdIfStalledOrQueued = -1; 
    }

    
    public PacketSnapshot(int originalPacketId, NetworkEnums.PacketShape shape, Point position, PredictedPacketStatus status, double progressOnWire) {
        
        if (status == PredictedPacketStatus.QUEUED || status == PredictedPacketStatus.STALLED_AT_NODE || status == PredictedPacketStatus.NOT_YET_GENERATED) {
            throw new IllegalArgumentException("Incorrect constructor used for status: " + status + ". Use the systemId constructor or the no-position constructor.");
        }
        if ((status == PredictedPacketStatus.ON_WIRE || status == PredictedPacketStatus.DELIVERED || status == PredictedPacketStatus.LOST) && position == null) {
            
            
            throw new IllegalArgumentException("Position cannot be null for status: " + status);
        }

        this.originalPacketId = originalPacketId;
        this.shape = Objects.requireNonNull(shape, "PacketSnapshot shape cannot be null");
        this.position = (position != null) ? new Point(position) : null; 
        this.status = status;
        
        this.progressOnWire = (status == PredictedPacketStatus.ON_WIRE)
                ? Math.max(0.0, Math.min(1.0, progressOnWire)) 
                : (status == PredictedPacketStatus.DELIVERED ? 1.0 : 0.0); 
        this.systemIdIfStalledOrQueued = -1; 
    }

    
    public PacketSnapshot(int originalPacketId, NetworkEnums.PacketShape shape, Point systemPosition, PredictedPacketStatus status, int systemId) {
        
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
        this.position = new Point(systemPosition); 
        this.status = status;
        this.progressOnWire = 0.0; 
        this.systemIdIfStalledOrQueued = systemId;
    }


    
    public int getOriginalPacketId() { return originalPacketId; }
    public NetworkEnums.PacketShape getShape() { return shape; }
    public Point getPosition() { return (position != null) ? new Point(position) : null; } 
    public PredictedPacketStatus getStatus() { return status; }
    public double getProgressOnWire() { return progressOnWire; }
    public int getSystemIdIfStalledOrQueued() { return systemIdIfStalledOrQueued; }

    
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
        
        return originalPacketId == that.originalPacketId &&
                Double.compare(that.progressOnWire, progressOnWire) == 0 &&
                systemIdIfStalledOrQueued == that.systemIdIfStalledOrQueued &&
                shape == that.shape &&
                Objects.equals(position, that.position) && 
                status == that.status;
    }

    @Override
    public int hashCode() {
        return Objects.hash(originalPacketId, shape, position, status, progressOnWire, systemIdIfStalledOrQueued);
    }
}
