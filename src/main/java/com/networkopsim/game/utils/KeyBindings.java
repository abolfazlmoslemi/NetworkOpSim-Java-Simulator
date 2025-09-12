package com.networkopsim.game.utils;


import com.networkopsim.game.model.core.System;

// ===== File: KeyBindings.java =====
import java.awt.event.KeyEvent;
import java.io.*;
import java.util.Collections; // Import for unmodifiableMap
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.Comparator; // For sorting
import java.util.LinkedHashSet; // For maintaining insertion order if sorted
import java.util.stream.Collectors;

public class KeyBindings {

    // Enum for game actions that can have a key binding
    public enum GameAction {
        TOGGLE_HUD("Toggle HUD", KeyEvent.VK_H),
        PAUSE_RESUME_GAME("Pause/Resume Game", KeyEvent.VK_P),
        OPEN_STORE("Open Store", KeyEvent.VK_S),
        START_SIMULATION_SCRUB_MODE("Start Simulation / Confirm Scrub", KeyEvent.VK_ENTER),
        DECREMENT_VIEWED_TIME("Scrub Time Left (Pre-Sim)", KeyEvent.VK_LEFT),
        INCREMENT_VIEWED_TIME("Scrub Time Right (Pre-Sim)", KeyEvent.VK_RIGHT),
        ESCAPE_MENU_CANCEL("Escape / Main Menu / Cancel Wiring", KeyEvent.VK_ESCAPE);
        // Add more actions as needed

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
        loadDefaultBindings(); // Load defaults into memory first
        loadBindingsFromFile(); // Then override with saved settings if file exists
    }

    private void loadDefaultBindings() {
        currentBindings.clear(); // Ensure it's clean before loading defaults
        for (GameAction action : GameAction.values()) {
            currentBindings.put(action, action.getDefaultKeyCode());
        }
        java.lang.System.out.println("KeyBindings: Default bindings loaded into memory.");
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

    public boolean setKeyCode(GameAction action, int newKeyCode) {
        // Check for conflict before setting
        for (Map.Entry<GameAction, Integer> entry : currentBindings.entrySet()) {
            if (entry.getValue() == newKeyCode && entry.getKey() != action) {
                java.lang.System.out.println("Key conflict: Key " + KeyEvent.getKeyText(newKeyCode) + " already assigned to " + entry.getKey().getDescription());
                return false; // Key is already in use by another action
            }
        }
        currentBindings.put(action, newKeyCode);
        java.lang.System.out.println("Key binding set in memory: " + action.getDescription() + " -> " + KeyEvent.getKeyText(newKeyCode));
        return true;
    }

    public Set<GameAction> getAllActions() {
        // Return a set sorted by description for consistent display order in UI
        return currentBindings.keySet().stream()
                .sorted(Comparator.comparing(GameAction::getDescription))
                .collect(Collectors.toCollection(LinkedHashSet::new)); // Use LinkedHashSet to maintain sorted order
    }

    public String getKeyText(int keyCode) {
        return KeyEvent.getKeyText(keyCode);
    }

    public Map<GameAction, Integer> getAllBindings() {
        return Collections.unmodifiableMap(new HashMap<>(currentBindings));
    }


    public void saveBindingsToFile() {
        Properties properties = new Properties();
        for (Map.Entry<GameAction, Integer> entry : currentBindings.entrySet()) {
            properties.setProperty(entry.getKey().name(), Integer.toString(entry.getValue()));
        }
        try (OutputStream output = new FileOutputStream(configFile)) {
            properties.store(output, "Game Key Bindings");
            java.lang.System.out.println("Key bindings saved to " + configFile);
        } catch (IOException io) {
            java.lang.System.err.println("Error saving key bindings: " + io.getMessage());
        }
    }

    public void loadBindingsFromFile() {
        Properties properties = new Properties();
        File file = new File(configFile);
        if (!file.exists()) {
            java.lang.System.out.println("Key bindings file not found ("+configFile+"). Using current in-memory bindings (which should be defaults if this is first load).");
            // No need to call loadDefaultBindings() again here, as it's called in constructor.
            // If file doesn't exist, currentBindings already holds defaults.
            return;
        }
        try (InputStream input = new FileInputStream(file)) {
            properties.load(input);
            // It's important to load defaults first, then override with file,
            // so if file is missing some keys, defaults are still there.
            // The constructor logic already does this (loadDefault then loadFromFile).
            // Here, we are applying loaded properties to currentBindings.
            for (String keyName : properties.stringPropertyNames()) {
                try {
                    GameAction action = GameAction.valueOf(keyName);
                    int keyCode = Integer.parseInt(properties.getProperty(keyName));
                    currentBindings.put(action, keyCode); // Override default or existing with file value
                } catch (IllegalArgumentException e) {
                    java.lang.System.err.println("Warning: Unknown action or invalid keycode in " + configFile + ": " + keyName + ". Using default for this action if available.");
                    // If action is unknown, it won't be in currentBindings from defaults, or will keep its default.
                }
            }
            java.lang.System.out.println("Key bindings loaded from " + configFile + " and applied to memory.");
        } catch (IOException io) {
            java.lang.System.err.println("Error loading key bindings from " + configFile + ": " + io.getMessage() + ". Using current in-memory bindings.");
            // If loading fails, currentBindings (which would be defaults or last saved state) are kept.
        }
    }

    /**
     * Resets the current in-memory key bindings to their default values.
     * This method DOES NOT automatically save these changes to the properties file.
     * Saving must be done explicitly by calling saveBindingsToFile().
     */
    public void resetToDefaults() {
        loadDefaultBindings(); // This reloads default values into currentBindings map
        java.lang.System.out.println("KeyBindings: In-memory bindings reset to defaults. File not modified by this operation.");
        // The following lines that manipulated the file are REMOVED:
        // File file = new File(configFile);
        // if (file.exists()) {
        //     if (file.delete()) {
        //         java.lang.System.out.println("Key bindings file deleted to reset to defaults.");
        //     } else {
        //         java.lang.System.err.println("Could not delete key bindings file to reset to defaults.");
        //     }
        // }
        // saveBindingsToFile(); // DO NOT SAVE TO FILE HERE
    }
}

