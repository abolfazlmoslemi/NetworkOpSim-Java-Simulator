module networkopsim.server {
    // Server depends on the common module
    requires networkopsim.common;

    // Server needs logging and java.desktop for some data types
    requires org.slf4j;
    requires java.desktop;

    // Server does not need to export its internal packages
}