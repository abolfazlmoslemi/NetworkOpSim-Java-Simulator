// ===== File: StorePanel.java =====

package com.networkopsim.game;
import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
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

    private static final Color DIALOG_BG_COLOR = new Color(45, 45, 55);
    private static final Color ITEM_BG_COLOR = new Color(70, 70, 85);
    private static final Color ITEM_BORDER_COLOR = new Color(100, 100, 120);
    private static final Color CLOSE_BUTTON_BG_COLOR = new Color(180, 80, 80);
    private static final Color CLOSE_BUTTON_BORDER_COLOR = new Color(130, 50, 50);
    private static final Font TITLE_FONT = new Font("Arial", Font.BOLD, 26);
    private static final Color TITLE_COLOR = Color.CYAN;
    private static final Font COIN_FONT = new Font("Arial", Font.BOLD, 18);
    private static final Color COIN_COLOR = Color.YELLOW;

    private static final Font ITEM_BUTTON_FONT_NAME = new Font("Arial", Font.BOLD, 13); // Slightly smaller for safety
    private static final Font ITEM_BUTTON_FONT_DESC = new Font("Arial", Font.PLAIN, 10); // Slightly smaller
    private static final Font ITEM_BUTTON_FONT_STATUS = new Font("Arial", Font.BOLD, 10); // Slightly smaller


    private static final Color ITEM_TEXT_COLOR_TITLE = Color.WHITE;
    private static final Color ITEM_TEXT_COLOR_DESC = new Color(204, 204, 204);
    private static final Color ITEM_TEXT_COLOR_COST = Color.ORANGE;
    private static final Color ITEM_TEXT_COLOR_ACTIVATED = new Color(170, 255, 170);


    private static final Color DISABLED_BUTTON_BG_COLOR = Color.DARK_GRAY.darker();
    private static final Color ACTIVATED_BUTTON_BG_COLOR = new Color(30, 100, 50);
    private static final Color FLASH_COLOR_PURCHASE = Color.GREEN.darker();

    private boolean anahitaPurchasedThisVisit = false;


    public StorePanel(NetworkGame owner, GamePanel gamePanel) {
        super(owner, "Game Store", true);
        this.game = Objects.requireNonNull(owner, "NetworkGame owner cannot be null");
        this.gamePanel = Objects.requireNonNull(gamePanel, "GamePanel cannot be null");
        this.gameState = Objects.requireNonNull(owner.getGameState(), "GameState cannot be null");
        setSize(550, 480);
        setLocationRelativeTo(owner);
        setResizable(false);
        setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE); // Keep this
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                java.lang.System.out.println("StorePanel: windowClosing event triggered.");
                closeStoreAndResumeGame();
            }
        });

        try {
            JPanel mainPanel = new JPanel(new BorderLayout(15, 15));
            mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 25, 20, 25));
            mainPanel.setBackground(DIALOG_BG_COLOR);
            JPanel topPanel = createTopPanel();
            mainPanel.add(topPanel, BorderLayout.NORTH);
            JPanel itemsPanel = createItemsPanel();
            mainPanel.add(itemsPanel, BorderLayout.CENTER);
            JPanel bottomPanel = createBottomPanel();
            mainPanel.add(bottomPanel, BorderLayout.SOUTH);
            setContentPane(mainPanel);
            java.lang.System.out.println("StorePanel: Constructor finished successfully.");
        } catch (Exception e) {
            java.lang.System.err.println("Error during StorePanel construction: " + e.getMessage());
            e.printStackTrace();
            // Optionally, re-throw or handle to prevent store from being used if construction fails
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
        itemsPanel.setOpaque(false);
        itemsPanel.setBorder(BorderFactory.createEmptyBorder(20, 0, 10, 0));

        atarButton = createItemButton(ITEM_ATAR_NAME, COST_ATAR, DESC_ATAR);
        airyamanButton = createItemButton(ITEM_AIRYAMAN_NAME, COST_AIRYAMAN, DESC_AIRYAMAN);
        anahitaButton = createItemButton(ITEM_ANAHITA_NAME, COST_ANAHITA, DESC_ANAHITA);

        atarButton.addActionListener(e -> purchaseItem(ITEM_ATAR_NAME, COST_ATAR, atarButton));
        airyamanButton.addActionListener(e -> purchaseItem(ITEM_AIRYAMAN_NAME, COST_AIRYAMAN, airyamanButton));
        anahitaButton.addActionListener(e -> purchaseItem(ITEM_ANAHITA_NAME, COST_ANAHITA, anahitaButton));

        itemsPanel.add(atarButton);
        itemsPanel.add(Box.createVerticalStrut(12));
        itemsPanel.add(airyamanButton);
        itemsPanel.add(Box.createVerticalStrut(12));
        itemsPanel.add(anahitaButton);
        return itemsPanel;
    }
    private JPanel createBottomPanel() {
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        bottomPanel.setOpaque(false);
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(10,0,0,0));
        closeButton = new JButton("Close Store");
        styleCloseButton(closeButton, CLOSE_BUTTON_BG_COLOR, CLOSE_BUTTON_BORDER_COLOR);
        closeButton.addActionListener(e -> {
            java.lang.System.out.println("StorePanel: Close button clicked.");
            closeStoreAndResumeGame();
        });
        bottomPanel.add(closeButton);
        return bottomPanel;
    }

    // Simplified HTML structure for debugging
    private String buildButtonHtml(String name, int cost, String description, boolean isActive, boolean isPurchasedThisVisit) {
        String costString = "(Cost: " + cost + ")";
        String statusString = "";

        if (isActive) {
            statusString = "ACTIVE";
        } else if (isPurchasedThisVisit && name.equals(ITEM_ANAHITA_NAME)) {
            statusString = "ACTIVATED";
        }

        // Basic div structure, less prone to complex HTML rendering issues
        return String.format(
                "<html><body style='width: 380px;'>" + // Adjusted width
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
        styleItemButton(button, ITEM_BG_COLOR, ITEM_BORDER_COLOR);
        return button;
    }


    private void styleItemButton(JButton button, Color bgColor, Color borderColor) {
        button.setBackground(bgColor);
        button.setFocusPainted(false);
        Border line = BorderFactory.createLineBorder(borderColor, 1);
        Border empty = BorderFactory.createEmptyBorder(8, 12, 8, 12);
        button.setBorder(BorderFactory.createCompoundBorder(line, empty));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        Dimension prefSize = new Dimension(450, 70); // Slightly reduced height
        button.setAlignmentX(Component.CENTER_ALIGNMENT);
        button.setPreferredSize(prefSize);
        button.setMaximumSize(prefSize);
        // button.setMinimumSize(prefSize); // Temporarily removed for debugging layout issues

        button.addMouseListener(new MouseAdapter() {
            Color originalColor;
            Color hoverColor;

            @Override
            public void mouseEntered(MouseEvent e) {
                if (button.isEnabled()) {
                    originalColor = button.getBackground();
                    if (originalColor.equals(DISABLED_BUTTON_BG_COLOR)) return;

                    int r = Math.min(255, originalColor.getRed() + 20);
                    int g = Math.min(255, originalColor.getGreen() + 20);
                    int b = Math.min(255, originalColor.getBlue() + 20);
                    hoverColor = new Color(r,g,b, originalColor.getAlpha());
                    button.setBackground(hoverColor);
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (button.isEnabled()) {
                    boolean isAtar = button == atarButton;
                    boolean isAiryaman = button == airyamanButton;
                    boolean isAnahita = button == anahitaButton;

                    if ((isAtar && gamePanel.isAtarActive()) || (isAiryaman && gamePanel.isAiryamanActive()) || (isAnahita && anahitaPurchasedThisVisit)) {
                        button.setBackground(ACTIVATED_BUTTON_BG_COLOR);
                    } else {
                        button.setBackground(ITEM_BG_COLOR);
                    }
                } else {
                    button.setBackground(DISABLED_BUTTON_BG_COLOR);
                }
            }
        });
    }
    private void styleCloseButton(JButton button, Color bgColor, Color borderColor) {
        button.setForeground(Color.WHITE);
        button.setFont(new Font("Arial", Font.BOLD, 16));
        button.setBackground(bgColor);
        button.setFocusPainted(false);
        Border line = BorderFactory.createLineBorder(borderColor, 2);
        Border empty = BorderFactory.createEmptyBorder(10, 25, 10, 25);
        button.setBorder(BorderFactory.createCompoundBorder(line, empty));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.addMouseListener(new MouseAdapter() {
            final Color originalColor = button.getBackground();
            final Color hoverColor = originalColor.brighter();
            @Override
            public void mouseEntered(MouseEvent e) {
                button.setBackground(hoverColor);
            }
            @Override
            public void mouseExited(MouseEvent e) {
                button.setBackground(originalColor);
            }
        });
    }
    private void purchaseItem(String itemName, int cost, JButton button) {
        if (gamePanel == null || !gamePanel.isGameRunning()) {
            java.lang.System.err.println("Purchase attempt failed: GamePanel not running or null.");
            JOptionPane.showMessageDialog(this, "Cannot purchase items now.\nGame is not running or paused.", "Store Unavailable", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (itemName.equals(ITEM_ATAR_NAME) && gamePanel.isAtarActive()) return;
        if (itemName.equals(ITEM_AIRYAMAN_NAME) && gamePanel.isAiryamanActive()) return;
        if (itemName.equals(ITEM_ANAHITA_NAME) && anahitaPurchasedThisVisit) return;


        if (gameState.spendCoins(cost)) {
            updateCoinsDisplay(gameState.getCoins());
            boolean activationSuccess = false;
            switch (itemName) {
                case ITEM_ATAR_NAME:
                    gamePanel.activateAtar();
                    activationSuccess = true;
                    break;
                case ITEM_AIRYAMAN_NAME:
                    gamePanel.activateAiryaman();
                    activationSuccess = true;
                    break;
                case ITEM_ANAHITA_NAME:
                    gamePanel.activateAnahita();
                    anahitaPurchasedThisVisit = true;
                    activationSuccess = true;
                    break;
                default:
                    java.lang.System.err.println("ERROR: Attempted to purchase unknown item: " + itemName);
                    gameState.addCoins(cost);
                    updateCoinsDisplay(gameState.getCoins());
                    activationSuccess = false;
            }
            if (activationSuccess) {
                flashButton(button, FLASH_COLOR_PURCHASE);
                java.lang.System.out.println("Purchased item: " + itemName + " for " + cost + " coins.");
            }
        } else {
            JOptionPane.showMessageDialog(this,
                    "Not enough coins for " + itemName + "!\nRequired: " + cost + ", Have: " + gameState.getCoins(),
                    "Purchase Failed",
                    JOptionPane.WARNING_MESSAGE);
        }
        updateButtonStates();
    }
    private void flashButton(JButton button, Color flashColor) {
        Color normalBg;
        boolean isAtar = button == atarButton;
        boolean isAiryaman = button == airyamanButton;
        boolean isAnahita = button == anahitaButton;

        if (!button.isEnabled()) {
            normalBg = DISABLED_BUTTON_BG_COLOR;
        } else if ((isAtar && gamePanel.isAtarActive()) ||
                (isAiryaman && gamePanel.isAiryamanActive()) ||
                (isAnahita && anahitaPurchasedThisVisit)) {
            normalBg = ACTIVATED_BUTTON_BG_COLOR;
        } else {
            normalBg = ITEM_BG_COLOR;
        }

        button.setBackground(flashColor);
        Timer flashTimer = new Timer(250, e -> {
            Point mousePosOnScreen = MouseInfo.getPointerInfo().getLocation();
            if(mousePosOnScreen == null) { // Headless environment or other issue
                button.setBackground(normalBg); // Fallback
                return;
            }
            Point mousePosOnButton = new Point(mousePosOnScreen);
            SwingUtilities.convertPointFromScreen(mousePosOnButton, button);

            if (button.isEnabled()) {
                if (button.contains(mousePosOnButton)) {
                    int r = Math.min(255, normalBg.getRed() + 20);
                    int g = Math.min(255, normalBg.getGreen() + 20);
                    int b = Math.min(255, normalBg.getBlue() + 20);
                    button.setBackground(new Color(r,g,b, normalBg.getAlpha()));
                } else {
                    button.setBackground(normalBg);
                }
            } else {
                button.setBackground(DISABLED_BUTTON_BG_COLOR);
            }
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
        if (gamePanel == null) {
            java.lang.System.err.println("StorePanel.updateButtonStates: gamePanel is null!");
            return;
        }
        if(atarButton == null || airyamanButton == null || anahitaButton == null){
            java.lang.System.err.println("StorePanel.updateButtonStates: One or more item buttons are null!");
            return;
        }


        int currentCoins = gameState.getCoins();

        boolean atarAlreadyActive = gamePanel.isAtarActive();
        atarButton.setText(buildButtonHtml(ITEM_ATAR_NAME, COST_ATAR, DESC_ATAR, atarAlreadyActive, false));
        atarButton.setEnabled(currentCoins >= COST_ATAR && !atarAlreadyActive);
        atarButton.setBackground(atarAlreadyActive ? ACTIVATED_BUTTON_BG_COLOR : (atarButton.isEnabled() ? ITEM_BG_COLOR : DISABLED_BUTTON_BG_COLOR));

        boolean airyamanAlreadyActive = gamePanel.isAiryamanActive();
        airyamanButton.setText(buildButtonHtml(ITEM_AIRYAMAN_NAME, COST_AIRYAMAN, DESC_AIRYAMAN, airyamanAlreadyActive, false));
        airyamanButton.setEnabled(currentCoins >= COST_AIRYAMAN && !airyamanAlreadyActive);
        airyamanButton.setBackground(airyamanAlreadyActive ? ACTIVATED_BUTTON_BG_COLOR : (airyamanButton.isEnabled() ? ITEM_BG_COLOR : DISABLED_BUTTON_BG_COLOR));

        anahitaButton.setText(buildButtonHtml(ITEM_ANAHITA_NAME, COST_ANAHITA, DESC_ANAHITA, false, anahitaPurchasedThisVisit));
        anahitaButton.setEnabled(currentCoins >= COST_ANAHITA && !anahitaPurchasedThisVisit);
        anahitaButton.setBackground(anahitaPurchasedThisVisit ? ACTIVATED_BUTTON_BG_COLOR : (anahitaButton.isEnabled() ? ITEM_BG_COLOR : DISABLED_BUTTON_BG_COLOR));

        if (isShowing()) {
            //java.lang.System.out.println("StorePanel: repaint called in updateButtonStates.");
            repaint();
        }
    }

    private void closeStoreAndResumeGame() {
        setVisible(false); // This should trigger windowClosed or windowClosing if not already handled
        if (gamePanel != null && gamePanel.isGameRunning() && gamePanel.isGamePaused()) {
            java.lang.System.out.println("StorePanel: Resuming game.");
            gamePanel.pauseGame(false);
            gamePanel.requestFocusInWindow();
        } else {
            java.lang.System.out.println("StorePanel: Game not in expected state to resume.");
        }
    }
    @Override
    public void setVisible(boolean b) {
        java.lang.System.out.println("StorePanel: setVisible called with: " + b);
        if (b) {
            if(gameState == null || gamePanel == null){
                java.lang.System.err.println("StorePanel.setVisible(true): gameState or gamePanel is null! Cannot show store.");
                super.setVisible(false);
                return;
            }
            java.lang.System.out.println("StorePanel: Opening store. Current Coins: " + gameState.getCoins());
            anahitaPurchasedThisVisit = false;
            try {
                updateCoinsDisplay(gameState.getCoins());
                updateButtonStates();
                setLocationRelativeTo(getOwner());
            } catch (Exception e) {
                java.lang.System.err.println("Error in StorePanel.setVisible(true) during UI update: " + e.getMessage());
                e.printStackTrace();
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
        if (b) {
            java.lang.System.out.println("StorePanel: Successfully made visible.");
        } else {
            java.lang.System.out.println("StorePanel: Successfully hidden.");
        }
    }
}