package com.networkopsim.game.view.dialogs;


import com.networkopsim.game.controller.core.NetworkGame;
import com.networkopsim.game.model.core.Packet;
import com.networkopsim.game.model.state.GameState;
import com.networkopsim.game.view.panels.GamePanel;

// ================================================================================
// FILE: StorePanel.java (کد کامل و نهایی با سیستم لاگ)
// ================================================================================
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Objects;

public class StorePanel extends JDialog {
    private static final Logger logger = LoggerFactory.getLogger(StorePanel.class);

    private final NetworkGame game;
    private final GamePanel gamePanel;
    private final GameState gameState;
    private JLabel coinsLabel;
    private JButton atarButton, airyamanButton, anahitaButton, speedLimiterButton, emergencyBrakeButton;
    private JButton aergiaButton, sisyphusButton, eliphasButton;
    private JButton closeButton;

    private static final String ITEM_ATAR_NAME = "O' Atar";
    private static final int COST_ATAR = 3;
    private static final String DESC_ATAR = "Disables collision Impact Waves for 10 seconds.";
    private static final String ITEM_AIRYAMAN_NAME = "O' Airyaman";
    private static final int COST_AIRYAMAN = 4;
    private static final String DESC_AIRYAMAN = "Disables Packet-to-Packet collisions for 5 seconds.";
    private static final String ITEM_ANAHITA_NAME = "O' Anahita";
    private static final int COST_ANAHITA = 5;
    private static final String DESC_ANAHITA = "Instantly resets the Noise level of all active Packets to zero.";
    private static final String ITEM_SPEED_LIMITER_NAME = "Speed Limiter";
    private static final int COST_SPEED_LIMITER = 7;
    private static final String DESC_SPEED_LIMITER = "Temporarily prevents packets from accelerating for 15 seconds.";
    private static final String ITEM_EMERGENCY_BRAKE_NAME = "Emergency Brake";
    private static final int COST_EMERGENCY_BRAKE = 8;
    private static final String DESC_EMERGENCY_BRAKE = "Instantly resets the speed of all active packets to their base speed.";
    private static final String ITEM_AERGIA_NAME = "Scroll of Aergia";
    private static final int COST_AERGIA = 10;
    private static final String DESC_AERGIA = "Click a wire to nullify packet acceleration at that point for 20s. (Has Cooldown)";
    private static final String ITEM_SISYPHUS_NAME = "Scroll of Sisyphus";
    private static final int COST_SISYPHUS = 15;
    private static final String DESC_SISYPHUS = "Allows you to move one non-reference system within a limited radius.";
    private static final String ITEM_ELIPHAS_NAME = "Scroll of Eliphas";
    private static final int COST_ELIPHAS = 20;
    private static final String DESC_ELIPHAS = "Click a wire to create a 30s field that gradually realigns passing packets.";

    private static final Color DIALOG_BG_COLOR = new Color(45, 45, 55);
    private static final Color ITEM_BG_COLOR = new Color(70, 70, 85);
    private static final Color ITEM_BORDER_COLOR = new Color(100, 100, 120);
    private static final Color CLOSE_BUTTON_BG_COLOR = new Color(180, 80, 80);
    private static final Color CLOSE_BUTTON_BORDER_COLOR = new Color(130, 50, 50);
    private static final Font TITLE_FONT = new Font("Arial", Font.BOLD, 26);
    private static final Color TITLE_COLOR = Color.CYAN;
    private static final Font COIN_FONT = new Font("Arial", Font.BOLD, 18);
    private static final Color COIN_COLOR = Color.YELLOW;
    private static final Font ITEM_BUTTON_FONT_NAME = new Font("Arial", Font.BOLD, 13);
    private static final Font ITEM_BUTTON_FONT_DESC = new Font("Arial", Font.PLAIN, 10);
    private static final Font ITEM_BUTTON_FONT_STATUS = new Font("Arial", Font.BOLD, 10);
    private static final Color ITEM_TEXT_COLOR_TITLE = Color.WHITE;
    private static final Color ITEM_TEXT_COLOR_DESC = new Color(204, 204, 204);
    private static final Color ITEM_TEXT_COLOR_COST = Color.ORANGE;
    private static final Color ITEM_TEXT_COLOR_ACTIVATED = new Color(170, 255, 170);
    private static final Color DISABLED_BUTTON_BG_COLOR = Color.DARK_GRAY.darker();
    private static final Color ACTIVATED_BUTTON_BG_COLOR = new Color(30, 100, 50);
    private static final Color FLASH_COLOR_PURCHASE = Color.GREEN.darker();

    private boolean anahitaPurchasedThisVisit = false;
    private boolean brakePurchasedThisVisit = false;

    public StorePanel(NetworkGame owner, GamePanel gamePanel) {
        super(owner, "Game Store", true);
        this.game = Objects.requireNonNull(owner, "NetworkGame owner cannot be null");
        this.gamePanel = Objects.requireNonNull(gamePanel, "GamePanel cannot be null");
        this.gameState = Objects.requireNonNull(owner.getGameState(), "GameState cannot be null");
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
        JPanel topPanel = createTopPanel();
        mainPanel.add(topPanel, BorderLayout.NORTH);
        JScrollPane itemsScrollPane = new JScrollPane(createItemsPanel());
        itemsScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        itemsScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        itemsScrollPane.setBorder(null);
        itemsScrollPane.getViewport().setBackground(DIALOG_BG_COLOR);
        mainPanel.add(itemsScrollPane, BorderLayout.CENTER);
        JPanel bottomPanel = createBottomPanel();
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);
        setContentPane(mainPanel);
    }

    private JPanel createTopPanel() {
        JPanel topPanel = new JPanel(new BorderLayout(10, 0));
        topPanel.setOpaque(false);
        JLabel titleLabel = new JLabel("Power-Up Store", SwingConstants.CENTER);
        titleLabel.setFont(TITLE_FONT);
        titleLabel.setForeground(TITLE_COLOR);
        coinsLabel = new JLabel("Coins: 0");
        coinsLabel.setFont(COIN_FONT);
        coinsLabel.setForeground(COIN_COLOR);
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

        atarButton.addActionListener(e -> purchaseItem(ITEM_ATAR_NAME, COST_ATAR, atarButton));
        airyamanButton.addActionListener(e -> purchaseItem(ITEM_AIRYAMAN_NAME, COST_AIRYAMAN, airyamanButton));
        anahitaButton.addActionListener(e -> purchaseItem(ITEM_ANAHITA_NAME, COST_ANAHITA, anahitaButton));
        speedLimiterButton.addActionListener(e -> purchaseItem(ITEM_SPEED_LIMITER_NAME, COST_SPEED_LIMITER, speedLimiterButton));
        emergencyBrakeButton.addActionListener(e -> purchaseItem(ITEM_EMERGENCY_BRAKE_NAME, COST_EMERGENCY_BRAKE, emergencyBrakeButton));
        aergiaButton.addActionListener(e -> purchaseItem(ITEM_AERGIA_NAME, COST_AERGIA, aergiaButton));
        sisyphusButton.addActionListener(e -> purchaseItem(ITEM_SISYPHUS_NAME, COST_SISYPHUS, sisyphusButton));
        eliphasButton.addActionListener(e -> purchaseItem(ITEM_ELIPHAS_NAME, COST_ELIPHAS, eliphasButton));

        itemsPanel.add(atarButton);
        itemsPanel.add(Box.createVerticalStrut(12));
        itemsPanel.add(airyamanButton);
        itemsPanel.add(Box.createVerticalStrut(12));
        itemsPanel.add(anahitaButton);
        itemsPanel.add(Box.createVerticalStrut(12));
        itemsPanel.add(speedLimiterButton);
        itemsPanel.add(Box.createVerticalStrut(12));
        itemsPanel.add(emergencyBrakeButton);

        JSeparator separator = new JSeparator(SwingConstants.HORIZONTAL);
        separator.setForeground(ITEM_BORDER_COLOR);
        separator.setBackground(DIALOG_BG_COLOR);
        itemsPanel.add(Box.createVerticalStrut(15));
        itemsPanel.add(separator);
        itemsPanel.add(Box.createVerticalStrut(15));

        itemsPanel.add(aergiaButton);
        itemsPanel.add(Box.createVerticalStrut(12));
        itemsPanel.add(sisyphusButton);
        itemsPanel.add(Box.createVerticalStrut(12));
        itemsPanel.add(eliphasButton);

        return itemsPanel;
    }

    private JPanel createBottomPanel() {
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        bottomPanel.setOpaque(false);
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(10,0,0,0));
        closeButton = new JButton("Close Store");
        styleButton(closeButton, CLOSE_BUTTON_BG_COLOR, CLOSE_BUTTON_BORDER_COLOR, new Font("Arial", Font.BOLD, 16));
        closeButton.addActionListener(e -> closeStoreAndResumeGame());
        bottomPanel.add(closeButton);
        return bottomPanel;
    }

    private String buildButtonHtml(String name, int cost, String description, String statusString) {
        String costString = "(Cost: " + cost + ")";
        return String.format(
                "<html><body style='width: 380px;'>" +
                        "<div style='font-family: Arial; font-size: %dpt; color: #%06X;'><b>%s</b> <span style='color: #%06X;'>%s</span></div>" +
                        "<div style='font-family: Arial; font-size: %dpt; color: #%06X; margin-top: 2px;'>%s</div>" +
                        (statusString.isEmpty() ? "" : "<div style='font-family: Arial; font-size: %dpt; color: #%06X; text-align: right;'><b>%s</b></div>") +
                        "</body></html>",
                ITEM_BUTTON_FONT_NAME.getSize(), ITEM_TEXT_COLOR_TITLE.getRGB() & 0xFFFFFF, name,
                ITEM_TEXT_COLOR_COST.getRGB() & 0xFFFFFF, costString,
                ITEM_BUTTON_FONT_DESC.getSize(), ITEM_TEXT_COLOR_DESC.getRGB() & 0xFFFFFF, description,
                ITEM_BUTTON_FONT_STATUS.getSize(), ITEM_TEXT_COLOR_ACTIVATED.getRGB() & 0xFFFFFF, statusString
        );
    }

    private JButton createItemButton(String name, int cost, String description) {
        JButton button = new JButton();
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setVerticalAlignment(SwingConstants.TOP);
        styleButton(button, ITEM_BG_COLOR, ITEM_BORDER_COLOR, null);
        Dimension prefSize = new Dimension(450, 75);
        button.setAlignmentX(Component.CENTER_ALIGNMENT);
        button.setPreferredSize(prefSize);
        button.setMaximumSize(prefSize);
        return button;
    }

    private void styleButton(JButton button, Color bgColor, Color borderColor, Font font) {
        if (font != null) {
            button.setFont(font);
        }
        button.setForeground(Color.WHITE);
        button.setBackground(bgColor);
        button.setFocusPainted(false);
        Border line = BorderFactory.createLineBorder(borderColor, 2);
        Border empty = BorderFactory.createEmptyBorder(10, 25, 10, 25);
        button.setBorder(BorderFactory.createCompoundBorder(line, empty));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));

        button.addMouseListener(new java.awt.event.MouseAdapter() {
            final Color originalColor = button.getBackground();
            final Color hoverColor = new Color(
                    Math.min(255, originalColor.getRed() + 20),
                    Math.min(255, originalColor.getGreen() + 20),
                    Math.min(255, originalColor.getBlue() + 20),
                    originalColor.getAlpha()
            );

            @Override
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                if (button.isEnabled()) {
                    button.setBackground(hoverColor);
                }
            }
            @Override
            public void mouseExited(java.awt.event.MouseEvent evt) {
                if (button.isEnabled()) {
                    button.setBackground(originalColor);
                }
            }
        });
    }

    private void purchaseItem(String itemName, int cost, JButton button) {
        if (gamePanel == null || !gamePanel.isGameRunning()) return;

        if ((itemName.equals(ITEM_ATAR_NAME) && gamePanel.isAtarActive()) ||
                (itemName.equals(ITEM_AIRYAMAN_NAME) && gamePanel.isAiryamanActive()) ||
                (itemName.equals(ITEM_SPEED_LIMITER_NAME) && gamePanel.isSpeedLimiterActive()) ||
                (itemName.equals(ITEM_ANAHITA_NAME) && anahitaPurchasedThisVisit) ||
                (itemName.equals(ITEM_EMERGENCY_BRAKE_NAME) && brakePurchasedThisVisit) ||
                (itemName.equals(ITEM_AERGIA_NAME) && gamePanel.isAergiaOnCooldown())) {
            return;
        }

        if (gameState.spendCoins(cost)) {
            logger.info("User purchased item '{}' for {} coins. Remaining coins: {}", itemName, cost, gameState.getCoins());
            if (!game.isMuted()) game.playSoundEffect("ui_confirm");
            updateCoinsDisplay(gameState.getCoins());

            switch (itemName) {
                case ITEM_ATAR_NAME: gamePanel.activateAtar(); break;
                case ITEM_AIRYAMAN_NAME: gamePanel.activateAiryaman(); break;
                case ITEM_ANAHITA_NAME:
                    gamePanel.activateAnahita();
                    anahitaPurchasedThisVisit = true;
                    break;
                case ITEM_SPEED_LIMITER_NAME:
                    gamePanel.activateSpeedLimiter();
                    break;
                case ITEM_EMERGENCY_BRAKE_NAME:
                    gamePanel.activateEmergencyBrake();
                    brakePurchasedThisVisit = true;
                    break;
                case ITEM_AERGIA_NAME:
                    gamePanel.enterAergiaPlacementMode();
                    setVisible(false);
                    return;
                case ITEM_SISYPHUS_NAME:
                    gamePanel.enterSisyphusDragMode();
                    setVisible(false);
                    return;
                case ITEM_ELIPHAS_NAME:
                    gamePanel.enterEliphasPlacementMode();
                    setVisible(false);
                    return;
            }
            flashButton(button, FLASH_COLOR_PURCHASE);
        } else {
            logger.warn("User failed to purchase item '{}'. Insufficient coins. Required: {}, Have: {}", itemName, cost, gameState.getCoins());
            if (!game.isMuted()) game.playSoundEffect("error");
            JOptionPane.showMessageDialog(this,
                    "Not enough coins for " + itemName + "!\nRequired: " + cost + ", Have: " + gameState.getCoins(),
                    "Purchase Failed",
                    JOptionPane.WARNING_MESSAGE);
        }
        updateButtonStates();
    }

    private void flashButton(JButton button, Color flashColor) {
        Color originalBg = button.getBackground();
        button.setBackground(flashColor);
        Timer flashTimer = new Timer(250, e -> {
            button.setBackground(originalBg);
            updateButtonStates();
        });
        flashTimer.setRepeats(false);
        flashTimer.start();
    }

    public void updateCoinsDisplay(int coins) {
        if (coinsLabel != null) {
            coinsLabel.setText("Coins: " + coins);
        }
    }

    private void updateButtonStates() {
        if (gamePanel == null) return;
        int currentCoins = gameState.getCoins();

        boolean atarActive = gamePanel.isAtarActive();
        atarButton.setText(buildButtonHtml(ITEM_ATAR_NAME, COST_ATAR, DESC_ATAR, atarActive ? "ACTIVE" : ""));
        atarButton.setEnabled(currentCoins >= COST_ATAR && !atarActive);
        atarButton.setBackground(atarActive ? ACTIVATED_BUTTON_BG_COLOR : (atarButton.isEnabled() ? ITEM_BG_COLOR : DISABLED_BUTTON_BG_COLOR));

        boolean airyamanActive = gamePanel.isAiryamanActive();
        airyamanButton.setText(buildButtonHtml(ITEM_AIRYAMAN_NAME, COST_AIRYAMAN, DESC_AIRYAMAN, airyamanActive ? "ACTIVE" : ""));
        airyamanButton.setEnabled(currentCoins >= COST_AIRYAMAN && !airyamanActive);
        airyamanButton.setBackground(airyamanActive ? ACTIVATED_BUTTON_BG_COLOR : (airyamanButton.isEnabled() ? ITEM_BG_COLOR : DISABLED_BUTTON_BG_COLOR));

        anahitaButton.setText(buildButtonHtml(ITEM_ANAHITA_NAME, COST_ANAHITA, DESC_ANAHITA, anahitaPurchasedThisVisit ? "ACTIVATED" : ""));
        anahitaButton.setEnabled(currentCoins >= COST_ANAHITA && !anahitaPurchasedThisVisit);
        anahitaButton.setBackground(anahitaPurchasedThisVisit ? ACTIVATED_BUTTON_BG_COLOR : (anahitaButton.isEnabled() ? ITEM_BG_COLOR : DISABLED_BUTTON_BG_COLOR));

        boolean speedLimiterActive = gamePanel.isSpeedLimiterActive();
        speedLimiterButton.setText(buildButtonHtml(ITEM_SPEED_LIMITER_NAME, COST_SPEED_LIMITER, DESC_SPEED_LIMITER, speedLimiterActive ? "ACTIVE" : ""));
        speedLimiterButton.setEnabled(currentCoins >= COST_SPEED_LIMITER && !speedLimiterActive);
        speedLimiterButton.setBackground(speedLimiterActive ? ACTIVATED_BUTTON_BG_COLOR : (speedLimiterButton.isEnabled() ? ITEM_BG_COLOR : DISABLED_BUTTON_BG_COLOR));

        emergencyBrakeButton.setText(buildButtonHtml(ITEM_EMERGENCY_BRAKE_NAME, COST_EMERGENCY_BRAKE, DESC_EMERGENCY_BRAKE, brakePurchasedThisVisit ? "ACTIVATED" : ""));
        emergencyBrakeButton.setEnabled(currentCoins >= COST_EMERGENCY_BRAKE && !brakePurchasedThisVisit);
        emergencyBrakeButton.setBackground(brakePurchasedThisVisit ? ACTIVATED_BUTTON_BG_COLOR : (emergencyBrakeButton.isEnabled() ? ITEM_BG_COLOR : DISABLED_BUTTON_BG_COLOR));

        boolean aergiaOnCooldown = gamePanel.isAergiaOnCooldown();
        aergiaButton.setText(buildButtonHtml(ITEM_AERGIA_NAME, COST_AERGIA, DESC_AERGIA, aergiaOnCooldown ? "ON COOLDOWN" : ""));
        aergiaButton.setEnabled(currentCoins >= COST_AERGIA && !aergiaOnCooldown);
        aergiaButton.setBackground(aergiaOnCooldown ? DISABLED_BUTTON_BG_COLOR : (aergiaButton.isEnabled() ? ITEM_BG_COLOR : DISABLED_BUTTON_BG_COLOR));

        sisyphusButton.setText(buildButtonHtml(ITEM_SISYPHUS_NAME, COST_SISYPHUS, DESC_SISYPHUS, ""));
        sisyphusButton.setEnabled(currentCoins >= COST_SISYPHUS);
        sisyphusButton.setBackground(sisyphusButton.isEnabled() ? ITEM_BG_COLOR : DISABLED_BUTTON_BG_COLOR);

        eliphasButton.setText(buildButtonHtml(ITEM_ELIPHAS_NAME, COST_ELIPHAS, DESC_ELIPHAS, ""));
        eliphasButton.setEnabled(currentCoins >= COST_ELIPHAS);
        eliphasButton.setBackground(eliphasButton.isEnabled() ? ITEM_BG_COLOR : DISABLED_BUTTON_BG_COLOR);

        if (isShowing()) {
            repaint();
        }
    }

    private void closeStoreAndResumeGame() {
        logger.debug("Closing store and resuming game.");
        setVisible(false);
        if (gamePanel != null && gamePanel.isGameRunning() && gamePanel.isGamePaused()) {
            gamePanel.pauseGame(false);
            gamePanel.requestFocusInWindow();
        }
    }

    @Override
    public void setVisible(boolean b) {
        if (b) {
            logger.debug("Store panel is being made visible.");
            if(gameState == null || gamePanel == null){
                logger.error("Cannot show store, GameState or GamePanel is null.");
                super.setVisible(false);
                return;
            }
            anahitaPurchasedThisVisit = false;
            brakePurchasedThisVisit = false;
            try {
                updateCoinsDisplay(gameState.getCoins());
                updateButtonStates();
                setLocationRelativeTo(getOwner());
            } catch (Exception e) {
                logger.error("Exception preparing store display.", e);
                super.setVisible(false);
                JOptionPane.showMessageDialog(this.game, "Error preparing store display.", "Store Error", JOptionPane.ERROR_MESSAGE);
                if (gamePanel.isGamePaused()) {
                    gamePanel.pauseGame(false);
                    gamePanel.requestFocusInWindow();
                }
                return;
            }
        }
        super.setVisible(b);
    }
}
