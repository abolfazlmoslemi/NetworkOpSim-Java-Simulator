
import javax.swing.*;
import java.awt.*;

public class SettingsPanel extends JPanel {
    private final NetworkGame game;
    private JSlider volumeSlider;
    private JCheckBox muteCheckbox;

    public SettingsPanel(NetworkGame game) {
        this.game = game;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setPreferredSize(new Dimension(NetworkGame.WINDOW_WIDTH, NetworkGame.WINDOW_HEIGHT));

        add(new JLabel("Settings"));

        volumeSlider = new JSlider(0, 100, 70);
        volumeSlider.addChangeListener(e -> System.out.println("Volume: " + volumeSlider.getValue()));
        add(volumeSlider);

        muteCheckbox = new JCheckBox("Mute");
        muteCheckbox.addActionListener(e -> System.out.println("Muted: " + muteCheckbox.isSelected()));
        add(muteCheckbox);

        JButton backButton = new JButton("Back");
        backButton.addActionListener(e -> game.showMenu());
        add(backButton);
    }
}
