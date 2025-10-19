// FILE: /NetworkOpSim-Multiplayer/NetworkOpSim-Core/src/main/java/com/networkopsim/core/game/model/state/GameState.java
// ================================================================================

package com.networkopsim.core.game.model.state;

import com.networkopsim.core.game.model.core.Packet;
import com.networkopsim.shared.model.NetworkEnums;

import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class GameState implements Serializable {
    private static final long serialVersionUID = 3L;
    public static final int MAX_LEVELS = 5;

    // --- Player 1 Stats ---
    private int player1Coins = 30;
    private int player1TotalPacketLossUnits = 0;
    private int player1TotalPacketUnitsGenerated = 0;
    private int player1TotalPacketsGeneratedCount = 0;
    private int player1TotalPacketsLostCount = 0;
    private double player1CooldownModifier = 1.0;
    private long player1LastPenaltyTime = 0;
    private int player1PendingAmmoReward = 0; // <<<--- NEW FIELD

    // --- Player 2 Stats ---
    private int player2Coins = 30;
    private int player2TotalPacketLossUnits = 0;
    private int player2TotalPacketUnitsGenerated = 0;
    private int player2TotalPacketsGeneratedCount = 0;
    private int player2TotalPacketsLostCount = 0;
    private double player2CooldownModifier = 1.0;
    private long player2LastPenaltyTime = 0;
    private int player2PendingAmmoReward = 0; // <<<--- NEW FIELD

    // --- Shared/Global State ---
    private boolean[] unlockedLevels = new boolean[MAX_LEVELS];
    private int maxWireLengthPerLevel = 500;
    private int remainingWireLength = maxWireLengthPerLevel;
    private int currentSelectedLevel = 1;
    private double maxSafeEntrySpeed = 4.0;
    private volatile boolean isDistributorBusy = false;
    private double globalPacketSpeedModifier = 1.0;

    public static class BulkPacketInfo implements Serializable {
        private static final long serialVersionUID = 1L;
        public final int originalSize;
        public final NetworkEnums.PacketType originalType;
        public final int ownerId;

        public BulkPacketInfo(int size, NetworkEnums.PacketType type, int ownerId) {
            this.originalSize = size;
            this.originalType = type;
            this.ownerId = ownerId;
        }
    }

    private final Map<Integer, BulkPacketInfo> activeBulkPackets = new ConcurrentHashMap<>();

    public GameState() {
        unlockedLevels[0] = true; // Level 1 is always unlocked
    }

    // --- MUTATORS (PLAYER-AWARE) ---

    public void addCoins(int playerId, int amount) {
        if (amount <= 0) return;
        if (playerId == 1) {
            player1Coins += amount;
        } else if (playerId == 2) {
            player2Coins += amount;
        }
    }

    public boolean spendCoins(int playerId, int amount) {
        if (amount <= 0) return false;
        if (playerId == 1 && player1Coins >= amount) {
            player1Coins -= amount;
            return true;
        } else if (playerId == 2 && player2Coins >= amount) {
            player2Coins -= amount;
            return true;
        }
        return false;
    }

    public void recordPacketGeneration(Packet packet) {
        if (packet == null) return;
        int ownerId = packet.getOwnerId();
        if (ownerId == 1) {
            player1TotalPacketUnitsGenerated += packet.getSize();
            player1TotalPacketsGeneratedCount++;
        } else if (ownerId == 2) {
            player2TotalPacketUnitsGenerated += packet.getSize();
            player2TotalPacketsGeneratedCount++;
        }
    }

    public void increasePacketLoss(Packet packet) {
        if (packet == null) return;
        int ownerId = packet.getOwnerId();
        int packetSize = packet.getSize();

        if (ownerId == 1) {
            player1TotalPacketLossUnits += packetSize;
            if (packet.getBulkParentId() == -1) {
                player1TotalPacketsLostCount++;
            }
        } else if (ownerId == 2) {
            player2TotalPacketLossUnits += packetSize;
            if (packet.getBulkParentId() == -1) {
                player2TotalPacketsLostCount++;
            }
        }
    }

    public void registerActiveBulkPacket(Packet bulkPacket) {
        if (bulkPacket != null && bulkPacket.isVolumetric()) {
            activeBulkPackets.put(bulkPacket.getId(), new BulkPacketInfo(bulkPacket.getSize(), bulkPacket.getPacketType(), bulkPacket.getOwnerId()));
        }
    }

    public void resolveBulkPacket(int bulkParentId, int partsMerged) {
        BulkPacketInfo info = activeBulkPackets.remove(bulkParentId);
        if (info != null) {
            int loss = info.originalSize - partsMerged;
            if (loss > 0) {
                if (info.ownerId == 1) {
                    player1TotalPacketLossUnits += loss;
                    if (partsMerged == 0) player1TotalPacketsLostCount++;
                } else if (info.ownerId == 2) {
                    player2TotalPacketLossUnits += loss;
                    if (partsMerged == 0) player2TotalPacketsLostCount++;
                }
            }
        }
    }

    // --- New Mutators for Feedback Loop and Penalties ---

    public void recordSuccessfulDeliveryForFeedback(int ownerId) {
        if (ownerId == 1) {
            player1PendingAmmoReward++;
        } else if (ownerId == 2) {
            player2PendingAmmoReward++;
        }
    }

    public int consumePendingAmmoReward(int playerId) {
        int reward = 0;
        if (playerId == 1) {
            reward = player1PendingAmmoReward;
            player1PendingAmmoReward = 0;
        } else if (playerId == 2) {
            reward = player2PendingAmmoReward;
            player2PendingAmmoReward = 0;
        }
        return reward;
    }

    public void increaseCooldownModifier(int playerId, double amount) {
        if (playerId == 1) {
            player1CooldownModifier += amount;
        } else if (playerId == 2) {
            player2CooldownModifier += amount;
        }
    }

    public void increaseGlobalPacketSpeedModifier(double amount) {
        globalPacketSpeedModifier += amount;
    }

    public void setLastPenaltyTime(int playerId, long time) {
        if (playerId == 1) {
            player1LastPenaltyTime = time;
        } else if (playerId == 2) {
            player2LastPenaltyTime = time;
        }
    }

    // --- RESET AND SETUP ---

    public void resetForNewLevel() {
        resetForSimulationAttemptOnly();
        this.remainingWireLength = this.maxWireLengthPerLevel;
    }

    public void resetForSimulationAttemptOnly() {
        player1Coins = 30;
        player1TotalPacketLossUnits = 0;
        player1TotalPacketUnitsGenerated = 0;
        player1TotalPacketsGeneratedCount = 0;
        player1TotalPacketsLostCount = 0;
        player1CooldownModifier = 1.0;
        player1LastPenaltyTime = 0;
        player1PendingAmmoReward = 0; // <<<--- RESET NEW FIELD

        player2Coins = 30;
        player2TotalPacketLossUnits = 0;
        player2TotalPacketUnitsGenerated = 0;
        player2TotalPacketsGeneratedCount = 0;
        player2TotalPacketsLostCount = 0;
        player2CooldownModifier = 1.0;
        player2LastPenaltyTime = 0;
        player2PendingAmmoReward = 0; // <<<--- RESET NEW FIELD

        globalPacketSpeedModifier = 1.0;
        activeBulkPackets.clear();
        this.isDistributorBusy = false;
    }

    // --- GETTERS (PLAYER-SPECIFIC) ---
    public int getPlayer1Coins() { return player1Coins; }
    public int getPlayer1TotalPacketLossUnits() { return player1TotalPacketLossUnits; }
    public int getPlayer1TotalPacketUnitsGenerated() { return player1TotalPacketUnitsGenerated; }
    public int getPlayer1TotalPacketsGeneratedCount() { return player1TotalPacketsGeneratedCount; }
    public int getPlayer1TotalPacketsLostCount() { return player1TotalPacketsLostCount; }
    public double getPlayer1PacketLossPercentage() {
        if (player1TotalPacketUnitsGenerated <= 0) return 0.0;
        return Math.min(100.0, ((double) player1TotalPacketLossUnits / player1TotalPacketUnitsGenerated) * 100.0);
    }

    public int getPlayer2Coins() { return player2Coins; }
    public int getPlayer2TotalPacketUnitsGenerated() { return player2TotalPacketUnitsGenerated; }
    public int getPlayer2TotalPacketsGeneratedCount() { return player2TotalPacketsGeneratedCount; }
    public int getPlayer2TotalPacketsLostCount() { return player2TotalPacketsLostCount; }
    public double getPlayer2PacketLossPercentage() {
        if (player2TotalPacketUnitsGenerated <= 0) return 0.0;
        return Math.min(100.0, ((double) player2TotalPacketLossUnits / player2TotalPacketUnitsGenerated) * 100.0);
    }

    // --- New Getters for Penalties ---

    public double getCooldownModifier(int playerId) {
        return (playerId == 1) ? player1CooldownModifier : player2CooldownModifier;
    }

    public long getLastPenaltyTime(int playerId) {
        return (playerId == 1) ? player1LastPenaltyTime : player2LastPenaltyTime;
    }

    public double getGlobalPacketSpeedModifier() {
        return globalPacketSpeedModifier;
    }


    // --- GETTERS & SETTERS (GLOBAL) ---
    public NetworkEnums.PacketType getOriginalBulkPacketType(int bulkParentId) {
        BulkPacketInfo info = activeBulkPackets.get(bulkParentId);
        return (info != null) ? info.originalType : NetworkEnums.PacketType.BULK;
    }
    public boolean isDistributorBusy() { return isDistributorBusy; }
    public void setDistributorBusy(boolean busy) { this.isDistributorBusy = busy; }
    public int getRemainingWireLength() { return remainingWireLength; }
    public void useWire(int length) { if (length > 0) remainingWireLength -= length; }
    public void returnWire(int length) { if (length > 0) remainingWireLength += length; }
    public void setMaxWireLengthForLevel(int length) { this.maxWireLengthPerLevel = Math.max(0, length); this.remainingWireLength = this.maxWireLengthPerLevel; }
    public int getMaxWireLengthForLevel() { return maxWireLengthPerLevel; }
    public void unlockLevel(int levelIndex) { if (levelIndex >= 0 && levelIndex < unlockedLevels.length) unlockedLevels[levelIndex] = true; }
    public boolean isLevelUnlocked(int levelIndex) { return levelIndex >= 0 && levelIndex < unlockedLevels.length && unlockedLevels[levelIndex]; }
    public int getMaxLevels() { return MAX_LEVELS; }
    public void setCurrentSelectedLevel(int level) { if (level >= 1 && level <= MAX_LEVELS) this.currentSelectedLevel = level; }
    public int getCurrentSelectedLevel() { return currentSelectedLevel; }
    public double getMaxSafeEntrySpeed() { return maxSafeEntrySpeed; }
    public void setMaxSafeEntrySpeed(double speed) { this.maxSafeEntrySpeed = speed; }
}