package com.cinema.booking.prototype.model;

import jakarta.persistence.*;

@Entity
@Table(name = "seat")
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String rowLabel;
    private int rowIndex;
    private int seatNumber;
    private boolean booked;

    @ManyToOne
    @JoinColumn(name = "booking_id")
    private Booking booking;

    public Seat() {
    }

    public Seat(String rowLabel, int rowIndex, int seatNumber) {
        this.rowLabel = rowLabel;
        this.rowIndex = rowIndex;
        this.seatNumber = seatNumber;
        this.booked = false;
    }

    public String getRowLabel() { return rowLabel; }

    public int getRowIndex() {
        return rowIndex;
    }

    public int getSeatNumber() {
        return seatNumber;
    }

    public boolean isBooked() { return booked; }

    public void setBooked(boolean booked) {
        this.booked = booked;
    }

    public Booking getBooking() {
        return booking;
    }

    public void setBooking(Booking booking) {
        this.booking = booking;
    }

    public String getSeatLabel() {
        return this.rowLabel + this.seatNumber;
    }

    public boolean isHighlighted(Booking highlightBooking) {
        return this.booked &&
                highlightBooking != null &&
                this.booking != null &&
                this.booking.getId().equals(highlightBooking.getId());
    }
}