package com.cinema.booking.prototype.strategy;

import com.cinema.booking.prototype.model.Seat;
import com.cinema.booking.prototype.repository.SeatRepository;
import com.cinema.booking.prototype.utils.CinemaUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
public class ManualBookingStrategyTest {

    @Mock
    private SeatRepository seatRepository;

    @Mock
    @Qualifier("defaultBookingStrategy")
    private BookingStrategy defaultStrategy;

    @InjectMocks
    private ManualBookingStrategy manualBookingStrategy;

    private List<Seat> allMockSeats;

    /**
     * Sets up a standard 2x10 cinema (A, B) using CinemaUtils.
     * Pre-booked seats: A5 and A6.
     * Row A (Index 0): A1-A4, A5(booked), A6(booked), A7-A10
     * Row B (Index 1): B1-B10 (all available)
     */
    @BeforeEach
    void setUp() {
        allMockSeats = CinemaUtils.createMockSeats(2, 10, List.of("A5", "A6"));
        Mockito.lenient().when(seatRepository.findAll()).thenReturn(allMockSeats);
    }

    @Test
    void testBookSeats_InvalidCount_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            manualBookingStrategy.bookSeats(0, "A1");
        }, "Should reject non-positive ticket count.");
    }

    @Test
    void testBookSeats_MissingPosition_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            manualBookingStrategy.bookSeats(1, null);
        }, "Should reject null position.");
        assertThrows(IllegalArgumentException.class, () -> {
            manualBookingStrategy.bookSeats(1, " ");
        }, "Should reject empty position.");
    }

    @Test
    void testBookSeats_InvalidPositionFormat_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            manualBookingStrategy.bookSeats(1, "A");
        }, "Should reject format with no seat number.");

        assertThrows(IllegalArgumentException.class, () -> {
            manualBookingStrategy.bookSeats(1, "1A");
        }, "Should reject format starting with number.");
    }

    @Test
    void testBookSeats_RowDoesNotExist_ThrowsException() {
        assertThrows(Exception.class, () -> {
            manualBookingStrategy.bookSeats(1, "Z5");
        }, "Should throw if row label does not exist.");
    }

    @Test
    void testFourBookSeats_SingleRow_Consecutively_Success() throws Exception {
        // Request 4 tickets starting at seat A1
        List<Seat> result = manualBookingStrategy.bookSeats(4, "A1");

        // Should get booking A1, A2, A3, A4
        assertEquals(4, result.size());
        assertEquals("A1", result.getFirst().getSeatLabel());
        assertEquals("A4", result.getLast().getSeatLabel());
    }

    @Test
    void testFiveBookSeats_SingleRow_SkipBookSeat_success() throws Exception {
        // Seats A5, A6 are booked. Request to book 5 tickets starting at position A4.
        // It filters AVAILABLE seats starting from position A4, which are: [A4, A7, A8, A9, A10]
        List<Seat> result = manualBookingStrategy.bookSeats(5, "A4");

        assertEquals(5, result.size());
        assertEquals("A4", result.getFirst().getSeatLabel());
    }

    @Test
    void testBookSeats_MultiRowOverflow_SuccessWithMiddleCluster() throws Exception {
        // Request 8 tickets starting at A8.
        // Row A: Available seats >= A8 are A8, A9, A10 (3 seats)
        // Remaining: 5. Overflow to Row B.
        // Row B (10 seats): Should pick the 5-seat block closest to the middle (B3-B7).

        List<Seat> result = manualBookingStrategy.bookSeats(8, "A8");

        assertEquals(8, result.size());

        // Check Row A seats (A8-A10)
        List<Seat> aSeats = result.stream().filter(s -> s.getRowLabel().equals("A")).toList();
        assertEquals(3, aSeats.size());
        assertEquals(8, aSeats.getFirst().getSeatNumber());

        // Check Row B seats (B3-B7) - Middle Cluster for 5 seats in a 10-seat row
        List<String> bSeatsLabels = result.stream()
                .filter(s -> s.getRowLabel().equals("B"))
                .map(Seat::getSeatLabel)
                .toList();

        assertEquals(5, bSeatsLabels.size());
        assertTrue(bSeatsLabels.contains("B3"), "Overflow should start at B3 (Middle Cluster)");
        assertTrue(bSeatsLabels.contains("B7"), "Overflow should end at B7 (Middle Cluster)");
    }

    @Test
    void testBookSeats_MultiRowOverflow_FullFulfillmentFailure() {
        // Total available seats: 18 (8 in A, 10 in B).
        // Request 19 tickets. Row A availability: A1-A4, A7-A10 (6 seats).
        // Total found: 6 (A) + 10 (B) = 16. Remaining: 3.

        assertThrows(Exception.class, () -> {
            manualBookingStrategy.bookSeats(19, "A1");
        }, "Should throw exception if final count cannot be fulfilled (16/19).");
    }

}