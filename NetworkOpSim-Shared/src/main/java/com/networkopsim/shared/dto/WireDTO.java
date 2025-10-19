// FILE: /NetworkOpSim-Multiplayer/NetworkOpSim-Shared/src/main/java/com/networkopsim/shared/dto/WireDTO.java
// ================================================================================

package com.networkopsim.shared.dto;

import java.awt.Point;
import java.awt.geom.Point2D;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * A Data Transfer Object representing the visual state of a Wire.
 * It contains all necessary information for the client to render a wire,
 * including its relay points and traversal count for bulk packets.
 */
public class WireDTO implements Serializable {
    private static final long serialVersionUID = 3L; // <-- Version bumped

    private final int id;
    private final Point startPortPosition;
    private final Point endPortPosition;
    private final List<Point2D.Double> relayPoints;
    private final int bulkPacketTraversals;
    private final int maxBulkTraversals;
    private final boolean isDestroyed;
    private final int ownerId;
    private final boolean collidesWithSystem; // <-- NEW FIELD

    public WireDTO(int id, Point startPortPosition, Point endPortPosition, List<Point2D.Double> relayPoints,
                   int bulkPacketTraversals, int maxBulkTraversals, boolean isDestroyed, int ownerId, boolean collidesWithSystem) { // <-- CONSTRUCTOR MODIFIED
        this.id = id;
        this.startPortPosition = startPortPosition;
        this.endPortPosition = endPortPosition;
        this.relayPoints = relayPoints;
        this.bulkPacketTraversals = bulkPacketTraversals;
        this.maxBulkTraversals = maxBulkTraversals;
        this.isDestroyed = isDestroyed;
        this.ownerId = ownerId;
        this.collidesWithSystem = collidesWithSystem; // <-- NEW
    }

    /**
     * Helper method to get the full path of the wire for drawing.
     * @return A list of all points (start, relays, end) defining the wire's path.
     */
    public List<Point2D.Double> getFullPathPoints() {
        List<Point2D.Double> path = new ArrayList<>();
        path.add(new Point2D.Double(startPortPosition.x, startPortPosition.y));
        path.addAll(relayPoints);
        path.add(new Point2D.Double(endPortPosition.x, endPortPosition.y));
        return path;
    }

    public int getId() {
        return id;
    }

    public Point getStartPortPosition() {
        return startPortPosition;
    }

    public Point getEndPortPosition() {
        return endPortPosition;
    }

    public List<Point2D.Double> getRelayPoints() {
        return relayPoints;
    }

    public int getBulkPacketTraversals() {
        return bulkPacketTraversals;
    }

    public int getMaxBulkTraversals() {
        return maxBulkTraversals;
    }

    public boolean isDestroyed() {
        return isDestroyed;
    }

    public int getOwnerId() {
        return ownerId;
    }

    public boolean isCollidingWithSystem() { // <-- NEW GETTER
        return collidesWithSystem;
    }
}