// FILE: GameRenderer.java
import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

public class GameRenderer {
    private final GamePanel gamePanel;
    private final GameState gameState;
    private final KeyBindings keyBindings;
    private static final Stroke WIRING_LINE_STROKE = new BasicStroke(2.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
            10.0f, new float[]{9f, 5f}, 0.0f);
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
    private static final Font TEMP_MESSAGE_FONT = new Font("Arial", Font.BOLD, 16);
    private static final Color GRID_COLOR = new Color(40, 40, 50);
    private static final Color HUD_BACKGROUND_COLOR = new Color(20, 20, 30, 210);
    private static final Color GAME_OVER_COLOR = Color.RED.darker();
    private static final Color LEVEL_COMPLETE_COLOR = Color.GREEN.darker();
    private static final Color TIME_SCRUB_BG_COLOR = new Color(50, 50, 60, 200);
    private static final Color TIME_SCRUB_FG_COLOR = Color.WHITE;
    private static final Color TIME_SCRUB_BAR_COLOR = Color.CYAN;
    private static final int PREDICTION_ALPHA_DEFAULT = 160;
    private static final int PREDICTION_ALPHA_STALLED = 120;
    private static final int PREDICTION_ALPHA_LOST = 100;
    private static final int PREDICTION_ALPHA_DELIVERED = 120;
    private static final Color PREDICTION_COLOR_LOST_BASE = Color.GRAY;
    private static final Color PREDICTION_COLOR_STALLED_BASE = Color.ORANGE.darker();
    private static final Color PREDICTION_COLOR_DELIVERED_BASE = Color.GREEN.darker();
    private static final Color PREDICTION_INDICATOR_COLOR = new Color(230, 230, 230, 220);
    private static final Color PREDICTION_LOST_X_COLOR = new Color(255, 0, 0, 150);
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

    public GameRenderer(GamePanel gamePanel, GameState gameState) {
        this.gamePanel = gamePanel;
        this.gameState = gameState;
        this.keyBindings = gamePanel.getGame().getKeyBindings();
    }

    public void render(Graphics g) {
        Graphics2D g2d = (Graphics2D) g.create();
        try {
            setupHighQualityRendering(g2d);
            drawGrid(g2d);
            synchronized(gamePanel.getWires()) {
                for (Wire w : gamePanel.getWires()) {
                    if (w != null) w.draw(g2d);
                }
            }
            synchronized(gamePanel.getSystems()) {
                for (System s : gamePanel.getSystems()) {
                    if (s != null) s.draw(g2d);
                }
            }
            if (gamePanel.isWireDrawingMode() && gamePanel.getSelectedOutputPort() != null && gamePanel.getMouseDragPos() != null) {
                drawWiringLine(g2d);
            }
            if (gamePanel.isSimulationStarted()) {
                synchronized(gamePanel.getPackets()) {
                    List<Packet> packetsSnapshot = new ArrayList<>(gamePanel.getPackets());
                    for (Packet p : packetsSnapshot) {
                        if (p != null && !p.isMarkedForRemoval() && p.getCurrentSystem() == null) {
                            p.draw(g2d);
                        }
                    }
                }
            } else {
                if (gamePanel.isNetworkValidatedForPrediction()) {
                    synchronized (gamePanel.getPredictedPacketStates()) {
                        List<PacketSnapshot> predictionSnapshot = new ArrayList<>(gamePanel.getPredictedPacketStates());
                        for (PacketSnapshot snapshot : predictionSnapshot) {
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

            String pauseInstruction = "";
            if (gamePanel.isGamePaused()) {
                pauseInstruction = String.format("%s: Resume | %s: Menu",
                        keyBindings.getKeyText(keyBindings.getKeyCode(KeyBindings.GameAction.PAUSE_RESUME_GAME)),
                        keyBindings.getKeyText(keyBindings.getKeyCode(KeyBindings.GameAction.ESCAPE_MENU_CANCEL)));
                drawPauseOverlay(g2d, pauseInstruction);
            }
            else if (gamePanel.isGameOver()) {
                drawEndGameOverlay(g2d, "GAME OVER", GAME_OVER_COLOR);
            } else if (gamePanel.isLevelComplete()) {
                drawEndGameOverlay(g2d, "LEVEL COMPLETE", LEVEL_COMPLETE_COLOR);
            }
        } finally {
            g2d.dispose();
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
        for (int x = 0; x <= width; x += GRID_SIZE) {
            g2d.drawLine(x, 0, x, height);
        }
        for (int y = 0; y <= height; y += GRID_SIZE) {
            g2d.drawLine(0, y, width, y);
        }
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
        int hudX = 15;
        int startY = 30;
        int lineHeightBold = 20;
        int lineHeightPlain = 18;
        int hudWidth = 250;
        List<String> lines = new ArrayList<>();
        List<Color> lineColors = new ArrayList<>();
        List<Font> lineFonts = new ArrayList<>();
        List<Integer> lineHeights = new ArrayList<>();
        lines.add("LEVEL: " + gamePanel.getCurrentLevel());
        lineColors.add(HUD_LEVEL_COLOR);
        lineFonts.add(HUD_FONT_BOLD);
        lineHeights.add(lineHeightBold);
        lines.add("COINS: " + gameState.getCoins());
        lineColors.add(HUD_COINS_COLOR);
        lineFonts.add(HUD_FONT_BOLD);
        lineHeights.add(lineHeightBold);
        lines.add("WIRE LEFT: " + gameState.getRemainingWireLength());
        lineColors.add(HUD_WIRE_COLOR);
        lineFonts.add(HUD_FONT_BOLD);
        lineHeights.add(lineHeightBold);
        int generatedCount = gameState.getTotalPacketsGeneratedCount();
        int lostCount = gameState.getTotalPacketsLostCount();
        lines.add("Generated: " + generatedCount);
        lineColors.add(HUD_COUNT_COLOR);
        lineFonts.add(HUD_COUNT_FONT);
        lineHeights.add(lineHeightPlain);
        lines.add("Lost: " + lostCount);
        lineColors.add(HUD_COUNT_COLOR);
        lineFonts.add(HUD_COUNT_FONT);
        lineHeights.add(lineHeightPlain);
        double lossPercent = gameState.getPacketLossPercentage();
        Color lossColor;
        if (!gamePanel.isSimulationStarted()) {
            lossColor = Color.GRAY;
        } else if (lossPercent >= 35) {
            lossColor = HUD_LOSS_DANGER_COLOR;
        } else if (lossPercent >= 15) {
            lossColor = HUD_LOSS_WARN_COLOR;
        } else {
            lossColor = HUD_LOSS_OK_COLOR;
        }
        lines.add(String.format("LOSS (Units): %.1f%% (%d U)", lossPercent, gameState.getTotalPacketLossUnits()));
        lineColors.add(lossColor);
        lineFonts.add(HUD_FONT_BOLD);
        lineHeights.add(lineHeightBold);
        if (gamePanel.isSimulationStarted()) {
            long simTimeMs = gamePanel.getSimulationTimeElapsedMs();
            lines.add(String.format("TIME: %.2f s", simTimeMs / 1000.0));
            lineColors.add(HUD_TIME_COLOR);
            lineFonts.add(HUD_FONT_BOLD);
            lineHeights.add(lineHeightBold);
        }
        String powerupText = "";
        if (gamePanel.isSimulationStarted() && (gamePanel.isAtarActive() || gamePanel.isAiryamanActive())) {
            powerupText = "ACTIVE:";
            if (gamePanel.isAtarActive()) powerupText += " Atar";
            if (gamePanel.isAiryamanActive()) powerupText += " Airyaman";
            lines.add(powerupText.trim());
            lineColors.add(HUD_POWERUP_COLOR);
            lineFonts.add(HUD_FONT_BOLD);
            lineHeights.add(lineHeightBold);
        }
        String hintText = "";
        String keyToggleHUD = keyBindings.getKeyText(keyBindings.getKeyCode(KeyBindings.GameAction.TOGGLE_HUD));
        String keyEsc = keyBindings.getKeyText(keyBindings.getKeyCode(KeyBindings.GameAction.ESCAPE_MENU_CANCEL));
        if (!gamePanel.isGameOver() && !gamePanel.isLevelComplete()) {
            if (!gamePanel.isSimulationStarted()) {
                String keyScrubLeft = keyBindings.getKeyText(keyBindings.getKeyCode(KeyBindings.GameAction.DECREMENT_VIEWED_TIME));
                String keyScrubRight = keyBindings.getKeyText(keyBindings.getKeyCode(KeyBindings.GameAction.INCREMENT_VIEWED_TIME));
                String keyStartSim = keyBindings.getKeyText(keyBindings.getKeyCode(KeyBindings.GameAction.START_SIMULATION_SCRUB_MODE));
                hintText = String.format("%s/%s: Scrub | %s: Start", keyScrubLeft, keyScrubRight, keyStartSim);
            } else if (gamePanel.isGameRunning() && !gamePanel.isGamePaused()) {
                String keyPause = keyBindings.getKeyText(keyBindings.getKeyCode(KeyBindings.GameAction.PAUSE_RESUME_GAME));
                String keyStore = keyBindings.getKeyText(keyBindings.getKeyCode(KeyBindings.GameAction.OPEN_STORE));
                hintText = String.format("%s: Pause | %s: Store | %s: Menu", keyPause, keyStore, keyEsc);
            } else if (gamePanel.isGamePaused()) {
            }
            if (!hintText.isEmpty()) {
                hintText += " | " + keyToggleHUD + ": HUD";
            } else {
                hintText = keyToggleHUD + ": HUD";
            }
            lines.add(hintText);
            lineColors.add(HUD_HINT_COLOR);
            lineFonts.add(HUD_FONT_PLAIN);
            lineHeights.add(lineHeightPlain);
        }
        int totalHeightAccumulated = 0;
        for (int height : lineHeights) {
            totalHeightAccumulated += height;
        }
        int hudHeight = 20 + totalHeightAccumulated;
        g2d.setColor(HUD_BACKGROUND_COLOR);
        g2d.fillRoundRect(hudX - 10, startY - 20, hudWidth, hudHeight, 15, 15);
        int currentY = startY;
        for (int i = 0; i < lines.size(); i++) {
            g2d.setColor(lineColors.get(i));
            g2d.setFont(lineFonts.get(i));
            g2d.drawString(lines.get(i), hudX, currentY);
            currentY += lineHeights.get(i);
        }
    }

    private void drawHudToggleHint(Graphics2D g2d) {
        g2d.setColor(Color.DARK_GRAY);
        g2d.setFont(HUD_TOGGLE_FONT);
        String keyToggleHUD = keyBindings.getKeyText(keyBindings.getKeyCode(KeyBindings.GameAction.TOGGLE_HUD));
        String text = keyToggleHUD + ": Toggle HUD";
        FontMetrics fm = g2d.getFontMetrics();
        int y = gamePanel.getHeight() - fm.getDescent() - 5;
        g2d.drawString(text, 10, y);
    }

    private void drawPauseOverlay(Graphics2D g2d, String instructionText) {
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

    private void drawEndGameOverlay(Graphics2D g2d, String message, Color color) {
        Composite originalComposite = g2d.getComposite();
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.8f));
        g2d.setColor(Color.BLACK);
        g2d.fillRect(0, 0, gamePanel.getWidth(), gamePanel.getHeight());
        g2d.setComposite(originalComposite);
        g2d.setFont(END_GAME_OVERLAY_FONT_LARGE);
        FontMetrics fm = g2d.getFontMetrics();
        int textWidth = fm.stringWidth(message);
        int x = (gamePanel.getWidth() - textWidth) / 2;
        int y = gamePanel.getHeight() / 2 - 20;
        g2d.setColor(Color.BLACK);
        g2d.drawString(message, x + 4, y + 4);
        g2d.setColor(color);
        g2d.drawString(message, x, y);
        g2d.setFont(END_GAME_OVERLAY_FONT_SMALL);
        g2d.setColor(Color.LIGHT_GRAY);
        String subText = "(Results dialog will appear shortly)";
        fm = g2d.getFontMetrics();
        int subWidth = fm.stringWidth(subText);
        int sx = (gamePanel.getWidth() - subWidth) / 2;
        int sy = y + fm.getAscent() + 15;
        g2d.drawString(subText, sx, sy);
    }

    private void drawTimeScrubberUI(Graphics2D g2d) {
        int panelWidth = gamePanel.getWidth();
        int panelHeight = gamePanel.getHeight();
        int scrubY = panelHeight - TIME_SCRUB_HEIGHT - TIME_SCRUB_PADDING;
        int scrubX = TIME_SCRUB_PADDING;
        int scrubWidth = panelWidth - 2 * TIME_SCRUB_PADDING;
        g2d.setColor(TIME_SCRUB_BG_COLOR);
        g2d.fillRoundRect(scrubX, scrubY, scrubWidth, TIME_SCRUB_HEIGHT, 10, 10);
        g2d.setColor(TIME_SCRUB_BG_COLOR.darker());
        g2d.drawRoundRect(scrubX, scrubY, scrubWidth, TIME_SCRUB_HEIGHT, 10, 10);
        long currentTime = gamePanel.getViewedTimeMs();
        long maxTime = gamePanel.getMaxPredictionTimeMs();
        double progress = (maxTime > 0) ? (double) currentTime / maxTime : 0.0;
        progress = Math.max(0.0, Math.min(1.0, progress));
        int progressBarInnerX = scrubX + 2;
        int progressBarInnerWidth = scrubWidth - 4;
        int progressFillWidth = (int) (progress * progressBarInnerWidth);
        if (progressFillWidth > 0) {
            g2d.setColor(TIME_SCRUB_BAR_COLOR);
            g2d.fillRect(progressBarInnerX, scrubY + 2, progressFillWidth, TIME_SCRUB_HEIGHT - 4);
        }
        g2d.setFont(TIME_SCRUB_FONT);
        g2d.setColor(TIME_SCRUB_FG_COLOR);
        String timeString = String.format("View Time: %.2f s", currentTime / 1000.0);
        FontMetrics fm = g2d.getFontMetrics();
        int textWidth = fm.stringWidth(timeString);
        int textX = scrubX + (scrubWidth - textWidth) / 2;
        int textY = scrubY + fm.getAscent() + (TIME_SCRUB_HEIGHT - fm.getHeight()) / 2;
        g2d.drawString(timeString, textX, textY);
    }

    private void drawPredictedPacket(Graphics2D g2d, PacketSnapshot snapshot) {
        if (snapshot.getStatus() == PredictedPacketStatus.NOT_YET_GENERATED || snapshot.getPosition() == null) {
            return;
        }
        Point position = snapshot.getPosition();
        int drawSize = snapshot.getDrawSize();
        int halfSize = drawSize / 2;
        int x = position.x - halfSize;
        int y = position.y - halfSize;
        Color baseColor = Port.getColorFromShape(snapshot.getShape());
        int alpha = PREDICTION_ALPHA_DEFAULT;
        String statusIndicator = null;
        switch (snapshot.getStatus()) {
            case LOST:
                alpha = PREDICTION_ALPHA_LOST;
                baseColor = PREDICTION_COLOR_LOST_BASE;
                break;
            case STALLED_AT_NODE:
                alpha = PREDICTION_ALPHA_STALLED;
                baseColor = PREDICTION_COLOR_STALLED_BASE;
                statusIndicator = "S";
                break;
            case DELIVERED:
                alpha = PREDICTION_ALPHA_DELIVERED;
                baseColor = PREDICTION_COLOR_DELIVERED_BASE;
                statusIndicator = "D";
                break;
            case ON_WIRE:
                break;
            default:
                break;
        }
        Color drawColor = new Color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(), alpha);
        g2d.setColor(drawColor);
        AffineTransform oldTransform = g2d.getTransform();
        Path2D path = null;
        try {
            switch (snapshot.getShape()) {
                case SQUARE:
                    g2d.fillRect(x, y, drawSize, drawSize);
                    break;
                case TRIANGLE:
                    path = new Path2D.Double();
                    path.moveTo(position.x, y); // Top point
                    path.lineTo(x + drawSize, y + drawSize); // Bottom-right
                    path.lineTo(x, y + drawSize); // Bottom-left
                    path.closePath();
                    g2d.fill(path);
                    break;
                default: // Should be Circle or other, draw oval as fallback
                    g2d.fillOval(x, y, drawSize, drawSize);
                    break;
            }
            g2d.setColor(drawColor.darker());
            g2d.setStroke(new BasicStroke(1));
            switch (snapshot.getShape()) {
                case SQUARE: g2d.drawRect(x, y, drawSize, drawSize); break;
                case TRIANGLE: if (path != null) g2d.draw(path); break;
                default: g2d.drawOval(x, y, drawSize, drawSize); break;
            }

            if (statusIndicator != null) {
                g2d.setColor(PREDICTION_INDICATOR_COLOR);
                g2d.setFont(PREDICTION_STATUS_FONT);
                FontMetrics fm = g2d.getFontMetrics();
                int charWidth = fm.stringWidth(statusIndicator);
                int charHeight = fm.getAscent();
                g2d.drawString(statusIndicator, position.x - charWidth / 2, position.y + charHeight / 3);
            }
            else if (snapshot.getStatus() == PredictedPacketStatus.LOST) {
                g2d.setColor(PREDICTION_LOST_X_COLOR);
                g2d.setStroke(new BasicStroke(1.5f));
                g2d.drawLine(x, y, x + drawSize, y + drawSize);
                g2d.drawLine(x + drawSize, y, x, y + drawSize);
            }
        } finally {
            g2d.setStroke(new BasicStroke(1)); // Reset stroke
            // g2d.setTransform(oldTransform); // Not needed if not transforming for prediction
        }
    }

    private void drawTemporaryMessage(Graphics2D g2d) {
        NetworkGame.TemporaryMessage msg = gamePanel.getGame().getTemporaryMessage();
        if (msg != null) {
            String text = msg.message;
            Color color = msg.color;
            long displayUntil = msg.displayUntilTimestamp;
            float alphaFactor = 1.0f;

            long currentTime = java.lang.System.currentTimeMillis();
            long timeLeft = displayUntil - currentTime;
            long fadeDuration = 500; // Start fading 500ms before disappearing

            if (timeLeft <= 0) {
                gamePanel.getGame().clearTemporaryMessage(); // Message expired
                return;
            } else if (timeLeft < fadeDuration) {
                alphaFactor = (float)timeLeft / fadeDuration;
                alphaFactor = Math.max(0.0f, Math.min(1.0f, alphaFactor)); // Clamp
            }

            g2d.setFont(TEMP_MESSAGE_FONT);
            FontMetrics fm = g2d.getFontMetrics();
            int textWidth = fm.stringWidth(text);
            int textHeight = fm.getHeight();
            int panelWidth = gamePanel.getWidth();

            int x = (panelWidth - textWidth) / 2;
            int y = 30 + fm.getAscent();

            Color bgColor = new Color(0, 0, 0, (int)(150 * alphaFactor));
            g2d.setColor(bgColor);
            g2d.fillRoundRect(x - 10, y - textHeight + fm.getDescent() - 5, textWidth + 20, textHeight + 5, 10, 10);

            g2d.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), (int)(255 * alphaFactor)));
            g2d.drawString(text, x, y);
        }
    }
}