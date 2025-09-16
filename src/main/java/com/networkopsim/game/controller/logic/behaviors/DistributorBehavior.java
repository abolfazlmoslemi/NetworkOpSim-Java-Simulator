// ===== File: DistributorBehavior.java (FINAL - Corrected with getSystemType) =====

package com.networkopsim.game.controller.logic.behaviors;

import com.networkopsim.game.controller.logic.GameEngine;
import com.networkopsim.game.model.core.Packet;
import com.networkopsim.game.model.core.System;
import com.networkopsim.game.model.enums.NetworkEnums;
import java.util.ArrayList;
import java.util.List;

public class DistributorBehavior extends AbstractSystemBehavior {

    @Override
    public void receivePacket(System system, Packet packet, GameEngine gameEngine, boolean isPredictionRun, boolean enteredCompatibly) {
        if (packet.getPacketType() == NetworkEnums.PacketType.BULK && !system.packetQueue.isEmpty()) {
            gameEngine.packetLostInternal(packet, isPredictionRun);
            return;
        }

        if (packet.getPacketType() != NetworkEnums.PacketType.BULK) {
            processOrQueuePacket(system, packet, gameEngine, isPredictionRun);
            return;
        }

        int numParts = packet.getSize();
        if (numParts <= 0) {
            gameEngine.packetLostInternal(packet, isPredictionRun);
            return;
        }

        List<Packet> messengerParts = new ArrayList<>();
        for (int i = 0; i < numParts; i++) {
            // Create a MESSENGER packet.
            Packet part = new Packet(NetworkEnums.PacketShape.CIRCLE, system.getX(), system.getY(), NetworkEnums.PacketType.MESSENGER);
            // Configure it to be part of a BULK group.
            part.configureAsBulkPart(packet.getId(), numParts);
            messengerParts.add(part);
        }

        gameEngine.packetLostInternal(packet, isPredictionRun);

        synchronized (system.packetQueue) {
            for (Packet part : messengerParts) {
                // Use the special queuePacket method which now bypasses capacity for Distributors.
                queuePacket(system, part, gameEngine, isPredictionRun);
            }
        }
    }

    // By not overriding processQueue, we use the reliable, one-by-one logic from the parent class.

    // [FIXED] Added the missing getSystemType method.
    @Override
    public NetworkEnums.SystemType getSystemType() {
        return NetworkEnums.SystemType.DISTRIBUTOR;
    }
}