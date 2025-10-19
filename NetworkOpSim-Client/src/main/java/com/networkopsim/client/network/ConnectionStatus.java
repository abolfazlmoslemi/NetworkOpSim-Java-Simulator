
package com.networkopsim.client.network;

import java.awt.Color;

/**
 * Represents the possible states of the network connection to the server.
 */
public enum ConnectionStatus {
    /** Actively connected to the server and exchanging data. */
    CONNECTED("Connected", new Color(30, 180, 30)),

    /** Not connected to the server. */
    DISCONNECTED("Offline", new Color(200, 30, 30)),

    /** An attempt to connect to the server is in progress. */
    CONNECTING("Connecting...", new Color(220, 150, 20));

    private final String displayText;
    private final Color displayColor;

    ConnectionStatus(String displayText, Color displayColor) {
        this.displayText = displayText;
        this.displayColor = displayColor;
    }

    public String getDisplayText() {
        return displayText;
    }

    public Color getDisplayColor() {
        return displayColor;
    }
}