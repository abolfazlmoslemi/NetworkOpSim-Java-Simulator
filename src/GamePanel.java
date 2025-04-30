
import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.Dimension;
import java.util.List;
import java.util.ArrayList;
import java.util.Objects;

public class GamePanel extends JPanel {
    private static final int GAME_TICK_MS = 16;

    private final NetworkGame game;
    private final GameState gameState;
    private final Timer gameLoopTimer;

    private final List<System> systems = new ArrayList<>();
    private final List<Wire> wires = new ArrayList<>();

    private boolean gameRunning = false;
    private boolean gamePaused = false;
    private boolean simulationStarted = false;
    private boolean levelComplete = false;
    private boolean gameOver = false;

    public GamePanel(NetworkGame game) {
        this.game = Objects.requireNonNull(game);
        this.gameState = Objects.requireNonNull(game.getGameState());
        setPreferredSize(new Dimension(NetworkGame.WINDOW_WIDTH, NetworkGame.WINDOW_HEIGHT));
        gameLoopTimer = new Timer(GAME_TICK_MS, e -> gameTick());
        gameLoopTimer.setRepeats(true);
    }

    public void initializeLevel(int level) {
        stopSimulation();
        gameState.resetForLevel();
        simulationStarted = false;
        levelComplete = false;
        gameOver = false;
        systems.clear();
        wires.clear();
        repaint();
    }

    public void attemptStartSimulation() {
        if (simulationStarted || gameOver || levelComplete) return;
        simulationStarted = true;
        gameRunning = true;
        gamePaused = false;
        gameLoopTimer.start();
    }

    private void gameTick() {
        if (!gameRunning || gamePaused || gameOver || levelComplete) return;
        for (System s : systems) {
            s.processQueue(this);
        }
        repaint();
    }

    public void stopSimulation() {
        if (gameLoopTimer.isRunning()) {
            gameLoopTimer.stop();
            gameRunning = false;
            gamePaused = false;
        }
    }

    public void packetLost(Packet packet) {
        gameState.increasePacketLoss(packet);
    }

    public void packetSuccessfullyDelivered(Packet packet) {
        gameState.recordPacketGeneration(packet);
        gameState.addCoins(packet.getSize());
    }
}
