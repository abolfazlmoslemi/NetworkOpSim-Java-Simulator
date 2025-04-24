import javax.swing.*;
import java.awt.*;

public class NetworkGame extends JFrame {
        public static final int WINDOW_WIDTH  = 1200;
        public static final int WINDOW_HEIGHT = 800;

        private CardLayout cardLayout;
        private JPanel mainPanelContainer;
        private MenuPanel menuPanel;
        private LevelSelectionPanel levelSelectionPanel;

        public NetworkGame() {
                setTitle("Network Operator Simulator");
                setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                setResizable(false);

                cardLayout = new CardLayout();
                mainPanelContainer = new JPanel(cardLayout);

                menuPanel = new MenuPanel(this);
                mainPanelContainer.add(menuPanel, "MainMenu");

                levelSelectionPanel = new LevelSelectionPanel(this);
                mainPanelContainer.add(levelSelectionPanel, "LevelSelection");

                setContentPane(mainPanelContainer);
                pack();
                setLocationRelativeTo(null);
                setVisible(true);
        }

        public void showMenu() {
                cardLayout.show(mainPanelContainer, "MainMenu");
        }

        public void showLevelSelection() {
                cardLayout.show(mainPanelContainer, "LevelSelection");
        }

        public void startGame() {
                System.out.println("Start Game pressed");
        }

        public static void main(String[] args) {
                SwingUtilities.invokeLater(NetworkGame::new);
        }
}
