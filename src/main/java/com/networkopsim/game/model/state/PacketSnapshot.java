package com.networkopsim.game.model.state;

import com.networkopsim.game.model.core.Packet;
import com.networkopsim.game.model.enums.NetworkEnums;
import java.awt.Point;
import java.awt.geom.Point2D;
import java.util.Objects;

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
    private final NetworkEnums.PacketType packetType;

    public PacketSnapshot(Packet packet, PredictedPacketStatus status) {
        Objects.requireNonNull(packet, "Source packet for snapshot cannot be null");
        this.originalPacketId = packet.getId();
        this.shape = packet.getShape();
        Point2D.Double visPos = packet.getVisualPosition();
        this.visualPosition = (visPos != null) ? new Point((int)Math.round(visPos.x), (int)Math.round(visPos.y)) : null;
        this.idealPosition = packet.getIdealPosition();
        this.status = status;
        this.noiseLevel = packet.getNoise();
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
            } else { this.rotationDirection = null; }
        } else { this.rotationDirection = null; }
    }

    public int getOriginalPacketId() { return originalPacketId; }
    public NetworkEnums.PacketShape getShape() { return shape; }
    public Point getVisualPosition() { return visualPosition; }
    public Point2D.Double getIdealPosition() { return idealPosition; }
    public PredictedPacketStatus getStatus() { return status; }
    public double getProgressOnWire() { return progressOnWire; }
    public int getSystemIdIfStalledOrQueued() { return systemIdIfStalledOrQueued; }
    public double getNoiseLevel() { return noiseLevel; }
    public Point2D.Double getRotationDirection() { return rotationDirection; }
    public NetworkEnums.PacketType getPacketType() { return packetType; }
    public int getDrawSize() { int sizeUnits; switch (shape) { case SQUARE: sizeUnits = 2; break; case TRIANGLE: sizeUnits = 3; break; default: sizeUnits = 1; } return Packet.BASE_DRAW_SIZE + (sizeUnits * 2); }
}