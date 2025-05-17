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
    private float masterVolume = 0.7f;
    private boolean isMuted = false;
    private Clip backgroundMusic;
    private boolean backgroundMusicWasPlaying = false; // To remember state when entering game

    private CardLayout cardLayout;
    private JPanel mainPanelContainer;

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
            setVolume(backgroundMusic, masterVolume);
            // Don't start it here, start it when showing menu for the first time or returning to menu
            // backgroundMusic.loop(Clip.LOOP_CONTINUOUSLY);
            // if (!isMuted) backgroundMusic.start();
            java.lang.System.out.println("Background music loaded from resources.");
            playBackgroundMusicIfNeeded(); // Play if starting in menu and not muted


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
            backgroundMusic.loop(Clip.LOOP_CONTINUOUSLY); // Ensures it loops
            // backgroundMusic.start(); // loop() also starts it if not already running
            java.lang.System.out.println("Background music started/resumed.");
        }
    }

    private void stopBackgroundMusic() {
        if (backgroundMusic != null && backgroundMusic.isRunning()) {
            backgroundMusic.stop();
            java.lang.System.out.println("Background music stopped.");
        }
    }


    private void setVolume(Clip clip, float volume) {
        if (clip == null || !clip.isOpen()) return;
        try {
            FloatControl gainControl = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
            volume = Math.max(0.0f, Math.min(1.0f, volume));
            float minDb = gainControl.getMinimum();
            float maxDb = gainControl.getMaximum();
            float targetDb = minDb + ((maxDb - minDb) * volume);
            targetDb = Math.max(minDb, Math.min(targetDb, maxDb));
            gainControl.setValue(targetDb);

        } catch (IllegalArgumentException e) {
            java.lang.System.err.println("Warning: Could not set volume for clip. MASTER_GAIN control might not be supported. " + e.getMessage());
        } catch (Exception e) {
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
                        try { if (finalAudioStream != null) finalAudioStream.close(); } catch (IOException ioe) { }
                        try { if (finalBufferedIn != null) finalBufferedIn.close(); } catch (IOException ioe) { }
                        try { if (finalSoundFileStream != null) finalSoundFileStream.close(); } catch (IOException ioe) { }
                    }
                });

                clip.open(audioStream);
                setVolume(clip, masterVolume);
                clip.start();

            } catch (UnsupportedAudioFileException | LineUnavailableException | IOException e) {
                java.lang.System.err.println("Error playing SFX '" + soundName + "' from resources: " + e.getMessage());
                if (clip != null && clip.isOpen()) clip.close();
                try { if (audioStream != null) audioStream.close(); } catch (IOException ioe) {}
                try { if (bufferedIn != null) bufferedIn.close(); } catch (IOException ioe) {}
                try { if (soundFileStream != null) soundFileStream.close(); } catch (IOException ioe) {}

            } catch (Exception e) {
                java.lang.System.err.println("Unexpected error playing SFX '" + soundName + "' from resources: " + e.getMessage());
                e.printStackTrace();
                if (clip != null && clip.isOpen()) clip.close();
                try { if (audioStream != null) audioStream.close(); } catch (IOException ioe) {}
                try { if (bufferedIn != null) bufferedIn.close(); } catch (IOException ioe) {}
                try { if (soundFileStream != null) soundFileStream.close(); } catch (IOException ioe) {}
            }
        }).start();
    }


    public float getMasterVolume() { return masterVolume; }
    public void setMasterVolume(float volume) {
        this.masterVolume = Math.max(0.0f, Math.min(1.0f, volume));
        setVolume(backgroundMusic, this.masterVolume); // Apply to background music
        // Applying to all currently playing SFX is more complex and usually not done for master volume.
        // New SFX will get the new volume.
        java.lang.System.out.println("Master Volume set to: " + String.format("%.2f", this.masterVolume));
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
                    backgroundMusicWasPlaying = true; // Remember it was playing
                }
            } else { // Unmuting
                // Only start it if it was playing before mute OR if it's supposed to be playing (e.g. in menu)
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
                backgroundMusicWasPlaying = false; // Reset flag
            }
        }
        java.lang.System.out.println("Audio Globally " + (isMuted ? "Muted" : "Unmuted"));
        if (settingsPanel != null && settingsPanel.isShowing()) {
            settingsPanel.updateUIFromGameState();
        }
    }

    public void startGame() {
        java.lang.System.out.println("Navigating to GamePanel - Level " + currentLevel);
        stopBackgroundMusic(); // Stop music when game starts
        backgroundMusicWasPlaying = true; // Assume it was playing or should have been
        clearTemporaryMessage();
        if (gamePanel == null) { java.lang.System.err.println("ERROR: GamePanel is null!"); return; }
        cardLayout.show(mainPanelContainer, "GamePanel");
        gamePanel.initializeLevel(currentLevel);
        SwingUtilities.invokeLater(gamePanel::requestFocusInWindow);
    }

    public void showSettings() {
        java.lang.System.out.println("Navigating to SettingsMenu");
        playBackgroundMusicIfNeeded(); // Play music if returning to a menu-like screen
        clearTemporaryMessage();
        if (settingsPanel == null) return;
        cardLayout.show(mainPanelContainer, "SettingsMenu");
        settingsPanel.updateUIFromGameState();
        SwingUtilities.invokeLater(settingsPanel::requestFocusInWindow);
    }

    public void showLevelSelection() {
        java.lang.System.out.println("Navigating to LevelSelection");
        playBackgroundMusicIfNeeded(); // Play music
        clearTemporaryMessage();
        if (levelSelectionPanel == null) return;
        levelSelectionPanel.updateLevelButtons();
        cardLayout.show(mainPanelContainer, "LevelSelection");
        SwingUtilities.invokeLater(levelSelectionPanel::requestFocusInWindow);
    }

    public void returnToMenu() {
        java.lang.System.out.println("Navigating to MainMenu");
        playBackgroundMusicIfNeeded(); // Play music when returning to main menu
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
        // No change to background music here, as game is just paused.
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
            storeDialog.setVisible(true);
            java.lang.System.out.println("Store dialog closed.");
            if (gamePanel.isShowing()) {
                SwingUtilities.invokeLater(gamePanel::requestFocusInWindow);
            }
        } catch (Exception e) {
            java.lang.System.err.println("Error during StorePanel visibility/interaction: " + e.getMessage());
            e.printStackTrace();
            if (gamePanel.isGamePaused()) {
                java.lang.System.err.println("Force unpausing game due to store dialog error.");
                gamePanel.pauseGame(false);
                SwingUtilities.invokeLater(gamePanel::requestFocusInWindow);
            }
        }
    }

    public void showKeyBindingDialog() {
        java.lang.System.out.println("Showing KeyBindingDialog");
        playBackgroundMusicIfNeeded(); // Ensure music is playing if in a menu context
        clearTemporaryMessage();
        KeyBindingPanel dialog = new KeyBindingPanel(this, this.keyBindings);
        dialog.setVisible(true);
        if (settingsPanel != null && settingsPanel.isShowing()) {
            SwingUtilities.invokeLater(settingsPanel::requestFocusInWindow);
        } else if (menuPanel != null && menuPanel.isShowing()) {
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
            storeDialog.dispose();
            java.lang.System.out.println("Store dialog disposed.");
        }
        if (temporaryMessageTimer != null && temporaryMessageTimer.isRunning()) {
            temporaryMessageTimer.stop();
        }
        java.lang.System.out.println("Exiting application.");
        dispose();
        java.lang.System.exit(0);
    }

    public void showTemporaryMessage(String message, Color color, int durationMs) {
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