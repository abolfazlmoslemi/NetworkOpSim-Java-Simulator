public class GameState {
    public static final int MAX_LEVELS = 2; 
    private int coins = 0;
    private boolean[] unlockedLevels = new boolean[MAX_LEVELS];
    private int totalPacketLossUnits = 0;
    private int totalPacketUnitsGenerated = 0;
    private int maxWireLengthPerLevel = 500; 
    private int remainingWireLength = maxWireLengthPerLevel;
    private int currentSelectedLevel = 1; 
    public GameState() {
        unlockedLevels[0] = true; 
        currentSelectedLevel = 1;
    }
    public int getCoins() { return coins; }
    public void addCoins(int amount) { if (amount > 0) coins += amount; }
    public boolean spendCoins(int amount) { if (amount > 0 && coins >= amount) { coins -= amount; return true; } return false; }
    public int getTotalPacketLossUnits() { return totalPacketLossUnits; }
    public int getTotalPacketUnitsGenerated() { return totalPacketUnitsGenerated; }
    public void recordPacketGeneration(Packet packet) {
        if (packet != null) totalPacketUnitsGenerated += packet.getSize();
    }
    public void increasePacketLoss(Packet packet) {
        if (packet != null) totalPacketLossUnits += packet.getSize();
    }
    public double getPacketLossPercentage() {
        if (totalPacketUnitsGenerated <= 0) return 0.0;
        double lossRatio = (double) Math.min(totalPacketLossUnits, totalPacketUnitsGenerated) / totalPacketUnitsGenerated;
        return Math.min(100.0, Math.max(0.0, lossRatio * 100.0));
    }
    private void resetPacketStats() { totalPacketLossUnits = 0; totalPacketUnitsGenerated = 0; }
    public int getRemainingWireLength() { return remainingWireLength; }
    public boolean useWire(int length) { if (length > 0 && remainingWireLength >= length) { remainingWireLength -= length; return true; } return false; }
    public void returnWire(int length) { if (length > 0) remainingWireLength += length; }
    public void setMaxWireLengthForLevel(int length) {
        this.maxWireLengthPerLevel = Math.max(0, length);
        this.remainingWireLength = this.maxWireLengthPerLevel;
        java.lang.System.out.println("Level wire budget set to: " + this.maxWireLengthPerLevel);
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
    public void resetForLevel() {
        resetPacketStats();
        this.remainingWireLength = this.maxWireLengthPerLevel;
        java.lang.System.out.println("GameState reset for level attempt. Wire length: " + this.remainingWireLength + " (Max: " + this.maxWireLengthPerLevel + ")");
    }
    public void setCurrentSelectedLevel(int level) { 
        if (level >= 1 && level <= MAX_LEVELS) this.currentSelectedLevel = level;
        else java.lang.System.err.println("Attempt to set invalid current selected level: " + level);
    }
    public int getCurrentSelectedLevel() { return currentSelectedLevel; }
}