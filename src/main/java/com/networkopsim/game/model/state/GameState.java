// ===== File: GameState.java (FINAL - Corrected Double Counting for BULK packets) =====

package com.networkopsim.game.model.state;

import com.networkopsim.game.model.core.Packet;
import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class GameState implements Serializable {
    private static final long serialVersionUID = 1L;
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

    private volatile boolean isDistributorBusy = false;
    private final Map<Integer, Integer> activeBulkPackets = new ConcurrentHashMap<>();

    public GameState() {
        for (int i = 0; i < unlockedLevels.length; i++) {
            unlockedLevels[i] = true;
        }
        currentSelectedLevel = 1;
    }

    public boolean isDistributorBusy() {
        return isDistributorBusy;
    }

    public void setDistributorBusy(boolean busy) {
        this.isDistributorBusy = busy;
    }

    public void registerActiveBulkPacket(Packet bulkPacket) {
        if (bulkPacket != null && bulkPacket.getPacketType() == com.networkopsim.game.model.enums.NetworkEnums.PacketType.BULK) {
            activeBulkPackets.put(bulkPacket.getId(), bulkPacket.getSize());
        }
    }

    public void resolveBulkPacket(int bulkParentId, int partsMerged) {
        Integer originalSize = activeBulkPackets.remove(bulkParentId);
        if (originalSize != null) {
            int loss = originalSize - partsMerged;
            if (loss > 0) {
                this.totalPacketLossUnits += loss;
                // Only count the whole packet as 'lost' if none of its parts made it.
                if (partsMerged == 0) {
                    this.totalPacketsLostCount++;
                }
            }
        }
    }

    public void recordLostBulkPart(Packet part) {
        // This logic is handled by the Merger's final resolution.
    }

    public void recordPacketGeneration(Packet packet) {
        if (packet != null) {
            // [FIXED] Do not count the units of a BULK packet here because its parts'
            // units will be counted later in recordBulkPartsGeneration. This prevents double-counting.
            if (packet.getPacketType() != com.networkopsim.game.model.enums.NetworkEnums.PacketType.BULK) {
                totalPacketUnitsGenerated += packet.getSize();
            }
            totalPacketsGeneratedCount++;
        }
    }

    public void recordBulkPartsGeneration(int bulkParentId, int totalSize) {
        // The units for the entire original bulk packet are added here.
        this.totalPacketUnitsGenerated += totalSize;
    }

    public void increasePacketLoss(Packet packet) {
        if (packet != null) {
            // For BULK packets destroyed before distribution or parts lost individually.
            if (packet.getPacketType() == com.networkopsim.game.model.enums.NetworkEnums.PacketType.BULK) {
                // A BULK packet is only "lost" if it hits a destructive system before a Distributor.
                totalPacketLossUnits += packet.getSize();
                totalPacketsLostCount++;
                return; // Prevent double-counting below.
            }
            // If a MESSENGER that's part of a bulk operation is lost, its loss is handled
            // by the resolveBulkPacket logic when the Merger finalizes the count.
            if (packet.getBulkParentId() != -1) {
                return;
            }

            // Standard packet loss for non-bulk-related packets.
            totalPacketLossUnits += packet.getSize();
            totalPacketsLostCount++;
        }
    }

    public void resetForNewLevel() {
        resetPacketStatsForSimulationAttempt();
        this.remainingWireLength = this.maxWireLengthPerLevel;
        activeBulkPackets.clear();
        this.isDistributorBusy = false;
        java.lang.System.out.println("GameState: Full reset for new level.");
    }

    public void resetForSimulationAttemptOnly() {
        resetPacketStatsForSimulationAttempt();
        activeBulkPackets.clear();
        this.isDistributorBusy = false;
        java.lang.System.out.println("GameState: Reset for simulation attempt ONLY.");
    }

    private void resetPacketStatsForSimulationAttempt() {
        totalPacketLossUnits = 0;
        totalPacketUnitsGenerated = 0;
        totalPacketsGeneratedCount = 0;
        totalPacketsLostCount = 0;
    }

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
    public void setRemainingWireLength(int length) { this.remainingWireLength = length; }
    public int getMaxWireLengthForLevel() { return maxWireLengthPerLevel; }
    public void unlockLevel(int levelIndex) { if (levelIndex >= 0 && levelIndex < unlockedLevels.length) { if (!unlockedLevels[levelIndex]) { unlockedLevels[levelIndex] = true; java.lang.System.out.println("Level " + (levelIndex + 1) + " unlocked."); } } else { java.lang.System.err.println("Warning: Attempt to unlock invalid level index: " + levelIndex); } }
    public boolean isLevelUnlocked(int levelIndex) { if (levelIndex >= 0 && levelIndex < unlockedLevels.length) { return unlockedLevels[levelIndex]; } return false; }
    public int getMaxLevels() { return MAX_LEVELS; }
    public void setCurrentSelectedLevel(int level) { if (level >= 1 && level <= MAX_LEVELS) this.currentSelectedLevel = level; }
    public int getCurrentSelectedLevel() { return currentSelectedLevel; }
    public double getMaxSafeEntrySpeed() { return maxSafeEntrySpeed; }
    public void setMaxSafeEntrySpeed(double speed) { this.maxSafeEntrySpeed = speed; }
}