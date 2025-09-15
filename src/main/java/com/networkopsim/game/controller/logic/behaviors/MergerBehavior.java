package com.networkopsim.game.controller.logic.behaviors;

import com.networkopsim.game.controller.logic.GameEngine;
import com.networkopsim.game.model.core.Packet;
import com.networkopsim.game.model.core.System;
import com.networkopsim.game.model.enums.NetworkEnums;
import com.networkopsim.game.controller.logic.SystemBehavior;

import java.util.ArrayList;
import java.util.List;

/**
 * Behavior for MERGER systems. Collects BIT packets and reassembles them into a single BULK packet
 * once all bits from a group have arrived.
 */
public class MergerBehavior extends AbstractSystemBehavior {

    @Override
    public void receivePacket(System system, Packet packet, GameEngine gameEngine, boolean isPredictionRun, boolean enteredCompatibly) {
        if (packet.getPacketType() != NetworkEnums.PacketType.BIT) {
            // If it's not a BIT packet, route it normally.
            processOrQueuePacket(system, packet, gameEngine, isPredictionRun);
            return;
        }

        int parentId = packet.getBulkParentId();
        if (parentId == -1) {
            // Invalid BIT packet, discard it.
            gameEngine.packetLostInternal(packet, isPredictionRun);
            return;
        }

        // Add the bit to the system's internal merging map
        system.getMergingPackets().computeIfAbsent(parentId, k -> new ArrayList<>()).add(packet);

        // The BIT packet is consumed and removed from the game.
        gameEngine.packetLostInternal(packet, isPredictionRun);

        List<Packet> collectedBits = system.getMergingPackets().get(parentId);
        if (collectedBits.size() >= packet.getTotalBitsInGroup()) {
            // All bits collected, create the new BULK packet.
            Packet newBulkPacket = new Packet(NetworkEnums.PacketShape.CIRCLE, system.getX(), system.getY(), NetworkEnums.PacketType.BULK);
            newBulkPacket.setCurrentSystem(system); // Set context before queuing

            // Queue the new BULK packet for processing in the next tick.
            queuePacket(system, newBulkPacket, gameEngine, isPredictionRun);

            // Clean up the collected bits from the map.
            system.getMergingPackets().remove(parentId);
        }
    }

    @Override
    public NetworkEnums.SystemType getSystemType() {
        return NetworkEnums.SystemType.MERGER;
    }
}