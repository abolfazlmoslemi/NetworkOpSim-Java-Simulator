// FILE: /NetworkOpSim-Multiplayer/NetworkOpSim-Client/src/main/java/com/networkopsim/client/offline/OfflineResultManager.java
// ================================================================================

package com.networkopsim.client.offline;

import com.networkopsim.shared.net.OfflineResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Manages the storage and retrieval of offline game results.
 * Results are saved to a local file and loaded for synchronization with the server.
 */
public class OfflineResultManager {
    private static final Logger log = LoggerFactory.getLogger(OfflineResultManager.class);
    private static final String OFFLINE_RESULTS_FILE = "offline_results.dat";

    /**
     * Saves a list of offline game results to a local file.
     * This method will overwrite any existing file.
     * @param results The list of OfflineResult objects to save.
     */
    public synchronized void saveResults(List<OfflineResult> results) {
        if (results == null || results.isEmpty()) {
            // If the list is empty, it means we've successfully synced, so delete the file.
            deleteResultsFile();
            return;
        }

        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(OFFLINE_RESULTS_FILE))) {
            oos.writeObject(results);
            log.info("{} offline results saved to file '{}'.", results.size(), OFFLINE_RESULTS_FILE);
        } catch (IOException e) {
            log.error("Failed to save offline results to file.", e);
        }
    }

    /**
     * Appends a single offline game result to the local file.
     * @param result The OfflineResult to add.
     */
    public synchronized void addResult(OfflineResult result) {
        if (result == null) return;
        List<OfflineResult> existingResults = loadResults();
        existingResults.add(result);
        saveResults(existingResults);
    }

    /**
     * Loads all pending offline game results from the local file.
     * @return A list of OfflineResult objects. Returns an empty list if the file doesn't exist or an error occurs.
     */
    @SuppressWarnings("unchecked")
    public synchronized List<OfflineResult> loadResults() {
        if (!Files.exists(Paths.get(OFFLINE_RESULTS_FILE))) {
            return new ArrayList<>();
        }

        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(OFFLINE_RESULTS_FILE))) {
            Object obj = ois.readObject();
            if (obj instanceof List) {
                log.info("Loaded {} offline results from file.", ((List<?>) obj).size());
                return (List<OfflineResult>) obj;
            } else {
                log.warn("Offline results file is corrupt or has an unexpected format.");
                return new ArrayList<>();
            }
        } catch (IOException | ClassNotFoundException e) {
            log.error("Failed to load offline results from file.", e);
            return new ArrayList<>();
        }
    }

    /**
     * Deletes the local file containing offline results.
     * This should be called after results have been successfully synchronized with the server.
     */
    public synchronized void deleteResultsFile() {
        try {
            Files.deleteIfExists(Paths.get(OFFLINE_RESULTS_FILE));
            log.info("Offline results file deleted successfully.");
        } catch (IOException e) {
            log.error("Failed to delete offline results file.", e);
        }
    }
}