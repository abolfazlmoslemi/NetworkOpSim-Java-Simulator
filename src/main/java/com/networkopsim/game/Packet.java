// ===== FILE: Packet.java (کد کامل و اصلاح شده نهایی) =====

package com.networkopsim.game;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.List;
import java.util.Objects;

public class Packet {
    // ========== مقادیر نهایی و تعدیل شده ==========
    public static final double BASE_SPEED_MAGNITUDE = 2.0;
    public static final int BASE_DRAW_SIZE = 8;
    public static final double SQUARE_COMPATIBLE_SPEED_FACTOR = 0.5;
    private static final double TRIANGLE_ACCELERATION_RATE = 0.018; // <<< نرخ شتاب به شدت کاهش یافت تا در مسیرهای طولانی به سرعت بالا برسد
    private static final double MESSENGER_DECELERATION_RATE = 0.04;
    public static final double MAX_SPEED_MAGNITUDE = 4.1;
    private static final double MESSENGER_INCOMPATIBLE_SPEED_BOOST = 1.5;
    // ============================================

    private static final double SECRET_PACKET_SLOW_SPEED_FACTOR = 0.25;
    private static final double WOBBLE_SELF_NOISE_RATE = 0.05;
    private static final double SECRET_REPULSION_DISTANCE = 100.0;
    private static final double SECRET_ATTRACTION_DISTANCE = 250.0;
    private static final double SECRET_SPEED_ADJUST_RATE = 0.05;

    private static final double COLLISION_RADIUS_FACTOR = 1.0;
    private static final Font NOISE_FONT = new Font("Arial", Font.PLAIN, 9);
    private static final Color NOISE_TEXT_COLOR = Color.WHITE;
    private static final Color IDEAL_POSITION_MARKER_COLOR = new Color(255, 255, 255, 60);
    private static final int IDEAL_POSITION_MARKER_SIZE = 4;
    private static int nextPacketId = 0;

    private final int id;
    private final NetworkEnums.PacketShape shape;
    private NetworkEnums.PacketType packetType;
    private Point2D.Double idealPosition;
    private Point2D.Double velocity;

    private int size;
    private int baseCoinValue;
    private NetworkEnums.PacketType originalPacketType;
    private int originalSize;
    private int originalBaseCoinValue;
    private boolean isUpgradedSecret = false;
    private double noise = 0.0;
    private boolean markedForRemoval = false;

    private Wire currentWire = null;
    private System currentSystem = null;
    private double progressOnWire = 0.0;
    private double currentSpeedMagnitude = 0.0;
    private double targetSpeedMagnitude = 0.0;
    private boolean isAccelerating = false;
    private boolean isDecelerating = false;
    private boolean isReversing = false;
    private boolean enteredViaIncompatiblePort = false;

    private Point2D.Double visualOffsetDirection = null;
    private int initialOffsetSidePreference = 0;
    private PredictedPacketStatus finalStatusForPrediction = null;

    private int bulkParentId = -1;
    private int totalBitsInGroup = 0;
    private enum ProtectedMovementMode { LIKE_SQUARE, LIKE_TRIANGLE, LIKE_MESSENGER }
    private ProtectedMovementMode protectedMovementMode = null;

    public static void resetGlobalId() {
        nextPacketId = 0;
    }

    public Packet(NetworkEnums.PacketShape shape, double startX, double startY, NetworkEnums.PacketType type) {
        this.id = nextPacketId++;
        this.shape = Objects.requireNonNull(shape, "Packet shape cannot be null");
        this.packetType = Objects.requireNonNull(type, "Packet type cannot be null");
        this.originalPacketType = type;
        this.idealPosition = new Point2D.Double(startX, startY);
        this.velocity = new Point2D.Double(0, 0);

        if (this.packetType == NetworkEnums.PacketType.MESSENGER) {
            this.size = 1;
            this.baseCoinValue = 1;
        } else {
            switch (type) {
                case SECRET:
                    this.size = 4; this.baseCoinValue = 3; break;
                case BULK:
                    this.size = 8; this.baseCoinValue = 8; break;
                case WOBBLE:
                    this.size = 10; this.baseCoinValue = 10; break;
                case BIT:
                    this.size = 1; this.baseCoinValue = 0; break;
                case NORMAL:
                case TROJAN:
                case PROTECTED:
                default:
                    if (shape == NetworkEnums.PacketShape.SQUARE) {
                        this.size = 2; this.baseCoinValue = 2;
                    } else if (shape == NetworkEnums.PacketShape.TRIANGLE) {
                        this.size = 3; this.baseCoinValue = 3;
                    } else {
                        this.size = 1; this.baseCoinValue = 1;
                    }
                    break;
            }
        }

        this.originalSize = this.size;
        this.originalBaseCoinValue = this.baseCoinValue;
        this.initialOffsetSidePreference = this.id % 2;
    }

    public Packet(NetworkEnums.PacketShape shape, double startX, double startY) {
        this(shape, startX, startY, NetworkEnums.PacketType.NORMAL);
    }


    public void draw(Graphics2D g2d) {
        if (markedForRemoval || currentSystem != null || idealPosition == null) return;
        Point2D.Double calculatedOffset = calculateCurrentVisualOffset();
        Point2D.Double visualPosition = new Point2D.Double(idealPosition.x + calculatedOffset.x, idealPosition.y + calculatedOffset.y);

        if (currentWire != null && noise > 0.05) {
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

        Path2D path = null;
        AffineTransform oldTransform = g2d.getTransform();
        boolean transformed = false;

        try {
            if (packetType == NetworkEnums.PacketType.MESSENGER && shape == NetworkEnums.PacketShape.CIRCLE) {
                g2d.setColor(new Color(0x9013FE));
                g2d.setStroke(new BasicStroke(2.5f));
                int infinityWidth = drawSize;
                int infinityHeight = drawSize / 2;
                int centerX = (int)Math.round(visualPosition.x);
                int centerY = (int)Math.round(visualPosition.y);
                g2d.drawArc(centerX - infinityWidth / 2, centerY - infinityHeight / 2, infinityWidth / 2, infinityHeight, 90, 180);
                g2d.drawArc(centerX, centerY - infinityHeight / 2, infinityWidth / 2, infinityHeight, 270, 180);
            } else {
                switch (packetType) {
                    case PROTECTED:
                        g2d.setColor(new Color(218, 165, 32, 220));
                        g2d.fillRect(drawX + 2, drawY + drawSize/3, drawSize - 4, drawSize*2/3 - 2);
                        g2d.setStroke(new BasicStroke(2));
                        g2d.drawArc(drawX + 2, drawY, drawSize - 4, drawSize*2/3, 0, 180);
                        g2d.setColor(Color.BLACK);
                        g2d.setStroke(new BasicStroke(1));
                        g2d.drawRect(drawX + 2, drawY + drawSize/3, drawSize - 4, drawSize*2/3 - 2);
                        break;
                    case BULK:
                        path = createHexagon(visualPosition.x, visualPosition.y, halfSize);
                        g2d.setColor(new Color(0x4a90e2));
                        g2d.fill(path);
                        g2d.setColor(Color.CYAN.darker());
                        g2d.setStroke(new BasicStroke(1.5f));
                        g2d.draw(path);
                        break;
                    case WOBBLE:
                        drawWireframeCube(g2d, visualPosition.x, visualPosition.y, drawSize);
                        break;
                    default:
                        Color packetColor = (packetType == NetworkEnums.PacketType.SECRET) ?
                                (isUpgradedSecret ? new Color(200, 200, 210) : new Color(50, 70, 120)) :
                                Port.getColorFromShape(shape);
                        if (packetType == NetworkEnums.PacketType.BIT) packetColor = new Color(Color.HSBtoRGB((this.bulkParentId * 0.27f) % 1.0f, 0.7f, 0.95f));
                        if (packetType == NetworkEnums.PacketType.MESSENGER) packetColor = Port.getColorFromShape(shape);
                        g2d.setColor(packetColor);
                        Point2D.Double directionForRotation = getVelocity();
                        if (shape == NetworkEnums.PacketShape.TRIANGLE && directionForRotation != null && Math.hypot(directionForRotation.x, directionForRotation.y) > 0.01) {
                            g2d.translate(visualPosition.x, visualPosition.y);
                            double angle = Math.atan2(directionForRotation.y, directionForRotation.x) + (isReversing ? Math.PI : 0);
                            g2d.rotate(angle);
                            path = new Path2D.Double();
                            path.moveTo(halfSize, 0); path.lineTo(-halfSize, -halfSize); path.lineTo(-halfSize, halfSize);
                            path.closePath();
                            g2d.fill(path);
                            transformed = true;
                        } else {
                            switch (shape) {
                                case SQUARE: g2d.fillRect(drawX, drawY, drawSize, drawSize); break;
                                case CIRCLE: g2d.fillOval(drawX, drawY, drawSize, drawSize); break;
                                case TRIANGLE:
                                    path = new Path2D.Double();
                                    path.moveTo(visualPosition.x, drawY); path.lineTo(drawX + drawSize, drawY + drawSize); path.lineTo(drawX, drawY + drawSize);
                                    path.closePath();
                                    g2d.fill(path);
                                    break;
                            }
                        }
                        break;
                }
            }
            Stroke defaultStroke = g2d.getStroke();
            if (packetType != NetworkEnums.PacketType.PROTECTED && packetType != NetworkEnums.PacketType.BULK && packetType != NetworkEnums.PacketType.WOBBLE && !(packetType == NetworkEnums.PacketType.MESSENGER && shape == NetworkEnums.PacketShape.CIRCLE)) {
                switch (packetType) {
                    case TROJAN:
                        g2d.setColor(new Color(255, 50, 50, 200));
                        g2d.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, new float[]{3f, 3f}, 0.0f));
                        if (transformed && path != null) g2d.draw(path); else if (shape == NetworkEnums.PacketShape.SQUARE) g2d.drawRect(drawX, drawY, drawSize, drawSize); else g2d.drawOval(drawX, drawY, drawSize, drawSize);
                        break;
                    case SECRET:
                        g2d.setColor(isUpgradedSecret ? new Color(200, 200, 210, 220) : new Color(170, 190, 255, 220));
                        g2d.setStroke(new BasicStroke(1.0f));
                        g2d.setFont(new Font("Arial", Font.BOLD, 9));
                        if (transformed) g2d.drawString("S", -3, 3); else g2d.drawString("S", drawX + 2, drawY + 9);
                        break;
                    case BIT:
                        g2d.setColor(new Color(255, 255, 255, 180));
                        g2d.setFont(new Font("Monospaced", Font.BOLD, 9));
                        String bitId = String.valueOf(this.bulkParentId);
                        if (transformed) g2d.drawString(bitId, -3, 3); else g2d.drawString(bitId, drawX + 2, drawY + 9);
                        break;
                }
            }
            g2d.setStroke(defaultStroke);
        } finally {
            if (transformed) g2d.setTransform(oldTransform);
            if (noise > 0.05) {
                g2d.setFont(NOISE_FONT);
                g2d.setColor(NOISE_TEXT_COLOR);
                String noiseText = String.format("%.1f", noise);
                FontMetrics fm = g2d.getFontMetrics();
                int textX = (int) Math.round(visualPosition.x + halfSize + 2);
                int textY = (int) Math.round(visualPosition.y - halfSize + fm.getAscent() / 2.0);
                g2d.drawString(noiseText, textX, textY);
            }
        }
    }

    private Path2D createHexagon(double centerX, double centerY, double radius) {
        Path2D hexagon = new Path2D.Double();
        for (int i = 0; i < 6; i++) {
            double angle = Math.toRadians(60 * i);
            double x = centerX + radius * Math.cos(angle);
            double y = centerY + radius * Math.sin(angle);
            if (i == 0) {
                hexagon.moveTo(x, y);
            } else {
                hexagon.lineTo(x, y);
            }
        }
        hexagon.closePath();
        return hexagon;
    }
    private void drawWireframeCube(Graphics2D g2d, double cx, double cy, double size) {
        double halfSize = size / 2.0;
        double offset = halfSize / 2.0;
        double x1 = cx - halfSize, y1 = cy - halfSize;
        double x2 = cx + halfSize, y2 = cy + halfSize;
        double x3 = x1 + offset, y3 = y1 - offset;
        double x4 = x2 + offset, y4 = y2 - offset;
        g2d.setColor(new Color(200, 200, 220));
        g2d.setStroke(new BasicStroke(1.5f));
        g2d.drawRect((int)x3, (int)y3, (int)(x4 - x3), (int)(y4 - y3));
        g2d.drawRect((int)x1, (int)y1, (int)(x2 - x1), (int)(y2 - y1));
        g2d.drawLine((int)x1, (int)y1, (int)x3, (int)y3);
        g2d.drawLine((int)x2, (int)y1, (int)x4, (int)y3);
        g2d.drawLine((int)x1, (int)y2, (int)x3, (int)y4);
        g2d.drawLine((int)x2, (int)y2, (int)x4, (int)y4);
    }

    public void update(GamePanel gamePanel, boolean isAiryamanActive, boolean isSpeedLimiterActive, boolean isPredictionRun) {
        if (markedForRemoval || currentSystem != null) return;
        if (currentWire == null) {
            gamePanel.packetLostInternal(this, isPredictionRun);
            return;
        }

        if (this.packetType == NetworkEnums.PacketType.SECRET) {
            if (isUpgradedSecret) {
                handleUpgradedSecretMovement(gamePanel);
            } else {
                System destSystem = currentWire.getEndPort().getParentSystem();
                targetSpeedMagnitude = (destSystem != null && destSystem.getQueueSize() > 0) ?
                        BASE_SPEED_MAGNITUDE * SECRET_PACKET_SLOW_SPEED_FACTOR : BASE_SPEED_MAGNITUDE;
                currentSpeedMagnitude = targetSpeedMagnitude;
                isAccelerating = false;
            }
        } else if (isAccelerating && !isSpeedLimiterActive) {
            currentSpeedMagnitude = Math.min(targetSpeedMagnitude, currentSpeedMagnitude + TRIANGLE_ACCELERATION_RATE);
            if (currentSpeedMagnitude >= targetSpeedMagnitude) isAccelerating = false;
        } else if (isDecelerating) {
            currentSpeedMagnitude = Math.max(targetSpeedMagnitude, currentSpeedMagnitude - MESSENGER_DECELERATION_RATE);
            if (currentSpeedMagnitude <= targetSpeedMagnitude) isDecelerating = false;
        } else if (this.packetType == NetworkEnums.PacketType.WOBBLE) {
            if (this.noise < this.size / 2.0) this.addNoise(WOBBLE_SELF_NOISE_RATE);
        }

        double wireLength = currentWire.getLength();
        if (wireLength > 1e-6) {
            progressOnWire += (isReversing ? -1 : 1) * currentSpeedMagnitude / wireLength;
        } else {
            progressOnWire = isReversing ? 0.0 : 1.0;
        }
        progressOnWire = Math.max(0.0, Math.min(1.0, progressOnWire));
        updateIdealPositionAndVelocity();

        if (!markedForRemoval && noise >= this.size) {
            gamePanel.packetLostInternal(this, isPredictionRun);
            return;
        }
        if (!isReversing && progressOnWire >= 1.0 - 1e-9) handleArrival(currentWire.getEndPort(), gamePanel, isPredictionRun);
        else if (isReversing && progressOnWire <= 1e-9) {
            isReversing = false;
            handleArrival(currentWire.getStartPort(), gamePanel, isPredictionRun);
        }
    }
    private void handleUpgradedSecretMovement(GamePanel gamePanel) {
        List<Packet> otherPackets = gamePanel.getAllActivePackets();
        Packet closestPacket = null;
        double minDistanceSq = Double.MAX_VALUE;
        Point2D.Double myPos = this.getPositionDouble();
        for (Packet other : otherPackets) {
            if (other == this || other.isMarkedForRemoval()) continue;
            double distSq = myPos.distanceSq(other.getPositionDouble());
            if (distSq < minDistanceSq) {
                minDistanceSq = distSq;
                closestPacket = other;
            }
        }
        if (closestPacket != null) {
            double minDistance = Math.sqrt(minDistanceSq);
            if (minDistance < SECRET_REPULSION_DISTANCE) targetSpeedMagnitude = BASE_SPEED_MAGNITUDE * 0.5;
            else if (minDistance > SECRET_ATTRACTION_DISTANCE) targetSpeedMagnitude = MAX_SPEED_MAGNITUDE;
            else targetSpeedMagnitude = BASE_SPEED_MAGNITUDE;
        } else {
            targetSpeedMagnitude = BASE_SPEED_MAGNITUDE;
        }
        if (currentSpeedMagnitude < targetSpeedMagnitude) currentSpeedMagnitude = Math.min(targetSpeedMagnitude, currentSpeedMagnitude + SECRET_SPEED_ADJUST_RATE);
        else if (currentSpeedMagnitude > targetSpeedMagnitude) currentSpeedMagnitude = Math.max(targetSpeedMagnitude, currentSpeedMagnitude - SECRET_SPEED_ADJUST_RATE);
    }
    private void handleArrival(Port destinationPort, GamePanel gamePanel, boolean isPredictionRun) {
        if (destinationPort == null || destinationPort.getParentSystem() == null) {
            gamePanel.packetLostInternal(this, isPredictionRun);
            return;
        }
        this.idealPosition = destinationPort.getPrecisePosition();
        System targetSystem = destinationPort.getParentSystem();
        if (this.packetType == NetworkEnums.PacketType.BULK && !isPredictionRun) {
            gamePanel.logBulkPacketWireUsage(this.currentWire);
        }
        boolean enteredCompatibly = packetType == NetworkEnums.PacketType.MESSENGER ||
                packetType == NetworkEnums.PacketType.PROTECTED ||
                packetType == NetworkEnums.PacketType.SECRET ||
                (Port.getShapeEnum(this.shape) == destinationPort.getShape());
        targetSystem.receivePacket(this, gamePanel, isPredictionRun, enteredCompatibly);
    }

    public void setWire(Wire wire, boolean compatiblePortExit) {
        this.currentWire = Objects.requireNonNull(wire, "Cannot set a null wire for packet " + id);
        this.progressOnWire = 0.0;
        this.currentSystem = null;
        this.isReversing = false;
        this.isDecelerating = false;
        this.isAccelerating = false;

        if (finalStatusForPrediction != null && finalStatusForPrediction != PredictedPacketStatus.ON_WIRE) {
            this.finalStatusForPrediction = null;
        }

        double initialSpeed;

        if (this.packetType == NetworkEnums.PacketType.PROTECTED) {
            int choice = System.getGlobalRandom().nextInt(3);
            if (choice == 0) this.protectedMovementMode = ProtectedMovementMode.LIKE_SQUARE;
            else if (choice == 1) this.protectedMovementMode = ProtectedMovementMode.LIKE_TRIANGLE;
            else this.protectedMovementMode = ProtectedMovementMode.LIKE_MESSENGER;

            switch (this.protectedMovementMode) {
                case LIKE_SQUARE:
                    initialSpeed = compatiblePortExit ? BASE_SPEED_MAGNITUDE * SQUARE_COMPATIBLE_SPEED_FACTOR : BASE_SPEED_MAGNITUDE;
                    targetSpeedMagnitude = initialSpeed;
                    break;
                case LIKE_TRIANGLE:
                    initialSpeed = BASE_SPEED_MAGNITUDE;
                    isAccelerating = !compatiblePortExit;
                    targetSpeedMagnitude = isAccelerating ? MAX_SPEED_MAGNITUDE : initialSpeed;
                    break;
                default:
                    initialSpeed = BASE_SPEED_MAGNITUDE;
                    targetSpeedMagnitude = initialSpeed;
                    break;
            }
        } else {
            switch(this.packetType) {
                case BULK:
                    isAccelerating = (wire.getRelayPointsCount() > 0);
                    initialSpeed = isAccelerating ? BASE_SPEED_MAGNITUDE : MAX_SPEED_MAGNITUDE;
                    targetSpeedMagnitude = MAX_SPEED_MAGNITUDE;
                    break;
                case WOBBLE: case SECRET:
                    initialSpeed = BASE_SPEED_MAGNITUDE;
                    targetSpeedMagnitude = initialSpeed;
                    break;
                case MESSENGER:
                    if (this.enteredViaIncompatiblePort) {
                        initialSpeed = BASE_SPEED_MAGNITUDE * MESSENGER_INCOMPATIBLE_SPEED_BOOST;
                        targetSpeedMagnitude = initialSpeed;
                    } else if (compatiblePortExit) {
                        initialSpeed = BASE_SPEED_MAGNITUDE;
                        targetSpeedMagnitude = initialSpeed;
                    } else {
                        initialSpeed = BASE_SPEED_MAGNITUDE;
                        targetSpeedMagnitude = 0;
                        isDecelerating = true;
                    }
                    break;
                default:
                    if (this.shape == NetworkEnums.PacketShape.SQUARE) {
                        initialSpeed = compatiblePortExit ? BASE_SPEED_MAGNITUDE * SQUARE_COMPATIBLE_SPEED_FACTOR : BASE_SPEED_MAGNITUDE;
                        targetSpeedMagnitude = initialSpeed;
                    } else if (this.shape == NetworkEnums.PacketShape.TRIANGLE) {
                        initialSpeed = BASE_SPEED_MAGNITUDE;
                        isAccelerating = !compatiblePortExit;
                        targetSpeedMagnitude = isAccelerating ? MAX_SPEED_MAGNITUDE : initialSpeed;
                    } else {
                        initialSpeed = BASE_SPEED_MAGNITUDE;
                        targetSpeedMagnitude = initialSpeed;
                    }
                    break;
            }
        }

        this.currentSpeedMagnitude = initialSpeed;
        this.enteredViaIncompatiblePort = false;
        updateIdealPositionAndVelocity();
    }

    public void transformToProtected() { this.originalPacketType = this.packetType; this.originalSize = this.size; this.originalBaseCoinValue = this.baseCoinValue; this.packetType = NetworkEnums.PacketType.PROTECTED; this.size = this.originalSize * 2; this.baseCoinValue = 5; this.protectedMovementMode = null; }
    public void upgradeSecretPacket() { if (this.packetType == NetworkEnums.PacketType.SECRET && !this.isUpgradedSecret) { this.isUpgradedSecret = true; this.size = 6; this.baseCoinValue = 4; } }
    public void revertToOriginalType() { this.packetType = this.originalPacketType; this.size = this.originalSize; this.baseCoinValue = this.originalBaseCoinValue; this.protectedMovementMode = null; }
    public void reverseDirection(GamePanel gamePanel) { if (this.isReversing) return; this.isReversing = true; this.isAccelerating = false; this.isDecelerating = false; if (!gamePanel.getGame().isMuted()) gamePanel.getGame().playSoundEffect("packet_reverse"); }
    public void setEnteredViaIncompatiblePort(boolean status) { this.enteredViaIncompatiblePort = status; }
    public NetworkEnums.PacketType getPacketType() { return packetType; }
    public void setPacketType(NetworkEnums.PacketType type) { this.packetType = type; }
    public int getId() { return id; }
    public NetworkEnums.PacketShape getShape() { return shape; }
    public int getSize() { return size; }
    public int getBaseCoinValue() { return baseCoinValue; }
    public Point getPosition() { Point2D.Double offset = calculateCurrentVisualOffset(); return new Point( (int)Math.round(idealPosition.x + offset.x), (int)Math.round(idealPosition.y + offset.y) ); }
    public Point2D.Double getPositionDouble() { Point2D.Double offset = calculateCurrentVisualOffset(); return new Point2D.Double( idealPosition.x + offset.x, idealPosition.y + offset.y ); }
    public Point2D.Double getIdealPositionDouble() { return (idealPosition != null) ? new Point2D.Double(idealPosition.x, idealPosition.y) : null; }
    public Wire getCurrentWire() { return currentWire; }
    public double getProgressOnWire() { return progressOnWire; }
    public double getNoise() { return noise; }
    public boolean isMarkedForRemoval() { return markedForRemoval; }
    public void markForRemoval() { this.markedForRemoval = true; }
    public System getCurrentSystem() { return currentSystem; }
    public void setCurrentSystem(System system) { this.currentSystem = system; this.isReversing = false; this.isAccelerating = false; this.isDecelerating = false; this.currentWire = null; this.progressOnWire = 0.0; this.currentSpeedMagnitude = 0.0; if (system != null) this.idealPosition = new Point2D.Double(system.getPosition().x, system.getPosition().y); }
    public int getDrawSize() { return BASE_DRAW_SIZE + (size * 2); }
    public void addNoise(double amount) { if (amount > 0 && !markedForRemoval) this.noise = Math.min(this.size, this.noise + amount); }
    public void resetNoise() { if (!markedForRemoval) this.noise = 0.0; }
    public double getCurrentSpeedMagnitude() { return currentSpeedMagnitude; }
    public void setCurrentSpeedMagnitude(double speed) { this.currentSpeedMagnitude = speed; }
    public void configureAsBit(int parentId, int totalBits) { if(this.packetType == NetworkEnums.PacketType.BIT){ this.bulkParentId = parentId; this.totalBitsInGroup = totalBits; } }
    public int getBulkParentId() { return bulkParentId; }
    public int getTotalBitsInGroup() { return totalBitsInGroup; }
    public PredictedPacketStatus getFinalStatusForPrediction() { return this.finalStatusForPrediction; }
    public void setFinalStatusForPrediction(PredictedPacketStatus status) { this.finalStatusForPrediction = status; }
    public Point2D.Double getVelocity() { if (this.velocity == null) return new Point2D.Double(0,0); return new Point2D.Double(this.velocity.x, this.velocity.y); }
    private void updateIdealPositionAndVelocity() { if (currentWire == null) return; Wire.PathInfo pathInfo = currentWire.getPathInfoAtProgress(progressOnWire); if (pathInfo != null) { this.idealPosition = pathInfo.position; double dx = pathInfo.direction.x * currentSpeedMagnitude; double dy = pathInfo.direction.y * currentSpeedMagnitude; this.velocity = new Point2D.Double(isReversing ? -dx : dx, isReversing ? -dy : dy); } else if (currentWire.getStartPort() != null) { this.idealPosition = currentWire.getStartPort().getPrecisePosition(); this.velocity = new Point2D.Double(0,0); } }
    public void setVisualOffsetDirectionFromForce(Point2D.Double forceDirection) { if (currentWire == null) { if (this.visualOffsetDirection == null) this.visualOffsetDirection = new Point2D.Double(0,1); return; } Wire.PathInfo pathInfo = currentWire.getPathInfoAtProgress(this.progressOnWire); if (pathInfo == null) { if (this.visualOffsetDirection == null) this.visualOffsetDirection = new Point2D.Double(0,1); return; } Point2D.Double wireDir = pathInfo.direction; if (forceDirection == null || (forceDirection.x == 0 && forceDirection.y == 0)) { if (this.visualOffsetDirection == null) { this.visualOffsetDirection = new Point2D.Double(-wireDir.y, wireDir.x); if (this.initialOffsetSidePreference == 1) { this.visualOffsetDirection.x *= -1; this.visualOffsetDirection.y *= -1; } double mag = Math.hypot(this.visualOffsetDirection.x, this.visualOffsetDirection.y); if (mag > 1e-6) { this.visualOffsetDirection.x /= mag; this.visualOffsetDirection.y /= mag; } else { this.visualOffsetDirection = new Point2D.Double(0,1); } } return; } Point2D.Double perp1 = new Point2D.Double(-wireDir.y, wireDir.x); double dotProductWithPerp1 = (forceDirection.x * perp1.x) + (forceDirection.y * perp1.y); if (Math.abs(dotProductWithPerp1) < 1e-6) { if (this.visualOffsetDirection == null) { this.visualOffsetDirection = new Point2D.Double(-wireDir.y, wireDir.x); if (this.initialOffsetSidePreference == 1) { this.visualOffsetDirection.x *= -1; this.visualOffsetDirection.y *= -1; } } } else if (dotProductWithPerp1 > 0) this.visualOffsetDirection = perp1; else this.visualOffsetDirection = new Point2D.Double(-perp1.x, -perp1.y); double mag = Math.hypot(this.visualOffsetDirection.x, this.visualOffsetDirection.y); if (mag > 1e-6) { this.visualOffsetDirection.x /= mag; this.visualOffsetDirection.y /= mag; } else { this.visualOffsetDirection = new Point2D.Double(-wireDir.y, wireDir.x); if (this.initialOffsetSidePreference == 1) { this.visualOffsetDirection.x *= -1; this.visualOffsetDirection.y *= -1; } double m = Math.hypot(this.visualOffsetDirection.x, this.visualOffsetDirection.y); if (m > 1e-6) { this.visualOffsetDirection.x /= m; this.visualOffsetDirection.y /= m; } else { this.visualOffsetDirection = new Point2D.Double(0,1); } } }
    private Point2D.Double calculateCurrentVisualOffset() { if (noise == 0 || currentWire == null) return new Point2D.Double(0, 0); if (this.visualOffsetDirection == null) { Wire.PathInfo pathInfo = currentWire.getPathInfoAtProgress(this.progressOnWire); if (pathInfo == null) return new Point2D.Double(0,0); Point2D.Double wireDir = pathInfo.direction; this.visualOffsetDirection = new Point2D.Double(-wireDir.y, wireDir.x); if (this.initialOffsetSidePreference == 1) { this.visualOffsetDirection.x *= -1; this.visualOffsetDirection.y *= -1; } double mag = Math.hypot(this.visualOffsetDirection.x, this.visualOffsetDirection.y); if (mag > 1e-6) { this.visualOffsetDirection.x /= mag; this.visualOffsetDirection.y /= mag; } else { this.visualOffsetDirection = new Point2D.Double(0,1); } } double halfDrawSize = getDrawSize() / 2.0; double maxPossibleOffsetToVertex = 0; if (shape == NetworkEnums.PacketShape.SQUARE) maxPossibleOffsetToVertex = halfDrawSize * Math.sqrt(2); else if (shape == NetworkEnums.PacketShape.TRIANGLE) maxPossibleOffsetToVertex = halfDrawSize * 1.15; else maxPossibleOffsetToVertex = halfDrawSize; double noiseRatio = Math.min(noise / (double)this.size, 1.0); double currentOffsetMagnitude = maxPossibleOffsetToVertex * noiseRatio; return new Point2D.Double(this.visualOffsetDirection.x * currentOffsetMagnitude, this.visualOffsetDirection.y * currentOffsetMagnitude); }
    public boolean collidesWith(Packet other) { if (this == other || other == null || this.currentSystem != null || other.currentSystem != null || this.markedForRemoval || other.markedForRemoval) return false; Point2D.Double thisPos = this.getPositionDouble(); Point2D.Double otherPos = other.getPositionDouble(); if (thisPos == null || otherPos == null) return false; double distSq = thisPos.distanceSq(otherPos); double r1 = this.getDrawSize() / 2.0; double r2 = other.getDrawSize() / 2.0; double combinedRadius = r1 + r2; double collisionThreshold = combinedRadius * COLLISION_RADIUS_FACTOR; return distSq < (collisionThreshold * collisionThreshold); }
    @Override public boolean equals(Object o) { if (this == o) return true; if (o == null || getClass() != o.getClass()) return false; Packet packet = (Packet) o; return id == packet.id; }
    @Override public int hashCode() { return Objects.hash(id); }
    @Override public String toString() { String status; if (markedForRemoval) status = "REMOVED"; else if (currentSystem != null) status = "QUEUED(Sys:" + currentSystem.getId() + ")"; else if (currentWire != null) status = String.format("ON_WIRE(W:%d P:%.1f%%)", currentWire.getId(), progressOnWire * 100); else status = "IDLE/INIT"; String idealPosStr = (idealPosition != null) ? String.format("%.1f,%.1f", idealPosition.x, idealPosition.y) : "null"; Point2D.Double currentVel = getVelocity(); String velStr = (currentVel != null) ? String.format("%.1f,%.1f (Mag:%.2f)", currentVel.x, currentVel.y, currentSpeedMagnitude) : "null"; return String.format("Packet{ID:%d, SHP:%s, TYP: %s, SZ:%d, N:%.1f/%d, V:%d, Ideal:(%s) Vel:(%s) Accel:%b Decel:%b Rev:%b FinalPred:%s St:%s}", id, shape, packetType, size, noise, this.size, baseCoinValue, idealPosStr, velStr, isAccelerating, isDecelerating, isReversing, (finalStatusForPrediction != null ? finalStatusForPrediction.name() : "N/A"), status); }
}