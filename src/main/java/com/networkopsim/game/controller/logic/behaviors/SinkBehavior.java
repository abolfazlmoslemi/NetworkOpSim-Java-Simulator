// ===== File: SinkBehavior.java (FINAL - Corrected for special MESSENGERs) =====

package com.networkopsim.game.controller.logic.behaviors;

import com.networkopsim.game.controller.logic.GameEngine;
import com.networkopsim.game.model.core.Packet;
import com.networkopsim.game.model.core.System;
import com.networkopsim.game.model.enums.NetworkEnums;

public class SinkBehavior extends AbstractSystemBehavior {

    @Override
    public void receivePacket(System system, Packet packet, GameEngine gameEngine, boolean isPredictionRun, boolean enteredCompatibly) {
        // [MODIFIED] BULK packets and MESSENGERs that are part of a BULK group (identified by bulkParentId)
        // should be handled by MERGERs, not SINKs. Receiving them here means they are lost.
        if (packet.getPacketType() == NetworkEnums.PacketType.BULK ||
                (packet.getPacketType() == NetworkEnums.PacketType.MESSENGER && packet.getBulkParentId() != -1)) {

            gameEngine.packetLostInternal(packet, isPredictionRun);
        } else {
            // All other packets are considered successfully delivered.
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