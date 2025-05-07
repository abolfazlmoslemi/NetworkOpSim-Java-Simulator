import javax.sound.sampled.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
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
    private CardLayout cardLayout;
    private JPanel mainPanelContainer;
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
            File musicFile = new File("assets/sounds/background_music.wav");
            if (!musicFile.exists() || !musicFile.isFile()) {
                java.lang.System.err.println("ERROR: Background music file not found: " + musicFile.getAbsolutePath());
                backgroundMusic = null; return;
            }
            AudioInputStream audioStream = AudioSystem.getAudioInputStream(musicFile);
            backgroundMusic = AudioSystem.getClip();
            backgroundMusic.open(audioStream);
            setVolume(backgroundMusic, masterVolume);
            backgroundMusic.loop(Clip.LOOP_CONTINUOUSLY);
            if (!isMuted) backgroundMusic.start();
            java.lang.System.out.println("Background music loaded. Playing: " + !isMuted);
        } catch (UnsupportedAudioFileException | LineUnavailableException | IOException e) {
            java.lang.System.err.println("Error loading or playing background music: " + e.getMessage());
            e.printStackTrace(); backgroundMusic = null;
        } catch (Exception e) {
            java.lang.System.err.println("Unexpected error initializing sounds: " + e.getMessage());
            e.printStackTrace(); backgroundMusic = null;
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
            Clip clip = null; AudioInputStream audioStream = null;
            try {
                File soundFile = new File("assets/sounds/" + soundName + ".wav");
                if (!soundFile.exists() || !soundFile.isFile()) {
                    java.lang.System.err.println("SFX Warning: File not found: " + soundFile.getAbsolutePath());
                    return;
                }
                audioStream = AudioSystem.getAudioInputStream(soundFile);
                clip = AudioSystem.getClip();
                final Clip finalClip = clip;
                final AudioInputStream finalAudioStream = audioStream;
                clip.addLineListener(event -> {
                    if (event.getType() == LineEvent.Type.STOP) {
                        if (finalClip != null && finalClip.isOpen()) finalClip.close();
                        if (finalAudioStream != null) try { finalAudioStream.close(); } catch (IOException ioe) {}
                    }
                });
                clip.open(audioStream);
                setVolume(clip, masterVolume);
                clip.start();
            } catch (UnsupportedAudioFileException | LineUnavailableException | IOException e) {
                java.lang.System.err.println("Error playing SFX '" + soundName + "': " + e.getMessage());
                if (clip != null && clip.isOpen()) clip.close();
                if (audioStream != null) try { audioStream.close(); } catch (IOException ioe) {}
            } catch (Exception e) {
                java.lang.System.err.println("Unexpected error playing SFX '" + soundName + "': " + e.getMessage());
                if (clip != null && clip.isOpen()) clip.close();
                if (audioStream != null) try { audioStream.close(); } catch (IOException ioe) {}
            }
        }).start();
    }
    public float getMasterVolume() { return masterVolume; }
    public void setMasterVolume(float volume) {
        this.masterVolume = Math.max(0.0f, Math.min(1.0f, volume));
        setVolume(backgroundMusic, this.masterVolume);
        java.lang.System.out.println("Master Volume set to: " + String.format("%.2f", this.masterVolume));
        if (settingsPanel != null && settingsPanel.isShowing()) {
            settingsPanel.updateUIFromGameState();
        }
    }
    public boolean isMuted() { return isMuted; }
    public void toggleMute() {
        isMuted = !isMuted;
        if (backgroundMusic != null && backgroundMusic.isOpen()) {
            if (isMuted && backgroundMusic.isRunning()) backgroundMusic.stop();
            else if (!isMuted && !backgroundMusic.isRunning()) {
                setVolume(backgroundMusic, masterVolume);
                backgroundMusic.loop(Clip.LOOP_CONTINUOUSLY);
            }
        }
        java.lang.System.out.println("Audio Globally " + (isMuted ? "Muted" : "Unmuted"));
        if (settingsPanel != null && settingsPanel.isShowing()) {
            settingsPanel.updateUIFromGameState();
        }
    }
    public void startGame() {
        java.lang.System.out.println("Navigating to GamePanel - Level " + currentLevel);
        if (gamePanel == null) { java.lang.System.err.println("ERROR: GamePanel is null!"); return; }
        cardLayout.show(mainPanelContainer, "GamePanel");
        gamePanel.initializeLevel(currentLevel);
        SwingUtilities.invokeLater(gamePanel::requestFocusInWindow);
    }
    public void showSettings() {
        java.lang.System.out.println("Navigating to SettingsMenu");
        if (settingsPanel == null) return;
        cardLayout.show(mainPanelContainer, "SettingsMenu");
        settingsPanel.updateUIFromGameState(); 
        SwingUtilities.invokeLater(settingsPanel::requestFocusInWindow);
    }
    public void showLevelSelection() {
        java.lang.System.out.println("Navigating to LevelSelection");
        if (levelSelectionPanel == null) return;
        levelSelectionPanel.updateLevelButtons();
        cardLayout.show(mainPanelContainer, "LevelSelection");
        SwingUtilities.invokeLater(levelSelectionPanel::requestFocusInWindow);
    }
    public void returnToMenu() {
        java.lang.System.out.println("Navigating to MainMenu");
        Component currentComponent = mainPanelContainer.getComponent(0);
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
            return;
        }
        java.lang.System.out.println("Attempting to show Store Dialog...");
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
        java.lang.System.out.println("Exiting application.");
        dispose();
        java.lang.System.exit(0);
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