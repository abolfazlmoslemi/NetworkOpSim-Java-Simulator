// FILE: /NetworkOpSim-Multiplayer/NetworkOpSim-Server/src/main/java/com/networkopsim/server/persistence/PlayerManager.java
// ================================================================================

package com.networkopsim.server.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.sql.*;

/**
 * Manages all CRUD (Create, Read, Update, Delete) operations for player profiles.
 */
public class PlayerManager {
    private static final Logger log = LoggerFactory.getLogger(PlayerManager.class);

    /**
     * Retrieves a player's profile from the database. If the player does not exist, creates a new one.
     * @param username The username to look for.
     * @return The PlayerProfile object, or null on failure.
     */
    public synchronized PlayerProfile getOrCreatePlayer(String username) {
        PlayerProfile profile = getPlayerByUsername(username);
        if (profile == null) {
            return createPlayer(username);
        }
        return profile;
    }

    private PlayerProfile getPlayerByUsername(String username) {
        String sql = "SELECT p.player_id, p.username, pr.xp, pr.coins, pr.unlocked_levels " +
                "FROM players p " +
                "JOIN player_progress pr ON p.player_id = pr.player_id " +
                "WHERE p.username = ?";

        try (Connection conn = DatabaseManager.connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            if (conn == null) return null;
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return new PlayerProfile(
                        rs.getInt("player_id"),
                        rs.getString("username"),
                        rs.getInt("xp"),
                        rs.getInt("coins"),
                        rs.getString("unlocked_levels")
                );
            }
        } catch (SQLException e) {
            log.error("Error fetching player profile for {}", username, e);
        }
        return null;
    }

    private PlayerProfile createPlayer(String username) {
        String insertPlayerSql = "INSERT INTO players(username) VALUES(?)";
        String insertProgressSql = "INSERT INTO player_progress(player_id) VALUES(?)";

        Connection conn = null;
        try {
            conn = DatabaseManager.connect();
            if (conn == null) return null;
            conn.setAutoCommit(false); // Start transaction

            int playerId;
            try (PreparedStatement playerPstmt = conn.prepareStatement(insertPlayerSql, Statement.RETURN_GENERATED_KEYS)) {
                playerPstmt.setString(1, username);
                playerPstmt.executeUpdate();
                ResultSet generatedKeys = playerPstmt.getGeneratedKeys();
                if (generatedKeys.next()) {
                    playerId = generatedKeys.getInt(1);
                } else {
                    throw new SQLException("Creating player failed, no ID obtained.");
                }
            }

            try (PreparedStatement progressPstmt = conn.prepareStatement(insertProgressSql)) {
                progressPstmt.setInt(1, playerId);
                progressPstmt.executeUpdate();
            }

            conn.commit(); // Commit transaction
            log.info("Created new player profile for username: {}", username);
            return getPlayerByUsername(username); // Fetch the newly created profile with default values

        } catch (SQLException e) {
            log.error("Error creating new player profile for {}", username, e);
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    log.error("Error rolling back transaction", ex);
                }
            }
            return null;
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException e) {
                    log.error("Error closing connection", e);
                }
            }
        }
    }

    /**
     * Updates the player's progress in the database.
     * @param profile The PlayerProfile object with updated data.
     */
    public synchronized void updatePlayerProgress(PlayerProfile profile) {
        String sql = "UPDATE player_progress SET xp = ?, coins = ?, unlocked_levels = ? WHERE player_id = ?";
        try (Connection conn = DatabaseManager.connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            if (conn == null) return;

            pstmt.setInt(1, profile.getXp());
            pstmt.setInt(2, profile.getCoins());
            pstmt.setString(3, profile.getUnlockedLevels());
            pstmt.setInt(4, profile.getPlayerId());

            pstmt.executeUpdate();
            log.debug("Updated progress for player {}", profile.getUsername());

        } catch (SQLException e) {
            log.error("Error updating progress for player {}", profile.getUsername(), e);
        }
    }
}