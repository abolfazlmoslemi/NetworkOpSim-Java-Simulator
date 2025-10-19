module networkopsim.shared {
    // Requires java.desktop for common classes like Point, Color, Point2D, etc.
    requires java.desktop;

    // Exports packages to be used by Core, Server and Client modules
    exports com.networkopsim.shared.dto;
    exports com.networkopsim.shared.model;
    exports com.networkopsim.shared.net;
}