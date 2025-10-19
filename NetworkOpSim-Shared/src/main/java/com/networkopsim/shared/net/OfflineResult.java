// FILE: /NetworkOpSim-Multiplayer/NetworkOpSim-Shared/src/main/java/com/networkopsim/shared/net/OfflineResult.java
// ================================================================================

package com.networkopsim.shared.net;

import java.io.Serializable;
import java.util.List;

/**
 * A container for the results of an offline game session.
 * This object is saved locally on the client and sent to the server for validation and leaderboard update
 * once the client is online again.
 */
public class OfflineResult implements Serializable {
    private static final long serialVersionUID = 1L;

    // --- Final Results Reported by Client ---
    private final String username;
    private final int level;
    private final long finalTimeMillis;
    private final int finalXp; // Example metric, can be expanded
    private final double finalLossPercentage;

    // --- Data for Server-Side Validation (DIV) ---
    private final long initialRandomSeed;
    private final List<UserCommand> userActionReplay; // A record of all pre-simulation actions

    // --- Security ---
    private final byte[] checksum;

    public OfflineResult(String username, int level, long finalTimeMillis, int finalXp, double finalLossPercentage,
                         long initialRandomSeed, List<UserCommand> userActionReplay, byte[] checksum) {
        this.username = username;
        this.level = level;
        this.finalTimeMillis = finalTimeMillis;
        this.finalXp = finalXp;
        this.finalLossPercentage = finalLossPercentage;
        this.initialRandomSeed = initialRandomSeed;
        this.userActionReplay = userActionReplay;
        this.checksum = checksum;
    }

    // --- Getters ---

    public String getUsername() {
        return username;
    }

    public int getLevel() {
        return level;
    }

    public long getFinalTimeMillis() {
        return finalTimeMillis;
    }

    public int getFinalXp() {
        return finalXp;
    }

    public double getFinalLossPercentage() {
        return finalLossPercentage;
    }

    public long getInitialRandomSeed() {
        return initialRandomSeed;
    }

    public List<UserCommand> getUserActionReplay() {
        return userActionReplay;
    }

    public byte[] getChecksum() {
        return checksum;
    }
}