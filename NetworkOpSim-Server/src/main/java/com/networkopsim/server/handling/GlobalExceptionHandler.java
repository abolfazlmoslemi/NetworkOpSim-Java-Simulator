// FILE: /NetworkOpSim-Multiplayer/NetworkOpSim-Server/src/main/java/com/networkopsim/server/handling/GlobalExceptionHandler.java
// ================================================================================

package com.networkopsim.server.handling;

import com.networkopsim.shared.net.ServerResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A central component for handling exceptions across the server.
 * Methods in this class are annotated with @ExceptionHandler to specify which exceptions they handle.
 * This class must be annotated with @ControllerAdvice to be discovered by the ExceptionDispatcher.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handles network-related I/O exceptions.
     * These typically occur during communication with the client.
     * @param ex The captured IOException.
     * @return A ServerResponse indicating a network error.
     */
    @ExceptionHandler({java.io.IOException.class})
    public ServerResponse handleIOException(java.io.IOException ex) {
        log.error("Network I/O Error occurred: {}", ex.getMessage());
        // We log the error but return a generic message to the client for security.
        return ServerResponse.error("A network communication error occurred. Please try again.");
    }

    /**
     * Handles exceptions related to invalid arguments in commands.
     * @param ex The captured IllegalArgumentException.
     * @return A ServerResponse indicating a bad request from the client.
     */
    @ExceptionHandler({IllegalArgumentException.class})
    public ServerResponse handleIllegalArgumentException(IllegalArgumentException ex) {
        log.warn("Invalid argument provided in a client command: {}", ex.getMessage());
        return ServerResponse.error("Invalid request: " + ex.getMessage());
    }

    /**
     * Handles exceptions related to invalid object casting.
     * @param ex The captured ClassCastException.
     * @return A ServerResponse indicating a bad request from the client.
     */
    @ExceptionHandler({ClassCastException.class})
    public ServerResponse handleClassCastException(ClassCastException ex) {
        log.warn("Invalid data type received from client: {}", ex.getMessage());
        return ServerResponse.error("Invalid data format in request.");
    }

    /**
     * A generic, catch-all handler for any other unexpected exceptions.
     * This acts as a fallback to prevent unhandled exceptions from crashing the session.
     * @param ex The captured Exception.
     * @return A ServerResponse indicating a generic internal server error.
     */
    @ExceptionHandler({Exception.class})
    public ServerResponse handleGenericException(Exception ex) {
        // For unexpected errors, we log the full stack trace for debugging.
        log.error("An unexpected server error occurred.", ex);
        return ServerResponse.error("An unexpected internal server error occurred.");
    }
}