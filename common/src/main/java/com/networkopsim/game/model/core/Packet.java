// ===== File: Packet.java (FINAL REVISED for 'common' module) =====
// ===== MODULE: common =====

package com.networkopsim.game.model.core;

import com.networkopsim.game.model.enums.NetworkEnums;
import com.networkopsim.game.model.state.PredictedPacketStatus;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.io.Serializable;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class Packet implements Serializable {
    private static final long serialVersionUID = 3L; // Version updated
    public static final double BASE_SPEED_MAGNITUDE = 2.0;
    public static final int BASE_DRAW_SIZE = 8;
    public static final double SQUARE_COMPATIBLE_SPEED_FACTOR = 0.5;
    public static final double TRIANGLE_ACCELERATION_RATE = 0.018;
    public static final double MESSENGER_DECELERATION_RATE = 0.04;
    public static final double MAX_SPEED_MAGNITUDE = 4.1;
    public static final double MESSENGER_INCOMPATIBLE_SPEED_BOOST = 2.0;
    public static final double SECRET_PACKET_SLOW_SPEED_FACTOR = 0.25;
    public static final double WOBBLE_SELF_NOISE_RATE = 0.05;
    public static final double SECRET_REPULSION_DISTANCE = 100.0;
    public static final double SECRET_ATTRACTION_DISTANCE = 250.0;
    public static final double SECRET_SPEED_ADJUST_RATE = 0.05;
    public static final double ELIPHAS_REALIGNMENT_FACTOR = 0.05;
    public static final double LONG_WIRE_THRESHOLD = 700.0;
    public static final long WIRE_TIMEOUT_MS = 30000;
    private static final int POSITION_HISTORY_SIZE = 20;
    private static final double PCA_ACTIVATION_NOISE_THRESHOLD = 2.5;
    private static final double PCA_SCALE_FACTOR = 1.4;
    private static final Path2D.Double BASE_SQUARE_HITBOX;
    private static final Path2D.Double BASE_TRIANGLE_HITBOX;
    private static final Path2D.Double BASE_CIRCLE_HITBOX;
    static {
        BASE_SQUARE_HITBOX = new Path2D.Double(); BASE_SQUARE_HITBOX.moveTo(-0.5, -0.5); BASE_SQUARE_HITBOX.lineTo(0.5, -0.5); BASE_SQUARE_HITBOX.lineTo(0.5, 0.5); BASE_SQUARE_HITBOX.lineTo(-0.5, 0.5); BASE_SQUARE_HITBOX.closePath();
        BASE_TRIANGLE_HITBOX = new Path2D.Double(); BASE_TRIANGLE_HITBOX.moveTo(0.5, 0); BASE_TRIANGLE_HITBOX.lineTo(-0.5, -0.5); BASE_TRIANGLE_HITBOX.lineTo(-0.5, 0.5); BASE_TRIANGLE_HITBOX.closePath();
        BASE_CIRCLE_HITBOX = new Path2D.Double(); int sides = 8; BASE_CIRCLE_HITBOX.moveTo(0.5, 0); for (int i = 1; i < sides; i++) { double angle = 2 * Math.PI * i / sides; BASE_CIRCLE_HITBOX.lineTo(0.5 * Math.cos(angle), 0.5 * Math.sin(angle)); } BASE_CIRCLE_HITBOX.closePath();
    }
    private static final float ANGULAR_DAMPING = 0.98f;
    private float angle = 0.0f;
    private float angularVelocity = 0.0f;
    private float momentOfInertia;
    private static int nextPacketId = 0;
    private final int id;
    private final NetworkEnums.PacketShape shape;
    private NetworkEnums.PacketType packetType;
    private int size;
    private int baseCoinValue;
    private NetworkEnums.PacketType originalPacketType;
    private int originalSize;
    private int originalBaseCoinValue;
    private int protectedBySystemId = -1;
    private Point2D.Double idealPosition;
    private Point2D.Double velocity;
    private double progressOnWire = 0.0;
    private double currentSpeedMagnitude = 0.0;
    private double targetSpeedMagnitude = 0.0;
    private boolean isAccelerating = false;
    private boolean isDecelerating = false;
    private boolean isReversing = false;
    private boolean enteredViaIncompatiblePort = false;
    private double noise = 0.0;
    private Point2D.Double visualOffsetDirection = null;
    private double visualOffsetMagnitude = 0.0;
    private final int initialOffsetSidePreference;
    private long timeOnCurrentWireMs = 0;
    private boolean markedForRemoval = false;
    private transient Wire currentWire;
    private transient System currentSystem;
    private int currentWireId = -1;
    private int currentSystemId = -1;
    private int bulkParentId = -1;
    private int totalBitsInGroup = 0;
    private boolean isUpgradedSecret = false;
    private PredictedPacketStatus finalStatusForPrediction = null;
    private boolean isGhost = false;
    private int ghostForSystemId = -1;
    private transient Path2D.Double hitbox;
    private transient List<Point2D.Double> positionHistory;

    public static void resetGlobalId() { nextPacketId = 0; }

    public Packet(NetworkEnums.PacketShape shape, double startX, double startY, NetworkEnums.PacketType type) {
        this.id = nextPacketId++;
        this.shape = Objects.requireNonNull(shape, "Packet shape cannot be null");
        this.packetType = Objects.requireNonNull(type, "Packet type cannot be null");
        this.originalPacketType = type;
        this.idealPosition = new Point2D.Double(startX, startY);
        this.velocity = new Point2D.Double(0, 0);
        switch (type) {
            case SECRET: this.size = 4; this.baseCoinValue = 3; break;
            case BULK: this.size = 8; this.baseCoinValue = 8; break;
            case WOBBLE: this.size = 10; this.baseCoinValue = 10; break;
            case MESSENGER: this.size = 1; this.baseCoinValue = 1; break;
            default:
                if (shape == NetworkEnums.PacketShape.SQUARE) { this.size = 2; this.baseCoinValue = 2; }
                else if (shape == NetworkEnums.PacketShape.TRIANGLE) { this.size = 3; this.baseCoinValue = 3; }
                else { this.size = 1; this.baseCoinValue = 1; }
                break;
        }
        this.originalSize = this.size;
        this.originalBaseCoinValue = this.baseCoinValue;
        this.initialOffsetSidePreference = this.id % 2;
        this.momentOfInertia = 0.5f * this.size * this.getDrawSize();
        this.positionHistory = new LinkedList<>();
        updateHitbox();
    }

    // Server-side logic will call this.
    public void setReversing(boolean reversing) {
        this.isReversing = reversing;
    }

    public void setWire(Wire wire, boolean compatiblePortExit) {
        this.currentWire = Objects.requireNonNull(wire, "Cannot set a null wire for packet " + id);
        this.currentWireId = wire.getId();
        this.currentSystem = null;
        this.currentSystemId = -1;
        this.progressOnWire = 0.0;
        this.isReversing = false;
        this.isDecelerating = false;
        this.isAccelerating = false;
        this.timeOnCurrentWireMs = 0;
        if (finalStatusForPrediction != null && finalStatusForPrediction != PredictedPacketStatus.ON_WIRE) {
            this.finalStatusForPrediction = null;
        }
        // Speed logic is now fully managed by the server's GameEngine
    }

    // This method was missing and caused the build error.
    // Add it anywhere inside the Packet class body.
    private void updateVisualOffsetMagnitude() {
        if (noise <= 0) {
            visualOffsetMagnitude = 0;
            return;
        }
        double halfDrawSize = getDrawSize() / 2.0;
        double maxPossibleOffsetToVertex;
        if (shape == NetworkEnums.PacketShape.SQUARE) {
            maxPossibleOffsetToVertex = halfDrawSize * Math.sqrt(2);
        } else if (shape == NetworkEnums.PacketShape.TRIANGLE) {
            maxPossibleOffsetToVertex = halfDrawSize * 1.15; // Approximation for triangle
        } else { // CIRCLE
            maxPossibleOffsetToVertex = halfDrawSize;
        }
        // Calculate the ratio of current noise to the packet's total capacity (size)
        double noiseRatio = Math.min(noise / (double)this.size, 1.0);
        this.visualOffsetMagnitude = maxPossibleOffsetToVertex * noiseRatio;
    }

    public void addNoise(double amount) { if (amount > 0 && !markedForRemoval) { this.noise = Math.min(this.size, this.noise + amount); updateVisualOffsetMagnitude(); } }

    public void updateHitbox() {
        // This is a rendering-related concept, but it's okay for the data model
        // to know its own boundaries.
        boolean usePCA = (this.packetType == NetworkEnums.PacketType.WOBBLE || this.noise > PCA_ACTIVATION_NOISE_THRESHOLD) && this.positionHistory != null && this.positionHistory.size() >= 5;
        if (usePCA) { updateHitboxWithPCA(); } else { updateHitboxDefault(); }
    }
    // Add these missing getters and setters to the Packet class in the 'common' module

    public PredictedPacketStatus getFinalStatusForPrediction() {
        return this.finalStatusForPrediction;
    }

    public float getAngularVelocity() {
        return this.angularVelocity;
    }

    public boolean isUpgradedSecret() {
        return this.isUpgradedSecret;
    }

    public boolean isAccelerating() {
        return this.isAccelerating;
    }

    public double getTargetSpeedMagnitude() {
        return this.targetSpeedMagnitude;
    }

    public boolean isDecelerating() {
        return this.isDecelerating;
    }

    public void setDecelerating(boolean decelerating) {
        this.isDecelerating = decelerating;
    }

    public boolean enteredViaIncompatiblePort() {
        return this.enteredViaIncompatiblePort;
    }

    public boolean isGhost() {
        return this.isGhost;
    }
    private void updateHitboxDefault() { Path2D.Double baseShape; switch (this.shape) { case SQUARE: baseShape = BASE_SQUARE_HITBOX; break; case TRIANGLE: baseShape = BASE_TRIANGLE_HITBOX; break; default: baseShape = BASE_CIRCLE_HITBOX; } AffineTransform tx = new AffineTransform(); Point2D.Double visualPos = getVisualPosition(); if (visualPos == null) return; tx.translate(visualPos.x, visualPos.y); double totalAngle = this.angle; if (this.shape == NetworkEnums.PacketShape.TRIANGLE) { Point2D.Double direction = getVelocity(); if (direction != null && (Math.abs(direction.x) > 0.01 || Math.abs(direction.y) > 0.01)) { double angleFromVelocity = Math.atan2(direction.y, direction.x); if (isReversing) angleFromVelocity += Math.PI; totalAngle += angleFromVelocity; } } tx.rotate(totalAngle); tx.scale(getDrawSize(), getDrawSize()); this.hitbox = (Path2D.Double) baseShape.createTransformedShape(tx); }
    private void updateHitboxWithPCA() { if (positionHistory == null || positionHistory.isEmpty()) { updateHitboxDefault(); return; } double meanX = 0, meanY = 0; for (Point2D.Double p : positionHistory) { meanX += p.x; meanY += p.y; } meanX /= positionHistory.size(); meanY /= positionHistory.size(); double covXX = 0, covXY = 0, covYY = 0; for (Point2D.Double p : positionHistory) { double dx = p.x - meanX; double dy = p.y - meanY; covXX += dx * dx; covXY += dx * dy; covYY += dy * dy; } covXX /= positionHistory.size(); covXY /= positionHistory.size(); covYY /= positionHistory.size(); double trace = covXX + covYY; double det = covXX * covYY - covXY * covXY; double discriminant = Math.sqrt(trace * trace - 4 * det); double eigVal1 = (trace + discriminant) / 2; double eigVal2 = (trace - discriminant) / 2; Point2D.Double eigVec1 = new Point2D.Double(covXY, eigVal1 - covXX); Point2D.Double eigVec2 = new Point2D.Double(covXY, eigVal2 - covXX); double mag1 = eigVec1.distance(0, 0); if (mag1 > 1e-6) { eigVec1.x /= mag1; eigVec1.y /= mag1; } double mag2 = eigVec2.distance(0, 0); if (mag2 > 1e-6) { eigVec2.x /= mag2; eigVec2.y /= mag2; } double halfWidth = PCA_SCALE_FACTOR * Math.sqrt(eigVal1) + getDrawSize() / 2.0; double halfHeight = PCA_SCALE_FACTOR * Math.sqrt(eigVal2) + getDrawSize() / 2.0; Point2D.Double center = new Point2D.Double(meanX, meanY); Point2D.Double axis1 = new Point2D.Double(eigVec1.x * halfWidth, eigVec1.y * halfWidth); Point2D.Double axis2 = new Point2D.Double(eigVec2.x * halfHeight, eigVec2.y * halfHeight); Point2D.Double c1 = new Point2D.Double(center.x + axis1.x + axis2.x, center.y + axis1.y + axis2.y); Point2D.Double c2 = new Point2D.Double(center.x - axis1.x + axis2.x, center.y - axis1.y + axis2.y); Point2D.Double c3 = new Point2D.Double(center.x - axis1.x - axis2.x, center.y - axis1.y - axis2.y); Point2D.Double c4 = new Point2D.Double(center.x + axis1.x - axis2.x, center.y + axis1.y - axis2.y); Path2D.Double pcaHitbox = new Path2D.Double(); pcaHitbox.moveTo(c1.x, c1.y); pcaHitbox.lineTo(c2.x, c2.y); pcaHitbox.lineTo(c3.x, c3.y); pcaHitbox.lineTo(c4.x, c4.y); pcaHitbox.closePath(); this.hitbox = pcaHitbox; }

    // --- Getters & Setters ---
    public int getId() { return id; }
    // ... Copy ALL other getters and setters from your original file. They are all valid.
    public void rebuildTransientReferences(Map<Integer, System> systemMap, Map<Integer, Wire> wireMap) { if (this.currentSystemId != -1) this.currentSystem = systemMap.get(this.currentSystemId); if (this.currentWireId != -1) this.currentWire = wireMap.get(this.currentWireId); if (this.positionHistory == null) { this.positionHistory = new LinkedList<>(); } updateHitbox(); }
    public NetworkEnums.PacketType getPacketType() { return packetType; }
    public void setPacketType(NetworkEnums.PacketType packetType) { this.packetType = packetType; }
    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }
    public int getBaseCoinValue() { return baseCoinValue; }
    public Point2D.Double getIdealPosition() { return (idealPosition != null) ? (Point2D.Double) idealPosition.clone() : null; }
    public Point2D.Double getVisualPosition() { if (visualOffsetMagnitude > 0 && visualOffsetDirection != null && idealPosition != null) { return new Point2D.Double(idealPosition.x + visualOffsetDirection.x * visualOffsetMagnitude, idealPosition.y + visualOffsetDirection.y * visualOffsetMagnitude); } return getIdealPosition(); }
    public float getAngle() { return angle; }
    public int getDrawSize() { return BASE_DRAW_SIZE + (size * 2); }
    public double getCurrentSpeedMagnitude() { return currentSpeedMagnitude; }
    public void setCurrentSpeedMagnitude(double currentSpeedMagnitude) { this.currentSpeedMagnitude = currentSpeedMagnitude; }
    public Point2D.Double getVelocity() { if (this.velocity == null) return new Point2D.Double(0,0); return (Point2D.Double) this.velocity.clone(); }
    public void setVelocity(Point2D.Double velocity) { this.velocity = velocity; }
    public double getProgressOnWire() { return progressOnWire; }
    public void setProgressOnWire(double progressOnWire) { this.progressOnWire = progressOnWire; }
    public System getCurrentSystem() { return currentSystem; }
    public void setCurrentSystem(System system) { this.currentSystem = system; this.currentSystemId = (system != null) ? system.getId() : -1; this.currentWire = null; this.currentWireId = -1; this.progressOnWire = 0.0; this.currentSpeedMagnitude = 0.0; if (system != null) { this.idealPosition = new Point2D.Double(system.getPosition().x, system.getPosition().y); } }
    public Wire getCurrentWire() { return currentWire; }
    public double getNoise() { return noise; }
    public boolean isMarkedForRemoval() { return markedForRemoval; }
    public void markForRemoval() { this.markedForRemoval = true; }
    public NetworkEnums.PacketShape getShape() { return shape; }
    public boolean isVolumetric() { return packetType == NetworkEnums.PacketType.BULK || packetType == NetworkEnums.PacketType.WOBBLE; }
    public void setVisualOffsetDirectionFromForce(Point2D.Double forceDirection) { this.visualOffsetDirection = forceDirection; }
    public void applyTorque(Point2D.Double forceDirection, double torqueMagnitude) { /* Server logic */ }
    public void incrementTimeOnWire(long ms) { this.timeOnCurrentWireMs += ms; }
    public long getTimeOnCurrentWireMs() { return this.timeOnCurrentWireMs; }
    public boolean collidesWith(Packet other) { if (this == other || other == null || this.currentSystem != null || other.currentSystem != null || this.markedForRemoval || other.markedForRemoval) return false; if (this.hitbox == null) this.updateHitbox(); if (other.hitbox == null) other.updateHitbox(); if (this.hitbox == null || other.hitbox == null) return false; Area area1 = new Area(this.hitbox); area1.intersect(new Area(other.hitbox)); return !area1.isEmpty(); }
    public void setFinalStatusForPrediction(PredictedPacketStatus status) { this.finalStatusForPrediction = status; }
    public void configureAsBulkPart(int parentId, int totalParts) { if(this.packetType == NetworkEnums.PacketType.MESSENGER){ this.bulkParentId = parentId; this.totalBitsInGroup = totalParts; this.baseCoinValue = 0; } }
    public void revertToOriginalType() { this.packetType = this.originalPacketType; this.size = this.originalSize; this.baseCoinValue = this.originalBaseCoinValue; this.protectedBySystemId = -1; }
    public void transformToProtected(int vpnSystemId) { this.originalPacketType = this.packetType; this.originalSize = this.size; this.originalBaseCoinValue = this.baseCoinValue; this.packetType = NetworkEnums.PacketType.PROTECTED; this.size = this.originalSize * 2; this.baseCoinValue = 5; this.protectedBySystemId = vpnSystemId; }
    public void upgradeSecretPacket() { if (this.packetType == NetworkEnums.PacketType.SECRET && !this.isUpgradedSecret) { this.isUpgradedSecret = true; this.size = 6; this.baseCoinValue = 4; } }
    public boolean isReversing() { return isReversing; }
    public int getBulkParentId() { return bulkParentId; }
    public void setIdealPosition(Point2D.Double pos) { this.idealPosition = pos; }
    public void setAngle(float angle) { this.angle = angle; }
    public void setAngularVelocity(float vel) { this.angularVelocity = vel; }
    public void setAccelerating(boolean accelerating) { this.isAccelerating = accelerating; }
    public void setTargetSpeedMagnitude(double speed) { this.targetSpeedMagnitude = speed; }
    public List<Point2D.Double> getPositionHistory() { return this.positionHistory; }
    public void setEnteredViaIncompatiblePort(boolean status) { this.enteredViaIncompatiblePort = status; }

}