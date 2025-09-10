// ================================================================================
// FILE: GameStateManager.java (کد کامل و نهایی با سیستم لاگ)
// ================================================================================
package com.networkopsim.game;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class GameStateManager {
    private static final Logger logger = LoggerFactory.getLogger(GameStateManager.class);
    public static final String SAVE_FILE_NAME = "game_autosave.dat";

    public static class SaveData implements Serializable {
        private static final long serialVersionUID = 1L;
        public final List<System> systems;
        public final List<Wire> wires;
        public final List<Packet> packets;
        public final GameState gameState;
        public final long simulationTimeElapsedMs;
        public final byte[] checksum;

        public SaveData(List<System> systems, List<Wire> wires, List<Packet> packets, GameState gameState, long simTime, byte[] checksum) {
            this.systems = systems;
            this.wires = wires;
            this.packets = packets;
            this.gameState = gameState;
            this.simulationTimeElapsedMs = simTime;
            this.checksum = checksum;
        }
    }

    /**
     * وضعیت فعلی بازی را در یک فایل ذخیره می‌کند.
     * @return true اگر ذخیره‌سازی موفق بود، در غیر این صورت false.
     */
    public static boolean saveGameState(GamePanel gamePanel) {
        logger.info("Attempting to save game state...");
        try (ObjectOutputStream oos = new ObjectOutputStream(new GZIPOutputStream(new FileOutputStream(SAVE_FILE_NAME)))) {
            List<System> systemsCopy = gamePanel.getSystemsDeepCopy();
            List<Wire> wiresCopy = gamePanel.getWiresDeepCopy();
            List<Packet> packetsCopy = gamePanel.getPacketsDeepCopy();
            GameState gameStateCopy = gamePanel.getGameStateDeepCopy();
            long simTime = gamePanel.getSimulationTimeElapsedMs();

            byte[] checksum = calculateChecksum(systemsCopy, packetsCopy, gameStateCopy, simTime);

            SaveData dataToSave = new SaveData(systemsCopy, wiresCopy, packetsCopy, gameStateCopy, simTime, checksum);
            oos.writeObject(dataToSave);
            logger.info("Game state saved successfully to '{}'.", SAVE_FILE_NAME);
            return true;
        } catch (IOException | NoSuchAlgorithmException e) {
            logger.error("SEVERE: Error saving game state to '{}'.", SAVE_FILE_NAME, e);
            return false;
        }
    }

    /**
     * وضعیت بازی را از فایل بارگذاری می‌کند.
     * @return آبجکت SaveData اگر بارگذاری و اعتبارسنجی موفق بود، در غیر این صورت null.
     */
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
                logger.error("SEVERE: Checksum mismatch! Save file '{}' may be corrupted or tampered with.", SAVE_FILE_NAME);
                return null;
            }
        } catch (IOException | ClassNotFoundException | NoSuchAlgorithmException e) {
            logger.error("SEVERE: Error loading game state from '{}'. The file might be incompatible or corrupted.", SAVE_FILE_NAME, e);
            return null;
        }
    }

    /**
     * فایل ذخیره خودکار را حذف می‌کند.
     */
    public static void deleteSaveFile() {
        File saveFile = new File(SAVE_FILE_NAME);
        if (saveFile.exists()) {
            if (saveFile.delete()) {
                logger.info("Autosave file '{}' deleted successfully.", SAVE_FILE_NAME);
            } else {
                logger.error("SEVERE: Failed to delete autosave file '{}'.", SAVE_FILE_NAME);
            }
        } else {
            logger.debug("Attempted to delete autosave file, but it does not exist.");
        }
    }

    /**
     * یک Checksum برای داده‌های بازی محاسبه می‌کند تا از تقلب جلوگیری شود.
     */
    private static byte[] calculateChecksum(List<System> systems, List<Packet> packets, GameState gameState, long simTime) throws NoSuchAlgorithmException, IOException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);

        oos.writeObject(systems);
        oos.writeObject(packets);
        oos.writeInt(gameState.getCoins());
        oos.writeLong(simTime);
        oos.writeDouble(gameState.getPacketLossPercentage());
        oos.writeUTF("N3tw0rk_Op_S1m_S@lt!_2024");

        oos.flush();
        oos.close();

        return digest.digest(baos.toByteArray());
    }
}