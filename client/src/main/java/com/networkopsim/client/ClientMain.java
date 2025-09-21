// ===== File: ClientMain.java (NEW - Entry point for the client application) =====
// ===== MODULE: client =====

package com.networkopsim.client;

import com.networkopsim.game.controller.core.NetworkGame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;

public class ClientMain {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                boolean nimbusFound = false;
                for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                    if ("Nimbus".equals(info.getName())) {
                        UIManager.setLookAndFeel(info.getClassName());
                        nimbusFound = true;
                        break;
                    }
                }
                if (!nimbusFound) {
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                }
            } catch (Exception e) {
                LoggerFactory.getLogger(ClientMain.class).warn("Could not set the Look and Feel.", e);
            }

            Logger mainLogger = LoggerFactory.getLogger(ClientMain.class);
            mainLogger.info("======================================================");
            mainLogger.info("     Network Operator Simulator Client Starting Up");
            mainLogger.info("======================================================");

            // NetworkGame now represents the main client window
            new NetworkGame();
        });
    }
}