// ===== File: SystemDrawer.java (FINAL CORRECTED VERSION v2) =====
// ===== MODULE: client =====

package com.networkopsim.game.view.rendering;

import com.networkopsim.game.model.core.Port;
import com.networkopsim.game.model.core.System;
import com.networkopsim.game.model.enums.NetworkEnums;

import java.awt.*;
import java.util.List;

public final class SystemDrawer {

    private SystemDrawer() {}

    // CORRECTED SIGNATURE: Added simulationTimeMs parameter
    public static void drawSystems(Graphics2D g2d, List<System> systems, long simulationTimeMs) {
        for (System s : systems) {
            if (s != null) {
                drawSystem(g2d, s, simulationTimeMs);
            }
        }
    }

    // CORRECTED SIGNATURE: Added simulationTimeMs parameter
    public static void drawSystem(Graphics2D g2d, System system, long simulationTimeMs) {
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

        if (system.getSystemType() == NetworkEnums.SystemType.SOURCE) {
            NetworkEnums.PacketType genType = system.getPacketTypeToGenerate();
            String label = null;
            Color labelColor = null;

            if (genType == NetworkEnums.PacketType.BULK) { label = "(BULK)"; labelColor = new Color(255, 175, 75); }
            else if (genType == NetworkEnums.PacketType.SECRET) { label = "(SECRET)"; labelColor = new Color(170, 140, 255); }
            else if (genType == NetworkEnums.PacketType.WOBBLE) { label = "(WOBBLE)"; labelColor = new Color(150, 255, 150); }

            if (label != null) {
                g2d.setFont(new Font("Arial", Font.BOLD, 10));
                FontMetrics fmLabel = g2d.getFontMetrics();
                int labelWidth = fmLabel.stringWidth(label);
                int labelX = system.getX() + (System.SYSTEM_WIDTH - labelWidth) / 2;
                int labelY = system.getY() + System.SYSTEM_HEIGHT + fmLabel.getAscent();
                g2d.setColor(labelColor);
                g2d.drawString(label, labelX, labelY);
            }
        }

        int indicatorSize = 8;
        int indicatorX = system.getX() + System.SYSTEM_WIDTH / 2 - indicatorSize / 2;
        int indicatorY = system.getY() - indicatorSize - 3;
        g2d.setColor(system.isIndicatorOn() ? Color.GREEN.brighter() : new Color(100, 0, 0));
        g2d.fillOval(indicatorX, indicatorY, indicatorSize, indicatorSize);
        g2d.setColor(Color.DARK_GRAY);
        g2d.drawOval(indicatorX, indicatorY, indicatorSize, indicatorSize);

        for (Port p : system.getInputPorts()) if(p!=null) p.draw(g2d);
        for (Port p : system.getOutputPorts()) if(p!=null) p.draw(g2d);

        if (!system.isReferenceSystem() && system.getQueueSize() > 0) {
            String queueText;
            Color queueColor;
            if (system.getSystemType() == NetworkEnums.SystemType.DISTRIBUTOR) {
                queueText = String.format("%d", system.getQueueSize());
                int bulkId = system.getCurrentBulkOperationId();
                if (bulkId != -1) {
                    float hue = ((bulkId * 31) + 17) % 100 / 100.0f;
                    queueColor = new Color(Color.HSBtoRGB(hue, 0.9f, 0.95f));
                } else {
                    queueColor = Color.CYAN;
                }
            } else {
                queueText = String.format("%d/%d", system.getQueueSize(), System.QUEUE_CAPACITY);
                if (system.getQueueSize() >= System.QUEUE_CAPACITY) queueColor = Color.RED;
                else if (system.getQueueSize() > System.QUEUE_CAPACITY / 2) queueColor = Color.ORANGE;
                else queueColor = Color.YELLOW;
            }
            g2d.setFont(new Font("Consolas", Font.BOLD, 11));
            fm = g2d.getFontMetrics();
            int bgWidth = fm.stringWidth(queueText) + 4;
            int bgHeight = fm.getHeight() - 2;
            int qx = system.getX() + System.SYSTEM_WIDTH - bgWidth - 3;
            int qy = system.getY() + System.SYSTEM_HEIGHT - bgHeight - 3;
            g2d.setColor(new Color(0, 0, 0, 180));
            g2d.fillRoundRect(qx, qy, bgWidth, bgHeight, 5, 5);
            g2d.setColor(queueColor);
            g2d.drawString(queueText, qx + 2, qy + fm.getAscent() -1);
        }

        if(system.getSystemType() == NetworkEnums.SystemType.ANTITROJAN) {
            // CORRECTED: Use the passed-in simulationTimeMs
            if(simulationTimeMs > 0 && simulationTimeMs < system.getAntiTrojanCooldownUntil()){
                g2d.setColor(new Color(0, 0, 0, 150));
                g2d.fillOval(system.getX() + 10, system.getY() + 10, System.SYSTEM_WIDTH - 20, System.SYSTEM_HEIGHT - 20);
                g2d.setColor(Color.CYAN);
                g2d.setStroke(new BasicStroke(2));
                double cooldownProgress = (double)(system.getAntiTrojanCooldownUntil() - simulationTimeMs) / 8000.0;
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