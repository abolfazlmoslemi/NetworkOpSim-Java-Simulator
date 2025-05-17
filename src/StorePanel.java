// FILE: StorePanel.java
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
    private static final Font ITEM_DESC_FONT = new Font("Arial", Font.PLAIN, 14);
    private static final Color ITEM_TEXT_COLOR_TITLE = Color.WHITE;
    private static final Color ITEM_TEXT_COLOR_DESC = new Color(204, 204, 204);
    private static final Color DISABLED_BUTTON_BG_COLOR = Color.DARK_GRAY.darker();
    private static final Color FLASH_COLOR = Color.GREEN.darker();

    public StorePanel(NetworkGame owner, GamePanel gamePanel) {
        super(owner, "Game Store", true);
        this.game = Objects.requireNonNull(owner, "NetworkGame owner cannot be null");
        this.gamePanel = Objects.requireNonNull(gamePanel, "GamePanel cannot be null");
        this.gameState = Objects.requireNonNull(owner.getGameState(), "GameState cannot be null");
        setSize(550, 450);
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
        JPanel itemsPanel = createItemsPanel();
        mainPanel.add(itemsPanel, BorderLayout.CENTER);
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
        itemsPanel.setOpaque(false);
        itemsPanel.setBorder(BorderFactory.createEmptyBorder(20, 0, 20, 0));
        atarButton = createItemButton(ITEM_ATAR_NAME, COST_ATAR, DESC_ATAR);
        airyamanButton = createItemButton(ITEM_AIRYAMAN_NAME, COST_AIRYAMAN, DESC_AIRYAMAN);
        anahitaButton = createItemButton(ITEM_ANAHITA_NAME, COST_ANAHITA, DESC_ANAHITA);
        atarButton.addActionListener(e -> purchaseItem(ITEM_ATAR_NAME, COST_ATAR, atarButton));
        airyamanButton.addActionListener(e -> purchaseItem(ITEM_AIRYAMAN_NAME, COST_AIRYAMAN, airyamanButton));
        anahitaButton.addActionListener(e -> purchaseItem(ITEM_ANAHITA_NAME, COST_ANAHITA, anahitaButton));
        itemsPanel.add(atarButton);
        itemsPanel.add(Box.createVerticalStrut(15));
        itemsPanel.add(airyamanButton);
        itemsPanel.add(Box.createVerticalStrut(15));
        itemsPanel.add(anahitaButton);
        return itemsPanel;
    }
    private JPanel createBottomPanel() {
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        bottomPanel.setOpaque(false);
        closeButton = new JButton("Close Store");
        styleCloseButton(closeButton, CLOSE_BUTTON_BG_COLOR, CLOSE_BUTTON_BORDER_COLOR);
        closeButton.addActionListener(e -> closeStoreAndResumeGame());
        bottomPanel.add(closeButton);
        return bottomPanel;
    }
    private JButton createItemButton(String name, int cost, String description) {
        String htmlContent = String.format(
                "<html><body style='width: 350px; text-align: left; padding: 5px;'>" +
                        "<div style='font-size: 11pt; color: #%06X;'><b>%s</b> (Cost: %d)</div>" +
                        "<p style='font-size: 9pt; color: #%06X; margin-top: 4px;'>%s</p>" +
                        "</body></html>",
                ITEM_TEXT_COLOR_TITLE.getRGB() & 0xFFFFFF,
                name, cost,
                ITEM_TEXT_COLOR_DESC.getRGB() & 0xFFFFFF,
                description
        );
        JButton button = new JButton(htmlContent);
        button.setFont(ITEM_DESC_FONT);
        button.setHorizontalAlignment(SwingConstants.LEFT);
        styleItemButton(button, ITEM_BG_COLOR, ITEM_BORDER_COLOR);
        return button;
    }
    private void styleItemButton(JButton button, Color bgColor, Color borderColor) {
        button.setBackground(bgColor);
        button.setFocusPainted(false);
        Border line = BorderFactory.createLineBorder(borderColor, 1);
        Border empty = BorderFactory.createEmptyBorder(10, 15, 10, 15);
        button.setBorder(BorderFactory.createCompoundBorder(line, empty));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        Dimension prefSize = new Dimension(450, 70);
        button.setAlignmentX(Component.CENTER_ALIGNMENT);
        button.setMaximumSize(prefSize);
        button.setPreferredSize(prefSize);
        button.addMouseListener(new MouseAdapter() {
            final Color originalColor = button.getBackground();
            final Color hoverColor = originalColor.brighter();
            @Override
            public void mouseEntered(MouseEvent e) {
                if (button.isEnabled()) {
                    button.setBackground(hoverColor);
                } else {
                    button.setBackground(DISABLED_BUTTON_BG_COLOR);
                }
            }
            @Override
            public void mouseExited(MouseEvent e) {
                if (button.isEnabled()) {
                    button.setBackground(originalColor);
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
        if (gamePanel == null || !gamePanel.isGameRunning()) { // Check if game is running (not just paused)
            java.lang.System.err.println("Purchase attempt failed: GamePanel not running or null.");
            JOptionPane.showMessageDialog(this, "Cannot purchase items now.\nGame is not running or paused.", "Store Unavailable", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (gameState.spendCoins(cost)) {
            updateCoins(gameState.getCoins());
            // game.playSoundEffect("purchase"); // REMOVED
            boolean activationSuccess = false;
            switch (itemName) {
                case ITEM_ATAR_NAME:
                    gamePanel.activateAtar(); // Power-up activation sounds also removed from GamePanel
                    activationSuccess = true;
                    break;
                case ITEM_AIRYAMAN_NAME:
                    gamePanel.activateAiryaman();
                    activationSuccess = true;
                    break;
                case ITEM_ANAHITA_NAME:
                    gamePanel.activateAnahita();
                    activationSuccess = true;
                    break;
                default:
                    java.lang.System.err.println("ERROR: Attempted to purchase unknown item: " + itemName);
                    gameState.addCoins(cost); // Return coins if item is unknown
                    updateCoins(gameState.getCoins());
                    activationSuccess = false;
            }
            if (activationSuccess) {
                flashButton(button, FLASH_COLOR);
                java.lang.System.out.println("Purchased item: " + itemName + " for " + cost + " coins.");
            }
        } else {
            // game.playSoundEffect("error"); // REMOVED or use a generic UI error if kept
            JOptionPane.showMessageDialog(this,
                    "Not enough coins for " + itemName + "!\nRequired: " + cost + ", Have: " + gameState.getCoins(),
                    "Purchase Failed",
                    JOptionPane.WARNING_MESSAGE);
        }
        updateButtonStates();
    }
    private void flashButton(JButton button, Color flashColor) {
        Color originalBg = button.isEnabled() ? ITEM_BG_COLOR : DISABLED_BUTTON_BG_COLOR;
        // Color currentBg = button.getBackground(); // Not needed
        button.setBackground(flashColor);
        Timer flashTimer = new Timer(300, e -> {
            if (button.isEnabled()) {
                Point mousePos = MouseInfo.getPointerInfo().getLocation();
                SwingUtilities.convertPointFromScreen(mousePos, button);
                if (button.contains(mousePos)) {
                    button.setBackground(ITEM_BG_COLOR.brighter());
                } else {
                    button.setBackground(ITEM_BG_COLOR);
                }
            } else {
                button.setBackground(DISABLED_BUTTON_BG_COLOR);
            }
        });
        flashTimer.setRepeats(false);
        flashTimer.start();
    }
    public void updateCoins(int coins) {
        if (coinsLabel != null) {
            coinsLabel.setText("Coins: " + coins);
        }
        updateButtonStates();
    }
    private void updateButtonStates() {
        int currentCoins = gameState.getCoins();
        atarButton.setEnabled(currentCoins >= COST_ATAR);
        airyamanButton.setEnabled(currentCoins >= COST_AIRYAMAN);
        anahitaButton.setEnabled(currentCoins >= COST_ANAHITA);
        atarButton.setBackground(atarButton.isEnabled() ? ITEM_BG_COLOR : DISABLED_BUTTON_BG_COLOR);
        airyamanButton.setBackground(airyamanButton.isEnabled() ? ITEM_BG_COLOR : DISABLED_BUTTON_BG_COLOR);
        anahitaButton.setBackground(anahitaButton.isEnabled() ? ITEM_BG_COLOR : DISABLED_BUTTON_BG_COLOR);
        repaint();
    }
    private void closeStoreAndResumeGame() {
        setVisible(false);
        if (gamePanel != null && gamePanel.isGameRunning() && gamePanel.isGamePaused()) {
            java.lang.System.out.println("Store closed, resuming game.");
            gamePanel.pauseGame(false);
        } else {
            java.lang.System.out.println("Store closed, game was not running or not paused appropriately.");
        }
    }
    @Override
    public void setVisible(boolean b) {
        if (b) {
            java.lang.System.out.println("Store opened. Coins: " + gameState.getCoins());
            updateCoins(gameState.getCoins());
            setLocationRelativeTo(getOwner()); // Ensure it's centered each time
        }
        super.setVisible(b);
    }
}