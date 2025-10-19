// FILE: /NetworkOpSim-Multiplayer/NetworkOpSim-Server/src/main/java/com/networkopsim/server/persistence/PlayerProfile.java
// ================================================================================

package com.networkopsim.server.persistence;

/**
 * A Plain Old Java Object (POJO) to hold a player's profile data in memory.
 */
public class PlayerProfile {
    private final int playerId;
    private final String username;
    private int xp;
    private int coins;
    private String unlockedLevels; // e.g., "1,2,3"

    public PlayerProfile(int playerId, String username, int xp, int coins, String unlockedLevels) {
        this.playerId = playerId;
        this.username = username;
        this.xp = xp;
        this.coins = coins;
        this.unlockedLevels = unlockedLevels;
    }

    // --- Getters ---
    public int getPlayerId() {
        return playerId;
    }

    public String getUsername() {
        return username;
    }

    public int getXp() {
        return xp;
    }

    public int getCoins() {
        return coins;
    }

    public String getUnlockedLevels() {
        return unlockedLevels;
    }

    // --- Setters for mutable fields ---
    public void setXp(int xp) {
        this.xp = xp;
    }

    public void setCoins(int coins) {
        this.coins = coins;
    }

    public void setUnlockedLevels(String unlockedLevels) {
        this.unlockedLevels = unlockedLevels;
    }
}