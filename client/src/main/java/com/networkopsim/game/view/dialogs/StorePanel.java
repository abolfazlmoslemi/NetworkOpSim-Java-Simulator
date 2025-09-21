// ===== File: StorePanel.java (FINAL CORRECTED VERSION) =====
// ===== MODULE: client =====

package com.networkopsim.game.view.dialogs;

import com.networkopsim.game.controller.core.NetworkGame;
import com.networkopsim.game.model.state.GameState;
import com.networkopsim.game.net.ClientAction;
import com.networkopsim.game.view.panels.GamePanel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Objects;

public class StorePanel extends JDialog {
  private static final Logger logger = LoggerFactory.getLogger(StorePanel.class);

  private final NetworkGame game;
  private final GamePanel gamePanel;
  private JLabel coinsLabel;

  private static final String ITEM_ATAR_NAME = "O' Atar"; private static final int COST_ATAR = 3; private static final String DESC_ATAR = "Disables collision Impact Waves for 10 seconds.";
  private static final String ITEM_AIRYAMAN_NAME = "O' Airyaman"; private static final int COST_AIRYAMAN = 4; private static final String DESC_AIRYAMAN = "Disables Packet-to-Packet collisions for 5 seconds.";
  private static final String ITEM_ANAHITA_NAME = "O' Anahita"; private static final int COST_ANAHITA = 5; private static final String DESC_ANAHITA = "Instantly resets the Noise level of all active Packets to zero.";
  private static final String ITEM_SPEED_LIMITER_NAME = "Speed Limiter"; private static final int COST_SPEED_LIMITER = 7; private static final String DESC_SPEED_LIMITER = "Temporarily prevents packets from accelerating for 15 seconds.";
  private static final String ITEM_EMERGENCY_BRAKE_NAME = "Emergency Brake"; private static final int COST_EMERGENCY_BRAKE = 8; private static final String DESC_EMERGENCY_BRAKE = "Instantly resets the speed of all active packets to their base speed.";
  private static final String ITEM_AERGIA_NAME = "Scroll of Aergia"; private static final int COST_AERGIA = 10; private static final String DESC_AERGIA = "Click a wire to nullify packet acceleration at that point for 20s. (Has Cooldown)";
  private static final String ITEM_SISYPHUS_NAME = "Scroll of Sisyphus"; private static final int COST_SISYPHUS = 15; private static final String DESC_SISYPHUS = "Allows you to move one non-reference system within a limited radius.";
  private static final String ITEM_ELIPHAS_NAME = "Scroll of Eliphas"; private static final int COST_ELIPHAS = 20; private static final String DESC_ELIPHAS = "Click a wire to create a 30s field that gradually realigns passing packets.";

  private static final Color DIALOG_BG_COLOR = new Color(45, 45, 55);
  private static final Color ITEM_BG_COLOR = new Color(70, 70, 85);
  private static final Color CLOSE_BUTTON_BG_COLOR = new Color(180, 80, 80);
  private static final Color DISABLED_BUTTON_BG_COLOR = Color.DARK_GRAY.darker();
  private static final Color ACTIVATED_BUTTON_BG_COLOR = new Color(30, 100, 50);
  private static final Color FLASH_COLOR_PURCHASE = Color.GREEN.darker();
  private static final Color TEXT_COLOR_TITLE = Color.WHITE;
  private static final Color TEXT_COLOR_DESC = new Color(204, 204, 204);
  private static final Color TEXT_COLOR_COST = Color.ORANGE;
  private static final Color TEXT_COLOR_ACTIVATED = new Color(170, 255, 170);

  private JButton atarButton, airyamanButton, anahitaButton, speedLimiterButton, emergencyBrakeButton, aergiaButton, sisyphusButton, eliphasButton;

  public StorePanel(NetworkGame owner, GamePanel gamePanel) {
    super(owner, "Game Store", true);
    this.game = owner;
    this.gamePanel = gamePanel;
    setSize(550, 750);
    setLocationRelativeTo(owner);
    setResizable(false);
    setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
    addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosing(WindowEvent e) {
        closeStoreAndResumeGame();
      }
    });

    JPanel mainPanel = new JPanel(new BorderLayout(15, 15));
    mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 25, 20, 25));
    mainPanel.setBackground(DIALOG_BG_COLOR);
    mainPanel.add(createTopPanel(), BorderLayout.NORTH);
    JScrollPane itemsScrollPane = new JScrollPane(createItemsPanel());
    itemsScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
    itemsScrollPane.setBorder(null);
    itemsScrollPane.getViewport().setBackground(DIALOG_BG_COLOR);
    mainPanel.add(itemsScrollPane, BorderLayout.CENTER);
    mainPanel.add(createBottomPanel(), BorderLayout.SOUTH);
    setContentPane(mainPanel);
  }

  private JPanel createTopPanel() {
    JPanel topPanel = new JPanel(new BorderLayout(10, 0));
    topPanel.setOpaque(false);
    JLabel titleLabel = new JLabel("Power-Up Store", SwingConstants.CENTER);
    titleLabel.setFont(new Font("Arial", Font.BOLD, 26));
    titleLabel.setForeground(Color.CYAN);
    coinsLabel = new JLabel("Coins: 0");
    coinsLabel.setFont(new Font("Arial", Font.BOLD, 18));
    coinsLabel.setForeground(Color.YELLOW);
    coinsLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 10));
    topPanel.add(titleLabel, BorderLayout.CENTER);
    topPanel.add(coinsLabel, BorderLayout.EAST);
    return topPanel;
  }

  private JPanel createItemsPanel() {
    JPanel itemsPanel = new JPanel();
    itemsPanel.setLayout(new BoxLayout(itemsPanel, BoxLayout.Y_AXIS));
    itemsPanel.setOpaque(true);
    itemsPanel.setBackground(DIALOG_BG_COLOR);
    itemsPanel.setBorder(BorderFactory.createEmptyBorder(20, 0, 10, 0));
    atarButton = createItemButton(ITEM_ATAR_NAME, COST_ATAR, DESC_ATAR);
    airyamanButton = createItemButton(ITEM_AIRYAMAN_NAME, COST_AIRYAMAN, DESC_AIRYAMAN);
    anahitaButton = createItemButton(ITEM_ANAHITA_NAME, COST_ANAHITA, DESC_ANAHITA);
    speedLimiterButton = createItemButton(ITEM_SPEED_LIMITER_NAME, COST_SPEED_LIMITER, DESC_SPEED_LIMITER);
    emergencyBrakeButton = createItemButton(ITEM_EMERGENCY_BRAKE_NAME, COST_EMERGENCY_BRAKE, DESC_EMERGENCY_BRAKE);
    aergiaButton = createItemButton(ITEM_AERGIA_NAME, COST_AERGIA, DESC_AERGIA);
    sisyphusButton = createItemButton(ITEM_SISYPHUS_NAME, COST_SISYPHUS, DESC_SISYPHUS);
    eliphasButton = createItemButton(ITEM_ELIPHAS_NAME, COST_ELIPHAS, DESC_ELIPHAS);
    atarButton.addActionListener(e -> purchaseItem(ITEM_ATAR_NAME, atarButton));
    airyamanButton.addActionListener(e -> purchaseItem(ITEM_AIRYAMAN_NAME, airyamanButton));
    anahitaButton.addActionListener(e -> purchaseItem(ITEM_ANAHITA_NAME, anahitaButton));
    speedLimiterButton.addActionListener(e -> purchaseItem(ITEM_SPEED_LIMITER_NAME, speedLimiterButton));
    emergencyBrakeButton.addActionListener(e -> purchaseItem(ITEM_EMERGENCY_BRAKE_NAME, emergencyBrakeButton));
    aergiaButton.addActionListener(e -> purchaseItem(ITEM_AERGIA_NAME, aergiaButton));
    sisyphusButton.addActionListener(e -> purchaseItem(ITEM_SISYPHUS_NAME, sisyphusButton));
    eliphasButton.addActionListener(e -> purchaseItem(ITEM_ELIPHAS_NAME, eliphasButton));
    itemsPanel.add(atarButton); itemsPanel.add(Box.createVerticalStrut(12));
    itemsPanel.add(airyamanButton); itemsPanel.add(Box.createVerticalStrut(12));
    itemsPanel.add(anahitaButton); itemsPanel.add(Box.createVerticalStrut(12));
    itemsPanel.add(speedLimiterButton); itemsPanel.add(Box.createVerticalStrut(12));
    itemsPanel.add(emergencyBrakeButton);
    JSeparator separator = new JSeparator(SwingConstants.HORIZONTAL);
    separator.setForeground(new Color(100, 100, 120)); separator.setBackground(DIALOG_BG_COLOR);
    itemsPanel.add(Box.createVerticalStrut(15)); itemsPanel.add(separator); itemsPanel.add(Box.createVerticalStrut(15));
    itemsPanel.add(aergiaButton); itemsPanel.add(Box.createVerticalStrut(12));
    itemsPanel.add(sisyphusButton); itemsPanel.add(Box.createVerticalStrut(12));
    itemsPanel.add(eliphasButton);
    return itemsPanel;
  }

  private JPanel createBottomPanel() {
    JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
    bottomPanel.setOpaque(false);
    bottomPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
    JButton closeButton = new JButton("Close Store");
    styleButton(closeButton, CLOSE_BUTTON_BG_COLOR, new Color(130, 50, 50), new Font("Arial", Font.BOLD, 16));
    closeButton.addActionListener(e -> closeStoreAndResumeGame());
    bottomPanel.add(closeButton);
    return bottomPanel;
  }

  private void purchaseItem(String itemName, JButton button) {
    if (!gamePanel.isGameRunning() || game.getGameClient() == null) return;
    logger.info("Sending purchase request for '{}' to server.", itemName);
    game.getGameClient().sendAction(new ClientAction(ClientAction.ActionType.BUY_ITEM, itemName));
    if (itemName.equals(ITEM_AERGIA_NAME) || itemName.equals(ITEM_SISYPHUS_NAME) || itemName.equals(ITEM_ELIPHAS_NAME)) {
      setVisible(false);
    } else {
      flashButton(button, FLASH_COLOR_PURCHASE);
    }
  }

  private void closeStoreAndResumeGame() {
    setVisible(false);
    if (gamePanel != null && gamePanel.isGameRunning() && gamePanel.isGamePaused()) {
      gamePanel.pauseGame(false);
      gamePanel.requestFocusInWindow();
    }
  }

  @Override
  public void setVisible(boolean b) {
    if (b) {
      updateCoinsDisplay(gamePanel.getGameState().getCoins());
      updateButtonStates();
    }
    super.setVisible(b);
  }

  public void updateCoinsDisplay(int coins) {
    if (coinsLabel != null) {
      coinsLabel.setText("Coins: " + coins);
    }
  }

  private void updateButtonStates() {
    GameState gs = gamePanel.getGameState();
    if(gs == null) return;
    int coins = gs.getCoins();
    // NOTE: Active status checks (e.g., gamePanel.isAtarActive()) are now placeholders.
    // The authoritative state is on the server. The client only reflects it.
    // For now, we mainly check the cost.
    updateButton(atarButton, ITEM_ATAR_NAME, COST_ATAR, DESC_ATAR, coins >= COST_ATAR, "");
    updateButton(airyamanButton, ITEM_AIRYAMAN_NAME, COST_AIRYAMAN, DESC_AIRYAMAN, coins >= COST_AIRYAMAN, "");
    updateButton(anahitaButton, ITEM_ANAHITA_NAME, COST_ANAHITA, DESC_ANAHITA, coins >= COST_ANAHITA, "");
    updateButton(speedLimiterButton, ITEM_SPEED_LIMITER_NAME, COST_SPEED_LIMITER, DESC_SPEED_LIMITER, coins >= COST_SPEED_LIMITER, "");
    updateButton(emergencyBrakeButton, ITEM_EMERGENCY_BRAKE_NAME, COST_EMERGENCY_BRAKE, DESC_EMERGENCY_BRAKE, coins >= COST_EMERGENCY_BRAKE, "");
    updateButton(aergiaButton, ITEM_AERGIA_NAME, COST_AERGIA, DESC_AERGIA, coins >= COST_AERGIA, "");
    updateButton(sisyphusButton, ITEM_SISYPHUS_NAME, COST_SISYPHUS, DESC_SISYPHUS, coins >= COST_SISYPHUS, "");
    updateButton(eliphasButton, ITEM_ELIPHAS_NAME, COST_ELIPHAS, DESC_ELIPHAS, coins >= COST_ELIPHAS, "");
  }

  private void updateButton(JButton button, String name, int cost, String desc, boolean enabled, String status) {
    button.setText(buildButtonHtml(name, cost, desc, status));
    button.setEnabled(enabled);
    button.setBackground(enabled ? ITEM_BG_COLOR : DISABLED_BUTTON_BG_COLOR);
  }

  private void flashButton(JButton button, Color flashColor) {
    Color originalBg = button.getBackground();
    button.setBackground(flashColor);
    new Timer(250, e -> {
      button.setBackground(originalBg);
      updateButtonStates();
    }) {{ setRepeats(false); }}.start();
  }

  private String buildButtonHtml(String name, int cost, String description, String statusString) {
    String costString = "(Cost: " + cost + ")";
    return String.format(
            "<html><body style='width: 380px;'><div style='font-family: Arial; font-size: 13pt; color: #%06X;'><b>%s</b> <span style='color: #%06X;'>%s</span></div><div style='font-family: Arial; font-size: 10pt; color: #%06X; margin-top: 2px;'>%s</div>" +
                    (statusString.isEmpty() ? "" : "<div style='font-family: Arial; font-size: 10pt; color: #%06X; text-align: right;'><b>%s</b></div>") +
                    "</body></html>",
            TEXT_COLOR_TITLE.getRGB() & 0xFFFFFF, name,
            TEXT_COLOR_COST.getRGB() & 0xFFFFFF, costString,
            TEXT_COLOR_DESC.getRGB() & 0xFFFFFF, description,
            TEXT_COLOR_ACTIVATED.getRGB() & 0xFFFFFF, statusString
    );
  }

  private JButton createItemButton(String name, int cost, String description) {
    JButton button = new JButton();
    button.setHorizontalAlignment(SwingConstants.LEFT);
    button.setVerticalAlignment(SwingConstants.TOP);
    styleButton(button, ITEM_BG_COLOR, new Color(100, 100, 120), null);
    Dimension prefSize = new Dimension(450, 75);
    button.setAlignmentX(Component.CENTER_ALIGNMENT);
    button.setPreferredSize(prefSize);
    button.setMaximumSize(prefSize);
    return button;
  }

  private void styleButton(JButton button, Color bgColor, Color borderColor, Font font) {
    if (font != null) { button.setFont(font); }
    button.setForeground(Color.WHITE);
    button.setBackground(bgColor);
    button.setFocusPainted(false);
    Border line = BorderFactory.createLineBorder(borderColor, 2);
    Border empty = BorderFactory.createEmptyBorder(10, 25, 10, 25);
    button.setBorder(BorderFactory.createCompoundBorder(line, empty));
    button.setCursor(new Cursor(Cursor.HAND_CURSOR));
    button.addMouseListener(new MouseAdapter() {
      final Color originalColor = button.getBackground();
      final Color hoverColor = new Color(Math.min(255, originalColor.getRed() + 20), Math.min(255, originalColor.getGreen() + 20), Math.min(255, originalColor.getBlue() + 20), originalColor.getAlpha());
      @Override
      public void mouseEntered(MouseEvent evt) {
        if (button.isEnabled()) button.setBackground(hoverColor);
      }
      @Override
      public void mouseExited(MouseEvent evt) {
        if (button.isEnabled()) button.setBackground(originalColor);
      }
    });
  }
}