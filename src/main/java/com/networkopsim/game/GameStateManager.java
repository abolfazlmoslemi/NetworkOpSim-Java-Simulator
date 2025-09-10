// ================================================================================
// FILE: GameStateManager.java (کلاس جدید برای مدیریت ذخیره و بارگذاری)
// ================================================================================
package com.networkopsim.game;

import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class GameStateManager {

    public static final String SAVE_FILE_NAME = "game_autosave.dat";

    // یک کلاس داخلی برای بسته‌بندی تمام داده‌های ذخیره شده
    public static class SaveData implements Serializable {
        private static final long serialVersionUID = 1L; // برای کنترل نسخه سریالایزیشن
        public final List<System> systems;
        public final List<Wire> wires;
        public final List<Packet> packets;
        public final GameState gameState;
        public final long simulationTimeElapsedMs;
        public final byte[] checksum; // برای اعتبارسنجی

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
        try (ObjectOutputStream oos = new ObjectOutputStream(new GZIPOutputStream(new FileOutputStream(SAVE_FILE_NAME)))) {
            // ایجاد یک کپی عمیق (deep copy) از داده‌ها برای جلوگیری از مشکلات همزمانی
            List<System> systemsCopy = gamePanel.getSystemsDeepCopy();
            List<Wire> wiresCopy = gamePanel.getWiresDeepCopy();
            List<Packet> packetsCopy = gamePanel.getPacketsDeepCopy();
            GameState gameStateCopy = gamePanel.getGameStateDeepCopy();
            long simTime = gamePanel.getSimulationTimeElapsedMs();

            byte[] checksum = calculateChecksum(systemsCopy, packetsCopy, gameStateCopy, simTime);

            SaveData dataToSave = new SaveData(systemsCopy, wiresCopy, packetsCopy, gameStateCopy, simTime, checksum);
            oos.writeObject(dataToSave);
            java.lang.System.out.println("Game state saved successfully with checksum.");
            return true;
        } catch (IOException | NoSuchAlgorithmException e) {
            java.lang.System.err.println("Error saving game state: " + e.getMessage());
            e.printStackTrace();
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
            return null;
        }

        try (ObjectInputStream ois = new ObjectInputStream(new GZIPInputStream(new FileInputStream(saveFile)))) {
            SaveData loadedData = (SaveData) ois.readObject();

            // اعتبارسنجی با Checksum
            byte[] expectedChecksum = calculateChecksum(loadedData.systems, loadedData.packets, loadedData.gameState, loadedData.simulationTimeElapsedMs);
            if (java.util.Arrays.equals(expectedChecksum, loadedData.checksum)) {
                java.lang.System.out.println("Save file loaded and checksum validated successfully.");
                return loadedData;
            } else {
                java.lang.System.err.println("Checksum mismatch! Save file may be corrupted or tampered with.");
                return null;
            }
        } catch (IOException | ClassNotFoundException | NoSuchAlgorithmException e) {
            java.lang.System.err.println("Error loading game state: " + e.getMessage());
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
                java.lang.System.out.println("Autosave file deleted.");
            } else {
                java.lang.System.err.println("Failed to delete autosave file.");
            }
        }
    }

    /**
     * یک Checksum برای داده‌های بازی محاسبه می‌کند تا از تقلب جلوگیری شود.
     */
    private static byte[] calculateChecksum(List<System> systems, List<Packet> packets, GameState gameState, long simTime) throws NoSuchAlgorithmException, IOException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);

        // داده‌های کلیدی را برای هش کردن می‌نویسیم
        oos.writeObject(systems);
        oos.writeObject(packets);
        oos.writeInt(gameState.getCoins());
        oos.writeLong(simTime);
        oos.writeDouble(gameState.getPacketLossPercentage());
        // یک "نمک" (salt) مخفی اضافه می‌کنیم تا حدس زدن هش سخت‌تر شود
        oos.writeUTF("N3tw0rk_Op_S1m_S@lt!_2024");

        oos.flush();
        oos.close();

        return digest.digest(baos.toByteArray());
    }
}