package com.cinema.booking.prototype.strategy;

import com.cinema.booking.prototype.model.Seat;

import java.util.List;

public interface BookingStrategy {

    /**
     * Finds and marks the required number of seats based on the strategy.
     * @param count The number of seats required.
     * @param position Optional starting position for manual strategy.
     * @return List of selected seats (not yet persisted/assigned to a Booking object).
     * @throws Exception if seats cannot be found.
     */
    List<Seat> bookSeats(int count, String position) throws Exception;
}