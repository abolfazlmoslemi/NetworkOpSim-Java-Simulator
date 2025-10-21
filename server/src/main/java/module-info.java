// ===== File: module-info.java (FINAL CORRECTED) =====
// ===== MODULE: server =====

module networkopsim.server {
    // Server depends on the common module
    requires networkopsim.common;

    // Server needs logging and java.desktop for some data types (like Point)
    requires org.slf4j;
    requires java.desktop;

    // Server does not need to export its internal packages
}