// FILE: GameState.java
public class GameState {
    public static final int MAX_LEVELS = 2;
    private int coins = 0;
    private boolean[] unlockedLevels = new boolean[MAX_LEVELS];

    // Existing Packet Unit Stats
    private int totalPacketLossUnits = 0;
    private int totalPacketUnitsGenerated = 0;

    // --- NEW Packet Count Stats ---
    private int totalPacketsGeneratedCount = 0;
    private int totalPacketsLostCount = 0;
    // -----------------------------

    private int maxWireLengthPerLevel = 500;
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

    /** Resets both unit and count stats for a new level attempt. */
    private void resetPacketStats() {
        totalPacketLossUnits = 0;
        totalPacketUnitsGenerated = 0;
        totalPacketsGeneratedCount = 0; // <<< RESET COUNT
        totalPacketsLostCount = 0;      // <<< RESET COUNT
    }

    // --- Wire Methods ---
    public int getRemainingWireLength() { return remainingWireLength; }
    public boolean useWire(int length) { if (length > 0 && remainingWireLength >= length) { remainingWireLength -= length; return true; } return false; }
    public void returnWire(int length) { if (length > 0) remainingWireLength += length; }
    public void setMaxWireLengthForLevel(int length) {
        this.maxWireLengthPerLevel = Math.max(0, length);
        this.remainingWireLength = this.maxWireLengthPerLevel;
        java.lang.System.out.println("Level wire budget set to: " + this.maxWireLengthPerLevel);
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

    /** Resets stats and wire length for a new level attempt. */
    public void resetForLevel() {
        resetPacketStats(); // This now resets counts too
        this.remainingWireLength = this.maxWireLengthPerLevel;
        java.lang.System.out.println("GameState reset for level attempt. Wire length: " + this.remainingWireLength + " (Max: " + this.maxWireLengthPerLevel + ")");
    }

    // --- Current Level Selection ---
    public void setCurrentSelectedLevel(int level) {
        if (level >= 1 && level <= MAX_LEVELS) this.currentSelectedLevel = level;
        else java.lang.System.err.println("Attempt to set invalid current selected level: " + level);
    }
    public int getCurrentSelectedLevel() { return currentSelectedLevel; }
}