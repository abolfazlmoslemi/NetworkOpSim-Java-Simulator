// FILE: /NetworkOpSim-Multiplayer/NetworkOpSim-Server/src/main/java/com/networkopsim/server/persistence/LeaderboardManager.java
// ================================================================================

package com.networkopsim.server.persistence;

import com.networkopsim.shared.dto.LeaderboardDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement; // <<<--- IMPORT ADDED
import java.util.HashMap;
import java.util.Map;

/**
 * Manages leaderboard data, handling database interactions for scores.
 */
public class LeaderboardManager {
    private static final Logger log = LoggerFactory.getLogger(LeaderboardManager.class);

    public LeaderboardManager() {
        DatabaseManager.initializeDatabase();
    }

    /**
     * Updates the score for a player on a specific level if the new score is better.
     * A better score is defined as a lower time. If times are equal, higher XP is better.
     * @param username The player's username.
     * @param level The level number.
     * @param timeMillis The time taken in milliseconds.
     * @param xp The experience points gained.
     */
    public synchronized void updateScore(String username, int level, long timeMillis, int xp) {
        String selectSql = "SELECT time_millis, xp FROM scores WHERE username = ? AND level = ?";
        String insertSql = "INSERT INTO scores(username, level, time_millis, xp) VALUES(?,?,?,?)";
        String updateSql = "UPDATE scores SET time_millis = ?, xp = ? WHERE username = ? AND level = ?";

        try (Connection conn = DatabaseManager.connect()) {
            try (PreparedStatement selectPstmt = conn.prepareStatement(selectSql)) {
                selectPstmt.setString(1, username);
                selectPstmt.setInt(2, level);
                ResultSet rs = selectPstmt.executeQuery();

                if (rs.next()) { // Record exists, check if new one is better
                    long existingTime = rs.getLong("time_millis");
                    int existingXp = rs.getInt("xp");
                    if (timeMillis < existingTime || (timeMillis == existingTime && xp > existingXp)) {
                        try (PreparedStatement updatePstmt = conn.prepareStatement(updateSql)) {
                            updatePstmt.setLong(1, timeMillis);
                            updatePstmt.setInt(2, xp);
                            updatePstmt.setString(3, username);
                            updatePstmt.setInt(4, level);
                            updatePstmt.executeUpdate();
                            log.info("Updated score for {} on level {}: time={}, xp={}", username, level, timeMillis, xp);
                        }
                    }
                } else { // No record exists, insert a new one
                    try (PreparedStatement insertPstmt = conn.prepareStatement(insertSql)) {
                        insertPstmt.setString(1, username);
                        insertPstmt.setInt(2, level);
                        insertPstmt.setLong(3, timeMillis);
                        insertPstmt.setInt(4, xp);
                        insertPstmt.executeUpdate();
                        log.info("Inserted new score for {} on level {}: time={}, xp={}", username, level, timeMillis, xp);
                    }
                }
            }
        } catch (SQLException e) {
            log.error("Error updating score for user {}", username, e);
        }
    }

    /**
     * Retrieves the leaderboard data for a specific player.
     * @param username The player's username.
     * @return A LeaderboardDTO containing personal bests and the global best XP.
     */
    public synchronized LeaderboardDTO getLeaderboardData(String username) {
        Map<Integer, LeaderboardDTO.ScoreRecord> personalBests = getPersonalBests(username);
        LeaderboardDTO.ScoreRecord globalBestXp = getGlobalBestXp();
        return new LeaderboardDTO(personalBests, globalBestXp);
    }

    private Map<Integer, LeaderboardDTO.ScoreRecord> getPersonalBests(String username) {
        String sql = "SELECT level, time_millis, xp FROM scores WHERE username = ?";
        Map<Integer, LeaderboardDTO.ScoreRecord> bests = new HashMap<>();
        try (Connection conn = DatabaseManager.connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                int level = rs.getInt("level");
                long time = rs.getLong("time_millis");
                int xp = rs.getInt("xp");
                bests.put(level, new LeaderboardDTO.ScoreRecord(username, time, xp));
            }
        } catch (SQLException e) {
            log.error("Error fetching personal bests for {}", username, e);
        }
        return bests;
    }

    private LeaderboardDTO.ScoreRecord getGlobalBestXp() {
        String sql = "SELECT username, time_millis, xp FROM scores ORDER BY xp DESC, time_millis ASC LIMIT 1";
        try (Connection conn = DatabaseManager.connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                String username = rs.getString("username");
                long time = rs.getLong("time_millis");
                int xp = rs.getInt("xp");
                return new LeaderboardDTO.ScoreRecord(username, time, xp);
            }
        } catch (SQLException e) {
            log.error("Error fetching global best XP", e);
        }
        return null; // No records found
    }
}