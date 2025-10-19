// FILE: /NetworkOpSim-Multiplayer/NetworkOpSim-Shared/src/main/java/com/networkopsim/shared/dto/GameStateDTO.java
// ================================================================================

package com.networkopsim.shared.dto;

import java.io.Serializable;
import java.util.List;

/**
 * A Data Transfer Object representing the state of the game at a specific moment.
 * This object is sent from the server to the client for rendering purposes.
 * It contains only the necessary data for display, without any game logic.
 * This version is adapted for 2-player multiplayer.
 */
public class GameStateDTO implements Serializable {
    private static final long serialVersionUID = 4L;

    // Core game elements for rendering
    private final List<SystemDTO> systems;
    private final List<WireDTO> wires;
    private final List<PacketDTO> packets;
    private final List<ActiveWireEffectDTO> activeWireEffects;

    // --- Multiplayer HUD Information ---
    private final int player1Coins;
    private final int player1PacketsGenerated;
    private final int player1PacketsLost;
    private final double player1LossPercentage;

    private final int player2Coins;
    private final int player2PacketsGenerated;
    private final int player2PacketsLost;
    private final double player2LossPercentage;

    private int localPlayerId; // Will be set by ClientHandler

    // --- Shared/Global Information ---
    private final long simulationTimeElapsedMs;
    private final int remainingWireLength;
    private final int currentLevel;

    // Game state flags
    private final boolean isSimulationRunning;
    private final boolean isSimulationPaused;
    private final boolean isGameOver;
    private final boolean isLevelComplete;

    // Power-up status
    private final boolean atarActive;
    private final boolean airyamanActive;
    private final boolean speedLimiterActive;

    // --- New Game Phase Information ---
    private final String gamePhase;
    private final long buildTimeRemainingMs;
    private final boolean isPlayer1Ready;
    private final boolean isPlayer2Ready;

    // --- New Pause/Store Status ---
    private final boolean isPlayer1InStore;
    private final boolean isPlayer2InStore;
    private final int pausedByPlayerId; // 0 = not paused, 1 = p1, 2 = p2


    public GameStateDTO(List<SystemDTO> systems, List<WireDTO> wires, List<PacketDTO> packets,
                        List<ActiveWireEffectDTO> activeWireEffects, long simulationTimeElapsedMs,
                        int remainingWireLength, int currentLevel, boolean isSimulationRunning,
                        boolean isSimulationPaused, boolean isGameOver, boolean isLevelComplete,
                        int player1Coins, int player1PacketsGenerated, int player1PacketsLost, double player1LossPercentage,
                        int player2Coins, int player2PacketsGenerated, int player2PacketsLost, double player2LossPercentage,
                        int localPlayerId,
                        boolean atarActive, boolean airyamanActive, boolean speedLimiterActive,
                        String gamePhase, long buildTimeRemainingMs, boolean isPlayer1Ready, boolean isPlayer2Ready,
                        boolean isPlayer1InStore, boolean isPlayer2InStore, int pausedByPlayerId) {
        this.systems = systems;
        this.wires = wires;
        this.packets = packets;
        this.activeWireEffects = activeWireEffects;
        this.simulationTimeElapsedMs = simulationTimeElapsedMs;
        this.remainingWireLength = remainingWireLength;
        this.currentLevel = currentLevel;
        this.isSimulationRunning = isSimulationRunning;
        this.isSimulationPaused = isSimulationPaused;
        this.isGameOver = isGameOver;
        this.isLevelComplete = isLevelComplete;
        this.player1Coins = player1Coins;
        this.player1PacketsGenerated = player1PacketsGenerated;
        this.player1PacketsLost = player1PacketsLost;
        this.player1LossPercentage = player1LossPercentage;
        this.player2Coins = player2Coins;
        this.player2PacketsGenerated = player2PacketsGenerated;
        this.player2PacketsLost = player2PacketsLost;
        this.player2LossPercentage = player2LossPercentage;
        this.localPlayerId = localPlayerId;
        this.atarActive = atarActive;
        this.airyamanActive = airyamanActive;
        this.speedLimiterActive = speedLimiterActive;
        this.gamePhase = gamePhase;
        this.buildTimeRemainingMs = buildTimeRemainingMs;
        this.isPlayer1Ready = isPlayer1Ready;
        this.isPlayer2Ready = isPlayer2Ready;
        this.isPlayer1InStore = isPlayer1InStore;
        this.isPlayer2InStore = isPlayer2InStore;
        this.pausedByPlayerId = pausedByPlayerId;
    }

    // --- Getters for core elements ---
    public List<SystemDTO> getSystems() { return systems; }
    public List<WireDTO> getWires() { return wires; }
    public List<PacketDTO> getPackets() { return packets; }
    public List<ActiveWireEffectDTO> getActiveWireEffects() { return activeWireEffects; }

    // --- Getters for shared/global state ---
    public long getSimulationTimeElapsedMs() { return simulationTimeElapsedMs; }
    public int getRemainingWireLength() { return remainingWireLength; }
    public int getCurrentLevel() { return currentLevel; }
    public boolean isSimulationRunning() { return isSimulationRunning; }
    public boolean isSimulationPaused() { return isSimulationPaused; }
    public boolean isGameOver() { return isGameOver; }
    public boolean isLevelComplete() { return isLevelComplete; }

    // --- Getters for player-specific data ---
    public int getPlayer1Coins() { return player1Coins; }
    public int getPlayer1PacketsGenerated() { return player1PacketsGenerated; }
    public int getPlayer1PacketsLost() { return player1PacketsLost; }
    public double getPlayer1LossPercentage() { return player1LossPercentage; }

    public int getPlayer2Coins() { return player2Coins; }
    public int getPlayer2PacketsGenerated() { return player2PacketsGenerated; }
    public int getPlayer2PacketsLost() { return player2PacketsLost; }
    public double getPlayer2LossPercentage() { return player2LossPercentage; }

    public int getLocalPlayerId() { return localPlayerId; }

    // --- Getters for power-ups ---
    public boolean isAtarActive() { return atarActive; }
    public boolean isAiryamanActive() { return airyamanActive; }
    public boolean isSpeedLimiterActive() { return speedLimiterActive; }

    // --- Getters for Game Phase ---
    public String getGamePhase() { return gamePhase; }
    public long getBuildTimeRemainingMs() { return buildTimeRemainingMs; }
    public boolean isPlayer1Ready() { return isPlayer1Ready; }
    public boolean isPlayer2Ready() { return isPlayer2Ready; }

    // --- Getters for Pause/Store Status ---
    public boolean isPlayer1InStore() { return isPlayer1InStore; }
    public boolean isPlayer2InStore() { return isPlayer2InStore; }
    public int getPausedByPlayerId() { return pausedByPlayerId; }


    // --- Convenience Getters for the local player ---
    public int getMyCoins() {
        return localPlayerId == 1 ? player1Coins : player2Coins;
    }
    public int getOpponentCoins() {
        return localPlayerId == 1 ? player2Coins : player1Coins;
    }
    public double getMyLossPercentage() {
        return localPlayerId == 1 ? player1LossPercentage : player2LossPercentage;
    }
    public double getOpponentLossPercentage() {
        return localPlayerId == 1 ? player2LossPercentage : player1LossPercentage;
    }

    public boolean isLocalPlayerReady() {
        return localPlayerId == 1 ? isPlayer1Ready : isPlayer2Ready;
    }

    public boolean isOpponentReady() {
        return localPlayerId == 1 ? isPlayer2Ready : isPlayer1Ready;
    }

    public boolean isOpponentInStore() {
        return localPlayerId == 1 ? isPlayer2InStore : isPlayer1InStore;
    }
}