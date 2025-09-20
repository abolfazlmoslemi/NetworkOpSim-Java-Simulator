// ===== File: NetworkEnums.java (FINAL - BIT type removed) =====
package com.networkopsim.game.model.enums;

public class NetworkEnums {
    private NetworkEnums() {}

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
        // BIT type has been removed. Special MESSENGERs are used instead.
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