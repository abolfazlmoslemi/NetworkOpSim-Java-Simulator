// FILE: /NetworkOpSim-Multiplayer/NetworkOpSim-Server/src/main/java/com/networkopsim/server/game/logic/SystemBehavior.java
// ================================================================================

package com.networkopsim.core.game.logic;

import com.networkopsim.core.game.model.core.Packet;
import com.networkopsim.core.game.model.core.System;
import com.networkopsim.shared.model.NetworkEnums;

/**
 * Interface for the Strategy design pattern, defining the unique behaviors of different system types.
 * This runs on the server to execute game logic.
 */
public interface SystemBehavior {

    /**
     * Defines how a system type handles a received packet.
     *
     * @param system The system instance executing the behavior.
     * @param packet The packet that was received.
     * @param gameEngine The main game engine, providing access to game state and actions.
     * @param isPredictionRun True if this is part of a non-interactive prediction simulation.
     * @param enteredCompatibly True if the packet entered through a port of a compatible shape.
     */
    void receivePacket(System system, Packet packet, GameEngine gameEngine, boolean isPredictionRun, boolean enteredCompatibly);

    /**
     * Defines how a system processes its internal packet queue each tick.
     *
     * @param system The system instance executing the behavior.
     * @param gameEngine The main game engine.
     * @param isPredictionRun True if this is a prediction run.
     */
    void processQueue(System system, GameEngine gameEngine, boolean isPredictionRun);

    /**
     * Defines the packet generation logic, only applicable to SOURCE systems.
     *
     * @param system The system instance executing the behavior.
     * @param gameEngine The main game engine.
     * @param currentSimTimeMs The current simulation time.
     * @param isPredictionRun True if this is a prediction run.
     */
    void attemptPacketGeneration(System system, GameEngine gameEngine, long currentSimTimeMs, boolean isPredictionRun);

    /**
     * Gets the specific type of the system.
     * @return The SystemType enum constant.
     */
    NetworkEnums.SystemType getSystemType();
}