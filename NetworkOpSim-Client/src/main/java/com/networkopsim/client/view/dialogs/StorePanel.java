// FILE: /NetworkOpSim-Multiplayer/NetworkOpSim-Client/src/main/java/com/networkopsim/client/view/dialogs/StorePanel.java
// ================================================================================

package com.networkopsim.client.view.dialogs;

import com.networkopsim.client.core.NetworkGame;
import com.networkopsim.client.view.panels.GamePanel;
import com.networkopsim.shared.dto.GameStateDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class StorePanel extends JDialog {
    private static final Logger log = LoggerFactory.getLogger(StorePanel.class);

    private final NetworkGame game;
    private final GamePanel gamePanel;
    private JLabel coinsLabel;

    // Item Constants
    private static final String ITEM_ATAR_NAME = "O' Atar"; private static final int COST_ATAR = 3; private static final String DESC_ATAR = "Disables collision Impact Waves for 10 seconds.";
    private static final String ITEM_AIRYAMAN_NAME = "O' Airyaman"; private static final int COST_AIRYAMAN = 4; private static final String DESC_AIRYAMAN = "Disables Packet-to-Packet collisions for 5 seconds.";
    private static final String ITEM_ANAHITA_NAME = "O' Anahita"; private static final int COST_ANAHITA = 5; private static final String DESC_ANAHITA = "Instantly resets the Noise level of all active Packets to zero.";
    private static final String ITEM_SPEED_LIMITER_NAME = "Speed Limiter"; private static final int COST_SPEED_LIMITER = 7; private static final String DESC_SPEED_LIMITER = "Temporarily prevents packets from accelerating for 15 seconds.";
    private static final String ITEM_EMERGENCY_BRAKE_NAME = "Emergency Brake"; private static final int COST_EMERGENCY_BRAKE = 8; private static final String DESC_EMERGENCY_BRAKE = "Instantly resets the speed of all active packets to their base speed.";
    private static final String ITEM_AERGIA_NAME = "Scroll of Aergia"; private static final int COST_AERGIA = 10; private static final String DESC_AERGIA = "Click a wire to nullify packet acceleration at that point for 20s.";
    private static final String ITEM_SISYPHUS_NAME = "Scroll of Sisyphus"; private static final int COST_SISYPHUS = 15; private static final String DESC_SISYPHUS = "Allows you to move one non-reference system within a limited radius.";
    private static final String ITEM_ELIPHAS_NAME = "Scroll of Eliphas"; private static final int COST_ELIPHAS = 20; private static final String DESC_ELIPHAS = "Click a wire to create a 30s field that realigns passing packets.";

    // UI Constants
    private static final Color DIALOG_BG_COLOR = new Color(45, 45, 55);
    private static final Color ITEM_BG_COLOR = new Color(230, 230, 235);
    private static final Color ITEM_BORDER_COLOR = new Color(180, 180, 190);
    private static final Color CLOSE_BUTTON_BG_COLOR = new Color(220, 100, 100);
    private static final Color CLOSE_BUTTON_BORDER_COLOR = new Color(130, 50, 50);
    private static final Font TITLE_FONT = new Font("Arial", Font.BOLD, 26);
    private static final Color TITLE_COLOR = Color.CYAN;
    private static final Font COIN_FONT = new Font("Arial", Font.BOLD, 18);
    private static final Color COIN_COLOR = Color.YELLOW;
    private static final Color ITEM_TEXT_COLOR_TITLE = Color.BLACK;
    private static final Color ITEM_TEXT_COLOR_DESC = new Color(50, 50, 50);
    private static final Color ITEM_TEXT_COLOR_COST = new Color(200, 100, 0);
    private static final Color ITEM_TEXT_COLOR_ACTIVATED = new Color(0, 100, 0);
    private static final Color DISABLED_BUTTON_BG_COLOR = new Color(180, 180, 180);
    private static final Color ACTIVATED_BUTTON_BG_COLOR = new Color(180, 230, 180);

    private boolean anahitaPurchasedThisVisit = false;
    private boolean brakePurchasedThisVisit = false;
    private JButton atarButton, airyamanButton, anahitaButton, speedLimiterButton, emergencyBrakeButton, aergiaButton, sisyphusButton, eliphasButton;

    public StorePanel(NetworkGame owner, GamePanel gamePanel) {
        super(owner, "Game Store", true);
        this.game = owner; this.gamePanel = gamePanel;
        setSize(550, 750); setLocationRelativeTo(owner); setResizable(false); setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() { @Override public void windowClosing(WindowEvent e) { closeStore(); } });

        JPanel mainPanel = new JPanel(new BorderLayout(15, 15)); mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 25, 20, 25)); mainPanel.setBackground(DIALOG_BG_COLOR);
        mainPanel.add(createTopPanel(), BorderLayout.NORTH);
        JScrollPane itemsScrollPane = new JScrollPane(createItemsPanel()); itemsScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED); itemsScrollPane.setBorder(null); itemsScrollPane.getViewport().setBackground(DIALOG_BG_COLOR);
        mainPanel.add(itemsScrollPane, BorderLayout.CENTER);
        mainPanel.add(createBottomPanel(), BorderLayout.SOUTH);
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

        atarButton.addActionListener(e -> purchaseItem(ITEM_ATAR_NAME, COST_ATAR));
        airyamanButton.addActionListener(e -> purchaseItem(ITEM_AIRYAMAN_NAME, COST_AIRYAMAN));
        anahitaButton.addActionListener(e -> purchaseItem(ITEM_ANAHITA_NAME, COST_ANAHITA));
        speedLimiterButton.addActionListener(e -> purchaseItem(ITEM_SPEED_LIMITER_NAME, COST_SPEED_LIMITER));
        emergencyBrakeButton.addActionListener(e -> purchaseItem(ITEM_EMERGENCY_BRAKE_NAME, COST_EMERGENCY_BRAKE));
        aergiaButton.addActionListener(e -> purchaseItem(ITEM_AERGIA_NAME, COST_AERGIA));
        sisyphusButton.addActionListener(e -> purchaseItem(ITEM_SISYPHUS_NAME, COST_SISYPHUS));
        eliphasButton.addActionListener(e -> purchaseItem(ITEM_ELIPHAS_NAME, COST_ELIPHAS));

        itemsPanel.add(atarButton); itemsPanel.add(Box.createVerticalStrut(12));
        itemsPanel.add(airyamanButton); itemsPanel.add(Box.createVerticalStrut(12));
        itemsPanel.add(anahitaButton); itemsPanel.add(Box.createVerticalStrut(12));
        itemsPanel.add(speedLimiterButton); itemsPanel.add(Box.createVerticalStrut(12));
        itemsPanel.add(emergencyBrakeButton);
        JSeparator separator = new JSeparator(SwingConstants.HORIZONTAL);
        separator.setForeground(ITEM_BORDER_COLOR); separator.setBackground(DIALOG_BG_COLOR);
        itemsPanel.add(Box.createVerticalStrut(15)); itemsPanel.add(separator); itemsPanel.add(Box.createVerticalStrut(15));
        itemsPanel.add(aergiaButton); itemsPanel.add(Box.createVerticalStrut(12));
        itemsPanel.add(sisyphusButton); itemsPanel.add(Box.createVerticalStrut(12));
        itemsPanel.add(eliphasButton);
        return itemsPanel;
    }

    private JPanel createBottomPanel() {
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        bottomPanel.setOpaque(false);
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(10,0,0,0));
        JButton closeButton = new JButton("Close Store");
        styleButton(closeButton, CLOSE_BUTTON_BG_COLOR, CLOSE_BUTTON_BORDER_COLOR, new Font("Arial", Font.BOLD, 16));
        closeButton.setForeground(Color.BLACK);
        closeButton.addActionListener(e -> closeStore());
        bottomPanel.add(closeButton);
        return bottomPanel;
    }

    private String buildButtonHtml(String name, int cost, String description, String statusString) {
        String costString = "(Cost: " + cost + ")";
        String titleColor = String.format("%06X", ITEM_TEXT_COLOR_TITLE.getRGB() & 0xFFFFFF);
        String costColor = String.format("%06X", ITEM_TEXT_COLOR_COST.getRGB() & 0xFFFFFF);
        String descColor = String.format("%06X", ITEM_TEXT_COLOR_DESC.getRGB() & 0xFFFFFF);
        String statusColor = String.format("%06X", ITEM_TEXT_COLOR_ACTIVATED.getRGB() & 0xFFFFFF);

        String statusHtml = statusString.isEmpty() ? "" : String.format("<div style='font-family: Arial; font-size: 10pt; color: #%s; text-align: right;'><b>%s</b></div>", statusColor, statusString);

        return String.format(
                "<html><body style='width: 380px; padding: 5px;'>" +
                        "<div style='font-family: Arial; font-size: 13pt; color: #%s;'><b>%s</b> <span style='color: #%s;'>%s</span></div>" +
                        "<div style='font-family: Arial; font-size: 10pt; color: #%s; margin-top: 2px;'>%s</div>" +
                        "%s" +
                        "</body></html>",
                titleColor, name, costColor, costString, descColor, description, statusHtml
        );
    }

    private JButton createItemButton(String name, int cost, String description) {
        JButton button = new JButton();
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setVerticalAlignment(SwingConstants.TOP);
        styleButton(button, ITEM_BG_COLOR, ITEM_BORDER_COLOR, null);
        Dimension prefSize = new Dimension(450, 85);
        button.setAlignmentX(Component.CENTER_ALIGNMENT);
        button.setPreferredSize(prefSize);
        button.setMaximumSize(prefSize);
        button.setMinimumSize(prefSize);
        return button;
    }

    private void styleButton(JButton button, Color bgColor, Color borderColor, Font font) {
        if (font != null) button.setFont(font);
        button.setForeground(Color.BLACK);
        button.setBackground(bgColor);
        button.setFocusPainted(false);
        Border line = BorderFactory.createLineBorder(borderColor, 2);
        Border empty = BorderFactory.createEmptyBorder(5, 10, 5, 10);
        button.setBorder(BorderFactory.createCompoundBorder(line, empty));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.addMouseListener(new java.awt.event.MouseAdapter() {
            final Color originalColor = button.getBackground();
            final Color hoverColor = originalColor.darker();
            @Override public void mouseEntered(java.awt.event.MouseEvent evt) { if (button.isEnabled()) button.setBackground(hoverColor); }
            @Override public void mouseExited(java.awt.event.MouseEvent evt) { if (button.isEnabled()) button.setBackground(originalColor); }
        });
    }

    private void purchaseItem(String itemName, int cost) {
        GameStateDTO state = gamePanel.getDisplayState();
        if (state == null || !state.isSimulationRunning()) return;

        if (isItemAlreadyActiveOrOnCooldown(itemName, state)) {
            game.playSoundEffect("error");
            return;
        }

        gamePanel.purchaseItem(itemName);

        if (itemName.equals(ITEM_ANAHITA_NAME)) anahitaPurchasedThisVisit = true;
        if (itemName.equals(ITEM_EMERGENCY_BRAKE_NAME)) brakePurchasedThisVisit = true;

        updateState();
    }

    private boolean isItemAlreadyActiveOrOnCooldown(String itemName, GameStateDTO state) {
        return (itemName.equals(ITEM_ATAR_NAME) && state.isAtarActive()) ||
                (itemName.equals(ITEM_AIRYAMAN_NAME) && state.isAiryamanActive()) ||
                (itemName.equals(ITEM_SPEED_LIMITER_NAME) && state.isSpeedLimiterActive()) ||
                (itemName.equals(ITEM_ANAHITA_NAME) && anahitaPurchasedThisVisit) ||
                (itemName.equals(ITEM_EMERGENCY_BRAKE_NAME) && brakePurchasedThisVisit);
    }

    public void updateState() {
        GameStateDTO state = gamePanel.getDisplayState();
        if (state == null) {
            coinsLabel.setText("Coins: ?");
            atarButton.setEnabled(false);
            airyamanButton.setEnabled(false);
            anahitaButton.setEnabled(false);
            speedLimiterButton.setEnabled(false);
            emergencyBrakeButton.setEnabled(false);
            aergiaButton.setEnabled(false);
            sisyphusButton.setEnabled(false);
            eliphasButton.setEnabled(false);
            return;
        }

        coinsLabel.setText("Coins: " + state.getMyCoins());
        int coins = state.getMyCoins();

        updateButton(atarButton, ITEM_ATAR_NAME, COST_ATAR, DESC_ATAR, coins >= COST_ATAR && !state.isAtarActive(), state.isAtarActive() ? "ACTIVE" : "");
        updateButton(airyamanButton, ITEM_AIRYAMAN_NAME, COST_AIRYAMAN, DESC_AIRYAMAN, coins >= COST_AIRYAMAN && !state.isAiryamanActive(), state.isAiryamanActive() ? "ACTIVE" : "");
        updateButton(anahitaButton, ITEM_ANAHITA_NAME, COST_ANAHITA, DESC_ANAHITA, coins >= COST_ANAHITA && !anahitaPurchasedThisVisit, anahitaPurchasedThisVisit ? "USED" : "");
        updateButton(speedLimiterButton, ITEM_SPEED_LIMITER_NAME, COST_SPEED_LIMITER, DESC_SPEED_LIMITER, coins >= COST_SPEED_LIMITER && !state.isSpeedLimiterActive(), state.isSpeedLimiterActive() ? "ACTIVE" : "");
        updateButton(emergencyBrakeButton, ITEM_EMERGENCY_BRAKE_NAME, COST_EMERGENCY_BRAKE, DESC_EMERGENCY_BRAKE, coins >= COST_EMERGENCY_BRAKE && !brakePurchasedThisVisit, brakePurchasedThisVisit ? "USED" : "");
        updateButton(aergiaButton, ITEM_AERGIA_NAME, COST_AERGIA, DESC_AERGIA, coins >= COST_AERGIA, "");
        updateButton(sisyphusButton, ITEM_SISYPHUS_NAME, COST_SISYPHUS, DESC_SISYPHUS, coins >= COST_SISYPHUS, "");
        updateButton(eliphasButton, ITEM_ELIPHAS_NAME, COST_ELIPHAS, DESC_ELIPHAS, coins >= COST_ELIPHAS, "");
    }

    private void updateButton(JButton button, String name, int cost, String desc, boolean enabled, String status) {
        button.setText(buildButtonHtml(name, cost, desc, status));
        button.setEnabled(enabled);
        boolean isActiveOrUsed = !status.isEmpty();
        button.setBackground(isActiveOrUsed ? ACTIVATED_BUTTON_BG_COLOR : (enabled ? ITEM_BG_COLOR : DISABLED_BUTTON_BG_COLOR));
    }

    private void closeStore() {
        setVisible(false);
        gamePanel.closeStore();
        gamePanel.requestFocusInWindow();
    }

    @Override
    public void setVisible(boolean b) {
        if (b) {
            anahitaPurchasedThisVisit = false;
            brakePurchasedThisVisit = false;
            updateState();
        }
        super.setVisible(b);
    }
}