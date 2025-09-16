// ===== File: MergerBehavior.java (FINAL - Corrected with getSystemType) =====

package com.networkopsim.game.controller.logic.behaviors;

import com.networkopsim.game.controller.logic.GameEngine;
import com.networkopsim.game.model.core.Packet;
import com.networkopsim.game.model.core.System;
import com.networkopsim.game.model.enums.NetworkEnums;
import java.util.ArrayList;
import java.util.List;

public class MergerBehavior extends AbstractSystemBehavior {

    @Override
    public void receivePacket(System system, Packet packet, GameEngine gameEngine, boolean isPredictionRun, boolean enteredCompatibly) {
        // Check if it's a MESSENGER that is part of a BULK group.
        if (packet.getPacketType() == NetworkEnums.PacketType.MESSENGER && packet.getBulkParentId() != -1) {
            int parentId = packet.getBulkParentId();

            system.getMergingPackets().computeIfAbsent(parentId, k -> new ArrayList<>()).add(packet);

            // The MESSENGER part is consumed.
            gameEngine.packetLostInternal(packet, isPredictionRun);

            List<Packet> collectedParts = system.getMergingPackets().get(parentId);
            if (collectedParts.size() >= packet.getTotalBitsInGroup()) {
                // All parts collected, create the new BULK packet.
                Packet newBulkPacket = new Packet(NetworkEnums.PacketShape.CIRCLE, system.getX(), system.getY(), NetworkEnums.PacketType.BULK);

                // Queue the new BULK packet for processing.
                queuePacket(system, newBulkPacket, gameEngine, isPredictionRun);

                system.getMergingPackets().remove(parentId);
            }
        } else {
            // If it's any other packet type, route it normally.
            processOrQueuePacket(system, packet, gameEngine, isPredictionRun);
        }
    }

    // [FIXED] Added the missing getSystemType method.
    @Override
    public NetworkEnums.SystemType getSystemType() {
        return NetworkEnums.SystemType.MERGER;
    }
}