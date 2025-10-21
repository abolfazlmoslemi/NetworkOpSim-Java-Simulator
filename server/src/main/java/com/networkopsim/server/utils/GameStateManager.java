// ===== File: GameStateManager.java (NEW - For Server Module) =====
// ===== MODULE: server =====

package com.networkopsim.server.utils;

import com.networkopsim.game.model.core.Packet;
import com.networkopsim.game.model.core.System;
import com.networkopsim.game.model.core.Wire;
import com.networkopsim.game.model.state.GameState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class GameStateManager {
    private static final Logger logger = LoggerFactory.getLogger(GameStateManager.class);
    public static final String SAVE_FILE_NAME = "game_server_save.dat"; // Renamed to avoid client/server conflict

    public static class SaveData implements Serializable {
        private static final long serialVersionUID = 1L;
        public final List<System> systems;
        public final List<Wire> wires;
        public final List<Packet> packets;
        public final GameState gameState;
        public final long simulationTimeElapsedMs;
        public final byte[] checksum;
        public SaveData(List<System> s, List<Wire> w, List<Packet> p, GameState gs, long time, byte[] sum) {
            systems = s; wires = w; packets = p; gameState = gs; simulationTimeElapsedMs = time; checksum = sum;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends Serializable> T deepCopy(T original) throws IOException, ClassNotFoundException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(original);
        oos.close();
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        ObjectInputStream ois = new ObjectInputStream(bais);
        return (T) ois.readObject();
    }

    public static boolean saveGameState(GameState gameState, List<System> systems, List<Wire> wires, List<Packet> packets, long simTime) {
        logger.info("Attempting to save game state...");
        try (ObjectOutputStream oos = new ObjectOutputStream(new GZIPOutputStream(new FileOutputStream(SAVE_FILE_NAME)))) {
            // Data is already copied, just need to create the save object
            List<System> systemsCopy = deepCopy(new ArrayList<>(systems));
            List<Wire> wiresCopy = deepCopy(new ArrayList<>(wires));
            List<Packet> packetsCopy = deepCopy(new ArrayList<>(packets));
            GameState gameStateCopy = deepCopy(gameState);

            byte[] checksum = calculateChecksum(systemsCopy, packetsCopy, gameStateCopy, simTime);
            SaveData dataToSave = new SaveData(systemsCopy, wiresCopy, packetsCopy, gameStateCopy, simTime, checksum);
            oos.writeObject(dataToSave);
            logger.info("Game state saved successfully to '{}'.", SAVE_FILE_NAME);
            return true;
        } catch (IOException | ClassNotFoundException | NoSuchAlgorithmException e) {
            logger.error("SEVERE: Error saving game state to '{}'.", SAVE_FILE_NAME, e);
            return false;
        }
    }

    public static SaveData loadGameState() {
        File saveFile = new File(SAVE_FILE_NAME);
        if (!saveFile.exists()) {
            logger.debug("No save file found at '{}'.", SAVE_FILE_NAME);
            return null;
        }
        logger.info("Attempting to load game state from '{}'...", SAVE_FILE_NAME);
        try (ObjectInputStream ois = new ObjectInputStream(new GZIPInputStream(new FileInputStream(saveFile)))) {
            SaveData loadedData = (SaveData) ois.readObject();
            byte[] expectedChecksum = calculateChecksum(loadedData.systems, loadedData.packets, loadedData.gameState, loadedData.simulationTimeElapsedMs);
            if (java.util.Arrays.equals(expectedChecksum, loadedData.checksum)) {
                logger.info("Save file loaded and checksum validated successfully.");
                return loadedData;
            } else {
                logger.error("SEVERE: Checksum mismatch! Save file '{}' may be corrupted.", SAVE_FILE_NAME);
                return null;
            }
        } catch (IOException | ClassNotFoundException | NoSuchAlgorithmException e) {
            logger.error("SEVERE: Error loading game state from '{}'.", SAVE_FILE_NAME, e);
            return null;
        }
    }

    public static void deleteSaveFile() {
        File saveFile = new File(SAVE_FILE_NAME);
        if (saveFile.exists()) {
            if (saveFile.delete()) {
                logger.info("Autosave file '{}' deleted.", SAVE_FILE_NAME);
            } else {
                logger.error("SEVERE: Failed to delete autosave file '{}'.", SAVE_FILE_NAME);
            }
        }
    }

    private static byte[] calculateChecksum(List<System> systems, List<Packet> packets, GameState gameState, long simTime) throws NoSuchAlgorithmException, IOException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(systems);
        oos.writeObject(packets);
        oos.writeInt(gameState.getCoins());
        oos.writeLong(simTime);
        oos.writeDouble(gameState.getPacketLossPercentage());
        oos.writeUTF("N3tw0rk_Op_S1m_S@lt!_2024_S3rv3r"); // Slightly different salt for server
        oos.flush(); oos.close();
        return digest.digest(baos.toByteArray());
    }
}