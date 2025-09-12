package com.networkopsim.game.model.enums;


import com.networkopsim.game.model.core.Packet;

// ===== File: NetworkEnums.java =====
public class NetworkEnums {
    private NetworkEnums() {}

    public enum PortType {
        INPUT, OUTPUT
    }

    public enum PortShape {
        SQUARE, TRIANGLE, ANY,
        // New shape for new packet types if needed in future
        CIRCLE
    }

    public enum PacketShape {
        SQUARE, TRIANGLE,
        // New shape for new packet types
        CIRCLE
    }

    // NEW ENUM for distinguishing packet functionalities
    public enum PacketType {
        NORMAL,
        SECRET,
        PROTECTED,
        TROJAN,
        MESSENGER,
        BULK,       // For Distribute/Merge systems
        WOBBLE,     // The new self-deflecting packet type
        BIT         // Represents a piece of a BULK packet, often implemented as a special MESSENGER
    }

    // NEW ENUM for different system functionalities
    public enum SystemType {
        NODE,       // Standard routing system
        SOURCE,     // Packet generator
        SINK,       // Packet destination
        SPY,        // Teleports normal packets, destroys secret ones
        CORRUPTOR,  // Adds noise, may create Trojan packets
        VPN,        // Creates Protected packets
        ANTITROJAN, // Scans for and converts Trojan packets
        DISTRIBUTOR,// Breaks down BULK packets
        MERGER      // Reassembles BIT packets into BULK packets
    }
}

