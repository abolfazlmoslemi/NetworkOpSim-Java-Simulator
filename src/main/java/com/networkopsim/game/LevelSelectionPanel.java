
package com.networkopsim.game;
import javax.swing.*;

import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Objects;
public class LevelSelectionPanel extends JPanel {
    private final NetworkGame game; 
    private final GameState gameState; 
    private final JButton[] levelButtons; 
    private final int NUM_LEVELS; 
    private static final Color BACKGROUND_COLOR_START = new Color(25, 25, 35);
    private static final Color BACKGROUND_COLOR_END = new Color(40, 40, 50);
    private static final Color TITLE_COLOR = Color.WHITE;
    private static final Font TITLE_FONT = new Font("Arial", Font.BOLD, 36);
    private static final Font BUTTON_FONT = new Font("Arial", Font.BOLD, 20);
    private static final Dimension BUTTON_SIZE = new Dimension(160, 100); 
    private static final Color UNLOCKED_BG_COLOR = new Color(70, 130, 180); 
    private static final Color UNLOCKED_FG_COLOR = Color.WHITE;
    private static final Color UNLOCKED_BORDER_COLOR = new Color(40, 90, 130);
    private static final Color LOCKED_BG_COLOR = Color.DARK_GRAY;
    private static final Color LOCKED_FG_COLOR = Color.LIGHT_GRAY;
    private static final Color LOCKED_BORDER_COLOR = Color.BLACK;
    private static final Font BACK_BUTTON_FONT = new Font("Arial", Font.BOLD, 18);
    private static final Color BACK_BUTTON_BG_COLOR = new Color(100, 100, 110);
    private static final Color BACK_BUTTON_BORDER_COLOR = new Color(70, 70, 80);
    public LevelSelectionPanel(NetworkGame game) {
        this.game = Objects.requireNonNull(game, "NetworkGame instance cannot be null");
        this.gameState = Objects.requireNonNull(game.getGameState(), "GameState cannot be null");
        this.NUM_LEVELS = gameState.getMaxLevels();
        setLayout(new BorderLayout(10, 10)); 
        setPreferredSize(new Dimension(NetworkGame.WINDOW_WIDTH, NetworkGame.WINDOW_HEIGHT));
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20)); 
        JLabel titleLabel = new JLabel("Select Level", SwingConstants.CENTER);
        titleLabel.setFont(TITLE_FONT);
        titleLabel.setForeground(TITLE_COLOR);
        add(titleLabel, BorderLayout.NORTH);
        JPanel levelsGridPanel = new JPanel();
        int cols = Math.max(2, (int) Math.ceil(Math.sqrt(NUM_LEVELS)));
        int rows = (int) Math.ceil((double) NUM_LEVELS / cols);
        levelsGridPanel.setLayout(new GridLayout(rows, cols, 25, 25)); 
        levelsGridPanel.setOpaque(false); 
        levelsGridPanel.setBorder(BorderFactory.createEmptyBorder(30, 50, 30, 50)); 
        levelButtons = new JButton[NUM_LEVELS];
        for (int i = 0; i < levelButtons.length; i++) {
            final int levelIndex = i;         
            final int levelNumber = i + 1;      
            levelButtons[i] = new JButton(); 
            levelButtons[i].setFont(BUTTON_FONT);
            levelButtons[i].setPreferredSize(BUTTON_SIZE);
            levelButtons[i].setMinimumSize(BUTTON_SIZE); 
            levelButtons[i].setFocusPainted(false);
            levelButtons[i].setCursor(new Cursor(Cursor.HAND_CURSOR));
            levelButtons[i].addActionListener(e -> {
                if (gameState.isLevelUnlocked(levelIndex)) {
                    game.setLevel(levelNumber); 
                    game.startGame();         
                } else {
                    game.playSoundEffect("error"); 
                    JOptionPane.showMessageDialog(
                            this.game, 
                            "Level " + levelNumber + " is locked!\n" +
                                    "Complete previous levels to unlock.",
                            "Level Locked",
                            JOptionPane.WARNING_MESSAGE);
                }
            });
            levelButtons[i].addMouseListener(new MouseAdapter() {
                Color originalBg;
                @Override
                public void mouseEntered(MouseEvent e) {
                    JButton btn = (JButton)e.getSource();
                    if (btn.isEnabled()) {
                        originalBg = btn.getBackground();
                        btn.setBackground(originalBg.brighter());
                    }
                }
                @Override
                public void mouseExited(MouseEvent e) {
                    JButton btn = (JButton)e.getSource();
                    if (btn.isEnabled() && originalBg != null) {
                        btn.setBackground(originalBg);
                    }
                    originalBg = null; 
                }
            });
            levelsGridPanel.add(levelButtons[i]); 
        }
        int totalCells = rows * cols;
        for (int i = NUM_LEVELS; i < totalCells; i++) {
            levelsGridPanel.add(Box.createRigidArea(new Dimension(0, 0)));
        }
        JPanel centerWrapper = new JPanel(new GridBagLayout());
        centerWrapper.setOpaque(false);
        centerWrapper.add(levelsGridPanel); 
        add(centerWrapper, BorderLayout.CENTER);
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER)); 
        bottomPanel.setOpaque(false);
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0)); 
        JButton backButton = new JButton("Back to Menu");
        backButton.setFont(BACK_BUTTON_FONT);
        styleStandardButton(backButton, BACK_BUTTON_BG_COLOR, BACK_BUTTON_BORDER_COLOR); 
        backButton.addActionListener(e -> game.returnToMenu()); 
        bottomPanel.add(backButton);
        add(bottomPanel, BorderLayout.SOUTH);
        updateLevelButtons(); 
    }
    private void styleStandardButton(JButton button, Color bgColor, Color borderColor) {
        button.setForeground(Color.WHITE); 
        button.setBackground(bgColor);
        button.setFocusPainted(false);
        Border line = BorderFactory.createLineBorder(borderColor, 2);
        Border empty = BorderFactory.createEmptyBorder(10, 20, 10, 20); 
        button.setBorder(BorderFactory.createCompoundBorder(line, empty));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.addMouseListener(new MouseAdapter() {
            final Color originalColor = button.getBackground();
            final Color hoverColor = originalColor.brighter();
            final Color disabledColor = bgColor.darker(); 
            @Override
            public void mouseEntered(MouseEvent e) {
                if (button.isEnabled()) {
                    button.setBackground(hoverColor);
                } else {
                }
            }
            @Override
            public void mouseExited(MouseEvent e) {
                if (button.isEnabled()) {
                    button.setBackground(originalColor);
                } else {
                }
            }
        });
    }
    public void updateLevelButtons() {
        if (levelButtons == null) return; 
        for (int i = 0; i < levelButtons.length; i++) {
            boolean unlocked = gameState.isLevelUnlocked(i);
            String levelNumStr = String.valueOf(i + 1);
            Border lineBorder;
            Border emptyPadding = BorderFactory.createEmptyBorder(10, 15, 10, 15);
            levelButtons[i].setEnabled(unlocked); 
            if (unlocked) {
                levelButtons[i].setText("Level " + levelNumStr);
                levelButtons[i].setBackground(UNLOCKED_BG_COLOR);
                levelButtons[i].setForeground(UNLOCKED_FG_COLOR);
                levelButtons[i].setToolTipText("Play Level " + levelNumStr);
                lineBorder = BorderFactory.createLineBorder(UNLOCKED_BORDER_COLOR, 2);
            } else {
                levelButtons[i].setText("<html><center>Level " + levelNumStr +
                        "<br><font color=#AAAAAA size=-2>(Locked)</font></center></html>");
                levelButtons[i].setBackground(LOCKED_BG_COLOR);
                levelButtons[i].setForeground(LOCKED_FG_COLOR); 
                levelButtons[i].setToolTipText("Level " + levelNumStr + " (Locked - Complete previous levels to unlock)");
                lineBorder = BorderFactory.createLineBorder(LOCKED_BORDER_COLOR, 2);
            }
            levelButtons[i].setBorder(BorderFactory.createCompoundBorder(lineBorder, emptyPadding));
        }
        repaint();
    }
    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g.create();
        try {
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            GradientPaint gp = new GradientPaint(0, 0, BACKGROUND_COLOR_START,
                    0, getHeight(), BACKGROUND_COLOR_END);
            g2d.setPaint(gp);
            g2d.fillRect(0, 0, getWidth(), getHeight());
        } finally {
            g2d.dispose();
        }
    }
}