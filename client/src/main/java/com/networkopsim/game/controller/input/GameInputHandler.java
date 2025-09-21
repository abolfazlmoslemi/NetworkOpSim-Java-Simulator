// ===== File: GameInputHandler.java (FINAL COMPLETE VERSION for Client) =====
// ===== MODULE: client =====

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
      if (action == KeyBindings.GameAction.START_SIMULATION_SCRUB_MODE) {
        gamePanel.attemptStartSimulation();
        e.consume();
      }
    } else {
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
    } else if (gamePanel.isSimulationStarted() && !gamePanel.isGameOver() && !gamePanel.isLevelComplete()) {
      boolean wasPaused = gamePanel.isGamePaused();
      gamePanel.pauseGame(true);
      int choice = JOptionPane.showConfirmDialog(
              game,
              "Return to the main menu?\nGame progress will be lost.",
              "Exit Level?",
              JOptionPane.YES_NO_OPTION,
              JOptionPane.QUESTION_MESSAGE);
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
  public void mouseClicked(MouseEvent e) {  }

  @Override
  public void mouseEntered(MouseEvent e) {  }

  @Override
  public void mouseExited(MouseEvent e) {
    if (gamePanel.isWireDrawingMode()) {
      gamePanel.cancelWiring();
    }
  }

  @Override
  public void mousePressed(MouseEvent e) {
    if (gamePanel.isGameOver() || gamePanel.isLevelComplete()) return;

    Point pressPoint = e.getPoint();
    gamePanel.requestFocusInWindow();

    if (SwingUtilities.isLeftMouseButton(e)) {
      if (gamePanel.isSimulationStarted()) return;
      if (gamePanel.isWireDrawingMode()) return;

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

      if (!gamePanel.isSimulationStarted()) {
        Wire.RelayPoint relayToDelete = findRelayPointAt(pressPoint);
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
            if (!game.isMuted()) game.playSoundEffect("error");
          }
        }
        gamePanel.cancelWiring();
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
    Cursor currentCursor = Cursor.getDefaultCursor();
    String tooltipText = null;

    if (gamePanel.isSimulationStarted() && gamePanel.isGamePaused()) {
      gamePanel.setCursor(Cursor.getDefaultCursor());
      gamePanel.setToolTipText(null);
      return;
    }

    Port portUnderMouse = gamePanel.findPortAt(currentPoint);
    Wire.RelayPoint relayUnderMouse = findRelayPointAt(currentPoint);
    Wire wireUnderMouse = (relayUnderMouse == null) ? gamePanel.findWireAt(currentPoint, 5.0) : null;

    if (relayUnderMouse != null) {
      relayUnderMouse.setHovered(true);
      currentCursor = Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR);
      tooltipText = "Relay Point (Right-Click to Delete)";
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

    gamePanel.setCursor(currentCursor);
    gamePanel.setToolTipText(tooltipText);
    gamePanel.repaint();
  }

  private Wire.RelayPoint findRelayPointAt(Point p) {
    if (p == null) return null;
    for (Wire w : gamePanel.getWires()) {
      for (Wire.RelayPoint rp : w.getRelayPoints()) {
        if (rp.contains(p)) return rp;
      }
    }
    return null;
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