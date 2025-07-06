// ===== File: StorePanel.java =====

package com.networkopsim.game;
import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Objects;

public class StorePanel extends JDialog {
    private final NetworkGame game;
    private final GamePanel gamePanel;
    private final GameState gameState;
    private JLabel coinsLabel;
    private JButton atarButton;
    private JButton airyamanButton;
    private JButton anahitaButton;
    private JButton speedLimiterButton; // NEW
    private JButton emergencyBrakeButton; // NEW
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

    // --- NEW Item Definitions ---
    private static final String ITEM_SPEED_LIMITER_NAME = "Speed Limiter";
    private static final int COST_SPEED_LIMITER = 7;
    private static final String DESC_SPEED_LIMITER = "Temporarily prevents packets from accelerating for 15 seconds.";
    private static final String ITEM_EMERGENCY_BRAKE_NAME = "Emergency Brake";
    private static final int COST_EMERGENCY_BRAKE = 8;
    private static final String DESC_EMERGENCY_BRAKE = "Instantly resets the speed of all active packets to their base speed.";


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
    private boolean brakePurchasedThisVisit = false; // NEW


    public StorePanel(NetworkGame owner, GamePanel gamePanel) {
        super(owner, "Game Store", true);
        this.game = Objects.requireNonNull(owner, "NetworkGame owner cannot be null");
        this.gamePanel = Objects.requireNonNull(gamePanel, "GamePanel cannot be null");
        this.gameState = Objects.requireNonNull(owner.getGameState(), "GameState cannot be null");
        setSize(550, 600); // Increased height for new items
        setLocationRelativeTo(owner);
        setResizable(false);
        setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE); // Keep this
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                closeStoreAndResumeGame();
            }
        });

        try {
            JPanel mainPanel = new JPanel(new BorderLayout(15, 15));
            mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 25, 20, 25));
            mainPanel.setBackground(DIALOG_BG_COLOR);
            JPanel topPanel = createTopPanel();
            mainPanel.add(topPanel, BorderLayout.NORTH);
            JScrollPane itemsScrollPane = new JScrollPane(createItemsPanel()); // Put items in a scroll pane
            itemsScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
            itemsScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            itemsScrollPane.setBorder(null);
            itemsScrollPane.getViewport().setBackground(DIALOG_BG_COLOR);
            mainPanel.add(itemsScrollPane, BorderLayout.CENTER);
            JPanel bottomPanel = createBottomPanel();
            mainPanel.add(bottomPanel, BorderLayout.SOUTH);
            setContentPane(mainPanel);
        } catch (Exception e) {
            java.lang.System.err.println("Error during StorePanel construction: " + e.getMessage());
            e.printStackTrace();
        }
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
        speedLimiterButton = createItemButton(ITEM_SPEED_LIMITER_NAME, COST_SPEED_LIMITER, DESC_SPEED_LIMITER); // NEW
        emergencyBrakeButton = createItemButton(ITEM_EMERGENCY_BRAKE_NAME, COST_EMERGENCY_BRAKE, DESC_EMERGENCY_BRAKE); // NEW

        atarButton.addActionListener(e -> purchaseItem(ITEM_ATAR_NAME, COST_ATAR, atarButton));
        airyamanButton.addActionListener(e -> purchaseItem(ITEM_AIRYAMAN_NAME, COST_AIRYAMAN, airyamanButton));
        anahitaButton.addActionListener(e -> purchaseItem(ITEM_ANAHITA_NAME, COST_ANAHITA, anahitaButton));
        speedLimiterButton.addActionListener(e -> purchaseItem(ITEM_SPEED_LIMITER_NAME, COST_SPEED_LIMITER, speedLimiterButton)); // NEW
        emergencyBrakeButton.addActionListener(e -> purchaseItem(ITEM_EMERGENCY_BRAKE_NAME, COST_EMERGENCY_BRAKE, emergencyBrakeButton)); // NEW

        itemsPanel.add(atarButton);
        itemsPanel.add(Box.createVerticalStrut(12));
        itemsPanel.add(airyamanButton);
        itemsPanel.add(Box.createVerticalStrut(12));
        itemsPanel.add(anahitaButton);
        itemsPanel.add(Box.createVerticalStrut(12)); // NEW
        itemsPanel.add(speedLimiterButton); // NEW
        itemsPanel.add(Box.createVerticalStrut(12)); // NEW
        itemsPanel.add(emergencyBrakeButton); // NEW

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

    private String buildButtonHtml(String name, int cost, String description, boolean isActive, boolean isPurchasedThisVisit) {
        String costString = "(Cost: " + cost + ")";
        String statusString = "";

        if (isActive) {
            statusString = "ACTIVE";
        } else if (isPurchasedThisVisit && (name.equals(ITEM_ANAHITA_NAME) || name.equals(ITEM_EMERGENCY_BRAKE_NAME)) ) {
            statusString = "ACTIVATED";
        }

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
        Dimension prefSize = new Dimension(450, 70);
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
        if (gamePanel == null || !gamePanel.isGameRunning()) {
            JOptionPane.showMessageDialog(this, "Cannot purchase items now.\nGame is not running or paused.", "Store Unavailable", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if ( (itemName.equals(ITEM_ATAR_NAME) && gamePanel.isAtarActive()) ||
                (itemName.equals(ITEM_AIRYAMAN_NAME) && gamePanel.isAiryamanActive()) ||
                (itemName.equals(ITEM_SPEED_LIMITER_NAME) && gamePanel.isSpeedLimiterActive()) ||
                (itemName.equals(ITEM_ANAHITA_NAME) && anahitaPurchasedThisVisit) ||
                (itemName.equals(ITEM_EMERGENCY_BRAKE_NAME) && brakePurchasedThisVisit)
        ) return;

        if (gameState.spendCoins(cost)) {
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
            }
            flashButton(button, FLASH_COLOR_PURCHASE);
        } else {
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
            updateButtonStates(); // Re-evaluate the correct background color
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
        atarButton.setText(buildButtonHtml(ITEM_ATAR_NAME, COST_ATAR, DESC_ATAR, atarActive, false));
        atarButton.setEnabled(currentCoins >= COST_ATAR && !atarActive);
        atarButton.setBackground(atarActive ? ACTIVATED_BUTTON_BG_COLOR : (atarButton.isEnabled() ? ITEM_BG_COLOR : DISABLED_BUTTON_BG_COLOR));

        boolean airyamanActive = gamePanel.isAiryamanActive();
        airyamanButton.setText(buildButtonHtml(ITEM_AIRYAMAN_NAME, COST_AIRYAMAN, DESC_AIRYAMAN, airyamanActive, false));
        airyamanButton.setEnabled(currentCoins >= COST_AIRYAMAN && !airyamanActive);
        airyamanButton.setBackground(airyamanActive ? ACTIVATED_BUTTON_BG_COLOR : (airyamanButton.isEnabled() ? ITEM_BG_COLOR : DISABLED_BUTTON_BG_COLOR));

        anahitaButton.setText(buildButtonHtml(ITEM_ANAHITA_NAME, COST_ANAHITA, DESC_ANAHITA, false, anahitaPurchasedThisVisit));
        anahitaButton.setEnabled(currentCoins >= COST_ANAHITA && !anahitaPurchasedThisVisit);
        anahitaButton.setBackground(anahitaPurchasedThisVisit ? ACTIVATED_BUTTON_BG_COLOR : (anahitaButton.isEnabled() ? ITEM_BG_COLOR : DISABLED_BUTTON_BG_COLOR));

        boolean speedLimiterActive = gamePanel.isSpeedLimiterActive();
        speedLimiterButton.setText(buildButtonHtml(ITEM_SPEED_LIMITER_NAME, COST_SPEED_LIMITER, DESC_SPEED_LIMITER, speedLimiterActive, false));
        speedLimiterButton.setEnabled(currentCoins >= COST_SPEED_LIMITER && !speedLimiterActive);
        speedLimiterButton.setBackground(speedLimiterActive ? ACTIVATED_BUTTON_BG_COLOR : (speedLimiterButton.isEnabled() ? ITEM_BG_COLOR : DISABLED_BUTTON_BG_COLOR));

        emergencyBrakeButton.setText(buildButtonHtml(ITEM_EMERGENCY_BRAKE_NAME, COST_EMERGENCY_BRAKE, DESC_EMERGENCY_BRAKE, false, brakePurchasedThisVisit));
        emergencyBrakeButton.setEnabled(currentCoins >= COST_EMERGENCY_BRAKE && !brakePurchasedThisVisit);
        emergencyBrakeButton.setBackground(brakePurchasedThisVisit ? ACTIVATED_BUTTON_BG_COLOR : (emergencyBrakeButton.isEnabled() ? ITEM_BG_COLOR : DISABLED_BUTTON_BG_COLOR));

        if (isShowing()) {
            repaint();
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
            if(gameState == null || gamePanel == null){
                super.setVisible(false);
                return;
            }
            anahitaPurchasedThisVisit = false;
            brakePurchasedThisVisit = false; // NEW
            try {
                updateCoinsDisplay(gameState.getCoins());
                updateButtonStates();
                setLocationRelativeTo(getOwner());
            } catch (Exception e) {
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