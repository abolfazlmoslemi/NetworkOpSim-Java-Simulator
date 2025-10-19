// FILE: /NetworkOpSim-Multiplayer/NetworkOpSim-Shared/src/main/java/com/networkopsim/shared/dto/ActiveWireEffectDTO.java
// ================================================================================
package com.networkopsim.shared.dto;

import java.awt.geom.Point2D;
import java.io.Serializable;

/**
 * A Data Transfer Object for representing an active visual effect on a wire.
 * This is used to render effects like Aergia and Eliphas on the client side.
 */
public class ActiveWireEffectDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum EffectType { AERGIA, ELIPHAS }

    private final EffectType type;
    private final Point2D.Double position;
    private final int parentWireId;

    public ActiveWireEffectDTO(EffectType type, Point2D.Double position, int parentWireId) {
        this.type = type;
        this.position = position;
        this.parentWireId = parentWireId;
    }

    public EffectType getType() {
        return type;
    }

    public Point2D.Double getPosition() {
        return position;
    }

    public int getParentWireId() {
        return parentWireId;
    }
}