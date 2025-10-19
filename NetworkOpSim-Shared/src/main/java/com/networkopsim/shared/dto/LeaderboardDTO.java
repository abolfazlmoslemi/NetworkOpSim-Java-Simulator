// FILE: /NetworkOpSim-Multiplayer/NetworkOpSim-Shared/src/main/java/com/networkopsim/shared/dto/LeaderboardDTO.java
// ================================================================================

package com.networkopsim.shared.dto;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * A Data Transfer Object for sending leaderboard information from the server to the client.
 * It contains the player's personal bests and the global best record.
 */
public class LeaderboardDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * A record to hold score details for a specific level or overall.
     */
    public static class ScoreRecord implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String username;
        private final long timeMillis; // Best time in milliseconds
        private final int xp;          // Best XP

        public ScoreRecord(String username, long timeMillis, int xp) {
            this.username = username;
            this.timeMillis = timeMillis;
            this.xp = xp;
        }

        public String getUsername() {
            return username;
        }

        public long getTimeMillis() {
            return timeMillis;
        }

        public int getXp() {
            return xp;
        }
    }

    // Player's personal best scores, mapping level number to their record.
    private final Map<Integer, ScoreRecord> personalBestsByLevel;

    // The single best XP score achieved by any player across all levels.
    private final ScoreRecord globalBestXp;

    public LeaderboardDTO(Map<Integer, ScoreRecord> personalBestsByLevel, ScoreRecord globalBestXp) {
        this.personalBestsByLevel = personalBestsByLevel;
        this.globalBestXp = globalBestXp;
    }

    public Map<Integer, ScoreRecord> getPersonalBestsByLevel() {
        return personalBestsByLevel;
    }

    public ScoreRecord getGlobalBestXp() {
        return globalBestXp;
    }
}