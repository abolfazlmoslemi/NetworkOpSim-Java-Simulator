module networkopsim.common {
    // We need java.desktop for classes like Point, Color, etc.
    requires java.desktop;

    // Export all packages so server and client can use them
    exports com.networkopsim.game.model.core;
    exports com.networkopsim.game.model.enums;
    exports com.networkopsim.game.model.state;
    exports com.networkopsim.game.net;
}