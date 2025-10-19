// FILE: /NetworkOpSim-Multiplayer/NetworkOpSim-Client/src/main/java/com/networkopsim/client/view/dialogs/KeyBindingPanel.java
// ================================================================================

package com.networkopsim.client.view.dialogs;

import com.networkopsim.client.core.NetworkGame;
import com.networkopsim.client.utils.KeyBindings;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.awt.event.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class KeyBindingPanel extends JDialog {
    private final NetworkGame game;
    private final KeyBindings keyBindings;
    private final Map<KeyBindings.GameAction, JButton> actionButtons = new HashMap<>();
    private KeyBindings.GameAction actionToChange = null;
    private JLabel waitingForKeyLabel;
    private JButton cancelButtonListening;
    private boolean hasUnsavedChanges = false;

    // UI Constants
    private static final Color DIALOG_BG_COLOR_START = new Color(35, 40, 50);
    private static final Color DIALOG_BG_COLOR_END = new Color(50, 55, 65);
    private static final Color PANEL_ITEM_BG_COLOR = new Color(60, 65, 75);
    private static final Color TEXT_COLOR = new Color(220, 220, 220);
    private static final Color ACTION_TEXT_COLOR = new Color(190, 210, 230);
    private static final Color KEY_TEXT_COLOR = Color.ORANGE;
    private static final Color BUTTON_BG_COLOR = new Color(75, 85, 100);
    private static final Color BUTTON_BORDER_COLOR = new Color(50, 55, 65);
    private static final Color BUTTON_HOVER_BG_COLOR = new Color(95, 105, 120);
    private static final Font TITLE_FONT = new Font("Segoe UI Semibold", Font.BOLD, 26);
    private static final Font LABEL_FONT = new Font("Segoe UI", Font.PLAIN, 16);
    private static final Font KEY_FONT = new Font("Consolas", Font.BOLD, 16);
    private static final Font BUTTON_FONT_SMALL = new Font("Segoe UI Semibold", Font.BOLD, 14);
    private static final Font WAITING_FONT = new Font("Segoe UI", Font.BOLD, 17);
    private static final Color SEPARATOR_COLOR = new Color(70, 75, 85);

    public KeyBindingPanel(NetworkGame owner, KeyBindings keyBindings) {
        super(owner, "Configure Key Bindings", true);
        this.game = Objects.requireNonNull(owner);
        this.keyBindings = Objects.requireNonNull(keyBindings);

        setupUI();
        setupListeners();
    }

    private void setupUI() {
        // Create a custom panel that will draw the background gradient
        JPanel mainPanel = new JPanel(new BorderLayout(15, 15)) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                GradientPaint gp = new GradientPaint(0, 0, DIALOG_BG_COLOR_START, 0, getHeight(), DIALOG_BG_COLOR_END);
                g2d.setPaint(gp);
                g2d.fillRect(0, 0, getWidth(), getHeight());
                g2d.dispose();
            }
        };
        mainPanel.setBorder(new EmptyBorder(20, 25, 20, 25));
        setContentPane(mainPanel); // Set this panel as the content pane for the dialog

        setPreferredSize(new Dimension(600, 650));

        JLabel titleLabel = new JLabel("Key Bindings", SwingConstants.CENTER);
        titleLabel.setFont(TITLE_FONT);
        titleLabel.setForeground(TEXT_COLOR);
        titleLabel.setBorder(new EmptyBorder(0, 0, 15, 0));
        mainPanel.add(titleLabel, BorderLayout.NORTH);

        JPanel bindingsContainerPanel = createBindingsListPanel();
        JScrollPane scrollPane = new JScrollPane(bindingsContainerPanel);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setBorder(BorderFactory.createLineBorder(SEPARATOR_COLOR.darker(), 1));
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        mainPanel.add(createBottomPanel(), BorderLayout.SOUTH);
        pack();
        setLocationRelativeTo(getOwner());
    }

    private JPanel createBindingsListPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(4, 2, 4, 2);

        for (KeyBindings.GameAction action : keyBindings.getAllActions()) {
            JPanel itemPanel = new JPanel(new BorderLayout(15, 0));
            itemPanel.setOpaque(true);
            itemPanel.setBackground(PANEL_ITEM_BG_COLOR);
            itemPanel.setBorder(BorderFactory.createCompoundBorder(
                    new MatteBorder(0, 0, 1, 0, SEPARATOR_COLOR),
                    new EmptyBorder(8, 12, 8, 12)
            ));

            JLabel actionLabel = new JLabel(action.getDescription() + ":");
            actionLabel.setFont(LABEL_FONT);
            actionLabel.setForeground(ACTION_TEXT_COLOR);

            JButton keyButton = new JButton(keyBindings.getKeyText(keyBindings.getKeyCode(action)));
            keyButton.setFont(KEY_FONT);
            styleActionButton(keyButton, new Dimension(130, 35));
            keyButton.addActionListener(e -> startListeningFor(action, keyButton));

            actionButtons.put(action, keyButton);
            itemPanel.add(actionLabel, BorderLayout.CENTER);
            itemPanel.add(keyButton, BorderLayout.EAST);
            panel.add(itemPanel, gbc);
        }
        gbc.weighty = 1; // Push everything to the top
        panel.add(Box.createVerticalGlue(), gbc);
        return panel;
    }

    private JPanel createBottomPanel() {
        JPanel southOuterPanel = new JPanel(new BorderLayout());
        southOuterPanel.setOpaque(false);

        JPanel waitingPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));
        waitingPanel.setOpaque(false);
        waitingPanel.setBorder(new EmptyBorder(10, 0, 0, 0));
        waitingForKeyLabel = new JLabel("Click an action's key to change it.");
        waitingForKeyLabel.setFont(LABEL_FONT);
        waitingForKeyLabel.setForeground(TEXT_COLOR);
        cancelButtonListening = new JButton("Cancel (Esc)");
        styleUtilityButton(cancelButtonListening, new Color(120, 70, 70));
        cancelButtonListening.setVisible(false);
        cancelButtonListening.addActionListener(e -> cancelKeyChange());
        waitingPanel.add(waitingForKeyLabel);
        waitingPanel.add(cancelButtonListening);

        JPanel bottomButtonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 10));
        bottomButtonPanel.setOpaque(false);
        bottomButtonPanel.setBorder(new EmptyBorder(10, 0, 0, 0));

        JButton saveButton = new JButton("Save & Close");
        styleUtilityButton(saveButton, new Color(70, 120, 70));
        saveButton.addActionListener(e -> {
            keyBindings.saveBindingsToFile();
            hasUnsavedChanges = false;
            dispose();
        });

        JButton defaultsButton = new JButton("Reset to Defaults");
        styleUtilityButton(defaultsButton, new Color(110, 110, 70));
        defaultsButton.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(this,
                    "Reset all key bindings to their defaults?\n(Changes are not saved until you click 'Save & Close')",
                    "Reset Bindings?", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (confirm == JOptionPane.YES_OPTION) {
                keyBindings.resetToDefaults();
                updateAllKeyButtonLabels();
                hasUnsavedChanges = true;
            }
        });

        JButton closeButton = new JButton("Close Without Saving");
        styleUtilityButton(closeButton, new Color(100, 100, 110));
        closeButton.addActionListener(e -> {
            if (hasUnsavedChanges) keyBindings.loadBindingsFromFile();
            hasUnsavedChanges = false;
            dispose();
        });

        bottomButtonPanel.add(saveButton);
        bottomButtonPanel.add(defaultsButton);
        bottomButtonPanel.add(closeButton);

        southOuterPanel.add(waitingPanel, BorderLayout.NORTH);
        southOuterPanel.add(bottomButtonPanel, BorderLayout.CENTER);
        return southOuterPanel;
    }

    private void setupListeners() {
        setFocusTraversalKeysEnabled(false);
        this.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (actionToChange != null) {
                    if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                        cancelKeyChange();
                    } else {
                        tryAssignKey(e.getKeyCode());
                    }
                    e.consume();
                } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    if (hasUnsavedChanges) keyBindings.loadBindingsFromFile();
                    hasUnsavedChanges = false;
                    dispose();
                }
            }
        });
        this.addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                KeyBindingPanel.this.requestFocusInWindow();
                hasUnsavedChanges = false;
            }

            @Override
            public void windowClosing(WindowEvent e) {
                if (actionToChange != null) cancelKeyChange();
                if (hasUnsavedChanges) keyBindings.loadBindingsFromFile();
                hasUnsavedChanges = false;
            }
        });
    }

    private void startListeningFor(KeyBindings.GameAction action, JButton button) {
        if (actionToChange != null) {
            JButton previousButton = actionButtons.get(actionToChange);
            if (previousButton != null) {
                previousButton.setText(keyBindings.getKeyText(keyBindings.getKeyCode(actionToChange)));
                previousButton.setEnabled(true);
            }
        }
        actionToChange = action;
        button.setText("PRESS KEY");
        button.setEnabled(false);
        waitingForKeyLabel.setText("Waiting for key for: \"" + action.getDescription() + "\"");
        waitingForKeyLabel.setFont(WAITING_FONT);
        waitingForKeyLabel.setForeground(Color.ORANGE);
        cancelButtonListening.setVisible(true);
        this.requestFocusInWindow();
    }

    private void tryAssignKey(int newKeyCode) {
        if (actionToChange == null) return;
        JButton currentButton = actionButtons.get(actionToChange);

        KeyBindings.GameAction conflictingAction = keyBindings.getActionForKey(newKeyCode);
        if (conflictingAction != null && conflictingAction != actionToChange) {
            JOptionPane.showMessageDialog(this,
                    "The key '" + KeyEvent.getKeyText(newKeyCode) + "' is already assigned to: " + conflictingAction.getDescription(),
                    "Key Conflict", JOptionPane.ERROR_MESSAGE);
            game.playSoundEffect("error");
            // Don't cancel, let the user try another key
        } else {
            keyBindings.setKeyCode(actionToChange, newKeyCode);
            currentButton.setText(keyBindings.getKeyText(newKeyCode));
            hasUnsavedChanges = true;
            finishListening();
        }
    }

    private void cancelKeyChange() {
        if (actionToChange != null) {
            JButton button = actionButtons.get(actionToChange);
            button.setText(keyBindings.getKeyText(keyBindings.getKeyCode(actionToChange)));
            finishListening();
        }
    }

    private void finishListening() {
        if (actionToChange != null) {
            actionButtons.get(actionToChange).setEnabled(true);
        }
        actionToChange = null;
        waitingForKeyLabel.setText("Click an action's key to change it.");
        waitingForKeyLabel.setFont(LABEL_FONT);
        waitingForKeyLabel.setForeground(TEXT_COLOR);
        cancelButtonListening.setVisible(false);
    }

    private void updateAllKeyButtonLabels() {
        for (Map.Entry<KeyBindings.GameAction, JButton> entry : actionButtons.entrySet()) {
            entry.getValue().setText(keyBindings.getKeyText(keyBindings.getKeyCode(entry.getKey())));
        }
    }

    @Override
    public void setVisible(boolean b) {
        if (b) { // Reset state every time dialog is opened
            if (actionToChange != null) cancelKeyChange();
            hasUnsavedChanges = false;
            keyBindings.loadBindingsFromFile(); // Load latest saved bindings
            updateAllKeyButtonLabels();
        }
        super.setVisible(b);
    }

    // --- Styling Methods ---

    private void styleActionButton(JButton button, Dimension preferredSize) {
        button.setBackground(BUTTON_BG_COLOR);
        button.setForeground(KEY_TEXT_COLOR);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(BUTTON_BORDER_COLOR), new EmptyBorder(5, 10, 5, 10)));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.setPreferredSize(preferredSize);
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (button.isEnabled()) button.setBackground(BUTTON_HOVER_BG_COLOR);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (button.isEnabled()) button.setBackground(BUTTON_BG_COLOR);
            }
        });
    }

    private void styleUtilityButton(JButton button, Color bgColor) {
        button.setFont(BUTTON_FONT_SMALL);
        button.setForeground(TEXT_COLOR);
        button.setBackground(bgColor);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(bgColor.darker()), new EmptyBorder(10, 18, 10, 18)));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                button.setBackground(bgColor.brighter());
            }

            @Override
            public void mouseExited(MouseEvent e) {
                button.setBackground(bgColor);
            }
        });
    }
}