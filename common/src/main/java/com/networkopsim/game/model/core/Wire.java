// ===== File: Wire.java (FINAL COMPLETE VERSION for common) =====
// ===== MODULE: common =====

package com.networkopsim.game.model.core;

import com.networkopsim.game.model.enums.NetworkEnums;
import java.awt.*;
import java.awt.geom.Point2D;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

public class Wire implements Serializable {
    private static final long serialVersionUID = 2L; // Version updated
    private static int nextId = 0;
    public static final int MAX_RELAY_POINTS = 3;
    public static final int RELAY_POINT_SIZE = 8;
    public static final int RELAY_CLICK_RADIUS = 12;
    public static final int MAX_BULK_TRAVERSALS = 3;

    private final int id;
    private final Port startPort;
    private final Port endPort;
    private final List<RelayPoint> relayPoints = new CopyOnWriteArrayList<>();
    private double length;
    private int bulkPacketTraversals = 0;

    public static class RelayPoint implements Serializable {
        private static final long serialVersionUID = 2L;
        private transient Wire parentWire;
        private Point2D.Double position;
        private final Point2D.Double lastGoodPosition;
        private boolean isHovered = false;
        private boolean isDragged = false;

        public RelayPoint(Wire parent, Point2D.Double pos) {
            this.parentWire = parent;
            this.position = pos;
            this.lastGoodPosition = new Point2D.Double(pos.x, pos.y);
        }

        public Point2D.Double getPosition() { return new Point2D.Double(position.x, position.y); }
        public void setPosition(Point2D.Double position) { this.position = position; }
        public void setHovered(boolean hovered) { isHovered = hovered; }
        public void setDragged(boolean dragged) { isDragged = dragged; }
        public void revertToLastPosition() { this.position = new Point2D.Double(this.lastGoodPosition.x, this.lastGoodPosition.y); }
        public Wire getParentWire() { return parentWire; }
        public void setParentWire(Wire parent) { this.parentWire = parent; }

        public void draw(Graphics2D g2d) {
            int drawSize = RELAY_POINT_SIZE;
            int x = (int)Math.round(position.x - drawSize / 2.0);
            int y = (int)Math.round(position.y - drawSize / 2.0);

            if (isDragged) g2d.setColor(new Color(255, 165, 0));
            else if (isHovered) g2d.setColor(Color.YELLOW);
            else g2d.setColor(Color.CYAN);
            g2d.fillOval(x, y, drawSize, drawSize);

            g2d.setColor(Color.DARK_GRAY);
            g2d.setStroke(new BasicStroke(1.5f));
            g2d.drawOval(x, y, drawSize, drawSize);
        }

        public boolean contains(Point p) {
            return p != null && position.distanceSq(p) < (RELAY_CLICK_RADIUS * RELAY_CLICK_RADIUS);
        }
    }

    public static class PathInfo {
        public final Point2D.Double position;
        public final Point2D.Double direction;
        public PathInfo(Point2D.Double position, Point2D.Double direction) { this.position = position; this.direction = direction; }
    }

    public Wire(Port startPort, Port endPort) {
        this.startPort = Objects.requireNonNull(startPort, "Wire startPort cannot be null.");
        this.endPort = Objects.requireNonNull(endPort, "Wire endPort cannot be null.");
        if (startPort.getType() != NetworkEnums.PortType.OUTPUT) throw new IllegalArgumentException("Wire startPort must be of type OUTPUT.");
        if (endPort.getType() != NetworkEnums.PortType.INPUT) throw new IllegalArgumentException("Wire endPort must be of type INPUT.");
        if (Objects.equals(startPort.getParentSystem(), endPort.getParentSystem())) throw new IllegalArgumentException("Wire cannot connect ports on the same system.");
        if (startPort.isConnected() || endPort.isConnected()) throw new IllegalStateException("One or both ports are already connected.");
        this.id = nextId++;
        startPort.setConnected(true);
        endPort.setConnected(true);
        this.recalculateLength();
    }

    public void recalculateLength() {
        this.length = 0;
        List<Point2D.Double> path = getFullPathPoints();
        for (int i = 0; i < path.size() - 1; i++) {
            this.length += path.get(i).distance(path.get(i+1));
        }
    }

    public List<Point2D.Double> getFullPathPoints() {
        List<Point2D.Double> path = new ArrayList<>();
        path.add(startPort.getPrecisePosition());
        for (RelayPoint rp : relayPoints) {
            path.add(rp.getPosition());
        }
        path.add(endPort.getPrecisePosition());
        return path;
    }

    public void draw(Graphics2D g2d, boolean isWireVisuallyValid) {
        if (startPort == null || endPort == null) return;
        List<Point2D.Double> path = getFullPathPoints();
        if (path.size() < 2) return;

        g2d.setStroke(new BasicStroke(isWireVisuallyValid ? 2.0f : 2.5f));
        g2d.setColor(isWireVisuallyValid ? Color.GRAY.brighter() : new Color(255, 0, 0, 180));

        for (int i = 0; i < path.size() - 1; i++) {
            Point2D.Double p1 = path.get(i);
            Point2D.Double p2 = path.get(i + 1);
            g2d.drawLine((int)p1.x, (int)p1.y, (int)p2.x, (int)p2.y);
        }

        if (bulkPacketTraversals > 0) {
            Point midPoint = new Point((int)((path.get(0).x + path.get(path.size()-1).x)/2), (int)((path.get(0).y + path.get(path.size()-1).y)/2));
            String traversalText = bulkPacketTraversals + "/" + MAX_BULK_TRAVERSALS;
            g2d.setFont(new Font("Arial", Font.BOLD, 10));
            Color textColor = (bulkPacketTraversals >= MAX_BULK_TRAVERSALS -1) ? Color.RED : Color.ORANGE;
            g2d.setColor(textColor);
            g2d.drawString(traversalText, midPoint.x, midPoint.y - 5);
        }

        for (RelayPoint rp : relayPoints) {
            rp.draw(g2d);
        }
    }

    public PathInfo getPathInfoAtProgress(double progress) {
        progress = Math.max(0.0, Math.min(1.0, progress));
        double targetLength = this.length * progress;
        double traversedLength = 0;
        List<Point2D.Double> path = getFullPathPoints();
        for (int i = 0; i < path.size() - 1; i++) {
            Point2D.Double p1 = path.get(i);
            Point2D.Double p2 = path.get(i + 1);
            double segmentLength = p1.distance(p2);
            if (traversedLength + segmentLength >= targetLength - 1e-9) {
                double remainingLengthOnSegment = targetLength - traversedLength;
                double progressOnSegment = (segmentLength > 1e-9) ? remainingLengthOnSegment / segmentLength : 0;
                double dx = p2.x - p1.x;
                double dy = p2.y - p1.y;
                Point2D.Double position = new Point2D.Double(p1.x + dx * progressOnSegment, p1.y + dy * progressOnSegment);
                double magnitude = Math.hypot(dx, dy);
                Point2D.Double direction = (magnitude > 1e-9) ? new Point2D.Double(dx / magnitude, dy / magnitude) : new Point2D.Double(1, 0);
                return new PathInfo(position, direction);
            }
            traversedLength += segmentLength;
        }
        Point2D.Double last = path.get(path.size() - 1);
        Point2D.Double secondToLast = path.get(path.size() - 2);
        double dx = last.x - secondToLast.x;
        double dy = last.y - secondToLast.y;
        double magnitude = Math.hypot(dx, dy);
        Point2D.Double direction = (magnitude > 1e-9) ? new Point2D.Double(dx / magnitude, dy / magnitude) : new Point2D.Double(1, 0);
        return new PathInfo(last, direction);
    }

    public void addRelayPoint(Point2D.Double pos) {
        if (relayPoints.size() < MAX_RELAY_POINTS) {
            relayPoints.add(new RelayPoint(this, pos));
            recalculateLength();
        }
    }

    public void removeRelayPoint(RelayPoint relayPoint) {
        if (relayPoints.remove(relayPoint)) {
            recalculateLength();
        }
    }

    public RelayPoint getRelayPointAt(Point p) {
        for (RelayPoint rp : relayPoints) {
            if (rp.contains(p)) return rp;
        }
        return null;
    }

    public void clearHoverStates() {
        for(RelayPoint rp : relayPoints) rp.setHovered(false);
    }

    public void recordBulkPacketTraversal() {
        this.bulkPacketTraversals++;
    }

    public boolean isDestroyed() {
        return bulkPacketTraversals >= MAX_BULK_TRAVERSALS;
    }

    public int getId() { return id; }
    public Port getStartPort() { return startPort; }
    public Port getEndPort() { return endPort; }
    public double getLength() { return length; }

    // NEW PUBLIC GETTER
    public List<RelayPoint> getRelayPoints() {
        return relayPoints;
    }

    public int getRelayPointsCount() { return relayPoints.size(); }

    public void destroy() {
        if (startPort != null) startPort.setConnected(false);
        if (endPort != null) endPort.setConnected(false);
    }

    public void rebuildTransientReferences(Map<Integer, System> systemMap) {
        if(startPort != null) startPort.rebuildTransientReferences(systemMap);
        if(endPort != null) endPort.rebuildTransientReferences(systemMap);
        for(RelayPoint rp : relayPoints) {
            rp.setParentWire(this);
        }
    }

    @Override public boolean equals(Object o) { if (this == o) return true; if (o == null || getClass() != o.getClass()) return false; Wire wire = (Wire) o; return id == wire.id; }
    @Override public int hashCode() { return Objects.hash(id); }
}