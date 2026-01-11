package com.cinema.booking.prototype.utils;

import com.cinema.booking.prototype.model.Seat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class CinemaUtils {

    /**
     * Helper to create a list of seats for mocking the repository result.
     * Index 0 is row A (furthest from screen).
     * @param totalRows The number of rows.
     * @param seatsPerRow The number of seats per row.
     * @param bookedSeats A list of seat labels (e.g., "B3", "A1") to mark as booked.
     */
    public static List<Seat> createMockSeats(int totalRows, int seatsPerRow, List<String> bookedSeats) {
        List<Seat> allSeats = new ArrayList<>();
        for (int r = 0; r < totalRows; r++) {
            char rowLabel = (char) ('A' + r);
            for (int s = 1; s <= seatsPerRow; s++) {
                Seat seat = new Seat(String.valueOf(rowLabel), r, s);
                if (bookedSeats.contains(rowLabel + String.valueOf(s))) {
                    seat.setBooked(true);
                }
                allSeats.add(seat);
            }
        }

        allSeats.sort(Comparator
                .comparing(Seat::getRowIndex)
                .thenComparing(Seat::getSeatNumber));
        return allSeats;
    }
}