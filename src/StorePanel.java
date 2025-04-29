import javax.swing.*;
import java.awt.*;

public class StorePanel extends JPanel {
    private final NetworkGame game;
    private JLabel coinsLabel;
    private JButton buyAtar, buyAiryaman, closeButton;

    public StorePanel(NetworkGame game) {
        this.game = game;
        setLayout(new FlowLayout());

        coinsLabel = new JLabel("Coins: 0");
        add(coinsLabel);

        buyAtar = new JButton("Buy Atar (3 coins)");
        buyAtar.addActionListener(e -> {
            // TODO: logic خرید
            System.out.println("Purchased Atar");
        });
        add(buyAtar);

        buyAiryaman = new JButton("Buy Airyaman (4 coins)");
        buyAiryaman.addActionListener(e -> {
            // TODO: logic خرید
            System.out.println("Purchased Airyaman");
        });
        add(buyAiryaman);

        closeButton = new JButton("Close");
        closeButton.addActionListener(e -> game.showMenu());
        add(closeButton);
    }
}
