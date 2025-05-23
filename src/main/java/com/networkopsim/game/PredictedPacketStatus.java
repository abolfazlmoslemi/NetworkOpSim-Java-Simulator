package com.networkopsim.game;
/**
 * Represents the predicted status of a packet during the time scrubbing phase.
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
    /** Packet is predicted to have reached a node, but no valid onward path (or connected wire) was found in the prediction. */
    STALLED_AT_NODE,
    /** (Optional future state) Packet is predicted to be inside a system's queue. Simple prediction might not show this state. */
    QUEUED
}