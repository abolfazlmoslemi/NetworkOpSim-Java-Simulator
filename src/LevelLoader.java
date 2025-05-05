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
                    initializeHardLevel1Layout(systems); 
                    break;
                case 2:
                    initializeHardLevel2Layout(systems); 
                    break;
                default:
                    java.lang.System.err.println("Warning: Invalid level number " + level + ". Loading HARD Level 1 layout as fallback.");
                    gameState.setMaxWireLengthForLevel(getWireBudgetForLevel(1)); 
                    initializeHardLevel1Layout(systems); 
                    actualLevelLoaded = 1;
                    JOptionPane.showMessageDialog(game,
                            "Level " + level + " not found. Loading Hard Level 1 instead.",
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
            case 1: return 10000; 
            case 2: return 20000; 
            default: return 1000; 
        }
    }
    private static void initializeHardLevel1Layout(List<System> systems) {
        java.lang.System.out.println("Loading HARD Level 1 Layout: 2 Sources -> 3 Nodes -> 2 Sinks (Specific Ports)");
        int panelWidth = NetworkGame.WINDOW_WIDTH;
        int panelHeight = NetworkGame.WINDOW_HEIGHT;
        int sysWidth = System.SYSTEM_WIDTH;
        int sysHeight = System.SYSTEM_HEIGHT;
        int hSpacing = (panelWidth - 4 * sysWidth) / 5; 
        int vSpacing = (panelHeight - 2 * sysHeight) / 3; 
        System sourceS = new System(hSpacing, vSpacing, true); 
        sourceS.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE);
        sourceS.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE);
        sourceS.configureGenerator(12, 1800); 
        systems.add(sourceS);
        System sourceT = new System(panelWidth - hSpacing - sysWidth, vSpacing, true); 
        sourceT.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE);
        sourceT.configureGenerator(10, 2100); 
        systems.add(sourceT);
        System node1 = new System(hSpacing * 2 + sysWidth, panelHeight / 2 - sysHeight / 2, false); 
        node1.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE);
        node1.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE); 
        node1.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE);
        node1.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE);
        systems.add(node1);
        System node2 = new System(panelWidth / 2 - sysWidth / 2, vSpacing * 2 + sysHeight, false); 
        node2.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE);
        node2.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE);
        node2.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE);
        node2.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE);
        systems.add(node2);
        System node3 = new System(panelWidth - (hSpacing * 2 + sysWidth) - sysWidth, panelHeight / 2 - sysHeight / 2, false); 
        node3.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE);
        node3.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE); 
        node3.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE);
        node3.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE);
        systems.add(node3);
        System sinkS = new System(hSpacing, panelHeight - vSpacing - sysHeight, true); 
        sinkS.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE);
        sinkS.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE);
        systems.add(sinkS);
        System sinkT = new System(panelWidth - hSpacing - sysWidth, panelHeight - vSpacing - sysHeight, true); 
        sinkT.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE);
        systems.add(sinkT);
        java.lang.System.out.println("Hard Level 1 systems created.");
    }
    private static void initializeHardLevel2Layout(List<System> systems) {
        java.lang.System.out.println("Loading HARD Level 2 Layout: 3 Sources -> 4 Nodes -> 3 Sinks (Specific Ports)");
        int panelWidth = NetworkGame.WINDOW_WIDTH;
        int panelHeight = NetworkGame.WINDOW_HEIGHT;
        int sysWidth = System.SYSTEM_WIDTH;
        int sysHeight = System.SYSTEM_HEIGHT;
        int topY = 80;
        int midY1 = panelHeight / 2 - 100;
        int midY2 = panelHeight / 2 + 100;
        int bottomY = panelHeight - 80 - sysHeight;
        int leftX = 100;
        int midX1 = panelWidth / 2 - 150;
        int midX2 = panelWidth / 2 + 150;
        int rightX = panelWidth - 100 - sysWidth;
        System sourceS1 = new System(leftX, topY, true); 
        sourceS1.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE);
        sourceS1.configureGenerator(15, 1500); 
        systems.add(sourceS1);
        System sourceT = new System(panelWidth/2 - sysWidth/2, topY, true); 
        sourceT.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE);
        sourceT.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE);
        sourceT.configureGenerator(18, 1600);
        systems.add(sourceT);
        System sourceS2 = new System(rightX, topY, true); 
        sourceS2.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE);
        sourceS2.configureGenerator(15, 1550);
        systems.add(sourceS2);
        System nodeA = new System(midX1, midY1, false); 
        nodeA.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE);
        nodeA.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE);
        nodeA.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE);
        nodeA.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE); 
        systems.add(nodeA);
        System nodeB = new System(midX2, midY1, false); 
        nodeB.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE);
        nodeB.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE);
        nodeB.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE);
        nodeB.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE); 
        systems.add(nodeB);
        System nodeC = new System(midX1, midY2, false); 
        nodeC.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE); 
        nodeC.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE);
        nodeC.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE);
        nodeC.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE);
        systems.add(nodeC);
        System nodeD = new System(midX2, midY2, false); 
        nodeD.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE); 
        nodeD.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE);
        nodeD.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE);
        nodeD.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE);
        systems.add(nodeD);
        System sinkS1 = new System(leftX, bottomY, true); 
        sinkS1.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE);
        sinkS1.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE);
        systems.add(sinkS1);
        System sinkT = new System(panelWidth/2 - sysWidth/2, bottomY, true); 
        sinkT.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE);
        sinkT.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE);
        systems.add(sinkT);
        System sinkS2 = new System(rightX, bottomY, true); 
        sinkS2.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE);
        systems.add(sinkS2);
        java.lang.System.out.println("Hard Level 2 systems created.");
    }
}