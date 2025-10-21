// ===== File: module-info.java (FINAL CORRECTED - No utils export) =====
// ===== MODULE: common =====

module networkopsim.common {
    // We need java.desktop for classes like Point, Color, etc. used in data models.
    requires java.desktop;
    // SLF4J API might be used by model classes for logging, so it's good practice.
    requires org.slf4j;

    // Export all packages so server and client can use them
    exports com.networkopsim.game.model.core;
    exports com.networkopsim.game.model.enums;
    exports com.networkopsim.game.model.state;
    exports com.networkopsim.game.net;
}