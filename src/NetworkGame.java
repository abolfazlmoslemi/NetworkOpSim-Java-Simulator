import javax.swing.*;
import java.awt.*;

public class NetworkGame extends JFrame {
        public static final int WINDOW_WIDTH  = 1200;
        public static final int WINDOW_HEIGHT = 800;

        private final CardLayout cardLayout;
        private final JPanel mainPanelContainer;

        // پنل‌های قبلی
        private final MenuPanel menuPanel;
        private final LevelSelectionPanel levelSelectionPanel;
        private final SettingsPanel settingsPanel;
        private final StorePanel storeDialog;

        // اضافه‌شده در کامیت ۶
        private final GameState gameState;
        private final GamePanel gamePanel;

        public NetworkGame() {
                setTitle("Network Operator Simulator");
                setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                setResizable(false);

                cardLayout = new CardLayout();
                mainPanelContainer = new JPanel(cardLayout);

                // پنل‌های فازهای 1–5
                menuPanel           = new MenuPanel(this);
                levelSelectionPanel = new LevelSelectionPanel(this);
                settingsPanel       = new SettingsPanel(this);
                storeDialog         = new StorePanel(this);

                mainPanelContainer.add(menuPanel,           "MainMenu");
                mainPanelContainer.add(levelSelectionPanel, "LevelSelection");
                mainPanelContainer.add(settingsPanel,       "SettingsMenu");
                mainPanelContainer.add(storeDialog,         "Store");

                // پیاده‌سازی کامیت ۶: GameState و GamePanel
                gameState = new GameState();
                gamePanel = new GamePanel(this);
                mainPanelContainer.add(gamePanel, "GamePanel");

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

        public void showSettings() {
                cardLayout.show(mainPanelContainer, "SettingsMenu");
        }

        public void showStore() {
                cardLayout.show(mainPanelContainer, "Store");
        }

        public void startGame() {
                cardLayout.show(mainPanelContainer, "GamePanel");
                gamePanel.initializeLevel(gameState.getCurrentSelectedLevel());
        }

        public GameState getGameState() {
                return gameState;
        }

        public static void main(String[] args) {
                SwingUtilities.invokeLater(NetworkGame::new);
        }
}
