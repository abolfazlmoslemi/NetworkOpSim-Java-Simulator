// ===== File: GameState.java (FINAL CORRECTED with fix for compile error) =====
// ===== MODULE: common =====

package com.networkopsim.game.model.state;

import com.networkopsim.game.model.core.Packet;
import com.networkopsim.game.model.enums.NetworkEnums;

import java.awt.geom.Point2D;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GameState implements Serializable {
    private static final long serialVersionUID = 103L;
    public static final int MAX_LEVELS = 5;

    public enum InteractiveMode { NONE, AERGIA_PLACEMENT, SISYPHUS_DRAG, ELIPHAS_PLACEMENT }

    public static class ActiveWireEffect implements Serializable {
        private static final long serialVersionUID = 105L;
        public final InteractiveMode type;
        public final Point2D.Double position;
        public final int parentWireId;
        public final long expiryTime;

        public ActiveWireEffect(InteractiveMode type, Point2D.Double pos, int wireId, long expiry) {
            this.type = type;
            this.position = pos;
            this.parentWireId = wireId;
            this.expiryTime = expiry;
        }
    }

    private int coins = 30;
    private boolean[] unlockedLevels = new boolean[MAX_LEVELS];
    private int totalPacketLossUnits = 0;
    private int totalPacketUnitsGenerated = 0;
    private int totalPacketsGeneratedCount = 0;
    private int totalPacketsLostCount = 0;
    private int maxWireLengthPerLevel = 500;
    private int remainingWireLength = maxWireLengthPerLevel;
    private int currentSelectedLevel = 1;
    private long currentLevelTimeLimitMs = 0;
    private double maxSafeEntrySpeed = 4.0;
    private volatile boolean isDistributorBusy = false;

    private volatile InteractiveMode currentInteractiveMode = InteractiveMode.NONE;
    private final List<ActiveWireEffect> activeWireEffects = new ArrayList<>();
    private long aergiaCooldownUntil = 0;

    public static class BulkPacketInfo implements Serializable {
        private static final long serialVersionUID = 104L;
        public final int originalSize;
        public final NetworkEnums.PacketType originalType;
        public BulkPacketInfo(int size, NetworkEnums.PacketType type) { this.originalSize = size; this.originalType = type; }
    }

    private final Map<Integer, BulkPacketInfo> activeBulkPackets = new HashMap<>();

    public GameState() {
        unlockedLevels[0] = true;
        for (int i = 1; i < unlockedLevels.length; i++) {
            unlockedLevels[i] = false;
        }
        currentSelectedLevel = 1;
    }

    public void resetForNewLevel() {
        resetPacketStatsForSimulationAttempt();
        // [FIXED] Corrected the variable name here
        this.remainingWireLength = maxWireLengthPerLevel;
        activeBulkPackets.clear();
        this.isDistributorBusy = false;
        this.currentInteractiveMode = InteractiveMode.NONE;
        this.activeWireEffects.clear();
        this.aergiaCooldownUntil = 0;
    }

    public void resetForSimulationAttemptOnly() {
        resetPacketStatsForSimulationAttempt();
        activeBulkPackets.clear();
        this.isDistributorBusy = false;
        this.currentInteractiveMode = InteractiveMode.NONE;
        this.activeWireEffects.clear();
        this.aergiaCooldownUntil = 0;
    }

    private void resetPacketStatsForSimulationAttempt() {
        totalPacketLossUnits = 0;
        totalPacketUnitsGenerated = 0;
        totalPacketsGeneratedCount = 0;
        totalPacketsLostCount = 0;
    }

    public boolean isDistributorBusy() { return isDistributorBusy; }
    public void setDistributorBusy(boolean busy) { this.isDistributorBusy = busy; }
    public void registerActiveBulkPacket(Packet bulkPacket) { if (bulkPacket != null && bulkPacket.isVolumetric()) { activeBulkPackets.put(bulkPacket.getId(), new BulkPacketInfo(bulkPacket.getSize(), bulkPacket.getPacketType())); } }
    public void resolveBulkPacket(int bulkParentId, int partsMerged) { BulkPacketInfo info = activeBulkPackets.remove(bulkParentId); if (info != null) { int loss = info.originalSize - partsMerged; if (loss > 0) { this.totalPacketLossUnits += loss; if (partsMerged == 0) { this.totalPacketsLostCount++; } } } }
    public NetworkEnums.PacketType getOriginalBulkPacketType(int bulkParentId) { BulkPacketInfo info = activeBulkPackets.get(bulkParentId); return (info != null) ? info.originalType : NetworkEnums.PacketType.BULK; }
    public void recordPacketGeneration(Packet packet) { if (packet != null) { totalPacketUnitsGenerated += packet.getSize(); totalPacketsGeneratedCount++; } }
    public void increasePacketLoss(Packet packet) { if (packet != null) { if (packet.getBulkParentId() != -1) { totalPacketLossUnits += packet.getSize(); } else { totalPacketLossUnits += packet.getSize(); totalPacketsLostCount++; } } }
    public int getCoins() { return coins; }
    public void addCoins(int amount) { if (amount > 0) coins += amount; }
    public boolean spendCoins(int amount) { if (amount > 0 && coins >= amount) { coins -= amount; return true; } return false; }
    public int getTotalPacketLossUnits() { return totalPacketLossUnits; }
    public int getTotalPacketUnitsGenerated() { return totalPacketUnitsGenerated; }
    public double getPacketLossPercentage() { if (totalPacketUnitsGenerated <= 0) return 0.0; double actualLossUnits = Math.min(totalPacketLossUnits, totalPacketUnitsGenerated); return Math.min(100.0, Math.max(0.0, ((double) actualLossUnits / totalPacketUnitsGenerated) * 100.0)); }
    public int getTotalPacketsGeneratedCount() { return totalPacketsGeneratedCount; }
    public int getTotalPacketsLostCount() { return totalPacketsLostCount; }
    public int getRemainingWireLength() { return remainingWireLength; }
    public void useWire(int length) { if (length > 0) remainingWireLength -= length; }
    public void returnWire(int length) { if (length > 0) remainingWireLength += length; }
    public void setMaxWireLengthForLevel(int length) { this.maxWireLengthPerLevel = Math.max(0, length); this.remainingWireLength = this.maxWireLengthPerLevel; }
    public int getMaxWireLengthForLevel() { return maxWireLengthPerLevel; }
    public void unlockLevel(int levelIndex) { if (levelIndex >= 0 && levelIndex < unlockedLevels.length) { if (!unlockedLevels[levelIndex]) { unlockedLevels[levelIndex] = true; } } }
    public boolean isLevelUnlocked(int levelIndex) { return (levelIndex >= 0 && levelIndex < unlockedLevels.length) ? unlockedLevels[levelIndex] : false; }
    public int getMaxLevels() { return MAX_LEVELS; }
    public void setCurrentSelectedLevel(int level) { if (level >= 1 && level <= MAX_LEVELS) this.currentSelectedLevel = level; }
    public int getCurrentSelectedLevel() { return currentSelectedLevel; }
    public double getMaxSafeEntrySpeed() { return maxSafeEntrySpeed; }
    public void setMaxSafeEntrySpeed(double speed) { this.maxSafeEntrySpeed = speed; }
    public long getCurrentLevelTimeLimitMs() { return currentLevelTimeLimitMs; }
    public void setCurrentLevelTimeLimitMs(long time) { this.currentLevelTimeLimitMs = time; }
    public InteractiveMode getCurrentInteractiveMode() { return currentInteractiveMode; }
    public void setCurrentInteractiveMode(InteractiveMode mode) { this.currentInteractiveMode = mode; }
    public List<ActiveWireEffect> getActiveWireEffects() { return activeWireEffects; }
    public long getAergiaCooldownUntil() { return aergiaCooldownUntil; }
    public void setAergiaCooldownUntil(long time) { this.aergiaCooldownUntil = time; }
}