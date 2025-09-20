package com.networkopsim.game.model.state;


import com.networkopsim.game.model.core.System;
import com.networkopsim.game.view.panels.GamePanel;

public class TemporalState {
    private final long timestamp; 
    public TemporalState(long time) {
        this.timestamp = time;
    }
    public long getTimestamp() {
        return timestamp;
    }
    public void restoreState(GamePanel panel) {
        java.lang.System.err.println("TemporalState.restoreState() - Functionality is NOT IMPLEMENTED.");
    }
    @Override
    public String toString() {
        return "TemporalState{timestamp=" + timestamp + " [NOT IMPLEMENTED]}";
    }
}

