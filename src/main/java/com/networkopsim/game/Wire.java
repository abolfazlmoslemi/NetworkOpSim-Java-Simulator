package com.networkopsim.game;

import java.awt.*;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.Objects;
public class Wire {
    private static int nextId = 0; 
    private final int id;
    private final Port startPort; 
    private final Port endPort;   
    private final double length;  
    public Wire(Port startPort, Port endPort) {
        this.startPort = Objects.requireNonNull(startPort, "Wire startPort cannot be null.");
        this.endPort = Objects.requireNonNull(endPort, "Wire endPort cannot be null.");
        if (startPort.getType() != NetworkEnums.PortType.OUTPUT) {
            throw new IllegalArgumentException("Wire startPort must be of type OUTPUT. Found: " + startPort.getType());
        }
        if (endPort.getType() != NetworkEnums.PortType.INPUT) {
            throw new IllegalArgumentException("Wire endPort must be of type INPUT. Found: " + endPort.getType());
        }
        if (Objects.equals(startPort.getParentSystem(), endPort.getParentSystem())) {
            if (startPort.getParentSystem() != null) { 
                throw new IllegalArgumentException("Wire cannot connect ports on the same system (ID: " + startPort.getParentSystem().getId() + ").");
            } else {
                throw new IllegalArgumentException("Wire cannot connect ports on the same system (parent system is null).");
            }
        }
        if (startPort.isConnected()) {
            throw new IllegalStateException("Start port " + startPort + " is already connected.");
        }
        if (endPort.isConnected()) {
            throw new IllegalStateException("End port " + endPort + " is already connected.");
        }
        this.id = nextId++;
        Point startPos = startPort.getPosition();
        Point endPos = endPort.getPosition();
        if (startPos == null || endPos == null) {
            java.lang.System.err.println("WARNING: Creating wire with null port positions. Wire ID: " + this.id);
            this.length = 0.0; 
        } else {
            this.length = startPos.distance(endPos);
        }
        startPort.setConnected(true);
        endPort.setConnected(true);
    }
    public void draw(Graphics2D g2d) {
        if (startPort == null || endPort == null || startPort.getPosition() == null || endPort.getPosition() == null) {
            return;
        }
        g2d.setColor(Color.GRAY.brighter()); 
        g2d.setStroke(new BasicStroke(2));   
        g2d.drawLine(startPort.getX(), startPort.getY(), endPort.getX(), endPort.getY());
    }
    public int getId() { return id; }
    public Port getStartPort() { return startPort; }
    public Port getEndPort() { return endPort; }
    public double getLength() { return length; }
    public Point getPointAtProgress(double progress) {
        progress = Math.max(0.0, Math.min(1.0, progress));
        Point2D startP = startPort.getPosition();
        Point2D endP = endPort.getPosition();
        if (startP == null || endP == null) {
            return new Point();
        }
        double currentX = startP.getX() + (endP.getX() - startP.getX()) * progress;
        double currentY = startP.getY() + (endP.getY() - startP.getY()) * progress;
        return new Point((int) Math.round(currentX), (int) Math.round(currentY));
    }
    public Point2D.Double getDirectionVector() {
        Point startP = startPort.getPosition();
        Point endP = endPort.getPosition();
        if (startP == null || endP == null) {
            return new Point2D.Double(1, 0);
        }
        double dx = endP.getX() - startP.getX();
        double dy = endP.getY() - startP.getY();
        double magnitude = Math.sqrt(dx * dx + dy * dy);
        if (magnitude < 1e-9) { 
            return new Point2D.Double(1, 0); 
        } else {
            return new Point2D.Double(dx / magnitude, dy / magnitude);
        }
    }
    public void destroy() {
        if (startPort != null) {
            startPort.setConnected(false);
        }
        if (endPort != null) {
            endPort.setConnected(false);
        }
        java.lang.System.out.println("Destroyed wire " + id + ", disconnecting ports: " + startPort.getId() + " <-> " + endPort.getId());
    }
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Wire wire = (Wire) o;
        return Objects.equals(startPort, wire.startPort) &&
                Objects.equals(endPort, wire.endPort);
    }
    @Override
    public int hashCode() {
        return Objects.hash(startPort, endPort);
    }
    @Override
    public String toString() {
        String startDesc = (startPort != null) ? startPort.toString() : "null_start";
        String endDesc = (endPort != null) ? endPort.toString() : "null_end";
        return "Wire{id=" + id +
                ", from=" + startDesc +
                ", to=" + endDesc +
                ", length=" + String.format("%.1f", length) +
                '}';
    }
}