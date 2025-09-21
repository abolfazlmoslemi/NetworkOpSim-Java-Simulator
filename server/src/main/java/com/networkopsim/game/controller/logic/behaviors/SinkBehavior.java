// ===== File: SinkBehavior.java (FINAL REVISED for server) =====
// ===== MODULE: server =====

package com.networkopsim.game.controller.logic.behaviors;

import com.networkopsim.game.controller.logic.GameEngine;
import com.networkopsim.game.model.core.Packet;
import com.networkopsim.game.model.enums.NetworkEnums;

public class SinkBehavior extends AbstractSystemBehavior {

    @Override
    public void receivePacket(com.networkopsim.game.model.core.System system, Packet packet, GameEngine gameEngine, boolean isPredictionRun, boolean enteredCompatibly) {

        if (packet.isVolumetric()) {
            gameEngine.packetSuccessfullyDeliveredInternal(packet, isPredictionRun);
            // Sound effect removed
            return;
        }

        if (packet.getPacketType() == NetworkEnums.PacketType.MESSENGER && packet.getBulkParentId() != -1) {
            gameEngine.packetLostInternal(packet, isPredictionRun);
        } else {
            gameEngine.packetSuccessfullyDeliveredInternal(packet, isPredictionRun);
            // Sound effect removed
        }
    }

    @Override
    public NetworkEnums.SystemType getSystemType() {
        return NetworkEnums.SystemType.SINK;
    }
}