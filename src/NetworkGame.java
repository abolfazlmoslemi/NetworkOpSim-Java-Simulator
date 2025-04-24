import javax.swing.*;
import java.awt.*;

public class NetworkGame extends JFrame {
        public static final int WINDOW_WIDTH  = 1200;
        public static final int WINDOW_HEIGHT = 800;

        private MenuPanel menuPanel;
        private CardLayout cardLayout;
        private JPanel mainPanelContainer;

        public NetworkGame() {
                setTitle("Network Operator Simulator");
                setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                setResizable(false);

                // راه‌اندازی کارت‌لِی‌آوت
                cardLayout = new CardLayout();
                mainPanelContainer = new JPanel(cardLayout);

                // افزودن منوی اصلی
                menuPanel = new MenuPanel(this);
                mainPanelContainer.add(menuPanel, "MainMenu");

                setContentPane(mainPanelContainer);
                pack();
                setLocationRelativeTo(null);
                setVisible(true);
        }

        /** فراخوانی شروع بازی از منو */
        public void startGame() {
                // TODO: در کامیت‌های بعدی به GamePanel سوئیچ شود
                System.out.println("Start Game pressed");
        }

        public static void main(String[] args) {
                SwingUtilities.invokeLater(NetworkGame::new);
        }
}
