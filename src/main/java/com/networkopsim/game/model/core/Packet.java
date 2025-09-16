// ===== File: Packet.java (FINAL - Added Ghost State and setSize) =====

package com.networkopsim.game.model.core;

import com.networkopsim.game.controller.logic.GameEngine;
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
    private static final long serialVersionUID = 2L;
    public static final double BASE_SPEED_MAGNITUDE = 2.0;
    public static final int BASE_DRAW_SIZE = 8;
    // ... (other constants are unchanged) ...
    public static final double SQUARE_COMPATIBLE_SPEED_FACTOR = 0.5;
    private static final double TRIANGLE_ACCELERATION_RATE = 0.018;
    private static final double MESSENGER_DECELERATION_RATE = 0.04;
    public static final double MAX_SPEED_MAGNITUDE = 4.1;
    private static final double MESSENGER_INCOMPATIBLE_SPEED_BOOST = 2.0;
    private static final double SECRET_PACKET_SLOW_SPEED_FACTOR = 0.25;
    private static final double WOBBLE_SELF_NOISE_RATE = 0.05;
    private static final double SECRET_REPULSION_DISTANCE = 100.0;
    private static final double SECRET_ATTRACTION_DISTANCE = 250.0;
    private static final double SECRET_SPEED_ADJUST_RATE = 0.05;
    private static final double ELIPHAS_REALIGNMENT_FACTOR = 0.05;
    private static final double LONG_WIRE_THRESHOLD = 700.0;
    private static final long WIRE_TIMEOUT_MS = 30000;
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
    private transient com.networkopsim.game.model.core.System currentSystem;
    private int currentWireId = -1;
    private int currentSystemId = -1;
    private int bulkParentId = -1;
    private int totalBitsInGroup = 0;
    private boolean isUpgradedSecret = false;
    private enum ProtectedMovementMode { LIKE_SQUARE, LIKE_TRIANGLE, LIKE_MESSENGER }
    private ProtectedMovementMode protectedMovementMode = null;
    private PredictedPacketStatus finalStatusForPrediction = null;

    // [NEW] Fields for the Ghost Packet logic
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
            default: if (shape == NetworkEnums.PacketShape.SQUARE) { this.size = 2; this.baseCoinValue = 2; } else if (shape == NetworkEnums.PacketShape.TRIANGLE) { this.size = 3; this.baseCoinValue = 3; } else { this.size = 1; this.baseCoinValue = 1; } break;
        }
        this.originalSize = this.size; this.originalBaseCoinValue = this.baseCoinValue; this.initialOffsetSidePreference = this.id % 2; this.momentOfInertia = 0.5f * this.size * this.getDrawSize();
        this.positionHistory = new LinkedList<>();
        updateHitbox();
    }

    public void update(GameEngine gameEngine, boolean isAiryamanActive, boolean isSpeedLimiterActive, boolean isPredictionRun) {
        // [MODIFIED] Ghost packets do not move or update. They just exist.
        if (markedForRemoval || currentSystem != null || isGhost) return;

        if (currentWire == null) { gameEngine.packetLostInternal(this, isPredictionRun); return; }
        timeOnCurrentWireMs += 16;
        if (timeOnCurrentWireMs > WIRE_TIMEOUT_MS) { gameEngine.packetLostInternal(this, isPredictionRun); return; }
        if (Math.abs(this.angularVelocity) > 0.001f) { this.angle += this.angularVelocity; this.angularVelocity *= ANGULAR_DAMPING; } else { this.angularVelocity = 0; }
        if (this.packetType == NetworkEnums.PacketType.SECRET) { if (isUpgradedSecret) { handleUpgradedSecretMovement(gameEngine); } else { com.networkopsim.game.model.core.System destSystem = currentWire.getEndPort().getParentSystem(); targetSpeedMagnitude = (destSystem != null && destSystem.getQueueSize() > 0) ? BASE_SPEED_MAGNITUDE * SECRET_PACKET_SLOW_SPEED_FACTOR : BASE_SPEED_MAGNITUDE; currentSpeedMagnitude = targetSpeedMagnitude; isAccelerating = false; } }
        else if (isAccelerating && !isSpeedLimiterActive) { currentSpeedMagnitude = Math.min(targetSpeedMagnitude, currentSpeedMagnitude + TRIANGLE_ACCELERATION_RATE); if (currentSpeedMagnitude >= targetSpeedMagnitude) isAccelerating = false; }
        else if (isDecelerating) { currentSpeedMagnitude = Math.max(targetSpeedMagnitude, currentSpeedMagnitude - MESSENGER_DECELERATION_RATE); if (currentSpeedMagnitude <= targetSpeedMagnitude) isDecelerating = false; }
        else if (this.packetType == NetworkEnums.PacketType.WOBBLE) { if (this.noise < this.size / 2.0) this.addNoise(WOBBLE_SELF_NOISE_RATE); }
        double wireLength = currentWire.getLength();
        if (wireLength > 1e-6) { progressOnWire += (isReversing ? -1 : 1) * currentSpeedMagnitude / wireLength; } else { progressOnWire = isReversing ? 0.0 : 1.0; }
        progressOnWire = Math.max(0.0, Math.min(1.0, progressOnWire));
        updateIdealPositionAndVelocity();
        updateHitbox();
        Point2D.Double currentVisualPos = getVisualPosition();
        if (currentVisualPos != null) {
            positionHistory.add(currentVisualPos);
            if (positionHistory.size() > POSITION_HISTORY_SIZE) { ((LinkedList<Point2D.Double>) positionHistory).removeFirst(); }
        }
        if (!markedForRemoval && noise >= this.size) { gameEngine.packetLostInternal(this, isPredictionRun); return; }
        if (!isReversing && progressOnWire >= 1.0 - 1e-9) { handleArrival(currentWire.getEndPort(), gameEngine, isPredictionRun); }
        else if (isReversing && progressOnWire <= 1e-9) { isReversing = false; handleArrival(currentWire.getStartPort(), gameEngine, isPredictionRun); }
    }

    // [NEW METHOD] Transforms a packet into a ghost.
    public void transformToGhost(com.networkopsim.game.model.core.System distributor) {
        this.isGhost = true;
        this.ghostForSystemId = distributor.getId();
        this.currentSystem = null; // Ensure it's not considered "in" a system
        this.currentSystemId = -1;
        this.progressOnWire = 1.0; // Stay at the end of the wire
        updateIdealPositionAndVelocity(); // Lock its position
    }

    // ... (rest of the file is largely unchanged, adding getters and setSize) ...
    public void setSize(int newSize) { if (this.packetType == NetworkEnums.PacketType.BULK) { this.size = newSize; } }
    public boolean isGhost() { return isGhost; }
    public int getGhostForSystemId() { return ghostForSystemId; }
    private void handleUpgradedSecretMovement(GameEngine gameEngine) { List<Packet> otherPackets = gameEngine.getAllActivePackets(); Packet closestPacket = null; double minDistanceSq = Double.MAX_VALUE; Point2D.Double myPos = this.getVisualPosition(); for (Packet other : otherPackets) { if (other == this || other.isMarkedForRemoval()) continue; double distSq = myPos.distanceSq(other.getVisualPosition()); if (distSq < minDistanceSq) { minDistanceSq = distSq; closestPacket = other; } } if (closestPacket != null) { double minDistance = Math.sqrt(minDistanceSq); if (minDistance < SECRET_REPULSION_DISTANCE) targetSpeedMagnitude = BASE_SPEED_MAGNITUDE * 0.5; else if (minDistance > SECRET_ATTRACTION_DISTANCE) targetSpeedMagnitude = MAX_SPEED_MAGNITUDE; else targetSpeedMagnitude = BASE_SPEED_MAGNITUDE; } else { targetSpeedMagnitude = BASE_SPEED_MAGNITUDE; } if (currentSpeedMagnitude < targetSpeedMagnitude) currentSpeedMagnitude = Math.min(targetSpeedMagnitude, currentSpeedMagnitude + SECRET_SPEED_ADJUST_RATE); else if (currentSpeedMagnitude > targetSpeedMagnitude) currentSpeedMagnitude = Math.max(targetSpeedMagnitude, currentSpeedMagnitude - SECRET_SPEED_ADJUST_RATE); }
    private void handleArrival(Port destinationPort, GameEngine gameEngine, boolean isPredictionRun) { if (destinationPort == null || destinationPort.getParentSystem() == null) { gameEngine.packetLostInternal(this, isPredictionRun); return; } this.idealPosition = destinationPort.getPrecisePosition(); com.networkopsim.game.model.core.System targetSystem = destinationPort.getParentSystem(); if (this.packetType == NetworkEnums.PacketType.BULK && !isPredictionRun) { gameEngine.logBulkPacketWireUsage(this.currentWire); } boolean enteredCompatibly = packetType == NetworkEnums.PacketType.MESSENGER || packetType == NetworkEnums.PacketType.PROTECTED || packetType == NetworkEnums.PacketType.SECRET || (Port.getShapeEnum(this.shape) == destinationPort.getShape()); targetSystem.receivePacket(this, gameEngine, isPredictionRun, enteredCompatibly); }
    public void setWire(Wire wire, boolean compatiblePortExit) { this.currentWire = Objects.requireNonNull(wire, "Cannot set a null wire for packet " + id); this.currentWireId = wire.getId(); this.currentSystem = null; this.currentSystemId = -1; this.progressOnWire = 0.0; this.isReversing = false; this.isDecelerating = false; this.isAccelerating = false; this.timeOnCurrentWireMs = 0; if (finalStatusForPrediction != null && finalStatusForPrediction != PredictedPacketStatus.ON_WIRE) { this.finalStatusForPrediction = null; } double initialSpeed; if (this.packetType == NetworkEnums.PacketType.PROTECTED) { int choice = com.networkopsim.game.model.core.System.getGlobalRandom().nextInt(3); if (choice == 0) this.protectedMovementMode = ProtectedMovementMode.LIKE_SQUARE; else if (choice == 1) this.protectedMovementMode = ProtectedMovementMode.LIKE_TRIANGLE; else this.protectedMovementMode = ProtectedMovementMode.LIKE_MESSENGER; switch (this.protectedMovementMode) { case LIKE_SQUARE: initialSpeed = compatiblePortExit ? BASE_SPEED_MAGNITUDE * SQUARE_COMPATIBLE_SPEED_FACTOR : BASE_SPEED_MAGNITUDE; targetSpeedMagnitude = initialSpeed; break; case LIKE_TRIANGLE: initialSpeed = BASE_SPEED_MAGNITUDE; isAccelerating = !compatiblePortExit; targetSpeedMagnitude = isAccelerating ? MAX_SPEED_MAGNITUDE : initialSpeed; break; default: initialSpeed = BASE_SPEED_MAGNITUDE; isAccelerating = compatiblePortExit; targetSpeedMagnitude = MAX_SPEED_MAGNITUDE; break; } } else { switch(this.packetType) { case BULK: double threshold = wire.getRelayPointsCount() > 0 ? LONG_WIRE_THRESHOLD * 0.75 : LONG_WIRE_THRESHOLD; boolean isLongWire = wire.getLength() >= threshold; isAccelerating = isLongWire; initialSpeed = BASE_SPEED_MAGNITUDE; targetSpeedMagnitude = isLongWire ? MAX_SPEED_MAGNITUDE : BASE_SPEED_MAGNITUDE; break; case WOBBLE: case SECRET: initialSpeed = BASE_SPEED_MAGNITUDE; targetSpeedMagnitude = initialSpeed; break; case MESSENGER: initialSpeed = BASE_SPEED_MAGNITUDE; if (this.enteredViaIncompatiblePort) { initialSpeed *= MESSENGER_INCOMPATIBLE_SPEED_BOOST; } isAccelerating = true; targetSpeedMagnitude = MAX_SPEED_MAGNITUDE; break; default: if (this.shape == NetworkEnums.PacketShape.SQUARE) { initialSpeed = compatiblePortExit ? BASE_SPEED_MAGNITUDE * SQUARE_COMPATIBLE_SPEED_FACTOR : BASE_SPEED_MAGNITUDE; targetSpeedMagnitude = initialSpeed; } else if (this.shape == NetworkEnums.PacketShape.TRIANGLE) { initialSpeed = BASE_SPEED_MAGNITUDE; isAccelerating = !compatiblePortExit; targetSpeedMagnitude = isAccelerating ? MAX_SPEED_MAGNITUDE : initialSpeed; } else { initialSpeed = BASE_SPEED_MAGNITUDE; targetSpeedMagnitude = initialSpeed; } break; } } this.currentSpeedMagnitude = initialSpeed; this.enteredViaIncompatiblePort = false; updateIdealPositionAndVelocity(); updateHitbox(); }
    public void teleportToWire(Wire wire) { this.currentWire = Objects.requireNonNull(wire, "Cannot teleport to a null wire"); this.currentWireId = wire.getId(); this.currentSystem = null; this.currentSystemId = -1; this.progressOnWire = 0.0; this.isReversing = false; this.isDecelerating = false; this.isAccelerating = false; this.enteredViaIncompatiblePort = false; this.timeOnCurrentWireMs = 0; if (finalStatusForPrediction != null && finalStatusForPrediction != PredictedPacketStatus.ON_WIRE) { this.finalStatusForPrediction = null; } this.currentSpeedMagnitude = BASE_SPEED_MAGNITUDE; this.targetSpeedMagnitude = BASE_SPEED_MAGNITUDE; updateIdealPositionAndVelocity(); updateHitbox(); }
    public void addNoise(double amount) { if (amount > 0 && !markedForRemoval) { this.noise = Math.min(this.size, this.noise + amount); updateVisualOffsetMagnitude(); } }
    public void resetNoise() { if (!markedForRemoval) { this.noise = 0.0; updateVisualOffsetMagnitude(); } }
    private void updateVisualOffsetMagnitude() { if (noise <= 0) { visualOffsetMagnitude = 0; return; } double halfDrawSize = getDrawSize() / 2.0; double maxPossibleOffsetToVertex; if (shape == NetworkEnums.PacketShape.SQUARE) maxPossibleOffsetToVertex = halfDrawSize * Math.sqrt(2); else if (shape == NetworkEnums.PacketShape.TRIANGLE) maxPossibleOffsetToVertex = halfDrawSize * 1.15; else maxPossibleOffsetToVertex = halfDrawSize; double noiseRatio = Math.min(noise / (double)this.size, 1.0); this.visualOffsetMagnitude = maxPossibleOffsetToVertex * noiseRatio; }
    public void nullifyAcceleration() { if (isAccelerating) { isAccelerating = false; targetSpeedMagnitude = currentSpeedMagnitude; } }
    public void realignToWire() { if (visualOffsetMagnitude > 0) { visualOffsetMagnitude *= (1.0 - ELIPHAS_REALIGNMENT_FACTOR); if (visualOffsetMagnitude < 0.01) visualOffsetMagnitude = 0; } }
    public void applyTorque(Point2D.Double forceVector, double forceMagnitude) { if (momentOfInertia < 1e-6) return; Point2D.Double packetDirection = this.getVelocity(); if (Math.hypot(packetDirection.x, packetDirection.y) < 0.1 && currentWire != null) { Wire.PathInfo pathInfo = currentWire.getPathInfoAtProgress(progressOnWire); if (pathInfo != null) packetDirection = pathInfo.direction; } double forceMag = Math.hypot(forceVector.x, forceVector.y); if (forceMag < 1e-6) return; Point2D.Double forceDir = new Point2D.Double(forceVector.x / forceMag, forceVector.y / forceMag); double packetDirMag = Math.hypot(packetDirection.x, packetDirection.y); if (packetDirMag < 1e-6) return; Point2D.Double packetDir = new Point2D.Double(packetDirection.x / packetDirMag, packetDirection.y / packetDirMag); double crossProduct = (packetDir.x * forceDir.y) - (packetDir.y * forceDir.x); float angularAcceleration = (float) ((crossProduct * forceMagnitude) / this.momentOfInertia); this.angularVelocity += angularAcceleration; }
    public boolean collidesWith(Packet other) { if (this == other || other == null || this.currentSystem != null || other.currentSystem != null || this.markedForRemoval || other.markedForRemoval) return false; if (this.hitbox == null) this.updateHitbox(); if (other.hitbox == null) other.updateHitbox(); if (this.hitbox == null || other.hitbox == null) return false; Area area1 = new Area(this.hitbox); area1.intersect(new Area(other.hitbox)); return !area1.isEmpty(); }
    public void updateHitbox() { boolean usePCA = (this.packetType == NetworkEnums.PacketType.WOBBLE || this.noise > PCA_ACTIVATION_NOISE_THRESHOLD) && this.positionHistory != null && this.positionHistory.size() >= 5; if (usePCA) { updateHitboxWithPCA(); } else { updateHitboxDefault(); } }
    private void updateHitboxDefault() { Path2D.Double baseShape; switch (this.shape) { case SQUARE: baseShape = BASE_SQUARE_HITBOX; break; case TRIANGLE: baseShape = BASE_TRIANGLE_HITBOX; break; default: baseShape = BASE_CIRCLE_HITBOX; } AffineTransform tx = new AffineTransform(); Point2D.Double visualPos = getVisualPosition(); if (visualPos == null) return; tx.translate(visualPos.x, visualPos.y); double totalAngle = this.angle; if (this.shape == NetworkEnums.PacketShape.TRIANGLE) { Point2D.Double direction = getVelocity(); if (direction != null && (Math.abs(direction.x) > 0.01 || Math.abs(direction.y) > 0.01)) { double angleFromVelocity = Math.atan2(direction.y, direction.x); if (isReversing) angleFromVelocity += Math.PI; totalAngle += angleFromVelocity; } } tx.rotate(totalAngle); tx.scale(getDrawSize(), getDrawSize()); this.hitbox = (Path2D.Double) baseShape.createTransformedShape(tx); }
    private void updateHitboxWithPCA() { if (positionHistory == null || positionHistory.isEmpty()) { updateHitboxDefault(); return; } double meanX = 0, meanY = 0; for (Point2D.Double p : positionHistory) { meanX += p.x; meanY += p.y; } meanX /= positionHistory.size(); meanY /= positionHistory.size(); double covXX = 0, covXY = 0, covYY = 0; for (Point2D.Double p : positionHistory) { double dx = p.x - meanX; double dy = p.y - meanY; covXX += dx * dx; covXY += dx * dy; covYY += dy * dy; } covXX /= positionHistory.size(); covXY /= positionHistory.size(); covYY /= positionHistory.size(); double trace = covXX + covYY; double det = covXX * covYY - covXY * covXY; double discriminant = Math.sqrt(trace * trace - 4 * det); double eigVal1 = (trace + discriminant) / 2; double eigVal2 = (trace - discriminant) / 2; Point2D.Double eigVec1 = new Point2D.Double(covXY, eigVal1 - covXX); Point2D.Double eigVec2 = new Point2D.Double(covXY, eigVal2 - covXX); double mag1 = eigVec1.distance(0, 0); if (mag1 > 1e-6) { eigVec1.x /= mag1; eigVec1.y /= mag1; } double mag2 = eigVec2.distance(0, 0); if (mag2 > 1e-6) { eigVec2.x /= mag2; eigVec2.y /= mag2; } double halfWidth = PCA_SCALE_FACTOR * Math.sqrt(eigVal1) + getDrawSize() / 2.0; double halfHeight = PCA_SCALE_FACTOR * Math.sqrt(eigVal2) + getDrawSize() / 2.0; Point2D.Double center = new Point2D.Double(meanX, meanY); Point2D.Double axis1 = new Point2D.Double(eigVec1.x * halfWidth, eigVec1.y * halfWidth); Point2D.Double axis2 = new Point2D.Double(eigVec2.x * halfHeight, eigVec2.y * halfHeight); Point2D.Double c1 = new Point2D.Double(center.x + axis1.x + axis2.x, center.y + axis1.y + axis2.y); Point2D.Double c2 = new Point2D.Double(center.x - axis1.x + axis2.x, center.y - axis1.y + axis2.y); Point2D.Double c3 = new Point2D.Double(center.x - axis1.x - axis2.x, center.y - axis1.y - axis2.y); Point2D.Double c4 = new Point2D.Double(center.x + axis1.x - axis2.x, center.y + axis1.y - axis2.y); Path2D.Double pcaHitbox = new Path2D.Double(); pcaHitbox.moveTo(c1.x, c1.y); pcaHitbox.lineTo(c2.x, c2.y); pcaHitbox.lineTo(c3.x, c3.y); pcaHitbox.lineTo(c4.x, c4.y); pcaHitbox.closePath(); this.hitbox = pcaHitbox; }
    private void updateIdealPositionAndVelocity() { if (currentWire == null) return; Wire.PathInfo pathInfo = currentWire.getPathInfoAtProgress(progressOnWire); if (pathInfo != null) { this.idealPosition = pathInfo.position; double dx = pathInfo.direction.x * currentSpeedMagnitude; double dy = pathInfo.direction.y * currentSpeedMagnitude; this.velocity = new Point2D.Double(isReversing ? -dx : dx, isReversing ? -dy : dy); } else if (currentWire.getStartPort() != null) { this.idealPosition = currentWire.getStartPort().getPrecisePosition(); this.velocity = new Point2D.Double(0, 0); } }
    public void setVisualOffsetDirectionFromForce(Point2D.Double forceDirection) { if (currentWire == null) { if (this.visualOffsetDirection == null) this.visualOffsetDirection = new Point2D.Double(0, 1); return; } Wire.PathInfo pathInfo = currentWire.getPathInfoAtProgress(this.progressOnWire); if (pathInfo == null) { if (this.visualOffsetDirection == null) this.visualOffsetDirection = new Point2D.Double(0, 1); return; } Point2D.Double wireDir = pathInfo.direction; if (forceDirection == null || (forceDirection.x == 0 && forceDirection.y == 0)) { if (this.visualOffsetDirection == null) { this.visualOffsetDirection = new Point2D.Double(-wireDir.y, wireDir.x); if (this.initialOffsetSidePreference == 1) { this.visualOffsetDirection.x *= -1; this.visualOffsetDirection.y *= -1; } double mag = Math.hypot(this.visualOffsetDirection.x, this.visualOffsetDirection.y); if (mag > 1e-6) { this.visualOffsetDirection.x /= mag; this.visualOffsetDirection.y /= mag; } else { this.visualOffsetDirection = new Point2D.Double(0, 1); } } return; } Point2D.Double perp1 = new Point2D.Double(-wireDir.y, wireDir.x); double dotProductWithPerp1 = (forceDirection.x * perp1.x) + (forceDirection.y * perp1.y); if (Math.abs(dotProductWithPerp1) < 1e-6) { if (this.visualOffsetDirection == null) { this.visualOffsetDirection = new Point2D.Double(-wireDir.y, wireDir.x); if (this.initialOffsetSidePreference == 1) { this.visualOffsetDirection.x *= -1; this.visualOffsetDirection.y *= -1; } } } else if (dotProductWithPerp1 > 0) this.visualOffsetDirection = perp1; else this.visualOffsetDirection = new Point2D.Double(-perp1.x, -perp1.y); double mag = Math.hypot(this.visualOffsetDirection.x, this.visualOffsetDirection.y); if (mag > 1e-6) { this.visualOffsetDirection.x /= mag; this.visualOffsetDirection.y /= mag; } else { this.visualOffsetDirection = new Point2D.Double(-wireDir.y, wireDir.x); if (this.initialOffsetSidePreference == 1) { this.visualOffsetDirection.x *= -1; this.visualOffsetDirection.y *= -1; } double m = Math.hypot(this.visualOffsetDirection.x, this.visualOffsetDirection.y); if (m > 1e-6) { this.visualOffsetDirection.x /= m; this.visualOffsetDirection.y /= m; } else { this.visualOffsetDirection = new Point2D.Double(0,1); } } }
    public void rebuildTransientReferences(Map<Integer, com.networkopsim.game.model.core.System> systemMap, Map<Integer, Wire> wireMap) { if (this.currentSystemId != -1) this.currentSystem = systemMap.get(this.currentSystemId); if (this.currentWireId != -1) this.currentWire = wireMap.get(this.currentWireId); if (this.currentWire != null) updateIdealPositionAndVelocity(); if (this.positionHistory == null) { this.positionHistory = new LinkedList<>(); } updateHitbox(); }
    public void transformToProtected(int vpnSystemId) { this.originalPacketType = this.packetType; this.originalSize = this.size; this.originalBaseCoinValue = this.baseCoinValue; this.packetType = NetworkEnums.PacketType.PROTECTED; this.size = this.originalSize * 2; this.baseCoinValue = 5; this.protectedMovementMode = null; this.protectedBySystemId = vpnSystemId; }
    public void upgradeSecretPacket() { if (this.packetType == NetworkEnums.PacketType.SECRET && !this.isUpgradedSecret) { this.isUpgradedSecret = true; this.size = 6; this.baseCoinValue = 4; } }
    public void revertToOriginalType() { this.packetType = this.originalPacketType; this.size = this.originalSize; this.baseCoinValue = this.originalBaseCoinValue; this.protectedMovementMode = null; this.protectedBySystemId = -1; }
    public void reverseDirection(GameEngine gameEngine) { if (this.isReversing) return; this.isReversing = true; this.isAccelerating = false; this.isDecelerating = false; if (!gameEngine.getGame().isMuted()) gameEngine.getGame().playSoundEffect("packet_reverse"); }
    public void setEnteredViaIncompatiblePort(boolean status) { this.enteredViaIncompatiblePort = status; }
    public NetworkEnums.PacketType getPacketType() { return packetType; }
    public void setPacketType(NetworkEnums.PacketType type) { this.packetType = type; }
    public int getId() { return id; }
    public NetworkEnums.PacketShape getShape() { return shape; }
    public int getSize() { return size; }
    public int getBaseCoinValue() { return baseCoinValue; }
    public Point2D.Double getIdealPosition() { return (idealPosition != null) ? (Point2D.Double) idealPosition.clone() : null; }
    public Point2D.Double getVisualPosition() { if (visualOffsetMagnitude > 0 && visualOffsetDirection != null && idealPosition != null) { return new Point2D.Double(idealPosition.x + visualOffsetDirection.x * visualOffsetMagnitude, idealPosition.y + visualOffsetDirection.y * visualOffsetMagnitude); } return getIdealPosition(); }
    public float getAngle() { return angle; }
    public boolean isReversing() { return isReversing; }
    public boolean isUpgradedSecret() { return isUpgradedSecret; }
    public int getBulkParentId() { return bulkParentId; }
    public int getTotalBitsInGroup() { return totalBitsInGroup; }
    public Wire getCurrentWire() { return currentWire; }
    public double getProgressOnWire() { return progressOnWire; }
    public double getNoise() { return noise; }
    public boolean isMarkedForRemoval() { return markedForRemoval; }
    public void markForRemoval() { this.markedForRemoval = true; }
    public com.networkopsim.game.model.core.System getCurrentSystem() { return currentSystem; }
    public void setCurrentSystem(com.networkopsim.game.model.core.System system) { this.currentSystem = system; this.currentSystemId = (system != null) ? system.getId() : -1; this.currentWire = null; this.currentWireId = -1; this.isReversing = false; this.isAccelerating = false; this.isDecelerating = false; this.progressOnWire = 0.0; this.currentSpeedMagnitude = 0.0; if (system != null) { this.idealPosition = new Point2D.Double(system.getPosition().x, system.getPosition().y); } }
    public int getDrawSize() { return BASE_DRAW_SIZE + (size * 2); }
    public double getCurrentSpeedMagnitude() { return currentSpeedMagnitude; }
    public void setCurrentSpeedMagnitude(double speed) { this.currentSpeedMagnitude = speed; }
    public void configureAsBulkPart(int parentId, int totalParts) { if(this.packetType == NetworkEnums.PacketType.MESSENGER){ this.bulkParentId = parentId; this.totalBitsInGroup = totalParts; this.baseCoinValue = 0; } }
    public PredictedPacketStatus getFinalStatusForPrediction() { return this.finalStatusForPrediction; }
    public void setFinalStatusForPrediction(PredictedPacketStatus status) { this.finalStatusForPrediction = status; }
    public Point2D.Double getVelocity() { if (this.velocity == null) return new Point2D.Double(0,0); return (Point2D.Double) this.velocity.clone(); }
    public int getProtectedBySystemId() { return protectedBySystemId; }
    @Override public boolean equals(Object o) { if (this == o) return true; if (o == null || getClass() != o.getClass()) return false; Packet packet = (Packet) o; return id == packet.id; }
    @Override public int hashCode() { return Objects.hash(id); }
    @Override public String toString() { String status; if (markedForRemoval) status = "REMOVED"; else if (currentSystem != null) status = "QUEUED(Sys:" + currentSystem.getId() + ")"; else if (currentWire != null) status = String.format("ON_WIRE(W:%d P:%.1f%%)", currentWire.getId(), progressOnWire * 100); else status = "IDLE/INIT"; String idealPosStr = (idealPosition != null) ? String.format("%.1f,%.1f", idealPosition.x, idealPosition.y) : "null"; Point2D.Double currentVel = getVelocity(); String velStr = (currentVel != null) ? String.format("%.1f,%.1f (Mag:%.2f)", currentVel.x, currentVel.y, currentSpeedMagnitude) : "null"; return String.format("Packet{ID:%d, SHP:%s, TYP: %s, SZ:%d, N:%.1f/%d, V:%d, Ideal:(%s) Vel:(%s) St:%s}", id, shape, packetType, size, noise, this.size, baseCoinValue, idealPosStr, velStr, status); }
}