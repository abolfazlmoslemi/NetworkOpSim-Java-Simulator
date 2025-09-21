// ===== File: ClientAction.java (REVISED for Power-ups & Interactive Features) =====
// ===== MODULE: common =====

package com.networkopsim.game.net;

import java.awt.Point;
import java.io.Serializable;

/**
 * A Data Transfer Object (DTO) that represents a single action performed by the user on the client.
 * The client sends this object to the server to request a change in the game state.
 */
public class ClientAction implements Serializable {
  private static final long serialVersionUID = 102L; // Version updated

  public enum ActionType {
    // --- Game Lifecycle ---
    INITIALIZE_LEVEL,
    START_SIMULATION,
    PAUSE_SIMULATION,
    RESUME_SIMULATION,

    // --- Pre-simulation Wiring ---
    CREATE_WIRE,
    DELETE_WIRE,
    ADD_RELAY_POINT,
    DELETE_RELAY_POINT,

    // --- In-simulation Store Items ---
    BUY_ITEM, // A generic request to buy an item by name

    // --- Interactive Actions (triggered after buying an item) ---
    PLACE_AERGIA_EFFECT,  // payload: wireId, Point
    PLACE_ELIPHAS_EFFECT, // payload: wireId, Point
    START_SISYPHUS_DRAG,  // payload: systemId
    UPDATE_SISYPHUS_DRAG, // payload: systemId, Point (new position)
    STOP_SISYPHUS_DRAG    // payload: systemId
  }

  public final ActionType type;
  public final Serializable[] payload; // Using a flexible payload array

  public ClientAction(ActionType type, Serializable... payload) {
    this.type = type;
    this.payload = payload;
  }

  // --- Helper methods to safely extract payload data ---

  public int getInt(int index) {
    if (payload != null && payload.length > index && payload[index] instanceof Integer) {
      return (Integer) payload[index];
    }
    throw new ClassCastException("Payload at index " + index + " is not an Integer or does not exist for action " + type);
  }

  public Point getPoint(int index) {
    if (payload != null && payload.length > index && payload[index] instanceof Point) {
      return (Point) payload[index];
    }
    throw new ClassCastException("Payload at index " + index + " is not a Point or does not exist for action " + type);
  }

  public String getString(int index) {
    if (payload != null && payload.length > index && payload[index] instanceof String) {
      return (String) payload[index];
    }
    throw new ClassCastException("Payload at index " + index + " is not a String or does not exist for action " + type);
  }
}