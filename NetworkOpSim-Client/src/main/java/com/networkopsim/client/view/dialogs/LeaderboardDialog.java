// FILE: /NetworkOpSim-Multiplayer/NetworkOpSim-Client/src/main/java/com/networkopsim/client/view/dialogs/LeaderboardDialog.java
// ================================================================================

package com.networkopsim.client.view.dialogs;

import com.networkopsim.client.core.NetworkGame;
import com.networkopsim.shared.dto.LeaderboardDTO;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.Map;

public class LeaderboardDialog extends JDialog {

    private final JTable personalBestTable;
    private final DefaultTableModel tableModel;
    private final JLabel globalBestLabel;

    // UI Constants
    private static final Color DIALOG_BG_COLOR = new Color(45, 45, 55);
    private static final Color TABLE_BG_COLOR = new Color(60, 60, 70);
    private static final Color TABLE_HEADER_BG_COLOR = new Color(80, 80, 95);
    private static final Color TEXT_COLOR = Color.WHITE;
    private static final Color HEADER_TEXT_COLOR = Color.BLACK; // For header
    private static final Font TITLE_FONT = new Font("Arial", Font.BOLD, 26);
    private static final Font HEADER_FONT = new Font("Arial", Font.BOLD, 14);
    private static final Font CELL_FONT = new Font("Consolas", Font.PLAIN, 13);

    public LeaderboardDialog(NetworkGame owner) {
        super(owner, "Leaderboard", true);

        setSize(600, 450);
        setLocationRelativeTo(owner);
        setResizable(false);

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBackground(DIALOG_BG_COLOR);
        mainPanel.setBorder(new EmptyBorder(15, 20, 15, 20));

        JLabel titleLabel = new JLabel("Leaderboard", SwingConstants.CENTER);
        titleLabel.setFont(TITLE_FONT);
        titleLabel.setForeground(TEXT_COLOR);
        mainPanel.add(titleLabel, BorderLayout.NORTH);

        String[] columnNames = {"Level", "Username", "Best Time", "Best XP"};
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        personalBestTable = new JTable(tableModel);
        personalBestTable.setFont(CELL_FONT);
        personalBestTable.setRowHeight(25);
        personalBestTable.setBackground(TABLE_BG_COLOR);
        personalBestTable.setForeground(TEXT_COLOR);
        personalBestTable.setGridColor(DIALOG_BG_COLOR);
        personalBestTable.getTableHeader().setFont(HEADER_FONT);
        personalBestTable.getTableHeader().setBackground(TABLE_HEADER_BG_COLOR);
        personalBestTable.getTableHeader().setForeground(HEADER_TEXT_COLOR);

        JScrollPane scrollPane = new JScrollPane(personalBestTable);
        scrollPane.getViewport().setBackground(TABLE_BG_COLOR);
        scrollPane.setBorder(BorderFactory.createLineBorder(TABLE_HEADER_BG_COLOR));
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        JPanel globalPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        globalPanel.setBackground(DIALOG_BG_COLOR);
        globalPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "Global Best XP",
                javax.swing.border.TitledBorder.CENTER,
                javax.swing.border.TitledBorder.TOP,
                HEADER_FONT, TEXT_COLOR
        ));
        globalBestLabel = new JLabel("Fetching data...");
        globalBestLabel.setFont(CELL_FONT.deriveFont(Font.BOLD));
        globalBestLabel.setForeground(Color.YELLOW);
        globalPanel.add(globalBestLabel);

        mainPanel.add(globalPanel, BorderLayout.SOUTH);

        setContentPane(mainPanel);
    }

    public void updateData(LeaderboardDTO data) {
        tableModel.setRowCount(0);

        if (data == null) {
            globalBestLabel.setText("Could not fetch leaderboard data.");
            return;
        }

        Map<Integer, LeaderboardDTO.ScoreRecord> personalBests = data.getPersonalBestsByLevel();
        if (personalBests.isEmpty()) {
            tableModel.addRow(new Object[]{"N/A", "No records yet", "N/A", "N/A"});
        } else {
            personalBests.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> {
                        int level = entry.getKey();
                        LeaderboardDTO.ScoreRecord record = entry.getValue();
                        String timeStr = formatTime(record.getTimeMillis());
                        tableModel.addRow(new Object[]{level, record.getUsername(), timeStr, record.getXp()});
                    });
        }

        LeaderboardDTO.ScoreRecord globalBest = data.getGlobalBestXp();
        if (globalBest != null) {
            String timeStr = formatTime(globalBest.getTimeMillis());
            globalBestLabel.setText(String.format("Player: %s | XP: %d | Time: %s",
                    globalBest.getUsername(), globalBest.getXp(), timeStr));
        } else {
            globalBestLabel.setText("No global records found.");
        }
    }

    private String formatTime(long millis) {
        if (millis <= 0) return "N/A";

        long totalSeconds = millis / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        long hundredths = (millis % 1000) / 10;

        return String.format("%02d:%02d.%02d", minutes, seconds, hundredths);
    }
}