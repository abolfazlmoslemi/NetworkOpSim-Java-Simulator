// FILE: /NetworkOpSim-Multiplayer/NetworkOpSim-Client/src/main/java/com/networkopsim/client/view/panels/LevelSelectionPanel.java
// ================================================================================

package com.networkopsim.client.view.panels;

import com.networkopsim.client.core.NetworkGame;
import com.networkopsim.shared.net.UserCommand;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Objects;

public class LevelSelectionPanel extends JPanel {
    private final NetworkGame game;
    private final JButton[] levelButtons;
    private final int NUM_LEVELS = 5; // Fixed number of levels

    private boolean isForOnlinePlay = false;
    private final JLabel titleLabel;
    private final JButton backButton;

    // UI Constants
    private static final Color BACKGROUND_COLOR_START = new Color(25, 25, 35);
    private static final Color BACKGROUND_COLOR_END = new Color(40, 40, 50);
    private static final Color TITLE_COLOR = Color.WHITE;
    private static final Font TITLE_FONT = new Font("Arial", Font.BOLD, 36);
    private static final Font BUTTON_FONT = new Font("Arial", Font.BOLD, 20);
    private static final Dimension BUTTON_SIZE = new Dimension(160, 100);
    private static final Color UNLOCKED_BG_COLOR = new Color(240, 240, 240); // Brighter background
    private static final Color UNLOCKED_FG_COLOR = Color.BLACK; // <-- CHANGED
    private static final Color UNLOCKED_BORDER_COLOR = new Color(40, 90, 130);
    private static final Color LOCKED_BG_COLOR = new Color(220, 220, 220); // Brighter background
    private static final Color LOCKED_FG_COLOR = Color.DARK_GRAY; // <-- CHANGED
    private static final Color LOCKED_BORDER_COLOR = Color.GRAY;
    private static final Font BACK_BUTTON_FONT = new Font("Arial", Font.BOLD, 18);
    private static final Color BACK_BUTTON_BG_COLOR = new Color(220, 220, 230);
    private static final Color BACK_BUTTON_BORDER_COLOR = new Color(70, 70, 80);
    private static final Color CANCEL_BUTTON_BG_COLOR = new Color(220, 180, 170);

    public LevelSelectionPanel(NetworkGame game) {
        this.game = Objects.requireNonNull(game, "NetworkGame instance cannot be null");
        setLayout(new BorderLayout(10, 10));
        setPreferredSize(new Dimension(NetworkGame.WINDOW_WIDTH, NetworkGame.WINDOW_HEIGHT));
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        titleLabel = new JLabel("Select Level", SwingConstants.CENTER);
        titleLabel.setFont(TITLE_FONT);
        titleLabel.setForeground(TITLE_COLOR);
        add(titleLabel, BorderLayout.NORTH);

        JPanel levelsGridPanel = new JPanel();
        int cols = 3;
        int rows = 2;
        levelsGridPanel.setLayout(new GridLayout(rows, cols, 25, 25));
        levelsGridPanel.setOpaque(false);
        levelsGridPanel.setBorder(BorderFactory.createEmptyBorder(30, 50, 30, 50));

        levelButtons = new JButton[NUM_LEVELS];
        for (int i = 0; i < levelButtons.length; i++) {
            final int levelNumber = i + 1;
            levelButtons[i] = new JButton();
            levelButtons[i].setFont(BUTTON_FONT);
            levelButtons[i].setPreferredSize(BUTTON_SIZE);
            levelButtons[i].setFocusPainted(false);
            levelButtons[i].setCursor(new Cursor(Cursor.HAND_CURSOR));
            levelButtons[i].addActionListener(e -> handleLevelClick(levelNumber));
            levelsGridPanel.add(levelButtons[i]);
        }

        // Fill remaining grid cells to keep layout consistent
        for (int i = NUM_LEVELS; i < rows * cols; i++) {
            levelsGridPanel.add(Box.createRigidArea(new Dimension(0, 0)));
        }

        JPanel centerWrapper = new JPanel(new GridBagLayout());
        centerWrapper.setOpaque(false);
        centerWrapper.add(levelsGridPanel);
        add(centerWrapper, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        bottomPanel.setOpaque(false);
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));
        backButton = new JButton("Back to Menu");
        backButton.setFont(BACK_BUTTON_FONT);
        styleStandardButton(backButton, BACK_BUTTON_BG_COLOR, BACK_BUTTON_BORDER_COLOR);
        backButton.addActionListener(e -> game.returnToMenu());
        bottomPanel.add(backButton);
        add(bottomPanel, BorderLayout.SOUTH);

        updateLevelButtons();
    }

    public void setMode(boolean isOnline) {
        this.isForOnlinePlay = isOnline;
        if (isOnline) {
            titleLabel.setText("Select Level for Online Match");
        } else {
            titleLabel.setText("Select Level");
        }
        resetToSelectionMode();
    }

    private void handleLevelClick(int levelNumber) {
        if (!levelButtons[levelNumber - 1].isEnabled()) {
            game.playSoundEffect("error");
            JOptionPane.showMessageDialog(this.game, "Level " + levelNumber + " is locked!", "Level Locked", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (isForOnlinePlay) {
            game.getNetworkManager().sendCommand(UserCommand.joinMatchmakingQueue(levelNumber));
            setWaitingMode(levelNumber);
        } else {
            game.setLevel(levelNumber);
            game.startOfflineGame();
        }
    }

    private void setWaitingMode(int level) {
        titleLabel.setText("Searching for opponent on Level " + level + "...");
        for (JButton button : levelButtons) {
            button.setEnabled(false);
        }
        backButton.setText("Cancel Search");
        styleStandardButton(backButton, CANCEL_BUTTON_BG_COLOR, CANCEL_BUTTON_BG_COLOR.darker());
        if(backButton.getActionListeners().length > 0) {
            backButton.removeActionListener(backButton.getActionListeners()[0]);
        }
        backButton.addActionListener(e -> {
            game.getNetworkManager().sendCommand(UserCommand.simpleCommand(UserCommand.CommandType.LEAVE_MATCHMAKING_QUEUE));
            game.returnToMenu(); // Go back to main menu after cancelling
        });
    }

    private void resetToSelectionMode() {
        updateLevelButtons();
        backButton.setText("Back to Menu");
        styleStandardButton(backButton, BACK_BUTTON_BG_COLOR, BACK_BUTTON_BORDER_COLOR);
        if(backButton.getActionListeners().length > 0) {
            backButton.removeActionListener(backButton.getActionListeners()[0]);
        }
        backButton.addActionListener(e -> game.returnToMenu());
    }

    private void styleStandardButton(JButton button, Color bgColor, Color borderColor) {
        button.setForeground(Color.BLACK); // <-- CHANGED
        button.setBackground(bgColor);
        button.setFocusPainted(false);
        Border line = BorderFactory.createLineBorder(borderColor, 2);
        Border empty = BorderFactory.createEmptyBorder(10, 20, 10, 20);
        button.setBorder(BorderFactory.createCompoundBorder(line, empty));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));

        button.addMouseListener(new MouseAdapter() {
            final Color originalColor = button.getBackground();
            final Color hoverColor = originalColor.darker(); // Darken on hover for light buttons
            @Override public void mouseEntered(MouseEvent e) { if (button.isEnabled()) button.setBackground(hoverColor); }
            @Override public void mouseExited(MouseEvent e) { if (button.isEnabled()) button.setBackground(originalColor); }
        });
    }

    public void updateLevelButtons() {
        if (levelButtons == null) return;

        boolean[] unlockedLevels = new boolean[NUM_LEVELS];
        unlockedLevels[0] = true;

        if (!game.isOfflineMode()) {
            // Future logic to get unlocked levels from server profile
        }

        for (int i = 0; i < levelButtons.length; i++) {
            boolean unlocked = unlockedLevels[i];
            String levelNumStr = String.valueOf(i + 1);

            levelButtons[i].setEnabled(unlocked);
            if (unlocked) {
                levelButtons[i].setText("Level " + levelNumStr);
                levelButtons[i].setBackground(UNLOCKED_BG_COLOR);
                levelButtons[i].setForeground(UNLOCKED_FG_COLOR);
                levelButtons[i].setToolTipText("Play Level " + levelNumStr);
                levelButtons[i].setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(UNLOCKED_BORDER_COLOR, 3),
                        BorderFactory.createEmptyBorder(10, 15, 10, 15)
                ));
            } else {
                levelButtons[i].setText("<html><center>Level " + levelNumStr + "<br><font color=#888888 size=-2>(Locked)</font></center></html>");
                levelButtons[i].setBackground(LOCKED_BG_COLOR);
                levelButtons[i].setForeground(LOCKED_FG_COLOR);
                levelButtons[i].setToolTipText("Level " + levelNumStr + " (Locked)");
                levelButtons[i].setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(LOCKED_BORDER_COLOR, 2),
                        BorderFactory.createEmptyBorder(10, 15, 10, 15)
                ));
            }
        }
        repaint();
    }

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g.create();
        try {
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            GradientPaint gp = new GradientPaint(0, 0, BACKGROUND_COLOR_START, 0, getHeight(), BACKGROUND_COLOR_END);
            g2d.setPaint(gp);
            g2d.fillRect(0, 0, getWidth(), getHeight());
        } finally {
            g2d.dispose();
        }
    }
}