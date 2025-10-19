// FILE: /NetworkOpSim-Multiplayer/NetworkOpSim-Client/src/main/java/com/networkopsim/client/utils/KeyBindings.java
// ================================================================================

package com.networkopsim.client.utils;

import java.awt.event.KeyEvent;
import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

public class KeyBindings {

    public enum GameAction {
        TOGGLE_HUD("Toggle HUD", KeyEvent.VK_H),
        PAUSE_RESUME_GAME("Pause/Resume Game", KeyEvent.VK_P),
        OPEN_STORE("Open Store", KeyEvent.VK_S),
        START_SIMULATION_SCRUB_MODE("Start Simulation", KeyEvent.VK_ENTER),
        DECREMENT_VIEWED_TIME("Scrub Time Left (Pre-Sim)", KeyEvent.VK_LEFT),
        INCREMENT_VIEWED_TIME("Scrub Time Right (Pre-Sim)", KeyEvent.VK_RIGHT),
        ESCAPE_MENU_CANCEL("Escape / Main Menu / Cancel", KeyEvent.VK_ESCAPE);

        private final String description;
        private final int defaultKeyCode;

        GameAction(String description, int defaultKeyCode) {
            this.description = description;
            this.defaultKeyCode = defaultKeyCode;
        }

        public String getDescription() {
            return description;
        }

        public int getDefaultKeyCode() {
            return defaultKeyCode;
        }
    }

    private final Map<GameAction, Integer> currentBindings;
    private final String configFile = "keybindings.properties";

    public KeyBindings() {
        currentBindings = new HashMap<>();
        loadDefaultBindings();
        loadBindingsFromFile();
    }

    private void loadDefaultBindings() {
        currentBindings.clear();
        for (GameAction action : GameAction.values()) {
            currentBindings.put(action, action.getDefaultKeyCode());
        }
    }

    public int getKeyCode(GameAction action) {
        return currentBindings.getOrDefault(action, -1);
    }

    public GameAction getActionForKey(int keyCode) {
        for (Map.Entry<GameAction, Integer> entry : currentBindings.entrySet()) {
            if (entry.getValue() == keyCode) {
                return entry.getKey();
            }
        }
        return null;
    }

    public void setKeyCode(GameAction action, int newKeyCode) {
        currentBindings.put(action, newKeyCode);
    }

    public Set<GameAction> getAllActions() {
        return Arrays.stream(GameAction.values())
                .sorted(Comparator.comparing(GameAction::getDescription))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public String getKeyText(int keyCode) {
        return KeyEvent.getKeyText(keyCode);
    }

    public void saveBindingsToFile() {
        Properties properties = new Properties();
        for (Map.Entry<GameAction, Integer> entry : currentBindings.entrySet()) {
            properties.setProperty(entry.getKey().name(), Integer.toString(entry.getValue()));
        }
        try (OutputStream output = new FileOutputStream(configFile)) {
            properties.store(output, "Game Key Bindings");
        } catch (IOException io) {
            System.err.println("Error saving key bindings: " + io.getMessage());
        }
    }

    public void loadBindingsFromFile() {
        Properties properties = new Properties();
        File file = new File(configFile);
        if (!file.exists()) {
            return; // Defaults are already loaded
        }
        try (InputStream input = new FileInputStream(file)) {
            properties.load(input);
            for (String keyName : properties.stringPropertyNames()) {
                try {
                    GameAction action = GameAction.valueOf(keyName);
                    int keyCode = Integer.parseInt(properties.getProperty(keyName));
                    currentBindings.put(action, keyCode);
                } catch (IllegalArgumentException e) {
                    System.err.println("Warning: Invalid key binding in config file: " + keyName);
                }
            }
        } catch (IOException io) {
            System.err.println("Error loading key bindings from file: " + io.getMessage());
        }
    }

    public void resetToDefaults() {
        loadDefaultBindings();
    }
}