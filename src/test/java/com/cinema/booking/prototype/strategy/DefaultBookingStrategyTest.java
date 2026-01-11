package com.cinema.booking.prototype.strategy;

import com.cinema.booking.prototype.model.Seat;
import com.cinema.booking.prototype.repository.SeatRepository;
import com.cinema.booking.prototype.utils.CinemaUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class DefaultBookingStrategyTest {

    @Mock
    private SeatRepository seatRepository;

    @InjectMocks
    private DefaultBookingStrategy defaultBookingStrategy;

    @Test
    void bookSeats_NegativeCount_ThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> {
            defaultBookingStrategy.bookSeats(-1, null);
        }, "Should throw IllegalArgumentException for count <= 0.");
    }

    @Test
    void bookSeats_ZeroCount_ThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> {
            defaultBookingStrategy.bookSeats(0, null);
        }, "Should throw IllegalArgumentException for count <= 0.");
    }

    @Test
    void bookSeats_SingleRowFragmentedBooking_Success() throws Exception {
        // Row A with 10 seats. Seats 5 and 6 are booked (middle gap).
        // Available: (A1-A4), (A7-A10). Request 6 seats.
        // Expectation: The strategy should take the largest block first.
        // 1. Block (A7-A10) center seat value 8.5, dist 3.0. Block (A1-A4) center 2.5, dist 3.0.
        // The loop finds A1-A4 first, then A7-A10.
        List<Seat> mockSeats = CinemaUtils.createMockSeats(1, 10, List.of("A5", "A6"));
        when(seatRepository.findAll()).thenReturn(mockSeats);

        List<Seat> selected = defaultBookingStrategy.bookSeats(6, null);

        assertEquals(6, selected.size());

        // Block 1 (A1-A4) and Block 2 (A7-A10) should be included.
        // Since both blocks have the same size (4) and same distance to middle (3.0),
        // the code should pick the one with the lowest seat number (A1-A4) first
        // due to the inner loop starting from the left.

        // Verify A1-A4 are present
        assertTrue(selected.stream().anyMatch(s -> s.getSeatNumber() == 1));
        assertTrue(selected.stream().anyMatch(s -> s.getSeatNumber() == 4));
        // Verify A7-A8 are present (Remaining 2 seats)
        assertTrue(selected.stream().anyMatch(s -> s.getSeatNumber() == 7));
        assertTrue(selected.stream().anyMatch(s -> s.getSeatNumber() == 8));

        // The exact seats booked might be A1-A4 (4 seats) and A7, A8 (2 seats)
        // Check for seat numbers 1, 2, 3, 4, 7, 8 in the final list
        List<Integer> selectedNumbers = selected.stream().map(Seat::getSeatNumber).toList();
        assertTrue(selectedNumbers.containsAll(List.of(1, 2, 3, 4, 7, 8)), "Should select 4 from one side and 2 from the other.");
    }

    @Test
    void bookSeats_TwoSeats_PicksMiddleSeat_BestRow() throws Exception {
        //  A single row (A) with 10 seats (1-10). Middle seats are A5 and A6.
        List<Seat> mockSeats = CinemaUtils.createMockSeats(1, 10, List.of());
        when(seatRepository.findAll()).thenReturn(mockSeats);

        List<Seat> selected = defaultBookingStrategy.bookSeats(2, null);

        assertEquals(2, selected.size());
        assertEquals(5, selected.get(0).getSeatNumber());
        assertEquals(6, selected.get(1).getSeatNumber());
        assertEquals("A", selected.get(0).getRowLabel());
    }

    @Test
    void bookSeats_OddNumberOfSeats_PicksBestBlock_CentredOnMiddle() throws Exception {
        //  Row A with 9 seats (1-9). Middle seat is 5. Request 3 seats.
        List<Seat> mockSeats = CinemaUtils.createMockSeats(1, 9, List.of());
        when(seatRepository.findAll()).thenReturn(mockSeats);

        List<Seat> selected = defaultBookingStrategy.bookSeats(3, null);

        assertEquals(3, selected.size());
        // Block 4, 5, 6 is the closest to the middle (5)
        assertEquals(4, selected.get(0).getSeatNumber());
        assertEquals(5, selected.get(1).getSeatNumber());
        assertEquals(6, selected.get(2).getSeatNumber());
    }

    @Test
    void bookSeats_MultipleRows_PicksFurthestRowFromScreenFirst() throws Exception {
        //  Rows A, B,C & D, 4 seats each. A is index 0 (furthest from screen).
        // Middle seats are 2 and 3. Request 2 seats.
        List<Seat> mockSeats = CinemaUtils.createMockSeats(4, 4, List.of());
        when(seatRepository.findAll()).thenReturn(mockSeats);

        List<Seat> selected = defaultBookingStrategy.bookSeats(2, null);

        // Should pick middle seats in Row A (index 0)
        assertEquals(2, selected.size());
        assertEquals("A", selected.get(0).getRowLabel());
        assertEquals(2, selected.get(0).getSeatNumber());
        assertEquals(3, selected.get(1).getSeatNumber());
    }

    @Test
    void bookSeats_MiddleBooked_PicksNextBestAdjacentTieBreak() throws Exception {
        //  Row A with 10 seats. Seats 5 and 6 are booked. Request 2 seats.
        // Available seats: A1-A4, A7-A10. Potential consecutive blocks: (3, 4) and (7, 8).
        // Both blocks have the same distance (2.0) to the middle (5.5).
        // The implementation favors the lowest seat number (A3, A4) due to loop order.
        List<Seat> mockSeats = CinemaUtils.createMockSeats(1, 10, List.of("A5", "A6"));
        when(seatRepository.findAll()).thenReturn(mockSeats);

        List<Seat> selected = defaultBookingStrategy.bookSeats(2, null);

        assertEquals(2, selected.size());
        assertEquals(3, selected.get(0).getSeatNumber());
        assertEquals(4, selected.get(1).getSeatNumber());
    }

    @Test
    void bookSeats_NotEnoughBookableSeats_ThrowsException_Indicate_NoSeatFound() {
        //  Only 2 available seats total. Request 3.
        List<Seat> mockSeats = CinemaUtils.createMockSeats(1, 4, List.of("A3", "A4")); // A1, A2 available
        when(seatRepository.findAll()).thenReturn(mockSeats);

        Exception thrown = assertThrows(Exception.class, () -> {
            defaultBookingStrategy.bookSeats(3, null);
        });

        assertTrue(thrown.getMessage().contains("Could not find 3 seats. Only 2 were available in the entire hall."));
    }
}