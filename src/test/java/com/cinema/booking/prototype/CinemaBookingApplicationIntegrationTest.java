package com.cinema.booking.prototype;

import com.cinema.booking.prototype.repository.BookingRepository;
import com.cinema.booking.prototype.repository.SeatRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.assertTrue;


@SpringBootTest
public class CinemaBookingApplicationIntegrationTest {

    @MockBean
    private ApplicationRunner myApplicationRunner;

    @Autowired
    private CinemaBookingApplication application;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private BookingRepository bookingRepository;

    private static final PrintStream originalSystemOut = System.out;

    private ByteArrayOutputStream outputStreamCaptor;

    @BeforeEach
    void setUp() throws Exception {
        // Reset Database in the correct order:
        // DELETE CHILD RECORDS (SEAT entity) FIRST
        seatRepository.deleteAll();
        // THEN DELETE PARENT RECORDS (BOOKING entity)
        bookingRepository.deleteAll();

        outputStreamCaptor = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outputStreamCaptor));
    }

    @AfterEach
    void tearDown() {
        System.setOut(originalSystemOut);
    }

    @Test
    void endToEnd_DefaultStrategy_Change_ManualStrategy_BookingSuccess() throws Exception {
        // SCENARIO: Default Strategy booking trigger first then change to Manual Strategy booking
        // 1. Setup: "Matrix 3 6" (3 rows: A, B, C; 6 seats/row)
        // 2. Menu: "1" (Book)
        // 3. Count: "2"
        // 4. Position: "C01" (Manual entry) -> Books C1, C2
        // 5. Menu: "3" (Exit)
        String inputSequence = "Matrix 3 6\n" +
                               "1\n" +
                               "2\n" +
                               "C01\n" +
                               "3\n";

        String output = runApplicationWithInput(inputSequence);
        // 1. Verify success confirmation and immediate follow-up lines
        assertTrue(output.contains("Successfully reserved 2 Matrix tickets."), "Booking success message must be present.");

        // 2. Verify the rows (C is closest, B is middle, A is furthest)
        // Row C (Manual Booking: C1, C2) Expected pattern: C o o . . . .
        assertTrue(output.contains("C o o . . . . "),
                "Row C must show the first two seats booked ('o o').");

        // 3. Verify the Row B (Empty) Expected pattern: B . . . . . .
        assertTrue(output.contains("B . . . . . . "),
                "Row B must be completely empty ('. . . . . . ').");

        // Row A (Empty) Expected pattern: A . . . . . .
        assertTrue(output.contains("A . . . . . . "),
                "Row A must be completely empty ('. . . . . . ').");

        // 4. Verify the seat number footer
        assertTrue(output.contains("  1 2 3 4 5 6 "),
                "Output must contain the seat number footer (1 to 6).");
    }

    @Test
    void endToEnd_DefaultStrategy_BookingSuccess() throws Exception {
        // SCENARIO: Default Strategy booking
        String testInput = "Avatar 5 5\n" +  // Setup: 5 rows, 5 seats
                "1\n" +           // Menu: Book
                "2\n" +           // Count: 2
                "\n" +            // Position: Default Strategy
                "3\n";            // Menu: Exit

        String output = runApplicationWithInput(testInput);
        System.out.println("endToEnd_DefaultStrategy_BookingSuccess \n" + output);
        // 1. Verify success confirmation (printed before the map)
        assertTrue(output.contains("Successfully reserved 2 Avatar tickets."), "Booking success message must be present.");

        // 2. Verify the booking ID and header lines immediately preceding the map
        assertTrue(output.contains("Booking id:"), "Output must contain 'Booking id:'");

        // 3. Verify an unbooked row (Row E is the furthest/highest index, printed first)
        // Expected pattern for 5 empty seats: E . . . . .
        assertTrue(output.contains("E . . . . . "), "Output must show Row E as completely empty");

        // 4. Verify the booked row (Row A) - Expected seats: A2 and A3. Pattern: A . o o . .
        assertTrue(output.contains("A . o o . . "), "Map must show middle seats booked in Row A (A2, A3) and furthest row is fufilled.");

        // 5. Verify the seat number footer
        assertTrue(output.contains("  1 2 3 4 5 "), "Output must contain the seat number footer (1 to 5).");
    }

    @Test
    void endToEnd_ManualStrategy_WithOverflow_Success() throws Exception {
        // SCENARIO: Manual booking fails to fit entirely in the chosen row due to shortage and overflows to the next row
        // 1. Setup: 3 Rows (A, B, C), 6 Seats/row. Total seats: 18.
        // 2. Pre-Block: Manually book B4, B5, B6 (the middle and end of Row B).
        // 3. Request: 5 tickets, starting at B1.
        //    - B1, B2, B3 are available
        //    - Overflow: 2 seats needed in row C
        // 4. Overflow Row: Row C. Available: C1 to C6.
        // 5. Overflow Placement (findBestFitSeatsForManualStrategy):
        //    - Middle of 6 seats: seats 2 and 3.
        //    - It should select C2 and C3.

        // --- Step 1: Initialize cinema + 1st Booking ---
        String preBlockInput = "Inception 3 6\n" +
                               "1\n" +
                               "3\n" +
                               "B4\n";

        // --- Step 2: 2nd booking with overflow seat ---
        String mainBookingInput = "1\n" +
                                  "5\n" +
                                  "B1\n" +
                                  "3\n";

        String inputSequence = preBlockInput + mainBookingInput;
        String output = runApplicationWithInput(inputSequence);

        // 1. Verify success confirmation for the first booking
        assertTrue(output.contains("Successfully reserved 3 Inception tickets."),
                "Successful in booking 3 inception ticket at C4,C5,C6 seats");

        // 2. Verify Row B (Manual part: B1, B2, B3 booked + C4, C5, C6 pre-booked)
        assertTrue(output.contains("B o o o # # #"), "Verify Row B must be full ('B o o o # # #').");

        // 3. Verify Row C (Overflow part: C2, C3 booked by default strategy)
        assertTrue(output.contains("C . o o . . . "),
                "Row C must show the 2 overflow seats booked in the middle cluster (C2, C3).");

        // 4. Verify Row A (unused)
        assertTrue(output.contains("A . . . . . . "), "Row A must be completely empty ('. . . . . . ').");

        // 5. Verify success confirmation for the 2nd Manual booking
        assertTrue(output.contains("Successfully reserved 5 Inception tickets."),
                "Successful in booking 5 inception ticket at (B1,B2,B3,C2,C3) seats");
    }

    @Test
    void endToEnd_Menu_CheckBooking_Success() throws Exception {
        // 1. Setup cinema, 2. Book successfully to get GIC0001
        // 3. Use menu option '2' to check the booking.
        // Input: Setup -> Book 1 seat (C1) -> Check Booking ('2') -> Enter Ref -> Exit
        String inputSequence = "Titanic 3 3\n" +
                "1\n" +
                "1\n" +
                "C1\n" +
                "2\n" + // <-- Triggers case "2" which is check booking
                "GIC0001\n" +
                "3\n";

        String output = runApplicationWithInput(inputSequence);

        // Verify successful booking and that the check booking logic ran.
        assertTrue(output.contains("Booking id:"), "Booking success message must be present.");

        // The check booking logic repeats the booking id header and prints the map again.
        assertTrue(output.contains("Enter booking id, or enter blank to go back to main menu:"),
                "Application must have entered the 'Check Booking' flow (case '2').");

        // Verify the map is printed after the check (it re-prints the C row with C1 booked)
        assertTrue(output.contains("C o . . "), "The map showing the booked seat must be displayed during check booking.");
    }

    @Test
    void endToEnd_Menu_Default_InvalidInput_Selection() throws Exception {
        // Test to cover the 'default:' case (Invalid selection)
        // Input: Setup -> Invalid Option ('9') -> Valid Option ('3', Exit)
        String inputSequence = "MovieInvalid 1 1\n" +
                "9\n" + // <-- Triggers default: case
                "3\n";

        String output = runApplicationWithInput(inputSequence);

        assertTrue(output.contains("Invalid selection."),
                "Application must print 'Invalid selection' when non-menu option is chosen.");
    }

    @Test
    void initializeSystem_Input_ReturnFalse_InvalidFormat() throws Exception {
        // Input: Bad format -> Valid Setup -> Exit
        String inputSequence = "Movie 3\n" + // Missing seat count (Failure)
                               "ValidMovie 1 1\n" + // Successful setup
                               "3\n";

        String output = runApplicationWithInput(inputSequence);

        assertTrue(output.contains("Invalid format. Please try again (e.g., Inception 8 10). Max rows 26, Max seats 50."),
                "System should reject setup when the format is invalid (matcher.find() fails).");

    }

    private String runApplicationWithInput(String input) throws Exception {
        Scanner testScanner = new Scanner(new ByteArrayInputStream(input.getBytes()));
        application.startApplicationLogic(testScanner);

        return outputStreamCaptor.toString();
    }
}