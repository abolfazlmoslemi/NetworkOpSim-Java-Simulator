// FILE: /NetworkOpSim-Multiplayer/NetworkOpSim-Client/src/main/java/com/networkopsim/client/core/NetworkGame.java
// ================================================================================

package com.networkopsim.client.core;

import com.networkopsim.client.network.ConnectionStatus;
import com.networkopsim.client.network.NetworkManager;
import com.networkopsim.client.offline.OfflineResultManager;
import com.networkopsim.client.utils.KeyBindings;
import com.networkopsim.client.view.dialogs.EndGameDialog;
import com.networkopsim.client.view.dialogs.KeyBindingPanel;
import com.networkopsim.client.view.dialogs.LeaderboardDialog;
import com.networkopsim.client.view.dialogs.StorePanel;
import com.networkopsim.client.view.panels.GamePanel;
import com.networkopsim.client.view.panels.LevelSelectionPanel;
import com.networkopsim.client.view.panels.MenuPanel;
import com.networkopsim.client.view.panels.SettingsPanel;
import com.networkopsim.shared.dto.GameStateDTO;
import com.networkopsim.shared.dto.LeaderboardDTO;
import com.networkopsim.shared.model.GameConstants;
import com.networkopsim.shared.net.OfflineResult;
import com.networkopsim.shared.net.ServerResponse;
import com.networkopsim.shared.net.UserCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.List;

public class NetworkGame extends JFrame {
    private static final Logger log = LoggerFactory.getLogger(NetworkGame.class);

    public static final int WINDOW_WIDTH = GameConstants.WINDOW_WIDTH;
    public static final int WINDOW_HEIGHT = GameConstants.WINDOW_HEIGHT;

    private final KeyBindings keyBindings;
    private final CardLayout cardLayout;
    private final JPanel mainPanelContainer;
    private final NetworkManager networkManager;
    private final OfflineResultManager offlineResultManager;

    private final GamePanel gamePanel;
    private final MenuPanel menuPanel;
    private final SettingsPanel settingsPanel;
    private final LevelSelectionPanel levelSelectionPanel;
    private LeaderboardDialog leaderboardDialog;
    private EndGameDialog endGameDialog; // <-- NEW
    private StorePanel storeDialog;
    private KeyBindingPanel keyBindingDialog;

    private int currentLevel = 1;
    private String username = "Player" + (int)(Math.random() * 1000);
    private boolean isOfflineMode = false;
    private boolean isOnlineMultiplayer = false;

    // Audio State
    private float masterVolume = 0.7f;
    private boolean isMuted = false;
    private Clip backgroundMusic;
    private boolean backgroundMusicWasPlaying = false;
    private static final double VOLUME_POWER_FACTOR = 1.8;
    private static final float MIN_AUDIBLE_DB_TARGET = -50.0f;
    private static final float SILENCE_DB = -80.0f;

    // Temporary Message State
    private TemporaryMessage currentTemporaryMessage = null;
    private Timer temporaryMessageTimer;

    public static class TemporaryMessage {
        public final String message;
        public final Color color;
        public final long displayUntilTimestamp;
        public TemporaryMessage(String message, Color color, long displayUntilTimestamp) {
            this.message = message; this.color = color; this.displayUntilTimestamp = displayUntilTimestamp;
        }
    }

    public NetworkGame() {
        log.info("Network Operator Simulator Client is starting up...");
        setTitle("Network Operator Simulator");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setResizable(false);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { handleExitRequest(); }
        });

        keyBindings = new KeyBindings();
        networkManager = new NetworkManager(this);
        offlineResultManager = new OfflineResultManager();

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

        networkManager.connect();
    }

    // --- Network Callbacks & Handling ---

    public void onConnectionEstablished() {
        isOfflineMode = false;
        SwingUtilities.invokeLater(() -> {
            showTemporaryMessage("Connected to server!", Color.GREEN, 2500);
            updateUIsForConnectionStatus();
            networkManager.sendCommand(UserCommand.setUsername(this.username));
            syncOfflineResults();
        });
    }

    private void syncOfflineResults() {
        List<OfflineResult> pendingResults = offlineResultManager.loadResults();
        if (!pendingResults.isEmpty()) {
            log.info("Found {} pending offline results. Syncing with server...", pendingResults.size());
            showTemporaryMessage("Syncing " + pendingResults.size() + " offline results...", Color.ORANGE, 5000);
            for (OfflineResult result : pendingResults) {
                if (result.getUsername().equals(this.username)) {
                    networkManager.sendCommand(UserCommand.submitOfflineResult(result));
                } else {
                    log.warn("Skipping offline result for a different user: {}", result.getUsername());
                }
            }
            offlineResultManager.deleteResultsFile();
        }
    }

    public void onConnectionFailed() {
        isOfflineMode = true;
        isOnlineMultiplayer = false;
        SwingUtilities.invokeLater(() -> {
            Object[] options = {"Retry", "Play Offline"};
            int choice = JOptionPane.showOptionDialog(this,
                    "Could not connect to the server.\nWould you like to retry or play in offline mode?",
                    "Connection Failed", JOptionPane.YES_NO_OPTION, JOptionPane.ERROR_MESSAGE, null, options, options[0]);
            if (choice == JOptionPane.YES_OPTION) {
                networkManager.connect();
            } else {
                log.info("User selected to play in offline mode.");
                updateUIsForConnectionStatus();
            }
        });
    }

    public void onDisconnected() {
        isOfflineMode = true;
        isOnlineMultiplayer = false;
        SwingUtilities.invokeLater(() -> {
            if (gamePanel.isShowing()) {
                gamePanel.stopSimulation(true);
                cardLayout.show(mainPanelContainer, "MainMenu");
                menuPanel.updateStartButtonLevel(currentLevel);
            }
            showTemporaryMessage("Disconnected from server. Now in offline mode.", Color.RED, 4000);
            updateUIsForConnectionStatus();
        });
    }

    public void handleServerResponse(ServerResponse response) {
        log.info("Handling server response: {} - {}", response.getStatus(), response.getMessage());
        SwingUtilities.invokeLater(() -> {
            if (response.getData() instanceof LeaderboardDTO) {
                if (leaderboardDialog == null) {
                    leaderboardDialog = new LeaderboardDialog(this);
                }
                leaderboardDialog.updateData((LeaderboardDTO) response.getData());
                leaderboardDialog.setVisible(true);
                return;
            }

            if (response.getMessage().contains("Wins!") || response.getMessage().contains("Draw!")) {
                showEndGameResults(response.getMessage());
                return;
            }

            if (response.getMessage().contains("Match found!")) {
                startMultiplayerGame();
                return;
            }
            if(response.getMessage().contains("left the matchmaking queue")) {
                menuPanel.resetMatchmakingButtons();
                if (levelSelectionPanel.isShowing()) {
                    levelSelectionPanel.setMode(isOnlineMultiplayer);
                }
            }

            Color color = response.getStatus() == ServerResponse.ResponseStatus.SUCCESS ? Color.GREEN : Color.ORANGE;
            showTemporaryMessage(response.getMessage(), color, 3000);
        });
    }

    public void updateUIsForConnectionStatus() {
        menuPanel.updateConnectionStatus(networkManager.getStatus());
    }

    // --- Game Flow & Panel Management ---

    public void startOfflineGame() {
        this.isOfflineMode = true;
        startGame();
    }

    public void startGame() {
        this.isOnlineMultiplayer = false;
        log.info("Attempting to start game for level {} in {} mode.", currentLevel, isOfflineMode ? "Offline" : "Online Single-Player");
        stopBackgroundMusic();
        cardLayout.show(mainPanelContainer, "GamePanel");

        if (isOfflineMode) {
            gamePanel.initializeLevelOffline(currentLevel);
        } else {
            gamePanel.initializeLevelOnline(currentLevel, false);
        }
        SwingUtilities.invokeLater(gamePanel::requestFocusInWindow);
    }

    public void startMultiplayerGame() {
        this.isOnlineMultiplayer = true;
        this.isOfflineMode = false;
        log.info("Server found a match! Starting online multiplayer game for level {}.", currentLevel);
        SwingUtilities.invokeLater(() -> {
            stopBackgroundMusic();
            cardLayout.show(mainPanelContainer, "GamePanel");
            gamePanel.initializeLevelOnline(currentLevel, true);
            gamePanel.requestFocusInWindow();
        });
    }

    public void returnToMenu() {
        log.info("Returning to main menu.");
        playBackgroundMusicIfNeeded();
        if (gamePanel.isShowing()) {
            gamePanel.stopSimulation(false);
        }
        if (levelSelectionPanel.isShowing()) {
            if (isOnlineMultiplayer) {
                networkManager.sendCommand(UserCommand.simpleCommand(UserCommand.CommandType.LEAVE_MATCHMAKING_QUEUE));
            }
        }
        isOnlineMultiplayer = false;
        cardLayout.show(mainPanelContainer, "MainMenu");
        menuPanel.updateStartButtonLevel(currentLevel);
        updateUIsForConnectionStatus();
        menuPanel.resetMatchmakingButtons();
        SwingUtilities.invokeLater(menuPanel::requestFocusInWindow);
    }

    public void showLevelSelectionForOnline() {
        log.debug("Showing level selection for online play.");
        playBackgroundMusicIfNeeded();
        isOnlineMultiplayer = true;
        levelSelectionPanel.setMode(true);
        cardLayout.show(mainPanelContainer, "LevelSelection");
        SwingUtilities.invokeLater(levelSelectionPanel::requestFocusInWindow);
    }

    public void showLevelSelection() {
        log.debug("Showing level selection panel for offline play.");
        playBackgroundMusicIfNeeded();
        isOnlineMultiplayer = false;
        levelSelectionPanel.setMode(false);
        cardLayout.show(mainPanelContainer, "LevelSelection");
        SwingUtilities.invokeLater(levelSelectionPanel::requestFocusInWindow);
    }

    public void showSettings() {
        log.debug("Showing settings panel.");
        playBackgroundMusicIfNeeded();
        cardLayout.show(mainPanelContainer, "SettingsMenu");
        settingsPanel.updateUIFromState();
        SwingUtilities.invokeLater(settingsPanel::requestFocusInWindow);
    }

    // --- Dialogs ---

    public void showLeaderboard() {
        if (networkManager.getStatus() != ConnectionStatus.CONNECTED) {
            showTemporaryMessage("You must be online to view the leaderboard.", Color.ORANGE, 3000);
            return;
        }
        networkManager.sendCommand(UserCommand.getLeaderboard());
    }

    private void showEndGameResults(String resultMessage) {
        if (endGameDialog == null) {
            endGameDialog = new EndGameDialog(this);
        }
        // Get the very last game state to show final stats
        GameStateDTO lastState = gamePanel.getDisplayState();
        endGameDialog.updateResults(lastState, resultMessage);
        endGameDialog.setVisible(true);
        // returnToMenu() is now called from the dialog's close button
    }

    public void showStore() {
        if (!gamePanel.isShowing() || !gamePanel.isGameRunning() || !gamePanel.isGamePaused()) {
            log.warn("Attempted to open store when game is not running or paused. Denied.");
            playSoundEffect("error");
            return;
        }
        if (storeDialog == null) {
            storeDialog = new StorePanel(this, gamePanel);
        }
        storeDialog.updateState();
        storeDialog.setLocationRelativeTo(this);
        storeDialog.setVisible(true);
    }

    public void showKeyBindingDialog() {
        if (keyBindingDialog == null) {
            keyBindingDialog = new KeyBindingPanel(this, this.keyBindings);
        }
        keyBindingDialog.setLocationRelativeTo(this);
        keyBindingDialog.setVisible(true);
    }

    // --- Lifecycle ---

    private void handleExitRequest() {
        int choice = JOptionPane.showConfirmDialog(this, "Are you sure you want to exit?", "Exit Game", JOptionPane.YES_NO_OPTION);
        if (choice == JOptionPane.YES_OPTION) {
            shutdownGame();
        }
    }

    public void shutdownGame() {
        log.info("Shutdown sequence initiated...");
        networkManager.disconnect();
        keyBindings.saveBindingsToFile();
        if (backgroundMusic != null) {
            if (backgroundMusic.isRunning()) backgroundMusic.stop();
            if (backgroundMusic.isOpen()) backgroundMusic.close();
        }
        if (temporaryMessageTimer != null) temporaryMessageTimer.stop();
        dispose();
        log.info("Application shutdown complete. Exiting.");
        System.exit(0);
    }

    // --- Sound Methods (unchanged) ---
    private void initSounds() { try { InputStream audioSrc = getClass().getResourceAsStream("/assets/sounds/background_music.wav"); if (audioSrc == null) { log.error("Background music not found in resources."); return; } InputStream bufferedIn = new BufferedInputStream(audioSrc); AudioInputStream audioStream = AudioSystem.getAudioInputStream(bufferedIn); backgroundMusic = AudioSystem.getClip(); backgroundMusic.open(audioStream); setVolumeOnClip(backgroundMusic, this.masterVolume); playBackgroundMusicIfNeeded(); log.info("Sound system initialized."); } catch (Exception e) { backgroundMusic = null; log.error("Error initializing sound system", e); } }
    public void playSoundEffect(String soundName) { if (isMuted) return; new Thread(() -> { try { InputStream audioSrc = getClass().getResourceAsStream("/assets/sounds/" + soundName + ".wav"); if (audioSrc == null) { log.warn("Sound effect not found: {}", soundName); return; } try (InputStream bufferedIn = new BufferedInputStream(audioSrc); AudioInputStream audioStream = AudioSystem.getAudioInputStream(bufferedIn); Clip clip = AudioSystem.getClip()) { clip.open(audioStream); setVolumeOnClip(clip, masterVolume); clip.start(); Thread.sleep(clip.getMicrosecondLength() / 1000 + 100); } } catch (Exception e) { log.error("Failed to play sound effect '{}'", soundName, e); } }).start(); }
    private void setVolumeOnClip(Clip clip, float linearVolume) { if (clip == null || !clip.isOpen() || !clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) return; try { FloatControl gainControl = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN); float minDb = gainControl.getMinimum(); float maxDb = gainControl.getMaximum(); float clamped = Math.max(0.0f, Math.min(1.0f, linearVolume)); float targetDb = (clamped == 0.0f) ? SILENCE_DB : MIN_AUDIBLE_DB_TARGET + ((maxDb - MIN_AUDIBLE_DB_TARGET) * (float)Math.pow(clamped, VOLUME_POWER_FACTOR)); gainControl.setValue(Math.max(minDb, targetDb)); } catch (Exception e) { log.warn("Could not set volume on clip.", e); } }
    public void setMasterVolume(float volume) { this.masterVolume = Math.max(0.0f, Math.min(1.0f, volume)); log.info("Master volume set to {}%", (int)(this.masterVolume * 100)); setVolumeOnClip(backgroundMusic, this.masterVolume); if (settingsPanel.isShowing()) settingsPanel.updateUIFromState(); }
    public void toggleMute() { isMuted = !isMuted; log.info("Audio muted status: {}", isMuted); if (isMuted) { if (backgroundMusic != null && backgroundMusic.isRunning()) { backgroundMusic.stop(); backgroundMusicWasPlaying = true; } } else { if (backgroundMusicWasPlaying) playBackgroundMusicIfNeeded(); } if (settingsPanel.isShowing()) settingsPanel.updateUIFromState(); }
    private void playBackgroundMusicIfNeeded() { if (backgroundMusic != null && !isMuted && !backgroundMusic.isRunning()) { backgroundMusic.loop(Clip.LOOP_CONTINUOUSLY); } }
    private void stopBackgroundMusic() { if (backgroundMusic != null && backgroundMusic.isRunning()) { backgroundMusic.stop(); } }

    // --- Temporary Message Methods (unchanged) ---
    public void showTemporaryMessage(String message, Color color, int durationMs) { currentTemporaryMessage = new TemporaryMessage(message, color, java.lang.System.currentTimeMillis() + durationMs); if (temporaryMessageTimer != null) temporaryMessageTimer.stop(); temporaryMessageTimer = new Timer(durationMs + 100, e -> clearTemporaryMessage()); temporaryMessageTimer.setRepeats(false); temporaryMessageTimer.start(); getVisiblePanel().ifPresent(Component::repaint); }
    public TemporaryMessage getTemporaryMessage() { if (currentTemporaryMessage != null && java.lang.System.currentTimeMillis() > currentTemporaryMessage.displayUntilTimestamp) { currentTemporaryMessage = null; } return currentTemporaryMessage; }
    public void clearTemporaryMessage() { if (currentTemporaryMessage != null) { currentTemporaryMessage = null; getVisiblePanel().ifPresent(Component::repaint); } if (temporaryMessageTimer != null) temporaryMessageTimer.stop(); }
    private java.util.Optional<Component> getVisiblePanel() { for (Component comp : mainPanelContainer.getComponents()) if (comp.isVisible()) return java.util.Optional.of(comp); return java.util.Optional.empty(); }

    // --- Getters ---
    public NetworkManager getNetworkManager() { return networkManager; }
    public boolean isOfflineMode() { return isOfflineMode; }
    public boolean isOnlineMultiplayer() { return isOnlineMultiplayer; }
    public KeyBindings getKeyBindings() { return keyBindings; }
    public int getCurrentLevel() { return currentLevel; }
    public float getMasterVolume() { return masterVolume; }
    public boolean isMuted() { return isMuted; }

    // --- Setters ---
    public void setLevel(int level) { this.currentLevel = level; menuPanel.updateStartButtonLevel(level); }
    public String getUsername() { return username; }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception e) { log.warn("Could not set system Look and Feel.", e); }
            new NetworkGame();
        });
    }
}