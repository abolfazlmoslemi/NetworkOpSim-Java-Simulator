package com.networkopsim.game.controller.input;

import com.networkopsim.game.controller.core.NetworkGame;
import com.networkopsim.game.model.core.Port;
import com.networkopsim.game.model.core.System;
import com.networkopsim.game.model.core.Wire;
import com.networkopsim.game.model.enums.NetworkEnums;
import com.networkopsim.game.utils.KeyBindings;
import com.networkopsim.game.view.panels.GamePanel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.Objects;

/**
 * Handles all keyboard and mouse input for the GamePanel.
 * It translates raw input events into game actions by calling methods on the GamePanel.
 * This class remains largely unchanged in the new architecture, as it already correctly
 * communicates with its parent View (GamePanel) rather than the core logic.
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
    public void keyTyped(KeyEvent e) {  }

    @Override
    public void keyReleased(KeyEvent e) {  }

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
            // Pre-simulation logic
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
        } else {
            // In-simulation logic
            if (gamePanel.getCurrentInteractiveMode() != GamePanel.InteractiveMode.NONE) {
                e.consume();
                return;
            }

            if (action == KeyBindings.GameAction.PAUSE_RESUME_GAME) {
                gamePanel.pauseGame(!gamePanel.isGamePaused());
                e.consume();
            } else if (action == KeyBindings.GameAction.OPEN_STORE) {
                if (!gamePanel.isGamePaused()) {
                    gamePanel.pauseGame(true);
                }
                SwingUtilities.invokeLater(() -> game.showStore());
                e.consume();
            }
        }
    }

    private void handleEscapeKey() {
        if (gamePanel.getCurrentInteractiveMode() != GamePanel.InteractiveMode.NONE) {
            gamePanel.cancelAllInteractiveModes();
            game.showTemporaryMessage("Action Canceled", Color.ORANGE, 1500);
            if(gamePanel.isSimulationStarted()) {
                gamePanel.pauseGame(true);
            }
            return;
        }

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
        } else {
            game.returnToMenu();
        }
    }

    @Override
    public void mouseClicked(MouseEvent e) {  }

    @Override
    public void mouseEntered(MouseEvent e) {  }

    @Override
    public void mouseExited(MouseEvent e) {
        if (gamePanel.isWireDrawingMode()) {
            gamePanel.cancelWiring();
        }
        if (gamePanel.isRelayPointDragMode()) {
            gamePanel.cancelRelayPointDrag();
        }
        if (gamePanel.getCurrentInteractiveMode() == GamePanel.InteractiveMode.SISYPHUS_DRAG && gamePanel.getSisyphusDraggedSystem() != null) {
            gamePanel.stopSisyphusDrag(); // Stop if mouse leaves panel
        }
    }

    @Override
    public void mousePressed(MouseEvent e) {
        if (gamePanel.isGameOver() || gamePanel.isLevelComplete()) return;

        Point pressPoint = e.getPoint();
        gamePanel.requestFocusInWindow();

        GamePanel.InteractiveMode mode = gamePanel.getCurrentInteractiveMode();

        if (mode != GamePanel.InteractiveMode.NONE) {
            if (SwingUtilities.isRightMouseButton(e)) {
                gamePanel.cancelAllInteractiveModes();
                game.showTemporaryMessage("Action Canceled", Color.ORANGE, 1500);
                if(gamePanel.isSimulationStarted()){
                    gamePanel.pauseGame(true);
                }
                return;
            }

            if (SwingUtilities.isLeftMouseButton(e)) {
                switch (mode) {
                    case AERGIA_PLACEMENT:
                    case ELIPHAS_PLACEMENT:
                        Wire clickedWire = gamePanel.findWireAt(pressPoint, 5.0);
                        if (clickedWire != null) {
                            if (mode == GamePanel.InteractiveMode.AERGIA_PLACEMENT) {
                                gamePanel.placeAergiaEffect(clickedWire, pressPoint);
                            } else {
                                gamePanel.placeEliphasEffect(clickedWire, pressPoint);
                            }
                        } else {
                            if (!game.isMuted()) game.playSoundEffect("error");
                            game.showTemporaryMessage("You must click on a wire.", Color.RED, 2000);
                        }
                        break;

                    case SISYPHUS_DRAG:
                        System clickedSystem = gamePanel.findSystemAt(pressPoint);
                        if (clickedSystem != null && !clickedSystem.isReferenceSystem()) {
                            gamePanel.startSisyphusDrag(clickedSystem, pressPoint);
                        } else {
                            if (!game.isMuted()) game.playSoundEffect("error");
                            game.showTemporaryMessage("Click on a non-reference system (e.g., Node, Spy).", Color.RED, 2500);
                        }
                        break;
                }
            }
            return;
        }

        if (SwingUtilities.isLeftMouseButton(e)) {
            if (gamePanel.isSimulationStarted()) return;
            if (gamePanel.isWireDrawingMode()) return;

            Wire.RelayPoint relayToDrag = gamePanel.findRelayPointAt(pressPoint);
            if (relayToDrag != null) {
                gamePanel.startRelayPointDrag(relayToDrag);
                return;
            }

            Port clickedPort = gamePanel.findPortAt(pressPoint);
            if (clickedPort != null && clickedPort.getType() == NetworkEnums.PortType.OUTPUT && !clickedPort.isConnected()) {
                gamePanel.startWiringMode(clickedPort, pressPoint);
                gamePanel.updateWiringPreview(pressPoint);
                return;
            }

            Wire wireToAddRelay = gamePanel.findWireAt(pressPoint, 5.0);
            if (wireToAddRelay != null) {
                gamePanel.addRelayPointRequest(wireToAddRelay, pressPoint);
            }

        } else if (SwingUtilities.isRightMouseButton(e)) {
            if (gamePanel.isWireDrawingMode()) {
                gamePanel.cancelWiring();
                return;
            }
            if (gamePanel.isRelayPointDragMode()) {
                gamePanel.cancelRelayPointDrag();
                return;
            }

            if (!gamePanel.isSimulationStarted()) {
                Wire.RelayPoint relayToDelete = gamePanel.findRelayPointAt(pressPoint);
                if (relayToDelete != null) {
                    gamePanel.deleteRelayPointRequest(relayToDelete);
                    return;
                }

                Wire wireToDelete = gamePanel.findWireAt(pressPoint, 10.0);
                if (wireToDelete != null) {
                    gamePanel.deleteWireRequest(wireToDelete);
                }
            } else {
                if(!game.isMuted()) game.playSoundEffect("error");
            }
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        if (gamePanel.isGameOver() || gamePanel.isLevelComplete()) return;

        if (gamePanel.getCurrentInteractiveMode() == GamePanel.InteractiveMode.SISYPHUS_DRAG && gamePanel.getSisyphusDraggedSystem() != null) {
            if (SwingUtilities.isLeftMouseButton(e)) {
                gamePanel.stopSisyphusDrag();
            }
            return;
        }

        if (SwingUtilities.isLeftMouseButton(e)) {
            if (gamePanel.isWireDrawingMode()) {
                if (!gamePanel.isSimulationStarted()) {
                    Point releasePoint = e.getPoint();
                    Port releasePort = gamePanel.findPortAt(releasePoint);
                    Port startPort = gamePanel.getSelectedOutputPort();
                    Color finalWireColor = gamePanel.getCurrentWiringColor();

                    if (startPort != null && releasePort != null &&
                            !Objects.equals(releasePort.getParentSystem(), startPort.getParentSystem()) &&
                            releasePort.getType() == NetworkEnums.PortType.INPUT &&
                            !releasePort.isConnected() &&
                            finalWireColor.equals(GamePanel.VALID_WIRING_COLOR_TARGET)) {
                        gamePanel.attemptWireCreation(startPort, releasePort);
                    } else if (releasePort != null) {
                        if (finalWireColor.equals(GamePanel.INVALID_WIRING_COLOR) ||
                                (finalWireColor.equals(GamePanel.DEFAULT_WIRING_COLOR) &&
                                        (Objects.equals(releasePort.getParentSystem(), startPort.getParentSystem()) ||
                                                releasePort.getType() != NetworkEnums.PortType.INPUT ||
                                                releasePort.isConnected())) ) {
                            if (!game.isMuted()) game.playSoundEffect("error");
                        }
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
        if (gamePanel.getCurrentInteractiveMode() == GamePanel.InteractiveMode.SISYPHUS_DRAG && gamePanel.getSisyphusDraggedSystem() != null) {
            if (SwingUtilities.isLeftMouseButton(e)) {
                gamePanel.updateSisyphusDrag(e.getPoint());
            }
            return;
        }

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
        if (gamePanel.isGameOver() || gamePanel.isLevelComplete()) {
            gamePanel.clearAllHoverStates();
            gamePanel.setCursor(Cursor.getDefaultCursor());
            gamePanel.setToolTipText(null);
            return;
        }

        Point currentPoint = e.getPoint();
        gamePanel.clearAllHoverStates();

        GamePanel.InteractiveMode mode = gamePanel.getCurrentInteractiveMode();
        Cursor currentCursor = Cursor.getDefaultCursor();
        String tooltipText = null;

        if (mode != GamePanel.InteractiveMode.NONE) {
            currentCursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
            switch (mode) {
                case AERGIA_PLACEMENT:
                    tooltipText = "Aergia: Click a wire to place. Right-click to cancel.";
                    if (gamePanel.findWireAt(currentPoint, 5.0) != null) currentCursor = Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR);
                    break;
                case ELIPHAS_PLACEMENT:
                    tooltipText = "Eliphas: Click a wire to place. Right-click to cancel.";
                    if (gamePanel.findWireAt(currentPoint, 5.0) != null) currentCursor = Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR);
                    break;
                case SISYPHUS_DRAG:
                    tooltipText = "Sisyphus: Click a non-reference system to move. Right-click to cancel.";
                    System sys = gamePanel.findSystemAt(currentPoint);
                    if (sys != null && !sys.isReferenceSystem()) currentCursor = Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR);
                    break;
            }
        } else { // Mode is NONE
            if (gamePanel.isSimulationStarted() && gamePanel.isGamePaused()) {
                // No hover effects when paused in simulation
                gamePanel.setCursor(Cursor.getDefaultCursor());
                gamePanel.setToolTipText(null);
                return;
            }

            Port portUnderMouse = gamePanel.findPortAt(currentPoint);
            Wire.RelayPoint relayUnderMouse = gamePanel.findRelayPointAt(currentPoint);
            Wire wireUnderMouse = (relayUnderMouse == null) ? gamePanel.findWireAt(currentPoint, 5.0) : null;

            if (relayUnderMouse != null) {
                relayUnderMouse.setHovered(true);
                currentCursor = Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR);
                tooltipText = "Relay Point (Left-Click to Drag, Right-Click to Delete)";
            } else if (portUnderMouse != null) {
                tooltipText = generatePortTooltip(portUnderMouse);
                if (!gamePanel.isSimulationStarted() && !portUnderMouse.isConnected()) {
                    currentCursor = (portUnderMouse.getType() == NetworkEnums.PortType.OUTPUT) ?
                            Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR) :
                            Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
                }
            } else if (wireUnderMouse != null) {
                if (!gamePanel.isSimulationStarted()) {
                    currentCursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
                    tooltipText = "Wire " + wireUnderMouse.getId() + " (Left-Click to Add Relay, Right-Click to Delete)";
                }
            }
        }

        gamePanel.setCursor(currentCursor);
        gamePanel.setToolTipText(tooltipText);
        gamePanel.repaint();
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
            tooltip += port.isConnected() ? ", Connected" : ", Not editable while simulation is running";
        }
        return tooltip;
    }
}