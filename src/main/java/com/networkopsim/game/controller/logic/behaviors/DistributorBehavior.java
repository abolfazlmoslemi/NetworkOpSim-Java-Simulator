package com.networkopsim.game.controller.logic.behaviors;

import com.networkopsim.game.controller.logic.GameEngine;
import com.networkopsim.game.model.core.Packet;
import com.networkopsim.game.model.core.System;
import com.networkopsim.game.model.enums.NetworkEnums;
import com.networkopsim.game.controller.logic.SystemBehavior;

import java.util.ArrayList;
import java.util.List;

/**
 * Behavior for DISTRIBUTOR systems. Breaks down a single BULK packet into multiple BIT packets.
 * Other packet types are routed normally.
 */
public class DistributorBehavior extends AbstractSystemBehavior {

    @Override
    public void receivePacket(System system, Packet packet, GameEngine gameEngine, boolean isPredictionRun, boolean enteredCompatibly) {
        if (packet.getPacketType() != NetworkEnums.PacketType.BULK) {
            // If it's not a BULK packet, just treat it like a normal node.
            processOrQueuePacket(system, packet, gameEngine, isPredictionRun);
            return;
        }

        int numBits = packet.getSize();
        if (numBits <= 0) {
            gameEngine.packetLostInternal(packet, isPredictionRun);
            return;
        }

        List<Packet> bitPackets = new ArrayList<>();
        for (int i = 0; i < numBits; i++) {
            // Create a new BIT packet. Position doesn't matter much as it will be queued immediately.
            Packet bit = new Packet(NetworkEnums.PacketShape.CIRCLE, system.getX(), system.getY(), NetworkEnums.PacketType.BIT);
            bit.configureAsBit(packet.getId(), numBits);
            bitPackets.add(bit);
        }

        // The original BULK packet is consumed and removed from the game.
        gameEngine.packetLostInternal(packet, isPredictionRun);

        // Queue all the newly created BIT packets.
        for (Packet bit : bitPackets) {
            // Bit packets need a system context before being queued.
            bit.setCurrentSystem(system);
            // This will attempt to route them immediately if possible, or queue them.
            processOrQueuePacket(system, bit, gameEngine, isPredictionRun);
        }
    }

    @Override
    public NetworkEnums.SystemType getSystemType() {
        return NetworkEnums.SystemType.DISTRIBUTOR;
    }
}