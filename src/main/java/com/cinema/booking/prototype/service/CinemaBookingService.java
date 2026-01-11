package com.cinema.booking.prototype.service;

import com.cinema.booking.prototype.model.Booking;
import com.cinema.booking.prototype.model.Seat;

import java.util.List;

public interface CinemaBookingService {

    void initializeCinema(String title, int rows, int cols);

    Booking createBooking(int numTickets, String startPosition) throws Exception;

    Booking getBooking(String bookingId);

    String getMovieTitle();

    int getAvailableSeatsCount();

    List<Seat> getAllSeats();

    int getTotalRows();

    int getSeatsPerRow();
}