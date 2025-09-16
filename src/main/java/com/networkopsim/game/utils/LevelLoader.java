// ===== File: LevelLoader.java (Final Corrected with All Levels 1-6) =====

package com.networkopsim.game.utils;

import com.networkopsim.game.controller.core.NetworkGame;
import com.networkopsim.game.model.core.Port;
import com.networkopsim.game.model.core.System;
import com.networkopsim.game.model.core.Wire;
import com.networkopsim.game.model.enums.NetworkEnums;
import com.networkopsim.game.model.state.GameState;

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
                case 6:
                    initializeLevel6(systems);
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
            case 1: return 4040;
            case 2: return 6380;
            case 3: return 9999;
            case 4: return 30000;
            case 5: return 55000;
            case 6: return 10000;
            default: return 1000;
        }
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

    // [CORRECTED] Added the full, correct implementations for levels 1-5.
    private static void initializeLevel1(List<System> systems) {
        int panelWidth = NetworkGame.WINDOW_WIDTH; int panelHeight = NetworkGame.WINDOW_HEIGHT; int sysWidth = System.SYSTEM_WIDTH; int sysHeight = System.SYSTEM_HEIGHT;
        System sourceS1 = new System(panelWidth / 7, panelHeight / 5 - sysHeight / 2, NetworkEnums.SystemType.SOURCE);
        sourceS1.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE); sourceS1.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE);
        sourceS1.configureGenerator(18, 1600, NetworkEnums.PacketType.NORMAL); systems.add(sourceS1);
        System sourceT1 = new System(panelWidth * 6 / 7 - sysWidth, panelHeight / 5 - sysHeight/2, NetworkEnums.SystemType.SOURCE);
        sourceT1.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE);
        sourceT1.configureGenerator(12, 1750, NetworkEnums.PacketType.NORMAL); systems.add(sourceT1);
        int nodeYTop = panelHeight / 3 - sysHeight; int nodeYMid = panelHeight / 2 - sysHeight / 2; int nodeYBot = panelHeight * 2 / 3;
        System node1 = new System(panelWidth / 4 - sysWidth, nodeYTop, NetworkEnums.SystemType.NODE);
        node1.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE); node1.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE); node1.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE); node1.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE); systems.add(node1);
        System node2 = new System(panelWidth * 3 / 4, nodeYTop, NetworkEnums.SystemType.NODE);
        node2.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE); node2.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE); node2.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE); node2.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE); systems.add(node2);
        System node3 = new System(panelWidth / 2 - sysWidth / 2, nodeYMid, NetworkEnums.SystemType.NODE);
        node3.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE); node3.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE); node3.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE); node3.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE); node3.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE); node3.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE); systems.add(node3);
        System node4 = new System(panelWidth / 4 - sysWidth, nodeYBot, NetworkEnums.SystemType.NODE);
        node4.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE); node4.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE); node4.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE); node4.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE); systems.add(node4);
        System node5 = new System(panelWidth * 3 / 4, nodeYBot, NetworkEnums.SystemType.NODE);
        node5.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE); node5.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE); node5.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE); node5.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE); systems.add(node5);
        System sinkS1 = new System(panelWidth / 7, panelHeight * 4 / 5 - sysHeight/2, NetworkEnums.SystemType.SINK);
        sinkS1.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE); sinkS1.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE); systems.add(sinkS1);
        System sinkT1 = new System(panelWidth * 6 / 7 - sysWidth, panelHeight * 4 / 5 - sysHeight/2, NetworkEnums.SystemType.SINK);
        sinkT1.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE); systems.add(sinkT1);
    }
    private static void initializeLevel2(List<System> systems) {
        int panelWidth = NetworkGame.WINDOW_WIDTH; int panelHeight = NetworkGame.WINDOW_HEIGHT; int sysWidth = System.SYSTEM_WIDTH; int sysHeight = System.SYSTEM_HEIGHT;
        System sourceS_A = new System(panelWidth / 8, panelHeight / 6 - sysHeight, NetworkEnums.SystemType.SOURCE);
        sourceS_A.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE); sourceS_A.configureGenerator(25, 1300, NetworkEnums.PacketType.NORMAL); systems.add(sourceS_A);
        System sourceM_B = new System(panelWidth / 2 - sysWidth / 2, panelHeight / 7 - sysHeight / 2, NetworkEnums.SystemType.SOURCE);
        sourceM_B.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE); sourceM_B.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE); sourceM_B.configureGenerator(30, 1200, NetworkEnums.PacketType.NORMAL); systems.add(sourceM_B);
        System sourceS_C = new System(panelWidth * 7 / 8 - sysWidth, panelHeight / 6 - sysHeight, NetworkEnums.SystemType.SOURCE);
        sourceS_C.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE); sourceS_C.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE); sourceS_C.configureGenerator(28, 1350, NetworkEnums.PacketType.NORMAL); systems.add(sourceS_C);
        System node1 = new System(panelWidth / 5 - sysWidth, panelHeight / 4 - sysHeight / 2, NetworkEnums.SystemType.NODE);
        node1.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE); node1.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE); node1.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE); node1.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE); systems.add(node1);
        System node2 = new System(panelWidth * 2 / 5 - sysWidth / 2, panelHeight / 4 - sysHeight / 2 + 20, NetworkEnums.SystemType.NODE);
        node2.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE); node2.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE); node2.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE); node2.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE); systems.add(node2);
        System node3 = new System(panelWidth * 3 / 5 - sysWidth / 2, panelHeight / 4 - sysHeight / 2 - 20, NetworkEnums.SystemType.NODE);
        node3.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE); node3.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE); node3.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE); node3.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE); systems.add(node3);
        System node4 = new System(panelWidth * 4 / 5, panelHeight / 4 - sysHeight / 2, NetworkEnums.SystemType.NODE);
        node4.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE); node4.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE); node4.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE); node4.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE); systems.add(node4);
        System node5_center = new System(panelWidth / 2 - sysWidth / 2, panelHeight / 2 - sysHeight - 20, NetworkEnums.SystemType.NODE);
        node5_center.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE); node5_center.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE); node5_center.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE); node5_center.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE); node5_center.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE); node5_center.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE); node5_center.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE); node5_center.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE); systems.add(node5_center);
        System node6 = new System(panelWidth / 3 - sysWidth, panelHeight * 3 / 4 - sysHeight / 2, NetworkEnums.SystemType.NODE);
        node6.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE); node6.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE); node6.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE); node6.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE); systems.add(node6);
        System node7 = new System(panelWidth * 2 / 3, panelHeight * 3 / 4 - sysHeight / 2, NetworkEnums.SystemType.NODE);
        node7.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE); node7.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE); node7.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE); node7.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE); systems.add(node7);
        System sinkS_A_End = new System(panelWidth / 8, panelHeight * 5 / 6, NetworkEnums.SystemType.SINK);
        sinkS_A_End.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE); sinkS_A_End.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE); systems.add(sinkS_A_End);
        System sinkM_B_End = new System(panelWidth / 2 - sysWidth / 2, panelHeight * 6 / 7, NetworkEnums.SystemType.SINK);
        sinkM_B_End.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE); sinkM_B_End.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE); systems.add(sinkM_B_End);
        System sinkS_C_End = new System(panelWidth * 7 / 8 - sysWidth, panelHeight * 5 / 6, NetworkEnums.SystemType.SINK);
        sinkS_C_End.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE); systems.add(sinkS_C_End);
    }
    private static void initializeLevel3(List<System> systems) {
        int w = NetworkGame.WINDOW_WIDTH; int h = NetworkGame.WINDOW_HEIGHT; int sysW = System.SYSTEM_WIDTH;
        System sourceSquare = new System(50, h / 2, NetworkEnums.SystemType.SOURCE);
        sourceSquare.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.CIRCLE); sourceSquare.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.CIRCLE); sourceSquare.configureGenerator(25, 2000, NetworkEnums.PacketType.MESSENGER); sourceSquare.setPacketShapeToGenerate(NetworkEnums.PacketShape.CIRCLE); systems.add(sourceSquare);
        System sourceTriangle = new System(50, h - 100, NetworkEnums.SystemType.SOURCE);
        sourceTriangle.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.CIRCLE); sourceTriangle.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.CIRCLE); sourceTriangle.configureGenerator(20, 2500, NetworkEnums.PacketType.MESSENGER); sourceTriangle.setPacketShapeToGenerate(NetworkEnums.PacketShape.CIRCLE); systems.add(sourceTriangle);
        System sourceMessenger = new System(50, 100, NetworkEnums.SystemType.SOURCE);
        sourceMessenger.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.CIRCLE); sourceMessenger.configureGenerator(15, 3000, NetworkEnums.PacketType.MESSENGER); sourceMessenger.setPacketShapeToGenerate(NetworkEnums.PacketShape.CIRCLE); systems.add(sourceMessenger);
        System sinkSquare = new System(w - sysW - 50, h / 2, NetworkEnums.SystemType.SINK);
        sinkSquare.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE); sinkSquare.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE); systems.add(sinkSquare);
        System sinkTriangle = new System(w - sysW - 50, h - 100, NetworkEnums.SystemType.SINK);
        sinkTriangle.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE); sinkTriangle.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE); systems.add(sinkTriangle);
        System sinkMessenger = new System(w - sysW - 50, 100, NetworkEnums.SystemType.SINK);
        sinkMessenger.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.CIRCLE); systems.add(sinkMessenger);
        System system6 = new System(w/4 - 40, 150, NetworkEnums.SystemType.CORRUPTOR);
        system6.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE); system6.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.CIRCLE); system6.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE); system6.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.CIRCLE); systems.add(system6);
        System system7 = new System(w/4 - 40, h/2, NetworkEnums.SystemType.VPN);
        system7.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE); system7.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE); system7.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE); system7.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE); systems.add(system7);
        System system8 = new System(w/4 - 40, h-150, NetworkEnums.SystemType.VPN);
        system8.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE); system8.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE); system8.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE); systems.add(system8);
        System vpn = new System(w/2 - 80, 80, NetworkEnums.SystemType.VPN);
        vpn.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.CIRCLE); vpn.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.CIRCLE); systems.add(vpn);
        System corruptor1 = new System(w/2 - 80, 220, NetworkEnums.SystemType.CORRUPTOR);
        corruptor1.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE); corruptor1.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE); corruptor1.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE); systems.add(corruptor1);
        System spy1 = new System(w/2 - 80, h/2, NetworkEnums.SystemType.SPY);
        spy1.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE); spy1.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE); spy1.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE); spy1.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE); systems.add(spy1);
        System corruptor2 = new System(w/2 - 80, h-220, NetworkEnums.SystemType.CORRUPTOR);
        corruptor2.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE); corruptor2.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE); corruptor2.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE); corruptor2.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE); systems.add(corruptor2);
        System corruptor3 = new System(w*3/5, 150, NetworkEnums.SystemType.CORRUPTOR);
        corruptor3.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.CIRCLE); corruptor3.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE); corruptor3.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.CIRCLE); corruptor3.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE); systems.add(corruptor3);
        System antiTrojan = new System(w*3/5, h/2, NetworkEnums.SystemType.ANTITROJAN);
        antiTrojan.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE); antiTrojan.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE); antiTrojan.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE); antiTrojan.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE); antiTrojan.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.CIRCLE); systems.add(antiTrojan);
        System spy2 = new System(w*3/5, h-150, NetworkEnums.SystemType.SPY);
        spy2.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE); spy2.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE); spy2.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE); spy2.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE); systems.add(spy2);
        System balancingNode = new System(w-sysW-150, h-250, NetworkEnums.SystemType.NODE);
        balancingNode.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE); balancingNode.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE); balancingNode.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE); balancingNode.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.CIRCLE); balancingNode.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE); systems.add(balancingNode);
    }
    private static void initializeLevel4(List<System> systems) {
        int w = NetworkGame.WINDOW_WIDTH; int h = NetworkGame.WINDOW_HEIGHT; int sysW = System.SYSTEM_WIDTH; int sectionX1 = 180, sectionX2 = 400, sectionX3 = 650, sectionX4 = 900;
        systems.add(createSource(50, 100, NetworkEnums.PacketShape.CIRCLE, 10, 5000, NetworkEnums.PacketType.BULK));
        systems.add(createSource(50, h / 2 - 80, NetworkEnums.PacketShape.SQUARE, 25, 1800, NetworkEnums.PacketType.NORMAL));
        systems.add(createSource(50, h / 2 + 80, NetworkEnums.PacketShape.TRIANGLE, 20, 2200, NetworkEnums.PacketType.MESSENGER));
        systems.add(createSource(50, h - 150, NetworkEnums.PacketShape.CIRCLE, 12, 3500, NetworkEnums.PacketType.SECRET));
        systems.add(createSink(w - sysW - 50, 100, NetworkEnums.PortShape.CIRCLE));
        systems.add(createSink(w - sysW - 50, h / 2 - 80, NetworkEnums.PortShape.SQUARE));
        systems.add(createSink(w - sysW - 50, h / 2 + 80, NetworkEnums.PortShape.TRIANGLE));
        systems.add(createSink(w - sysW - 50, h - 150, NetworkEnums.PortShape.CIRCLE));
        System distributor1 = new System(sectionX1, 50, NetworkEnums.SystemType.DISTRIBUTOR);
        distributor1.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.CIRCLE); distributor1.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.CIRCLE); distributor1.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.CIRCLE); systems.add(distributor1);
        System distributor2 = new System(sectionX1, 150, NetworkEnums.SystemType.DISTRIBUTOR);
        distributor2.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.CIRCLE); distributor2.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.CIRCLE); distributor2.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.CIRCLE); systems.add(distributor2);
        System corruptor1 = new System(sectionX1, h / 2 - 40, NetworkEnums.SystemType.CORRUPTOR);
        corruptor1.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE); corruptor1.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE); corruptor1.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE); corruptor1.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE); systems.add(corruptor1);
        System corruptor2 = new System(sectionX1, h - 200, NetworkEnums.SystemType.CORRUPTOR);
        corruptor2.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE); corruptor2.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.CIRCLE); corruptor2.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE); corruptor2.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.CIRCLE); systems.add(corruptor2);
        System antiTrojan1 = new System(sectionX2, h / 2 - 150, NetworkEnums.SystemType.ANTITROJAN);
        antiTrojan1.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE); antiTrojan1.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE); systems.add(antiTrojan1);
        System antiTrojan2 = new System(sectionX2, h / 2 + 50, NetworkEnums.SystemType.ANTITROJAN);
        antiTrojan2.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE); antiTrojan2.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE); systems.add(antiTrojan2);
        System vpn1 = new System(sectionX2, 50, NetworkEnums.SystemType.VPN);
        vpn1.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE); vpn1.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE); vpn1.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE); systems.add(vpn1);
        System vpn2 = new System(sectionX2, h - 100, NetworkEnums.SystemType.VPN);
        vpn2.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.CIRCLE); vpn2.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.CIRCLE); systems.add(vpn2);
        System nodeHub1 = new System(sectionX2 + 100, h / 2 - 40, NetworkEnums.SystemType.NODE);
        nodeHub1.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE); nodeHub1.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE); nodeHub1.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE); nodeHub1.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE); nodeHub1.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.CIRCLE); systems.add(nodeHub1);
        System nodeHub2 = new System(sectionX2 - 100, h / 2 + 150, NetworkEnums.SystemType.NODE);
        nodeHub2.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.CIRCLE); nodeHub2.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE); nodeHub2.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.CIRCLE); nodeHub2.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE); systems.add(nodeHub2);
        int spy1Y = h / 2 - 80; System spy1 = new System(sectionX3, spy1Y, NetworkEnums.SystemType.SPY);
        spy1.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE); spy1.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE); spy1.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE); spy1.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE); systems.add(spy1);
        int spy2Y = h - 150; System spy2 = new System(sectionX3, spy2Y, NetworkEnums.SystemType.SPY);
        spy2.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.CIRCLE); spy2.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.CIRCLE); spy2.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE); systems.add(spy2);
        System newSource1 = new System(sectionX3, spy1Y - 150, NetworkEnums.SystemType.SOURCE);
        newSource1.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE); newSource1.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE); newSource1.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.CIRCLE); newSource1.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE);
        newSource1.configureGenerator(-1, 3300, NetworkEnums.PacketType.MESSENGER);
        newSource1.setPacketShapeToGenerate(NetworkEnums.PacketShape.CIRCLE);
        systems.add(newSource1);
        System newSource2 = new System(sectionX3, spy2Y - 150, NetworkEnums.SystemType.SOURCE);
        newSource2.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.CIRCLE); newSource2.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE); newSource2.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE); newSource2.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.CIRCLE); newSource2.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE);
        newSource2.configureGenerator(-1, 2900, NetworkEnums.PacketType.MESSENGER);
        newSource2.setPacketShapeToGenerate(NetworkEnums.PacketShape.CIRCLE);
        systems.add(newSource2);
        System merger1 = new System(sectionX4, 80, NetworkEnums.SystemType.MERGER);
        merger1.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.CIRCLE); merger1.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.CIRCLE); merger1.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.CIRCLE); systems.add(merger1);
        System merger2 = new System(sectionX4, 180, NetworkEnums.SystemType.MERGER);
        merger2.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.CIRCLE); merger2.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.CIRCLE); merger2.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.CIRCLE); systems.add(merger2);
    }
    private static void initializeLevel5(List<System> systems) {
        int w = NetworkGame.WINDOW_WIDTH; int h = NetworkGame.WINDOW_HEIGHT; int sysW = System.SYSTEM_WIDTH; int sysH = System.SYSTEM_HEIGHT; int section_width = w / 5;
        systems.add(createSource(30, h / 2 - 150, NetworkEnums.PacketShape.SQUARE, 20, 2200, NetworkEnums.PacketType.NORMAL));
        systems.add(createSource(30, h / 2, NetworkEnums.PacketShape.TRIANGLE, 20, 2400, NetworkEnums.PacketType.NORMAL));
        systems.add(createSource(30, h / 2 + 150, NetworkEnums.PacketShape.CIRCLE, 10, 3500, NetworkEnums.PacketType.SECRET));
        systems.add(createSource(120, 50, NetworkEnums.PacketShape.CIRCLE, 15, 3000, NetworkEnums.PacketType.MESSENGER));
        systems.add(createSource(120, h - 100 - sysH, NetworkEnums.PacketShape.CIRCLE, 5, 8000, NetworkEnums.PacketType.BULK));
        systems.add(createSource(30, 50, NetworkEnums.PacketShape.CIRCLE, 8, 5000, NetworkEnums.PacketType.WOBBLE));
        systems.add(createSink(w - sysW - 30, h / 2 - 100, NetworkEnums.PortShape.SQUARE));
        systems.add(createSink(w - sysW - 30, h / 2 + 100, NetworkEnums.PortShape.TRIANGLE));
        systems.add(createSink(w - sysW - 30, 50, NetworkEnums.PortShape.CIRCLE));
        systems.add(createSink(w - sysW - 30, h - 100 - sysH, NetworkEnums.PortShape.CIRCLE));
        System corr1 = new System(section_width, 100, NetworkEnums.SystemType.CORRUPTOR);
        corr1.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE); corr1.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE); corr1.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE); corr1.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE); systems.add(corr1);
        System distributor = new System(section_width, h / 2 - 20, NetworkEnums.SystemType.DISTRIBUTOR);
        distributor.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.CIRCLE); distributor.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.CIRCLE); distributor.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.CIRCLE); systems.add(distributor);
        System new_corr_1 = new System(section_width, h - 150 - sysH, NetworkEnums.SystemType.CORRUPTOR);
        new_corr_1.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.CIRCLE); new_corr_1.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE); new_corr_1.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.CIRCLE); systems.add(new_corr_1);
        int spy_section_x = section_width * 2;
        System spy1 = new System(spy_section_x, h / 2 - 250, NetworkEnums.SystemType.SPY);
        spy1.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE); spy1.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE); spy1.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE); spy1.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE); spy1.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.CIRCLE); systems.add(spy1);
        System spy2 = new System(spy_section_x, h / 2 + 150, NetworkEnums.SystemType.SPY);
        spy2.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.CIRCLE); spy2.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE); spy2.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.CIRCLE); spy2.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE); systems.add(spy2);
        System spy3 = new System(spy_section_x + 80, 50, NetworkEnums.SystemType.SPY);
        spy3.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE); spy3.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE); spy3.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE); spy3.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE); systems.add(spy3);
        System spy4 = new System(spy_section_x + 80, h - 80 - sysH, NetworkEnums.SystemType.SPY);
        spy4.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.CIRCLE); spy4.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE); spy4.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.CIRCLE); spy4.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE); systems.add(spy4);
        System spy5 = new System(spy_section_x + 160, h/2 - 100, NetworkEnums.SystemType.SPY);
        spy5.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE); spy5.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.CIRCLE); spy5.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE); spy5.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.CIRCLE); systems.add(spy5);
        System spy6 = new System(spy_section_x + 160, h/2 + 100, NetworkEnums.SystemType.SPY);
        spy6.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE); spy6.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.CIRCLE); spy6.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE); spy6.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.CIRCLE); systems.add(spy6);
        int section4_x = section_width * 3 + 100;
        System antiTrojan = new System(section4_x, 50, NetworkEnums.SystemType.ANTITROJAN);
        antiTrojan.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE); antiTrojan.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE); antiTrojan.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE); antiTrojan.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE); systems.add(antiTrojan);
        System vpn1 = new System(section4_x, h / 2 - 100, NetworkEnums.SystemType.VPN);
        vpn1.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE); vpn1.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.CIRCLE); vpn1.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE); vpn1.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.CIRCLE); systems.add(vpn1);
        System merger = new System(section4_x, h - 120 - sysH, NetworkEnums.SystemType.MERGER);
        merger.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.CIRCLE); merger.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.CIRCLE); merger.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.CIRCLE); merger.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.CIRCLE); systems.add(merger);
        System vpn2 = new System(section_width + 150, h - 150 - sysH, NetworkEnums.SystemType.VPN);
        vpn2.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE); vpn2.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE); systems.add(vpn2);
        System new_merger = new System(section_width * 3, h/2 + 200, NetworkEnums.SystemType.MERGER);
        new_merger.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.CIRCLE); new_merger.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.CIRCLE); new_merger.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.CIRCLE); systems.add(new_merger);
        System new_antitrojan = new System(section_width * 3, 200, NetworkEnums.SystemType.ANTITROJAN);
        new_antitrojan.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE); new_antitrojan.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE); new_antitrojan.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE); systems.add(new_antitrojan);
        int final_nodes_x = section_width * 4 + 50;
        System finalNode1 = new System(final_nodes_x, h/2 - 200, NetworkEnums.SystemType.NODE);
        finalNode1.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE); finalNode1.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.CIRCLE); finalNode1.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE); finalNode1.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.CIRCLE); systems.add(finalNode1);
        System finalNode2 = new System(final_nodes_x, h/2, NetworkEnums.SystemType.NODE);
        finalNode2.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.SQUARE); finalNode2.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE); finalNode2.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE); finalNode2.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE); systems.add(finalNode2);
        System new_corr_2 = new System(final_nodes_x - 120, h/2 + 120, NetworkEnums.SystemType.CORRUPTOR);
        new_corr_2.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE); new_corr_2.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.CIRCLE); new_corr_2.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE); new_corr_2.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.CIRCLE); systems.add(new_corr_2);
        System finalNode3 = new System(section_width + 150, h/2 - 50, NetworkEnums.SystemType.NODE);
        finalNode3.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.TRIANGLE); finalNode3.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.CIRCLE); finalNode3.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.TRIANGLE); finalNode3.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.SQUARE); systems.add(finalNode3);
    }

    private static void initializeLevel6(List<System> systems) {
        int w = NetworkGame.WINDOW_WIDTH;
        int h = NetworkGame.WINDOW_HEIGHT;
        int sysHeight = System.SYSTEM_HEIGHT;
        int y_pos = h / 2 - sysHeight / 2;

        int x1 = 100;
        int x2 = 350;
        int x3 = 600;
        int x4 = 850;

        // 1. BULK Source
        System source = createSource(x1, y_pos, NetworkEnums.PacketShape.CIRCLE, 5, 8000, NetworkEnums.PacketType.BULK);
        systems.add(source);

        // 2. Distributor
        System distributor = new System(x2, y_pos, NetworkEnums.SystemType.DISTRIBUTOR);
        distributor.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.CIRCLE);
        distributor.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.CIRCLE);
        systems.add(distributor);

        // 3. Merger
        System merger = new System(x3, y_pos, NetworkEnums.SystemType.MERGER);
        merger.addPort(NetworkEnums.PortType.INPUT, NetworkEnums.PortShape.CIRCLE);
        merger.addPort(NetworkEnums.PortType.OUTPUT, NetworkEnums.PortShape.CIRCLE);
        systems.add(merger);

        // 4. SINK
        System sink = createSink(x4, y_pos, NetworkEnums.PortShape.CIRCLE);
        systems.add(sink);
    }
}