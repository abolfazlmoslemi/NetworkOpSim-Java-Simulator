package com.networkopsim.game.view.rendering;

import com.networkopsim.game.model.core.Packet;
import com.networkopsim.game.model.core.Port;
import com.networkopsim.game.model.core.System;
import com.networkopsim.game.model.enums.NetworkEnums;

import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.Random;

/**
 * A helper class dedicated to rendering Packet objects.
 * This encapsulates the drawing logic that was previously in the Packet class.
 */
public final class PacketDrawer {

    private PacketDrawer() {}

    /**
     * Draws the main body of the packet (shape and primary color).
     */
    public static void drawPacketBody(Graphics2D g2d, Packet packet, int drawX, int drawY, int drawSize, int halfSize) {
        NetworkEnums.PacketType packetType = packet.getPacketType();
        NetworkEnums.PacketShape shape = packet.getShape();

        if (packetType == NetworkEnums.PacketType.MESSENGER && shape == NetworkEnums.PacketShape.CIRCLE) {
            drawMessengerBody(g2d, drawSize);
        } else {
            switch (packetType) {
                case PROTECTED:
                    drawProtectedBody(g2d, drawSize);
                    break;
                case SECRET:
                    drawSecretBody(g2d, packet.isUpgradedSecret(), drawX, drawY, drawSize);
                    break;
                case BULK:
                    drawBulkBody(g2d, drawSize);
                    break;
                case WOBBLE:
                    drawWobbleBody(g2d, drawSize);
                    break;
                default: // NORMAL, TROJAN, BIT
                    drawDefaultBody(g2d, packet, shape, drawX, drawY, drawSize, halfSize);
                    break;
            }
        }
    }

    /**
     * Draws overlays and decorations on top of the packet body (e.g., Trojan outlines, Bit numbers).
     */
    public static void drawPacketOverlays(Graphics2D g2d, Packet packet, int drawX, int drawY, int drawSize, int halfSize) {
        Stroke defaultStroke = g2d.getStroke();
        if (packet.getPacketType() != NetworkEnums.PacketType.PROTECTED && packet.getPacketType() != NetworkEnums.PacketType.BULK &&
                packet.getPacketType() != NetworkEnums.PacketType.WOBBLE && !(packet.getPacketType() == NetworkEnums.PacketType.MESSENGER && packet.getShape() == NetworkEnums.PacketShape.CIRCLE) &&
                packet.getPacketType() != NetworkEnums.PacketType.SECRET) {

            switch (packet.getPacketType()) {
                case TROJAN:
                    g2d.setColor(new Color(255, 50, 50, 200));
                    g2d.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, new float[]{3f, 3f}, 0.0f));
                    if (packet.getShape() == NetworkEnums.PacketShape.SQUARE) g2d.drawRect(drawX, drawY, drawSize, drawSize);
                    else g2d.drawOval(drawX, drawY, drawSize, drawSize);
                    break;
                case BIT:
                    g2d.setColor(new Color(255, 255, 255, 180));
                    g2d.setFont(new Font("Monospaced", Font.BOLD, 9));
                    String bitId = String.valueOf(packet.getBulkParentId());
                    g2d.drawString(bitId, drawX + 2, drawY + 9);
                    break;
            }
        }
        g2d.setStroke(defaultStroke);
    }

    private static void drawDefaultBody(Graphics2D g2d, Packet packet, NetworkEnums.PacketShape shape, int drawX, int drawY, int drawSize, int halfSize) {
        Color packetColor = Port.getColorFromShape(shape);
        if (packet.getPacketType() == NetworkEnums.PacketType.BIT) {
            packetColor = new Color(Color.HSBtoRGB((packet.getBulkParentId() * 0.27f) % 1.0f, 0.7f, 0.95f));
        }
        g2d.setColor(packetColor);

        switch (shape) {
            case SQUARE:
                g2d.fillRect(drawX, drawY, drawSize, drawSize);
                break;
            case CIRCLE:
                g2d.fillOval(drawX, drawY, drawSize, drawSize);
                break;
            case TRIANGLE:
                Path2D path = new Path2D.Double();
                path.moveTo(halfSize, 0); path.lineTo(-halfSize, -halfSize); path.lineTo(-halfSize, halfSize);
                path.closePath();
                g2d.fill(path);
                break;
        }
    }

    private static void drawMessengerBody(Graphics2D g2d, int drawSize) {
        double scale = drawSize * 1.8;
        Path2D.Double leftPolygon = new Path2D.Double();
        leftPolygon.moveTo(-0.45 * scale, 0.15 * scale); leftPolygon.lineTo(-0.20 * scale, 0.35 * scale);
        leftPolygon.lineTo(0.05 * scale, 0.15 * scale); leftPolygon.lineTo(0.05 * scale, -0.15 * scale);
        leftPolygon.lineTo(-0.20 * scale, -0.35 * scale); leftPolygon.lineTo(-0.45 * scale, -0.15 * scale);
        leftPolygon.closePath();

        Path2D.Double rightPolygon = new Path2D.Double();
        rightPolygon.moveTo(0.45 * scale, -0.15 * scale); rightPolygon.lineTo(0.20 * scale, -0.35 * scale);
        rightPolygon.lineTo(-0.05 * scale, -0.15 * scale); rightPolygon.lineTo(-0.05 * scale, 0.15 * scale);
        rightPolygon.lineTo(0.20 * scale, 0.35 * scale); rightPolygon.lineTo(0.45 * scale, 0.15 * scale);
        rightPolygon.closePath();

        g2d.setColor(Color.WHITE);
        g2d.fill(leftPolygon);
        g2d.fill(rightPolygon);
    }

    private static void drawProtectedBody(Graphics2D g2d, int drawSize) {
        Color mainBodyColor = new Color(205, 120, 50); Color shackleColor = new Color(240, 190, 100); Color shadowLineColor = new Color(160, 90, 40); Color keyholeColor = Color.BLACK;
        int bodyWidth = (int) (drawSize * 0.8); int bodyX = -bodyWidth / 2; int bodyY = (int) (-drawSize * 0.2); int bodyHeight = (int) (drawSize * 0.7); int shackleWidth = (int) (drawSize * 0.6); int shackleX = -shackleWidth / 2; int shackleThickness = (int) (Math.max(2, drawSize * 0.15)); int shackleHeight = (int) (drawSize * 0.4);
        g2d.setColor(shackleColor); g2d.fillRect(shackleX, -drawSize / 2, shackleWidth, shackleThickness); g2d.fillRect(shackleX, -drawSize/2, shackleThickness, shackleHeight); g2d.fillRect(shackleX + shackleWidth - shackleThickness, -drawSize/2, shackleThickness, shackleHeight);
        g2d.setColor(mainBodyColor); g2d.fillRect(bodyX, bodyY, bodyWidth, bodyHeight);
        g2d.setColor(shadowLineColor); int lineWidth = (int) (Math.max(1, bodyWidth * 0.1)); int lineX1 = bodyX + (int) (bodyWidth * 0.2); int lineX2 = bodyX + (int) (bodyWidth * 0.8) - lineWidth; g2d.fillRect(lineX1, bodyY, lineWidth, bodyHeight); g2d.fillRect(lineX2, bodyY, lineWidth, bodyHeight);
        g2d.setColor(keyholeColor); int keyholeCircleDiameter = (int) (drawSize * 0.25); int keyholeCircleX = -keyholeCircleDiameter / 2; int keyholeCircleY = bodyY + (int) (bodyHeight * 0.15); g2d.fillOval(keyholeCircleX, keyholeCircleY, keyholeCircleDiameter, keyholeCircleDiameter); int keyholeStemTopY = keyholeCircleY + keyholeCircleDiameter / 2; int keyholeStemWidthTop = (int) (drawSize * 0.1); int keyholeStemWidthBottom = (int) (drawSize * 0.25); int keyholeStemHeight = (int) (bodyHeight * 0.5); int keyholeStemBottomY = keyholeStemTopY + keyholeStemHeight; int[] xPoints = { -keyholeStemWidthTop / 2, keyholeStemWidthTop / 2, keyholeStemWidthBottom / 2, -keyholeStemWidthBottom / 2 }; int[] yPoints = { keyholeStemTopY, keyholeStemTopY, keyholeStemBottomY, keyholeStemBottomY }; g2d.fillPolygon(xPoints, yPoints, 4);
    }

    private static void drawSecretBody(Graphics2D g2d, boolean isUpgraded, int drawX, int drawY, int drawSize) {
        Color[] blueCamoPalette = { new Color(30, 40, 80), new Color(60, 80, 130), new Color(110, 120, 150) }; Color[] grayCamoPalette = { new Color(50, 50, 50), new Color(100, 100, 100), new Color(150, 150, 150) }; Color[] selectedPalette = isUpgraded ? grayCamoPalette : blueCamoPalette;
        Shape oldClip = g2d.getClip(); g2d.setClip(new java.awt.geom.Ellipse2D.Double(drawX, drawY, drawSize, drawSize)); int pixelSize = Math.max(2, drawSize / 6); Random rand = System.getGlobalRandom();
        for (int py = drawY; py < drawY + drawSize; py += pixelSize) { for (int px = drawX; px < drawX + drawSize; px += pixelSize) { g2d.setColor(selectedPalette[rand.nextInt(selectedPalette.length)]); g2d.fillRect(px, py, pixelSize, pixelSize); } }
        g2d.setClip(oldClip); g2d.setColor(Color.BLACK); g2d.setStroke(new BasicStroke(1.0f)); g2d.drawOval(drawX, drawY, drawSize, drawSize);
    }

    private static void drawBulkBody(Graphics2D g2d, int drawSize) {
        Color darkBlue = new Color(20, 40, 100); Color midBlue = new Color(40, 80, 160); Color lightBlue = new Color(80, 140, 220); Color outlineColor = new Color(200, 220, 255, 150);
        double radius = drawSize / 3.5; double h_offset = radius * 1.5; double v_offset = radius * Math.sqrt(3.0) / 2.0;
        Point2D.Double[] centers = { new Point2D.Double(0, -v_offset * 2), new Point2D.Double(-h_offset / 2, -v_offset), new Point2D.Double(h_offset / 2, -v_offset), new Point2D.Double(-h_offset, 0), new Point2D.Double(0, 0), new Point2D.Double(h_offset, 0), new Point2D.Double(-h_offset / 2, v_offset), new Point2D.Double(h_offset / 2, v_offset), new Point2D.Double(0, v_offset * 2) };
        g2d.setStroke(new BasicStroke(1.5f));
        for (Point2D.Double centerOffset : centers) { Color fillColor = (centerOffset.y < -v_offset * 1.5) ? darkBlue : (centerOffset.y > v_offset * 1.5) ? lightBlue : midBlue; Path2D hexagonPath = createHexagon(centerOffset.x, centerOffset.y, radius); g2d.setColor(fillColor); g2d.fill(hexagonPath); g2d.setColor(outlineColor); g2d.draw(hexagonPath); }
    }

    private static Path2D createHexagon(double centerX, double centerY, double radius) { Path2D hexagon = new Path2D.Double(); for (int i = 0; i < 6; i++) { double angle = Math.toRadians(60 * i); double x = centerX + radius * Math.cos(angle); double y = centerY + radius * Math.sin(angle); if (i == 0) hexagon.moveTo(x, y); else hexagon.lineTo(x, y); } hexagon.closePath(); return hexagon; }

    private static void drawWobbleBody(Graphics2D g2d, int drawSize) {
        Color darkNodeColor=new Color(15,60,115); Color midNodeColor=new Color(40,100,150); Color lightNodeColor=new Color(130,180,210); Color lightLineColor=new Color(130,180,210,150);
        double scale_wobble=drawSize*0.5; double nodeRadius=Math.max(1.5,drawSize*0.08);
        Point[] nodes=new Point[13]; nodes[0]=new Point(0,0);
        for (int i=0;i<6;i++){double ang=Math.toRadians(60*i);nodes[i+1]=new Point((int)(scale_wobble*Math.cos(ang)),(int)(scale_wobble*Math.sin(ang)));}
        for (int i=0;i<6;i++){double ang=Math.toRadians(60*i+30);nodes[i+7]=new Point((int)(scale_wobble*2*Math.cos(ang)),(int)(scale_wobble*2*Math.sin(ang)));}
        int[][] connections={{0,1},{0,2},{0,3},{0,4},{0,5},{0,6},{1,2},{2,3},{3,4},{4,5},{5,6},{6,1},{1,7},{1,8},{2,8},{2,9},{3,9},{3,10},{4,10},{4,11},{5,11},{5,12},{6,12},{6,7},{7,8},{8,9},{9,10},{10,11},{11,12},{12,7}};
        g2d.setStroke(new BasicStroke((float)Math.max(1.0,drawSize*0.04),BasicStroke.CAP_BUTT,BasicStroke.JOIN_MITER,10.0f,new float[]{2f,2f},0.0f)); g2d.setColor(lightLineColor);
        for(int[] conn:connections)g2d.drawLine(nodes[conn[0]].x,nodes[conn[0]].y,nodes[conn[1]].x,nodes[conn[1]].y);
        int nodeDiameter=(int)(nodeRadius*2); g2d.setColor(darkNodeColor);
        for(int i=7;i<=12;i++)g2d.fillOval(nodes[i].x-(int)nodeRadius,nodes[i].y-(int)nodeRadius,nodeDiameter,nodeDiameter);
        g2d.setColor(lightNodeColor); for(int i=1;i<=6;i++)g2d.fillOval(nodes[i].x-(int)nodeRadius,nodes[i].y-(int)nodeRadius,nodeDiameter,nodeDiameter);
        g2d.setColor(midNodeColor); int arcNodeDiameter=(int)(nodeRadius*3); g2d.fillArc(nodes[0].x-arcNodeDiameter/2,nodes[0].y-arcNodeDiameter/2,arcNodeDiameter,arcNodeDiameter,45,270);
    }
}