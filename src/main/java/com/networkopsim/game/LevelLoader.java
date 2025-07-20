
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
                    initializeExtremeLevel1Layout(systems);
                    break;
                case 2:
                    initializeNightmareLevel2Layout(systems);
                    break;
                case 3:
                    initializeLevel3(systems); // From original file
                    break;
                case 4:
                    initializeLevel4(systems); // From original file
                    break;
                case 5:
                    initializeLevel5(systems); // From original file
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
            case 1: return 4750;   // Budget from the new challenging level 1
            case 2: return 6500;   // Budget from the new challenging level 2
            case 3: return 35000;  // Budget from the original level 3
            case 4: return 38000;  // Budget from the original level 4
            case 5: return 42000;  // Budget from the original level 5
            default: return 1000;  // Default fallback
        }
    }

    // =======================================================================
    // == NEW, CHALLENGING LEVELS 1 & 2
    // =======================================================================

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

    // =======================================================================
    // == ORIGINAL LEVELS 3, 4, 5
    // =======================================================================

    /**
     * مرحله ۳: گره مرکزی (Central Hub)
     * هدف: تمام ترافیک باید از یک یا دو گره مرکزی عبور کند.
     * چالش: مدیریت شدید ترافیک در نقاط گلوگاه (chokepoints) و جلوگیری از پر شدن صف‌ها.
     * ورودی/خروجی کل: ۱۸
     */
    private static void initializeLevel3(List<System> systems) {
        int w = NetworkGame.WINDOW_WIDTH;
        int h = NetworkGame.WINDOW_HEIGHT;
        int sysW = System.SYSTEM_WIDTH;

        // Sources (8 Outputs)
        systems.add(createSource(50, 50, NetworkEnums.PacketShape.SQUARE, 20, 1800, NetworkEnums.PacketType.NORMAL));
        systems.add(createSource(w - sysW - 50, 50, NetworkEnums.PacketShape.SQUARE, 20, 1800, NetworkEnums.PacketType.NORMAL));
        systems.add(createSource(50, h - 150, NetworkEnums.PacketShape.TRIANGLE, 15, 2500, NetworkEnums.PacketType.MESSENGER));
        systems.add(createSource(w - sysW - 50, h - 150, NetworkEnums.PacketShape.TRIANGLE, 15, 2500, NetworkEnums.PacketType.MESSENGER));
        System s_bulk = createSource(w/2 - sysW/2, 50, NetworkEnums.PacketShape.CIRCLE, 5, 8000, NetworkEnums.PacketType.BULK);
        s_bulk.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.CIRCLE);
        systems.add(s_bulk);
        System s_secret = createSource(w/2 - sysW/2, h - 150, NetworkEnums.PacketShape.CIRCLE, 10, 3500, NetworkEnums.PacketType.SECRET);
        s_secret.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.CIRCLE);
        systems.add(s_secret);

        // Sinks (8 Inputs)
        System k_sq = createSink(50, h/2, NetworkEnums.PortShape.SQUARE);
        k_sq.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE);
        systems.add(k_sq);
        System k_tr = createSink(w-sysW-50, h/2, NetworkEnums.PortShape.TRIANGLE);
        k_tr.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE);
        systems.add(k_tr);
        System k_c1 = createSink(w/2 - 200, h - 100, NetworkEnums.PortShape.CIRCLE);
        k_c1.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.CIRCLE);
        systems.add(k_c1);
        System k_c2 = createSink(w/2 + 200, h - 100, NetworkEnums.PortShape.CIRCLE);
        k_c2.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.CIRCLE);
        systems.add(k_c2);

        // Processing Systems (10 Inputs, 10 Outputs)
        int centerX = w/2 - sysW/2;
        int centerY = h/2 - 50;

        System hub1 = new System(centerX-150, centerY, NetworkEnums.SystemType.NODE);
        hub1.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE);
        hub1.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE);
        hub1.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE);
        hub1.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE);
        systems.add(hub1);

        System hub2 = new System(centerX+150, centerY, NetworkEnums.SystemType.NODE);
        hub2.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE);
        hub2.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE);
        hub2.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE);
        hub2.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE);
        systems.add(hub2);

        System distributor = new System(centerX, 150, NetworkEnums.SystemType.DISTRIBUTOR);
        distributor.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.CIRCLE);
        distributor.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.CIRCLE);
        distributor.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.CIRCLE);
        distributor.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.CIRCLE);
        systems.add(distributor);

        System vpn = new System(200, 200, NetworkEnums.SystemType.VPN);
        vpn.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE);
        vpn.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE);
        systems.add(vpn);

        System corruptor = new System(w-200-sysW, 200, NetworkEnums.SystemType.CORRUPTOR);
        corruptor.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE);
        corruptor.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE);
        systems.add(corruptor);

        System spy1 = new System(200, h - 250, NetworkEnums.SystemType.SPY);
        spy1.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.CIRCLE);
        spy1.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.CIRCLE);
        systems.add(spy1);

        System spy2 = new System(w-200-sysW, h - 250, NetworkEnums.SystemType.SPY);
        spy2.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.CIRCLE);
        spy2.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.CIRCLE);
        systems.add(spy2);
    }

    /**
     * مرحله ۴: شبکه نامتقارن
     * هدف: حل پازل مسیریابی در یک شبکه با طراحی نامنظم.
     * چالش: سیستم‌ها و منابع به صورت نامتقارن پخش شده‌اند و مسیرهای بهینه مشخص نیستند.
     * ورودی/خروجی کل: ۲۰
     */
    private static void initializeLevel4(List<System> systems) {
        int w = NetworkGame.WINDOW_WIDTH;
        int h = NetworkGame.WINDOW_HEIGHT;
        int sysW = System.SYSTEM_WIDTH;

        // Sources (10 Outputs)
        systems.add(createSource(50, 50, NetworkEnums.PacketShape.SQUARE, 20, 1500, NetworkEnums.PacketType.NORMAL));
        systems.add(createSource(50, 200, NetworkEnums.PacketShape.TRIANGLE, 20, 1600, NetworkEnums.PacketType.MESSENGER));
        systems.add(createSource(50, 350, NetworkEnums.PacketShape.CIRCLE, 10, 3000, NetworkEnums.PacketType.SECRET));
        systems.add(createSource(w - sysW - 50, 50, NetworkEnums.PacketShape.SQUARE, 20, 1500, NetworkEnums.PacketType.NORMAL));
        systems.add(createSource(w - sysW - 50, 200, NetworkEnums.PacketShape.TRIANGLE, 20, 1600, NetworkEnums.PacketType.MESSENGER));
        systems.add(createSource(w - sysW - 50, 350, NetworkEnums.PacketShape.CIRCLE, 10, 3000, NetworkEnums.PacketType.SECRET));
        System s_bulk_wobble = new System(w/2 - sysW/2, 50, NetworkEnums.SystemType.SOURCE);
        s_bulk_wobble.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.CIRCLE);
        s_bulk_wobble.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.CIRCLE);
        s_bulk_wobble.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.CIRCLE);
        s_bulk_wobble.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.CIRCLE);
        s_bulk_wobble.configureGenerator(10, 4000, NetworkEnums.PacketType.WOBBLE); // Also generates bulk
        systems.add(s_bulk_wobble);


        // Sinks (10 Inputs)
        systems.add(createSink(50, h-100, NetworkEnums.PortShape.SQUARE));
        systems.add(createSink(200, h-100, NetworkEnums.PortShape.TRIANGLE));
        systems.add(createSink(350, h-100, NetworkEnums.PortShape.CIRCLE));
        systems.add(createSink(w - sysW - 50, h-100, NetworkEnums.PortShape.SQUARE));
        systems.add(createSink(w - sysW - 200, h-100, NetworkEnums.PortShape.TRIANGLE));
        systems.add(createSink(w - sysW - 350, h-100, NetworkEnums.PortShape.CIRCLE));
        System k_multi = new System(w/2 - sysW/2, h - 100, NetworkEnums.SystemType.SINK);
        k_multi.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE);
        k_multi.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE);
        k_multi.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.CIRCLE);
        k_multi.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.CIRCLE);
        systems.add(k_multi);

        // Processing Systems (10 Inputs, 10 Outputs)
        System node1 = new System(250, 250, NetworkEnums.SystemType.NODE);
        node1.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE);
        node1.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE);
        systems.add(node1);
        System vpn = new System(250, 400, NetworkEnums.SystemType.VPN);
        vpn.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE);
        vpn.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE);
        vpn.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE);
        vpn.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE);
        systems.add(vpn);

        System node2 = new System(w-sysW-250, 250, NetworkEnums.SystemType.NODE);
        node2.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE);
        node2.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE);
        systems.add(node2);
        System corruptor = new System(w-sysW-250, 400, NetworkEnums.SystemType.CORRUPTOR);
        corruptor.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE);
        corruptor.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE);
        corruptor.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE);
        corruptor.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE);
        systems.add(corruptor);

        System spy1 = new System(350, 150, NetworkEnums.SystemType.SPY);
        spy1.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.CIRCLE);
        spy1.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.CIRCLE);
        systems.add(spy1);
        System spy2 = new System(w-sysW-350, 150, NetworkEnums.SystemType.SPY);
        spy2.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.CIRCLE);
        spy2.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.CIRCLE);
        systems.add(spy2);
    }

    /**
     * مرحله ۵: هرج و مرج مطلق
     * هدف: آزمون نهایی بازیکن در شلوغ‌ترین حالت ممکن.
     * چالش: تعداد بسیار زیاد سیستم و پورت. مسیرها به شدت در هم تنیده هستند.
     * مدیریت همزمان انواع مختلف ترافیک با سرعت بالا ضروری است.
     * ورودی/خروجی کل: ۲۵
     */
    private static void initializeLevel5(List<System> systems) {
        int w = NetworkGame.WINDOW_WIDTH;
        int h = NetworkGame.WINDOW_HEIGHT;
        int sysW = System.SYSTEM_WIDTH;

        // Sources (12 Outputs)
        int srcX1 = 50, srcX2 = 200;
        int srcY1 = 50, srcY2 = 150, srcY3 = 250, srcY4 = 350;
        systems.add(createSource(srcX1, srcY1, NetworkEnums.PacketShape.SQUARE, 15, 1800, NetworkEnums.PacketType.NORMAL));
        systems.add(createSource(srcX1, srcY2, NetworkEnums.PacketShape.SQUARE, 15, 1800, NetworkEnums.PacketType.NORMAL));
        systems.add(createSource(srcX1, srcY3, NetworkEnums.PacketShape.TRIANGLE, 15, 2000, NetworkEnums.PacketType.MESSENGER));
        systems.add(createSource(srcX1, srcY4, NetworkEnums.PacketShape.TRIANGLE, 15, 2000, NetworkEnums.PacketType.MESSENGER));
        systems.add(createSource(srcX2, srcY1, NetworkEnums.PacketShape.CIRCLE, 10, 3000, NetworkEnums.PacketType.SECRET));
        systems.add(createSource(srcX2, srcY2, NetworkEnums.PacketShape.CIRCLE, 10, 3000, NetworkEnums.PacketType.SECRET));
        System s_multi = new System(125, h-150, NetworkEnums.SystemType.SOURCE);
        s_multi.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.CIRCLE);
        s_multi.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.CIRCLE);
        s_multi.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.CIRCLE);
        s_multi.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.CIRCLE);
        s_multi.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.CIRCLE);
        s_multi.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.CIRCLE);
        s_multi.configureGenerator(20, 2000, NetworkEnums.PacketType.WOBBLE);
        systems.add(s_multi);


        // Sinks (12 Inputs)
        int sinkX1 = w-sysW-50, sinkX2 = w-sysW-200;
        systems.add(createSink(sinkX1, srcY1, NetworkEnums.PortShape.SQUARE));
        systems.add(createSink(sinkX1, srcY2, NetworkEnums.PortShape.SQUARE));
        systems.add(createSink(sinkX1, srcY3, NetworkEnums.PortShape.TRIANGLE));
        systems.add(createSink(sinkX1, srcY4, NetworkEnums.PortShape.TRIANGLE));
        systems.add(createSink(sinkX2, srcY1, NetworkEnums.PortShape.CIRCLE));
        systems.add(createSink(sinkX2, srcY2, NetworkEnums.PortShape.CIRCLE));
        System k_multi = new System(w-125-sysW, h-150, NetworkEnums.SystemType.SINK);
        k_multi.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.CIRCLE);
        k_multi.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.CIRCLE);
        k_multi.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.CIRCLE);
        k_multi.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.CIRCLE);
        k_multi.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.CIRCLE);
        k_multi.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.CIRCLE);
        systems.add(k_multi);

        // Processing Systems (13 Inputs, 13 Outputs)
        int centerX = w/2 - sysW/2;
        int cY1 = 150, cY2 = h/2, cY3 = h-200;

        System vpn = new System(centerX-200, cY1, NetworkEnums.SystemType.VPN);
        vpn.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE);
        vpn.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE);
        vpn.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE);
        vpn.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE);
        systems.add(vpn);

        System corruptor = new System(centerX+200, cY1, NetworkEnums.SystemType.CORRUPTOR);
        corruptor.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE);
        corruptor.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE);
        corruptor.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE);
        corruptor.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE);
        systems.add(corruptor);

        System antiTrojan = new System(centerX, cY2, NetworkEnums.SystemType.ANTITROJAN);
        antiTrojan.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE);
        antiTrojan.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE);
        antiTrojan.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE);
        antiTrojan.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE);
        systems.add(antiTrojan);

        System distributor = new System(centerX-200, cY3, NetworkEnums.SystemType.DISTRIBUTOR);
        distributor.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.CIRCLE);
        distributor.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.CIRCLE);
        distributor.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.CIRCLE);
        distributor.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.CIRCLE);
        systems.add(distributor);

        System merger = new System(centerX+200, cY3, NetworkEnums.SystemType.MERGER);
        merger.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.CIRCLE);
        merger.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.CIRCLE);
        merger.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.CIRCLE);
        merger.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.CIRCLE);
        systems.add(merger);

        System spy1 = new System(centerX, 50, NetworkEnums.SystemType.SPY);
        spy1.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.CIRCLE);
        spy1.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.CIRCLE);
        systems.add(spy1);
        System spy2 = new System(centerX, h-100, NetworkEnums.SystemType.SPY);
        spy2.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.CIRCLE);
        spy2.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.CIRCLE);
        systems.add(spy2);
    }


    // =======================================================================
    // == HELPER METHODS (Required for Levels 3, 4, 5)
    // =======================================================================

    private static System createSource(int x, int y, NetworkEnums.PacketShape shape, int count, int freq, NetworkEnums.PacketType type) {
        System source = new System(x, y, NetworkEnums.SystemType.SOURCE);
        source.addPort(NetworkEnums.PortType.OUTPUT, Port.getShapeEnum(shape));
        source.configureGenerator(count, freq, type);
        // ========== تغییر اصلی اینجاست ==========
        // شکل بسته تولیدی را به صراحت برای سیستم مشخص می‌کنیم
        source.setPacketShapeToGenerate(shape);
        // =====================================
        return source;
    }

    private static System createSink(int x, int y, NetworkEnums.PortShape shape) {
        System sink = new System(x, y, NetworkEnums.SystemType.SINK);
        sink.addPort(NetworkEnums.PortType.INPUT, shape);
        return sink;
    }
}