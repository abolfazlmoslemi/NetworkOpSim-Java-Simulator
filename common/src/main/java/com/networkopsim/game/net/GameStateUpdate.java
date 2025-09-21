// ===== File: GameStateUpdate.java (REVISED for Power-ups & Interactive Features) =====
// ===== MODULE: common =====

package com.networkopsim.game.net;

import com.networkopsim.game.model.core.Packet;
import com.networkopsim.game.model.core.System;
import com.networkopsim.game.model.core.Wire;
import com.networkopsim.game.model.state.GameState;

import java.io.Serializable;
import java.util.List;
import java.awt.geom.Point2D;


public class GameStateUpdate implements Serializable {
  private static final long serialVersionUID = 101L; // Version updated

  // Core State
  public final GameState gameState;
  public final List<System> systems;
  public final List<Wire> wires;
  public final List<Packet> packets;
  public final long simulationTimeMs;

  // Game Status Flags
  public final boolean isGameOver;
  public final boolean isLevelComplete;
  public final boolean isSimulationRunning;
  public final boolean isSimulationPaused;

  // Active Power-up Status
  public final boolean isAtarActive;
  public final boolean isAiryamanActive;
  public final boolean isSpeedLimiterActive;

  // Interactive Mode Status
  public final GameState.InteractiveMode currentInteractiveMode;
  public final List<GameState.ActiveWireEffect> activeWireEffects;

  public GameStateUpdate(GameState gameState, List<System> systems, List<Wire> wires, List<Packet> packets,
                         long simulationTimeMs, boolean isGameOver, boolean isLevelComplete,
                         boolean isSimulationRunning, boolean isSimulationPaused,
                         boolean isAtarActive, boolean isAiryamanActive, boolean isSpeedLimiterActive,
                         GameState.InteractiveMode currentInteractiveMode, List<GameState.ActiveWireEffect> activeWireEffects) {
    this.gameState = gameState;
    this.systems = systems;
    this.wires = wires;
    this.packets = packets;
    this.simulationTimeMs = simulationTimeMs;
    this.isGameOver = isGameOver;
    this.isLevelComplete = isLevelComplete;
    this.isSimulationRunning = isSimulationRunning;
    this.isSimulationPaused = isSimulationPaused;
    this.isAtarActive = isAtarActive;
    this.isAiryamanActive = isAiryamanActive;
    this.isSpeedLimiterActive = isSpeedLimiterActive;
    this.currentInteractiveMode = currentInteractiveMode;
    this.activeWireEffects = activeWireEffects;
  }
}