// FILE: /NetworkOpSim-Multiplayer/NetworkOpSim-Core/src/main/java/com/networkopsim/core/game/logic/behaviors/SinkBehavior.java
// ================================================================================

package com.networkopsim.core.game.logic.behaviors;

import com.networkopsim.core.game.logic.GameEngine;
import com.networkopsim.core.game.model.core.Packet;
import com.networkopsim.core.game.model.core.System;
import com.networkopsim.shared.model.NetworkEnums;

public class SinkBehavior extends AbstractSystemBehavior {

    @Override
    public void receivePacket(System system, Packet packet, GameEngine gameEngine, boolean isPredictionRun, boolean enteredCompatibly) {

        // In multiplayer, check if the packet's owner matches the sink's owner.
        // A sink with ownerId 0 is a neutral sink for offline/single-player modes.
        boolean ownerMatches = (system.getOwnerId() == 0 || packet.getOwnerId() == system.getOwnerId());

        if (!ownerMatches) {
            // Packet reached the opponent's sink. It's considered lost for the owner.
            gameEngine.packetLostInternal(packet, isPredictionRun);
            return;
        }

        // --- Original logic for matched owners or neutral sinks ---

        if (packet.isVolumetric()) {
            // A volumetric packet arriving at its correct Sink is always a success.
            gameEngine.packetSuccessfullyDeliveredInternal(packet, isPredictionRun);
            // --- NEW: Signal for feedback loop ---
            if (!isPredictionRun) {
                gameEngine.triggerSuccessfulDeliveryFeedback(packet);
            }
            return;
        }

        // MESSENGER parts of a bulk operation arriving at a sink is an error.
        if (packet.getPacketType() == NetworkEnums.PacketType.MESSENGER && packet.getBulkParentId() != -1) {
            gameEngine.packetLostInternal(packet, isPredictionRun);
        } else {
            // All other standard packets are considered successfully delivered.
            gameEngine.packetSuccessfullyDeliveredInternal(packet, isPredictionRun);
            // --- NEW: Signal for feedback loop ---
            if (!isPredictionRun) {
                gameEngine.triggerSuccessfulDeliveryFeedback(packet);
            }
        }
    }

    @Override
    public NetworkEnums.SystemType getSystemType() {
        return NetworkEnums.SystemType.SINK;
    }
}