// FILE: /NetworkOpSim-Multiplayer/NetworkOpSim-Client/src/main/java/com/networkopsim/client/view/dialogs/EndGameDialog.java
// ================================================================================

package com.networkopsim.client.view.dialogs;

import com.networkopsim.client.core.NetworkGame;
import com.networkopsim.shared.dto.GameStateDTO;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EndGameDialog extends JDialog {

    private final NetworkGame game;
    private final JLabel yourResultLabel;
    private final JLabel opponentResultLabel;
    private final JPanel yourPanel;
    private final JPanel opponentPanel;

    // UI Constants
    private static final Color DIALOG_BG_COLOR = new Color(45, 45, 55);
    private static final Color PANEL_BG_COLOR = new Color(240, 240, 240); // Light gray background for panels
    private static final Color TEXT_COLOR_LIGHT = Color.WHITE;
    private static final Color TEXT_COLOR_DARK = Color.BLACK; // For text on light backgrounds
    private static final Font TITLE_FONT = new Font("Arial", Font.BOLD, 28);
    private static final Font STATS_FONT = new Font("Consolas", Font.PLAIN, 16);
    private static final Color WIN_COLOR = new Color(80, 180, 80);
    private static final Color LOSE_COLOR = new Color(200, 80, 80);
    private static final Color DRAW_COLOR = new Color(150, 150, 150);

    public EndGameDialog(NetworkGame owner) {
        super(owner, "Game Over", true);
        this.game = owner;

        setSize(700, 400);
        setLocationRelativeTo(owner);
        setResizable(false);
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        JPanel mainPanel = new JPanel(new BorderLayout(20, 20));
        mainPanel.setBackground(DIALOG_BG_COLOR);
        mainPanel.setBorder(new EmptyBorder(20, 25, 20, 25));

        JLabel titleLabel = new JLabel("Match Results", SwingConstants.CENTER);
        titleLabel.setFont(TITLE_FONT);
        titleLabel.setForeground(TEXT_COLOR_LIGHT);
        mainPanel.add(titleLabel, BorderLayout.NORTH);

        JPanel resultsPanel = new JPanel(new GridLayout(1, 2, 20, 0));
        resultsPanel.setOpaque(false);

        yourPanel = new JPanel(new BorderLayout());
        yourPanel.setBackground(PANEL_BG_COLOR);
        yourResultLabel = createResultLabel();
        yourPanel.add(yourResultLabel, BorderLayout.CENTER);
        resultsPanel.add(yourPanel);

        opponentPanel = new JPanel(new BorderLayout());
        opponentPanel.setBackground(PANEL_BG_COLOR);
        opponentResultLabel = createResultLabel();
        opponentPanel.add(opponentResultLabel, BorderLayout.CENTER);
        resultsPanel.add(opponentPanel);

        mainPanel.add(resultsPanel, BorderLayout.CENTER);

        JButton closeButton = new JButton("Return to Menu");
        closeButton.addActionListener(e -> {
            setVisible(false);
            game.returnToMenu();
        });
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        buttonPanel.setOpaque(false);
        buttonPanel.add(closeButton);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        setContentPane(mainPanel);
    }

    private JLabel createResultLabel() {
        JLabel label = new JLabel();
        label.setFont(STATS_FONT);
        label.setForeground(TEXT_COLOR_DARK); // <-- CHANGED
        label.setVerticalAlignment(SwingConstants.TOP);
        label.setBorder(new EmptyBorder(15, 15, 15, 15));
        return label;
    }

    public void updateResults(GameStateDTO lastState, String resultMessage) {
        if (lastState == null) return;

        double p1Score = extractScore(resultMessage, "P1");
        double p2Score = extractScore(resultMessage, "P2");

        double yourScore = lastState.getLocalPlayerId() == 1 ? p1Score : p2Score;
        double opponentScore = lastState.getLocalPlayerId() == 1 ? p2Score : p1Score;

        int yourCoins = lastState.getMyCoins();
        double yourLoss = lastState.getMyLossPercentage();

        int opponentCoins = lastState.getOpponentCoins();
        double opponentLoss = lastState.getOpponentLossPercentage();

        yourResultLabel.setText(String.format("<html>" +
                "<b>Final Score: %.1f</b><br><br>" +
                "Coins: %d<br>" +
                "Packet Loss: %.1f%%" +
                "</html>", yourScore, yourCoins, yourLoss));

        opponentResultLabel.setText(String.format("<html>" +
                "<b>Final Score: %.1f</b><br><br>" +
                "Coins: %d<br>" +
                "Packet Loss: %.1f%%" +
                "</html>", opponentScore, opponentCoins, opponentLoss));

        Color yourColor, opponentColor;
        String yourTitle, opponentTitle;

        if (yourScore > opponentScore) {
            yourColor = WIN_COLOR;
            opponentColor = LOSE_COLOR;
            yourTitle = "YOU (WINNER)";
            opponentTitle = "OPPONENT (LOSER)";
        } else if (opponentScore > yourScore) {
            yourColor = LOSE_COLOR;
            opponentColor = WIN_COLOR;
            yourTitle = "YOU (LOSER)";
            opponentTitle = "OPPONENT (WINNER)";
        } else {
            yourColor = DRAW_COLOR;
            opponentColor = DRAW_COLOR;
            yourTitle = "YOU (DRAW)";
            opponentTitle = "OPPONENT (DRAW)";
        }

        yourPanel.setBorder(createTitledBorder(" " + yourTitle + " ", yourColor));
        opponentPanel.setBorder(createTitledBorder(" " + opponentTitle + " ", opponentColor));
    }

    private TitledBorder createTitledBorder(String title, Color color) {
        TitledBorder border = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(color, 2),
                title,
                TitledBorder.CENTER,
                TitledBorder.TOP,
                STATS_FONT.deriveFont(Font.BOLD),
                color
        );
        return border;
    }

    private double extractScore(String message, String playerLabel) {
        Pattern pattern = Pattern.compile(playerLabel + "\\s*\\(([^)]+)\\)");
        Matcher matcher = pattern.matcher(message);
        if (matcher.find()) {
            try {
                return Double.parseDouble(matcher.group(1));
            } catch (NumberFormatException e) {
                return 0.0;
            }
        }
        return 0.0;
    }
}