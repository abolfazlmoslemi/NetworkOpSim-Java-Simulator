// FILE: /NetworkOpSim-Multiplayer/NetworkOpSim-Shared/src/main/java/com/networkopsim/shared/net/UserCommand.java
// ================================================================================

package com.networkopsim.shared.net;

import com.networkopsim.shared.model.NetworkEnums;
import java.awt.Point;
import java.io.Serializable;

/**
 * Represents a command sent from the client to the server, initiated by a user action.
 * This class is serializable to be sent over the network.
 */
public class UserCommand implements Serializable {
    private static final long serialVersionUID = 5L; // Version bumped

    /**
     * Defines the type of action the user has performed.
     */
    public enum CommandType {
        // Pre-simulation wiring commands
        CREATE_WIRE,
        DELETE_WIRE,
        ADD_RELAY_POINT,
        MOVE_RELAY_POINT,
        DELETE_RELAY_POINT,
        DRAG_RELAY_POINT_START,
        DRAG_RELAY_POINT_UPDATE,
        DRAG_RELAY_POINT_END,

        // Simulation control commands
        START_SIMULATION,
        PAUSE_SIMULATION,
        RESUME_SIMULATION,
        STOP_SIMULATION,

        // Store and power-up commands
        PURCHASE_ITEM,
        PLAYER_IN_STORE,

        // User login/identification
        SET_USERNAME,
        GET_LEADERBOARD, // <-- NEW

        // Offline sync
        SUBMIT_OFFLINE_RESULT,

        // Multiplayer Commands
        CONTROL_REFERENCE_SYSTEM,
        JOIN_MATCHMAKING_QUEUE,
        LEAVE_MATCHMAKING_QUEUE,
        PLAYER_READY,
    }

    private final CommandType type;
    private final Object[] args;

    public UserCommand(CommandType type, Object... args) {
        this.type = type;
        this.args = args;
    }

    public CommandType getType() {
        return type;
    }

    public Object[] getArgs() {
        return args;
    }

    // --- Static factory methods for convenience ---

    public static UserCommand setUsername(String username) {
        return new UserCommand(CommandType.SET_USERNAME, username);
    }

    public static UserCommand createWire(int startPortId, int endPortId) {
        return new UserCommand(CommandType.CREATE_WIRE, startPortId, endPortId);
    }

    public static UserCommand deleteWire(int wireId) {
        return new UserCommand(CommandType.DELETE_WIRE, wireId);
    }

    public static UserCommand addRelayPoint(int wireId, Point position) {
        return new UserCommand(CommandType.ADD_RELAY_POINT, wireId, position);
    }

    public static UserCommand moveRelayPoint(int wireId, int relayIndex, Point newPosition) {
        return new UserCommand(CommandType.MOVE_RELAY_POINT, wireId, relayIndex, newPosition);
    }

    public static UserCommand joinMatchmakingQueue(int level) {
        return new UserCommand(CommandType.JOIN_MATCHMAKING_QUEUE, level);
    }

    public static UserCommand playerInStore(boolean isInStore) {
        return new UserCommand(CommandType.PLAYER_IN_STORE, isInStore);
    }

    public static UserCommand getLeaderboard() {
        return new UserCommand(CommandType.GET_LEADERBOARD);
    }

    public static UserCommand purchaseItem(String itemName) {
        return new UserCommand(CommandType.PURCHASE_ITEM, itemName);
    }

    public static UserCommand submitOfflineResult(OfflineResult result) {
        return new UserCommand(CommandType.SUBMIT_OFFLINE_RESULT, result);
    }

    public static UserCommand controlReferenceSystem(int targetSystemId) { // Sabotage
        return new UserCommand(CommandType.CONTROL_REFERENCE_SYSTEM, targetSystemId);
    }

    public static UserCommand deployPacketFromReference(int targetSystemId, NetworkEnums.PacketType packetType) {
        return new UserCommand(CommandType.CONTROL_REFERENCE_SYSTEM, targetSystemId, packetType);
    }

    public static UserCommand simpleCommand(CommandType type) {
        if (type == CommandType.CREATE_WIRE || type == CommandType.DELETE_WIRE || type == CommandType.SET_USERNAME ||
                type == CommandType.SUBMIT_OFFLINE_RESULT || type == CommandType.CONTROL_REFERENCE_SYSTEM || type == CommandType.ADD_RELAY_POINT ||
                type == CommandType.MOVE_RELAY_POINT || type == CommandType.JOIN_MATCHMAKING_QUEUE || type == CommandType.PLAYER_IN_STORE ||
                type == CommandType.GET_LEADERBOARD) {
            throw new IllegalArgumentException("simpleCommand cannot be used for command type " + type);
        }
        return new UserCommand(type);
    }
}