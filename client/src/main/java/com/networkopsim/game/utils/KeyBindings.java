// ===== File: KeyBindings.java (REVISED for Client) =====
// ===== MODULE: client =====

package com.networkopsim.game.utils;

import java.awt.event.KeyEvent;
import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

public class KeyBindings {

  // Enum for game actions that can have a key binding
  public enum GameAction {
    TOGGLE_HUD("Toggle HUD", KeyEvent.VK_H),
    PAUSE_RESUME_GAME("Pause/Resume Game", KeyEvent.VK_P),
    OPEN_STORE("Open Store", KeyEvent.VK_S),
    START_SIMULATION_SCRUB_MODE("Start Simulation", KeyEvent.VK_ENTER),
    // DECREMENT_VIEWED_TIME REMOVED
    // INCREMENT_VIEWED_TIME REMOVED
    ESCAPE_MENU_CANCEL("Escape / Main Menu / Cancel Action", KeyEvent.VK_ESCAPE);

    private final String description;
    private final int defaultKeyCode;

    GameAction(String description, int defaultKeyCode) {
      this.description = description;
      this.defaultKeyCode = defaultKeyCode;
    }

    public String getDescription() { return description; }
    public int getDefaultKeyCode() { return defaultKeyCode; }
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
    for (Map.Entry<GameAction, Integer> entry : currentBindings.entrySet()) {
      if (entry.getValue() == newKeyCode && entry.getKey() != action) {
        java.lang.System.err.println("Key conflict: Key " + KeyEvent.getKeyText(newKeyCode) + " already assigned to " + entry.getKey().getDescription());
        return false;
      }
    }
    currentBindings.put(action, newKeyCode);
    return true;
  }

  public Set<GameAction> getAllActions() {
    return Arrays.stream(GameAction.values())
            .sorted(Comparator.comparing(GameAction::getDescription))
            .collect(Collectors.toCollection(LinkedHashSet::new));
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
      return;
    }
    try (InputStream input = new FileInputStream(file)) {
      properties.load(input);
      for (String keyName : properties.stringPropertyNames()) {
        try {
          GameAction action = GameAction.valueOf(keyName);
          int keyCode = Integer.parseInt(properties.getProperty(keyName));
          currentBindings.put(action, keyCode);
        } catch (IllegalArgumentException e) {
          java.lang.System.err.println("Warning: Ignoring unknown action in config file: " + keyName);
        }
      }
    } catch (IOException io) {
      java.lang.System.err.println("Error loading key bindings: " + io.getMessage());
    }
  }

  public void resetToDefaults() {
    loadDefaultBindings();
  }
}