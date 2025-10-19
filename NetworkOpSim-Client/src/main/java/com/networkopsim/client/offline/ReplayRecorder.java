// FILE: /NetworkOpSim-Multiplayer/NetworkOpSim-Client/src/main/java/com/networkopsim/client/offline/ReplayRecorder.java
// ================================================================================

package com.networkopsim.client.offline;

import com.networkopsim.shared.net.UserCommand;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Records user actions during an offline game session to create a replay.
 * This replay data is sent to the server for Data Integrity Validation (DIV).
 */
public class ReplayRecorder implements Serializable {
    private static final long serialVersionUID = 1L;

    private long initialRandomSeed;
    private final long startTime;
    private final List<TimedUserCommand> commandHistory;

    /**
     * Inner class to store a command along with its execution timestamp relative to the game start.
     */
    private static class TimedUserCommand implements Serializable {
        private static final long serialVersionUID = 1L;
        final long timestamp; // Milliseconds since game start
        final UserCommand command;

        TimedUserCommand(long timestamp, UserCommand command) {
            this.timestamp = timestamp;
            this.command = command;
        }
    }

    public ReplayRecorder() {
        this.startTime = System.currentTimeMillis();
        this.commandHistory = Collections.synchronizedList(new ArrayList<>());
    }

    /**
     * Sets the initial random seed used for the offline game simulation.
     * This is crucial for the server to replicate the game conditions exactly.
     * @param seed The initial seed.
     */
    public void setInitialRandomSeed(long seed) {
        this.initialRandomSeed = seed;
    }

    /**
     * Records a user action with a relative timestamp.
     * @param command The UserCommand performed by the user.
     */
    public void recordAction(UserCommand command) {
        long timestamp = System.currentTimeMillis() - startTime;
        commandHistory.add(new TimedUserCommand(timestamp, command));
    }

    /**
     * Returns the initial random seed for this replay.
     * @return The seed.
     */
    public long getInitialRandomSeed() {
        return initialRandomSeed;
    }

    /**
     * Returns an unmodifiable list of the recorded commands.
     * This is what will be sent to the server.
     * @return The list of user commands.
     */
    public List<UserCommand> getCommandReplay() {
        // For simplicity, we can just send the commands.
        // Timestamps could be used for more complex replays.
        List<UserCommand> commands = new ArrayList<>();
        commandHistory.forEach(timedCommand -> commands.add(timedCommand.command));
        return Collections.unmodifiableList(commands);
    }

    /**
     * Clears all recorded actions and resets the timer.
     */
    public void reset() {
        commandHistory.clear();
        // The start time is final, so a new recorder should be created for a new game.
    }
}