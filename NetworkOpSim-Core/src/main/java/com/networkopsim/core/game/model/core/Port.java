// FILE: /NetworkOpSim-Multiplayer/NetworkOpSim-Server/src/main/java/com/networkopsim/server/game/model/core/Port.java
// ================================================================================

package com.networkopsim.core.game.model.core;

import com.networkopsim.shared.model.NetworkEnums;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.BasicStroke;
import java.awt.Rectangle;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.io.Serializable;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

public class Port implements Serializable {
    private static final long serialVersionUID = 1L;
    public static final int PORT_SIZE = 10;
    private NetworkEnums.PortType type;
    private NetworkEnums.PortShape shape;
    private transient System parentSystem;
    private final int parentSystemId;
    private final int index;
    private Point position;
    private boolean isConnected = false;
    private final int id;
    private static int nextId = 0;

    public Port(System parent, NetworkEnums.PortType type, NetworkEnums.PortShape shape, int index) {
        this.id = nextId++;
        this.parentSystem = Objects.requireNonNull(parent, "Port parent system cannot be null");
        this.parentSystemId = parent.getId();
        this.type = Objects.requireNonNull(type, "Port type cannot be null");
        this.shape = Objects.requireNonNull(shape, "Port shape cannot be null");
        this.index = index;
        int estInputCount = parent.getInputPorts().size() + (type == NetworkEnums.PortType.INPUT ? 1 : 0);
        int estOutputCount = parent.getOutputPorts().size() + (type == NetworkEnums.PortType.OUTPUT ? 1 : 0);
        this.position = calculatePosition(parent.getX(), parent.getY(), estInputCount, estOutputCount, type, index);
    }

    public void randomizeShape() {
        Random rand = System.getGlobalRandom();
        int pick = rand.nextInt(NetworkEnums.PortShape.values().length - 1); // Exclude ANY
        this.shape = NetworkEnums.PortShape.values()[pick];
    }

    public static void resetGlobalId() {
        nextId = 0;
    }

    public static Point calculatePosition(int systemX, int systemY, int totalInputPorts, int totalOutputPorts, NetworkEnums.PortType portType, int portIndex) {
        int parentWidth = System.SYSTEM_WIDTH;
        int parentHeight = System.SYSTEM_HEIGHT;
        int x, y;
        int totalPortsOnSide = (portType == NetworkEnums.PortType.INPUT) ? Math.max(1, totalInputPorts) : Math.max(1, totalOutputPorts);
        x = (portType == NetworkEnums.PortType.INPUT) ? systemX : systemX + parentWidth;
        int spacing = parentHeight / (totalPortsOnSide + 1);
        y = systemY + (portIndex + 1) * spacing;
        return new Point(x, y);
    }

    public static Color getColorFromShape(NetworkEnums.PortShape shape) {
        if (shape == null) return Color.WHITE;
        switch (shape) {
            case SQUARE:   return Color.CYAN;
            case TRIANGLE: return Color.YELLOW;
            case CIRCLE:   return Color.ORANGE;
            case ANY:      return Color.LIGHT_GRAY;
            default:       return Color.WHITE;
        }
    }

    public static Color getColorFromShape(NetworkEnums.PacketShape shape) {
        if (shape == null) return Color.WHITE;
        switch (shape) {
            case SQUARE:   return Color.CYAN;
            case TRIANGLE: return Color.YELLOW;
            case CIRCLE:   return new Color(0x4a90e2);
            default:       return Color.WHITE;
        }
    }

    public static NetworkEnums.PortShape getShapeEnum(NetworkEnums.PacketShape packetShape) {
        if (packetShape == null) return null;
        switch (packetShape) {
            case SQUARE:   return NetworkEnums.PortShape.SQUARE;
            case TRIANGLE: return NetworkEnums.PortShape.TRIANGLE;
            case CIRCLE:   return NetworkEnums.PortShape.CIRCLE;
            default:       return null;
        }
    }

    public static NetworkEnums.PacketShape getPacketShapeFromPortShapeStatic(NetworkEnums.PortShape portShape) {
        if (portShape == null) return null;
        switch (portShape) {
            case SQUARE: return NetworkEnums.PacketShape.SQUARE;
            case TRIANGLE: return NetworkEnums.PacketShape.TRIANGLE;
            case CIRCLE: return NetworkEnums.PacketShape.CIRCLE;
            default: return null;
        }
    }

    public void updatePosition(int systemX, int systemY, int totalInputPorts, int totalOutputPorts) {
        this.position = calculatePosition(systemX, systemY, totalInputPorts, totalOutputPorts, this.type, this.index);
    }

    public void rebuildTransientReferences(Map<Integer, System> systemMap) { this.parentSystem = systemMap.get(this.parentSystemId); }
    public int getId() { return id; }
    public NetworkEnums.PortType getType() { return type; }
    public NetworkEnums.PortShape getShape() { return shape; }
    public System getParentSystem() { return parentSystem; }
    public Point getPosition() { return (position != null) ? new Point(position.x, position.y) : new Point(0,0); }
    public int getX() { return position != null ? position.x : 0; }
    public int getY() { return position != null ? position.y : 0; }
    public boolean isConnected() { return isConnected; }
    public int getIndex() { return index; }
    public void setConnected(boolean connected) { isConnected = connected; }
    public Point2D.Double getPrecisePosition() { return (position != null) ? new Point2D.Double(position.x, position.y) : new Point2D.Double(0,0); }
    @Override public boolean equals(Object o) { if (this == o) return true; if (o == null || getClass() != o.getClass()) return false; Port port = (Port) o; return id == port.id; }
    @Override public int hashCode() { return Objects.hash(id); }
    @Override public String toString() { String parentIdStr = (parentSystem != null) ? Integer.toString(parentSystem.getId()) : "null"; String posStr = (position != null) ? position.x + "," + position.y : "null"; return "Port{" + id + ":" + parentIdStr + "." + type + "[" + index + "]" + shape + "@" + posStr + (isConnected ? ", Connected" : ", Open") + '}'; }
}