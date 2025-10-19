// FILE: /NetworkOpSim-Multiplayer/NetworkOpSim-Client/src/main/java/com/networkopsim/client/view/panels/MenuPanel.java
// ================================================================================

package com.networkopsim.client.view.panels;

import com.networkopsim.client.core.NetworkGame;
import com.networkopsim.client.network.ConnectionStatus;
import com.networkopsim.shared.net.UserCommand;

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
    private final JButton playOnlineButton;
    private final JButton cancelMatchmakingButton;
    private final JButton connectButton;
    private final JLabel connectionStatusLabel;
    private Image backgroundImage;

    // UI Constants
    private static final Color BUTTON_BG_COLOR = new Color(70, 130, 180, 220);
    private static final Color BUTTON_BORDER_COLOR = new Color(40, 90, 130);
    private static final Color ONLINE_BUTTON_BG_COLOR = new Color(70, 180, 130, 220);
    private static final Color CANCEL_BUTTON_BG_COLOR = new Color(180, 130, 70, 220);
    private static final Color BUTTON_TEXT_COLOR = Color.BLACK;
    private static final Font BUTTON_FONT = new Font("Arial", Font.BOLD, 22);
    private static final Font STATUS_FONT = new Font("Arial", Font.BOLD, 14);
    private static final Dimension BUTTON_SIZE = new Dimension(320, 65);
    private static final String BACKGROUND_IMAGE_PATH = "/assets/images/menu_background.png";

    public MenuPanel(NetworkGame game) {
        this.game = Objects.requireNonNull(game, "NetworkGame instance cannot be null");
        setLayout(new BorderLayout());
        setPreferredSize(new Dimension(NetworkGame.WINDOW_WIDTH, NetworkGame.WINDOW_HEIGHT));
        loadBackgroundImage();

        // Top Panel for Title
        JPanel topPanel = new JPanel();
        topPanel.setOpaque(false);
        topPanel.setBorder(BorderFactory.createEmptyBorder(50, 0, 0, 0));
        JLabel titleLabel = new JLabel("Network Operator Simulator");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 48));
        titleLabel.setForeground(new Color(210, 220, 255));
        topPanel.add(titleLabel);
        add(topPanel, BorderLayout.NORTH);

        // Center Panel for Buttons
        JPanel buttonPanelWrapper = new JPanel(new GridBagLayout());
        buttonPanelWrapper.setOpaque(false);
        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.Y_AXIS));
        buttonPanel.setOpaque(false);

        startButton = createMenuButton("Start Offline (Level " + game.getCurrentLevel() + ")", BUTTON_BG_COLOR);
        playOnlineButton = createMenuButton("Play Online", ONLINE_BUTTON_BG_COLOR);
        cancelMatchmakingButton = createMenuButton("Cancel Search", CANCEL_BUTTON_BG_COLOR);
        cancelMatchmakingButton.setVisible(false);

        JButton levelsButton = createMenuButton("Select Level", BUTTON_BG_COLOR);
        JButton leaderboardButton = createMenuButton("Leaderboard", BUTTON_BG_COLOR);
        JButton settingsButton = createMenuButton("Settings", BUTTON_BG_COLOR);
        JButton exitButton = createMenuButton("Exit Game", BUTTON_BG_COLOR);

        buttonPanel.add(startButton);
        buttonPanel.add(Box.createVerticalStrut(15));
        buttonPanel.add(playOnlineButton);
        buttonPanel.add(cancelMatchmakingButton);
        buttonPanel.add(Box.createVerticalStrut(15));
        buttonPanel.add(levelsButton);
        buttonPanel.add(Box.createVerticalStrut(15));
        buttonPanel.add(leaderboardButton);
        buttonPanel.add(Box.createVerticalStrut(15));
        buttonPanel.add(settingsButton);
        buttonPanel.add(Box.createVerticalStrut(30));
        buttonPanel.add(exitButton);

        startButton.addActionListener(e -> game.startOfflineGame());
        playOnlineButton.addActionListener(e -> game.showLevelSelectionForOnline());
        cancelMatchmakingButton.addActionListener(e -> cancelMatchmaking());
        levelsButton.addActionListener(e -> game.showLevelSelection());
        leaderboardButton.addActionListener(e -> game.showLeaderboard());
        settingsButton.addActionListener(e -> game.showSettings());
        exitButton.addActionListener(e -> handleExitRequest());

        buttonPanelWrapper.add(buttonPanel);
        add(buttonPanelWrapper, BorderLayout.CENTER);

        // Bottom Panel for Connection Status
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 10));
        bottomPanel.setOpaque(false);

        connectionStatusLabel = new JLabel("Status: Unknown");
        connectionStatusLabel.setFont(STATUS_FONT);

        connectButton = new JButton("Connect");
        connectButton.setFont(STATUS_FONT.deriveFont(12f));
        connectButton.addActionListener(e -> toggleConnection());

        bottomPanel.add(connectionStatusLabel);
        bottomPanel.add(connectButton);
        add(bottomPanel, BorderLayout.SOUTH);
    }

    private void findMatch() {
        game.getNetworkManager().sendCommand(UserCommand.joinMatchmakingQueue(game.getCurrentLevel()));
        playOnlineButton.setVisible(false);
        playOnlineButton.setText("Searching for opponent...");
        playOnlineButton.setEnabled(false);
        cancelMatchmakingButton.setVisible(true);
        startButton.setEnabled(false);
    }

    private void cancelMatchmaking() {
        game.getNetworkManager().sendCommand(UserCommand.simpleCommand(UserCommand.CommandType.LEAVE_MATCHMAKING_QUEUE));
        resetMatchmakingButtons();
    }

    public void resetMatchmakingButtons() {
        playOnlineButton.setVisible(true);
        playOnlineButton.setText("Play Online");
        playOnlineButton.setEnabled(game.getNetworkManager().getStatus() == ConnectionStatus.CONNECTED);
        cancelMatchmakingButton.setVisible(false);
        startButton.setEnabled(true);
    }

    private void toggleConnection() {
        ConnectionStatus status = game.getNetworkManager().getStatus();
        if (status == ConnectionStatus.DISCONNECTED) {
            game.getNetworkManager().connect();
        } else if (status == ConnectionStatus.CONNECTED) {
            cancelMatchmaking();
            game.getNetworkManager().disconnect();
        }
    }

    public void updateConnectionStatus(ConnectionStatus status) {
        if (status == null) return;
        connectionStatusLabel.setText("Status: " + status.getDisplayText());
        connectionStatusLabel.setForeground(status.getDisplayColor());

        switch (status) {
            case CONNECTED:
                connectButton.setText("Disconnect");
                connectButton.setEnabled(true);
                playOnlineButton.setEnabled(true);
                break;
            case DISCONNECTED:
                connectButton.setText("Connect");
                connectButton.setEnabled(true);
                playOnlineButton.setEnabled(false);
                resetMatchmakingButtons();
                break;
            case CONNECTING:
                connectButton.setText("Connecting...");
                connectButton.setEnabled(false);
                playOnlineButton.setEnabled(false);
                break;
        }
    }

    private void loadBackgroundImage() {
        try (InputStream imgStream = getClass().getResourceAsStream(BACKGROUND_IMAGE_PATH)) {
            if (imgStream != null) {
                backgroundImage = ImageIO.read(imgStream);
            } else {
                backgroundImage = null;
            }
        } catch (Exception e) {
            backgroundImage = null;
        }
    }

    private JButton createMenuButton(String text, Color bgColor) {
        JButton button = new JButton(text);
        button.setFont(BUTTON_FONT);
        button.setForeground(BUTTON_TEXT_COLOR);
        button.setBackground(bgColor);
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
            @Override public void mouseEntered(MouseEvent e) { if(button.isEnabled()) button.setBackground(hoverColor); }
            @Override public void mouseExited(MouseEvent e) { if(button.isEnabled()) button.setBackground(originalColor); }
        });
        return button;
    }

    private void handleExitRequest() {
        int choice = JOptionPane.showConfirmDialog(
                game, "Are you sure you want to exit?", "Exit Game",
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (choice == JOptionPane.YES_OPTION) {
            game.shutdownGame();
        }
    }

    public void updateStartButtonLevel(int level) {
        if (startButton != null) {
            startButton.setText("Start Offline (Level " + level + ")");
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (backgroundImage != null) {
            g.drawImage(backgroundImage, 0, 0, getWidth(), getHeight(), this);
        } else {
            Graphics2D g2d = (Graphics2D) g;
            GradientPaint gp = new GradientPaint(0, 0, new Color(25, 25, 35), 0, getHeight(), new Color(45, 45, 55));
            g2d.setPaint(gp);
            g2d.fillRect(0, 0, getWidth(), getHeight());
        }
    }
}