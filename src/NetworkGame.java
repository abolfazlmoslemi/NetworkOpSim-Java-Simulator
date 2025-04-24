public class NetworkGame extends JFrame {
-    public NetworkGame() {
        -        setTitle("Network Operator Simulator");
        -        setSize(WINDOW_WIDTH, WINDOW_HEIGHT);
        -        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        -        setResizable(false);
        -        setLocationRelativeTo(null);
        -        setVisible(true);
        -    }
+    private MenuPanel menuPanel;
+    private CardLayout cardLayout;
+    private JPanel mainPanelContainer;
+
        +    public NetworkGame() {
        +        setTitle("Network Operator Simulator");
        +        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        +        setResizable(false);
        +
                +        cardLayout = new CardLayout();
        +        mainPanelContainer = new JPanel(cardLayout);
        +
                +        menuPanel = new MenuPanel(this);
        +        mainPanelContainer.add(menuPanel, "MainMenu");
        +
                +        setContentPane(mainPanelContainer);
        +        pack();
        +        setLocationRelativeTo(null);
        +        setVisible(true);
        +    }
+
        +    public void startGame() {
        +        System.out.println("Start Game pressed");
        +    }
}
