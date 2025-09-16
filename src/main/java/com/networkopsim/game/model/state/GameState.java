// ===== File: GameState.java (Final Corrected with MAX_LEVELS = 6) =====

package com.networkopsim.game.model.state;

import java.io.Serializable;

public class GameState implements Serializable {
    private static final long serialVersionUID = 1L;
    // [MODIFIED] Increased max levels to include the new test level.
    public static final int MAX_LEVELS = 6;
    private int coins = 30;
    private boolean[] unlockedLevels = new boolean[MAX_LEVELS];
    private int totalPacketLossUnits = 0;
    private int totalPacketUnitsGenerated = 0;
    private int totalPacketsGeneratedCount = 0;
    private int totalPacketsLostCount = 0;
    private int maxWireLengthPerLevel = 500;
    private int remainingWireLength = maxWireLengthPerLevel;
    private int currentSelectedLevel = 1;

    private double maxSafeEntrySpeed = 4.0;

    public GameState() {
        // Unlock all levels by default for testing purposes
        for (int i = 0; i < unlockedLevels.length; i++) {
            unlockedLevels[i] = true;
        }
        currentSelectedLevel = 1;
    }

    public int getCoins() { return coins; }
    public void addCoins(int amount) { if (amount > 0) coins += amount; }
    public boolean spendCoins(int amount) { if (amount > 0 && coins >= amount) { coins -= amount; return true; } return false; }

    public int getTotalPacketLossUnits() { return totalPacketLossUnits; }
    public int getTotalPacketUnitsGenerated() { return totalPacketUnitsGenerated; }

    public double getPacketLossPercentage() {
        if (totalPacketUnitsGenerated <= 0) return 0.0;
        double actualLossUnits = Math.min(totalPacketLossUnits, totalPacketUnitsGenerated);
        return Math.min(100.0, Math.max(0.0, ((double) actualLossUnits / totalPacketUnitsGenerated) * 100.0));
    }

    public int getTotalPacketsGeneratedCount() { return totalPacketsGeneratedCount; }
    public int getTotalPacketsLostCount() { return totalPacketsLostCount; }

    public void recordPacketGeneration(com.networkopsim.game.model.core.Packet packet) {
        if (packet != null) {
            totalPacketUnitsGenerated += packet.getSize();
            totalPacketsGeneratedCount++;
        }
    }

    public void increasePacketLoss(com.networkopsim.game.model.core.Packet packet) {
        if (packet != null) {
            totalPacketLossUnits += packet.getSize();
            totalPacketsLostCount++;
        }
    }

    private void resetPacketStatsForSimulationAttempt() {
        totalPacketLossUnits = 0;
        totalPacketUnitsGenerated = 0;
        totalPacketsGeneratedCount = 0;
        totalPacketsLostCount = 0;
        java.lang.System.out.println("GameState: Packet stats reset for simulation attempt.");
    }

    public int getRemainingWireLength() { return remainingWireLength; }
    public void useWire(int length) { if (length > 0) remainingWireLength -= length; }
    public void returnWire(int length) { if (length > 0) remainingWireLength += length; }

    public void setMaxWireLengthForLevel(int length) {
        this.maxWireLengthPerLevel = Math.max(0, length);
        this.remainingWireLength = this.maxWireLengthPerLevel;
        java.lang.System.out.println("GameState: Max wire length set to: " + this.maxWireLengthPerLevel);
    }

    public void setRemainingWireLength(int length) {
        this.remainingWireLength = length;
    }

    public int getMaxWireLengthForLevel() { return maxWireLengthPerLevel; }

    public void unlockLevel(int levelIndex) {
        if (levelIndex >= 0 && levelIndex < unlockedLevels.length) {
            if (!unlockedLevels[levelIndex]) {
                unlockedLevels[levelIndex] = true;
                java.lang.System.out.println("Level " + (levelIndex + 1) + " unlocked.");
            }
        } else {
            java.lang.System.err.println("Warning: Attempt to unlock invalid level index: " + levelIndex);
        }
    }

    public boolean isLevelUnlocked(int levelIndex) {
        if (levelIndex >= 0 && levelIndex < unlockedLevels.length) {
            return unlockedLevels[levelIndex];
        }
        return false;
    }

    public int getMaxLevels() { return MAX_LEVELS; }

    public void resetForNewLevel() {
        resetPacketStatsForSimulationAttempt();
        this.remainingWireLength = this.maxWireLengthPerLevel;
        java.lang.System.out.println("GameState: Full reset for new level.");
    }

    public void resetForSimulationAttemptOnly() {
        resetPacketStatsForSimulationAttempt();
        java.lang.System.out.println("GameState: Reset for simulation attempt ONLY.");
    }

    public void setCurrentSelectedLevel(int level) {
        if (level >= 1 && level <= MAX_LEVELS) this.currentSelectedLevel = level;
        else java.lang.System.err.println("Attempt to set invalid current selected level: " + level);
    }

    public int getCurrentSelectedLevel() { return currentSelectedLevel; }

    public double getMaxSafeEntrySpeed() {
        return maxSafeEntrySpeed;
    }

    public void setMaxSafeEntrySpeed(double speed) {
        this.maxSafeEntrySpeed = speed;
    }
}