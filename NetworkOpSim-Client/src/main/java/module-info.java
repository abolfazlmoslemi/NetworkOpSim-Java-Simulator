module networkopsim.client {
    // Requires Shared module for DTOs and network classes
    requires networkopsim.shared;
    // Requires Core module for offline game simulation
    requires networkopsim.core;

    // Standard Java modules
    requires java.desktop;
    requires java.sql; // JOptionPane uses this internally sometimes

    // Logging
    requires org.slf4j;
}