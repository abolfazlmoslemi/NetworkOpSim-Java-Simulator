// ===== File: MenuPanel.java (UNCHANGED - Logic remains the same) =====
// ===== MODULE: client =====

package com.networkopsim.game.view.panels;

import com.networkopsim.game.controller.core.NetworkGame;
import com.networkopsim.game.model.core.System; // This import should resolve via the 'common' module dependency

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.InputStream;
import java.util.Objects;

public class MenuPanel extends JPanel {
  private final NetworkGame game;
  private final JButton startButton;
  private Image backgroundImage;
  private static final Color BUTTON_BG_COLOR = new Color(70, 130, 180, 220);
  private static final Color BUTTON_BORDER_COLOR = new Color(40, 90, 130);
  private static final Color BUTTON_TEXT_COLOR = Color.WHITE;
  private static final Font BUTTON_FONT = new Font("Arial", Font.BOLD, 22);
  private static final Dimension BUTTON_SIZE = new Dimension(320, 65);
  private static final String BACKGROUND_IMAGE_PATH = "/assets/images/menu_background.png";

  public MenuPanel(NetworkGame game) {
    this.game = Objects.requireNonNull(game, "NetworkGame instance cannot be null");
    setLayout(new BorderLayout());
    setPreferredSize(new Dimension(NetworkGame.WINDOW_WIDTH, NetworkGame.WINDOW_HEIGHT));
    loadBackgroundImage();

    JPanel buttonPanelWrapper = new JPanel(new GridBagLayout());
    buttonPanelWrapper.setOpaque(false);
    JPanel buttonPanel = new JPanel();
    buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.Y_AXIS));
    buttonPanel.setOpaque(false);

    startButton = createMenuButton("Start Game (Level " + game.getCurrentLevel() + ")");
    JButton levelsButton = createMenuButton("Select Level");
    JButton settingsButton = createMenuButton("Settings");
    JButton exitButton = createMenuButton("Exit Game");

    buttonPanel.add(startButton);
    buttonPanel.add(Box.createVerticalStrut(15));
    buttonPanel.add(levelsButton);
    buttonPanel.add(Box.createVerticalStrut(15));
    buttonPanel.add(settingsButton);
    buttonPanel.add(Box.createVerticalStrut(30));
    buttonPanel.add(exitButton);

    startButton.addActionListener(e -> game.startGame());
    levelsButton.addActionListener(e -> game.showLevelSelection());
    settingsButton.addActionListener(e -> game.showSettings());
    exitButton.addActionListener(e -> handleExitRequest());

    buttonPanelWrapper.add(buttonPanel);
    add(buttonPanelWrapper, BorderLayout.CENTER);
  }

  private void loadBackgroundImage() {
    try {
      InputStream imgStream = getClass().getResourceAsStream(BACKGROUND_IMAGE_PATH);
      if (imgStream != null) {
        backgroundImage = ImageIO.read(imgStream);
        imgStream.close();
      } else {
        java.lang.System.err.println("Warning: Menu background image not found in resources at " + BACKGROUND_IMAGE_PATH);
        backgroundImage = null;
      }
    } catch (Exception e) {
      java.lang.System.err.println("Error loading menu background image from resources: " + e.getMessage());
      backgroundImage = null;
    }
  }

  private JButton createMenuButton(String text) {
    JButton button = new JButton(text);
    button.setFont(BUTTON_FONT);
    button.setForeground(BUTTON_TEXT_COLOR);
    button.setBackground(BUTTON_BG_COLOR);
    button.setFocusPainted(false);
    Border line = BorderFactory.createLineBorder(BUTTON_BORDER_COLOR, 2);
    Border empty = BorderFactory.createEmptyBorder(12, 25, 12, 25);
    button.setBorder(BorderFactory.createCompoundBorder(line, empty));
    button.setPreferredSize(BUTTON_SIZE);
    button.setMaximumSize(BUTTON_SIZE);
    button.setMinimumSize(BUTTON_SIZE);
    button.setAlignmentX(Component.CENTER_ALIGNMENT);
    button.setCursor(new Cursor(Cursor.HAND_CURSOR));

    button.addMouseListener(new MouseAdapter() {
      final Color originalColor = button.getBackground();
      final Color hoverColor = new Color(
              Math.min(255, originalColor.getRed() + 20),
              Math.min(255, originalColor.getGreen() + 20),
              Math.min(255, originalColor.getBlue() + 20),
              originalColor.getAlpha()
      );

      @Override
      public void mouseEntered(MouseEvent e) {
        button.setBackground(hoverColor);
      }

      @Override
      public void mouseExited(MouseEvent e) {
        button.setBackground(originalColor);
      }
    });
    return button;
  }

  private void handleExitRequest() {
    int choice = JOptionPane.showConfirmDialog(
            game,
            "Are you sure you want to exit?",
            "Exit Game",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE);
    if (choice == JOptionPane.YES_OPTION) {
      game.shutdownGame();
    }
  }

  public void updateStartButtonLevel(int level) {
    if (startButton != null) {
      startButton.setText("Start Game (Level " + level + ")");
    }
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    Graphics2D g2d = (Graphics2D) g.create();
    try {
      setupHighQualityRendering(g2d);
      if (backgroundImage != null) {
        g.drawImage(backgroundImage, 0, 0, getWidth(), getHeight(), this);
      } else {
        GradientPaint gp = new GradientPaint(0, 0, new Color(25, 25, 35),
                0, getHeight(), new Color(45, 45, 55));
        g2d.setPaint(gp);
        g2d.fillRect(0, 0, getWidth(), getHeight());
      }

      g2d.setFont(new Font("Arial", Font.BOLD, 48));
      String title = "Network Operator Simulator";
      FontMetrics fm = g2d.getFontMetrics();
      int titleWidth = fm.stringWidth(title);
      int titleX = (getWidth() - titleWidth) / 2;
      int titleY = 100;

      g2d.setColor(Color.BLACK);
      g2d.drawString(title, titleX + 2, titleY + 2);
      g2d.setColor(new Color(210, 220, 255));
      g2d.drawString(title, titleX, titleY);
    } finally {
      g2d.dispose();
    }
  }

  private void setupHighQualityRendering(Graphics2D g2d) {
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
  }
}