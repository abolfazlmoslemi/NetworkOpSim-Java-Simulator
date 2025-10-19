// FILE: /NetworkOpSim-Multiplayer/NetworkOpSim-Server/src/main/java/com/networkopsim/server/game/model/state/PredictedPacketStatus.java
// ================================================================================

package com.networkopsim.core.game.model.state;

/**
 * Represents the predicted status of a packet during a headless simulation run.
 */
public enum PredictedPacketStatus {
    /** Packet has not been generated yet at the viewed time. */
    NOT_YET_GENERATED,
    /** Packet is predicted to be travelling on a wire at the viewed time. */
    ON_WIRE,
    /** Packet is predicted to have reached a compatible sink by the viewed time. */
    DELIVERED,
    /** Packet is predicted to be lost (e.g., hit incompatible sink, dead end) by the viewed time. */
    LOST,
    /** Packet is predicted to have reached a node, but no valid onward path was found. */
    STALLED_AT_NODE,
    /** Packet is predicted to be inside a system's queue. */
    QUEUED
}