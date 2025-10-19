// FILE: /NetworkOpSim-Multiplayer/NetworkOpSim-Client/src/main/java/com/networkopsim/client/view/panels/SettingsPanel.java
// ================================================================================

package com.networkopsim.client.view.panels;

import com.networkopsim.client.core.NetworkGame;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Hashtable;
import java.util.Objects;

public class SettingsPanel extends JPanel {
    private final NetworkGame game;
    private JSlider volumeSlider;
    private JCheckBox muteCheckbox;
    private JLabel volumeValueLabel;
    private boolean isProgrammaticallyChangingSlider = false;

    // UI Constants
    private static final Color BACKGROUND_COLOR_START = new Color(30, 30, 40);
    private static final Color BACKGROUND_COLOR_END = new Color(45, 45, 55);
    private static final Color TEXT_COLOR = Color.WHITE;
    private static final Color VALUE_TEXT_COLOR = Color.CYAN;
    private static final Font TITLE_FONT = new Font("Arial", Font.BOLD, 36);
    private static final Font LABEL_FONT = new Font("Arial", Font.BOLD, 18);
    private static final Font CHECKBOX_FONT = new Font("Arial", Font.PLAIN, 16);
    private static final Font BUTTON_FONT = new Font("Arial", Font.BOLD, 18);
    private static final Color BUTTON_BG_COLOR = new Color(100, 100, 110);
    private static final Color BUTTON_BORDER_COLOR = new Color(70, 70, 80);

    public SettingsPanel(NetworkGame game) {
        this.game = Objects.requireNonNull(game, "NetworkGame instance cannot be null");
        setLayout(new GridBagLayout());
        setPreferredSize(new Dimension(NetworkGame.WINDOW_WIDTH, NetworkGame.WINDOW_HEIGHT));

        JPanel settingsContent = new JPanel();
        settingsContent.setLayout(new BoxLayout(settingsContent, BoxLayout.Y_AXIS));
        settingsContent.setOpaque(false);
        settingsContent.setBorder(BorderFactory.createEmptyBorder(30, 50, 30, 50));

        settingsContent.add(createTitleLabel("Settings"));
        settingsContent.add(Box.createVerticalStrut(40));
        settingsContent.add(createVolumeControlPanel());
        settingsContent.add(Box.createVerticalStrut(15));
        settingsContent.add(createMuteControlPanel());
        settingsContent.add(Box.createVerticalStrut(30));
        settingsContent.add(createKeyBindingButtonPanel());
        settingsContent.add(Box.createVerticalGlue());
        settingsContent.add(createBackButtonPanel());

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.CENTER;
        add(settingsContent, gbc);

        updateUIFromState();
    }

    private JLabel createTitleLabel(String text) {
        JLabel titleLabel = new JLabel(text, SwingConstants.CENTER);
        titleLabel.setFont(TITLE_FONT);
        titleLabel.setForeground(TEXT_COLOR);
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        return titleLabel;
    }

    private JPanel createVolumeControlPanel() {
        JPanel volumePanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 5));
        volumePanel.setOpaque(false);
        volumePanel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel volumeLabel = new JLabel("Master Volume:");
        styleLabel(volumeLabel);

        volumeSlider = new JSlider(0, 100, (int) (game.getMasterVolume() * 100));
        volumeSlider.setPreferredSize(new Dimension(350, 60));
        volumeSlider.setOpaque(false);
        volumeSlider.setMajorTickSpacing(25);
        volumeSlider.setPaintTicks(true);
        volumeSlider.setPaintLabels(true);
        volumeSlider.setForeground(TEXT_COLOR);

        Hashtable<Integer, JLabel> labelTable = new Hashtable<>();
        Font sliderLabelFont = LABEL_FONT.deriveFont(12f);
        JLabel[] labels = {new JLabel("0%"), new JLabel("25%"), new JLabel("50%"), new JLabel("75%"), new JLabel("100%")};
        for (int i = 0; i < labels.length; i++) {
            labels[i].setFont(sliderLabelFont);
            labels[i].setForeground(TEXT_COLOR);
            labelTable.put(i * 25, labels[i]);
        }
        volumeSlider.setLabelTable(labelTable);
        volumeSlider.addChangeListener(new VolumeSliderListener());

        volumeValueLabel = new JLabel(String.format("%d%%", volumeSlider.getValue()));
        styleLabel(volumeValueLabel);
        volumeValueLabel.setForeground(VALUE_TEXT_COLOR);
        volumeValueLabel.setPreferredSize(new Dimension(55, 30));

        volumePanel.add(volumeLabel);
        volumePanel.add(volumeSlider);
        volumePanel.add(volumeValueLabel);
        return volumePanel;
    }

    private JPanel createMuteControlPanel() {
        JPanel mutePanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        mutePanel.setOpaque(false);
        mutePanel.setAlignmentX(Component.CENTER_ALIGNMENT);

        muteCheckbox = new JCheckBox(" Mute All Audio");
        muteCheckbox.setFont(CHECKBOX_FONT);
        muteCheckbox.setForeground(TEXT_COLOR);
        muteCheckbox.setOpaque(false);
        muteCheckbox.setFocusPainted(false);
        muteCheckbox.setIconTextGap(10);
        muteCheckbox.addActionListener(new MuteCheckboxListener());
        mutePanel.add(muteCheckbox);
        return mutePanel;
    }

    private JPanel createKeyBindingButtonPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        panel.setOpaque(false);
        panel.setAlignmentX(Component.CENTER_ALIGNMENT);
        JButton keyBindingButton = new JButton("Configure Key Bindings");
        keyBindingButton.setFont(BUTTON_FONT.deriveFont(16f));
        styleStandardButton(keyBindingButton, BUTTON_BG_COLOR, BUTTON_BORDER_COLOR);
        keyBindingButton.addActionListener(e -> game.showKeyBindingDialog());
        panel.add(keyBindingButton);
        return panel;
    }

    private JPanel createBackButtonPanel() {
        JPanel backButtonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        backButtonPanel.setOpaque(false);
        backButtonPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
        backButtonPanel.setBorder(BorderFactory.createEmptyBorder(20, 0, 0, 0));
        JButton backButton = new JButton("Back to Menu");
        backButton.setFont(BUTTON_FONT);
        styleStandardButton(backButton, BUTTON_BG_COLOR, BUTTON_BORDER_COLOR);
        backButton.addActionListener(e -> game.returnToMenu());
        backButtonPanel.add(backButton);
        return backButtonPanel;
    }

    private void styleLabel(JLabel label) {
        label.setForeground(TEXT_COLOR);
        label.setFont(LABEL_FONT);
    }

    private void styleStandardButton(JButton button, Color bgColor, Color borderColor) {
        button.setForeground(TEXT_COLOR);
        button.setBackground(bgColor);
        button.setFocusPainted(false);
        button.setContentAreaFilled(false);
        Border line = BorderFactory.createLineBorder(borderColor, 2);
        Border empty = BorderFactory.createEmptyBorder(10, 20, 10, 20);
        button.setBorder(BorderFactory.createCompoundBorder(line, empty));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.addMouseListener(new MouseAdapter() {
            final Color originalColor = button.getBackground();
            final Color hoverColor = bgColor.brighter();
            @Override public void mouseEntered(MouseEvent e) { if(button.isEnabled()) button.setBackground(hoverColor); }
            @Override public void mouseExited(MouseEvent e) { if(button.isEnabled()) button.setBackground(originalColor); }
        });
    }

    public void updateUIFromState() {
        if (SwingUtilities.isEventDispatchThread()) {
            doUpdateUI();
        } else {
            SwingUtilities.invokeLater(this::doUpdateUI);
        }
    }

    private void doUpdateUI() {
        isProgrammaticallyChangingSlider = true;
        int sliderValue = (int) (game.getMasterVolume() * 100);
        volumeSlider.setValue(sliderValue);
        volumeValueLabel.setText(String.format("%d%%", sliderValue));
        muteCheckbox.setSelected(game.isMuted());
        updateVolumeSliderState();
        isProgrammaticallyChangingSlider = false;
    }

    private void updateVolumeSliderState() {
        boolean enabled = !game.isMuted();
        volumeSlider.setEnabled(enabled);
        volumeValueLabel.setEnabled(enabled);
    }

    private class VolumeSliderListener implements ChangeListener {
        @Override
        public void stateChanged(ChangeEvent e) {
            if (isProgrammaticallyChangingSlider) return;
            int sliderValue = volumeSlider.getValue();
            volumeValueLabel.setText(String.format("%d%%", sliderValue));
            float newVolume = sliderValue / 100.0f;
            game.setMasterVolume(newVolume);
            if (game.isMuted() && newVolume > 0.001f) {
                game.toggleMute(); // Automatically unmute if slider is moved
            }
        }
    }

    private class MuteCheckboxListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            game.toggleMute();
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
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