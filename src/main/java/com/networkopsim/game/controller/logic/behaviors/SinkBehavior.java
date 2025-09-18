// ===== File: SinkBehavior.java (FINAL - Correctly Handles Merged BULK Packets) =====

package com.networkopsim.game.controller.logic.behaviors;

import com.networkopsim.game.controller.logic.GameEngine;
import com.networkopsim.game.model.core.Packet;
import com.networkopsim.game.model.core.System;
import com.networkopsim.game.model.enums.NetworkEnums;

public class SinkBehavior extends AbstractSystemBehavior {

    @Override
    public void receivePacket(System system, Packet packet, GameEngine gameEngine, boolean isPredictionRun, boolean enteredCompatibly) {

        // [SCENARIO 1] A volumetric (BULK or WOBBLE) packet arrives.
        // [MODIFIED] This now handles any volumetric packet.
        if (packet.isVolumetric()) {
            // A volumetric packet arriving at a Sink is ALWAYS considered a success,
            // because it could only have been created by a Merger. The actual
            // packet loss calculation for its original parts has already been
            // handled by the Merger reporting to the GameState.
            // A raw volumetric packet from a source cannot reach here without being processed.
            gameEngine.packetSuccessfullyDeliveredInternal(packet, isPredictionRun);
            if (!isPredictionRun && !gameEngine.getGame().isMuted()) {
                gameEngine.getGame().playSoundEffect("delivery_success");
            }
            return;
        }

        // [SCENARIO 2] A MESSENGER part of a BULK packet arrives.
        // This is always an error, as these parts should only go to a Merger.
        if (packet.getPacketType() == NetworkEnums.PacketType.MESSENGER && packet.getBulkParentId() != -1) {
            // The final loss is calculated by the Merger's timeout logic.
            // We just need to remove the packet from the simulation here.
            gameEngine.packetLostInternal(packet, isPredictionRun);
        } else {
            // [SCENARIO 3] All other standard packets.
            // These are considered successfully delivered.
            gameEngine.packetSuccessfullyDeliveredInternal(packet, isPredictionRun);
            if (!isPredictionRun && !gameEngine.getGame().isMuted()) {
                gameEngine.getGame().playSoundEffect("delivery_success");
            }
        }
    }

    @Override
    public NetworkEnums.SystemType getSystemType() {
        return NetworkEnums.SystemType.SINK;
    }
}