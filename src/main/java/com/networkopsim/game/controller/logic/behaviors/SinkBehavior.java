package com.networkopsim.game.controller.logic.behaviors;

import com.networkopsim.game.controller.logic.GameEngine;
import com.networkopsim.game.model.core.Packet;
import com.networkopsim.game.model.core.System;
import com.networkopsim.game.model.enums.NetworkEnums;
import com.networkopsim.game.controller.logic.SystemBehavior;
/**
 * Behavior for SINK systems. Handles the final delivery or loss of packets.
 */
public class SinkBehavior extends AbstractSystemBehavior {

    @Override
    public void receivePacket(System system, Packet packet, GameEngine gameEngine, boolean isPredictionRun, boolean enteredCompatibly) {
        if (packet.getPacketType() == NetworkEnums.PacketType.BULK || packet.getPacketType() == NetworkEnums.PacketType.BIT) {
            // BULK and BIT packets should be handled by MERGERs, not SINKs.
            gameEngine.packetLostInternal(packet, isPredictionRun);
        } else {
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