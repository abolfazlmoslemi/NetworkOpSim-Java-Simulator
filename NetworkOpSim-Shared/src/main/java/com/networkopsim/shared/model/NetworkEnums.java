// FILE: /NetworkOpSim-Multiplayer/NetworkOpSim-Shared/src/main/java/com/networkopsim/shared/model/NetworkEnums.java
// ================================================================================

package com.networkopsim.shared.model;

/**
 * Contains all shared enumerations for the Network Operator Simulator game.
 * This file is part of the shared module to be used by both the Client and Server.
 */
public class NetworkEnums {
    private NetworkEnums() {} // Prevent instantiation

    public enum PortType {
        INPUT, OUTPUT
    }

    public enum PortShape {
        SQUARE, TRIANGLE, ANY, CIRCLE
    }

    public enum PacketShape {
        SQUARE, TRIANGLE, CIRCLE
    }

    public enum PacketType {
        NORMAL,
        SECRET,
        PROTECTED,
        TROJAN,
        MESSENGER,
        BULK,
        WOBBLE
    }

    public enum SystemType {
        NODE,
        SOURCE,
        SINK,
        SPY,
        CORRUPTOR,
        VPN,
        ANTITROJAN,
        DISTRIBUTOR,
        MERGER
    }
}