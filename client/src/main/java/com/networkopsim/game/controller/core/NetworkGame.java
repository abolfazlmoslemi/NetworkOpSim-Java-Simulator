// ===== File: NetworkGame.java (FINAL CORRECTED VERSION) =====
// ===== MODULE: client =====

package com.networkopsim.game.controller.core;

import com.networkopsim.client.GameClient;
import com.networkopsim.game.model.state.GameState;
import com.networkopsim.game.utils.KeyBindings;
import com.networkopsim.game.view.dialogs.KeyBindingPanel;
import com.networkopsim.game.view.dialogs.StorePanel;
import com.networkopsim.game.view.panels.GamePanel;
import com.networkopsim.game.view.panels.LevelSelectionPanel;
import com.networkopsim.game.view.panels.MenuPanel;
import com.networkopsim.game.view.panels.SettingsPanel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

public class NetworkGame extends JFrame {
  private static final Logger logger = LoggerFactory.getLogger(NetworkGame.class);

  public static final int WINDOW_WIDTH = 1200;
  public static final int WINDOW_HEIGHT = 800;
  private static final String SERVER_HOST = "localhost";
  private static final int SERVER_PORT = 12345;

  private final GameState gameState;
  private final KeyBindings keyBindings;
  private final CardLayout cardLayout;
  private final JPanel mainPanelContainer;
  private GameClient gameClient;

  private final GamePanel gamePanel;
  private final MenuPanel menuPanel;
  private final SettingsPanel settingsPanel;
  private final LevelSelectionPanel levelSelectionPanel;
  private StorePanel storeDialog;
  private KeyBindingPanel keyBindingDialog;

  private int currentLevel = 1;

  private float masterVolume = 0.7f;
  private boolean isMuted = false;
  private Clip backgroundMusic;
  private boolean backgroundMusicWasPlaying = false;
  private static final double VOLUME_POWER_FACTOR = 1.8;
  private static final float MIN_AUDIBLE_DB_TARGET = -50.0f;
  private static final float SILENCE_DB = -80.0f;

  public static class TemporaryMessage {
    public final String message; public final Color color; public final long displayUntilTimestamp;
    public TemporaryMessage(String message, Color color, long ts) { this.message = message; this.color = color; this.displayUntilTimestamp = ts; }
  }
  private TemporaryMessage currentTemporaryMessage = null;
  private Timer temporaryMessageTimer;

  public NetworkGame() {
    logger.info("Network Operator Simulator Client is starting up...");
    setTitle("Network Operator Simulator - Client");
    setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
    setResizable(false);
    addWindowListener(new WindowAdapter() { @Override public void windowClosing(WindowEvent e) { handleExitRequest(); } });

    gameState = new GameState();
    keyBindings = new KeyBindings();
    currentLevel = gameState.getCurrentSelectedLevel();

    cardLayout = new CardLayout();
    mainPanelContainer = new JPanel(cardLayout);
    menuPanel = new MenuPanel(this);
    gamePanel = new GamePanel(this);
    settingsPanel = new SettingsPanel(this);
    levelSelectionPanel = new LevelSelectionPanel(this);

    mainPanelContainer.add(menuPanel, "MainMenu");
    mainPanelContainer.add(levelSelectionPanel, "LevelSelection");
    mainPanelContainer.add(gamePanel, "GamePanel");
    mainPanelContainer.add(settingsPanel, "SettingsMenu");
    setContentPane(mainPanelContainer);

    initSounds();
    pack();
    setLocationRelativeTo(null);
    setVisible(true);
    menuPanel.requestFocusInWindow();
    logger.info("Application initialized successfully. Showing main menu.");
  }

  public void startGame() {
    logger.info("Attempting to connect to server and start game for level {}.", currentLevel);
    if (backgroundMusic != null && backgroundMusic.isRunning()) {
      backgroundMusicWasPlaying = true;
    } else {
      backgroundMusicWasPlaying = false;
    }
    stopBackgroundMusic();
    clearTemporaryMessage();

    if (gameClient != null) {
      gameClient.close();
    }

    // CRITICAL CHANGE: Use the new constructor and pass the level number.
    gameClient = new GameClient(SERVER_HOST, SERVER_PORT, this.gamePanel, this.currentLevel);
    new Thread(gameClient, "GameClientThread").start();

    gamePanel.initializeForNetworkGame(currentLevel);

    // CRITICAL CHANGE: The initialization action is now sent by GameClient itself.

    cardLayout.show(mainPanelContainer, "GamePanel");
    SwingUtilities.invokeLater(gamePanel::requestFocusInWindow);
  }

  public void returnToMenu() {
    logger.info("Returning to main menu.");
    playBackgroundMusicIfNeeded();
    clearTemporaryMessage();

    if (gameClient != null) {
      gameClient.close();
      gameClient = null;
    }

    cardLayout.show(mainPanelContainer, "MainMenu");
    menuPanel.updateStartButtonLevel(currentLevel);
    SwingUtilities.invokeLater(menuPanel::requestFocusInWindow);
  }

  public void shutdownGame() {
    logger.info("Shutdown sequence initiated...");
    if (gameClient != null) {
      gameClient.close();
    }
    clearTemporaryMessage();
    keyBindings.saveBindingsToFile();
    if (backgroundMusic != null) {
      if (backgroundMusic.isRunning()) backgroundMusic.stop();
      if (backgroundMusic.isOpen()) backgroundMusic.close();
    }
    if (storeDialog != null) storeDialog.dispose();
    if (keyBindingDialog != null) keyBindingDialog.dispose();
    if (temporaryMessageTimer != null && temporaryMessageTimer.isRunning()) temporaryMessageTimer.stop();
    dispose();
    logger.info("Application shutdown complete. Exiting.");
    java.lang.System.exit(0);
  }

  private void handleExitRequest() {
    int choice = JOptionPane.showConfirmDialog(this, "Are you sure you want to exit?", "Exit Game", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
    if (choice == JOptionPane.YES_OPTION) {
      shutdownGame();
    }
  }

  public void showSettings() {
    logger.debug("Showing settings panel.");
    playBackgroundMusicIfNeeded();
    clearTemporaryMessage();
    cardLayout.show(mainPanelContainer, "SettingsMenu");
    settingsPanel.updateUIFromGameState();
    SwingUtilities.invokeLater(settingsPanel::requestFocusInWindow);
  }

  public void showLevelSelection() {
    logger.debug("Showing level selection panel.");
    playBackgroundMusicIfNeeded();
    clearTemporaryMessage();
    levelSelectionPanel.updateLevelButtons();
    cardLayout.show(mainPanelContainer, "LevelSelection");
    SwingUtilities.invokeLater(levelSelectionPanel::requestFocusInWindow);
  }

  public void showStore() {
    if (!gamePanel.isGamePaused()) {
      logger.warn("Attempted to open store while game was not paused. Request denied.");
      if (!isMuted()) playSoundEffect("error");
      return;
    }
    logger.debug("Showing store panel.");
    clearTemporaryMessage();
    if (storeDialog == null) {
      storeDialog = new StorePanel(this, gamePanel);
    }
    storeDialog.updateCoinsDisplay(gamePanel.getGameState().getCoins());
    storeDialog.setLocationRelativeTo(this);
    storeDialog.setVisible(true);
    if (gamePanel.isShowing()) {
      SwingUtilities.invokeLater(gamePanel::requestFocusInWindow);
    }
  }

  public void showKeyBindingDialog() {
    logger.debug("Showing key binding dialog.");
    playBackgroundMusicIfNeeded();
    clearTemporaryMessage();
    if (keyBindingDialog != null && keyBindingDialog.isVisible()) {
      keyBindingDialog.toFront();
      return;
    }
    keyBindingDialog = new KeyBindingPanel(this, this.keyBindings);
    keyBindingDialog.setVisible(true);
    if (settingsPanel.isShowing()) {
      SwingUtilities.invokeLater(settingsPanel::requestFocusInWindow);
    } else if (menuPanel.isShowing()) {
      SwingUtilities.invokeLater(menuPanel::requestFocusInWindow);
    }
  }

  private void initSounds() {
    try {
      String musicPath = "/assets/sounds/background_music.wav";
      InputStream audioSrc = getClass().getResourceAsStream(musicPath);
      if (audioSrc == null) { logger.error("Background music file not found in resources: {}", musicPath); backgroundMusic = null; return; }
      InputStream bufferedIn = new BufferedInputStream(audioSrc);
      AudioInputStream audioStream = AudioSystem.getAudioInputStream(bufferedIn);
      backgroundMusic = AudioSystem.getClip();
      backgroundMusic.open(audioStream);
      setVolume(backgroundMusic, this.masterVolume);
      playBackgroundMusicIfNeeded();
      logger.info("Sound system initialized successfully.");
    } catch (Exception e) {
      backgroundMusic = null;
      logger.error("An unexpected error occurred during sound initialization", e);
    }
  }

  public void playSoundEffect(String soundName) { if (isMuted) return; new Thread(() -> { Clip clip = null; AudioInputStream audioStream = null; InputStream soundFileStream = null; InputStream bufferedIn = null; try { String soundPath = "/assets/sounds/" + soundName + ".wav"; soundFileStream = getClass().getResourceAsStream(soundPath); if (soundFileStream == null) { logger.warn("Sound effect not found: {}", soundPath); return; } bufferedIn = new BufferedInputStream(soundFileStream); audioStream = AudioSystem.getAudioInputStream(bufferedIn); clip = AudioSystem.getClip(); final Clip finalClip = clip; clip.addLineListener(event -> { if (event.getType() == LineEvent.Type.STOP) { if (finalClip != null && finalClip.isOpen()) finalClip.close(); } }); clip.open(audioStream); setVolume(clip, masterVolume); clip.start(); } catch (Exception e) { logger.error("Failed to play sound effect '{}'", soundName, e); } }).start(); }
  public void showTemporaryMessage(String message, Color color, int durationMs) { logger.debug("Displaying temporary message: '{}'", message); currentTemporaryMessage = new TemporaryMessage(message, color, java.lang.System.currentTimeMillis() + durationMs); if (temporaryMessageTimer != null && temporaryMessageTimer.isRunning()) { temporaryMessageTimer.stop(); } temporaryMessageTimer = new Timer(durationMs + 100, e -> clearTemporaryMessage()); temporaryMessageTimer.setRepeats(false); temporaryMessageTimer.start(); Component visiblePanel = getVisiblePanel(); if(visiblePanel != null) visiblePanel.repaint(); }
  public TemporaryMessage getTemporaryMessage() { if (currentTemporaryMessage != null && java.lang.System.currentTimeMillis() < currentTemporaryMessage.displayUntilTimestamp) { return currentTemporaryMessage; } if (currentTemporaryMessage != null) { clearTemporaryMessage(); } return null; }
  public void clearTemporaryMessage() { boolean needsRepaint = (currentTemporaryMessage != null); currentTemporaryMessage = null; if (temporaryMessageTimer != null && temporaryMessageTimer.isRunning()) { temporaryMessageTimer.stop(); } if (needsRepaint) { Component visiblePanel = getVisiblePanel(); if(visiblePanel != null) visiblePanel.repaint(); } }
  private Component getVisiblePanel() { for(Component comp : mainPanelContainer.getComponents()) if(comp.isVisible()) return comp; return null; }

  // Getters and Setters
  public GameClient getGameClient() { return gameClient; }
  public int getCurrentLevel() { return currentLevel; }
  public GameState getGameState() { return gameState; }
  public KeyBindings getKeyBindings() { return keyBindings; }
  public float getMasterVolume() { return masterVolume; }
  public boolean isMuted() { return isMuted; }
  public void setLevel(int level) { if (level >= 1 && level <= gameState.getMaxLevels()) { this.currentLevel = level; gameState.setCurrentSelectedLevel(level); if (menuPanel != null) menuPanel.updateStartButtonLevel(level); } else { logger.warn("Attempted to set an invalid level: {}", level); } }

  // Sound methods (unchanged)
  private void playBackgroundMusicIfNeeded() { if (backgroundMusic != null && backgroundMusic.isOpen() && !isMuted && !backgroundMusic.isRunning()) { backgroundMusic.loop(Clip.LOOP_CONTINUOUSLY); } }
  private void stopBackgroundMusic() { if (backgroundMusic != null && backgroundMusic.isRunning()) { backgroundMusic.stop(); } }
  private void setVolume(Clip clip, float linearVolume) { if (clip == null || !clip.isOpen() || !clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) { return; } try { FloatControl gainControl = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN); float minDbPossible = gainControl.getMinimum(); float maxDbPossible = gainControl.getMaximum(); float clampedLinearVolume = Math.max(0.0f, Math.min(1.0f, linearVolume)); float targetDb; if (clampedLinearVolume <= 0.001f) { targetDb = SILENCE_DB; } else { float perceptuallyScaledVolume = (float) Math.pow(clampedLinearVolume, VOLUME_POWER_FACTOR); float effectiveMaxDb = Math.min(maxDbPossible, 6.0f); float rangeDb = effectiveMaxDb - MIN_AUDIBLE_DB_TARGET; targetDb = MIN_AUDIBLE_DB_TARGET + (rangeDb * perceptuallyScaledVolume); } targetDb = Math.max(minDbPossible, Math.min(targetDb, maxDbPossible)); gainControl.setValue(targetDb); } catch (Exception e) { logger.warn("Could not set volume on clip.", e); } }
  public void setMasterVolume(float volume) { this.masterVolume = Math.max(0.0f, Math.min(1.0f, volume)); setVolume(backgroundMusic, this.masterVolume); if (settingsPanel.isShowing()) { settingsPanel.updateUIFromGameState(); } }
  public void toggleMute() { isMuted = !isMuted; if (backgroundMusic != null) { if (isMuted) { if (backgroundMusic.isRunning()) { backgroundMusic.stop(); backgroundMusicWasPlaying = true; } } else { Component visiblePanel = getVisiblePanel(); if (visiblePanel == menuPanel || visiblePanel == levelSelectionPanel || visiblePanel == settingsPanel || backgroundMusicWasPlaying) { playBackgroundMusicIfNeeded(); } backgroundMusicWasPlaying = false; } } if (settingsPanel.isShowing()) { settingsPanel.updateUIFromGameState(); } }
}