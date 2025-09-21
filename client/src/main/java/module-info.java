// ===== File: module-info.java (FINAL CORRECTED) =====
// ===== MODULE: client =====

module networkopsim.client {
    // Client depends on the common module
    requires networkopsim.common;

    // Client needs logging, and java.desktop for Swing/AWT UI and sound
    requires org.slf4j;
    requires java.desktop;

    // Client does not need to export its internal packages
}