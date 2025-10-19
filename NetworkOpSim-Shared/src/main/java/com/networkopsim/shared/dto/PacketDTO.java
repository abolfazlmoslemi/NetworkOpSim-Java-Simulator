// FILE: /NetworkOpSim-Multiplayer/NetworkOpSim-Shared/src/main/java/com/networkopsim/shared/dto/PacketDTO.java
// ================================================================================

package com.networkopsim.shared.dto;

import com.networkopsim.shared.model.NetworkEnums;

import java.awt.geom.Point2D;
import java.io.Serializable;

/**
 * A Data Transfer Object representing the visual state of a Packet.
 * Contains all necessary data for the client to render a packet on a wire.
 */
public class PacketDTO implements Serializable {
    private static final long serialVersionUID = 2L; // <<<--- VERSION BUMPED ---<<<

    private final int id;
    private final NetworkEnums.PacketShape shape;
    private final NetworkEnums.PacketType packetType;
    private final int size;
    private final double noise;

    // Positional and rotational data
    private final Point2D.Double visualPosition;
    private final Point2D.Double idealPosition;
    private final float angle;
    private final Point2D.Double velocity; // For triangle orientation
    private final boolean isReversing;

    // Special properties
    private final boolean isUpgradedSecret;
    private final int bulkParentId; // For coloring messenger packets

    // <<<--- FIELD ADDED FOR MULTIPLAYER ---<<<
    private final int ownerId; // 1 for player 1, 2 for player 2

    public PacketDTO(int id, NetworkEnums.PacketShape shape, NetworkEnums.PacketType packetType, int size,
                     double noise, Point2D.Double visualPosition, Point2D.Double idealPosition,
                     float angle, Point2D.Double velocity, boolean isReversing,
                     boolean isUpgradedSecret, int bulkParentId, int ownerId) { // <<<--- CONSTRUCTOR MODIFIED ---<<<
        this.id = id;
        this.shape = shape;
        this.packetType = packetType;
        this.size = size;
        this.noise = noise;
        this.visualPosition = visualPosition;
        this.idealPosition = idealPosition;
        this.angle = angle;
        this.velocity = velocity;
        this.isReversing = isReversing;
        this.isUpgradedSecret = isUpgradedSecret;
        this.bulkParentId = bulkParentId;
        this.ownerId = ownerId; // <<<--- LINE ADDED ---<<<
    }

    public int getDrawSize() {
        final int BASE_DRAW_SIZE = 8;
        return BASE_DRAW_SIZE + (size * 2);
    }

    public int getId() {
        return id;
    }

    public NetworkEnums.PacketShape getShape() {
        return shape;
    }

    public NetworkEnums.PacketType getPacketType() {
        return packetType;
    }

    public int getSize() {
        return size;
    }

    public double getNoise() {
        return noise;
    }

    public Point2D.Double getVisualPosition() {
        return visualPosition;
    }

    public Point2D.Double getIdealPosition() {
        return idealPosition;
    }

    public float getAngle() {
        return angle;
    }

    public Point2D.Double getVelocity() {
        return velocity;
    }

    public boolean isReversing() {
        return isReversing;
    }

    public boolean isUpgradedSecret() {
        return isUpgradedSecret;
    }

    public int getBulkParentId() {
        return bulkParentId;
    }

    // <<<--- GETTER ADDED ---<<<
    public int getOwnerId() {
        return ownerId;
    }
}