import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class MenuPanel extends JPanel {
    private final NetworkGame game;
    private final JButton startButton;
    private final JButton exitButton;

    public MenuPanel(NetworkGame game) {
        this.game = game;
        setLayout(new GridBagLayout());
        setPreferredSize(new Dimension(NetworkGame.WINDOW_WIDTH, NetworkGame.WINDOW_HEIGHT));

        startButton = createMenuButton("Start Game");
        exitButton  = createMenuButton("Exit");

        startButton.addActionListener(e -> game.startGame());
        exitButton .addActionListener(e -> System.exit(0));

        Box vbox = Box.createVerticalBox();
        vbox.add(startButton);
        vbox.add(Box.createVerticalStrut(20));
        vbox.add(exitButton);

        add(vbox);
    }

    private JButton createMenuButton(String text) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Arial", Font.BOLD, 24));
        btn.setBackground(new Color(70, 130, 180));
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        Border line = BorderFactory.createLineBorder(new Color(40, 90, 130), 2);
        Border empty = BorderFactory.createEmptyBorder(10, 20, 10, 20);
        btn.setBorder(BorderFactory.createCompoundBorder(line, empty));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.addMouseListener(new MouseAdapter() {
            final Color orig = btn.getBackground();
            @Override public void mouseEntered(MouseEvent e)  { btn.setBackground(orig.brighter()); }
            @Override public void mouseExited(MouseEvent e)   { btn.setBackground(orig); }
        });
        return btn;
    }
}
