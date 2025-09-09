// ================================================================================
// FILE: LevelLoader.java (کد کامل و نهایی با رفع خطا)
// ================================================================================
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
                    initializeLevel3_Redesigned(systems);
                    break;
                case 4:
                    initializeLevel4_Central_Challenge(systems);
                    break;
                case 5:
                    // ===== متد بازگردانده شده برای رفع خطا =====
                    initializeLevel5(systems);
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
            case 1: return 4750;
            case 2: return 6500;
            case 3: return 21000;
            case 4: return 30000;
            case 5: return 42000;
            default: return 1000;
        }
    }

    // ===== متد نهایی و بازنویسی شده برای مرحله 4 =====
    private static void initializeLevel4_Central_Challenge(List<System> systems) {
        int w = NetworkGame.WINDOW_WIDTH;
        int h = NetworkGame.WINDOW_HEIGHT;
        int sysW = System.SYSTEM_WIDTH;
        int sysH = System.SYSTEM_HEIGHT;
        int centerX = w / 2 - sysW / 2;
        int centerY = h / 2 - sysH / 2;

        // --- هسته مرکزی: تمام مبدأها و مقصدها (تعداد و مکان ثابت) ---
        // Sources
        System sourceSquare = createSource(centerX - 100, centerY - 80, NetworkEnums.PacketShape.SQUARE, 25, 1500, NetworkEnums.PacketType.NORMAL);
        sourceSquare.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE); // 2 total output ports
        systems.add(sourceSquare);

        System sourceTriangle = createSource(centerX + 100, centerY - 80, NetworkEnums.PacketShape.TRIANGLE, 20, 2000, NetworkEnums.PacketType.MESSENGER);
        sourceTriangle.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE); // 2 total output ports
        systems.add(sourceTriangle);

        System sourceSecret = createSource(centerX - 100, centerY + 80, NetworkEnums.PacketShape.CIRCLE, 12, 3000, NetworkEnums.PacketType.SECRET);
        sourceSecret.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.CIRCLE); // 2 total output ports
        systems.add(sourceSecret);

        System sourceBulk = createSource(centerX + 100, centerY + 80, NetworkEnums.PacketShape.CIRCLE, 10, 4000, NetworkEnums.PacketType.BULK);
        sourceBulk.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.CIRCLE); // 2 total output ports
        systems.add(sourceBulk);

        // Sinks
        System sinkSquare = createSink(centerX, centerY - 120, NetworkEnums.PortShape.SQUARE);
        sinkSquare.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE); // 2 total input ports
        systems.add(sinkSquare);

        System sinkTriangle = createSink(centerX, centerY + 120, NetworkEnums.PortShape.TRIANGLE);
        sinkTriangle.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE); // 2 total input ports
        systems.add(sinkTriangle);

        System sinkCircle1 = createSink(centerX - 180, centerY, NetworkEnums.PortShape.CIRCLE);
        sinkCircle1.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.CIRCLE); // 2 total input ports
        systems.add(sinkCircle1);

        System sinkCircle2 = createSink(centerX + 180, centerY, NetworkEnums.PortShape.CIRCLE);
        sinkCircle2.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.CIRCLE); // 2 total input ports
        systems.add(sinkCircle2);

        // --- حلقه سیستم‌های پردازشی با تعداد مشخص شده ---
        // 4 Nodes
        System node1 = new System(w/4, h/4, NetworkEnums.SystemType.NODE);
        node1.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE); node1.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE);
        node1.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE); node1.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE);
        systems.add(node1);
        System node2 = new System(w*3/4 - sysW, h/4, NetworkEnums.SystemType.NODE);
        node2.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE); node2.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE);
        node2.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE); node2.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE);
        systems.add(node2);
        System node3 = new System(w/4, h*3/4 - sysH, NetworkEnums.SystemType.NODE);
        node3.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE); node3.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE);
        node3.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE); node3.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE);
        systems.add(node3);
        System node4 = new System(w*3/4 - sysW, h*3/4 - sysH, NetworkEnums.SystemType.NODE);
        node4.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE); node4.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE);
        node4.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE); node4.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE);
        systems.add(node4);

        // 3 Spies
        System spy1 = new System(50, centerY, NetworkEnums.SystemType.SPY);
        spy1.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.CIRCLE); spy1.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE);
        spy1.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.CIRCLE); spy1.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE);
        systems.add(spy1);
        System spy2 = new System(w-sysW-50, centerY, NetworkEnums.SystemType.SPY);
        spy2.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.CIRCLE); spy2.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE);
        spy2.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.CIRCLE); spy2.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE);
        systems.add(spy2);
        System spy3 = new System(centerX, 50, NetworkEnums.SystemType.SPY);
        spy3.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE);
        spy3.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE);
        systems.add(spy3);

        // 2 VPNs
        System vpn1 = new System(50, 50, NetworkEnums.SystemType.VPN);
        vpn1.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.CIRCLE); vpn1.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE);
        vpn1.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.CIRCLE); vpn1.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE);
        systems.add(vpn1);
        System vpn2 = new System(w-sysW-50, h-sysH-50, NetworkEnums.SystemType.VPN);
        vpn2.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE); vpn2.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.CIRCLE);
        vpn2.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE); vpn2.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.CIRCLE);
        systems.add(vpn2);

        // 3 Corruptors
        System corr1 = new System(w-sysW-50, 50, NetworkEnums.SystemType.CORRUPTOR);
        corr1.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE); corr1.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE);
        corr1.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE); corr1.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE);
        systems.add(corr1);
        System corr2 = new System(50, h-sysH-50, NetworkEnums.SystemType.CORRUPTOR);
        corr2.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE); corr2.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE);
        corr2.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE); corr2.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE);
        systems.add(corr2);
        System corr3 = new System(centerX, h-sysH-50, NetworkEnums.SystemType.CORRUPTOR);
        corr3.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.CIRCLE);
        corr3.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.CIRCLE);
        systems.add(corr3);

        // 2 Distributors
        System dist1 = new System(w/4, 50, NetworkEnums.SystemType.DISTRIBUTOR);
        dist1.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.CIRCLE);
        dist1.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.CIRCLE); dist1.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.CIRCLE);
        systems.add(dist1);
        System dist2 = new System(w*3/4 - sysW, 50, NetworkEnums.SystemType.DISTRIBUTOR);
        dist2.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.CIRCLE);
        dist2.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.CIRCLE); dist2.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.CIRCLE);
        systems.add(dist2);

        // 2 Mergers
        System merg1 = new System(w/4, h-sysH-50, NetworkEnums.SystemType.MERGER);
        merg1.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.CIRCLE); merg1.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.CIRCLE);
        merg1.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.CIRCLE);
        systems.add(merg1);
        System merg2 = new System(w*3/4 - sysW, h-sysH-50, NetworkEnums.SystemType.MERGER);
        merg2.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.CIRCLE); merg2.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.CIRCLE);
        merg2.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.CIRCLE);
        systems.add(merg2);

        // 1 AntiTrojan
        System antiT = new System(centerX, centerY, NetworkEnums.SystemType.ANTITROJAN);
        antiT.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE);
        antiT.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE);
        systems.add(antiT);
    }

    // ===== این متد برای رفع خطای کامپایل بازگردانده شده است =====
    private static void initializeLevel5(List<System> systems) {
        int w = NetworkGame.WINDOW_WIDTH;
        int h = NetworkGame.WINDOW_HEIGHT;
        int sysW = System.SYSTEM_WIDTH;
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

    private static void initializeExtremeLevel1Layout(List<System> systems) {
        java.lang.System.out.println("Loading EXTREME Level 1 Layout: 2 Sources -> 5 Nodes -> 2 Sinks (No ANY ports)");
        int panelWidth = NetworkGame.WINDOW_WIDTH;
        int panelHeight = NetworkGame.WINDOW_HEIGHT;
        int sysWidth = System.SYSTEM_WIDTH;
        int sysHeight = System.SYSTEM_HEIGHT;
        System sourceS1 = new System(panelWidth / 7, panelHeight / 5 - sysHeight / 2, true);
        sourceS1.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE);
        sourceS1.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE);
        sourceS1.configureGenerator(18, 1600);
        systems.add(sourceS1);
        System sourceT1 = new System(panelWidth * 6 / 7 - sysWidth, panelHeight / 5 - sysHeight/2, true);
        sourceT1.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE);
        sourceT1.configureGenerator(12, 1750);
        systems.add(sourceT1);
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
        System node3 = new System(panelWidth / 2 - sysWidth / 2, nodeYMid, false);
        node3.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE);
        node3.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE);
        node3.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE);
        node3.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE);
        node3.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE);
        node3.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE);
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
        System sourceS_A = new System(panelWidth / 8, panelHeight / 6 - sysHeight, true);
        sourceS_A.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE);
        sourceS_A.configureGenerator(25, 1300);
        systems.add(sourceS_A);
        System sourceM_B = new System(panelWidth / 2 - sysWidth / 2, panelHeight / 7 - sysHeight / 2, true);
        sourceM_B.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE);
        sourceM_B.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE);
        sourceM_B.configureGenerator(30, 1200);
        systems.add(sourceM_B);
        System sourceS_C = new System(panelWidth * 7 / 8 - sysWidth, panelHeight / 6 - sysHeight, true);
        sourceS_C.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE);
        sourceS_C.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE);
        sourceS_C.configureGenerator(28, 1350);
        systems.add(sourceS_C);
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
    private static void initializeLevel3_Redesigned(List<System> systems) {
        int w = NetworkGame.WINDOW_WIDTH;
        int h = NetworkGame.WINDOW_HEIGHT;
        int sysW = System.SYSTEM_WIDTH;
        System sourceSquare = new System(50, h / 2, NetworkEnums.SystemType.SOURCE);
        sourceSquare.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE);
        sourceSquare.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE);
        sourceSquare.configureGenerator(25, 2000, NetworkEnums.PacketType.NORMAL);
        sourceSquare.setPacketShapeToGenerate(NetworkEnums.PacketShape.SQUARE);
        systems.add(sourceSquare);
        System sourceTriangle = new System(50, h - 100, NetworkEnums.SystemType.SOURCE);
        sourceTriangle.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE);
        sourceTriangle.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE);
        sourceTriangle.configureGenerator(20, 2500, NetworkEnums.PacketType.NORMAL);
        sourceTriangle.setPacketShapeToGenerate(NetworkEnums.PacketShape.TRIANGLE);
        systems.add(sourceTriangle);
        System sourceMessenger = new System(50, 100, NetworkEnums.SystemType.SOURCE);
        sourceMessenger.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.CIRCLE);
        sourceMessenger.configureGenerator(15, 3000, NetworkEnums.PacketType.MESSENGER);
        sourceMessenger.setPacketShapeToGenerate(NetworkEnums.PacketShape.CIRCLE);
        systems.add(sourceMessenger);
        System sinkSquare = new System(w - sysW - 50, h / 2, NetworkEnums.SystemType.SINK);
        sinkSquare.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE);
        sinkSquare.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE);
        systems.add(sinkSquare);
        System sinkTriangle = new System(w - sysW - 50, h - 100, NetworkEnums.SystemType.SINK);
        sinkTriangle.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE);
        sinkTriangle.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE);
        systems.add(sinkTriangle);
        System sinkMessenger = new System(w - sysW - 50, 100, NetworkEnums.SystemType.SINK);
        sinkMessenger.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.CIRCLE);
        systems.add(sinkMessenger);
        System node1 = new System(w/4 - 40, 150, NetworkEnums.SystemType.NODE);
        node1.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE);
        node1.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.CIRCLE);
        node1.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE);
        node1.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.CIRCLE);
        systems.add(node1);
        System node2 = new System(w/4 - 40, h/2, NetworkEnums.SystemType.NODE);
        node2.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE);
        node2.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE);
        node2.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE);
        node2.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE);
        systems.add(node2);
        System node3 = new System(w/4 - 40, h-150, NetworkEnums.SystemType.NODE);
        node3.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE);
        node3.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE);
        node3.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE);
        systems.add(node3);
        System vpn = new System(w/2 - 80, 80, NetworkEnums.SystemType.VPN);
        vpn.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.CIRCLE);
        vpn.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.CIRCLE);
        systems.add(vpn);
        System corruptor1 = new System(w/2 - 80, 220, NetworkEnums.SystemType.CORRUPTOR);
        corruptor1.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE);
        corruptor1.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE);
        corruptor1.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE);
        systems.add(corruptor1);
        System spy1 = new System(w/2 - 80, h/2, NetworkEnums.SystemType.SPY);
        spy1.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE);
        spy1.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE);
        spy1.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE);
        spy1.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE);
        systems.add(spy1);
        System corruptor2 = new System(w/2 - 80, h-220, NetworkEnums.SystemType.CORRUPTOR);
        corruptor2.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE);
        corruptor2.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE);
        corruptor2.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE);
        corruptor2.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE);
        systems.add(corruptor2);
        System corruptor3 = new System(w*3/5, 150, NetworkEnums.SystemType.CORRUPTOR);
        corruptor3.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.CIRCLE);
        corruptor3.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE);
        corruptor3.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.CIRCLE);
        corruptor3.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE);
        systems.add(corruptor3);
        System antiTrojan = new System(w*3/5, h/2, NetworkEnums.SystemType.ANTITROJAN);
        antiTrojan.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE);
        antiTrojan.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE);
        antiTrojan.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE);
        antiTrojan.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE);
        antiTrojan.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.CIRCLE);
        systems.add(antiTrojan);
        System spy2 = new System(w*3/5, h-150, NetworkEnums.SystemType.SPY);
        spy2.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE);
        spy2.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE);
        spy2.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE);
        spy2.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE);
        systems.add(spy2);
        System node_final = new System(w*4/5, h/2, NetworkEnums.SystemType.NODE);
        node_final.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE);
        node_final.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE);
        node_final.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.CIRCLE);
        node_final.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE);
        node_final.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE);
        node_final.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.CIRCLE);
        systems.add(node_final);
        System balancingNode = new System(w-sysW-150, h-250, NetworkEnums.SystemType.NODE);
        balancingNode.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE);
        balancingNode.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE);
        balancingNode.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE);
        balancingNode.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.CIRCLE);
        balancingNode.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE);
        systems.add(balancingNode);
    }
    private static System createSource(int x, int y, NetworkEnums.PacketShape shape, int count, int freq, NetworkEnums.PacketType type) {
        System source = new System(x, y, NetworkEnums.SystemType.SOURCE);
        source.addPort(NetworkEnums.PortType.OUTPUT, Port.getShapeEnum(shape));
        source.configureGenerator(count, freq, type);
        source.setPacketShapeToGenerate(shape);
        return source;
    }
    private static System createSink(int x, int y, NetworkEnums.PortShape shape) {
        System sink = new System(x, y, NetworkEnums.SystemType.SINK);
        sink.addPort(NetworkEnums.PortType.INPUT, shape);
        return sink;
    }
}