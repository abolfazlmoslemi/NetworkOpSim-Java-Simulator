// ===== File: SystemBehavior.java (Corrected Path & Package) =====
// PATH: src/main/java/com/networkopsim/game/controller/logic/SystemBehavior.java

package com.networkopsim.game.controller.logic; // <-- CORRECTED PACKAGE

import com.networkopsim.game.model.core.Packet;
import com.networkopsim.game.model.core.System;
import com.networkopsim.game.model.enums.NetworkEnums;

/**
 * Interface for the Strategy design pattern, defining the unique behaviors of different system types.
 * This helps in adhering to the Open/Closed Principle by allowing new system behaviors
 * to be added without modifying the core System class.
 */
public interface SystemBehavior {

    /**
     * Defines how a system type handles a received packet.
     * This method contains the core logic that was previously in the `switch` statement
     * of the System class's receivePacket method.
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
     * For most simple systems, this will be a standard behavior, but complex systems
     * like MERGER might have custom logic.
     *
     * @param system The system instance executing the behavior.
     * @param gameEngine The main game engine.
     * @param isPredictionRun True if this is a prediction run.
     */
    void processQueue(System system, GameEngine gameEngine, boolean isPredictionRun);

    /**
     * Defines the packet generation logic, only applicable to SOURCE systems.
     * For all other system types, this method will be empty.
     *
     * @param system The system instance executing the behavior.
     * @param gameEngine The main game engine.
     * @param currentSimTimeMs The current simulation time.
     * @param isPredictionRun True if this is a prediction run.
     */
    void attemptPacketGeneration(System system, GameEngine gameEngine, long currentSimTimeMs, boolean isPredictionRun);

    /**
     * Gets the specific type of the system, which is useful for rendering and identification.
     * @return The SystemType enum constant.
     */
    NetworkEnums.SystemType getSystemType();

}