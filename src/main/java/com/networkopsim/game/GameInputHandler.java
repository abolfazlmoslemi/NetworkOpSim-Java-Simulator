package com.networkopsim.game;// FILE: GameInputHandler.java
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
// import java.util.ArrayList; // Not used
// import java.util.List; // Not used
import java.util.Objects;
// import java.awt.geom.Line2D; // Not used

public class GameInputHandler implements KeyListener, MouseListener, MouseMotionListener {
    private final GamePanel gamePanel;
    private final NetworkGame game;
    private final KeyBindings keyBindings;

    public GameInputHandler(GamePanel gamePanel, NetworkGame game) {
        this.gamePanel = Objects.requireNonNull(gamePanel, "GamePanel cannot be null");
        this.game = Objects.requireNonNull(game, "NetworkGame cannot be null");
        this.keyBindings = Objects.requireNonNull(game.getKeyBindings(), "KeyBindings cannot be null");
    }
    @Override public void keyTyped(KeyEvent e) {  }
    @Override public void keyReleased(KeyEvent e) {  }
    @Override
    public void keyPressed(KeyEvent e) {
        int keyCode = e.getKeyCode();
        KeyBindings.GameAction action = keyBindings.getActionForKey(keyCode);
        if (action == KeyBindings.GameAction.ESCAPE_MENU_CANCEL) {
            handleEscapeKey();
            return;
        }
        if (action == KeyBindings.GameAction.TOGGLE_HUD) {
            gamePanel.toggleHUD();
            e.consume();
        }
        if (gamePanel.isGameOver() || gamePanel.isLevelComplete()) {
            e.consume();
            return;
        }
        if (!gamePanel.isSimulationStarted()) {
            if (action == KeyBindings.GameAction.DECREMENT_VIEWED_TIME) {
                gamePanel.decrementViewedTime();
                e.consume();
            } else if (action == KeyBindings.GameAction.INCREMENT_VIEWED_TIME) {
                gamePanel.incrementViewedTime();
                e.consume();
            } else if (action == KeyBindings.GameAction.START_SIMULATION_SCRUB_MODE) {
                gamePanel.attemptStartSimulation();
                e.consume();
            }
            else if (action == KeyBindings.GameAction.PAUSE_RESUME_GAME || action == KeyBindings.GameAction.OPEN_STORE) {
                e.consume();
            }
        } else {
            if (action == KeyBindings.GameAction.PAUSE_RESUME_GAME) {
                gamePanel.pauseGame(!gamePanel.isGamePaused());
                e.consume();
            } else if (action == KeyBindings.GameAction.OPEN_STORE) {
                if (gamePanel.isSimulationStarted()) {
                    if (!gamePanel.isGamePaused()) {
                        gamePanel.pauseGame(true);
                    }
                    SwingUtilities.invokeLater(() -> game.showStore());
                }
                e.consume();
            }
            else if (action == KeyBindings.GameAction.DECREMENT_VIEWED_TIME ||
                    action == KeyBindings.GameAction.INCREMENT_VIEWED_TIME ||
                    action == KeyBindings.GameAction.START_SIMULATION_SCRUB_MODE) {
                e.consume();
            }
        }
    }
    private void handleEscapeKey() {
        if (gamePanel.isWireDrawingMode()) {
            gamePanel.cancelWiring();
            gamePanel.requestFocusInWindow();
        } else if (gamePanel.isRelayPointDragMode()) {
            gamePanel.cancelRelayPointDrag();
            gamePanel.requestFocusInWindow();
        } else if (gamePanel.isSimulationStarted() && !gamePanel.isGameOver() && !gamePanel.isLevelComplete()) {
            boolean wasPaused = gamePanel.isGamePaused();
            gamePanel.pauseGame(true);
            int choice = JOptionPane.showConfirmDialog(
                    game,
                    "Return to the main menu?\nCurrent level progress will be lost.",
                    "Exit Level?",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE);
            if (choice == JOptionPane.YES_OPTION) {
                gamePanel.stopSimulation();
                game.returnToMenu();
            } else {
                if (!wasPaused) {
                    gamePanel.pauseGame(false);
                }
                gamePanel.requestFocusInWindow();
            }
        }
        else {
            game.returnToMenu();
        }
    }
    @Override public void mouseClicked(MouseEvent e) {  }
    @Override public void mouseEntered(MouseEvent e) {  }
    @Override
    public void mouseExited(MouseEvent e) {
        if (gamePanel.isWireDrawingMode()) {
            gamePanel.cancelWiring();
        }
        if (gamePanel.isRelayPointDragMode()) {
            gamePanel.cancelRelayPointDrag();
        }
    }
    @Override
    public void mousePressed(MouseEvent e) {
        if (gamePanel.isGameOver() || gamePanel.isLevelComplete()) {
            return;
        }
        Point pressPoint = e.getPoint();
        gamePanel.requestFocusInWindow();

        if (SwingUtilities.isLeftMouseButton(e)) {
            if (gamePanel.isSimulationStarted()) return;

            if (gamePanel.isWireDrawingMode()) return; // Already wiring, ignore other presses

            // Priority 1: Press on a relay point to start dragging it
            Wire.RelayPoint relayToDrag = gamePanel.findRelayPointAt(pressPoint);
            if (relayToDrag != null) {
                gamePanel.startRelayPointDrag(relayToDrag);
                return;
            }

            // Priority 2: Press on a port to start wiring
            Port clickedPort = gamePanel.findPortAt(pressPoint);
            if (clickedPort != null &&
                    clickedPort.getType() == NetworkEnums.PortType.OUTPUT &&
                    !clickedPort.isConnected())
            {
                gamePanel.startWiringMode(clickedPort, pressPoint);
                gamePanel.updateWiringPreview(pressPoint);
                return;
            }

            // Priority 3: Press on a wire to add a relay point
            Wire wireToAddRelay = gamePanel.findWireAt(pressPoint, 5.0); // Smaller threshold for adding point
            if (wireToAddRelay != null) {
                gamePanel.addRelayPointRequest(wireToAddRelay, pressPoint);
            }

        }
        else if (SwingUtilities.isRightMouseButton(e)) {
            if (gamePanel.isWireDrawingMode()) {
                gamePanel.cancelWiring();
                return;
            }
            if (gamePanel.isRelayPointDragMode()) {
                gamePanel.cancelRelayPointDrag();
                return;
            }

            if (!gamePanel.isSimulationStarted()) {
                // Priority 1: Right-click on a relay point to delete it
                Wire.RelayPoint relayToDelete = gamePanel.findRelayPointAt(pressPoint);
                if (relayToDelete != null) {
                    gamePanel.deleteRelayPointRequest(relayToDelete);
                    return;
                }

                // Priority 2: Right-click on a wire to delete the whole wire
                Wire wireToDelete = gamePanel.findWireAt(pressPoint, 10.0);
                if (wireToDelete != null) {
                    gamePanel.deleteWireRequest(wireToDelete);
                }
            }
            else if (gamePanel.isSimulationStarted()){
                if(!game.isMuted()) game.playSoundEffect("error");
            }
        }
    }
    @Override
    public void mouseReleased(MouseEvent e) {
        if (gamePanel.isGameOver() || gamePanel.isLevelComplete()) {
            return;
        }

        if (SwingUtilities.isLeftMouseButton(e)) {
            if (gamePanel.isWireDrawingMode()) {
                if (!gamePanel.isSimulationStarted()) {
                    Point releasePoint = e.getPoint();
                    Port releasePort = gamePanel.findPortAt(releasePoint);
                    boolean connectionMade = false;
                    Port startPort = gamePanel.getSelectedOutputPort();
                    Color finalWireColor = gamePanel.getCurrentWiringColor();

                    if (startPort != null && releasePort != null &&
                            !Objects.equals(releasePort.getParentSystem(), startPort.getParentSystem()) &&
                            releasePort.getType() == NetworkEnums.PortType.INPUT &&
                            !releasePort.isConnected() &&
                            finalWireColor.equals(GamePanel.VALID_WIRING_COLOR_TARGET) ) {
                        connectionMade = gamePanel.attemptWireCreation(startPort, releasePort);
                    }

                    if (!connectionMade && releasePort != null) {
                        if (finalWireColor.equals(GamePanel.INVALID_WIRING_COLOR) ||
                                (finalWireColor.equals(GamePanel.DEFAULT_WIRING_COLOR) &&
                                        (Objects.equals(releasePort.getParentSystem(), startPort.getParentSystem()) ||
                                                releasePort.getType() != NetworkEnums.PortType.INPUT ||
                                                releasePort.isConnected())) ) {
                            if (!game.isMuted()) game.playSoundEffect("error");
                        }
                    } else if (!connectionMade && releasePort == null) {
                        // No sound for releasing in empty space
                    }
                }
                gamePanel.cancelWiring();
            } else if (gamePanel.isRelayPointDragMode()) {
                gamePanel.stopRelayPointDrag();
            }
        }
    }
    @Override
    public void mouseDragged(MouseEvent e) {
        if (gamePanel.isGameOver() || gamePanel.isLevelComplete() || gamePanel.isSimulationStarted()) {
            return;
        }

        if (SwingUtilities.isLeftMouseButton(e)) {
            Point currentDragPos = e.getPoint();
            if (gamePanel.isWireDrawingMode()) {
                gamePanel.updateDragPos(currentDragPos);
                gamePanel.updateWiringPreview(currentDragPos);
            } else if (gamePanel.isRelayPointDragMode()) {
                gamePanel.updateDraggedRelayPointPosition(currentDragPos);
            }
        }
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        if (gamePanel.isWireDrawingMode()
                || gamePanel.isRelayPointDragMode()
                || gamePanel.isGameOver()
                || gamePanel.isLevelComplete()
                || (gamePanel.isSimulationStarted() && gamePanel.isGamePaused()))
        {
            if (!gamePanel.isWireDrawingMode() && !gamePanel.isRelayPointDragMode()) gamePanel.setCursor(Cursor.getDefaultCursor());
            gamePanel.clearAllHoverStates();
            gamePanel.setToolTipText(null);
            return;
        }

        Point currentPoint = e.getPoint();
        gamePanel.clearAllHoverStates(); // Clear previous hover states before checking new ones

        Port portUnderMouse = gamePanel.findPortAt(currentPoint);
        Wire.RelayPoint relayUnderMouse = gamePanel.findRelayPointAt(currentPoint);
        Wire wireUnderMouse = (relayUnderMouse == null) ? gamePanel.findWireAt(currentPoint, 5.0) : null;

        Cursor currentCursor = Cursor.getDefaultCursor();
        String tooltipText = null;

        if (relayUnderMouse != null) {
            relayUnderMouse.setHovered(true);
            currentCursor = Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR);
            tooltipText = "Relay Point (Left-Click to Drag, Right-Click to Delete)";
        }
        else if (portUnderMouse != null) {
            tooltipText = generatePortTooltip(portUnderMouse);
            if (!gamePanel.isSimulationStarted()) {
                if (!portUnderMouse.isConnected()) {
                    currentCursor = (portUnderMouse.getType() == NetworkEnums.PortType.OUTPUT) ?
                            Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR) :
                            Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
                }
            }
        }
        else if (wireUnderMouse != null) {
            if (!gamePanel.isSimulationStarted()) {
                currentCursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
                tooltipText = "Wire " + wireUnderMouse.getId() + " (Left-Click to Add Relay, Right-Click to Delete)";
            }
        }
        gamePanel.setCursor(currentCursor);
        gamePanel.setToolTipText(tooltipText);
        gamePanel.repaint(); // Repaint to show hover effect
    }

    private String generatePortTooltip(Port port) {
        if (port == null) return null;
        System portParent = port.getParentSystem();
        String tooltip = "Port: " + port.getShape() + " (" + port.getType() + ")";
        if (portParent != null) tooltip += " on Sys " + portParent.getId();

        if (!gamePanel.isSimulationStarted()) {
            tooltip += port.isConnected() ? ", Connected" : ", Available";
            if (!port.isConnected()) {
                tooltip += (port.getType() == NetworkEnums.PortType.OUTPUT) ?
                        " (Left-Click to start wire)" :
                        " (Connect wire here)";
            }
        } else {
            tooltip += port.isConnected() ? ", Connected" : ", Available (Sim Running)";
        }
        return tooltip;
    }
}