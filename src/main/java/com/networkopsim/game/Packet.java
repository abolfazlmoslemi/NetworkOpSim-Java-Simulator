// ===== File: Packet.java =====

package com.networkopsim.game;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.Objects;

public class Packet {
    public static final double BASE_SPEED_MAGNITUDE = 2.1;
    public static final int BASE_DRAW_SIZE = 8;
    public static final double SQUARE_COMPATIBLE_SPEED_FACTOR = 0.5;
    private static final double TRIANGLE_ACCELERATION_RATE = 0.06;
    public static final double MAX_SPEED_MAGNITUDE = 4.2;
    private static final double SECRET_PACKET_SLOW_SPEED_FACTOR = 0.25;
    private static final double WOBBLE_SELF_NOISE_RATE = 0.05;

    private static final double COLLISION_RADIUS_FACTOR = 1.0;
    private static final Font NOISE_FONT = new Font("Arial", Font.PLAIN, 9);
    private static final Color NOISE_TEXT_COLOR = Color.WHITE;
    private static final Color IDEAL_POSITION_MARKER_COLOR = new Color(255, 255, 255, 60);
    private static final int IDEAL_POSITION_MARKER_SIZE = 4;
    private static int nextPacketId = 0;

    private final int id;
    private final NetworkEnums.PacketShape shape;

    private int size;
    private int baseCoinValue;
    private NetworkEnums.PacketType packetType;
    private NetworkEnums.PacketType originalPacketType;
    private int originalSize;
    private int originalBaseCoinValue;
    private boolean isUpgradedSecret = false;

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
    private int initialOffsetSidePreference = 0;

    // --- NEW: Field for reversing direction ---
    private boolean isReversing = false;

    // Fields for Bit/Bulk packet relationship
    private int bulkParentId = -1;
    private int totalBitsInGroup = 0;

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

        switch (type) {
            case SECRET:
                this.size = 4; this.baseCoinValue = 3; break;
            case BULK:
                this.size = 8; this.baseCoinValue = 8; break;
            case WOBBLE:
                this.size = 10; this.baseCoinValue = 10; break;
            case BIT:
                this.size = 1; this.baseCoinValue = 0; // BITs don't give coins, the final BULK might.
                break;
            case MESSENGER:
                this.size = 1; this.baseCoinValue = 1; break;
            case NORMAL:
            case TROJAN:
            case PROTECTED: // Should not be created directly, but have fallback values
            default:
                if (shape == NetworkEnums.PacketShape.SQUARE) {
                    this.size = 2; this.baseCoinValue = 2;
                } else if (shape == NetworkEnums.PacketShape.TRIANGLE) {
                    this.size = 3; this.baseCoinValue = 3;
                } else { // CIRCLE / others
                    this.size = 1; this.baseCoinValue = 1;
                }
                break;
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
        Color packetColor = Port.getColorFromShape(shape);

        if (packetType == NetworkEnums.PacketType.BIT) {
            // Unique color per bulk parent
            packetColor = new Color(Color.HSBtoRGB((this.bulkParentId * 0.27f) % 1.0f, 0.7f, 0.95f));
        } else if (packetType == NetworkEnums.PacketType.BULK) {
            packetColor = new Color(0x4a90e2); // Hexagonal blue color
        } else if (packetType == NetworkEnums.PacketType.WOBBLE) {
            packetColor = new Color(0xd0d0d0); // Light grey/white color
        } else if (packetType == NetworkEnums.PacketType.SECRET) {
            packetColor = isUpgradedSecret ? new Color(110, 110, 120) : new Color(50, 70, 120);
        }
        g2d.setColor(packetColor);

        Path2D path = null;
        AffineTransform oldTransform = g2d.getTransform();
        boolean transformed = false;

        try {
            Point2D.Double directionForRotation = getVelocity();
            if (shape == NetworkEnums.PacketShape.TRIANGLE && directionForRotation != null && Math.hypot(directionForRotation.x, directionForRotation.y) > 0.01) {
                g2d.translate(visualPosition.x, visualPosition.y);
                // NEW: Adjust rotation if reversing
                double angle = Math.atan2(directionForRotation.y, directionForRotation.x);
                if (isReversing) {
                    angle += Math.PI; // Rotate 180 degrees
                }
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
                    default: g2d.fillOval(drawX, drawY, drawSize, drawSize); break;
                }
            }

            Stroke defaultStroke = g2d.getStroke();
            switch (packetType) {
                case PROTECTED:
                    g2d.setColor(new Color(100, 255, 100, 150));
                    g2d.setStroke(new BasicStroke(2.5f));
                    if (transformed && path != null) g2d.draw(path);
                    else if (shape == NetworkEnums.PacketShape.SQUARE) g2d.drawRect(drawX-1, drawY-1, drawSize+2, drawSize+2);
                    else g2d.drawOval(drawX-1, drawY-1, drawSize+2, drawSize+2);
                    break;
                case TROJAN:
                    g2d.setColor(new Color(255, 50, 50, 200));
                    g2d.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, new float[]{3f, 3f}, 0.0f));
                    if (transformed && path != null) g2d.draw(path);
                    else if (shape == NetworkEnums.PacketShape.SQUARE) g2d.drawRect(drawX, drawY, drawSize, drawSize);
                    else g2d.drawOval(drawX, drawY, drawSize, drawSize);
                    break;
                case SECRET:
                    g2d.setColor(isUpgradedSecret ? new Color(200, 200, 210, 220) : new Color(170, 190, 255, 220));
                    g2d.setStroke(new BasicStroke(1.0f));
                    if (transformed) {
                        g2d.setFont(new Font("Arial", Font.BOLD, 9)); g2d.drawString("S", -3, 3);
                    } else {
                        g2d.setFont(new Font("Arial", Font.BOLD, 9)); g2d.drawString("S", drawX + 2, drawY + 9);
                    }
                    break;
                case MESSENGER:
                    g2d.setColor(new Color(255, 255, 100, 255));
                    g2d.setStroke(new BasicStroke(1.0f));
                    if (transformed) {
                        g2d.setStroke(new BasicStroke(2.0f)); g2d.drawLine(-3, 0, -1, 3); g2d.drawLine(-1, 3, 3, -2);
                    } else {
                        int centerX = drawX + halfSize; int centerY = drawY + halfSize;
                        g2d.setStroke(new BasicStroke(2.0f)); g2d.drawLine(centerX - 3, centerY, centerX - 1, centerY + 3); g2d.drawLine(centerX - 1, centerY + 3, centerX + 3, centerY - 2);
                    }
                    break;
                case BIT:
                    g2d.setColor(new Color(255, 255, 255, 180));
                    g2d.setFont(new Font("Monospaced", Font.BOLD, 9));
                    String bitId = String.valueOf(this.bulkParentId);
                    if (transformed) g2d.drawString(bitId, -3, 3);
                    else g2d.drawString(bitId, drawX + 2, drawY + 9);
                    break;
            }
            g2d.setStroke(defaultStroke);

            if (noise > 0.05) {
                float noiseRatio = Math.min(1.0f, (float) (noise / this.size));
                int alpha = Math.min(255, 60 + (int) (noiseRatio * 195));
                Color noiseEffectColor = new Color(200, 0, 0, alpha);
                Composite originalComposite = g2d.getComposite();
                g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, noiseRatio * 0.5f + 0.15f));
                g2d.setColor(noiseEffectColor);
                if (transformed && path != null) g2d.fill(path);
                else {
                    if (shape == NetworkEnums.PacketShape.SQUARE) g2d.fillRect(drawX, drawY, drawSize, drawSize);
                    else if (path != null) g2d.fill(path);
                    else g2d.fillOval(drawX, drawY, drawSize, drawSize);
                }
                g2d.setComposite(originalComposite);
            }
        } finally {
            if (transformed) g2d.setTransform(oldTransform);
            if (noise > 0.05) {
                String noiseString = String.format("N:%.1f", noise);
                g2d.setFont(NOISE_FONT); g2d.setColor(NOISE_TEXT_COLOR);
                FontMetrics fm = g2d.getFontMetrics(); int textWidth = fm.stringWidth(noiseString);
                int textX = (int) Math.round(visualPosition.x - textWidth / 2.0);
                int textY = drawY - fm.getDescent() - 1;
                g2d.drawString(noiseString, textX, textY);
            }
        }
    }

    public void transformToProtected() {
        this.originalPacketType = this.packetType;
        this.originalSize = this.size;
        this.originalBaseCoinValue = this.baseCoinValue;
        this.packetType = NetworkEnums.PacketType.PROTECTED;
        this.size = this.originalSize * 2;
        this.baseCoinValue = 5;
    }

    public void upgradeSecretPacket() {
        if (this.packetType == NetworkEnums.PacketType.SECRET && !this.isUpgradedSecret) {
            this.isUpgradedSecret = true;
            this.size = 6;
            this.baseCoinValue = 4;
        }
    }

    public void revertToOriginalType() {
        this.packetType = this.originalPacketType;
        this.size = this.originalSize;
        this.baseCoinValue = this.originalBaseCoinValue;
    }

    public void setWire(Wire wire, boolean compatiblePortExit) {
        this.currentWire = Objects.requireNonNull(wire, "Cannot set a null wire for packet " + id);
        this.progressOnWire = 0.0;
        this.currentSystem = null;
        this.isReversing = false; // NEW: Ensure isReversing is false on new wire assignment
        if (this.finalStatusForPrediction == PredictedPacketStatus.QUEUED || this.finalStatusForPrediction == PredictedPacketStatus.STALLED_AT_NODE || this.finalStatusForPrediction == PredictedPacketStatus.DELIVERED || this.finalStatusForPrediction == PredictedPacketStatus.LOST) {
            this.finalStatusForPrediction = null;
        }

        double initialSpeed;
        switch(this.packetType) {
            case BULK:
                this.isAccelerating = (wire.getRelayPointsCount() > 0);
                initialSpeed = this.isAccelerating ? BASE_SPEED_MAGNITUDE : MAX_SPEED_MAGNITUDE;
                this.targetSpeedMagnitude = MAX_SPEED_MAGNITUDE;
                break;
            case WOBBLE:
                initialSpeed = BASE_SPEED_MAGNITUDE;
                this.isAccelerating = false;
                this.targetSpeedMagnitude = initialSpeed;
                break;
            default: // Existing logic for SQUARE, TRIANGLE, etc.
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
                break;
        }

        this.currentSpeedMagnitude = initialSpeed;
        updateIdealPositionAndVelocity();
    }

    public void update(GamePanel gamePanel, boolean isAiryamanActive, boolean isSpeedLimiterActive, boolean isPredictionRun) {
        if (markedForRemoval || currentSystem != null) return;
        if (currentWire == null) {
            this.velocity = new Point2D.Double(0,0); this.currentSpeedMagnitude = 0.0; this.isAccelerating = false;
            if (this.finalStatusForPrediction != PredictedPacketStatus.LOST && this.finalStatusForPrediction != PredictedPacketStatus.DELIVERED) setFinalStatusForPrediction(PredictedPacketStatus.LOST);
            gamePanel.packetLostInternal(this, isPredictionRun);
            return;
        }

        if (this.packetType == NetworkEnums.PacketType.SECRET && !this.isUpgradedSecret) {
            System destSystem = currentWire.getEndPort().getParentSystem();
            if (destSystem != null && destSystem.getQueueSize() > 0) {
                targetSpeedMagnitude = BASE_SPEED_MAGNITUDE * SECRET_PACKET_SLOW_SPEED_FACTOR;
            } else {
                targetSpeedMagnitude = BASE_SPEED_MAGNITUDE;
            }
            currentSpeedMagnitude = targetSpeedMagnitude; // Secret packets have instant speed change
            isAccelerating = false;
        } else if (isAccelerating && !isSpeedLimiterActive) { // MODIFIED: Check for speed limiter
            if (currentSpeedMagnitude < targetSpeedMagnitude) currentSpeedMagnitude = Math.min(targetSpeedMagnitude, currentSpeedMagnitude + TRIANGLE_ACCELERATION_RATE);
            else { currentSpeedMagnitude = targetSpeedMagnitude; isAccelerating = false; }
        } else if (this.packetType == NetworkEnums.PacketType.WOBBLE) {
            // Self-inflicted noise for Wobble packets
            if (this.noise < this.size / 2.0) { // Cap noise to prevent self-destruction
                this.addNoise(WOBBLE_SELF_NOISE_RATE);
            }
        }

        double wireLength = currentWire.getLength();
        if (wireLength > 1e-6) {
            // --- NEW: Handle reversing ---
            if(isReversing) {
                progressOnWire -= currentSpeedMagnitude / wireLength;
            } else {
                progressOnWire += currentSpeedMagnitude / wireLength;
            }
        } else {
            progressOnWire = isReversing ? 0.0 : 1.0;
        }
        progressOnWire = Math.max(0.0, Math.min(1.0, progressOnWire));
        updateIdealPositionAndVelocity();

        if (!markedForRemoval && noise >= this.size) {
            setFinalStatusForPrediction(PredictedPacketStatus.LOST);
            gamePanel.packetLostInternal(this, isPredictionRun);
            return;
        }

        // --- NEW: Check for end of travel in both directions ---
        if (!isReversing && progressOnWire >= 1.0 - 1e-9) { // Reached the end
            handleArrival(currentWire.getEndPort(), gamePanel, isPredictionRun);
        } else if (isReversing && progressOnWire <= 1e-9) { // Returned to the start
            this.isReversing = false; // Stop reversing after arrival
            handleArrival(currentWire.getStartPort(), gamePanel, isPredictionRun);
        }
    }

    // --- NEW: Helper for handling arrival at a port ---
    private void handleArrival(Port destinationPort, GamePanel gamePanel, boolean isPredictionRun) {
        if (destinationPort == null || destinationPort.getPosition() == null) {
            setFinalStatusForPrediction(PredictedPacketStatus.LOST);
            gamePanel.packetLostInternal(this, isPredictionRun);
            return;
        }
        this.idealPosition = destinationPort.getPrecisePosition();
        System targetSystem = destinationPort.getParentSystem();
        if (targetSystem != null) {
            if (this.packetType == NetworkEnums.PacketType.BULK && !isPredictionRun) {
                gamePanel.logBulkPacketWireUsage(this.currentWire);
            }
            targetSystem.receivePacket(this, gamePanel, isPredictionRun);
        } else {
            setFinalStatusForPrediction(PredictedPacketStatus.LOST);
            gamePanel.packetLostInternal(this, isPredictionRun);
        }
    }

    // --- NEW: Method to trigger reversal ---
    public void reverseDirection(GamePanel gamePanel) {
        if (this.isReversing) return; // Don't reverse a packet that is already reversing
        this.isReversing = true;
        if (!gamePanel.getGame().isMuted()) {
            gamePanel.getGame().playSoundEffect("packet_reverse");
        }
    }

    public void configureAsBit(int parentId, int totalBits) {
        if(this.packetType == NetworkEnums.PacketType.BIT){
            this.bulkParentId = parentId;
            this.totalBitsInGroup = totalBits;
        }
    }

    public int getBulkParentId() { return bulkParentId; }
    public int getTotalBitsInGroup() { return totalBitsInGroup; }

    public NetworkEnums.PacketType getPacketType() { return packetType; }
    public void setPacketType(NetworkEnums.PacketType packetType) {
        if(this.packetType != packetType){
            this.originalPacketType = this.packetType;
            this.packetType = packetType;
        }
    }
    public Point2D.Double getVelocity() {
        if (this.velocity == null) return new Point2D.Double(0,0);
        return new Point2D.Double(this.velocity.x, this.velocity.y);
    }
    public void setCurrentSystem(System system) {
        this.currentSystem = system;
        this.isReversing = false; // Can't be reversing if it's in a system
        if (this.finalStatusForPrediction == PredictedPacketStatus.ON_WIRE) this.finalStatusForPrediction = null;
        if (system != null) {
            this.currentWire = null; this.progressOnWire = 0.0;
            Point sysPosPoint = system.getPosition();
            this.idealPosition = (sysPosPoint != null) ? new Point2D.Double(sysPosPoint.x, sysPosPoint.y) : new Point2D.Double(0,0);
            this.velocity = new Point2D.Double(0, 0);
            this.currentSpeedMagnitude = 0.0; this.targetSpeedMagnitude = 0.0; this.isAccelerating = false;
        }
    }
    private void updateIdealPositionAndVelocity() {
        if (currentWire == null) return;
        Wire.PathInfo pathInfo = currentWire.getPathInfoAtProgress(progressOnWire);
        if (pathInfo != null) {
            this.idealPosition = pathInfo.position;
            // NEW: Adjust velocity direction if reversing
            if (isReversing) {
                this.velocity = new Point2D.Double(pathInfo.direction.x * -currentSpeedMagnitude, pathInfo.direction.y * -currentSpeedMagnitude);
            } else {
                this.velocity = new Point2D.Double(pathInfo.direction.x * currentSpeedMagnitude, pathInfo.direction.y * currentSpeedMagnitude);
            }
        } else {
            this.idealPosition = currentWire.getStartPort().getPrecisePosition();
            this.velocity = new Point2D.Double(0,0);
        }
    }
    public void setVisualOffsetDirectionFromForce(Point2D.Double forceDirection) {
        if (currentWire == null) { if (this.visualOffsetDirection == null) this.visualOffsetDirection = new Point2D.Double(0,1); return; }
        Wire.PathInfo pathInfo = currentWire.getPathInfoAtProgress(this.progressOnWire);
        if (pathInfo == null) { if (this.visualOffsetDirection == null) this.visualOffsetDirection = new Point2D.Double(0,1); return; }
        Point2D.Double wireDir = pathInfo.direction;
        if (forceDirection == null || (forceDirection.x == 0 && forceDirection.y == 0)) {
            if (this.visualOffsetDirection == null) {
                this.visualOffsetDirection = new Point2D.Double(-wireDir.y, wireDir.x);
                if (this.initialOffsetSidePreference == 1) { this.visualOffsetDirection.x *= -1; this.visualOffsetDirection.y *= -1; }
                double mag = Math.hypot(this.visualOffsetDirection.x, this.visualOffsetDirection.y);
                if (mag > 1e-6) { this.visualOffsetDirection.x /= mag; this.visualOffsetDirection.y /= mag; } else { this.visualOffsetDirection = new Point2D.Double(0,1); }
            }
            return;
        }
        Point2D.Double perp1 = new Point2D.Double(-wireDir.y, wireDir.x);
        double dotProductWithPerp1 = (forceDirection.x * perp1.x) + (forceDirection.y * perp1.y);
        if (Math.abs(dotProductWithPerp1) < 1e-6) {
            if (this.visualOffsetDirection == null) {
                this.visualOffsetDirection = new Point2D.Double(-wireDir.y, wireDir.x);
                if (this.initialOffsetSidePreference == 1) { this.visualOffsetDirection.x *= -1; this.visualOffsetDirection.y *= -1; }
            }
        } else if (dotProductWithPerp1 > 0) this.visualOffsetDirection = perp1;
        else this.visualOffsetDirection = new Point2D.Double(-perp1.x, -perp1.y);
        double mag = Math.hypot(this.visualOffsetDirection.x, this.visualOffsetDirection.y);
        if (mag > 1e-6) { this.visualOffsetDirection.x /= mag; this.visualOffsetDirection.y /= mag;
        } else {
            this.visualOffsetDirection = new Point2D.Double(-wireDir.y, wireDir.x);
            if (this.initialOffsetSidePreference == 1) { this.visualOffsetDirection.x *= -1; this.visualOffsetDirection.y *= -1; }
            double m = Math.hypot(this.visualOffsetDirection.x, this.visualOffsetDirection.y);
            if (m > 1e-6) { this.visualOffsetDirection.x /= m; this.visualOffsetDirection.y /= m; } else { this.visualOffsetDirection = new Point2D.Double(0,1); }
        }
    }
    private Point2D.Double calculateCurrentVisualOffset() {
        if (noise == 0 || currentWire == null) return new Point2D.Double(0, 0);
        if (this.visualOffsetDirection == null) {
            Wire.PathInfo pathInfo = currentWire.getPathInfoAtProgress(this.progressOnWire);
            if (pathInfo == null) return new Point2D.Double(0,0);
            Point2D.Double wireDir = pathInfo.direction;
            this.visualOffsetDirection = new Point2D.Double(-wireDir.y, wireDir.x);
            if (this.initialOffsetSidePreference == 1) { this.visualOffsetDirection.x *= -1; this.visualOffsetDirection.y *= -1; }
            double mag = Math.hypot(this.visualOffsetDirection.x, this.visualOffsetDirection.y);
            if (mag > 1e-6) { this.visualOffsetDirection.x /= mag; this.visualOffsetDirection.y /= mag; } else { this.visualOffsetDirection = new Point2D.Double(0,1); }
        }
        double halfDrawSize = getDrawSize() / 2.0; double maxPossibleOffsetToVertex = 0;
        if (shape == NetworkEnums.PacketShape.SQUARE) maxPossibleOffsetToVertex = halfDrawSize * Math.sqrt(2);
        else if (shape == NetworkEnums.PacketShape.TRIANGLE) maxPossibleOffsetToVertex = halfDrawSize * 1.15;
        else maxPossibleOffsetToVertex = halfDrawSize;
        double noiseRatio = Math.min(noise / (double)this.size, 1.0);
        double currentOffsetMagnitude = maxPossibleOffsetToVertex * noiseRatio;
        return new Point2D.Double(this.visualOffsetDirection.x * currentOffsetMagnitude, this.visualOffsetDirection.y * currentOffsetMagnitude);
    }
    public void markForRemoval() { this.markedForRemoval = true; }
    public void setFinalStatusForPrediction(PredictedPacketStatus status) { this.finalStatusForPrediction = status; }
    public PredictedPacketStatus getFinalStatusForPrediction() { return this.finalStatusForPrediction; }
    public int getId() { return id; }
    public NetworkEnums.PacketShape getShape() { return shape; }
    public int getSize() { return size; }
    public int getBaseCoinValue() { return baseCoinValue; }
    public Point getPosition() { if (idealPosition == null) return new Point(); Point2D.Double offset = calculateCurrentVisualOffset(); return new Point( (int)Math.round(idealPosition.x + offset.x), (int)Math.round(idealPosition.y + offset.y) ); }
    public Point2D.Double getPositionDouble() { if (idealPosition == null) return new Point2D.Double(); Point2D.Double offset = calculateCurrentVisualOffset(); return new Point2D.Double( idealPosition.x + offset.x, idealPosition.y + offset.y ); }
    public Point2D.Double getIdealPositionDouble() { return (idealPosition != null) ? new Point2D.Double(idealPosition.x, idealPosition.y) : new Point2D.Double(); }
    public Wire getCurrentWire() { return currentWire; }
    public double getProgressOnWire() { return progressOnWire; }
    public double getNoise() { return noise; }
    public boolean isMarkedForRemoval() { return markedForRemoval; }
    public System getCurrentSystem() { return currentSystem; }
    public int getDrawSize() { return BASE_DRAW_SIZE + (size * 2); }
    public void addNoise(double amount) { if (amount > 0 && !markedForRemoval) this.noise = Math.min(this.size, this.noise + amount); }
    public void resetNoise() { if (!markedForRemoval && this.noise > 0) { this.noise = 0.0; this.visualOffsetDirection = null; } }
    public boolean collidesWith(Packet other) { if (this == other || other == null) return false; Point2D.Double thisPos = this.getPositionDouble(); Point2D.Double otherPos = other.getPositionDouble(); if (thisPos == null || otherPos == null) return false; if (this.currentSystem != null || other.currentSystem != null || this.markedForRemoval || other.markedForRemoval) return false; double distSq = thisPos.distanceSq(otherPos); double r1 = this.getDrawSize() / 2.0; double r2 = other.getDrawSize() / 2.0; double combinedRadius = r1 + r2; double collisionThreshold = combinedRadius * COLLISION_RADIUS_FACTOR; double collisionThresholdSq = collisionThreshold * collisionThreshold; return distSq < collisionThresholdSq; }
    @Override public boolean equals(Object o) { if (this == o) return true; if (o == null || getClass() != o.getClass()) return false; Packet packet = (Packet) o; return id == packet.id; }
    @Override public int hashCode() { return Objects.hash(id); }
    @Override public String toString() { String status; if (markedForRemoval) status = "REMOVED"; else if (currentSystem != null) status = "QUEUED(Sys:" + currentSystem.getId() + ")"; else if (currentWire != null) status = String.format("ON_WIRE(W:%d P:%.1f%%)", currentWire.getId(), progressOnWire * 100); else status = "IDLE/INIT"; String idealPosStr = (idealPosition != null) ? String.format("%.1f,%.1f", idealPosition.x, idealPosition.y) : "null"; Point2D.Double currentVel = getVelocity(); String velStr = (currentVel != null) ? String.format("%.1f,%.1f (Mag:%.2f)", currentVel.x, currentVel.y, currentSpeedMagnitude) : "null"; return String.format("Packet{ID:%d, SHP:%s, TYP: %s, SZ:%d, N:%.1f/%d, V:%d, Ideal:(%s) Vel:(%s) Accel:%b Rev:%b FinalPred:%s St:%s}", id, shape, packetType, size, noise, this.size, baseCoinValue, idealPosStr, velStr, isAccelerating, isReversing, (finalStatusForPrediction != null ? finalStatusForPrediction.name() : "N/A"), status); }

    // --- NEW: Getter for current speed ---
    public double getCurrentSpeedMagnitude() {
        return currentSpeedMagnitude;
    }
    // --- NEW: Setter for emergency brake ---
    public void setCurrentSpeedMagnitude(double speed) {
        this.currentSpeedMagnitude = speed;
    }
}