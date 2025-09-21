// ===== File: GameStateUpdate.java (NEW - For Server -> Client communication) =====
// ===== MODULE: common =====

package com.networkopsim.game.net;

import com.networkopsim.game.model.core.Packet;
import com.networkopsim.game.model.core.System;
import com.networkopsim.game.model.core.Wire;
import com.networkopsim.game.model.state.GameState;

import java.io.Serializable;
import java.util.List;

/**
 * A Data Transfer Object (DTO) that encapsulates the entire state of the game world for a single frame.
 * The server creates and sends this object to the client, and the client uses it to render the view.
 */
public class GameStateUpdate implements Serializable {
  private static final long serialVersionUID = 1L;

  public final GameState gameState;
  public final List<System> systems;
  public final List<Wire> wires;
  public final List<Packet> packets;
  public final long simulationTimeMs;
  public final boolean isGameOver;
  public final boolean isLevelComplete;
  public final boolean isSimulationRunning;
  public final boolean isSimulationPaused;

  public GameStateUpdate(GameState gameState, List<System> systems, List<Wire> wires, List<Packet> packets,
                         long simulationTimeMs, boolean isGameOver, boolean isLevelComplete,
                         boolean isSimulationRunning, boolean isSimulationPaused) {
    this.gameState = gameState;
    this.systems = systems;
    this.wires = wires;
    this.packets = packets;
    this.simulationTimeMs = simulationTimeMs;
    this.isGameOver = isGameOver;
    this.isLevelComplete = isLevelComplete;
    this.isSimulationRunning = isSimulationRunning;
    this.isSimulationPaused = isSimulationPaused;
  }
}