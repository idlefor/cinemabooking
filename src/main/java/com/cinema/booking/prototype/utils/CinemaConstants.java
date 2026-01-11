package com.cinema.booking.prototype.utils;

public class CinemaConstants {

    // Configuration constants for cinema hall setup
    public static final int MAX_ROWS = 26;
    public static final int MAX_SEATS_PER_ROW = 50;

    // Regular expression pattern for initializing the cinema system:
    // Captures: 1. Movie Title (non-greedy, one or more chars) 2. Rows (one or more digits) 3. Seats (one or more digits)
    public static final String INITIAL_SETUP_REGEX = "(.+) (\\d+) (\\d+)";
}