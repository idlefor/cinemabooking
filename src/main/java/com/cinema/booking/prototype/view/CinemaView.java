package com.cinema.booking.prototype.view;

import com.cinema.booking.prototype.model.Booking;
import com.cinema.booking.prototype.model.Seat;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CinemaView {

    public void printWelcome(String title, long availableSeats) {
        System.out.println("\nWelcome to GIC Cinemas");
        System.out.println("[1] Book tickets for " + title + " (" + availableSeats + " seats available)");
        System.out.println("[2] Check bookings");
        System.out.println("[3] Exit");
        System.out.print("Please enter your selection:\n> ");
    }

    public void printBookingSuccess(Booking booking, int count, String movieTitle) {
        System.out.println("Successfully reserved " + count + " " + movieTitle + " tickets.");
        System.out.println("Booking id: " + booking.getBookingReference());
        System.out.println("Selected seats:");
    }

    public void printCinemaMap(List<Seat> allSeats, int totalRows, int seatsPerRow, Booking highlightBooking) {
        System.out.println("\n      S C R E E N");
        System.out.println("----------------------");

        // Logic purely for rendering
        for (int r = totalRows - 1; r >= 0; r--) {
            int finalR = r;
            // s.getRowIndex() access remains here for filtering, which groups seats by row.
            List<Seat> row = allSeats.stream().filter(s -> s.getRowIndex() == finalR).toList();

            char rowLabel = (char) ('A' + r);
            System.out.print(rowLabel + " ");

            for (Seat s : row) {
                String symbol = ". ";

                // Refactored: Assumes Seat class now handles the display state (isHighlighted).
                if (s.isHighlighted(highlightBooking)) {
                    symbol = "o "; // Highlighted booking
                } else if (s.isBooked()) {
                    symbol = "# "; // Other booking
                }

                System.out.print(symbol);
            }
            System.out.println();
        }

        System.out.print("  ");
        for (int i = 1; i <= seatsPerRow; i++) {
            System.out.print(i + " ");
        }
        System.out.println("\n");
    }

    public void printMessage(String msg) {
        System.out.println(msg);
    }
}