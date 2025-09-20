module networkoperatorsimulator.appmodule {
    requires java.desktop;          // Swing/AWT
    requires org.slf4j;             // SLF4J API
    requires ch.qos.logback.classic; // (logback has named modules since 1.4)

    // Export ONLY the packages that actually exist now:
    exports com.networkopsim.game.controller.core;
    exports com.networkopsim.game.controller.input;

    // NEW EXPORT: This line is added to make GameEngine visible to GamePanel.
    exports com.networkopsim.game.controller.logic;

    exports com.networkopsim.game.model.core;
    exports com.networkopsim.game.model.enums;
    exports com.networkopsim.game.model.state;

    exports com.networkopsim.game.view.panels;
    exports com.networkopsim.game.view.dialogs;
    exports com.networkopsim.game.view.rendering;

    exports com.networkopsim.game.utils;
}