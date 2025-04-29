

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
    }

    public int getCoins() {
        return coins;
    }

    public void addCoins(int amount) {
        if (amount > 0) coins += amount;
    }

    public boolean spendCoins(int amount) {
        if (amount > 0 && coins >= amount) {
            coins -= amount;
            return true;
        }
        return false;
    }

    public void recordPacketGeneration(Packet packet) {
        if (packet != null) totalPacketUnitsGenerated += packet.getSize();
    }

    public void increasePacketLoss(Packet packet) {
        if (packet != null) totalPacketLossUnits += packet.getSize();
    }

    public double getPacketLossPercentage() {
        if (totalPacketUnitsGenerated <= 0) return 0.0;
        double ratio = (double) Math.min(totalPacketLossUnits, totalPacketUnitsGenerated)
                / totalPacketUnitsGenerated;
        return Math.min(100.0, Math.max(0.0, ratio * 100.0));
    }

    public int getRemainingWireLength() {
        return remainingWireLength;
    }

    public boolean useWire(int length) {
        if (length > 0 && remainingWireLength >= length) {
            remainingWireLength -= length;
            return true;
        }
        return false;
    }

    public void returnWire(int length) {
        if (length > 0) remainingWireLength += length;
    }

    public void setMaxWireLengthForLevel(int length) {
        maxWireLengthPerLevel = Math.max(0, length);
        remainingWireLength = maxWireLengthPerLevel;
        System.out.println("Wire budget set to: " + maxWireLengthPerLevel);
    }

    public void resetForLevel() {
        totalPacketLossUnits = 0;
        totalPacketUnitsGenerated = 0;
        remainingWireLength = maxWireLengthPerLevel;
        System.out.println("GameState reset. Remaining wire: " + remainingWireLength);
    }

    public int getCurrentSelectedLevel() {
        return currentSelectedLevel;
    }

    public void setCurrentSelectedLevel(int level) {
        if (level >= 1 && level <= MAX_LEVELS) currentSelectedLevel = level;
    }
}
