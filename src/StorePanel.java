import javax.swing.*;
import java.awt.*;

public class StorePanel extends JDialog {
    private final NetworkGame game;
    private JLabel coinsLabel;
    private JButton buyAtar;
    private JButton buyAiryaman;
    private JButton closeButton;

    public StorePanel(NetworkGame owner) {
        super(owner, "Store", true);
        this.game = owner;
        setSize(400, 300);
        setLayout(new FlowLayout());

        coinsLabel = new JLabel("Coins: 0");
        add(coinsLabel);

        buyAtar = new JButton("Buy Atar (3 coins)");
        buyAtar.addActionListener(e -> System.out.println("Purchased Atar"));
        add(buyAtar);

        buyAiryaman = new JButton("Buy Airyaman (4 coins)");
        buyAiryaman.addActionListener(e -> System.out.println("Purchased Airyaman"));
        add(buyAiryaman);

        closeButton = new JButton("Close");
        closeButton.addActionListener(e -> setVisible(false));
        add(closeButton);
    }
}
