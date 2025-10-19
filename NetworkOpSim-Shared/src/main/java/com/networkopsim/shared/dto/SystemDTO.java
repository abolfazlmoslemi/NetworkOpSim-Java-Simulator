// FILE: /NetworkOpSim-Multiplayer/NetworkOpSim-Shared/src/main/java/com/networkopsim/shared/dto/SystemDTO.java
// ================================================================================

package com.networkopsim.shared.dto;

import com.networkopsim.shared.model.NetworkEnums;

import java.awt.Point;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * A Data Transfer Object representing the visual state of a System.
 * It holds all necessary information for the client to render a system correctly.
 */
public class SystemDTO implements Serializable {
    private static final long serialVersionUID = 5L;

    private final int id;
    private final int x;
    private final int y;
    private final NetworkEnums.SystemType systemType;
    private final List<PortDTO> ports;
    private final int queueSize;
    private final int queueCapacity;
    private final boolean isDisabled;
    private final boolean isIndicatorOn;
    private final NetworkEnums.PacketType packetTypeToGenerate;
    private final int currentBulkOperationId;
    private final boolean allPortsConnected;

    // Multiplayer and Controllable System fields
    private final int ownerId;
    private final int disabledForPlayerId;
    private final boolean isControllable;
    private final long systemCooldownRemainingMs;
    private final Map<Integer, Map<NetworkEnums.PacketType, Integer>> ammoStock;
    private final Map<Integer, Map<NetworkEnums.PacketType, Long>> packetCooldownsMs;

    public SystemDTO(int id, int x, int y, NetworkEnums.SystemType systemType, List<PortDTO> ports,
                     int queueSize, int queueCapacity, boolean isDisabled, boolean isIndicatorOn,
                     NetworkEnums.PacketType packetTypeToGenerate, int currentBulkOperationId,
                     boolean allPortsConnected,
                     int ownerId, int disabledForPlayerId, boolean isControllable,
                     long systemCooldownRemainingMs,
                     Map<Integer, Map<NetworkEnums.PacketType, Integer>> ammoStock,
                     Map<Integer, Map<NetworkEnums.PacketType, Long>> packetCooldownsMs) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.systemType = systemType;
        this.ports = ports;
        this.queueSize = queueSize;
        this.queueCapacity = queueCapacity;
        this.isDisabled = isDisabled;
        this.isIndicatorOn = isIndicatorOn;
        this.packetTypeToGenerate = packetTypeToGenerate;
        this.currentBulkOperationId = currentBulkOperationId;
        this.allPortsConnected = allPortsConnected;
        this.ownerId = ownerId;
        this.disabledForPlayerId = disabledForPlayerId;
        this.isControllable = isControllable;
        this.systemCooldownRemainingMs = systemCooldownRemainingMs;
        this.ammoStock = ammoStock;
        this.packetCooldownsMs = packetCooldownsMs;
    }

    // Getters for basic info
    public int getId() { return id; }
    public int getX() { return x; }
    public int getY() { return y; }
    public NetworkEnums.SystemType getSystemType() { return systemType; }
    public List<PortDTO> getPorts() { return ports; }
    public int getQueueSize() { return queueSize; }
    public int getQueueCapacity() { return queueCapacity; }
    public boolean isDisabled() { return isDisabled; }
    public boolean isIndicatorOn() { return isIndicatorOn; }
    public NetworkEnums.PacketType getPacketTypeToGenerate() { return packetTypeToGenerate; }
    public int getCurrentBulkOperationId() { return currentBulkOperationId; }
    public boolean areAllPortsConnected() { return allPortsConnected; }

    // Getters for multiplayer and controllable info
    public int getOwnerId() { return ownerId; }
    public int getDisabledForPlayerId() { return disabledForPlayerId; }
    public boolean isControllable() { return isControllable; }
    public long getSystemCooldownRemainingMs() { return systemCooldownRemainingMs; }

    // Convenience Getters for Client
    public Map<NetworkEnums.PacketType, Integer> getAllAmmoForPlayer(int playerId) {
        return this.ammoStock.getOrDefault(playerId, Collections.emptyMap());
    }
    public long getPacketCooldownForPlayer(int playerId, NetworkEnums.PacketType type) {
        return this.packetCooldownsMs.getOrDefault(playerId, Collections.emptyMap()).getOrDefault(type, 0L);
    }

    public static class PortDTO implements Serializable {
        private static final long serialVersionUID = 2L; // Version bumped
        private final int id;
        private final NetworkEnums.PortType type;
        private final NetworkEnums.PortShape shape;
        private final Point position;
        private final int parentSystemId; // <-- NEW FIELD

        public PortDTO(int id, NetworkEnums.PortType type, NetworkEnums.PortShape shape, Point position, int parentSystemId) { // <-- CONSTRUCTOR MODIFIED
            this.id = id; this.type = type; this.shape = shape; this.position = position; this.parentSystemId = parentSystemId;
        }
        public int getId() { return id; }
        public NetworkEnums.PortType getType() { return type; }
        public NetworkEnums.PortShape getShape() { return shape; }
        public Point getPosition() { return position; }
        public int getParentSystemId() { return parentSystemId; } // <-- NEW GETTER
    }
}