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
                    initializeLevel1WithNewSystems(systems);
                    break;
                case 2:
                    initializeNightmareLevel2Layout(systems);
                    break;
                default:
                    java.lang.System.err.println("Warning: Invalid level number " + level + ". Loading Level 1 as fallback.");
                    gameState.setMaxWireLengthForLevel(getWireBudgetForLevel(1));
                    initializeLevel1WithNewSystems(systems);
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
            case 1: return 30000;
            case 2: return 30000;
            default: return 1000;
        }
    }

    private static void initializeLevel1WithNewSystems(List<System> systems) {
        java.lang.System.out.println("Loading Level 1 Layout with new systems.");
        int panelWidth = NetworkGame.WINDOW_WIDTH;
        int panelHeight = NetworkGame.WINDOW_HEIGHT;
        int sysWidth = System.SYSTEM_WIDTH;

        System sourceS1 = new System(panelWidth / 8, panelHeight / 5, NetworkEnums.SystemType.SOURCE);
        sourceS1.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE);
        sourceS1.configureGenerator(20, 1500);
        systems.add(sourceS1);

        System sinkS1 = new System(panelWidth * 7 / 8 - sysWidth, panelHeight * 4 / 5, NetworkEnums.SystemType.SINK);
        sinkS1.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE);
        systems.add(sinkS1);

        System corruptor1 = new System(panelWidth / 3, panelHeight / 3, NetworkEnums.SystemType.CORRUPTOR);
        corruptor1.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE);
        corruptor1.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE);
        systems.add(corruptor1);

        System antiTrojan1 = new System(panelWidth / 2, panelHeight / 2, NetworkEnums.SystemType.ANTITROJAN);
        antiTrojan1.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE);
        antiTrojan1.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE);
        systems.add(antiTrojan1);

        System vpn1 = new System(panelWidth * 2 / 3, panelHeight * 2 / 3, NetworkEnums.SystemType.VPN);
        vpn1.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE);
        vpn1.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE);
        systems.add(vpn1);

        System spy1 = new System(panelWidth / 5, panelHeight * 3 / 4, NetworkEnums.SystemType.SPY);
        spy1.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE);
        spy1.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE);
        systems.add(spy1);

        System spy2 = new System(panelWidth * 4 / 5, panelHeight / 4, NetworkEnums.SystemType.SPY);
        spy2.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE);
        spy2.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE);
        systems.add(spy2);
    }

    private static void initializeNightmareLevel2Layout(List<System> systems) {
        java.lang.System.out.println("Loading NIGHTMARE Level 2 Layout: 3 Sources -> 7 Nodes -> 3 Sinks (No ANY ports, high density)");
        int panelWidth = NetworkGame.WINDOW_WIDTH;
        int panelHeight = NetworkGame.WINDOW_HEIGHT;
        int sysWidth = System.SYSTEM_WIDTH;
        int sysHeight = System.SYSTEM_HEIGHT;

        // Sources
        System sourceS_A = new System(panelWidth / 8, panelHeight / 6 - sysHeight, NetworkEnums.SystemType.SOURCE);
        sourceS_A.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE);
        sourceS_A.configureGenerator(25, 1300);
        systems.add(sourceS_A);

        System sourceM_B = new System(panelWidth / 2 - sysWidth / 2, panelHeight / 7 - sysHeight / 2, NetworkEnums.SystemType.SOURCE);
        sourceM_B.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE);
        sourceM_B.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE);
        sourceM_B.configureGenerator(30, 1200);
        systems.add(sourceM_B);

        System sourceS_C = new System(panelWidth * 7 / 8 - sysWidth, panelHeight / 6 - sysHeight, NetworkEnums.SystemType.SOURCE);
        sourceS_C.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE);
        sourceS_C.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE);
        sourceS_C.configureGenerator(28, 1350);
        systems.add(sourceS_C);

        // MODIFIED: Added a source for SECRET packets
        System sourceSecret = new System(50, panelHeight / 2, NetworkEnums.SystemType.SOURCE);
        sourceSecret.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE); // Port shape doesn't matter for secret packets
        sourceSecret.configureGenerator(10, 3000, NetworkEnums.PacketType.SECRET);
        systems.add(sourceSecret);

        // Nodes
        int nodeTopRowY = panelHeight / 4 - sysHeight / 2;
        int nodeMidRowY1 = panelHeight / 2 - sysHeight - 30;
        int nodeBotRowY = panelHeight * 3 / 4 - sysHeight / 2;

        System node1 = new System(panelWidth / 5 - sysWidth, nodeTopRowY, NetworkEnums.SystemType.NODE);
        node1.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE);
        node1.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE);
        node1.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE);
        node1.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE);
        systems.add(node1);

        System node2 = new System(panelWidth * 2 / 5 - sysWidth / 2, nodeTopRowY + 20, NetworkEnums.SystemType.NODE);
        node2.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE); node2.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE);
        node2.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE); node2.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE);
        systems.add(node2);

        System node3 = new System(panelWidth * 3 / 5 - sysWidth / 2, nodeTopRowY - 20, NetworkEnums.SystemType.NODE);
        node3.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE); node3.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE);
        node3.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE); node3.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE);
        systems.add(node3);

        System node4 = new System(panelWidth * 4 / 5, nodeTopRowY, NetworkEnums.SystemType.NODE);
        node4.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE); node4.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE);
        node4.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE); node4.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE);
        systems.add(node4);

        System node5_center = new System(panelWidth / 2 - sysWidth / 2, nodeMidRowY1 + 10, NetworkEnums.SystemType.VPN); // Changed to VPN for testing
        node5_center.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE); node5_center.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE);
        node5_center.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE); node5_center.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE);
        node5_center.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE); node5_center.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE);
        node5_center.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE); node5_center.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE);
        systems.add(node5_center);

        System node6 = new System(panelWidth / 3 - sysWidth, nodeBotRowY, NetworkEnums.SystemType.NODE);
        node6.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE); node6.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE);
        node6.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE); node6.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE);
        systems.add(node6);

        System node7 = new System(panelWidth * 2 / 3, nodeBotRowY, NetworkEnums.SystemType.NODE);
        node7.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE); node7.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE);
        node7.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE); node7.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE);
        systems.add(node7);

        // Sinks
        System sinkS_A_End = new System(panelWidth / 8, panelHeight * 5 / 6, NetworkEnums.SystemType.SINK);
        sinkS_A_End.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE);
        sinkS_A_End.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE);
        systems.add(sinkS_A_End);

        System sinkM_B_End = new System(panelWidth / 2 - sysWidth / 2, panelHeight * 6 / 7, NetworkEnums.SystemType.SINK);
        sinkM_B_End.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE);
        sinkM_B_End.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE);
        systems.add(sinkM_B_End);

        System sinkS_C_End = new System(panelWidth * 7 / 8 - sysWidth, panelHeight * 5 / 6, NetworkEnums.SystemType.SINK);
        sinkS_C_End.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE);
        systems.add(sinkS_C_End);
    }
}