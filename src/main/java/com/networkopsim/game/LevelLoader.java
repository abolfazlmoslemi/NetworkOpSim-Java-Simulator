// ===== File: LevelLoader.java =====

package com.networkopsim.game;
import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class LevelLoader {
    public static class LevelLayout {
        public final List<System> systems;
        public final List<Wire> wires;
        public final int levelNumber;
        public LevelLayout(int levelNumber, List<System> systems) {
            this.levelNumber = levelNumber;
            this.systems = Objects.requireNonNull(systems, "Systems list cannot be null");
            this.wires = new ArrayList<>();
        }
    }

    public static LevelLayout loadLevel(int level, GameState gameState, NetworkGame game) {
        Objects.requireNonNull(gameState, "GameState cannot be null for level loading");
        Objects.requireNonNull(game, "NetworkGame cannot be null for level loading");
        List<System> systems = new ArrayList<>();
        int actualLevelLoaded = level;
        try {
            int wireBudget = getWireBudgetForLevel(level);
            gameState.setMaxWireLengthForLevel(wireBudget);
            switch (level) {
                case 1:
                    initializeChallengingLevel1(systems);
                    break;
                case 2:
                    initializeHardcoreLevel2(systems);
                    break;
                default:
                    java.lang.System.err.println("Warning: Invalid level number " + level + ". Loading Level 1 as fallback.");
                    gameState.setMaxWireLengthForLevel(getWireBudgetForLevel(1));
                    initializeChallengingLevel1(systems);
                    actualLevelLoaded = 1;
                    JOptionPane.showMessageDialog(game,
                            "Level " + level + " not found. Loading Level 1 instead.",
                            "Level Not Found", JOptionPane.WARNING_MESSAGE);
                    break;
            }
            for (System s : systems) {
                if(s != null) {
                    s.updateAllPortPositions();
                }
            }
            return new LevelLayout(actualLevelLoaded, systems);
        } catch (Exception e) {
            java.lang.System.err.println("FATAL ERROR initializing level " + level + " layout: " + e.getMessage());
            e.printStackTrace();
            JOptionPane.showMessageDialog(game,
                    "Failed to initialize the layout for level " + level + ".\nError: " + e.getMessage(),
                    "Level Load Error", JOptionPane.ERROR_MESSAGE);
            return null;
        }
    }

    private static int getWireBudgetForLevel(int level) {
        switch (level) {
            case 1: return 32000;
            case 2: return 35000;
            default: return 1000;
        }
    }

    /**
     * Level 1: A challenging layout featuring all systems and packets.
     * Total Inputs: 13, Total Outputs: 13
     */
    private static void initializeChallengingLevel1(List<System> systems) {
        int panelWidth = NetworkGame.WINDOW_WIDTH;
        int panelHeight = NetworkGame.WINDOW_HEIGHT;
        int sysWidth = System.SYSTEM_WIDTH;

        // --- SOURCES (4 Outputs) ---
        System sourceNormal = new System(50, 100, NetworkEnums.SystemType.SOURCE);
        sourceNormal.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE);
        sourceNormal.configureGenerator(15, 2000);
        systems.add(sourceNormal);

        System sourceSpecial = new System(50, 250, NetworkEnums.SystemType.SOURCE);
        sourceSpecial.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE);
        sourceSpecial.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.CIRCLE);
        sourceSpecial.configureGenerator(5, 4000, NetworkEnums.PacketType.WOBBLE);
        systems.add(sourceSpecial);

        System sourceBulk = new System(50, 400, NetworkEnums.SystemType.SOURCE);
        sourceBulk.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.CIRCLE);
        sourceBulk.configureGenerator(2, 10000, NetworkEnums.PacketType.BULK);
        systems.add(sourceBulk);

        // --- SINKS (4 Inputs) ---
        System sink1 = new System(panelWidth - sysWidth - 50, 100, NetworkEnums.SystemType.SINK);
        sink1.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE);
        systems.add(sink1);

        System sink2 = new System(panelWidth - sysWidth - 50, 250, NetworkEnums.SystemType.SINK);
        sink2.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE);
        sink2.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.CIRCLE);
        systems.add(sink2);

        System sinkBulk = new System(panelWidth - sysWidth - 50, 400, NetworkEnums.SystemType.SINK);
        sinkBulk.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.CIRCLE); // For merged packets
        systems.add(sinkBulk);

        // --- PROCESSING SYSTEMS (9 Inputs, 9 Outputs) ---
        // Top Path
        System distributor = new System(300, 400, NetworkEnums.SystemType.DISTRIBUTOR);
        distributor.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.CIRCLE);
        distributor.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.CIRCLE); // To VPN
        distributor.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.CIRCLE); // To Corruptor
        systems.add(distributor);

        System vpn = new System(500, 300, NetworkEnums.SystemType.VPN);
        vpn.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.CIRCLE);
        vpn.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.CIRCLE);
        systems.add(vpn);

        // Bottom Path
        System corruptor = new System(500, 500, NetworkEnums.SystemType.CORRUPTOR);
        corruptor.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.CIRCLE);
        corruptor.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.CIRCLE);
        systems.add(corruptor);

        // Central Path
        System spy = new System(350, 175, NetworkEnums.SystemType.SPY);
        spy.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE);
        spy.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE);
        spy.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE);
        spy.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE);
        systems.add(spy);

        System antiTrojan = new System(650, 175, NetworkEnums.SystemType.ANTITROJAN);
        antiTrojan.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE);
        antiTrojan.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE);
        antiTrojan.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE);
        antiTrojan.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE);
        systems.add(antiTrojan);

        // Final Merge Point
        System merger = new System(800, 400, NetworkEnums.SystemType.MERGER);
        merger.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.CIRCLE);
        merger.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.CIRCLE);
        merger.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.CIRCLE);
        systems.add(merger);
    }

    /**
     * Level 2: A very difficult and dense layout.
     * Total Inputs: 16, Total Outputs: 16
     */
    private static void initializeHardcoreLevel2(List<System> systems) {
        int panelWidth = NetworkGame.WINDOW_WIDTH;
        int panelHeight = NetworkGame.WINDOW_HEIGHT;
        int sysWidth = System.SYSTEM_WIDTH;

        // --- SOURCES (5 Outputs) ---
        System sourceTopLeft = new System(50, 50, NetworkEnums.SystemType.SOURCE);
        sourceTopLeft.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE);
        sourceTopLeft.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE);
        sourceTopLeft.configureGenerator(20, 1500);
        systems.add(sourceTopLeft);

        System sourceBottomLeft = new System(50, panelHeight - 150, NetworkEnums.SystemType.SOURCE);
        sourceBottomLeft.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.CIRCLE);
        sourceBottomLeft.configureGenerator(3, 8000, NetworkEnums.PacketType.BULK);
        systems.add(sourceBottomLeft);

        System sourceTopRight = new System(panelWidth - sysWidth - 50, 50, NetworkEnums.SystemType.SOURCE);
        sourceTopRight.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE);
        sourceTopRight.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.CIRCLE);
        sourceTopRight.configureGenerator(10, 3000, NetworkEnums.PacketType.WOBBLE);
        systems.add(sourceTopRight);

        // --- SINKS (5 Inputs) ---
        System sinkTopLeft = new System(50, panelHeight - 250, NetworkEnums.SystemType.SINK);
        sinkTopLeft.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE);
        systems.add(sinkTopLeft);

        System sinkBottomRight = new System(panelWidth - sysWidth - 50, panelHeight - 150, NetworkEnums.SystemType.SINK);
        sinkBottomRight.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE);
        sinkBottomRight.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE);
        systems.add(sinkBottomRight);

        System sinkCenter = new System(panelWidth / 2 - sysWidth / 2, 50, NetworkEnums.SystemType.SINK);
        sinkCenter.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.CIRCLE);
        sinkCenter.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.CIRCLE); // For merged and wobble
        systems.add(sinkCenter);

        // --- PROCESSING SYSTEMS (11 Inputs, 11 Outputs) ---
        // Left Column
        System distributor = new System(250, panelHeight - 150, NetworkEnums.SystemType.DISTRIBUTOR);
        distributor.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.CIRCLE);
        distributor.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.CIRCLE); // -> VPN
        distributor.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.CIRCLE); // -> Corruptor
        systems.add(distributor);

        System vpn = new System(250, 200, NetworkEnums.SystemType.VPN);
        vpn.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE);
        vpn.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.CIRCLE);
        vpn.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE);
        vpn.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.CIRCLE);
        systems.add(vpn);

        // Central Column
        System spy = new System(panelWidth/2 - sysWidth/2, panelHeight/2 - 50, NetworkEnums.SystemType.SPY);
        spy.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE);
        spy.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE);
        spy.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE);
        spy.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE);
        systems.add(spy);

        System antiTrojan = new System(panelWidth/2 - sysWidth/2, panelHeight/2 + 50, NetworkEnums.SystemType.ANTITROJAN);
        antiTrojan.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE);
        antiTrojan.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.CIRCLE);
        antiTrojan.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE);
        antiTrojan.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.CIRCLE);
        systems.add(antiTrojan);

        // Right Column
        System corruptor = new System(panelWidth - sysWidth - 250, 200, NetworkEnums.SystemType.CORRUPTOR);
        corruptor.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE);
        corruptor.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.CIRCLE);
        corruptor.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE);
        corruptor.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.CIRCLE);
        systems.add(corruptor);

        System merger = new System(panelWidth - sysWidth - 250, panelHeight - 150, NetworkEnums.SystemType.MERGER);
        merger.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.CIRCLE);
        merger.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.CIRCLE);
        merger.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.CIRCLE);
        systems.add(merger);
    }
}