// ===== File: GameRenderer.java (FINAL COMPLETE VERSION for Client) =====
// ===== MODULE: client =====

package com.networkopsim.game.view.rendering;

import com.networkopsim.game.controller.core.NetworkGame;
import com.networkopsim.game.model.core.Packet;
import com.networkopsim.game.model.core.Port;
import com.networkopsim.game.model.core.System;
import com.networkopsim.game.model.core.Wire;
import com.networkopsim.game.model.enums.NetworkEnums;
import com.networkopsim.game.model.state.GameState;
import com.networkopsim.client.utils.KeyBindings;
import com.networkopsim.game.view.panels.GamePanel;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class GameRenderer {
  private final GamePanel gamePanel;
  private GameState gameState;
  private final KeyBindings keyBindings;

  private List<System> systems = Collections.emptyList();
  private List<Wire> wires = Collections.emptyList();
  private List<Packet> packets = Collections.emptyList();

  // --- UI Constants ---
  private static final Stroke WIRING_LINE_STROKE = new BasicStroke(2.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, new float[]{9f, 5f}, 0.0f);
  private static final Font HUD_FONT_BOLD = new Font("Consolas", Font.BOLD, 14);
  private static final Font HUD_FONT_PLAIN = new Font("Consolas", Font.PLAIN, 11);
  private static final Font HUD_COUNT_FONT = new Font("Consolas", Font.PLAIN, 12);
  private static final Font HUD_TOGGLE_FONT = new Font("Consolas", Font.PLAIN, 10);
  private static final Font PAUSE_OVERLAY_FONT_LARGE = new Font("Arial", Font.BOLD, 60);
  private static final Font PAUSE_OVERLAY_FONT_SMALL = new Font("Arial", Font.PLAIN, 18);
  private static final Font END_GAME_OVERLAY_FONT_LARGE = new Font("Arial", Font.BOLD, 72);
  private static final Font END_GAME_OVERLAY_FONT_SMALL = new Font("Arial", Font.PLAIN, 16);
  private static final Font TEMP_MESSAGE_FONT = new Font("Arial", Font.BOLD, 16);
  private static final Color GRID_COLOR = new Color(40, 40, 50);
  private static final Color HUD_BACKGROUND_COLOR = new Color(20, 20, 30, 210);
  private static final Color GAME_OVER_COLOR = Color.RED.darker();
  private static final Color LEVEL_COMPLETE_COLOR = Color.GREEN.darker();
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
  private static final Font NOISE_FONT = new Font("Arial", Font.PLAIN, 9);
  private static final Color NOISE_TEXT_COLOR = Color.WHITE;
  private static final Color IDEAL_POSITION_MARKER_COLOR = new Color(255, 255, 255, 60);
  private static final int IDEAL_POSITION_MARKER_SIZE = 4;
  private static final int GRID_SIZE = 25;

  public GameRenderer(GamePanel gamePanel, Object ignoredGameEngine, GameState gameState) {
    this.gamePanel = gamePanel;
    this.gameState = gameState;
    this.keyBindings = gamePanel.getGame().getKeyBindings();
  }

  public void setGameState(GameState newGameState) {
    this.gameState = newGameState;
  }

  public void setServerState(List<System> systems, List<Wire> wires, List<Packet> packets) {
    this.systems = (systems != null) ? systems : Collections.emptyList();
    this.wires = (wires != null) ? wires : Collections.emptyList();
    this.packets = (packets != null) ? packets : Collections.emptyList();
  }

  public void render(Graphics g) {
    Graphics2D g2d = (Graphics2D) g.create();
    try {
      setupHighQualityRendering(g2d);
      drawGrid(g2d);

      boolean isBudgetExceeded = (gameState != null) && gameState.getRemainingWireLength() < 0;

      for (Wire w : this.wires) {
        if (w != null) {
          w.draw(g2d, !isBudgetExceeded);
        }
      }

      // [CORRECTED] Call drawSystems with the simulation time
      SystemDrawer.drawSystems(g2d, this.systems, gamePanel.getSimulationTimeElapsedMs());

      if (gamePanel.isWireDrawingMode() && gamePanel.getSelectedOutputPort() != null && gamePanel.getMouseDragPos() != null) {
        drawWiringLine(g2d);
      }

      List<Packet> packetsForRendering = this.packets.stream()
              .filter(p -> p != null && p.getCurrentSystem() == null && !p.isGhost())
              .collect(Collectors.toList());
      for (Packet p : packetsForRendering) {
        drawPacket(g2d, p);
      }

      if (gamePanel.isShowHUD()) {
        drawHUD(g2d);
      } else {
        drawHudToggleHint(g2d);
      }

      drawTemporaryMessage(g2d);

      if (gamePanel.isGamePaused()) {
        drawPauseOverlay(g2d);
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
    if (gameState == null) return;
    int hudX = 15, startY = 30, lineHeightBold = 20, lineHeightPlain = 18, hudWidth = 250;
    List<String> lines = new ArrayList<>();
    List<Color> lineColors = new ArrayList<>();
    List<Font> lineFonts = new ArrayList<>();
    List<Integer> lineHeights = new ArrayList<>();

    lines.add("LEVEL: " + gameState.getCurrentSelectedLevel()); lineColors.add(HUD_LEVEL_COLOR); lineFonts.add(HUD_FONT_BOLD); lineHeights.add(lineHeightBold);
    lines.add("COINS: " + gameState.getCoins()); lineColors.add(HUD_COINS_COLOR); lineFonts.add(HUD_FONT_BOLD); lineHeights.add(lineHeightBold);
    lines.add("WIRE LEFT: " + gameState.getRemainingWireLength()); lineColors.add(HUD_WIRE_COLOR); lineFonts.add(HUD_FONT_BOLD); lineHeights.add(lineHeightBold);
    lines.add(String.format("TIME: %.2f s", gamePanel.getSimulationTimeElapsedMs() / 1000.0)); lineColors.add(HUD_TIME_COLOR); lineFonts.add(HUD_FONT_BOLD); lineHeights.add(lineHeightBold);

    int generatedCount = gameState.getTotalPacketsGeneratedCount();
    int lostCount = gameState.getTotalPacketsLostCount();
    double lossPercent = gameState.getPacketLossPercentage();

    lines.add("Generated: " + generatedCount); lineColors.add(HUD_COUNT_COLOR); lineFonts.add(HUD_COUNT_FONT); lineHeights.add(lineHeightPlain);
    lines.add("Lost: " + lostCount); lineColors.add(HUD_COUNT_COLOR); lineFonts.add(HUD_COUNT_FONT); lineHeights.add(lineHeightPlain);

    Color lossColor;
    if (lossPercent >= 35.0) { lossColor = HUD_LOSS_DANGER_COLOR; }
    else if (lossPercent >= 15.0) { lossColor = HUD_LOSS_WARN_COLOR; }
    else { lossColor = HUD_LOSS_OK_COLOR; }
    lines.add(String.format("LOSS (Units): %.1f%%", lossPercent)); lineColors.add(lossColor); lineFonts.add(HUD_FONT_BOLD); lineHeights.add(lineHeightBold);

    int totalHeightAccumulated = 0; for (int height : lineHeights) { totalHeightAccumulated += height; } int hudHeight = 20 + totalHeightAccumulated; g2d.setColor(HUD_BACKGROUND_COLOR); g2d.fillRoundRect(hudX - 10, startY - 20, hudWidth, hudHeight, 15, 15);
    int currentY = startY; for (int i = 0; i < lines.size(); i++) { g2d.setColor(lineColors.get(i)); g2d.setFont(lineFonts.get(i)); g2d.drawString(lines.get(i), hudX, currentY); currentY += lineHeights.get(i); }
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

  private void drawPauseOverlay(Graphics2D g2d) {
    Composite originalComposite = g2d.getComposite();
    g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.7f));
    g2d.setColor(Color.BLACK);
    g2d.fillRect(0, 0, gamePanel.getWidth(), gamePanel.getHeight());
    g2d.setComposite(originalComposite);
    String text = "PAUSED";
    g2d.setFont(PAUSE_OVERLAY_FONT_LARGE);
    FontMetrics fm = g2d.getFontMetrics();
    int x = (gamePanel.getWidth() - fm.stringWidth(text)) / 2;
    int y = gamePanel.getHeight() / 2;
    g2d.setColor(Color.DARK_GRAY);
    g2d.drawString(text, x + 3, y + 3);
    g2d.setColor(Color.YELLOW);
    g2d.drawString(text, x, y);
    String instructionText = String.format("%s: Resume | %s: Menu", keyBindings.getKeyText(keyBindings.getKeyCode(KeyBindings.GameAction.PAUSE_RESUME_GAME)), keyBindings.getKeyText(keyBindings.getKeyCode(KeyBindings.GameAction.ESCAPE_MENU_CANCEL)));
    g2d.setFont(PAUSE_OVERLAY_FONT_SMALL);
    fm = g2d.getFontMetrics();
    int ix = (gamePanel.getWidth() - fm.stringWidth(instructionText)) / 2;
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
    int x = (gamePanel.getWidth() - fm.stringWidth(message)) / 2;
    int y = gamePanel.getHeight() / 2 - 20;
    g2d.setColor(Color.BLACK);
    g2d.drawString(message, x + 4, y + 4);
    g2d.setColor(color);
    g2d.drawString(message, x, y);
    g2d.setFont(END_GAME_OVERLAY_FONT_SMALL);
    g2d.setColor(Color.LIGHT_GRAY);
    String subText = "(Server will handle results)";
    fm = g2d.getFontMetrics();
    int sx = (gamePanel.getWidth() - fm.stringWidth(subText)) / 2;
    int sy = y + fm.getAscent() + 15;
    g2d.drawString(subText, sx, sy);
  }

  private void drawTemporaryMessage(Graphics2D g2d) {
    NetworkGame.TemporaryMessage msg = gamePanel.getGame().getTemporaryMessage();
    if (msg != null) {
      long currentTime = java.lang.System.currentTimeMillis();
      long timeLeft = msg.displayUntilTimestamp - currentTime;
      long fadeDuration = 500;
      if (timeLeft <= 0) {
        gamePanel.getGame().clearTemporaryMessage();
        return;
      }
      float alphaFactor = (timeLeft < fadeDuration) ? (float)timeLeft / fadeDuration : 1.0f;
      g2d.setFont(TEMP_MESSAGE_FONT);
      FontMetrics fm = g2d.getFontMetrics();
      int textWidth = fm.stringWidth(msg.message);
      int x = (gamePanel.getWidth() - textWidth) / 2;
      int y = 30 + fm.getAscent();
      Color bgColor = new Color(0, 0, 0, (int)(150 * alphaFactor));
      g2d.setColor(bgColor);
      g2d.fillRoundRect(x - 10, y - fm.getHeight() + fm.getDescent() - 5, textWidth + 20, fm.getHeight() + 5, 10, 10);
      g2d.setColor(new Color(msg.color.getRed(), msg.color.getGreen(), msg.color.getBlue(), (int)(255 * alphaFactor)));
      g2d.drawString(msg.message, x, y);
    }
  }
}