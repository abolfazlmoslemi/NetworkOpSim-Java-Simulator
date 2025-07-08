// ===== File: LevelLoader.java =====
// ===== File: LevelLoader.java (FINAL VERSION with DENSE and COMPLEX levels) =====

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
                    initializeLevel1(systems);
                    break;
                case 2:
                    initializeLevel2(systems);
                    break;
                case 3:
                    initializeLevel3(systems);
                    break;
                case 4:
                    initializeLevel4(systems);
                    break;
                case 5:
                    initializeLevel5(systems);
                    break;
                default:
                    java.lang.System.err.println("Warning: Invalid level number " + level + ". Loading Level 1 as fallback.");
                    gameState.setMaxWireLengthForLevel(getWireBudgetForLevel(1));
                    initializeLevel1(systems);
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
            case 2: return 32000;
            case 3: return 35000;
            case 4: return 38000;
            case 5: return 42000;
            default: return 25000;
        }
    }

    /**
     * مرحله ۱: شبکه مقدماتی شلوغ
     * هدف: آشنایی با تمام مفاهیم در یک شبکه با تراکم متوسط.
     * چالش: مسیرهای متقاطع، نیاز به استفاده از تمام سیستم‌ها.
     * ورودی/خروجی کل: ۱۱
     */
    private static void initializeLevel1(List<System> systems) {
        int w = NetworkGame.WINDOW_WIDTH;
        int h = NetworkGame.WINDOW_HEIGHT;
        int sysW = System.SYSTEM_WIDTH;

        // Sources (4 Outputs)
        systems.add(createSource(50, 80, NetworkEnums.PacketShape.SQUARE, 10, 2500, NetworkEnums.PacketType.NORMAL));
        systems.add(createSource(50, 230, NetworkEnums.PacketShape.TRIANGLE, 10, 3000, NetworkEnums.PacketType.MESSENGER));
        System s_secret = createSource(50, h - 230, NetworkEnums.PacketShape.CIRCLE, 8, 4000, NetworkEnums.PacketType.SECRET);
        s_secret.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.CIRCLE); // Total 2 circle outputs
        systems.add(s_secret);

        // Sinks (4 Outputs)
        systems.add(createSink(w - sysW - 50, 80, NetworkEnums.PortShape.SQUARE));
        systems.add(createSink(w - sysW - 50, 230, NetworkEnums.PortShape.TRIANGLE));
        System k_circle = createSink(w - sysW - 50, h-230, NetworkEnums.PortShape.CIRCLE);
        k_circle.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.CIRCLE);
        systems.add(k_circle);

        // Processing Systems (7 Inputs, 7 Outputs)
        System vpn = new System(250, h/2, NetworkEnums.SystemType.VPN);
        vpn.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE);
        vpn.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE);
        vpn.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE);
        vpn.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE);
        systems.add(vpn);

        System corruptor = new System(w/2 - sysW/2, 100, NetworkEnums.SystemType.CORRUPTOR);
        corruptor.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE);
        corruptor.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE);
        corruptor.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE);
        corruptor.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE);
        systems.add(corruptor);

        System spy1 = new System(w/2 - sysW/2, h - 150, NetworkEnums.SystemType.SPY);
        spy1.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.CIRCLE);
        spy1.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.CIRCLE);
        systems.add(spy1);

        System spy2 = new System(w-300, 150, NetworkEnums.SystemType.SPY);
        spy2.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.CIRCLE);
        spy2.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.CIRCLE);
        systems.add(spy2);

        System antiTrojan = new System(w - 300, h - 150, NetworkEnums.SystemType.ANTITROJAN);
        antiTrojan.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE);
        antiTrojan.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE);
        systems.add(antiTrojan);

        System distributor = new System(350, h-100, NetworkEnums.SystemType.DISTRIBUTOR);
        distributor.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.CIRCLE);
        distributor.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.CIRCLE);
        systems.add(distributor);

        System merger = new System(w-400, h/2, NetworkEnums.SystemType.MERGER);
        merger.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.CIRCLE);
        merger.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.CIRCLE);
        systems.add(merger);

        // Add dummy source/sink for bulk packets
        systems.add(createSource(50, h - 100, NetworkEnums.PacketShape.CIRCLE, 2, 12000, NetworkEnums.PacketType.BULK));
        systems.add(createSink(w-200, h/2, NetworkEnums.PortShape.CIRCLE));
    }

    /**
     * مرحله ۲: چالش مسیرهای موازی
     * هدف: بازیکن باید بین مسیرهای مختلف یکی را انتخاب کند. مثلا یک مسیر امن (VPN) و یک مسیر خطرناک (Corruptor).
     * چالش: مدیریت ترافیک و تقسیم بار بین دو مسیر اصلی.
     * ورودی/خروجی کل: ۱۵
     */
    private static void initializeLevel2(List<System> systems) {
        int w = NetworkGame.WINDOW_WIDTH;
        int h = NetworkGame.WINDOW_HEIGHT;
        int sysW = System.SYSTEM_WIDTH;

        // Sources (6 Outputs)
        System s1 = createSource(50, 100, NetworkEnums.PacketShape.SQUARE, 15, 2000, NetworkEnums.PacketType.NORMAL);
        s1.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE);
        systems.add(s1);
        System s2 = createSource(50, h/2, NetworkEnums.PacketShape.TRIANGLE, 15, 2200, NetworkEnums.PacketType.MESSENGER);
        s2.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE);
        systems.add(s2);
        systems.add(createSource(50, h - 200, NetworkEnums.PacketShape.CIRCLE, 4, 8000, NetworkEnums.PacketType.BULK));
        systems.add(createSource(w - sysW - 50, 50, NetworkEnums.PacketShape.CIRCLE, 10, 3000, NetworkEnums.PacketType.WOBBLE));

        // Sinks (6 Inputs)
        System k1 = createSink(w - sysW - 50, h - 200, NetworkEnums.PortShape.SQUARE);
        k1.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE);
        systems.add(k1);
        System k2 = createSink(w - sysW - 50, h/2, NetworkEnums.PortShape.TRIANGLE);
        k2.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE);
        systems.add(k2);
        systems.add(createSink(w-250, h - 100, NetworkEnums.PortShape.CIRCLE));
        systems.add(createSink(250, 100, NetworkEnums.PortShape.CIRCLE));

        // Processing Systems (9 Inputs, 9 Outputs)
        // Top Path (Dangerous)
        System corruptor1 = new System(w/2 - 200, 150, NetworkEnums.SystemType.CORRUPTOR);
        corruptor1.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE);
        corruptor1.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE);
        corruptor1.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE);
        corruptor1.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE);
        systems.add(corruptor1);
        System spy1 = new System(w/2 + 100, 150, NetworkEnums.SystemType.SPY);
        spy1.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE);
        spy1.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE);
        systems.add(spy1);

        // Bottom Path (Safe)
        System vpn1 = new System(w/2 - 200, h - 250, NetworkEnums.SystemType.VPN);
        vpn1.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE);
        vpn1.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE);
        vpn1.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE);
        vpn1.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE);
        systems.add(vpn1);
        System antiTrojan1 = new System(w/2 + 100, h - 250, NetworkEnums.SystemType.ANTITROJAN);
        antiTrojan1.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE);
        antiTrojan1.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE);
        systems.add(antiTrojan1);

        // Bulk Path
        System distributor = new System(250, h-100, NetworkEnums.SystemType.DISTRIBUTOR);
        distributor.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.CIRCLE);
        distributor.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.CIRCLE);
        systems.add(distributor);
        System merger = new System(w-450, h-100, NetworkEnums.SystemType.MERGER);
        merger.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.CIRCLE);
        merger.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.CIRCLE);
        systems.add(merger);

        // Secret/Wobble path
        System spy2 = new System(w-250, 250, NetworkEnums.SystemType.SPY);
        spy2.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.CIRCLE);
        spy2.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.CIRCLE);
        systems.add(spy2);
    }

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


    // Helper methods for creating systems
    private static System createSource(int x, int y, NetworkEnums.PacketShape shape, int count, int freq, NetworkEnums.PacketType type) {
        System source = new System(x, y, NetworkEnums.SystemType.SOURCE);
        source.addPort(NetworkEnums.PortType.OUTPUT, Port.getShapeEnum(shape));
        source.configureGenerator(count, freq, type);
        return source;
    }

    private static System createSink(int x, int y, NetworkEnums.PortShape shape) {
        System sink = new System(x, y, NetworkEnums.SystemType.SINK);
        sink.addPort(NetworkEnums.PortType.INPUT, shape);
        return sink;
    }
}