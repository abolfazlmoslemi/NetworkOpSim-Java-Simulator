// ==== GameState.java ====

// ======= GameState.java =======
package com.networkopsim.game;
// FILE: GameState.java
public class GameState {
    public static final int MAX_LEVELS = 2;
    private int coins = 30;
    private boolean[] unlockedLevels = new boolean[MAX_LEVELS];

    // Existing Packet Unit Stats
    private int totalPacketLossUnits = 0;
    private int totalPacketUnitsGenerated = 0;

    // --- NEW Packet Count Stats ---
    private int totalPacketsGeneratedCount = 0;
    private int totalPacketsLostCount = 0;
    // -----------------------------

    private int maxWireLengthPerLevel = 500; // Initial default, will be set by LevelLoader
    private int remainingWireLength = maxWireLengthPerLevel;
    private int currentSelectedLevel = 1;

    public GameState() {
        unlockedLevels[0] = true;
        currentSelectedLevel = 1;
    }

    // --- Coin Methods ---
    public int getCoins() { return coins; }
    public void addCoins(int amount) { if (amount > 0) coins += amount; }
    public boolean spendCoins(int amount) { if (amount > 0 && coins >= amount) { coins -= amount; return true; } return false; }

    // --- Packet Unit Stat Methods ---
    public int getTotalPacketLossUnits() { return totalPacketLossUnits; }
    public int getTotalPacketUnitsGenerated() { return totalPacketUnitsGenerated; }

    /** Calculates loss percentage based on packet UNITS (size). */
    public double getPacketLossPercentage() {
        if (totalPacketUnitsGenerated <= 0) return 0.0;
        // Ensure loss units don't exceed generated units for percentage calculation
        double actualLossUnits = Math.min(totalPacketLossUnits, totalPacketUnitsGenerated);
        double lossRatio = (double) actualLossUnits / totalPacketUnitsGenerated;
        return Math.min(100.0, Math.max(0.0, lossRatio * 100.0));
    }

    // --- Packet Count Stat Methods (NEW) ---
    public int getTotalPacketsGeneratedCount() { return totalPacketsGeneratedCount; }
    public int getTotalPacketsLostCount() { return totalPacketsLostCount; }
    // ---------------------------------------

    /** Records a generated packet's units AND increments the count. */
    public void recordPacketGeneration(Packet packet) {
        if (packet != null) {
            totalPacketUnitsGenerated += packet.getSize();
            totalPacketsGeneratedCount++; // <<< INCREMENT COUNT HERE
        }
    }

    /** Increases lost packet units AND increments the lost count. */
    public void increasePacketLoss(Packet packet) {
        if (packet != null) {
            totalPacketLossUnits += packet.getSize();
            totalPacketsLostCount++; // <<< INCREMENT COUNT HERE
        }
    }

    /** Resets only packet-related stats for a new simulation attempt. Wire length is NOT reset here. */
    private void resetPacketStatsForSimulationAttempt() {
        totalPacketLossUnits = 0;
        totalPacketUnitsGenerated = 0;
        totalPacketsGeneratedCount = 0;
        totalPacketsLostCount = 0;
        // coins earned in this attempt would also be reset if tracked separately per attempt
        // for now, global coins are not reset per attempt, only per level initialization if desired
        java.lang.System.out.println("GameState: Packet stats reset for simulation attempt.");
    }

    // --- Wire Methods ---
    public int getRemainingWireLength() { return remainingWireLength; }

    /**
     * MODIFIED: This method now returns void and will ALWAYS subtract the length,
     * allowing remainingWireLength to go negative. Callers are responsible for
     * pre-checking the budget if they want to prevent exceeding it.
     */
    public void useWire(int length) {
        if (length > 0) {
            remainingWireLength -= length;
        }
    }

    public void returnWire(int length) {
        if (length > 0) {
            remainingWireLength += length;
        }
    }
    public void setMaxWireLengthForLevel(int length) {
        this.maxWireLengthPerLevel = Math.max(0, length);
        // This is the key: when a new level's max wire is set, also reset current remaining to this max.
        this.remainingWireLength = this.maxWireLengthPerLevel;
        java.lang.System.out.println("GameState: Max wire length for level set to: " + this.maxWireLengthPerLevel + ". Remaining wire also reset to this value.");
    }
    public int getMaxWireLengthForLevel() { return maxWireLengthPerLevel; }

    // --- Level Management ---
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

    /**
     * Resets stats for a new level or a full level restart.
     * This will reset packet stats AND set remaining wire length to the max for the level.
     */
    public void resetForNewLevel() {
        resetPacketStatsForSimulationAttempt(); // Resets packet counts and units
        this.remainingWireLength = this.maxWireLengthPerLevel; // Reset wire to max for the level
        // Any other stats that are per-level (not per-attempt) should be reset here.
        // For example, coins might be reset here if they are not persistent across levels.
        // For now, coins are persistent.
        java.lang.System.out.println("GameState: Full reset for new level. Wire length: " + this.remainingWireLength + " (Max: " + this.maxWireLengthPerLevel + ")");
    }

    /**
     * Resets stats specifically for a new simulation *attempt* within the same level.
     * Importantly, this does NOT reset the remainingWireLength, as wire usage
     * should persist across attempts within the same level editing phase.
     */
    public void resetForSimulationAttemptOnly() {
        resetPacketStatsForSimulationAttempt(); // Resets packet counts and units
        // DO NOT reset remainingWireLength here.
        java.lang.System.out.println("GameState: Reset for simulation attempt ONLY. Packet stats cleared. Wire length ("+ remainingWireLength +") UNCHANGED.");
    }


    // --- Current Level Selection ---
    public void setCurrentSelectedLevel(int level) {
        if (level >= 1 && level <= MAX_LEVELS) this.currentSelectedLevel = level;
        else java.lang.System.err.println("Attempt to set invalid current selected level: " + level);
    }
    public int getCurrentSelectedLevel() { return currentSelectedLevel; }
}