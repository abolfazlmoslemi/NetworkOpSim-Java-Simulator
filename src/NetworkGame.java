// ===== File: NetworkGame.java =====

// FILE: NetworkGame.java
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
    private float masterVolume = 0.7f; // Linear master volume (0.0 to 1.0)
    private boolean isMuted = false;
    private Clip backgroundMusic;
    private boolean backgroundMusicWasPlaying = false; // To remember state when entering game

    private CardLayout cardLayout;
    private JPanel mainPanelContainer;

    // Power factor for non-linear volume scaling.
    // Lower values (e.g., 1.5-1.8) make the volume drop less steep at lower slider values.
    // Higher values (e.g., 2.0-2.5) give more perceived control at very low volumes but drop off faster.
    private static final double VOLUME_POWER_FACTOR = 1.8; // CHANGED from 2.5


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
    }

    private void handleExitRequest() {
        int choice = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to exit?",
                "Exit Game",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);
        if (choice == JOptionPane.YES_OPTION) {
            shutdownGame();
        }
    }

    private void initSounds() {
        try {
            String musicPath = "/assets/sounds/background_music.wav"; // Make sure this file exists in resources
            InputStream audioSrc = getClass().getResourceAsStream(musicPath);

            if (audioSrc == null) {
                java.lang.System.err.println("ERROR: Background music resource not found: " + musicPath);
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
            // Apply initial volume setting using the new setVolume logic
            setVolume(backgroundMusic, this.masterVolume);
            java.lang.System.out.println("Background music loaded from resources.");
            playBackgroundMusicIfNeeded();


        } catch (UnsupportedAudioFileException | LineUnavailableException | IOException e) {
            java.lang.System.err.println("Error loading or playing background music from resources: " + e.getMessage());
            e.printStackTrace();
            backgroundMusic = null;
            JOptionPane.showMessageDialog(this,
                    "Error loading or playing background music:\n" + e.getMessage(),
                    "Sound Error", JOptionPane.ERROR_MESSAGE);
        } catch (Exception e) {
            java.lang.System.err.println("Unexpected error initializing sounds: " + e.getMessage());
            e.printStackTrace();
            backgroundMusic = null;
        }
    }

    private void playBackgroundMusicIfNeeded() {
        if (backgroundMusic != null && backgroundMusic.isOpen() && !isMuted && !backgroundMusic.isRunning()) {
            backgroundMusic.loop(Clip.LOOP_CONTINUOUSLY);
            java.lang.System.out.println("Background music started/resumed.");
        }
    }

    private void stopBackgroundMusic() {
        if (backgroundMusic != null && backgroundMusic.isRunning()) {
            backgroundMusic.stop();
            java.lang.System.out.println("Background music stopped.");
        }
    }

    /**
     * Sets the volume for a given Clip, applying a non-linear (power) curve
     * to the input linearVolume to better match human perception of loudness.
     *
     * @param clip The audio Clip whose volume is to be set.
     * @param linearVolume A value from 0.0f (silent) to 1.0f (full volume)
     *                     representing the desired linear volume from the slider.
     */
    private void setVolume(Clip clip, float linearVolume) {
        if (clip == null || !clip.isOpen()) return;
        try {
            FloatControl gainControl = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);

            float clampedLinearVolume = Math.max(0.0f, Math.min(1.0f, linearVolume));

            float effectiveVolume;
            if (clampedLinearVolume <= 0.001f) {
                effectiveVolume = 0.0f;
            } else {
                effectiveVolume = (float) Math.pow(clampedLinearVolume, VOLUME_POWER_FACTOR);
            }

            float minDb = gainControl.getMinimum();
            float maxDb = gainControl.getMaximum();
            float targetDb;

            if (effectiveVolume <= 0.0f) {
                targetDb = minDb;
                if (targetDb == Float.NEGATIVE_INFINITY || targetDb < -80.0f) { // Ensure "silence" isn't too loud if minDb is high
                    targetDb = -80.0f; // A common "very quiet" dB value, adjust if needed
                }
            } else {
                // Scale effectiveVolume to the dB range [minDb, maxDb]
                // A simple linear mapping of the powered volume to the dB range.
                targetDb = minDb + (maxDb - minDb) * effectiveVolume;
            }

            // Final clamp to ensure the value is valid for the control
            targetDb = Math.max(minDb, Math.min(targetDb, maxDb));

            // Only set if the control actually exists and is of the correct type
            if (gainControl != null) {
                gainControl.setValue(targetDb);
            }


        } catch (IllegalArgumentException e) {
            // This can happen if FloatControl.Type.MASTER_GAIN is not supported,
            // or if the calculated targetDb is somehow out of the control's valid range
            // despite clamping (though clamping should prevent the latter).
            java.lang.System.err.println("Warning: Could not set volume for clip. MASTER_GAIN control might not be supported or value out of range. " + e.getMessage());
        } catch (Exception e) {
            // Catch-all for any other unexpected issues during volume setting.
            java.lang.System.err.println("Warning: An unexpected error occurred setting volume for clip. " + e.getMessage());
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
                    java.lang.System.err.println("SFX Warning: Resource not found: " + soundPath);
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
                setVolume(clip, masterVolume); // Use the same perceptual volume setting
                clip.start();

            } catch (UnsupportedAudioFileException | LineUnavailableException | IOException e) {
                java.lang.System.err.println("Error playing SFX '" + soundName + "' from resources: " + e.getMessage());
                // Cleanup resources
                if (clip != null && clip.isOpen()) clip.close();
                try { if (audioStream != null) audioStream.close(); } catch (IOException ioe) {/* ignore */}
                try { if (bufferedIn != null) bufferedIn.close(); } catch (IOException ioe) {/* ignore */}
                try { if (soundFileStream != null) soundFileStream.close(); } catch (IOException ioe) {/* ignore */}

            } catch (Exception e) {
                java.lang.System.err.println("Unexpected error playing SFX '" + soundName + "' from resources: " + e.getMessage());
                e.printStackTrace();
                // Cleanup resources
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
        setVolume(backgroundMusic, this.masterVolume);
        java.lang.System.out.println("Master Volume (linear input) set to: " + String.format("%.2f", this.masterVolume));
        if (settingsPanel != null && settingsPanel.isShowing()) {
            settingsPanel.updateUIFromGameState();
        }
    }
    public boolean isMuted() { return isMuted; }
    public void toggleMute() {
        isMuted = !isMuted;
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
        java.lang.System.out.println("Audio Globally " + (isMuted ? "Muted" : "Unmuted"));
        if (settingsPanel != null && settingsPanel.isShowing()) {
            settingsPanel.updateUIFromGameState();
        }
    }

    public void startGame() {
        java.lang.System.out.println("Navigating to GamePanel - Level " + currentLevel);
        stopBackgroundMusic();
        backgroundMusicWasPlaying = true;
        clearTemporaryMessage();
        if (gamePanel == null) { java.lang.System.err.println("ERROR: GamePanel is null!"); return; }
        cardLayout.show(mainPanelContainer, "GamePanel");
        gamePanel.initializeLevel(currentLevel);
        SwingUtilities.invokeLater(gamePanel::requestFocusInWindow);
    }

    public void showSettings() {
        java.lang.System.out.println("Navigating to SettingsMenu");
        playBackgroundMusicIfNeeded();
        clearTemporaryMessage();
        if (settingsPanel == null) return;
        cardLayout.show(mainPanelContainer, "SettingsMenu");
        settingsPanel.updateUIFromGameState();
        SwingUtilities.invokeLater(settingsPanel::requestFocusInWindow);
    }

    public void showLevelSelection() {
        java.lang.System.out.println("Navigating to LevelSelection");
        playBackgroundMusicIfNeeded();
        clearTemporaryMessage();
        if (levelSelectionPanel == null) return;
        levelSelectionPanel.updateLevelButtons();
        cardLayout.show(mainPanelContainer, "LevelSelection");
        SwingUtilities.invokeLater(levelSelectionPanel::requestFocusInWindow);
    }

    public void returnToMenu() {
        java.lang.System.out.println("Navigating to MainMenu");
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
            java.lang.System.out.println("Stopping game simulation on return to menu.");
            gamePanel.stopSimulation();
        }
        if (menuPanel == null) return;
        cardLayout.show(mainPanelContainer, "MainMenu");
        menuPanel.updateStartButtonLevel(currentLevel);
        SwingUtilities.invokeLater(menuPanel::requestFocusInWindow);
    }

    public void showStore() {
        if (gamePanel == null || !gamePanel.isGamePaused()) {
            java.lang.System.err.println("Error: showStore called but game is not paused or gamePanel is null.");
            if (!isMuted()) playSoundEffect("error");
            return;
        }
        java.lang.System.out.println("Attempting to show Store Dialog...");
        clearTemporaryMessage();
        if (storeDialog == null) {
            try {
                storeDialog = new StorePanel(this, gamePanel);
                java.lang.System.out.println("StorePanel dialog instance created.");
            } catch (Exception e) {
                java.lang.System.err.println("FATAL ERROR creating StorePanel dialog: " + e.getMessage());
                e.printStackTrace();
                if (gamePanel.isGamePaused()) gamePanel.pauseGame(false);
                JOptionPane.showMessageDialog(this, "Error opening store!", "Store Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
        }
        try {
            storeDialog.updateCoins(gameState.getCoins());
            java.lang.System.out.println("Store coins updated: " + gameState.getCoins());
            storeDialog.setLocationRelativeTo(this);
            storeDialog.setVisible(true); // This is a modal dialog, blocks here
            java.lang.System.out.println("Store dialog closed."); // Executed after dialog is closed
            // Focus request should be handled by the dialog itself or after it closes
            if (gamePanel.isShowing()) { // Check if gamePanel is still the active view
                SwingUtilities.invokeLater(gamePanel::requestFocusInWindow);
            }
        } catch (Exception e) {
            java.lang.System.err.println("Error during StorePanel visibility/interaction: " + e.getMessage());
            e.printStackTrace();
            // If an error occurs, ensure game is unpaused if it was paused for the store
            if (gamePanel.isGamePaused()) {
                java.lang.System.err.println("Force unpausing game due to store dialog error.");
                gamePanel.pauseGame(false); // Unpause
                SwingUtilities.invokeLater(gamePanel::requestFocusInWindow);
            }
        }
    }

    public void showKeyBindingDialog() {
        java.lang.System.out.println("Showing KeyBindingDialog");
        playBackgroundMusicIfNeeded();
        clearTemporaryMessage();
        // Check if a dialog is already open to prevent multiple instances (optional, but good practice)
        if (keyBindingDialog != null && keyBindingDialog.isVisible()) {
            keyBindingDialog.toFront();
            return;
        }
        keyBindingDialog = new KeyBindingPanel(this, this.keyBindings);
        keyBindingDialog.setVisible(true); // Modal, blocks until closed
        // After dialog closes, request focus back to the panel that opened it
        if (settingsPanel != null && settingsPanel.isShowing()) {
            SwingUtilities.invokeLater(settingsPanel::requestFocusInWindow);
        } else if (menuPanel != null && menuPanel.isShowing()) { // Or if opened from main menu
            SwingUtilities.invokeLater(menuPanel::requestFocusInWindow);
        }
    }


    public int getCurrentLevel() { return currentLevel; }
    public void setLevel(int level) {
        if (level >= 1 && level <= gameState.getMaxLevels()) {
            this.currentLevel = level;
            gameState.setCurrentSelectedLevel(level);
            java.lang.System.out.println("Selected Level set to: " + level);
            if (menuPanel != null) menuPanel.updateStartButtonLevel(level);
        } else {
            java.lang.System.err.println("Warning: Attempted to set invalid level: " + level);
        }
    }

    public GameState getGameState() { return gameState; }
    public KeyBindings getKeyBindings() { return keyBindings; }

    public void shutdownGame() {
        java.lang.System.out.println("Shutdown requested...");
        clearTemporaryMessage();
        if (gamePanel != null && (gamePanel.isGameRunning() || gamePanel.isGamePaused())) {
            gamePanel.stopSimulation();
        }
        if (keyBindings != null) {
            keyBindings.saveBindingsToFile();
        }
        if (backgroundMusic != null) {
            if (backgroundMusic.isRunning()) {
                backgroundMusic.stop();
            }
            if (backgroundMusic.isOpen()) {
                backgroundMusic.close();
                java.lang.System.out.println("Background music clip closed.");
            }
        }
        if (storeDialog != null) {
            storeDialog.dispose(); // Ensure dialog resources are released
            java.lang.System.out.println("Store dialog disposed.");
        }
        if (keyBindingDialog != null) {
            keyBindingDialog.dispose();
            java.lang.System.out.println("Key binding dialog disposed.");
        }
        if (temporaryMessageTimer != null && temporaryMessageTimer.isRunning()) {
            temporaryMessageTimer.stop();
        }
        java.lang.System.out.println("Exiting application.");
        dispose(); // Dispose the main JFrame
        java.lang.System.exit(0); // Terminate the application
    }

    public void showTemporaryMessage(String message, Color color, int durationMs) {
        currentTemporaryMessage = new TemporaryMessage(message, color, java.lang.System.currentTimeMillis() + durationMs);
        if (temporaryMessageTimer != null && temporaryMessageTimer.isRunning()) {
            temporaryMessageTimer.stop();
        }
        temporaryMessageTimer = new Timer(durationMs + 100, e -> {
            clearTemporaryMessage(); // This will also trigger a repaint if needed
        });
        temporaryMessageTimer.setRepeats(false);
        temporaryMessageTimer.start();
        if (gamePanel != null && gamePanel.isShowing()) gamePanel.repaint();
    }

    public TemporaryMessage getTemporaryMessage() {
        if (currentTemporaryMessage != null && java.lang.System.currentTimeMillis() < currentTemporaryMessage.displayUntilTimestamp) {
            return currentTemporaryMessage;
        }
        // If message expired, clear it and repaint
        if (currentTemporaryMessage != null && java.lang.System.currentTimeMillis() >= currentTemporaryMessage.displayUntilTimestamp) {
            clearTemporaryMessage(); // This handles repaint if gamePanel is showing
        }
        return null; // Return null if expired or never set
    }

    public void clearTemporaryMessage() {
        boolean needsRepaint = (currentTemporaryMessage != null);
        currentTemporaryMessage = null;
        if (temporaryMessageTimer != null && temporaryMessageTimer.isRunning()) {
            temporaryMessageTimer.stop();
        }
        // Only repaint if gamePanel is visible and a message was active
        if (needsRepaint && gamePanel != null && gamePanel.isShowing()) {
            gamePanel.repaint();
        }
    }


    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                boolean nimbusFound = false;
                for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                    if ("Nimbus".equals(info.getName())) { UIManager.setLookAndFeel(info.getClassName()); nimbusFound = true; break; }
                }
                if (!nimbusFound) UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                java.lang.System.out.println("Look and Feel set.");
            } catch (Exception e) { java.lang.System.err.println("Could not set Look and Feel."); }
            new NetworkGame();
        });
    }
}