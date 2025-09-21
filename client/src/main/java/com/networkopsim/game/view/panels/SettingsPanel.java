// ===== File: SettingsPanel.java (UNCHANGED - Logic is client-side) =====
// ===== MODULE: client =====

package com.networkopsim.game.view.panels;

import com.networkopsim.game.controller.core.NetworkGame;

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

  private static final Color BACKGROUND_COLOR_START = new Color(30, 30, 40);
  private static final Color BACKGROUND_COLOR_END = new Color(45, 45, 55);
  private static final Color TEXT_COLOR = Color.WHITE;
  private static final Color VALUE_TEXT_COLOR = Color.CYAN;
  private static final Font TITLE_FONT = new Font("Arial", Font.BOLD, 36);
  private static final Font LABEL_FONT = new Font("Arial", Font.BOLD, 18);
  private static final Font CHECKBOX_FONT = new Font("Arial", Font.PLAIN, 16);
  private static final Font BUTTON_FONT = new Font("Arial", Font.BOLD, 18);
  private static final Font KEYBIND_BUTTON_FONT = new Font("Arial", Font.BOLD, 16);
  private static final Color BUTTON_BG_COLOR = new Color(100, 100, 110);
  private static final Color BUTTON_BORDER_COLOR = new Color(70, 70, 80);

  private boolean isProgrammaticallyChangingSlider = false;

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

    JPanel volumePanel = createVolumeControlPanel();
    settingsContent.add(volumePanel);
    settingsContent.add(Box.createVerticalStrut(15));

    JPanel mutePanel = createMuteControlPanel();
    settingsContent.add(mutePanel);
    settingsContent.add(Box.createVerticalStrut(30));

    JPanel keyBindingButtonPanel = createKeyBindingButtonPanel();
    settingsContent.add(keyBindingButtonPanel);

    settingsContent.add(Box.createVerticalGlue());

    JPanel backButtonPanel = createBackButtonPanel();
    settingsContent.add(backButtonPanel);
    settingsContent.add(Box.createVerticalStrut(20));

    GridBagConstraints gbc = new GridBagConstraints();
    gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 1.0; gbc.weighty = 1.0;
    gbc.anchor = GridBagConstraints.CENTER;
    add(settingsContent, gbc);

    updateUIFromGameState();
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
    volumeSlider.setMinorTickSpacing(5);
    volumeSlider.setPaintTicks(true);
    volumeSlider.setPaintLabels(true);
    volumeSlider.setForeground(TEXT_COLOR);

    Hashtable<Integer, JLabel> labelTable = new Hashtable<>();
    Font sliderLabelFont = LABEL_FONT.deriveFont(12f);
    JLabel[] labels = {new JLabel("0%"), new JLabel("25%"), new JLabel("50%"), new JLabel("75%"), new JLabel("100%")};
    for(int i = 0; i <= 100; i += 25) {
      labels[i/25].setFont(sliderLabelFont);
      labels[i/25].setForeground(TEXT_COLOR);
      labelTable.put(i, labels[i/25]);
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
    muteCheckbox.setSelected(game.isMuted());
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
    keyBindingButton.setFont(KEYBIND_BUTTON_FONT);
    styleStandardButton(keyBindingButton, BUTTON_BG_COLOR, BUTTON_BORDER_COLOR);
    keyBindingButton.addActionListener(e -> game.showKeyBindingDialog());
    panel.add(keyBindingButton);
    return panel;
  }

  private JPanel createBackButtonPanel() {
    JPanel backButtonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
    backButtonPanel.setOpaque(false);
    backButtonPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
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

  public void updateUIFromGameState() {
    if (SwingUtilities.isEventDispatchThread()) {
      doUpdateUIFromGameState();
    } else {
      SwingUtilities.invokeLater(this::doUpdateUIFromGameState);
    }
  }

  private void doUpdateUIFromGameState() {
    isProgrammaticallyChangingSlider = true;
    int sliderValue = (int) (game.getMasterVolume() * 100);
    if (volumeSlider.getValue() != sliderValue) volumeSlider.setValue(sliderValue);
    if (volumeValueLabel != null) volumeValueLabel.setText(String.format("%d%%", sliderValue));
    if (muteCheckbox.isSelected() != game.isMuted()) muteCheckbox.setSelected(game.isMuted());
    updateVolumeSliderState();
    isProgrammaticallyChangingSlider = false;
  }

  private void updateVolumeSliderState() {
    boolean enabled = !game.isMuted();
    volumeSlider.setEnabled(enabled);
    if (volumeValueLabel != null) volumeValueLabel.setEnabled(enabled);
  }

  private class VolumeSliderListener implements ChangeListener {
    @Override
    public void stateChanged(ChangeEvent e) {
      if (isProgrammaticallyChangingSlider) return;
      int sliderValue = volumeSlider.getValue();
      if (volumeValueLabel != null) volumeValueLabel.setText(String.format("%d%%", sliderValue));
      float newVolume = sliderValue / 100.0f;
      game.setMasterVolume(newVolume);
      if (game.isMuted() && newVolume > 0.001f) game.toggleMute();
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