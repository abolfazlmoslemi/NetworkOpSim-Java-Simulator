// ===== File: GameRenderer.java (Final Corrected Version with HUD/Pause Improvements) =====

package com.networkopsim.game.view.rendering;

import com.networkopsim.game.controller.core.NetworkGame;
import com.networkopsim.game.controller.logic.GameEngine;
import com.networkopsim.game.model.core.Packet;
import com.networkopsim.game.model.core.Port;
import com.networkopsim.game.model.core.System;
import com.networkopsim.game.model.core.Wire;
import com.networkopsim.game.model.enums.NetworkEnums;
import com.networkopsim.game.model.state.GameState;
import com.networkopsim.game.model.state.PacketSnapshot;
import com.networkopsim.game.model.state.PredictedPacketStatus;
import com.networkopsim.game.utils.KeyBindings;
import com.networkopsim.game.view.panels.GamePanel;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

public class GameRenderer {
    private final GamePanel gamePanel;
    private GameState gameState;
    private final GameEngine gameEngine;
    private final KeyBindings keyBindings;

    // --- Constants (Unchanged) ---
    private static final Stroke WIRING_LINE_STROKE = new BasicStroke(2.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, new float[]{9f, 5f}, 0.0f);
    private static final Font HUD_FONT_BOLD = new Font("Consolas", Font.BOLD, 14);
    private static final Font HUD_FONT_PLAIN = new Font("Consolas", Font.PLAIN, 11);
    private static final Font HUD_COUNT_FONT = new Font("Consolas", Font.PLAIN, 12);
    private static final Font HUD_TOGGLE_FONT = new Font("Consolas", Font.PLAIN, 10);
    private static final Font PAUSE_OVERLAY_FONT_LARGE = new Font("Arial", Font.BOLD, 60);
    private static final Font PAUSE_OVERLAY_FONT_SMALL = new Font("Arial", Font.PLAIN, 18);
    private static final Font END_GAME_OVERLAY_FONT_LARGE = new Font("Arial", Font.BOLD, 72);
    private static final Font END_GAME_OVERLAY_FONT_SMALL = new Font("Arial", Font.PLAIN, 16);
    private static final Font TIME_SCRUB_FONT = new Font("Consolas", Font.BOLD, 14);
    private static final Font PREDICTION_STATUS_FONT = new Font("Arial", Font.BOLD, 9);
    private static final Font PREDICTION_NOISE_FONT = new Font("Arial", Font.PLAIN, 8);
    private static final Font TEMP_MESSAGE_FONT = new Font("Arial", Font.BOLD, 16);
    private static final Color GRID_COLOR = new Color(40, 40, 50);
    private static final Color HUD_BACKGROUND_COLOR = new Color(20, 20, 30, 210);
    private static final Color GAME_OVER_COLOR = Color.RED.darker();
    private static final Color LEVEL_COMPLETE_COLOR = Color.GREEN.darker();
    private static final Color TIME_SCRUB_BG_COLOR = new Color(50, 50, 60, 200);
    private static final Color TIME_SCRUB_FG_COLOR = Color.WHITE;
    private static final Color TIME_SCRUB_BAR_COLOR = Color.CYAN;
    private static final int PREDICTION_ALPHA_ON_WIRE = 180;
    private static final int PREDICTION_ALPHA_QUEUED = 150;
    private static final int PREDICTION_ALPHA_STALLED = 120;
    private static final int PREDICTION_ALPHA_DELIVERED = 160;
    private static final Color PREDICTION_COLOR_STALLED_BASE = Color.ORANGE.darker();
    private static final Color PREDICTION_COLOR_QUEUED_BASE = Color.MAGENTA.darker();
    private static final Color PREDICTION_COLOR_DELIVERED_BASE = Color.GREEN.darker();
    private static final Color PREDICTION_INDICATOR_COLOR = new Color(230, 230, 230, 220);
    private static final Color PREDICTION_NOISE_TEXT_COLOR = Color.WHITE;
    private static final Color PREDICTION_IDEAL_POS_MARKER_COLOR = new Color(255,255,255, 50);
    private static final int PREDICTION_IDEAL_POS_MARKER_SIZE = 3;
    private static final int GRID_SIZE = 25;
    private static final int TIME_SCRUB_HEIGHT = 30;
    private static final int TIME_SCRUB_PADDING = 10;
    private static final Color HUD_LEVEL_COLOR = Color.LIGHT_GRAY;
    private static final Color HUD_COINS_COLOR = Color.YELLOW;
    private static final Color HUD_WIRE_COLOR = Color.CYAN;
    private static final Color HUD_TIME_COLOR = Color.WHITE;
    private static final Color HUD_COUNT_COLOR = Color.LIGHT_GRAY;
    private static final Color HUD_POWERUP_COLOR = Color.ORANGE;
    private static final Color HUD_HINT_COLOR = Color.GRAY;
    private static final Color HUD_LOSS_OK_COLOR = Color.GREEN.darker();
    private static final Color HUD_LOSS_WARN_COLOR = Color.ORANGE;
    private static final Color HUD_LOSS_DANGER_COLOR = Color.RED;
    private static final Color AERGIA_EFFECT_COLOR = new Color(100, 100, 255, 80);
    private static final Color ELIPHAS_EFFECT_COLOR = new Color(255, 150, 50, 80);
    private static final Color SISYPHUS_RADIUS_COLOR = new Color(200, 100, 255, 60);
    private static final Color SISYPHUS_INVALID_OVERLAY_COLOR = new Color(255, 0, 0, 100);
    private static final Font NOISE_FONT = new Font("Arial", Font.PLAIN, 9);
    private static final Color NOISE_TEXT_COLOR = Color.WHITE;
    private static final Color IDEAL_POSITION_MARKER_COLOR = new Color(255, 255, 255, 60);
    private static final int IDEAL_POSITION_MARKER_SIZE = 4;

    public GameRenderer(GamePanel gamePanel, GameEngine gameEngine, GameState gameState) {
        this.gamePanel = gamePanel;
        this.gameEngine = gameEngine;
        this.gameState = gameState;
        this.keyBindings = gamePanel.getGame().getKeyBindings();
    }

    public void setGameState(GameState newGameState) {
        this.gameState = newGameState;
    }

    public void render(Graphics g) {
        Graphics2D g2d = (Graphics2D) g.create();
        try {
            setupHighQualityRendering(g2d);
            drawGrid(g2d);

            boolean isBudgetExceeded = gameState.getRemainingWireLength() < 0;
            List<System> systemsSnapshot = gameEngine.getSystems();

            for (Wire w : gameEngine.getWires()) {
                if (w != null) {
                    boolean isWireVisuallyInvalid = isBudgetExceeded || gamePanel.isWireIntersectingAnySystem(w, systemsSnapshot);
                    w.draw(g2d, !isWireVisuallyInvalid);
                }
            }

            for (System s : systemsSnapshot) {
                s.setIndicator(s.areAllPortsConnected());
            }
            SystemDrawer.drawSystems(g2d, systemsSnapshot);

            if (gamePanel.getCurrentInteractiveMode() == GamePanel.InteractiveMode.SISYPHUS_DRAG && gamePanel.getSisyphusDraggedSystem() != null) {
                drawSisyphusUI(g2d);
            }

            drawActiveEffects(g2d);

            if (gamePanel.isWireDrawingMode() && gamePanel.getSelectedOutputPort() != null && gamePanel.getMouseDragPos() != null) {
                drawWiringLine(g2d);
            }

            if (gamePanel.isSimulationStarted()) {
                for (Packet p : gameEngine.getPacketsForRendering()) {
                    drawPacket(g2d, p);
                }
            } else {
                if (gamePanel.isNetworkValidatedForPrediction()) {
                    for (PacketSnapshot snapshot : gamePanel.getPredictedPacketStates()) {
                        if (snapshot.getStatus() != PredictedPacketStatus.LOST) {
                            drawPredictedPacket(g2d, snapshot);
                        }
                    }
                }
                drawTimeScrubberUI(g2d);
            }

            if (gamePanel.isShowHUD()) {
                drawHUD(g2d);
            } else {
                drawHudToggleHint(g2d);
            }

            drawTemporaryMessage(g2d);

            if (gamePanel.isGamePaused()) {
                drawPauseOverlay(g2d); // MODIFIED: No longer needs a parameter
            } else if (gamePanel.isGameOver()) {
                drawEndGameOverlay(g2d, "GAME OVER", GAME_OVER_COLOR);
            } else if (gamePanel.isLevelComplete()) {
                drawEndGameOverlay(g2d, "LEVEL COMPLETE", LEVEL_COMPLETE_COLOR);
            }
        } finally {
            g2d.dispose();
        }
    }

    private void drawPacket(Graphics2D g2d, Packet packet) {
        if (packet.isMarkedForRemoval() || packet.getCurrentSystem() != null || packet.getIdealPosition() == null) return;
        Point2D.Double idealPosition = packet.getIdealPosition();
        Point2D.Double visualPosition = packet.getVisualPosition();
        if (visualPosition == null) return;

        if (packet.getCurrentWire() != null && packet.getNoise() > 0.05) {
            g2d.setColor(IDEAL_POSITION_MARKER_COLOR);
            int markerHalf = IDEAL_POSITION_MARKER_SIZE / 2;
            int idealXInt = (int)Math.round(idealPosition.x);
            int idealYInt = (int)Math.round(idealPosition.y);
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
            PacketDrawer.drawPacketBody(g2d, packet, -halfSize, -halfSize, drawSize, halfSize);
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

    private void setupHighQualityRendering(Graphics2D g2d) {
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    }

    private void drawGrid(Graphics2D g2d) {
        g2d.setColor(GRID_COLOR);
        int width = gamePanel.getWidth();
        int height = gamePanel.getHeight();
        for (int x = 0; x <= width; x += GRID_SIZE) { g2d.drawLine(x, 0, x, height); }
        for (int y = 0; y <= height; y += GRID_SIZE) { g2d.drawLine(0, y, width, y); }
    }

    private void drawWiringLine(Graphics2D g2d) {
        Port startPort = gamePanel.getSelectedOutputPort();
        Point dragPos = gamePanel.getMouseDragPos();
        Color wiringColor = gamePanel.getCurrentWiringColor();
        if (startPort == null || startPort.getPosition() == null || dragPos == null) return;
        g2d.setColor(wiringColor);
        Stroke oldStroke = g2d.getStroke();
        g2d.setStroke(WIRING_LINE_STROKE);
        g2d.drawLine(startPort.getX(), startPort.getY(), dragPos.x, dragPos.y);
        g2d.setStroke(oldStroke);
    }

    private void drawHUD(Graphics2D g2d) {
        int hudX = 15, startY = 30, lineHeightBold = 20, lineHeightPlain = 18, hudWidth = 250;
        java.util.List<String> lines = new ArrayList<>(); java.util.List<Color> lineColors = new ArrayList<>(); java.util.List<Font> lineFonts = new ArrayList<>(); java.util.List<Integer> lineHeights = new ArrayList<>();
        lines.add("LEVEL: " + gamePanel.getCurrentLevel()); lineColors.add(HUD_LEVEL_COLOR); lineFonts.add(HUD_FONT_BOLD); lineHeights.add(lineHeightBold);
        lines.add("COINS: " + gameState.getCoins()); lineColors.add(HUD_COINS_COLOR); lineFonts.add(HUD_FONT_BOLD); lineHeights.add(lineHeightBold);
        lines.add("WIRE LEFT: " + gameState.getRemainingWireLength()); lineColors.add(HUD_WIRE_COLOR); lineFonts.add(HUD_FONT_BOLD); lineHeights.add(lineHeightBold);
        int generatedCount, lostCount; double lossPercent;
        if (gamePanel.isSimulationStarted()) {
            generatedCount = gameState.getTotalPacketsGeneratedCount(); lostCount = gameState.getTotalPacketsLostCount(); lossPercent = gameState.getPacketLossPercentage();
            lines.add(String.format("TIME: %.2f s", gameEngine.getSimulationTimeElapsedMs() / 1000.0)); lineColors.add(HUD_TIME_COLOR); lineFonts.add(HUD_FONT_BOLD); lineHeights.add(lineHeightBold);
            String powerupText = ""; if (gamePanel.isAtarActive()) powerupText += " Atar"; if (gamePanel.isAiryamanActive()) powerupText += " Airyaman"; if (gamePanel.isSpeedLimiterActive()) powerupText += " SpeedLimit"; if (!powerupText.isEmpty()) { lines.add("ACTIVE:" + powerupText); lineColors.add(HUD_POWERUP_COLOR); lineFonts.add(HUD_FONT_BOLD); lineHeights.add(lineHeightBold); }

            // NEW: Add display for current interactive mode
            GamePanel.InteractiveMode currentMode = gamePanel.getCurrentInteractiveMode();
            if (currentMode != GamePanel.InteractiveMode.NONE) {
                String modeText = "MODE: ";
                switch (currentMode) {
                    case AERGIA_PLACEMENT: modeText += "Place Aergia Scroll"; break;
                    case ELIPHAS_PLACEMENT: modeText += "Place Eliphas Scroll"; break;
                    case SISYPHUS_DRAG: modeText += "Move System (Sisyphus)"; break;
                }
                lines.add(modeText); lineColors.add(HUD_POWERUP_COLOR); lineFonts.add(HUD_FONT_BOLD); lineHeights.add(lineHeightBold);
            }

            if (gamePanel.isAergiaOnCooldown()) { long cdLeft = gamePanel.getAergiaCooldownTimeRemaining(); lines.add(String.format("AERGIA CD: %.1fs", cdLeft / 1000.0)); lineColors.add(Color.LIGHT_GRAY); lineFonts.add(HUD_FONT_PLAIN); lineHeights.add(lineHeightPlain); }
        } else {
            GamePanel.PredictionRunStats predStats = gamePanel.getDisplayedPredictionStats(); generatedCount = predStats.totalPacketsGenerated; lostCount = predStats.totalPacketsLost; lossPercent = predStats.packetLossPercentage;
            lines.add(String.format("PRED. TIME: %.2f s", gamePanel.getViewedTimeMs() / 1000.0)); lineColors.add(HUD_TIME_COLOR); lineFonts.add(HUD_FONT_BOLD); lineHeights.add(lineHeightBold);
        }
        lines.add("Generated: " + generatedCount); lineColors.add(HUD_COUNT_COLOR); lineFonts.add(HUD_COUNT_FONT); lineHeights.add(lineHeightPlain);
        lines.add("Lost: " + lostCount); lineColors.add(HUD_COUNT_COLOR); lineFonts.add(HUD_COUNT_FONT); lineHeights.add(lineHeightPlain);
        Color lossColor; if (!gamePanel.isSimulationStarted() && !gamePanel.isNetworkValidatedForPrediction()) { lossColor = Color.DARK_GRAY; } else if (lossPercent >= 35.0) { lossColor = HUD_LOSS_DANGER_COLOR; } else if (lossPercent >= 15.0) { lossColor = HUD_LOSS_WARN_COLOR; } else { lossColor = HUD_LOSS_OK_COLOR; }
        lines.add(String.format("LOSS (Units): %.1f%%", lossPercent)); lineColors.add(lossColor); lineFonts.add(HUD_FONT_BOLD); lineHeights.add(lineHeightBold);
        String hintText = ""; String keyToggleHUD = keyBindings.getKeyText(keyBindings.getKeyCode(KeyBindings.GameAction.TOGGLE_HUD)); String keyEsc = keyBindings.getKeyText(keyBindings.getKeyCode(KeyBindings.GameAction.ESCAPE_MENU_CANCEL));
        if (!gamePanel.isGameOver() && !gamePanel.isLevelComplete()) { if (!gamePanel.isSimulationStarted()) { String keyScrubLeft = keyBindings.getKeyText(keyBindings.getKeyCode(KeyBindings.GameAction.DECREMENT_VIEWED_TIME)); String keyScrubRight = keyBindings.getKeyText(keyBindings.getKeyCode(KeyBindings.GameAction.INCREMENT_VIEWED_TIME)); String keyStartSim = keyBindings.getKeyText(keyBindings.getKeyCode(KeyBindings.GameAction.START_SIMULATION_SCRUB_MODE)); hintText = String.format("%s/%s: Scrub | %s: Start", keyScrubLeft, keyScrubRight, keyStartSim); } else if (gamePanel.isGameRunning() && !gamePanel.isGamePaused()) { String keyPause = keyBindings.getKeyText(keyBindings.getKeyCode(KeyBindings.GameAction.PAUSE_RESUME_GAME)); String keyStore = keyBindings.getKeyText(keyBindings.getKeyCode(KeyBindings.GameAction.OPEN_STORE)); hintText = String.format("%s: Pause | %s: Store | %s: Menu", keyPause, keyStore, keyEsc); } if (!hintText.isEmpty()) { hintText += " | " + keyToggleHUD + ": HUD"; } else if(gamePanel.isSimulationStarted() && !gamePanel.isGamePaused()) { hintText = keyToggleHUD + ": HUD"; } if(!hintText.isEmpty()){ lines.add(hintText); lineColors.add(HUD_HINT_COLOR); lineFonts.add(HUD_FONT_PLAIN); lineHeights.add(lineHeightPlain); } }
        int totalHeightAccumulated = 0; for (int height : lineHeights) { totalHeightAccumulated += height; } int hudHeight = 20 + totalHeightAccumulated; g2d.setColor(HUD_BACKGROUND_COLOR); g2d.fillRoundRect(hudX - 10, startY - 20, hudWidth, hudHeight, 15, 15);
        int currentY = startY; for (int i = 0; i < lines.size(); i++) { g2d.setColor(lineColors.get(i)); g2d.setFont(lineFonts.get(i)); g2d.drawString(lines.get(i), hudX, currentY); currentY += lineHeights.get(i); }
    }

    private void drawHudToggleHint(Graphics2D g2d) { g2d.setColor(Color.DARK_GRAY); g2d.setFont(HUD_TOGGLE_FONT); String keyToggleHUD = keyBindings.getKeyText(keyBindings.getKeyCode(KeyBindings.GameAction.TOGGLE_HUD)); String text = keyToggleHUD + ": Toggle HUD"; FontMetrics fm = g2d.getFontMetrics(); int y = gamePanel.getHeight() - fm.getDescent() - 5; g2d.drawString(text, 10, y); }

    // MODIFIED: This method now dynamically generates the instruction text
    private void drawPauseOverlay(Graphics2D g2d) {
        Composite originalComposite = g2d.getComposite();
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.7f));
        g2d.setColor(Color.BLACK);
        g2d.fillRect(0, 0, gamePanel.getWidth(), gamePanel.getHeight());
        g2d.setComposite(originalComposite);

        String text = "PAUSED";
        g2d.setFont(PAUSE_OVERLAY_FONT_LARGE);
        FontMetrics fm = g2d.getFontMetrics();
        int textWidth = fm.stringWidth(text);
        int x = (gamePanel.getWidth() - textWidth) / 2;
        int y = gamePanel.getHeight() / 2;
        g2d.setColor(Color.DARK_GRAY);
        g2d.drawString(text, x + 3, y + 3);
        g2d.setColor(Color.YELLOW);
        g2d.drawString(text, x, y);

        // NEW DYNAMIC LOGIC FOR INSTRUCTION TEXT
        String instructionText;
        GamePanel.InteractiveMode mode = gamePanel.getCurrentInteractiveMode();
        if (mode != GamePanel.InteractiveMode.NONE) {
            switch(mode) {
                case AERGIA_PLACEMENT: instructionText = "Placing Scroll of Aergia..."; break;
                case SISYPHUS_DRAG: instructionText = "Moving System with Scroll of Sisyphus..."; break;
                case ELIPHAS_PLACEMENT: instructionText = "Placing Scroll of Eliphas..."; break;
                default: instructionText = "PAUSED"; break; // Fallback
            }
        } else {
            instructionText = String.format("%s: Resume | %s: Menu",
                    keyBindings.getKeyText(keyBindings.getKeyCode(KeyBindings.GameAction.PAUSE_RESUME_GAME)),
                    keyBindings.getKeyText(keyBindings.getKeyCode(KeyBindings.GameAction.ESCAPE_MENU_CANCEL)));
        }

        g2d.setFont(PAUSE_OVERLAY_FONT_SMALL);
        fm = g2d.getFontMetrics();
        int instructionWidth = fm.stringWidth(instructionText);
        int ix = (gamePanel.getWidth() - instructionWidth) / 2;
        int iy = y + fm.getAscent() + 10;
        g2d.setColor(Color.BLACK);
        g2d.drawString(instructionText, ix + 1, iy + 1);
        g2d.setColor(Color.LIGHT_GRAY);
        g2d.drawString(instructionText, ix, iy);
    }

    private void drawEndGameOverlay(Graphics2D g2d, String message, Color color) { Composite originalComposite = g2d.getComposite(); g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.8f)); g2d.setColor(Color.BLACK); g2d.fillRect(0, 0, gamePanel.getWidth(), gamePanel.getHeight()); g2d.setComposite(originalComposite); g2d.setFont(END_GAME_OVERLAY_FONT_LARGE); FontMetrics fm = g2d.getFontMetrics(); int textWidth = fm.stringWidth(message); int x = (gamePanel.getWidth() - textWidth) / 2; int y = gamePanel.getHeight() / 2 - 20; g2d.setColor(Color.BLACK); g2d.drawString(message, x + 4, y + 4); g2d.setColor(color); g2d.drawString(message, x, y); g2d.setFont(END_GAME_OVERLAY_FONT_SMALL); g2d.setColor(Color.LIGHT_GRAY); String subText = "(Results dialog will appear shortly)"; fm = g2d.getFontMetrics(); int subWidth = fm.stringWidth(subText); int sx = (gamePanel.getWidth() - subWidth) / 2; int sy = y + fm.getAscent() + 15; g2d.drawString(subText, sx, sy); }
    private void drawTimeScrubberUI(Graphics2D g2d) { int panelWidth = gamePanel.getWidth(); int panelHeight = gamePanel.getHeight(); int scrubY = panelHeight - TIME_SCRUB_HEIGHT - TIME_SCRUB_PADDING; int scrubX = TIME_SCRUB_PADDING; int scrubWidth = panelWidth - 2 * TIME_SCRUB_PADDING; g2d.setColor(TIME_SCRUB_BG_COLOR); g2d.fillRoundRect(scrubX, scrubY, scrubWidth, TIME_SCRUB_HEIGHT, 10, 10); g2d.setColor(TIME_SCRUB_BG_COLOR.darker()); g2d.drawRoundRect(scrubX, scrubY, scrubWidth, TIME_SCRUB_HEIGHT, 10, 10); long currentTime = gamePanel.getViewedTimeMs(); long maxTime = gamePanel.getCurrentLevelTimeLimitMs(); double progress = (maxTime > 0) ? (double) currentTime / maxTime : 0.0; progress = Math.max(0.0, Math.min(1.0, progress)); int progressBarInnerX = scrubX + 2; int progressBarInnerWidth = scrubWidth - 4; int progressFillWidth = (int) (progress * progressBarInnerWidth); if (progressFillWidth > 0) { g2d.setColor(TIME_SCRUB_BAR_COLOR); g2d.fillRect(progressBarInnerX, scrubY + 2, progressFillWidth, TIME_SCRUB_HEIGHT - 4); } g2d.setFont(TIME_SCRUB_FONT); g2d.setColor(TIME_SCRUB_FG_COLOR); String timeString = String.format("View Time: %.2f s / %.2f s", currentTime / 1000.0, maxTime / 1000.0); FontMetrics fm = g2d.getFontMetrics(); int textWidth = fm.stringWidth(timeString); int textX = scrubX + (scrubWidth - textWidth) / 2; int textY = scrubY + fm.getAscent() + (TIME_SCRUB_HEIGHT - fm.getHeight()) / 2; g2d.drawString(timeString, textX, textY); }
    private void drawPredictedPacket(Graphics2D g2d, PacketSnapshot snapshot) { if (snapshot.getStatus() == PredictedPacketStatus.NOT_YET_GENERATED || snapshot.getVisualPosition() == null || snapshot.getStatus() == PredictedPacketStatus.LOST) return; Point visualPosPoint = snapshot.getVisualPosition(); Point2D.Double idealPosDouble = snapshot.getIdealPosition(); int drawSize = snapshot.getDrawSize(); int halfSize = drawSize / 2; int drawX = visualPosPoint.x - halfSize; int drawY = visualPosPoint.y - halfSize; Color baseColor = Port.getColorFromShape(snapshot.getShape()); int alpha = PREDICTION_ALPHA_ON_WIRE; String statusIndicator = null; switch (snapshot.getStatus()) { case STALLED_AT_NODE: alpha = PREDICTION_ALPHA_STALLED; baseColor = PREDICTION_COLOR_STALLED_BASE; statusIndicator = "S"; break; case QUEUED: alpha = PREDICTION_ALPHA_QUEUED; baseColor = PREDICTION_COLOR_QUEUED_BASE; statusIndicator = "Q"; break; case DELIVERED: alpha = PREDICTION_ALPHA_DELIVERED; baseColor = PREDICTION_COLOR_DELIVERED_BASE; statusIndicator = "D"; break; case ON_WIRE: break; } Color drawColor = new Color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(), alpha); g2d.setColor(drawColor); Path2D path = null; AffineTransform oldTransform = g2d.getTransform(); boolean transformed = false; try { if (snapshot.getStatus() == PredictedPacketStatus.ON_WIRE && snapshot.getNoiseLevel() > 0.05 && idealPosDouble != null) { g2d.setColor(PREDICTION_IDEAL_POS_MARKER_COLOR); int markerHalf = PREDICTION_IDEAL_POS_MARKER_SIZE / 2; int idealXInt = (int)Math.round(idealPosDouble.x); int idealYInt = (int)Math.round(idealPosDouble.y); g2d.drawLine(idealXInt - markerHalf, idealYInt, idealXInt + markerHalf, idealYInt); g2d.drawLine(idealXInt, idealYInt - markerHalf, idealXInt, idealYInt + markerHalf); g2d.setColor(drawColor); } Point2D.Double rotDir = snapshot.getRotationDirection(); if (snapshot.getShape() == NetworkEnums.PacketShape.TRIANGLE && rotDir != null) { g2d.translate(visualPosPoint.x, visualPosPoint.y); g2d.rotate(Math.atan2(rotDir.y, rotDir.x)); path = new Path2D.Double(); path.moveTo(halfSize, 0); path.lineTo(-halfSize, -halfSize); path.lineTo(-halfSize, halfSize); path.closePath(); g2d.fill(path); transformed = true; } else { switch (snapshot.getShape()) { case SQUARE: g2d.fillRect(drawX, drawY, drawSize, drawSize); break; case CIRCLE: g2d.fillOval(drawX, drawY, drawSize, drawSize); break; case TRIANGLE: path = new Path2D.Double(); path.moveTo(visualPosPoint.x + halfSize, visualPosPoint.y); path.lineTo(visualPosPoint.x - halfSize, visualPosPoint.y - halfSize); path.lineTo(visualPosPoint.x - halfSize, visualPosPoint.y + halfSize); path.closePath(); g2d.fill(path); break; } } g2d.setColor(drawColor.darker()); g2d.setStroke(new BasicStroke(1)); if (transformed && path != null) { g2d.draw(path); } else { switch (snapshot.getShape()) { case SQUARE: g2d.drawRect(drawX, drawY, drawSize, drawSize); break; case CIRCLE: g2d.drawOval(drawX, drawY, drawSize, drawSize); break; case TRIANGLE: if (path != null) g2d.draw(path); break; } } Stroke defaultStroke = g2d.getStroke(); switch (snapshot.getPacketType()) { case PROTECTED: g2d.setColor(new Color(100, 255, 100, 150)); g2d.setStroke(new BasicStroke(2.0f)); if (transformed && path != null) g2d.draw(path); else g2d.drawOval(drawX-1, drawY-1, drawSize+2, drawSize+2); break; case TROJAN: g2d.setColor(new Color(255, 50, 50, 180)); g2d.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, new float[]{3f, 3f}, 0.0f)); if (transformed && path != null) g2d.draw(path); else g2d.drawOval(drawX, drawY, drawSize, drawSize); break; default: break; } g2d.setStroke(defaultStroke); if (statusIndicator != null) { g2d.setColor(PREDICTION_INDICATOR_COLOR); g2d.setFont(PREDICTION_STATUS_FONT); FontMetrics fm = g2d.getFontMetrics(); int charWidth = fm.stringWidth(statusIndicator); g2d.drawString(statusIndicator, visualPosPoint.x - charWidth / 2, visualPosPoint.y + fm.getAscent() / 3); } if (snapshot.getNoiseLevel() > 0.05) { g2d.setFont(PREDICTION_NOISE_FONT); g2d.setColor(PREDICTION_NOISE_TEXT_COLOR); String noiseText = String.format("%.1f", snapshot.getNoiseLevel()); FontMetrics fm = g2d.getFontMetrics(); g2d.drawString(noiseText, visualPosPoint.x + halfSize/2 + 2, visualPosPoint.y + halfSize + fm.getAscent() - 2 ); } } finally { g2d.setStroke(new BasicStroke(1)); if (transformed) { g2d.setTransform(oldTransform); } } }
    private void drawTemporaryMessage(Graphics2D g2d) { NetworkGame.TemporaryMessage msg = gamePanel.getGame().getTemporaryMessage(); if (msg != null) { String text = msg.message; Color color = msg.color; long displayUntil = msg.displayUntilTimestamp; float alphaFactor = 1.0f; long currentTime = java.lang.System.currentTimeMillis(); long timeLeft = displayUntil - currentTime; long fadeDuration = 500; if (timeLeft <= 0) { gamePanel.getGame().clearTemporaryMessage(); return; } else if (timeLeft < fadeDuration) { alphaFactor = (float)timeLeft / fadeDuration; } g2d.setFont(TEMP_MESSAGE_FONT); FontMetrics fm = g2d.getFontMetrics(); int textWidth = fm.stringWidth(text); int textHeight = fm.getHeight(); int panelWidth = gamePanel.getWidth(); int x = (panelWidth - textWidth) / 2; int y = 30 + fm.getAscent(); Color bgColor = new Color(0, 0, 0, (int)(150 * alphaFactor)); g2d.setColor(bgColor); g2d.fillRoundRect(x - 10, y - textHeight + fm.getDescent() - 5, textWidth + 20, textHeight + 5, 10, 10); g2d.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), (int)(255 * alphaFactor))); g2d.drawString(text, x, y); } }
    private void drawSisyphusUI(Graphics2D g2d) { System draggedSystem = gamePanel.getSisyphusDraggedSystem(); Point startPos = gamePanel.getSisyphusDragStartPos(); if (draggedSystem == null || startPos == null) return; int radius = (int) gamePanel.getSisyphusDragRadius(); g2d.setColor(SISYPHUS_RADIUS_COLOR); g2d.fillOval(startPos.x - radius, startPos.y - radius, radius * 2, radius * 2); g2d.setColor(SISYPHUS_RADIUS_COLOR.darker()); g2d.drawOval(startPos.x - radius, startPos.y - radius, radius * 2, radius * 2); if (!gamePanel.isSisyphusMoveValid()) { g2d.setColor(SISYPHUS_INVALID_OVERLAY_COLOR); g2d.fillRect(draggedSystem.getX(), draggedSystem.getY(), System.SYSTEM_WIDTH, System.SYSTEM_HEIGHT); } }
    private void drawActiveEffects(Graphics2D g2d) { List<GamePanel.ActiveWireEffect> effects = gamePanel.getActiveWireEffects(); synchronized (effects) { for (GamePanel.ActiveWireEffect effect : effects) { Point2D.Double pos = effect.position; double radius = 0; Color color = Color.BLACK; String symbol = "?"; if (effect.type == GamePanel.InteractiveMode.AERGIA_PLACEMENT) { radius = 30.0; color = AERGIA_EFFECT_COLOR; symbol = "A"; } else if (effect.type == GamePanel.InteractiveMode.ELIPHAS_PLACEMENT) { radius = 40.0; color = ELIPHAS_EFFECT_COLOR; symbol = "E"; } int diameter = (int)(radius * 2); g2d.setColor(color); g2d.fillOval((int)(pos.x - radius), (int)(pos.y - radius), diameter, diameter); g2d.setColor(color.darker()); g2d.drawOval((int)(pos.x - radius), (int)(pos.y - radius), diameter, diameter); g2d.setColor(Color.WHITE); g2d.setFont(new Font("Arial", Font.BOLD, 12)); FontMetrics fm = g2d.getFontMetrics(); int charWidth = fm.stringWidth(symbol); g2d.drawString(symbol, (int)pos.x - charWidth / 2, (int)pos.y + fm.getAscent() / 2 - 2); } } }
}