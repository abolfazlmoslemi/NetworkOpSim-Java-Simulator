// FILE: /NetworkOpSim-Multiplayer/NetworkOpSim-Server/src/main/java/com/networkopsim/server/core/IClientCommandHandler.java
// ================================================================================

package com.networkopsim.server.core;

import com.networkopsim.shared.net.UserCommand;

/**
 * An interface defining the contract for processing user commands.
 * This is used to create a dynamic proxy for global exception handling around the command processing logic.
 */
public interface IClientCommandHandler {
    /**
     * Processes a command received from a client.
     * This method will be intercepted by the exception handling proxy.
     *
     * @param command The UserCommand to process.
     * @throws Exception if any error occurs during command processing.
     */
    void processCommand(UserCommand command) throws Exception;
}