// ===== File: SystemDrawer.java (FINAL - With Custom Colored Distributor Queue) =====

package com.networkopsim.game.view.rendering;

import com.networkopsim.game.model.core.Port;
import com.networkopsim.game.model.core.System;
import com.networkopsim.game.model.enums.NetworkEnums;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public final class SystemDrawer {

    private SystemDrawer() {}

    public static void drawSystems(Graphics2D g2d, List<System> systems) {
        for (System s : systems) {
            if (s != null) {
                drawSystem(g2d, s);
            }
        }
    }

    private static void drawSystem(Graphics2D g2d, System system) {
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
        g2d.fillRect(system.getX(), system.getY(), System.SYSTEM_WIDTH, System.SYSTEM_HEIGHT);
        g2d.setColor(Color.LIGHT_GRAY);
        g2d.setStroke(new BasicStroke(1));
        g2d.drawRect(system.getX(), system.getY(), System.SYSTEM_WIDTH, System.SYSTEM_HEIGHT);

        String systemName = system.getSystemType().name();
        int fontSize = (systemName.length() > 8) ? 9 : 11;
        g2d.setFont(new Font("Arial", Font.BOLD, fontSize));
        g2d.setColor(Color.WHITE);
        FontMetrics fm = g2d.getFontMetrics();
        int textWidth = fm.stringWidth(systemName);
        int textX = system.getX() + (System.SYSTEM_WIDTH - textWidth) / 2;
        int textY = system.getY() + (System.SYSTEM_HEIGHT - fm.getHeight()) / 2 + fm.getAscent();
        g2d.drawString(systemName, textX, textY);

        // ... (rest of the drawing logic is the same up to the queue drawing)

        int indicatorSize = 8;
        int indicatorX = system.getX() + System.SYSTEM_WIDTH / 2 - indicatorSize / 2;
        int indicatorY = system.getY() - indicatorSize - 3;
        g2d.setColor(system.isIndicatorOn() ? Color.GREEN.brighter() : new Color(100, 0, 0));
        g2d.fillOval(indicatorX, indicatorY, indicatorSize, indicatorSize);
        g2d.setColor(Color.DARK_GRAY);
        g2d.drawOval(indicatorX, indicatorY, indicatorSize, indicatorSize);

        for (Port p : system.getInputPorts()) if(p!=null) p.draw(g2d);
        for (Port p : system.getOutputPorts()) if(p!=null) p.draw(g2d);

        // [MODIFIED] Custom queue display logic.
        if (!system.isReferenceSystem() && system.getQueueSize() > 0) {
            String queueText;
            Color queueColor;

            if (system.getSystemType() == NetworkEnums.SystemType.DISTRIBUTOR) {
                // For Distributors, show only the current count and use the color of the current BULK operation.
                queueText = String.format("%d", system.getQueueSize());
                int bulkId = system.getCurrentBulkOperationId();
                if (bulkId != -1) {
                    queueColor = new Color(Color.HSBtoRGB((bulkId * 0.27f) % 1.0f, 0.9f, 0.95f));
                } else {
                    queueColor = Color.CYAN; // Fallback color
                }
            } else {
                // For all other systems, show count vs capacity with status colors.
                queueText = String.format("%d/%d", system.getQueueSize(), System.QUEUE_CAPACITY);
                if (system.getQueueSize() == System.QUEUE_CAPACITY) queueColor = Color.RED;
                else if (system.getQueueSize() > System.QUEUE_CAPACITY / 2) queueColor = Color.ORANGE;
                else queueColor = Color.YELLOW;
            }

            g2d.setFont(new Font("Consolas", Font.BOLD, 11));
            fm = g2d.getFontMetrics();
            textWidth = fm.stringWidth(queueText);
            int bgWidth = textWidth + 4;
            int bgHeight = fm.getHeight() - 2;
            int qx = system.getX() + System.SYSTEM_WIDTH - bgWidth - 3;
            int qy = system.getY() + System.SYSTEM_HEIGHT - bgHeight - 3;
            g2d.setColor(new Color(0, 0, 0, 180));
            g2d.fillRoundRect(qx, qy, bgWidth, bgHeight, 5, 5);
            g2d.setColor(queueColor);
            g2d.drawString(queueText, qx + 2, qy + fm.getAscent() -1);
        }

        if(system.getSystemType() == NetworkEnums.SystemType.ANTITROJAN) {
            long currentTime = -1;
            Object timeObj = UIManager.get("game.time.ms");
            if (timeObj instanceof Long) currentTime = (Long) timeObj;
            if(currentTime != -1 && currentTime < system.getAntiTrojanCooldownUntil()){
                g2d.setColor(new Color(0, 0, 0, 150));
                g2d.fillOval(system.getX() + 10, system.getY() + 10, System.SYSTEM_WIDTH - 20, System.SYSTEM_HEIGHT - 20);
                g2d.setColor(Color.CYAN);
                g2d.setStroke(new BasicStroke(2));
                double cooldownProgress = (double)(system.getAntiTrojanCooldownUntil() - currentTime) / 8000.0;
                g2d.drawArc(system.getX() + 15, system.getY() + 15, System.SYSTEM_WIDTH - 30, System.SYSTEM_HEIGHT - 30, 90, (int)(360 * cooldownProgress));
            }
        }

        if (system.isDisabled()) {
            g2d.setColor(new Color(255, 0, 0, 100));
            g2d.fillRect(system.getX(), system.getY(), System.SYSTEM_WIDTH, System.SYSTEM_HEIGHT);
            g2d.setColor(Color.RED);
            g2d.setStroke(new BasicStroke(3));
            g2d.drawLine(system.getX(), system.getY(), system.getX() + System.SYSTEM_WIDTH, system.getY() + System.SYSTEM_HEIGHT);
            g2d.drawLine(system.getX() + System.SYSTEM_WIDTH, system.getY(), system.getX(), system.getY() + System.SYSTEM_HEIGHT);
        }
    }
}
