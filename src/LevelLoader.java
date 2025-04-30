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
            int wireBudget = getDefaultWireBudgetForLevel(level);
            gameState.setMaxWireLengthForLevel(wireBudget);
            switch (level) {
                case 1:
                    initializeFinalLevel1Layout(systems);
                    break;
                case 2:
                    initializeFinalLevel2Layout(systems);
                    break;
                default:
                    java.lang.System.err.println("Warning: Invalid level number " + level + ". Loading Final Level 1 layout as fallback.");
                    gameState.setMaxWireLengthForLevel(getDefaultWireBudgetForLevel(1));
                    initializeFinalLevel1Layout(systems);
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
    private static int getDefaultWireBudgetForLevel(int level) {
        switch (level) {
            case 1: return 100000;
            case 2: return 1700;
            default: return 800;
        }
    }
    private static void initializeFinalLevel1Layout(List<System> systems) {
        java.lang.System.out.println("Loading FINAL Level 1 Layout (Specific Ports, Diamond)...");
        int centerX = NetworkGame.WINDOW_WIDTH / 2;
        int centerY = NetworkGame.WINDOW_HEIGHT / 2 + 30;
        int horizontalOffset = 180;
        int verticalOffset = 220;
        int sourceX = centerX; int sourceY = centerY - verticalOffset;
        int node1X = centerX - horizontalOffset; int node1Y = centerY;
        int node2X = centerX + horizontalOffset; int node2Y = centerY;
        int sinkX = centerX; int sinkY = centerY + verticalOffset;
        System source = new System(sourceX - System.SYSTEM_WIDTH / 2, sourceY - System.SYSTEM_HEIGHT / 2, true);
        source.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE);
        source.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE);
        source.configureGenerator(10, 2000);
        System node1 = new System(node1X - System.SYSTEM_WIDTH / 2, node1Y - System.SYSTEM_HEIGHT / 2, false);
        node1.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE);
        node1.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE);
        node1.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE);
        node1.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE);
        System node2 = new System(node2X - System.SYSTEM_WIDTH / 2, node2Y - System.SYSTEM_HEIGHT / 2, false);
        node2.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE);
        node2.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE);
        node2.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE);
        node2.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE);
        System sink = new System(sinkX - System.SYSTEM_WIDTH / 2, sinkY - System.SYSTEM_HEIGHT / 2, true);
        sink.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE);
        sink.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE);
        systems.add(source);
        systems.add(node1);
        systems.add(node2);
        systems.add(sink);
        java.lang.System.out.println("FINAL Level 1 systems created.");
    }
    private static void initializeFinalLevel2Layout(List<System> systems) {
        java.lang.System.out.println("Loading FINAL Level 2 Layout (Bottleneck & Collision Focus)...");
        int centerX = NetworkGame.WINDOW_WIDTH / 2;
        int topY = 120;
        int nodeY = NetworkGame.WINDOW_HEIGHT / 2;
        int bottomY = NetworkGame.WINDOW_HEIGHT - 120;
        int sourceHOffset = 180;
        int sinkHOffset = 300;
        System source1 = new System(centerX - sourceHOffset - System.SYSTEM_WIDTH / 2, topY - System.SYSTEM_HEIGHT / 2, true);
        source1.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE);
        source1.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE);
        source1.configureGenerator(8, 1500);
        System source2 = new System(centerX + sourceHOffset - System.SYSTEM_WIDTH / 2, topY - System.SYSTEM_HEIGHT / 2, true);
        source2.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE);
        source2.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE);
        source2.configureGenerator(8, 1600);
        System nodeCenter = new System(centerX - System.SYSTEM_WIDTH / 2, nodeY - System.SYSTEM_HEIGHT / 2, false);
        nodeCenter.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE);
        nodeCenter.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE);
        nodeCenter.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE);
        nodeCenter.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE);
        nodeCenter.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE);
        nodeCenter.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE);
        nodeCenter.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE);
        nodeCenter.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE);
        System sink1 = new System(centerX - sinkHOffset - System.SYSTEM_WIDTH / 2, bottomY - System.SYSTEM_HEIGHT / 2, true);
        sink1.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE);
        sink1.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE);
        System sink2 = new System(centerX + sinkHOffset - System.SYSTEM_WIDTH / 2, bottomY - System.SYSTEM_HEIGHT / 2, true);
        sink2.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE);
        sink2.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE);
        systems.add(source1);
        systems.add(source2);
        systems.add(nodeCenter);
        systems.add(sink1);
        systems.add(sink2);
        java.lang.System.out.println("FINAL Level 2 systems created.");
    }
}