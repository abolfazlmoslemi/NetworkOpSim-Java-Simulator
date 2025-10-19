// FILE: /NetworkOpSim-Multiplayer/NetworkOpSim-Client/src/main/java/com/networkopsim/client/input/GameInputHandler.java
// ================================================================================

package com.networkopsim.client.input;

import com.networkopsim.client.core.NetworkGame;
import com.networkopsim.client.utils.KeyBindings;
import com.networkopsim.client.view.panels.GamePanel;
import com.networkopsim.shared.dto.GameStateDTO;
import com.networkopsim.shared.dto.SystemDTO;
import com.networkopsim.shared.dto.WireDTO;
import com.networkopsim.shared.model.NetworkEnums;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Point2D;
import java.util.Objects;

/**
 * Handles all keyboard and mouse input for the GamePanel.
 */
public class GameInputHandler implements KeyListener, MouseListener, MouseMotionListener {
    private final GamePanel gamePanel;
    private final NetworkGame game;
    private final KeyBindings keyBindings;

    public GameInputHandler(GamePanel gamePanel, NetworkGame game) {
        this.gamePanel = Objects.requireNonNull(gamePanel, "GamePanel cannot be null");
        this.game = Objects.requireNonNull(game, "NetworkGame cannot be null");
        this.keyBindings = Objects.requireNonNull(game.getKeyBindings(), "KeyBindings cannot be null");
    }

    @Override
    public void keyTyped(KeyEvent e) {}

    @Override
    public void keyReleased(KeyEvent e) {}

    @Override
    public void keyPressed(KeyEvent e) {
        int keyCode = e.getKeyCode();
        KeyBindings.GameAction action = keyBindings.getActionForKey(keyCode);

        if (action == KeyBindings.GameAction.ESCAPE_MENU_CANCEL) {
            handleEscapeKey();
            return;
        }

        GameStateDTO state = gamePanel.getDisplayState();
        if (state == null || state.isGameOver() || state.isLevelComplete()) {
            e.consume();
            return;
        }

        String gamePhase = state.getGamePhase();
        boolean inBuildPhase = "PRE_BUILD".equals(gamePhase) || "OVERTIME_BUILD".equals(gamePhase);
        boolean inOfflineBuildPhase = !gamePanel.isOnlineMultiplayer() && !state.isSimulationRunning();

        // --- In-simulation actions (Pause, Store) ---
        if (state.isSimulationRunning()) {
            if (action == KeyBindings.GameAction.PAUSE_RESUME_GAME) {
                if (gamePanel.isGamePaused()) {
                    gamePanel.pauseGame(false); // Request to resume
                } else {
                    gamePanel.pauseGame(true); // Request to pause
                }
                e.consume();
            } else if (action == KeyBindings.GameAction.OPEN_STORE) {
                if (!gamePanel.isGamePaused() || state.getPausedByPlayerId() == state.getLocalPlayerId() || state.getPausedByPlayerId() == 0) {
                    gamePanel.requestOpenStore();
                } else {
                    game.showTemporaryMessage("Cannot open store while opponent has paused the game.", Color.ORANGE, 3000);
                }
                e.consume();
            }
        }
        // --- Build-phase actions (Start Simulation) ---
        else if (inBuildPhase || inOfflineBuildPhase) {
            if (action == KeyBindings.GameAction.START_SIMULATION_SCRUB_MODE) {
                gamePanel.attemptStartSimulation();
                e.consume();
            }
        }
    }

    private void handleEscapeKey() {
        if (gamePanel.isWireDrawingMode()) {
            gamePanel.cancelWiring();
            gamePanel.requestFocusInWindow();
        } else if (gamePanel.isRelayDragging()) {
            gamePanel.endRelayDrag();
            gamePanel.requestFocusInWindow();
        } else if (gamePanel.getDisplayState() != null && gamePanel.getDisplayState().isSimulationRunning()) {
            boolean wasPaused = gamePanel.isGamePaused();
            gamePanel.pauseGame(true);
            int choice = JOptionPane.showConfirmDialog(game,
                    "Return to the main menu?\nCurrent game progress will be lost.",
                    "Exit Game?",
                    JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (choice == JOptionPane.YES_OPTION) {
                game.returnToMenu();
            } else {
                if (!wasPaused) {
                    gamePanel.pauseGame(false);
                }
                gamePanel.requestFocusInWindow();
            }
        } else {
            game.returnToMenu();
        }
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        GameStateDTO state = gamePanel.getDisplayState();
        if (state == null || state.isGameOver() || state.isLevelComplete()) return;

        Point pressPoint = e.getPoint();
        SystemDTO systemUnderMouse = gamePanel.findSystemAt(pressPoint);

        if (SwingUtilities.isRightMouseButton(e)) {
            if (gamePanel.isOnlineMultiplayer() && state.isSimulationRunning()) {
                if (systemUnderMouse != null &&
                        (systemUnderMouse.getSystemType() == NetworkEnums.SystemType.SOURCE || systemUnderMouse.getSystemType() == NetworkEnums.SystemType.SINK) &&
                        systemUnderMouse.getOwnerId() != state.getLocalPlayerId() && systemUnderMouse.getOwnerId() != 0)
                {
                    int choice = JOptionPane.showConfirmDialog(game,
                            "Sabotage opponent's system for 10 coins?",
                            "Confirm Sabotage", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
                    if (choice == JOptionPane.YES_OPTION) {
                        gamePanel.sabotageRequest(systemUnderMouse.getId());
                    }
                    return;
                }
            }
        }

        if (SwingUtilities.isLeftMouseButton(e)) {
            if (gamePanel.isOnlineMultiplayer() && state.isSimulationRunning()) {
                if (systemUnderMouse != null && systemUnderMouse.isControllable()) {
                    gamePanel.showControllableSystemMenu(systemUnderMouse, e.getPoint());
                    e.consume();
                    return;
                }
            }
        }
    }

    @Override
    public void mouseEntered(MouseEvent e) {}

    @Override
    public void mouseExited(MouseEvent e) {
        if (gamePanel.isWireDrawingMode()) {
            gamePanel.cancelWiring();
        }
        if (gamePanel.isRelayDragging()) {
            gamePanel.endRelayDrag();
        }
    }

    @Override
    public void mousePressed(MouseEvent e) {
        GameStateDTO state = gamePanel.getDisplayState();
        if (state == null || state.isGameOver() || state.isLevelComplete()) return;

        Point pressPoint = e.getPoint();
        gamePanel.requestFocusInWindow();

        String gamePhase = state.getGamePhase();
        boolean inBuildPhase = gamePanel.isOnlineMultiplayer() ?
                ("PRE_BUILD".equals(gamePhase) || "OVERTIME_BUILD".equals(gamePhase)) :
                !state.isSimulationRunning();

        if (SwingUtilities.isLeftMouseButton(e)) {
            if (!inBuildPhase) return;
            if (gamePanel.isWireDrawingMode() || gamePanel.isRelayDragging()) return;

            WireDTO wireUnderMouse = gamePanel.findWireAt(pressPoint, 10.0);
            if (wireUnderMouse != null) {
                Point2D.Double relayPoint = gamePanel.findRelayPointAt(wireUnderMouse, pressPoint, 10.0);
                if (relayPoint != null) {
                    int index = wireUnderMouse.getRelayPoints().indexOf(relayPoint);
                    gamePanel.startRelayDrag(wireUnderMouse, relayPoint, index);
                    return;
                }
            }

            SystemDTO.PortDTO clickedPort = gamePanel.findPortAt(pressPoint);
            if (clickedPort != null && clickedPort.getType() == NetworkEnums.PortType.OUTPUT) {
                gamePanel.startWiringMode(clickedPort, pressPoint);
                return;
            }

            WireDTO wireToAddRelay = gamePanel.findWireAt(pressPoint, 5.0);
            if (wireToAddRelay != null) {
                if (wireToAddRelay.getRelayPoints().size() >= 3) {
                    game.showTemporaryMessage("Maximum 3 relay points per wire.", Color.ORANGE, 2500);
                    game.playSoundEffect("error");
                    return;
                }

                int myCoins = state.getMyCoins();
                if (myCoins < 1) {
                    game.showTemporaryMessage("Not enough coins (Cost: 1)", Color.RED, 2500);
                    game.playSoundEffect("error");
                    return;
                }

                int choice = JOptionPane.showConfirmDialog(game,
                        "Add a relay point here for 1 coin?",
                        "Confirm Purchase", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);

                if (choice == JOptionPane.YES_OPTION) {
                    gamePanel.addRelayPointRequest(wireToAddRelay.getId(), pressPoint);
                }
            }
        } else if (SwingUtilities.isRightMouseButton(e)) {
            if (gamePanel.isWireDrawingMode()) {
                gamePanel.cancelWiring();
                return;
            }
            if (!inBuildPhase) {
                return;
            }

            WireDTO wireToDelete = gamePanel.findWireAt(pressPoint, 10.0);
            if (wireToDelete != null) {
                gamePanel.deleteWireRequest(wireToDelete.getId());
            }
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        GameStateDTO state = gamePanel.getDisplayState();
        if (state == null || state.isGameOver() || state.isLevelComplete()) return;

        boolean inBuildPhase = gamePanel.isOnlineMultiplayer() ?
                ("PRE_BUILD".equals(state.getGamePhase()) || "OVERTIME_BUILD".equals(state.getGamePhase())) :
                !state.isSimulationRunning();

        if (SwingUtilities.isLeftMouseButton(e)) {
            if (gamePanel.isRelayDragging()) {
                gamePanel.endRelayDrag();
                return;
            }
            if (gamePanel.isWireDrawingMode()) {
                if (inBuildPhase) {
                    Point releasePoint = e.getPoint();
                    SystemDTO.PortDTO releasePort = gamePanel.findPortAt(releasePoint);
                    SystemDTO.PortDTO startPort = gamePanel.getSelectedOutputPort();

                    if (startPort != null && releasePort != null) {
                        gamePanel.attemptWireCreation(startPort, releasePort);
                    }
                }
                gamePanel.cancelWiring();
            }
        }
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        GameStateDTO state = gamePanel.getDisplayState();
        if (state == null || state.isGameOver() || state.isLevelComplete()) {
            return;
        }

        boolean inBuildPhase = gamePanel.isOnlineMultiplayer() ?
                ("PRE_BUILD".equals(state.getGamePhase()) || "OVERTIME_BUILD".equals(state.getGamePhase())) :
                !state.isSimulationRunning();

        if (!inBuildPhase) return;

        if (SwingUtilities.isLeftMouseButton(e)) {
            Point currentDragPos = e.getPoint();
            if (gamePanel.isRelayDragging()) {
                gamePanel.updateRelayDrag(currentDragPos);
            } else if (gamePanel.isWireDrawingMode()) {
                gamePanel.updateWiringPreview(currentDragPos);
            }
        }
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        GameStateDTO state = gamePanel.getDisplayState();
        if (state == null || state.isGameOver() || state.isLevelComplete() || gamePanel.isRelayDragging()) {
            if (!gamePanel.isRelayDragging()) {
                gamePanel.setCursor(Cursor.getDefaultCursor());
            }
            gamePanel.setToolTipText(null);
            return;
        }

        Point currentPoint = e.getPoint();
        Cursor currentCursor = Cursor.getDefaultCursor();
        String tooltipText = null;

        boolean inBuildPhase = gamePanel.isOnlineMultiplayer() ?
                ("PRE_BUILD".equals(state.getGamePhase()) || "OVERTIME_BUILD".equals(state.getGamePhase())) :
                !state.isSimulationRunning();

        if (inBuildPhase) {
            SystemDTO.PortDTO portUnderMouse = gamePanel.findPortAt(currentPoint);
            WireDTO wireUnderMouse = gamePanel.findWireAt(currentPoint, 5.0);

            if (portUnderMouse != null) {
                tooltipText = "Port: " + portUnderMouse.getShape() + " (" + portUnderMouse.getType() + ")";
                currentCursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
            } else if (wireUnderMouse != null) {
                if (gamePanel.findRelayPointAt(wireUnderMouse, currentPoint, 10.0) != null) {
                    tooltipText = "Drag to move Relay Point";
                    currentCursor = Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR);
                } else {
                    tooltipText = "Wire " + wireUnderMouse.getId() + " (Click to add Relay, Right-click to delete)";
                    currentCursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
                }
            }
        }
        else if (gamePanel.isOnlineMultiplayer() && state.isSimulationRunning()) {
            SystemDTO systemUnderMouse = gamePanel.findSystemAt(currentPoint);
            if (systemUnderMouse != null) {
                if (systemUnderMouse.isControllable()) {
                    tooltipText = "Controllable System (Click to deploy packets)";
                    currentCursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
                } else if ((systemUnderMouse.getSystemType() == NetworkEnums.SystemType.SOURCE || systemUnderMouse.getSystemType() == NetworkEnums.SystemType.SINK) &&
                        systemUnderMouse.getOwnerId() != state.getLocalPlayerId() && systemUnderMouse.getOwnerId() != 0) {
                    tooltipText = "Opponent's System (Right-click to Sabotage for 10 coins)";
                    currentCursor = Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR);
                }
            }
        }

        gamePanel.setCursor(currentCursor);
        if (!Objects.equals(gamePanel.getToolTipText(), tooltipText)) {
            gamePanel.setToolTipText(tooltipText);
        }
    }
}