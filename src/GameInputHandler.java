import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.awt.geom.Line2D;


public class GameInputHandler implements KeyListener, MouseListener, MouseMotionListener {
    private final GamePanel gamePanel;
    
    private final NetworkGame game; 

    public GameInputHandler(GamePanel gamePanel, NetworkGame game) {
        this.gamePanel = Objects.requireNonNull(gamePanel, "GamePanel cannot be null");
        this.game = Objects.requireNonNull(game, "NetworkGame cannot be null");
        
    }

    @Override public void keyTyped(KeyEvent e) {  }
    @Override public void keyReleased(KeyEvent e) {  }

    @Override
    public void keyPressed(KeyEvent e) {
        int keyCode = e.getKeyCode();

        
        if (keyCode == KeyEvent.VK_ESCAPE) {
            handleEscapeKey();
            return; 
        }
        if (keyCode == KeyEvent.VK_H) {
            gamePanel.toggleHUD();
            e.consume(); 
            
        }

        

        
        
        if (gamePanel.isGameOver() || gamePanel.isLevelComplete()) {
            e.consume();
            return;
        }

        
        if (!gamePanel.isSimulationStarted()) {
            switch (keyCode) {
                case KeyEvent.VK_LEFT:
                    gamePanel.decrementViewedTime(); 
                    e.consume(); 
                    break;
                case KeyEvent.VK_RIGHT:
                    gamePanel.incrementViewedTime(); 
                    e.consume(); 
                    break;
                case KeyEvent.VK_ENTER:
                    gamePanel.attemptStartSimulation(); 
                    e.consume();
                    break;
                
                
                case KeyEvent.VK_P:
                case KeyEvent.VK_S:
                    e.consume(); 
                    break;
            }
        }
        
        else {
            switch (keyCode) {
                case KeyEvent.VK_P:
                    gamePanel.pauseGame(!gamePanel.isGamePaused()); 
                    e.consume();
                    break;
                
                case KeyEvent.VK_S: 
                    
                    if (gamePanel.isSimulationStarted() && gamePanel.isGamePaused()) {
                        
                        SwingUtilities.invokeLater(() -> {
                            
                            game.showStore();
                        });
                    } else if (gamePanel.isSimulationStarted() && !gamePanel.isGamePaused()){
                        
                        java.lang.System.out.println("Store key pressed while running. Pause the game ('P') first to open store.");
                        game.playSoundEffect("error"); 
                    }
                    
                    e.consume();
                    break;
                
                case KeyEvent.VK_LEFT:
                case KeyEvent.VK_RIGHT:
                case KeyEvent.VK_ENTER:
                    e.consume();
                    break;
            }
        }
    }

    
    private void handleEscapeKey() {
        
        if (gamePanel.isSimulationStarted() && !gamePanel.isGameOver() && !gamePanel.isLevelComplete()) {
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
            java.lang.System.out.println("Wire drawing cancelled (mouse exited panel).");
            gamePanel.cancelWiring();
            
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
            
            
            if (!gamePanel.isSimulationStarted() && !gamePanel.isWireDrawingMode()) {
                Port clickedPort = findPortAt(pressPoint);
                
                if (clickedPort != null &&
                        clickedPort.getType() == NetworkEnums.PortType.OUTPUT &&
                        !clickedPort.isConnected())
                {
                    gamePanel.startWiringMode(clickedPort, pressPoint);
                }
            }
            
        }
        
        else if (SwingUtilities.isRightMouseButton(e)) {
            
            
            if (gamePanel.isWireDrawingMode()) {
                java.lang.System.out.println("Wire drawing cancelled (right-click).");
                gamePanel.cancelWiring();
            }
            
            
            else if (!gamePanel.isSimulationStarted()) {
                Wire wireToDelete = findWireAt(pressPoint);
                if (wireToDelete != null) {
                    gamePanel.deleteWireRequest(wireToDelete);
                }
            }
            
            else if (gamePanel.isSimulationStarted()){
                
                java.lang.System.out.println("Cannot delete wire while simulation running.");
                game.playSoundEffect("error");
            }
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        
        if (gamePanel.isGameOver() || gamePanel.isLevelComplete()) {
            return;
        }

        
        
        
        if (gamePanel.isWireDrawingMode() && SwingUtilities.isLeftMouseButton(e)) {
            
            if (!gamePanel.isSimulationStarted()) {
                Point releasePoint = e.getPoint();
                Port releasePort = findPortAt(releasePoint);
                boolean connectionMade = false;
                Port startPort = gamePanel.getSelectedOutputPort();

                
                if (startPort != null && releasePort != null &&
                        releasePort.getType() == NetworkEnums.PortType.INPUT &&
                        !releasePort.isConnected() &&
                        !Objects.equals(releasePort.getParentSystem(), startPort.getParentSystem()))
                {
                    
                    connectionMade = gamePanel.attemptWireCreation(startPort, releasePort);
                }

                
                if (!connectionMade && releasePort != null) {
                    game.playSoundEffect("wire_fail");
                } else if (!connectionMade && releasePort == null) {
                    
                }
            }
            
            gamePanel.cancelWiring();
        }
    }


    

    @Override
    public void mouseDragged(MouseEvent e) {
        
        if (gamePanel.isGameOver() || gamePanel.isLevelComplete()) {
            return;
        }
        
        if (gamePanel.isWireDrawingMode() && !gamePanel.isSimulationStarted() && SwingUtilities.isLeftMouseButton(e)) {
            gamePanel.updateDragPos(e.getPoint());
            gamePanel.repaint(); 
        }
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        
        if (gamePanel.isWireDrawingMode()
                || gamePanel.isGameOver()
                || gamePanel.isLevelComplete()
                || (gamePanel.isSimulationStarted() && gamePanel.isGamePaused()))
        {
            
            if (!gamePanel.isWireDrawingMode()) gamePanel.setCursor(Cursor.getDefaultCursor());
            gamePanel.setToolTipText(null);
            return;
        }

        
        Point currentPoint = e.getPoint();
        Port portUnderMouse = findPortAt(currentPoint);
        Wire wireUnderMouse = findWireAt(currentPoint);

        Cursor currentCursor = Cursor.getDefaultCursor();
        String tooltipText = null;

        
        if (portUnderMouse != null) {
            tooltipText = generatePortTooltip(portUnderMouse);
            
            if (!gamePanel.isSimulationStarted() && !portUnderMouse.isConnected()) {
                currentCursor = (portUnderMouse.getType() == NetworkEnums.PortType.OUTPUT) ?
                        Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR) : 
                        Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);       
            }
        }
        
        else if (wireUnderMouse != null) {
            
            if (!gamePanel.isSimulationStarted()) {
                currentCursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR); 
                tooltipText = "Wire " + wireUnderMouse.getId() + " (Right-Click to Delete)";
            } else {
                
                
            }
        }

        
        gamePanel.setCursor(currentCursor);
        gamePanel.setToolTipText(tooltipText); 
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
                        " (Click to start wire)" :
                        " (Connect wire here)";
            }
        } else {
            
            tooltip += port.isConnected() ? ", Connected" : ", Available";
        }
        return tooltip;
    }

    
    private Port findPortAt(Point p) {
        if (p == null) return null;
        synchronized (gamePanel.getSystems()) { 
            List<System> systemsSnapshot = new ArrayList<>(gamePanel.getSystems()); 
            for (System s : systemsSnapshot) {
                if (s != null) {
                    Port port = s.getPortAt(p);
                    if (port != null) return port;
                }
            }
        }
        return null;
    }

    
    private Wire findWireAt(Point p) {
        if (p == null) return null;
        final double CLICK_THRESHOLD = 10.0;
        final double CLICK_THRESHOLD_SQ = CLICK_THRESHOLD * CLICK_THRESHOLD;
        Wire closestWire = null;
        double minDistanceSq = Double.MAX_VALUE;

        synchronized (gamePanel.getWires()) { 
            List<Wire> wiresSnapshot = new ArrayList<>(gamePanel.getWires()); 
            for (Wire w : wiresSnapshot) {
                if (w == null || w.getStartPort() == null || w.getEndPort() == null ||
                        w.getStartPort().getPosition() == null || w.getEndPort().getPosition() == null) {
                    continue; 
                }
                Point start = w.getStartPort().getPosition();
                Point end = w.getEndPort().getPosition();
                double distSq = Line2D.ptSegDistSq(start.x, start.y, end.x, end.y, p.x, p.y);

                if (distSq < minDistanceSq) {
                    minDistanceSq = distSq;
                    closestWire = w;
                }
            }
        }
        return (closestWire != null && minDistanceSq < CLICK_THRESHOLD_SQ) ? closestWire : null;
    }

}
