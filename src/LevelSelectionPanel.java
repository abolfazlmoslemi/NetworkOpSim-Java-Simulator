import javax.swing.*;
import java.awt.*;

public class LevelSelectionPanel extends JPanel {
    private final NetworkGame game;
    private final int NUM_LEVELS = 3;

    public LevelSelectionPanel(NetworkGame game) {
        this.game = game;
        setLayout(new GridLayout(0, 3, 20, 20));
        setPreferredSize(new Dimension(NetworkGame.WINDOW_WIDTH, NetworkGame.WINDOW_HEIGHT));

        for (int i = 1; i <= NUM_LEVELS; i++) {
            JButton lvlButton = new JButton("Level " + i);
            lvlButton.setFont(new Font("Arial", Font.BOLD, 20));
            final int level = i;
            lvlButton.addActionListener(e -> {
                System.out.println("Selected Level " + level);
                game.getGameState().setCurrentSelectedLevel(level);
                game.showLevelSelection();
            });
            add(lvlButton);
        }

        JButton backButton = new JButton("Back");
        backButton.setFont(new Font("Arial", Font.PLAIN, 18));
        backButton.addActionListener(e -> game.showMenu());
        add(backButton);
    }
}
