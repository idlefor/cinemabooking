package com.cinema.booking.prototype.service;

import com.cinema.booking.prototype.model.Booking;
import com.cinema.booking.prototype.model.Seat;
import com.cinema.booking.prototype.repository.BookingRepository;
import com.cinema.booking.prototype.repository.SeatRepository;
import com.cinema.booking.prototype.strategy.BookingStrategy;
import com.cinema.booking.prototype.view.CinemaView; // Added dependency for handleBooking tests
import com.cinema.booking.prototype.utils.CinemaUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CinemaBookingServiceImplTest {

    @Mock
    private SeatRepository seatRepository;

    @Mock
    private BookingRepository bookingRepository;

    // Inject mock for I/O handling tests
    @Mock
    private CinemaView cinemaView;

    @Mock
    private BookingStrategy defaultStrategy;

    @Mock
    private BookingStrategy manualStrategy;

    @InjectMocks
    private CinemaBookingServiceImpl cinemaBookingService;

    private AtomicLong mockBookingCounter = new AtomicLong(1);

    private List<Seat> mockSeats;

    private final int TOTAL_SEATS = 80; // 8 rows * 10 seats

    private final int AVAILABLE_SEATS = 77; // 80 - 3 booked (A5, A6, A7)

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(cinemaBookingService, "bookingCounter", mockBookingCounter);
        ReflectionTestUtils.setField(cinemaBookingService, "movieTitle", "Test Movie");
        ReflectionTestUtils.setField(cinemaBookingService, "totalRows", 8);
        ReflectionTestUtils.setField(cinemaBookingService, "seatsPerRow", 10);

        // Mock seats setup (A5, A6, A7 booked)
        mockSeats = CinemaUtils.createMockSeats(8, 10, List.of("A5", "A6", "A7"));
        Mockito.lenient().when(seatRepository.findAll()).thenReturn(mockSeats);

        Mockito.lenient().when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> {
            Booking booking = invocation.getArgument(0);
            if (booking.getId() == null) {
                booking.setId(99L); // Simulate ID generation
            }
            if (booking.getBookingReference() == null) {
                // Manually set the expected ref based on the counter
                booking.setBookingReference("GIC" + String.format("%04d", mockBookingCounter.get()));
            }
            return booking;
        });
    }

    @Test
    void createBooking_ConcurrencyConflict_ThrowsException() throws Exception {
        // ARRANGE: Strategy returns a seat that is already marked as booked (A5 is booked in mock setup)
        Seat conflictedSeat = mockSeats.stream().filter(s -> s.getRowLabel().equals("A") && s.getSeatNumber() == 5).findFirst().get();

        // Reset booked status of the seat in the list to simulate the seat coming from the strategy
        conflictedSeat.setBooked(true);

        when(defaultStrategy.bookSeats(anyInt(), any())).thenReturn(List.of(conflictedSeat));

        Exception exception = assertThrows(Exception.class, () -> {
            cinemaBookingService.createBooking(1, null);
        });

        assertTrue(exception.getMessage().contains("Conflict detected: Seat A5 is already booked."));

        // Verify that the booking was NOT saved and the counter was NOT incremented
        verify(bookingRepository, never()).save(any(Booking.class));
        assertEquals(1, mockBookingCounter.get(), "Booking counter should not have incremented.");
    }

    @Test
    void getAvailableSeatsCount_ReturnsCorrectCount() {
        // 3 seats are booked (A5, A6, A7)
        int expectedAvailable = TOTAL_SEATS - 3;

        int actualAvailable = cinemaBookingService.getAvailableSeatsCount();

        assertEquals(expectedAvailable, actualAvailable);
        verify(seatRepository, times(1)).findAll();
    }

    @Test
    void handleCheckBooking_BookingFound_PrintsMap() {
        String ref = "GIC0005";
        Booking mockBooking = new Booking();
        mockBooking.setBookingReference(ref);

        Scanner mockScanner = mockScannerWithInput(ref + "\n");
        when(bookingRepository.findByBookingReference(ref)).thenReturn(Optional.of(mockBooking));

        cinemaBookingService.handleCheckBooking(mockScanner);

        verify(cinemaView, times(1)).printMessage("Booking id: " + ref);
        verify(cinemaView, times(1)).printCinemaMap(anyList(), eq(8), eq(10), eq(mockBooking));
    }

    @Test
    void handleCheckBooking_BookingNotFound_PrintsMessage() {
        String ref = "GIC9999";
        Scanner mockScanner = mockScannerWithInput(ref + "\n");
        when(bookingRepository.findByBookingReference(ref)).thenReturn(Optional.empty());

        cinemaBookingService.handleCheckBooking(mockScanner);

        verify(cinemaView, times(1)).printMessage("Booking not found.");
        verify(cinemaView, never()).printCinemaMap(anyList(), anyInt(), anyInt(), any(Booking.class));
    }

    @Test
    void handleCheckBooking_BlankInput_Returns() {
        Scanner mockScanner = mockScannerWithInput("\n");

        cinemaBookingService.handleCheckBooking(mockScanner);

        verify(cinemaView, never()).printMessage(eq("Booking not found."));
        verify(bookingRepository, never()).findByBookingReference(anyString());
    }

    @Test
    void handleBooking_InvalidInput_ReturnsOnBlank() throws Exception {
        // Enter blank immediately
        Scanner mockScanner = mockScannerWithInput("\n");
        cinemaBookingService.handleBooking(mockScanner);

        //  Should return before attempting to read position or create booking
        verify(defaultStrategy, never()).bookSeats(anyInt(), any());
    }

    @Test
    void handleBooking_InvalidCountLoop_ThenValidSuccess() throws Exception {
        // 1. Invalid: "abc" (NumberFormatException)
        // 2. Invalid: "0" (count <= 0)
        // 3. Invalid: "100" (count > availableSeats, which is 77)
        // 4. Valid: "1" (Success, default strategy)
        // 5. Position: "" (Default strategy selected)
        String input = "abc\n0\n100\n1\n\n";
        Scanner mockScanner = mockScannerWithInput(input);

        Booking mockBooking = new Booking();
        mockBooking.setBookingReference("GIC0001");

        when(defaultStrategy.bookSeats(eq(1), eq(null))).thenReturn(mockSeats.subList(0, 1));
        when(bookingRepository.save(any(Booking.class))).thenReturn(mockBooking);

        cinemaBookingService.handleBooking(mockScanner);

        verify(cinemaView, times(1)).printMessage("Invalid number. Please enter a positive integer.");
        verify(cinemaView, times(1)).printMessage("Ticket count must be positive.");
        verify(cinemaView, times(1)).printMessage("Sorry, there are only " + AVAILABLE_SEATS + " seats available.");
        verify(defaultStrategy, times(1)).bookSeats(eq(1), eq(null));
        verify(cinemaView, times(1)).printBookingSuccess(eq(mockBooking), eq(1), eq("Test Movie"));
        verify(cinemaView, times(1)).printMessage("Booking id: GIC0001 confirmed.");
    }

    @Test
    void handleBooking_ManualSuccess_PrintsMap() throws Exception {
        // 1. Count: "2" (Valid)
        // 2. Position: "B03" (Manual strategy selected)
        String input = "2\nB03\n";
        Scanner mockScanner = mockScannerWithInput(input);

        Booking mockBooking = new Booking();
        mockBooking.setBookingReference("GIC0001");

        when(manualStrategy.bookSeats(eq(2), eq("B03"))).thenReturn(mockSeats.subList(0, 2));
        when(bookingRepository.save(any(Booking.class))).thenReturn(mockBooking);

        cinemaBookingService.handleBooking(mockScanner);

        verify(manualStrategy, times(1)).bookSeats(eq(2), eq("B03"));
        verify(cinemaView, times(1)).printCinemaMap(anyList(), eq(8), eq(10), eq(mockBooking));
    }

    @Test
    void handleBooking_BookingFailsInStrategy_PrintsErrorMessage() throws Exception {
        // ARRANGE:
        String input = "1\n\n"; // Count 1, position default
        Scanner mockScanner = mockScannerWithInput(input);

        // Force createBooking (via handleBooking) to throw an exception
        when(defaultStrategy.bookSeats(anyInt(), any())).thenThrow(new Exception("Strategy failed internally."));

        cinemaBookingService.handleBooking(mockScanner);

        verify(cinemaView, times(1)).printMessage("Error processing booking: Strategy failed internally.");
    }

    @Test
    void initializeCinema_ShouldClearAndPopulateSeatsCorrectly() {
        String title = "Dune";
        int rows = 2;
        int seats = 5;

        cinemaBookingService.initializeCinema(title, rows, seats);

        verify(seatRepository, times(1)).deleteAll();
        verify(seatRepository, times(10)).save(any(Seat.class));

        assertEquals(title, cinemaBookingService.getMovieTitle());
        assertEquals(rows, cinemaBookingService.getTotalRows());
        assertEquals(seats, cinemaBookingService.getSeatsPerRow());
    }

    @Test
    void createBooking_ManualStrategy_Success() throws Exception {
        String position = "A05";
        List<Seat> selectedSeats = mockSeats.subList(0, 3);
        when(manualStrategy.bookSeats(eq(3), eq(position))).thenReturn(selectedSeats);

        Booking result = cinemaBookingService.createBooking(3, position);

        verify(defaultStrategy, never()).bookSeats(anyInt(), any());
        verify(manualStrategy, times(1)).bookSeats(eq(3), eq(position));
        verify(bookingRepository, times(2)).save(any(Booking.class));

        // The counter should have been incremented during createBooking
        assertEquals("GIC0001", result.getBookingReference());
        assertTrue(selectedSeats.stream().allMatch(Seat::isBooked), "Selected seats must be marked booked.");
    }

    @Test
    void createBooking_DefaultStrategy_Success() throws Exception {
        List<Seat> selectedSeats = mockSeats.subList(0, 2);
        when(defaultStrategy.bookSeats(eq(2), eq(null))).thenReturn(selectedSeats);

        Booking result = cinemaBookingService.createBooking(2, null);

        verify(manualStrategy, never()).bookSeats(anyInt(), any());
        verify(defaultStrategy, times(1)).bookSeats(eq(2), eq(null));
        verify(bookingRepository, times(2)).save(any(Booking.class));

        assertEquals("GIC0001", result.getBookingReference());
    }

    @Test
    void createBooking_ShouldThrowExceptionOnStrategyFailure() throws Exception {
        when(defaultStrategy.bookSeats(anyInt(), any())).thenReturn(List.of());

        Exception exception = assertThrows(Exception.class, () -> {
            cinemaBookingService.createBooking(5, null);});

        assertTrue(exception.getMessage().contains("failed to reserve 5 seats."));
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void getBooking_Found() {
        String ref = "GIC0042";
        Booking mockBooking = new Booking();
        mockBooking.setBookingReference(ref);
        when(bookingRepository.findByBookingReference(ref)).thenReturn(Optional.of(mockBooking));

        Booking result = cinemaBookingService.getBooking(ref);

        assertNotNull(result);
        assertEquals(ref, result.getBookingReference());
    }

    @Test
    void getBooking_NotFound() {
        String ref = "GIC0042";
        when(bookingRepository.findByBookingReference(ref)).thenReturn(Optional.empty());

        Booking result = cinemaBookingService.getBooking(ref);

        assertNull(result);
    }

    private Scanner mockScannerWithInput(String input) {
        InputStream in = new ByteArrayInputStream(input.getBytes());
        return new Scanner(in);
    }
}