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
                    initializeExtremeLevel1Layout(systems); // Renamed for more challenge
                    break;
                case 2:
                    initializeNightmareLevel2Layout(systems); // Renamed for maximum challenge
                    break;
                default:
                    java.lang.System.err.println("Warning: Invalid level number " + level + ". Loading Extreme Level 1 layout as fallback.");
                    gameState.setMaxWireLengthForLevel(getWireBudgetForLevel(1));
                    initializeExtremeLevel1Layout(systems);
                    actualLevelLoaded = 1;
                    JOptionPane.showMessageDialog(game,
                            "Level " + level + " not found. Loading Extreme Level 1 instead.",
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
            case 1: return 4750; // Updated wire budget for level 1
            case 2: return 6500; // Updated wire budget for level 2
            default: return 1000; // Default fallback (should ideally not be reached if level validation is robust)
        }
    }

    private static void initializeExtremeLevel1Layout(List<System> systems) {
        java.lang.System.out.println("Loading EXTREME Level 1 Layout: 2 Sources -> 5 Nodes -> 2 Sinks (No ANY ports)");
        int panelWidth = NetworkGame.WINDOW_WIDTH;
        int panelHeight = NetworkGame.WINDOW_HEIGHT;
        int sysWidth = System.SYSTEM_WIDTH;
        int sysHeight = System.SYSTEM_HEIGHT;

        // Sources (Total 3 Output ports: 2S, 1T)
        System sourceS1 = new System(panelWidth / 7, panelHeight / 5 - sysHeight / 2, true);
        sourceS1.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE);
        sourceS1.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE);
        sourceS1.configureGenerator(18, 1600);
        systems.add(sourceS1);

        System sourceT1 = new System(panelWidth * 6 / 7 - sysWidth, panelHeight / 5 - sysHeight/2, true);
        sourceT1.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE);
        sourceT1.configureGenerator(12, 1750);
        systems.add(sourceT1);

        // Nodes (5 nodes, specific ports only)
        int nodeYTop = panelHeight / 3 - sysHeight;
        int nodeYMid = panelHeight / 2 - sysHeight / 2;
        int nodeYBot = panelHeight * 2 / 3;

        System node1 = new System(panelWidth / 4 - sysWidth, nodeYTop, false);
        node1.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE);
        node1.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE);
        node1.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE);
        node1.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE);
        systems.add(node1);

        System node2 = new System(panelWidth * 3 / 4, nodeYTop, false);
        node2.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE);
        node2.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE);
        node2.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE);
        node2.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE);
        systems.add(node2);

        System node3 = new System(panelWidth / 2 - sysWidth / 2, nodeYMid, false); // Central node
        node3.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE);
        node3.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE);
        node3.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE); // Extra input
        node3.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE);
        node3.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE);
        node3.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE); // Extra output
        systems.add(node3);

        System node4 = new System(panelWidth / 4 - sysWidth, nodeYBot, false);
        node4.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE);
        node4.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE);
        node4.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE);
        node4.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE);
        systems.add(node4);

        System node5 = new System(panelWidth * 3 / 4, nodeYBot, false);
        node5.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE);
        node5.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE);
        node5.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE);
        node5.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE);
        systems.add(node5);

        // Sinks (Total 3 Input ports: 2S, 1T to match sources)
        System sinkS1 = new System(panelWidth / 7, panelHeight * 4 / 5 - sysHeight/2, true);
        sinkS1.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE);
        sinkS1.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE);
        systems.add(sinkS1);

        System sinkT1 = new System(panelWidth * 6 / 7 - sysWidth, panelHeight * 4 / 5 - sysHeight/2, true);
        sinkT1.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE);
        systems.add(sinkT1);

        java.lang.System.out.println("Extreme Level 1 systems created. Total Source Outputs: 3. Total Sink Inputs: 3.");
    }

    private static void initializeNightmareLevel2Layout(List<System> systems) {
        java.lang.System.out.println("Loading NIGHTMARE Level 2 Layout: 3 Sources -> 7 Nodes -> 3 Sinks (No ANY ports, high density)");
        int panelWidth = NetworkGame.WINDOW_WIDTH;
        int panelHeight = NetworkGame.WINDOW_HEIGHT;
        int sysWidth = System.SYSTEM_WIDTH;
        int sysHeight = System.SYSTEM_HEIGHT;

        // Sources (Total 5 Output ports: 3S, 2T)
        System sourceS_A = new System(panelWidth / 8, panelHeight / 6 - sysHeight, true);
        sourceS_A.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE);
        sourceS_A.configureGenerator(25, 1300);
        systems.add(sourceS_A);

        System sourceM_B = new System(panelWidth / 2 - sysWidth / 2, panelHeight / 7 - sysHeight / 2, true);
        sourceM_B.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE);
        sourceM_B.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE);
        sourceM_B.configureGenerator(30, 1200); // Very high traffic
        systems.add(sourceM_B);

        System sourceS_C = new System(panelWidth * 7 / 8 - sysWidth, panelHeight / 6 - sysHeight, true);
        sourceS_C.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE);
        sourceS_C.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE);
        sourceS_C.configureGenerator(28, 1350);
        systems.add(sourceS_C);

        // Nodes (7 nodes, creating a dense and complex network)
        int nodeTopRowY = panelHeight / 4 - sysHeight / 2;
        int nodeMidRowY1 = panelHeight / 2 - sysHeight - 30;
        int nodeMidRowY2 = panelHeight / 2 + 30;
        int nodeBotRowY = panelHeight * 3 / 4 - sysHeight / 2;

        System node1 = new System(panelWidth / 5 - sysWidth, nodeTopRowY, false);
        node1.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE);
        node1.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE);
        node1.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE);
        node1.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE);
        systems.add(node1);

        System node2 = new System(panelWidth * 2 / 5 - sysWidth / 2, nodeTopRowY + 20, false);
        node2.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE);
        node2.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE);
        node2.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE);
        node2.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE);
        systems.add(node2);

        System node3 = new System(panelWidth * 3 / 5 - sysWidth / 2, nodeTopRowY - 20, false);
        node3.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE);
        node3.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE);
        node3.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE);
        node3.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE);
        systems.add(node3);

        System node4 = new System(panelWidth * 4 / 5, nodeTopRowY, false);
        node4.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE);
        node4.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE);
        node4.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE);
        node4.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE);
        systems.add(node4);

        // Central choke point / complex router
        System node5_center = new System(panelWidth / 2 - sysWidth / 2, nodeMidRowY1 + 10, false);
        node5_center.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE);
        node5_center.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE);
        node5_center.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE);
        node5_center.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE);
        node5_center.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE);
        node5_center.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE);
        node5_center.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE);
        node5_center.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE);
        systems.add(node5_center);

        System node6 = new System(panelWidth / 3 - sysWidth, nodeBotRowY, false);
        node6.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE);
        node6.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE);
        node6.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE);
        node6.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE);
        systems.add(node6);

        System node7 = new System(panelWidth * 2 / 3, nodeBotRowY, false);
        node7.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE);
        node7.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE);
        node7.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE);
        node7.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE);
        systems.add(node7);

        // Sinks (Total 5 Input ports: 3S, 2T to match sources)
        System sinkS_A_End = new System(panelWidth / 8, panelHeight * 5 / 6, true);
        sinkS_A_End.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE);
        sinkS_A_End.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE);
        systems.add(sinkS_A_End);

        System sinkM_B_End = new System(panelWidth / 2 - sysWidth / 2, panelHeight * 6 / 7, true);
        sinkM_B_End.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE);
        sinkM_B_End.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE);
        systems.add(sinkM_B_End);

        System sinkS_C_End = new System(panelWidth * 7 / 8 - sysWidth, panelHeight * 5 / 6, true);
        sinkS_C_End.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE);
        systems.add(sinkS_C_End);

        java.lang.System.out.println("Nightmare Level 2 systems created. Total Source Outputs: 5. Total Sink Inputs: 5.");
    }
}