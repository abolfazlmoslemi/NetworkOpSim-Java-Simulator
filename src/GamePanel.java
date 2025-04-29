import javax.swing.*;
import java.awt.*;
import java.util.Objects;

public class GamePanel extends JPanel {
    private static final int GAME_TICK_MS = 16;

    private final NetworkGame game;
    private final GameState gameState;
    private final Timer gameLoopTimer;

    private boolean gameRunning       = false;
    private boolean gamePaused        = false;
    private boolean simulationStarted = false;
    private boolean levelComplete     = false;
    private boolean gameOver          = false;

    public GamePanel(NetworkGame game) {
        this.game      = Objects.requireNonNull(game);
        this.gameState = Objects.requireNonNull(game.getGameState());

        setPreferredSize(new Dimension(NetworkGame.WINDOW_WIDTH, NetworkGame.WINDOW_HEIGHT));
        setBackground(Color.BLACK);

        gameLoopTimer = new Timer(GAME_TICK_MS, e -> gameTick());
        gameLoopTimer.setRepeats(true);
    }

    public void initializeLevel(int level) {
        stopSimulation();
        gameState.resetForLevel();
        simulationStarted = false;
        levelComplete     = false;
        gameOver          = false;
        repaint();
        System.out.println("Level " + level + " initialized.");
    }

    public void attemptStartSimulation() {
        if (simulationStarted || gameOver || levelComplete) return;
        simulationStarted = true;
        gameRunning       = true;
        gamePaused        = false;
        gameLoopTimer.start();
        System.out.println("Simulation started.");
    }

    private void gameTick() {
        if (!gameRunning || gamePaused || gameOver || levelComplete) return;
        // TODO: منطق به‌روزرسانی پکت‌ها و سیستم‌ها
        repaint();
    }

    public void stopSimulation() {
        if (gameLoopTimer.isRunning()) {
            gameLoopTimer.stop();
            gameRunning = false;
            gamePaused  = false;
            System.out.println("Simulation stopped.");
        }
    }
}
