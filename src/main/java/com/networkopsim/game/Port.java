
        package com.networkopsim.game;
import com.networkopsim.game.NetworkEnums;
import com.networkopsim.game.System;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D; // IMPORT ENSURED
import java.util.Objects;
import java.util.Random;

public class Port {
    public static final int PORT_SIZE = 10;
    private NetworkEnums.PortType type;
    private NetworkEnums.PortShape shape;
    private final System parentSystem;
    private final int index;
    private Point position;
    private boolean isConnected = false;
    private final int id;
    private static int nextId = 0;

    public Port(System parent, NetworkEnums.PortType type, NetworkEnums.PortShape shape, int index) {
        this.id = nextId++;
        this.parentSystem = Objects.requireNonNull(parent, "Port parent system cannot be null");
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
            case CIRCLE:   return new Color(0x4a90e2); // Use a distinct color for bulk/wobble
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

    public void updatePosition(int systemX, int systemY, int totalInputPorts, int totalOutputPorts) {
        this.position = calculatePosition(systemX, systemY, totalInputPorts, totalOutputPorts, this.type, this.index);
    }

    public void draw(Graphics2D g2d) {
        if (position == null) return;
        Color portColor = getColorFromShape(shape);
        g2d.setColor(portColor);
        int x = position.x - PORT_SIZE / 2;
        int y = position.y - PORT_SIZE / 2;
        Path2D path = null;
        switch (shape) {
            case SQUARE:
                g2d.fillRect(x, y, PORT_SIZE, PORT_SIZE);
                break;
            case TRIANGLE:
                path = new Path2D.Double();
                if (type == NetworkEnums.PortType.OUTPUT) {
                    path.moveTo(x, y);
                    path.lineTo(x + PORT_SIZE, position.y);
                    path.lineTo(x, y + PORT_SIZE);
                } else {
                    path.moveTo(x + PORT_SIZE, y);
                    path.lineTo(x, position.y);
                    path.lineTo(x + PORT_SIZE, y + PORT_SIZE);
                }
                path.closePath();
                g2d.fill(path);
                break;
            case CIRCLE:
                g2d.fillOval(x, y, PORT_SIZE, PORT_SIZE);
                break;
            case ANY:
                g2d.setColor(Color.GRAY);
                g2d.fillOval(x + 2, y + 2, PORT_SIZE - 4, PORT_SIZE - 4);
                break;
        }

        if (shape == NetworkEnums.PortShape.ANY) {
            g2d.setColor(Color.DARK_GRAY);
        } else {
            g2d.setColor(portColor.darker().darker());
        }
        g2d.setStroke(new BasicStroke(1));
        switch (shape) {
            case SQUARE:
                g2d.drawRect(x, y, PORT_SIZE, PORT_SIZE);
                break;
            case TRIANGLE:
                if (path != null) g2d.draw(path);
                break;
            case CIRCLE:
                g2d.drawOval(x, y, PORT_SIZE, PORT_SIZE);
                break;
            case ANY:
                g2d.drawOval(x + 2, y + 2, PORT_SIZE - 4, PORT_SIZE - 4);
                break;
        }
    }

    public boolean contains(Point p) {
        if (p == null || position == null) return false;
        int clickRadius = PORT_SIZE + 4;
        Rectangle bounds = new Rectangle(position.x - clickRadius / 2, position.y - clickRadius / 2, clickRadius, clickRadius);
        return bounds.contains(p);
    }

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

    public Point2D.Double getPrecisePosition() {
        return (position != null) ? new Point2D.Double(position.x, position.y) : new Point2D.Double(0,0);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Port port = (Port) o;
        return id == port.id;
    }
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
    @Override
    public String toString() {
        String parentIdStr = (parentSystem != null) ? Integer.toString(parentSystem.getId()) : "null";
        String posStr = (position != null) ? position.x + "," + position.y : "null";
        return "Port{" + id + ":" +
                parentIdStr + "." + type + "[" + index + "]" +
                shape + "@" + posStr +
                (isConnected ? ", Connected" : ", Open") +
                '}';
    }
}