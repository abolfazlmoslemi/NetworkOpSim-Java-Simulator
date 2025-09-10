// ================================================================================
// FILE: NetworkGame.java (کد کامل و نهایی با سیستم لاگ)
// ================================================================================
package com.networkopsim.game;

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
    private GamePanel gamePanel;
    private MenuPanel menuPanel;
    private SettingsPanel settingsPanel;
    private LevelSelectionPanel levelSelectionPanel;
    private StorePanel storeDialog;
    private GameState gameState;
    private KeyBindings keyBindings;
    private KeyBindingPanel keyBindingDialog;
    private int currentLevel = 1;
    private float masterVolume = 0.7f;
    private boolean isMuted = false;
    private Clip backgroundMusic;
    private boolean backgroundMusicWasPlaying = false;
    private CardLayout cardLayout;
    private JPanel mainPanelContainer;
    private static final double VOLUME_POWER_FACTOR = 1.8;
    private static final float MIN_AUDIBLE_DB_TARGET = -50.0f;
    private static final float SILENCE_DB = -80.0f;

    public static class TemporaryMessage {
        public final String message;
        public final Color color;
        public final long displayUntilTimestamp;
        public TemporaryMessage(String message, Color color, long displayUntilTimestamp) {
            this.message = message;
            this.color = color;
            this.displayUntilTimestamp = displayUntilTimestamp;
        }
    }
    private TemporaryMessage currentTemporaryMessage = null;
    private Timer temporaryMessageTimer;

    public NetworkGame() {
        logger.info("========================================================");
        logger.info("Network Operator Simulator is starting up...");
        setTitle("Network Operator Simulator");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setResizable(false);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                handleExitRequest();
            }
        });
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

        checkForExistingSave();
        logger.info("Application initialized successfully. Showing main menu.");
    }

    private void handleExitRequest() {
        int choice = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to exit?",
                "Exit Game",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);
        if (choice == JOptionPane.YES_OPTION) {
            shutdownGame();
        } else {
            logger.debug("User cancelled exit request.");
        }
    }

    private void initSounds() {
        try {
            String musicPath = "/assets/sounds/background_music.wav";
            InputStream audioSrc = getClass().getResourceAsStream(musicPath);

            if (audioSrc == null) {
                logger.error("Background music file not found in resources: {}", musicPath);
                JOptionPane.showMessageDialog(this,
                        "Background music file (background_music.wav) not found in resources.\nExpected path: " + musicPath,
                        "Resource Error", JOptionPane.ERROR_MESSAGE);
                backgroundMusic = null;
                return;
            }
            InputStream bufferedIn = new BufferedInputStream(audioSrc);
            AudioInputStream audioStream = AudioSystem.getAudioInputStream(bufferedIn);

            backgroundMusic = AudioSystem.getClip();
            backgroundMusic.open(audioStream);
            setVolume(backgroundMusic, this.masterVolume);
            playBackgroundMusicIfNeeded();
            logger.info("Sound system initialized successfully.");

        } catch (UnsupportedAudioFileException | LineUnavailableException | IOException e) {
            backgroundMusic = null;
            logger.error("Error loading or playing background music", e);
            JOptionPane.showMessageDialog(this,
                    "Error loading or playing background music:\n" + e.getMessage(),
                    "Sound Error", JOptionPane.ERROR_MESSAGE);
        } catch (Exception e) {
            backgroundMusic = null;
            logger.error("An unexpected error occurred during sound initialization", e);
        }
    }

    private void playBackgroundMusicIfNeeded() {
        if (backgroundMusic != null && backgroundMusic.isOpen() && !isMuted && !backgroundMusic.isRunning()) {
            backgroundMusic.loop(Clip.LOOP_CONTINUOUSLY);
            logger.debug("Background music started playing.");
        }
    }

    private void stopBackgroundMusic() {
        if (backgroundMusic != null && backgroundMusic.isRunning()) {
            backgroundMusic.stop();
            logger.debug("Background music stopped.");
        }
    }

    private void setVolume(Clip clip, float linearVolume) {
        if (clip == null || !clip.isOpen() || !clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            return;
        }
        try {
            FloatControl gainControl = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
            float minDbPossible = gainControl.getMinimum();
            float maxDbPossible = gainControl.getMaximum();
            float clampedLinearVolume = Math.max(0.0f, Math.min(1.0f, linearVolume));
            float targetDb;
            if (clampedLinearVolume <= 0.001f) {
                targetDb = SILENCE_DB;
            } else {
                float perceptuallyScaledVolume = (float) Math.pow(clampedLinearVolume, VOLUME_POWER_FACTOR);
                float effectiveMaxDb = Math.min(maxDbPossible, 6.0f);
                float rangeDb = effectiveMaxDb - MIN_AUDIBLE_DB_TARGET;
                targetDb = MIN_AUDIBLE_DB_TARGET + (rangeDb * perceptuallyScaledVolume);
            }
            targetDb = Math.max(minDbPossible, Math.min(targetDb, maxDbPossible));
            if (clampedLinearVolume <= 0.001f) {
                if (minDbPossible <= SILENCE_DB) {
                    targetDb = SILENCE_DB;
                } else {
                    targetDb = minDbPossible;
                }
            }
            gainControl.setValue(targetDb);
        } catch (Exception e) {
            logger.warn("Could not set volume on clip.", e);
        }
    }

    public void playSoundEffect(String soundName) {
        if (isMuted) return;
        new Thread(() -> {
            Clip clip = null;
            AudioInputStream audioStream = null;
            InputStream soundFileStream = null;
            InputStream bufferedIn = null;
            try {
                String soundPath = "/assets/sounds/" + soundName + ".wav";
                soundFileStream = getClass().getResourceAsStream(soundPath);
                if (soundFileStream == null) {
                    logger.warn("Sound effect not found: {}", soundPath);
                    return;
                }
                bufferedIn = new BufferedInputStream(soundFileStream);
                audioStream = AudioSystem.getAudioInputStream(bufferedIn);
                clip = AudioSystem.getClip();
                final Clip finalClip = clip;
                final AudioInputStream finalAudioStream = audioStream;
                final InputStream finalBufferedIn = bufferedIn;
                final InputStream finalSoundFileStream = soundFileStream;
                clip.addLineListener(event -> {
                    if (event.getType() == LineEvent.Type.STOP) {
                        if (finalClip != null && finalClip.isOpen()) finalClip.close();
                        try { if (finalAudioStream != null) finalAudioStream.close(); } catch (IOException ioe) { /* ignore */ }
                        try { if (finalBufferedIn != null) finalBufferedIn.close(); } catch (IOException ioe) { /* ignore */ }
                        try { if (finalSoundFileStream != null) finalSoundFileStream.close(); } catch (IOException ioe) { /* ignore */ }
                    }
                });
                clip.open(audioStream);
                setVolume(clip, masterVolume);
                clip.start();
            } catch (Exception e) {
                logger.error("Failed to play sound effect '{}'", soundName, e);
                if (clip != null && clip.isOpen()) clip.close();
                try { if (audioStream != null) audioStream.close(); } catch (IOException ioe) {/* ignore */}
                try { if (bufferedIn != null) bufferedIn.close(); } catch (IOException ioe) {/* ignore */}
                try { if (soundFileStream != null) soundFileStream.close(); } catch (IOException ioe) {/* ignore */}
            }
        }).start();
    }

    public float getMasterVolume() { return masterVolume; }
    public void setMasterVolume(float volume) {
        this.masterVolume = Math.max(0.0f, Math.min(1.0f, volume));
        logger.info("Master volume set to {}%", (int)(this.masterVolume * 100));
        setVolume(backgroundMusic, this.masterVolume);
        if (settingsPanel != null && settingsPanel.isShowing()) {
            settingsPanel.updateUIFromGameState();
        }
    }
    public boolean isMuted() { return isMuted; }
    public void toggleMute() {
        isMuted = !isMuted;
        logger.info("Audio muted status set to: {}", isMuted);
        if (backgroundMusic != null && backgroundMusic.isOpen()) {
            if (isMuted) {
                if (backgroundMusic.isRunning()) {
                    backgroundMusic.stop();
                    backgroundMusicWasPlaying = true;
                }
            } else {
                boolean shouldBePlayingNow = false;
                for(Component comp : mainPanelContainer.getComponents()){
                    if(comp.isVisible() && (comp == menuPanel || comp == levelSelectionPanel || comp == settingsPanel)){
                        shouldBePlayingNow = true;
                        break;
                    }
                }
                if(shouldBePlayingNow || backgroundMusicWasPlaying){
                    playBackgroundMusicIfNeeded();
                }
                backgroundMusicWasPlaying = false;
            }
        }
        if (settingsPanel != null && settingsPanel.isShowing()) {
            settingsPanel.updateUIFromGameState();
        }
    }

    public void startGame() {
        logger.info("Attempting to start game for level {}.", currentLevel);
        if (backgroundMusic != null && backgroundMusic.isRunning()) {
            backgroundMusicWasPlaying = true;
        } else {
            backgroundMusicWasPlaying = false;
        }
        stopBackgroundMusic();
        clearTemporaryMessage();
        if (gamePanel == null) {
            logger.error("Cannot start game, GamePanel is null!");
            return;
        }
        cardLayout.show(mainPanelContainer, "GamePanel");
        gamePanel.initializeLevel(currentLevel);
        SwingUtilities.invokeLater(gamePanel::requestFocusInWindow);
    }

    public void showSettings() {
        logger.debug("Showing settings panel.");
        playBackgroundMusicIfNeeded();
        clearTemporaryMessage();
        if (settingsPanel == null) return;
        cardLayout.show(mainPanelContainer, "SettingsMenu");
        settingsPanel.updateUIFromGameState();
        SwingUtilities.invokeLater(settingsPanel::requestFocusInWindow);
    }

    public void showLevelSelection() {
        logger.debug("Showing level selection panel.");
        playBackgroundMusicIfNeeded();
        clearTemporaryMessage();
        if (levelSelectionPanel == null) return;
        levelSelectionPanel.updateLevelButtons();
        cardLayout.show(mainPanelContainer, "LevelSelection");
        SwingUtilities.invokeLater(levelSelectionPanel::requestFocusInWindow);
    }

    public void returnToMenu() {
        logger.info("Returning to main menu.");
        playBackgroundMusicIfNeeded();
        clearTemporaryMessage();
        boolean gamePanelWasVisible = false;
        for (Component comp : mainPanelContainer.getComponents()) {
            if (comp.isVisible() && comp == gamePanel) {
                gamePanelWasVisible = true;
                break;
            }
        }
        if (gamePanelWasVisible && gamePanel != null && (gamePanel.isGameRunning() || gamePanel.isGamePaused())) {
            gamePanel.stopSimulation();
        }

        GameStateManager.deleteSaveFile();

        if (menuPanel == null) return;
        cardLayout.show(mainPanelContainer, "MainMenu");
        menuPanel.updateStartButtonLevel(currentLevel);
        SwingUtilities.invokeLater(menuPanel::requestFocusInWindow);
    }

    public void showStore() {
        if (gamePanel == null || !gamePanel.isGamePaused()) {
            logger.warn("Attempted to open store while game was not paused. Request denied.");
            if (!isMuted()) playSoundEffect("error");
            return;
        }
        logger.debug("Showing store panel.");
        clearTemporaryMessage();
        if (storeDialog == null) {
            try {
                storeDialog = new StorePanel(this, gamePanel);
            } catch (Exception e) {
                if (gamePanel.isGamePaused()) gamePanel.pauseGame(false);
                logger.error("Failed to create StorePanel dialog.", e);
                JOptionPane.showMessageDialog(this, "Error opening store!", "Store Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
        }
        try {
            storeDialog.updateCoinsDisplay(gameState.getCoins());
            storeDialog.setLocationRelativeTo(this);
            storeDialog.setVisible(true);
            if (gamePanel.isShowing()) {
                SwingUtilities.invokeLater(gamePanel::requestFocusInWindow);
            }
        } catch (Exception e) {
            logger.error("Exception while showing store dialog.", e);
            if (gamePanel.isGamePaused()) {
                gamePanel.pauseGame(false);
                SwingUtilities.invokeLater(gamePanel::requestFocusInWindow);
            }
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
        if (settingsPanel != null && settingsPanel.isShowing()) {
            SwingUtilities.invokeLater(settingsPanel::requestFocusInWindow);
        } else if (menuPanel != null && menuPanel.isShowing()) {
            SwingUtilities.invokeLater(menuPanel::requestFocusInWindow);
        }
    }

    public int getCurrentLevel() { return currentLevel; }
    public void setLevel(int level) {
        if (level >= 1 && level <= gameState.getMaxLevels()) {
            logger.info("Current level set to {}.", level);
            this.currentLevel = level;
            gameState.setCurrentSelectedLevel(level);
            if (menuPanel != null) menuPanel.updateStartButtonLevel(level);
        } else {
            logger.warn("Attempted to set an invalid level: {}", level);
        }
    }

    public GameState getGameState() { return gameState; }
    public KeyBindings getKeyBindings() { return keyBindings; }

    public void shutdownGame() {
        logger.info("Shutdown sequence initiated...");
        clearTemporaryMessage();
        if (gamePanel != null && (gamePanel.isGameRunning() || gamePanel.isGamePaused())) {
            gamePanel.stopSimulation();
        }
        if (keyBindings != null) {
            keyBindings.saveBindingsToFile();
        }
        if (backgroundMusic != null) {
            if (backgroundMusic.isRunning()) backgroundMusic.stop();
            if (backgroundMusic.isOpen()) backgroundMusic.close();
        }
        if (storeDialog != null) { storeDialog.dispose(); }
        if (keyBindingDialog != null) { keyBindingDialog.dispose(); }
        if (temporaryMessageTimer != null && temporaryMessageTimer.isRunning()) temporaryMessageTimer.stop();
        dispose();
        logger.info("Application shutdown complete. Exiting.");
        java.lang.System.exit(0);
    }

    public void showTemporaryMessage(String message, Color color, int durationMs) {
        logger.debug("Displaying temporary message: '{}'", message);
        currentTemporaryMessage = new TemporaryMessage(message, color, java.lang.System.currentTimeMillis() + durationMs);
        if (temporaryMessageTimer != null && temporaryMessageTimer.isRunning()) {
            temporaryMessageTimer.stop();
        }
        temporaryMessageTimer = new Timer(durationMs + 100, e -> {
            clearTemporaryMessage();
        });
        temporaryMessageTimer.setRepeats(false);
        temporaryMessageTimer.start();
        if (gamePanel != null && gamePanel.isShowing()) gamePanel.repaint();
        else if (menuPanel != null && menuPanel.isShowing()) menuPanel.repaint();
        else if (settingsPanel != null && settingsPanel.isShowing()) settingsPanel.repaint();
        else if (levelSelectionPanel != null && levelSelectionPanel.isShowing()) levelSelectionPanel.repaint();
    }

    public TemporaryMessage getTemporaryMessage() {
        if (currentTemporaryMessage != null && java.lang.System.currentTimeMillis() < currentTemporaryMessage.displayUntilTimestamp) {
            return currentTemporaryMessage;
        }
        if (currentTemporaryMessage != null && java.lang.System.currentTimeMillis() >= currentTemporaryMessage.displayUntilTimestamp) {
            clearTemporaryMessage();
        }
        return null;
    }

    public void clearTemporaryMessage() {
        boolean needsRepaint = (currentTemporaryMessage != null);
        currentTemporaryMessage = null;
        if (temporaryMessageTimer != null && temporaryMessageTimer.isRunning()) {
            temporaryMessageTimer.stop();
        }
        if (needsRepaint) {
            if (gamePanel != null && gamePanel.isShowing()) gamePanel.repaint();
            else if (menuPanel != null && menuPanel.isShowing()) menuPanel.repaint();
            else if (settingsPanel != null && settingsPanel.isShowing()) settingsPanel.repaint();
            else if (levelSelectionPanel != null && levelSelectionPanel.isShowing()) levelSelectionPanel.repaint();
        }
    }

    private void checkForExistingSave() {
        GameStateManager.SaveData saveData = GameStateManager.loadGameState();
        if (saveData != null) {
            logger.info("Valid saved game data found. Prompting user to resume.");
            int choice = JOptionPane.showConfirmDialog(
                    this,
                    "An unfinished game was found. Do you want to resume it?",
                    "Resume Game?",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE
            );

            if (choice == JOptionPane.YES_OPTION) {
                logger.info("User chose to resume the saved game.");
                cardLayout.show(mainPanelContainer, "GamePanel");
                gamePanel.loadFromSaveData(saveData);
            } else {
                logger.info("User chose not to resume. Deleting saved game file.");
                GameStateManager.deleteSaveFile();
            }
        } else {
            logger.info("No existing save file found. Starting fresh.");
        }
    }

// فایل: NetworkGame.java

// فایل: NetworkGame.java

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                boolean nimbusFound = false;
                for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                    if ("Nimbus".equals(info.getName())) { UIManager.setLookAndFeel(info.getClassName()); nimbusFound = true; break; }
                }
                if (!nimbusFound) UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                LoggerFactory.getLogger(NetworkGame.class).warn("Could not set Look and Feel.", e);
            }

            // ===== کد اصلاح شده برای رفع تداخل نام =====
            Logger mainLogger = LoggerFactory.getLogger(NetworkGame.class);
            // استفاده از نام کامل کلاس برای جلوگیری از تداخل
            String workingDir = java.lang.System.getProperty("user.dir");
            mainLogger.info("========================================================");
            mainLogger.info("Application starting up...");
            mainLogger.info("Current Working Directory: {}", workingDir);
            mainLogger.info("========================================================");
            // ===============================================

            new NetworkGame();
        });
    }
}