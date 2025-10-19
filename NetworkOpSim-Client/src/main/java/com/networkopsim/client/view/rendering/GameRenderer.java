// FILE: /NetworkOpSim-Multiplayer/NetworkOpSim-Client/src/main/java/com/networkopsim/client/view/rendering/GameRenderer.java
// ================================================================================

package com.networkopsim.client.view.rendering;

import com.networkopsim.client.core.NetworkGame;
import com.networkopsim.client.view.panels.GamePanel;
import com.networkopsim.shared.dto.*;
import com.networkopsim.shared.model.NetworkEnums;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class GameRenderer {
    private final GamePanel gamePanel;

    // --- New fields for interpolation ---
    private final Map<Integer, Point2D.Double> packetVisualPositions = new ConcurrentHashMap<>();
    private static final double INTERPOLATION_FACTOR = 0.3;

    // --- All rendering constants ---
    private static final Font HUD_FONT_BOLD = new Font("Consolas", Font.BOLD, 16);
    private static final Font HUD_TITLE_FONT = new Font("Arial", Font.BOLD, 18);
    private static final Font PAUSE_OVERLAY_FONT_LARGE = new Font("Arial", Font.BOLD, 60);
    private static final Font PAUSE_OVERLAY_FONT_SMALL = new Font("Arial", Font.BOLD, 24);
    private static final Font END_GAME_OVERLAY_FONT_LARGE = new Font("Arial", Font.BOLD, 72);
    private static final Font TEMP_MESSAGE_FONT = new Font("Arial", Font.BOLD, 16);
    private static final Font BUILD_PHASE_TIMER_FONT = new Font("Arial", Font.BOLD, 48);
    private static final Font BUILD_PHASE_SUBTEXT_FONT = new Font("Arial", Font.PLAIN, 16);


    private static final Color GRID_COLOR = new Color(40, 40, 50);
    private static final Color HUD_BACKGROUND_COLOR = new Color(20, 20, 30, 210);
    private static final Color GAME_OVER_COLOR = Color.RED.darker();
    private static final Color LEVEL_COMPLETE_COLOR = Color.GREEN.darker();

    // Player/Opponent specific colors
    private static final Color PLAYER_COLOR_PRIMARY = new Color(60, 150, 255);
    private static final Color OPPONENT_COLOR_PRIMARY = new Color(255, 100, 60);
    private static final Color INVALID_WIRING_COLOR = new Color(220, 0, 0);

    private static final Color IDEAL_POSITION_MARKER_COLOR = new Color(255, 255, 255, 60);
    private static final int IDEAL_POSITION_MARKER_SIZE = 4;
    private static final Font NOISE_FONT = new Font("Arial", Font.PLAIN, 9);
    private static final Color NOISE_TEXT_COLOR = Color.WHITE;
    private static final int GRID_SIZE = 25;

    public static final Color PLAYER_1_PACKET_COLOR = new Color(0x4a90e2); // Blue
    public static final Color PLAYER_2_PACKET_COLOR = new Color(0xd0021b); // Red

    public GameRenderer(GamePanel gamePanel) {
        this.gamePanel = gamePanel;
    }

    public void render(Graphics g, GameStateDTO state) {
        Graphics2D g2d = (Graphics2D) g.create();
        try {
            setupHighQualityRendering(g2d);
            drawGrid(g2d);

            for (WireDTO w : state.getWires()) {
                if (w != null) drawWire(g2d, w, state.getLocalPlayerId());
            }

            for (SystemDTO s : state.getSystems()) {
                SystemDrawer.drawSystem(g2d, s, state.getLocalPlayerId());
            }

            updateAndDrawPackets(g2d, state);

            String gamePhase = state.getGamePhase();
            if (gamePanel.isOnlineMultiplayer() && ("PRE_BUILD".equals(gamePhase) || "OVERTIME_BUILD".equals(gamePhase))) {
                drawBuildPhaseUI(g2d, state);
            } else {
                if (gamePanel.isOnlineMultiplayer()) {
                    drawMultiplayerHUD(g2d, state);
                } else {
                    drawSinglePlayerHUD(g2d, state);
                }
            }


            drawTemporaryMessage(g2d);

            if (state.isSimulationPaused()) {
                drawPauseOverlay(g2d, state);
            } else if (state.isGameOver()) {
                drawEndGameOverlay(g2d, "GAME OVER", GAME_OVER_COLOR);
            } else if (state.isLevelComplete()) {
                drawEndGameOverlay(g2d, "LEVEL COMPLETE", LEVEL_COMPLETE_COLOR);
            }
        } finally {
            g2d.dispose();
        }
    }

    private void updateAndDrawPackets(Graphics2D g2d, GameStateDTO state) {
        // Cleanup old packets that are no longer in the state
        packetVisualPositions.keySet().removeIf(packetId -> state.getPackets().stream().noneMatch(p -> p.getId() == packetId));

        for (PacketDTO packet : state.getPackets()) {
            Point2D.Double serverPos = packet.getVisualPosition();
            if (serverPos == null) continue;

            if (gamePanel.isOfflineMode()) {
                // In offline, the simulation is local and already smooth, so we just draw the packet directly.
                drawPacket(g2d, packet, serverPos);
            } else {
                // In online, we interpolate for smooth movement.
                Point2D.Double currentVisualPos = packetVisualPositions.computeIfAbsent(packet.getId(), id -> new Point2D.Double(serverPos.x, serverPos.y));

                // Interpolate position
                currentVisualPos.x += (serverPos.x - currentVisualPos.x) * INTERPOLATION_FACTOR;
                currentVisualPos.y += (serverPos.y - currentVisualPos.y) * INTERPOLATION_FACTOR;

                drawPacket(g2d, packet, currentVisualPos);
            }
        }
    }

    private void drawPacket(Graphics2D g2d, PacketDTO packet, Point2D.Double visualPosition) {
        if (visualPosition == null) return;

        if (packet.getIdealPosition() != null && packet.getNoise() > 0.05) {
            g2d.setColor(IDEAL_POSITION_MARKER_COLOR);
            int markerHalf = IDEAL_POSITION_MARKER_SIZE / 2;
            int idealXInt = (int)Math.round(packet.getIdealPosition().x);
            int idealYInt = (int)Math.round(packet.getIdealPosition().y);
            g2d.drawLine(idealXInt - markerHalf, idealYInt, idealXInt + markerHalf, idealYInt);
            g2d.drawLine(idealXInt, idealYInt - markerHalf, idealXInt, idealYInt + markerHalf);
        }

        int drawSize = packet.getDrawSize();
        int halfSize = drawSize / 2;
        AffineTransform oldTransform = g2d.getTransform();
        try {
            g2d.translate(visualPosition.x, visualPosition.y);
            double totalAngle = packet.getAngle();
            if (packet.getShape() == NetworkEnums.PacketShape.TRIANGLE) {
                Point2D.Double direction = packet.getVelocity();
                if (direction != null && (Math.abs(direction.x) > 0.01 || Math.abs(direction.y) > 0.01)) {
                    double angleFromVelocity = Math.atan2(direction.y, direction.x);
                    if (packet.isReversing()) angleFromVelocity += Math.PI;
                    totalAngle += angleFromVelocity;
                }
            }
            g2d.rotate(totalAngle);

            Color baseColor;
            if (gamePanel.isOnlineMultiplayer()) {
                baseColor = (packet.getOwnerId() == 1) ? PLAYER_1_PACKET_COLOR : PLAYER_2_PACKET_COLOR;
            } else {
                baseColor = PacketDrawer.getColorFromPacketShape(packet.getShape());
            }

            PacketDrawer.drawPacketBody(g2d, packet, -halfSize, -halfSize, drawSize, baseColor);
            PacketDrawer.drawPacketOverlays(g2d, packet, -halfSize, -halfSize, drawSize, halfSize);

        } finally {
            g2d.setTransform(oldTransform);
            if (packet.getNoise() > 0.05) {
                g2d.setFont(NOISE_FONT);
                g2d.setColor(NOISE_TEXT_COLOR);
                String noiseText = String.format("%.1f", packet.getNoise());
                FontMetrics fm = g2d.getFontMetrics();
                int textX = (int) Math.round(visualPosition.x + halfSize + 2);
                int textY = (int) Math.round(visualPosition.y - halfSize + fm.getAscent() / 2.0);
                g2d.drawString(noiseText, textX, textY);
            }
        }
    }

    private void drawWire(Graphics2D g2d, WireDTO wire, int localPlayerId) {
        java.util.List<Point2D.Double> path = wire.getFullPathPoints();
        if (path.size() < 2) return;

        boolean isDamaged = wire.getBulkPacketTraversals() > 0;
        boolean isBroken = wire.isDestroyed();

        Color wireColor;
        float strokeWidth = 2.0f;

        if (gamePanel.isOnlineMultiplayer()) {
            if (wire.getOwnerId() == 0) { // Neutral wires
                wireColor = Color.GRAY.brighter();
            } else if (wire.getOwnerId() == localPlayerId) { // Player's own wires
                wireColor = PLAYER_COLOR_PRIMARY;
                strokeWidth = 2.5f;
            } else { // Opponent's wires
                wireColor = new Color(OPPONENT_COLOR_PRIMARY.getRed(), OPPONENT_COLOR_PRIMARY.getGreen(), OPPONENT_COLOR_PRIMARY.getBlue(), 120);
                strokeWidth = 1.5f;
            }
        } else { // Offline mode
            wireColor = isDamaged ? Color.ORANGE : Color.GRAY.brighter();
        }

        if (isBroken) {
            wireColor = Color.RED.darker();
            strokeWidth = 3.0f;
        }

        if (wire.isCollidingWithSystem()) {
            wireColor = INVALID_WIRING_COLOR;
        }

        Stroke stroke = new BasicStroke(isBroken ? 3.0f : strokeWidth, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, isBroken ? new float[]{4f, 8f} : null, 0.0f);
        g2d.setStroke(stroke);
        g2d.setColor(wireColor);

        for (int i = 0; i < path.size() - 1; i++) {
            Point2D.Double p1 = path.get(i);
            Point2D.Double p2 = path.get(i + 1);
            g2d.drawLine((int)p1.x, (int)p1.y, (int)p2.x, (int)p2.y);
        }

        g2d.setStroke(new BasicStroke(1));
        for (Point2D.Double relayPos : wire.getRelayPoints()) {
            int rX = (int) relayPos.x - 4;
            int rY = (int) relayPos.y - 4;
            g2d.setColor(Color.WHITE);
            g2d.fillRect(rX, rY, 8, 8);
            g2d.setColor(wireColor.darker());
            g2d.drawRect(rX, rY, 8, 8);
        }
    }

    private void drawSinglePlayerHUD(Graphics2D g2d, GameStateDTO state) {
        int hudX = 15, startY = 30, lineHeight = 20, hudWidth = 250;
        g2d.setColor(HUD_BACKGROUND_COLOR);
        g2d.fillRoundRect(hudX - 10, startY - 20, hudWidth, 160, 15, 15);
        int currentY = startY;
        g2d.setFont(HUD_FONT_BOLD.deriveFont(14f));
        g2d.setColor(Color.LIGHT_GRAY);
        g2d.drawString("LEVEL: " + state.getCurrentLevel(), hudX, currentY); currentY += lineHeight;
        g2d.setColor(Color.YELLOW);
        g2d.drawString("COINS: " + state.getMyCoins(), hudX, currentY); currentY += lineHeight;
        g2d.setColor(Color.CYAN);
        g2d.drawString("WIRE LEFT: " + state.getRemainingWireLength(), hudX, currentY); currentY += lineHeight;
        g2d.setColor(Color.WHITE);
        g2d.drawString(String.format("TIME: %.2f s", state.getSimulationTimeElapsedMs() / 1000.0), hudX, currentY); currentY += lineHeight;
        g2d.setColor(Color.LIGHT_GRAY);
        g2d.drawString("Generated: " + state.getPlayer1PacketsGenerated(), hudX, currentY); currentY += lineHeight;
        g2d.setColor(Color.LIGHT_GRAY);
        g2d.drawString("Lost: " + state.getPlayer1PacketsLost(), hudX, currentY); currentY += lineHeight;
        double lossPercent = state.getMyLossPercentage();
        Color lossColor = (lossPercent >= 35.0) ? Color.RED : (lossPercent >= 15.0) ? Color.ORANGE : Color.GREEN.darker();
        g2d.setColor(lossColor);
        g2d.drawString(String.format("LOSS: %.1f%%", lossPercent), hudX, currentY);
    }

    private void drawBuildPhaseUI(Graphics2D g2d, GameStateDTO state) {
        int centerX = gamePanel.getWidth() / 2;
        int topY = 40;
        long timeMs = state.getBuildTimeRemainingMs();
        boolean isOvertime = timeMs < 0;
        timeMs = Math.abs(timeMs);
        long seconds = (timeMs / 1000) % 60;
        long minutes = (timeMs / (1000 * 60));
        String timeString = String.format("%02d:%02d", minutes, seconds);
        String title = isOvertime ? "OVERTIME" : "BUILD PHASE";
        g2d.setFont(BUILD_PHASE_TIMER_FONT);
        FontMetrics fm = g2d.getFontMetrics();
        int timeWidth = fm.stringWidth(timeString);
        g2d.setColor(isOvertime ? OPPONENT_COLOR_PRIMARY : PLAYER_COLOR_PRIMARY);
        g2d.drawString(timeString, centerX - timeWidth / 2, topY + fm.getAscent());
        g2d.setFont(BUILD_PHASE_SUBTEXT_FONT);
        fm = g2d.getFontMetrics();
        int titleWidth = fm.stringWidth(title);
        g2d.setColor(Color.LIGHT_GRAY);
        g2d.drawString(title, centerX - titleWidth / 2, topY - 5);
        String myStatus = state.isLocalPlayerReady() ? "YOU: READY" : "YOU: BUILDING";
        String oppStatus = state.isOpponentReady() ? "OPPONENT: READY" : "OPPONENT: BUILDING";
        g2d.setColor(state.isLocalPlayerReady() ? Color.GREEN : Color.ORANGE);
        g2d.drawString(myStatus, 30, topY + fm.getAscent() / 2);
        g2d.setColor(state.isOpponentReady() ? Color.GREEN : Color.ORANGE);
        int oppStatusWidth = fm.stringWidth(oppStatus);
        g2d.drawString(oppStatus, gamePanel.getWidth() - oppStatusWidth - 30, topY + fm.getAscent() / 2);
    }

    private void drawMultiplayerHUD(Graphics2D g2d, GameStateDTO state) {
        int hudWidth = 220;
        int hudHeight = 100;
        int margin = 15;
        int yPos = margin;

        int playerX = margin;
        drawPlayerInfoBox(g2d, "YOU", state.getLocalPlayerId(), state, playerX, yPos, hudWidth, hudHeight, PLAYER_COLOR_PRIMARY);

        int opponentX = gamePanel.getWidth() - hudWidth - margin;
        drawPlayerInfoBox(g2d, "OPPONENT", state.getLocalPlayerId() == 1 ? 2 : 1, state, opponentX, yPos, hudWidth, hudHeight, OPPONENT_COLOR_PRIMARY);

        g2d.setFont(HUD_FONT_BOLD);
        String timeText = String.format("TIME: %.2f s", state.getSimulationTimeElapsedMs() / 1000.0);
        FontMetrics fm = g2d.getFontMetrics();
        int timeWidth = fm.stringWidth(timeText);
        g2d.setColor(Color.WHITE);
        g2d.drawString(timeText, (gamePanel.getWidth() - timeWidth) / 2, yPos + fm.getAscent());
    }

    private void drawPlayerInfoBox(Graphics2D g2d, String title, int playerId, GameStateDTO state, int x, int y, int w, int h, Color titleColor) {
        g2d.setColor(HUD_BACKGROUND_COLOR);
        g2d.fillRoundRect(x, y, w, h, 15, 15);
        g2d.setColor(titleColor);
        g2d.setStroke(new BasicStroke(2));
        g2d.drawRoundRect(x, y, w, h, 15, 15);

        int textX = x + 15;
        int currentY = y + 25;
        int lineHeight = 22;

        g2d.setFont(HUD_TITLE_FONT);
        g2d.setColor(titleColor);
        g2d.drawString(title, textX, currentY);
        currentY += lineHeight;

        g2d.setFont(HUD_FONT_BOLD);

        int coins = (playerId == 1) ? state.getPlayer1Coins() : state.getPlayer2Coins();
        double loss = (playerId == 1) ? state.getPlayer1LossPercentage() : state.getPlayer2LossPercentage();

        g2d.setColor(Color.YELLOW);
        g2d.drawString("COINS: " + coins, textX, currentY);
        currentY += lineHeight;

        Color lossColor = (loss >= 35.0) ? OPPONENT_COLOR_PRIMARY : (loss >= 15.0) ? Color.ORANGE : PLAYER_COLOR_PRIMARY;
        g2d.setColor(lossColor);
        g2d.drawString(String.format("LOSS: %.1f%%", loss), textX, currentY);
    }

    private void drawTemporaryMessage(Graphics2D g2d) {
        NetworkGame.TemporaryMessage msg = gamePanel.getGame().getTemporaryMessage();
        if (msg != null) {
            long currentTime = java.lang.System.currentTimeMillis();
            long timeLeft = msg.displayUntilTimestamp - currentTime;
            if (timeLeft <= 0) return;
            float alphaFactor = (timeLeft < 500) ? (float)timeLeft / 500.0f : 1.0f;

            g2d.setFont(TEMP_MESSAGE_FONT);
            FontMetrics fm = g2d.getFontMetrics();
            int textWidth = fm.stringWidth(msg.message);
            int x = (gamePanel.getWidth() - textWidth) / 2;
            int y = gamePanel.getHeight() - 40;

            g2d.setColor(new Color(0, 0, 0, (int)(150 * alphaFactor)));
            g2d.fillRoundRect(x - 10, y - fm.getAscent() - 2, textWidth + 20, fm.getHeight() + 4, 10, 10);
            g2d.setColor(new Color(msg.color.getRed(), msg.color.getGreen(), msg.color.getBlue(), (int)(255 * alphaFactor)));
            g2d.drawString(msg.message, x, y);
        }
    }

    private void setupHighQualityRendering(Graphics2D g2d) {
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    }

    private void drawGrid(Graphics2D g2d) {
        g2d.setColor(GRID_COLOR);
        int width = gamePanel.getWidth();
        int height = gamePanel.getHeight();
        for (int x = 0; x <= width; x += GRID_SIZE) g2d.drawLine(x, 0, x, height);
        for (int y = 0; y <= height; y += GRID_SIZE) g2d.drawLine(0, y, width, y);
    }

    private void drawPauseOverlay(Graphics2D g2d, GameStateDTO state) {
        Composite originalComposite = g2d.getComposite();
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.7f));
        g2d.setColor(Color.BLACK);
        g2d.fillRect(0, 0, gamePanel.getWidth(), gamePanel.getHeight());
        g2d.setComposite(originalComposite);

        g2d.setFont(PAUSE_OVERLAY_FONT_LARGE);
        FontMetrics fmLarge = g2d.getFontMetrics();
        String text = "PAUSED";
        int x = (gamePanel.getWidth() - fmLarge.stringWidth(text)) / 2;
        int y = gamePanel.getHeight() / 2;
        g2d.setColor(Color.YELLOW);
        g2d.drawString(text, x, y);

        if (state.isOpponentInStore()) {
            g2d.setFont(PAUSE_OVERLAY_FONT_SMALL);
            FontMetrics fmSmall = g2d.getFontMetrics();
            String subText = "Opponent is in the store...";
            int subX = (gamePanel.getWidth() - fmSmall.stringWidth(subText)) / 2;
            int subY = y + fmLarge.getAscent();
            g2d.setColor(Color.WHITE);
            g2d.drawString(subText, subX, subY);
        }
    }

    private void drawEndGameOverlay(Graphics2D g2d, String message, Color color) {
        Composite originalComposite = g2d.getComposite();
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.8f));
        g2d.setColor(Color.BLACK);
        g2d.fillRect(0, 0, gamePanel.getWidth(), gamePanel.getHeight());
        g2d.setComposite(originalComposite);
        g2d.setFont(END_GAME_OVERLAY_FONT_LARGE);
        FontMetrics fm = g2d.getFontMetrics();
        int x = (gamePanel.getWidth() - fm.stringWidth(message)) / 2;
        int y = gamePanel.getHeight() / 2 - 20;
        g2d.setColor(color);
        g2d.drawString(message, x, y);
    }
}