// FILE: /NetworkOpSim-Multiplayer/NetworkOpSim-Shared/src/main/java/com/networkopsim/shared/net/ServerResponse.java
// ================================================================================

package com.networkopsim.shared.net;

import java.io.Serializable;

/**
 * Represents a response sent from the server to the client.
 * This can be used to acknowledge commands, report errors, or provide general feedback.
 */
public class ServerResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum ResponseStatus {
        SUCCESS,
        ERROR,
        INFO
    }

    private final ResponseStatus status;
    private final String message;
    private final Object data; // Optional data payload

    public ServerResponse(ResponseStatus status, String message, Object data) {
        this.status = status;
        this.message = message;
        this.data = data;
    }

    public ServerResponse(ResponseStatus status, String message) {
        this(status, message, null);
    }

    public ResponseStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public Object getData() {
        return data;
    }

    // --- Static factory methods for convenience ---

    public static ServerResponse success(String message) {
        return new ServerResponse(ResponseStatus.SUCCESS, message);
    }

    public static ServerResponse success(String message, Object data) {
        return new ServerResponse(ResponseStatus.SUCCESS, message, data);
    }

    public static ServerResponse error(String message) {
        return new ServerResponse(ResponseStatus.ERROR, message);
    }

    public static ServerResponse info(String message) {
        return new ServerResponse(ResponseStatus.INFO, message);
    }
}