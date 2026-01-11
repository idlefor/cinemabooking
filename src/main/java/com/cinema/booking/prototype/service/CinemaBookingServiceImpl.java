package com.cinema.booking.prototype.service;

import com.cinema.booking.prototype.model.Booking;
import com.cinema.booking.prototype.model.Seat;
import com.cinema.booking.prototype.repository.BookingRepository;
import com.cinema.booking.prototype.repository.SeatRepository;
import com.cinema.booking.prototype.strategy.BookingStrategy;
import com.cinema.booking.prototype.view.CinemaView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class CinemaBookingServiceImpl implements CinemaBookingService {

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private CinemaView cinemaView;

    @Autowired
    @Qualifier("defaultBookingStrategy")
    private BookingStrategy defaultStrategy;

    @Autowired
    @Qualifier("manualBookingStrategy")
    private BookingStrategy manualStrategy;

    private String movieTitle;
    private int totalRows;
    private int seatsPerRow;
    private final AtomicLong bookingCounter = new AtomicLong(1);

    public void handleBooking(Scanner scanner) {
        int count;
        while (true) {
            cinemaView.printMessage("Enter number of tickets to book, or enter blank to go back to main menu:");
            System.out.print("> ");
            String countStr = scanner.nextLine().trim();

            if (countStr.isEmpty()) return;

            try {
                count = Integer.parseInt(countStr);
            } catch (NumberFormatException e) {
                cinemaView.printMessage("Invalid number. Please enter a positive integer.");
                continue;
            }

            int availableSeats = getAvailableSeatsCount();

            if (count <= 0) {
                cinemaView.printMessage("Ticket count must be positive.");
                continue;
            }

            if (count > availableSeats) {
                cinemaView.printMessage("Sorry, there are only " + availableSeats + " seats available.");
                continue;
            }
            break;
        }

        try {
            cinemaView.printMessage("Enter preferred starting position (e.g., B03) or enter blank for default best fit.");
            System.out.print("> ");
            String position = scanner.nextLine().trim();

            String bookingPosition = position.isEmpty() ? null : position;

            Booking booking = createBooking(count, bookingPosition);

            cinemaView.printBookingSuccess(booking, count, getMovieTitle());
            cinemaView.printCinemaMap(
                    getAllSeats(),
                    getTotalRows(),
                    getSeatsPerRow(),
                    booking
            );

            cinemaView.printMessage("Booking id: " + booking.getBookingReference() + " confirmed.");

        } catch (Exception e) {
            cinemaView.printMessage("Error processing booking: " + e.getMessage());
        }
    }

    public void handleCheckBooking(Scanner scanner) {
        cinemaView.printMessage("Enter booking id, or enter blank to go back to main menu:");
        System.out.print("> ");
        String id = scanner.nextLine().trim();
        if (id.isEmpty()) return;

        Booking booking = getBooking(id);
        if (booking == null) {
            cinemaView.printMessage("Booking not found.");
            return;
        }

        cinemaView.printMessage("Booking id: " + booking.getBookingReference());
        cinemaView.printMessage("Selected seats:");
        cinemaView.printCinemaMap(
                getAllSeats(),
                getTotalRows(),
                getSeatsPerRow(),
                booking);
    }

    @Override
    public void initializeCinema(String title, int rows, int seats) {
        this.movieTitle = title;
        this.totalRows = rows;
        this.seatsPerRow = seats;

        seatRepository.deleteAll();
        for (int r = 0; r < rows; r++) {
            char rowLabel = (char) ('A' + r);
            for (int s = 1; s <= seats; s++) {
                seatRepository.save(new Seat(String.valueOf(rowLabel), r, s));
            }
        }
    }

    @Override
    @Transactional
    public Booking createBooking(int count, String position) throws Exception {
        // 1. Determine which strategy to use
        BookingStrategy strategy = (position == null || position.isEmpty()) ? defaultStrategy : manualStrategy;

        // 2. Execute the strategy to get the potential Seat entities
        List<Seat> selectedSeats = strategy.bookSeats(count, position);

        if (selectedSeats != null && !selectedSeats.isEmpty()) {
            for (Seat seat : selectedSeats) {
                if (seat.isBooked()) {
                    throw new Exception("Conflict detected: Seat " + seat.getRowLabel() + seat.getSeatNumber() + " is already booked.");
                }
            }

            // A. Create the new Booking object (Transient state)
            Booking newBooking = new Booking();
            newBooking.setBookingReference("GIC" + String.format("%04d", bookingCounter.getAndIncrement()));

            // B. Immediately save the transient Booking to make it PERSISTENT.
            // This generates the ID and prevents the TransientPropertyValueException.
            // The returned object (persistentBooking) is the managed instance.
            Booking persistentBooking = bookingRepository.save(newBooking);

            // B. Process and link the seats
            for (Seat seat : selectedSeats) {
                // Use the persistent Booking object for linking.
                // The addSeat method in Booking.java handles the crucial steps:
                // 1. persistentBooking.getSeats().add(seat)
                // 2. seat.setBooking(persistentBooking)  <-- Sets FK to the now-persistent parent
                // 3. seat.setBooked(true)
                persistentBooking.addSeat(seat);
            }

            // C. Final Save/Merge:
            return bookingRepository.save(persistentBooking);
        }

        throw new Exception("The booking strategy failed to reserve " + count + " seats.");
    }

    @Override
    public Booking getBooking(String bookingReference) {
        Optional<Booking> result = bookingRepository.findByBookingReference(bookingReference);
        return result.orElse(null);
    }

    @Override
    public List<Seat> getAllSeats() {
        return seatRepository.findAll();
    }

    @Override
    public int getAvailableSeatsCount() {
        return (int) getAllSeats().stream().filter(s -> !s.isBooked()).count();
    }

    @Override
    public String getMovieTitle() {
        return movieTitle;
    }

    @Override
    public int getTotalRows() {
        return totalRows;
    }

    @Override
    public int getSeatsPerRow() {
        return seatsPerRow;
    }
}