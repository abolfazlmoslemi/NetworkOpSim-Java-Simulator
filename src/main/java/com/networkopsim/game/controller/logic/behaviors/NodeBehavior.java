package com.networkopsim.game.controller.logic.behaviors;

import com.networkopsim.game.controller.logic.GameEngine;
import com.networkopsim.game.model.core.Packet;
import com.networkopsim.game.model.core.System;
import com.networkopsim.game.model.enums.NetworkEnums;
import com.networkopsim.game.controller.logic.SystemBehavior;
/**
 * Behavior for standard NODE and ANTITROJAN systems.
 * Their logic is simply to process or queue packets based on standard routing rules.
 */
public class NodeBehavior extends AbstractSystemBehavior {

    private final NetworkEnums.SystemType systemType;

    public NodeBehavior(NetworkEnums.SystemType type) {
        // This behavior can be used for both NODE and ANTITROJAN as their routing is identical.
        if (type != NetworkEnums.SystemType.NODE && type != NetworkEnums.SystemType.ANTITROJAN) {
            throw new IllegalArgumentException("NodeBehavior is only for NODE and ANTITROJAN system types.");
        }
        this.systemType = type;
    }

    @Override
    public void receivePacket(System system, Packet packet, GameEngine gameEngine, boolean isPredictionRun, boolean enteredCompatibly) {
        // Standard nodes simply process or queue the packet.
        processOrQueuePacket(system, packet, gameEngine, isPredictionRun);
    }

    @Override
    public NetworkEnums.SystemType getSystemType() {
        return this.systemType;
    }
}