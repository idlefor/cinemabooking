package com.cinema.booking.prototype.strategy;

import com.cinema.booking.prototype.model.Seat;
import com.cinema.booking.prototype.repository.SeatRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service("manualBookingStrategy")
public class ManualBookingStrategy implements BookingStrategy {

    private final SeatRepository seatRepository;

    @Autowired
    public ManualBookingStrategy(SeatRepository seatRepository) {
        this.seatRepository = seatRepository;
    }

    @Override
    public List<Seat> bookSeats(int count, String position) throws Exception {
        if (position == null || position.trim().isEmpty()) {
            throw new IllegalArgumentException("Manual booking requires a starting position (e.g., A5).");
        }
        if (count <= 0) {
            throw new IllegalArgumentException("Number of tickets must be positive.");
        }

        String cleanedPosition = position.toUpperCase().trim();
        String rowLabel;
        int startSeatNumber;

        try {
            int digitIndex = -1;
            for (int i = 0; i < cleanedPosition.length(); i++) {
                if (Character.isDigit(cleanedPosition.charAt(i))) {
                    digitIndex = i;
                    break;
                }
            }

            if (digitIndex <= 0) {
                throw new IllegalArgumentException("Invalid manual position format: " + position);
            }

            rowLabel = cleanedPosition.substring(0, digitIndex);
            startSeatNumber = Integer.parseInt(cleanedPosition.substring(digitIndex));

        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid manual position format: " + position);
        }

        Map<Integer, List<Seat>> seatsByRow = seatRepository.findAll().stream()
                .sorted(Comparator.comparing(Seat::getSeatNumber))
                .collect(Collectors.groupingBy(Seat::getRowIndex));

        // Find the specific starting row index based on the label (e.g., "B")
        Optional<Integer> startRowIndexOpt = seatsByRow.entrySet().stream()
                .filter(entry -> !entry.getValue().isEmpty()
                        && entry.getValue().getFirst().getRowLabel().equals(rowLabel))
                .map(Map.Entry::getKey)
                .findFirst();

        if (startRowIndexOpt.isEmpty()) {
            throw new Exception("Row " + rowLabel + " does not exist.");
        }

        int startRowIndex = startRowIndexOpt.get();
        List<Seat> selectedSeats = new ArrayList<>();
        int remainingCount = count;

        // Rule: Start from specified position and fill to the right in the same row.
        List<Seat> startRowSeats = seatsByRow.get(startRowIndex);
        if (startRowSeats != null) {
            List<Seat> availableInRowFromStart = startRowSeats.stream()
                    .filter(s -> s.getSeatNumber() >= startSeatNumber && !s.isBooked()).toList();

            int seatsToTakeInRow = Math.min(remainingCount, availableInRowFromStart.size());
            selectedSeats.addAll(availableInRowFromStart.subList(0, seatsToTakeInRow));
            remainingCount -= seatsToTakeInRow;
        }

        // Rule: Overflow to the next row CLOSER to the screen (Higher Index).
        // Rule: Overflow allocation follows default rules (Middle Cluster).
        if (remainingCount > 0) {
            // Find rows closer to screen (Index > StartIndex)
            // Sort Ascending (Immediate next row first)
            List<Integer> overflowRowIndices = seatsByRow.keySet().stream()
                    .filter(i -> i > startRowIndex)
                    .sorted()
                    .collect(Collectors.toList());

            for (int rowIndex : overflowRowIndices) {
                if (remainingCount == 0) break;

                List<Seat> rowSeats = seatsByRow.get(rowIndex);
                List<Seat> availableSeats = rowSeats.stream()
                        .filter(seat -> !seat.isBooked())
                        .collect(Collectors.toList());

                if (availableSeats.isEmpty()) continue;

                // Use "Middle Cluster" logic for overflow seats
                List<Seat> overflowSeats = findBestFitSeatsForManualStrategy(availableSeats, remainingCount, rowSeats.size());

                int seatsToBookInOverflow = overflowSeats.size();
                if (seatsToBookInOverflow > 0) {
                    selectedSeats.addAll(overflowSeats);
                    remainingCount -= seatsToBookInOverflow;
                }
            }
        }

        if (remainingCount > 0) {
            throw new Exception("Could not fulfill booking. Only " + (count - remainingCount) + " seats can be reserved which is less than what request");
        }

        return selectedSeats;
    }

    private List<Seat> findBestFitSeatsForManualStrategy(List<Seat> availableSeats, int needed, int totalSeatsInRow) {
        // --- 1. Edge Case Check ---
        // If there are no available seats in the row or no seats are needed, return an empty list immediately.
        if (availableSeats.isEmpty() || needed == 0) return List.of();

        // --- 2. Define Middle Reference Point ---
        // Determine the ideal target seat number (the middle seat of the row).
        // Note: totalSeatsInRow is the actual count (e.g., 10), seat numbers are 1-based.
        // This calculation finds the center seat number (e.g., 5 for 9 seats, 5 or 6 for 10 seats).
        int middleIndex = (totalSeatsInRow % 2 == 0) ? (totalSeatsInRow / 2) : (totalSeatsInRow / 2) + 1;

        List<Seat> bestBlock = List.of();
        // Initialize the minimum distance to the middle point to the largest possible value.
        double minDistanceToMiddle = Double.MAX_VALUE;

        // --- 3. Iterative Search for Best Consecutive Block ---
        // Loop through all possible starting positions (i) in the list of available seats.
        // The loop stops when there are fewer remaining available seats than the 'needed' count.
        for (int i = 0; i <= availableSeats.size() - needed; i++) {
            // Get the potential block of 'needed' size starting at index 'i'.
            List<Seat> currentBlock = availableSeats.subList(i, i + needed);

            // Check if the physical seats in the block are consecutive (e.g., seat 5, 6, 7).
            boolean isConsecutive = true;
            for (int k = 1; k < currentBlock.size(); k++) {
                // Compare the current seat number (k) with the previous one (k-1).
                if (currentBlock.get(k).getSeatNumber() != currentBlock.get(k - 1).getSeatNumber() + 1) {
                    isConsecutive = false;
                    break;
                }
            }

            // --- 4. Evaluate Block Distance (Only if Consecutive) ---
            if (isConsecutive) {
                // Calculate the center of the current block (using 0.5 for an even number of seats).
                // Example: Block (4, 5, 6) center is 5. Block (4, 5) center is 4.5.
                double blockCenterSeatNumber = currentBlock.get(0).getSeatNumber() + (needed - 1) / 2.0;

                // Calculate the absolute distance from the block's center to the row's middle point.
                double distance = Math.abs(blockCenterSeatNumber - middleIndex);

                // If this block is closer to the middle than the previously best-found block, update the best block.
                // Note: If distances are equal, the first one found (the lowest seat number block) is kept.
                if (distance < minDistanceToMiddle) {
                    minDistanceToMiddle = distance;
                    bestBlock = currentBlock;
                }
            }
        }

        // --- 5. Fallback Strategy (If NO Consecutive Block Was Found) ---
        // The Manual Strategy uses a fallback for overflow, trying to book what it can
        // from the beginning of the next available row, even if non-consecutive.
        if (bestBlock.isEmpty()) {
            int countInRow = Math.min(needed, availableSeats.size());

            if (countInRow > 0) {
                // Select the first 'countInRow' seats available in this row.
                // This sacrifices the "consecutive" rule but ensures maximum seats are booked in the overflow row.
                return availableSeats.subList(0, countInRow).stream()
                        .filter(s -> !s.isBooked())
                        .limit(needed)
                        .collect(Collectors.toList());
            }
        }
        return bestBlock; // Return the best consecutive block found (or the fallback block).
    }
}