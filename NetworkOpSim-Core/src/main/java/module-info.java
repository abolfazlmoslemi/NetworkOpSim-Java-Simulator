module networkopsim.core {
    // Requires Shared module for DTOs and Enums
    requires networkopsim.shared;
    // Requires java.desktop for AWT/Swing classes used in the game model (Point, Point2D, etc.)
    requires java.desktop;
    // Requires slf4j for logging within the game engine
    requires org.slf4j;

    // Exports all the game logic and model packages to be used by Client and Server
    exports com.networkopsim.core.game.logic;
    exports com.networkopsim.core.game.logic.behaviors;
    exports com.networkopsim.core.game.model.core;
    exports com.networkopsim.core.game.model.state;
    exports com.networkopsim.core.game.utils;
}