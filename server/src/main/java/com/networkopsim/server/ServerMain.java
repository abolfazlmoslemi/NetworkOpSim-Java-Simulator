// ===== File: ServerMain.java (NEW - Entry point for the server application) =====
// ===== MODULE: server =====

package com.networkopsim.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServerMain {
    private static final Logger logger = LoggerFactory.getLogger(ServerMain.class);
    private static final int DEFAULT_PORT = 12345;

    public static void main(String[] args) {
        logger.info("======================================================");
        logger.info("   Starting Network Operator Simulator Server");
        logger.info("======================================================");

        GameServer server = new GameServer(DEFAULT_PORT);
        new Thread(server, "GameServerThread").start();
    }
}