package com.networkopsim.shared.model;

/**
 * A final class to hold globally shared constants for the game.
 * This prevents circular dependencies between modules that might need these values.
 */
public final class GameConstants {

    /** Private constructor to prevent instantiation of this utility class. */
    private GameConstants() {}

    /** The standard width of the game window. */
    public static final int WINDOW_WIDTH = 1200;

    /** The standard height of the game window. */
    public static final int WINDOW_HEIGHT = 800;

}