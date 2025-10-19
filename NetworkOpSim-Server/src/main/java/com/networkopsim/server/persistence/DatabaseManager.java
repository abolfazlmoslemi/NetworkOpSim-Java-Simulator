// FILE: /NetworkOpSim-Multiplayer/NetworkOpSim-Server/src/main/java/com/networkopsim/server/persistence/DatabaseManager.java
// ================================================================================

package com.networkopsim.server.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Manages the connection to the SQLite database.
 * Handles database creation and table setup.
 */
public class DatabaseManager {
    private static final Logger log = LoggerFactory.getLogger(DatabaseManager.class);
    private static final String DB_URL = "jdbc:sqlite:leaderboard.db";

    /**
     * Establishes a connection to the SQLite database.
     * If the database file does not exist, it will be created.
     * @return A Connection object to the database.
     */
    public static Connection connect() {
        Connection conn = null;
        try {
            conn = DriverManager.getConnection(DB_URL);
            // We reduce repetitive logging, connection success is assumed on operation success.
        } catch (SQLException e) {
            log.error("Failed to connect to SQLite database.", e);
        }
        return conn;
    }

    /**
     * Initializes the database by creating all necessary tables if they don't exist.
     */
    public static void initializeDatabase() {
        createScoresTable();
        createPlayerTables();
    }

    private static void createScoresTable() {
        String sql = "CREATE TABLE IF NOT EXISTS scores ("
                + " id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + " username TEXT NOT NULL,"
                + " level INTEGER NOT NULL,"
                + " time_millis INTEGER NOT NULL,"
                + " xp INTEGER NOT NULL,"
                + " UNIQUE(username, level)"
                + ");";

        try (Connection conn = connect();
             Statement stmt = conn.createStatement()) {
            if (conn == null) return;
            stmt.execute(sql);
            log.info("Scores table initialized successfully.");
        } catch (SQLException e) {
            log.error("Error initializing scores table.", e);
        }
    }

    private static void createPlayerTables() {
        String playerSql = "CREATE TABLE IF NOT EXISTS players ("
                + " player_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + " username TEXT NOT NULL UNIQUE,"
                + " created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
                + ");";

        String progressSql = "CREATE TABLE IF NOT EXISTS player_progress ("
                + " progress_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + " player_id INTEGER NOT NULL UNIQUE," // Each player has only one progress record
                + " xp INTEGER DEFAULT 0,"
                + " coins INTEGER DEFAULT 30,"
                + " unlocked_levels TEXT DEFAULT '1',"
                + " FOREIGN KEY (player_id) REFERENCES players(player_id)"
                + ");";

        try (Connection conn = connect();
             Statement stmt = conn.createStatement()) {
            if (conn == null) return;
            stmt.execute(playerSql);
            stmt.execute(progressSql);
            log.info("Player tables initialized successfully.");
        } catch (SQLException e) {
            log.error("Error initializing player tables.", e);
        }
    }
}