// FILE: /NetworkOpSim-Multiplayer/NetworkOpSim-Client/src/main/java/com/networkopsim/client/view/rendering/SystemDrawer.java
// ================================================================================

package com.networkopsim.client.view.rendering;

import com.networkopsim.shared.dto.SystemDTO;
import com.networkopsim.shared.model.NetworkEnums;

import java.awt.*;
import java.awt.geom.Path2D;

public final class SystemDrawer {

    private static final int SYSTEM_WIDTH = 80;
    private static final int SYSTEM_HEIGHT = 60;
    private static final int PORT_SIZE = 10;

    private static final Color PLAYER_OWNED_BORDER = new Color(60, 150, 255);
    private static final Color OPPONENT_OWNED_BORDER = new Color(255, 100, 60);
    private static final Color SABOTAGE_LOCK_COLOR = new Color(220, 50, 50);
    private static final Color CONTROLLABLE_ICON_COLOR = new Color(200, 200, 70);

    // --- New Indicator Colors ---
    private static final Color INDICATOR_ON_COLOR = new Color(80, 220, 80);
    private static final Color INDICATOR_OFF_COLOR = new Color(150, 50, 50);

    private SystemDrawer() {}

    public static void drawSystem(Graphics2D g2d, SystemDTO system, int localPlayerId) {
        Color bodyColor;
        switch(system.getSystemType()) {
            case SOURCE: case SINK: bodyColor = new Color(90, 90, 90); break;
            case SPY: bodyColor = new Color(130, 60, 130); break;
            case CORRUPTOR: bodyColor = new Color(150, 40, 40); break;
            case VPN: bodyColor = new Color(60, 130, 60); break;
            case ANTITROJAN: bodyColor = new Color(60, 130, 130); break;
            case DISTRIBUTOR: bodyColor = new Color(160, 100, 40); break;
            case MERGER: bodyColor = new Color(40, 100, 160); break;
            case NODE: default: bodyColor = new Color(60, 80, 130); break;
        }

        g2d.setColor(bodyColor);
        g2d.fillRect(system.getX(), system.getY(), SYSTEM_WIDTH, SYSTEM_HEIGHT);
        g2d.setColor(Color.LIGHT_GRAY);
        g2d.setStroke(new BasicStroke(1));
        g2d.drawRect(system.getX(), system.getY(), SYSTEM_WIDTH, SYSTEM_HEIGHT);

        if (system.getOwnerId() != 0) {
            Color borderColor = (system.getOwnerId() == localPlayerId) ? PLAYER_OWNED_BORDER : OPPONENT_OWNED_BORDER;
            g2d.setColor(borderColor);
            g2d.setStroke(new BasicStroke(3));
            g2d.drawRect(system.getX() - 2, system.getY() - 2, SYSTEM_WIDTH + 4, SYSTEM_HEIGHT + 4);
        }

        String systemName = system.getSystemType().name();
        g2d.setFont(new Font("Arial", Font.BOLD, 11));
        g2d.setColor(Color.WHITE);
        FontMetrics fm = g2d.getFontMetrics();
        int textWidth = fm.stringWidth(systemName);
        int textX = system.getX() + (SYSTEM_WIDTH - textWidth) / 2;
        int textY = system.getY() + (SYSTEM_HEIGHT - fm.getHeight()) / 2 + fm.getAscent();
        g2d.drawString(systemName, textX, textY);

        drawConnectionIndicator(g2d, system); // <-- NEW METHOD CALL

        for (SystemDTO.PortDTO p : system.getPorts()) {
            if(p != null) drawPort(g2d, p);
        }

        if (system.getQueueSize() > 0) {
            drawQueueIndicator(g2d, system);
        }

        if (system.isControllable()) {
            drawControllableIndicator(g2d, system);
            long cooldownMs = system.getSystemCooldownRemainingMs();
            if (cooldownMs > 0) {
                drawCooldownOverlay(g2d, system, cooldownMs, 15000); // 15000 is total cooldown
            }
        }


        if (system.isDisabled()) {
            g2d.setColor(new Color(255, 0, 0, 100));
            g2d.fillRect(system.getX(), system.getY(), SYSTEM_WIDTH, SYSTEM_HEIGHT);
            g2d.setColor(Color.RED);
            g2d.setStroke(new BasicStroke(3));
            g2d.drawLine(system.getX(), system.getY(), system.getX() + SYSTEM_WIDTH, system.getY() + SYSTEM_HEIGHT);
            g2d.drawLine(system.getX() + SYSTEM_WIDTH, system.getY(), system.getX(), system.getY() + SYSTEM_HEIGHT);
        }

        if (system.getDisabledForPlayerId() == localPlayerId) {
            drawSabotageLock(g2d, system);
        }
    }

    private static void drawConnectionIndicator(Graphics2D g2d, SystemDTO system) {
        int indicatorSize = 8;
        int x = system.getX() + (SYSTEM_WIDTH - indicatorSize) / 2;
        int y = system.getY() + 5;

        if (system.areAllPortsConnected()) {
            g2d.setColor(INDICATOR_ON_COLOR);
        } else {
            g2d.setColor(INDICATOR_OFF_COLOR);
        }

        g2d.fillOval(x, y, indicatorSize, indicatorSize);
        g2d.setColor(g2d.getColor().darker());
        g2d.setStroke(new BasicStroke(1));
        g2d.drawOval(x, y, indicatorSize, indicatorSize);
    }

    private static void drawPort(Graphics2D g2d, SystemDTO.PortDTO port) {
        if (port.getPosition() == null) return;
        Color portColor = getPortColor(port.getShape());
        g2d.setColor(portColor);

        int x = port.getPosition().x - PORT_SIZE / 2;
        int y = port.getPosition().y - PORT_SIZE / 2;
        Path2D path = null;
        switch (port.getShape()) {
            case SQUARE: g2d.fillRect(x, y, PORT_SIZE, PORT_SIZE); break;
            case TRIANGLE:
                path = new Path2D.Double();
                if (port.getType() == NetworkEnums.PortType.OUTPUT) {
                    path.moveTo(x, y); path.lineTo(x + PORT_SIZE, port.getPosition().y); path.lineTo(x, y + PORT_SIZE);
                } else {
                    path.moveTo(x + PORT_SIZE, y); path.lineTo(x, port.getPosition().y); path.lineTo(x + PORT_SIZE, y + PORT_SIZE);
                }
                path.closePath(); g2d.fill(path); break;
            case CIRCLE: g2d.fillOval(x, y, PORT_SIZE, PORT_SIZE); break;
            case ANY: g2d.setColor(Color.GRAY); g2d.fillOval(x + 2, y + 2, PORT_SIZE - 4, PORT_SIZE - 4); break;
        }

        g2d.setColor(portColor.darker().darker());
        g2d.setStroke(new BasicStroke(1));
        if (path != null) g2d.draw(path);
        else g2d.draw(new Rectangle(x,y,PORT_SIZE,PORT_SIZE));
    }

    private static void drawQueueIndicator(Graphics2D g2d, SystemDTO system) {
        String queueText = String.format("%d/%d", system.getQueueSize(), system.getQueueCapacity());
        Color queueColor;
        if (system.getQueueSize() >= system.getQueueCapacity()) queueColor = Color.RED;
        else if (system.getQueueSize() > system.getQueueCapacity() / 2) queueColor = Color.ORANGE;
        else queueColor = Color.YELLOW;

        g2d.setFont(new Font("Consolas", Font.BOLD, 11));
        FontMetrics fm = g2d.getFontMetrics();
        int textWidth = fm.stringWidth(queueText);
        int bgWidth = textWidth + 4;
        int bgHeight = fm.getHeight() - 2;
        int qx = system.getX() + SYSTEM_WIDTH - bgWidth - 3;
        int qy = system.getY() + 3;

        g2d.setColor(new Color(0, 0, 0, 180));
        g2d.fillRoundRect(qx, qy, bgWidth, bgHeight, 5, 5);
        g2d.setColor(queueColor);
        g2d.drawString(queueText, qx + 2, qy + fm.getAscent() - 1);
    }

    // --- New Drawing Methods ---

    private static void drawControllableIndicator(Graphics2D g2d, SystemDTO system) {
        int iconSize = 8;
        int x = system.getX() + 5;
        int y = system.getY() + 5;
        g2d.setColor(CONTROLLABLE_ICON_COLOR);
        g2d.setStroke(new BasicStroke(2));
        // Draw a star shape
        Path2D.Double star = new Path2D.Double();
        star.moveTo(x + iconSize / 2.0, y);
        for (int i = 1; i < 5; i++) {
            double angle = Math.PI / 2.5 * i;
            double radius = (i % 2 == 0) ? iconSize / 2.0 : iconSize / 4.0;
            star.lineTo(x + iconSize / 2.0 + radius * Math.sin(angle), y + iconSize / 2.0 - radius * Math.cos(angle));
        }
        star.closePath();
        g2d.fill(star);
    }

    private static void drawCooldownOverlay(Graphics2D g2d, SystemDTO system, long remainingMs, long totalMs) {
        double percentage = (double) remainingMs / totalMs;
        int angle = (int) (percentage * 360);

        Color overlayColor = new Color(0, 0, 0, 150);
        g2d.setColor(overlayColor);

        Composite oldComposite = g2d.getComposite();
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.6f));
        g2d.fillArc(system.getX(), system.getY(), SYSTEM_WIDTH, SYSTEM_HEIGHT, 90, angle);
        g2d.setComposite(oldComposite);
    }

    private static void drawSabotageLock(Graphics2D g2d, SystemDTO system) {
        int centerX = system.getX() + SYSTEM_WIDTH / 2;
        int centerY = system.getY() + SYSTEM_HEIGHT / 2;
        int lockBodyWidth = 20;
        int lockBodyHeight = 15;
        int shackleRadius = 8;

        g2d.setColor(new Color(0,0,0,128));
        g2d.fillOval(centerX - 25, centerY - 25, 50, 50);

        g2d.setColor(SABOTAGE_LOCK_COLOR);
        g2d.setStroke(new BasicStroke(3));

        // Shackle (arc)
        g2d.drawArc(centerX - shackleRadius, centerY - lockBodyHeight / 2 - shackleRadius * 2, shackleRadius * 2, shackleRadius * 2, 0, 180);

        // Body
        g2d.fillRoundRect(centerX - lockBodyWidth / 2, centerY - lockBodyHeight / 2, lockBodyWidth, lockBodyHeight, 5, 5);
    }

    private static Color getPortColor(NetworkEnums.PortShape shape) {
        if (shape == null) return Color.WHITE;
        switch (shape) {
            case SQUARE:   return Color.CYAN;
            case TRIANGLE: return Color.YELLOW;
            case CIRCLE:   return Color.ORANGE;
            case ANY:      return Color.LIGHT_GRAY;
            default:       return Color.WHITE;
        }
    }
}