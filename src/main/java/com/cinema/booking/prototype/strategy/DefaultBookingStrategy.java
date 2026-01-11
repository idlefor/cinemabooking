package com.cinema.booking.prototype.strategy;

import com.cinema.booking.prototype.model.Seat;
import com.cinema.booking.prototype.repository.SeatRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service("defaultBookingStrategy")
public class DefaultBookingStrategy implements BookingStrategy {

    private final SeatRepository seatRepository;

    @Autowired
    public DefaultBookingStrategy(SeatRepository seatRepository) {
        this.seatRepository = seatRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Seat> bookSeats(int count, String position) throws Exception {
        if (count <= 0) {
            throw new IllegalArgumentException("Number of tickets must be positive.");
        }

        List<Seat> allSeats = seatRepository.findAll();

        // 1. Initial availability check
        long totalAvailableSeats = allSeats.stream().filter(s -> !s.isBooked()).count();
        if (count > totalAvailableSeats) {
            throw new Exception("Could not find " + count + " seats. Only " + totalAvailableSeats + " were available in the entire hall.");
        }

        // Get total seats per row for findBestFitSeats (assuming uniform rows)
        int totalSeatsInRow = allSeats.stream().mapToInt(Seat::getSeatNumber).max().orElse(0);

        // 2. Group all seats by row index, sorted by seat number
        Map<Integer, List<Seat>> seatsByRow = allSeats.stream()
                .sorted(Comparator.comparing(Seat::getSeatNumber))
                .collect(Collectors.groupingBy(Seat::getRowIndex));

        // 3. Prioritize rows from A (Index 0) upwards
        List<Integer> sortedRowIndices = seatsByRow.keySet().stream().sorted().toList();

        List<Seat> selectedSeats = new ArrayList<>();
        int remainingCount = count;

        for (int rowIndex : sortedRowIndices) {
            if (remainingCount == 0) break;

            List<Seat> rowSeats = seatsByRow.get(rowIndex);

            // Get a mutable list of available seats in this row, sorted by seat number
            List<Seat> availableSeatsInRow = rowSeats.stream()
                    .filter(seat -> !seat.isBooked())
                    .sorted(Comparator.comparing(Seat::getSeatNumber))
                    .collect(Collectors.toCollection(ArrayList::new));

            // Continuously process the best row until no more segments can be found.
            while (remainingCount > 0 && !availableSeatsInRow.isEmpty()) {

                // Find the largest consecutive block in the remaining seats, prioritized by middle.
                // The max size we look for is limited by the current remainingCount.
                List<Seat> bestBlockFound = findLargestBestBlock(availableSeatsInRow, remainingCount, totalSeatsInRow);

                if (!bestBlockFound.isEmpty()) {
                    selectedSeats.addAll(bestBlockFound);
                    remainingCount -= bestBlockFound.size();

                    // Remove booked seats from the current row's availability
                    availableSeatsInRow.removeAll(bestBlockFound);

                } else {
                    // No block of size >= 1 was found in this row. Move to the next row.
                    break;
                }
            }
        }

        if (remainingCount > 0) {
            throw new Exception("Could not fulfill the request. Only " + (count - remainingCount) + " could be booked in consecutive blocks.");
        }

        return selectedSeats;
    }

    /**
     * Helper method to find the largest consecutive block (up to maxCount) in the available seats,
     * prioritizing the one closest to the middle of the row.
     */
    private List<Seat> findLargestBestBlock(List<Seat> availableSeats, int maxCount, int totalSeatsInRow) {
        List<Seat> bestBlock = List.of();

        // Iterate from the largest block size needed down to 1
        for (int size = maxCount; size >= 1; size--) {
            // findBestFitSeats returns the single best block of that exact size.
            List<Seat> block = findBestFitSeatsForDefaultStrategy(availableSeats, size, totalSeatsInRow);
            if (!block.isEmpty()) {
                // Since we iterate from maxCount down to 1, the first block found is the largest.
                bestBlock = block;
                break;
            }
        }
        return bestBlock;
    }


    /**
     * Finds the best-fit block of seats of a specific size ('needed') in an available list, prioritizing the middle.
     * The block size returned is guaranteed to be either 0 or exactly 'needed' seats.
     */
    public List<Seat> findBestFitSeatsForDefaultStrategy(List<Seat> availableSeats, int needed, int totalSeatsInRow) {
        if (availableSeats.isEmpty() || needed == 0) return List.of();

        double middlePoint = (totalSeatsInRow + 1) / 2.0;

        List<Seat> bestBlock = List.of();
        double minDistanceToMiddle = Double.MAX_VALUE;

        // Loop runs for all possible starting positions that can accommodate 'needed' seats.
        for (int i = 0; i <= availableSeats.size() - needed; i++) {
            List<Seat> currentBlock = availableSeats.subList(i, i + needed);

            boolean isConsecutive = true;
            for (int k = 1; k < currentBlock.size(); k++) {
                // Check for physical consecutiveness (e.g., seat number 5 followed by 6)
                if (currentBlock.get(k).getSeatNumber() != currentBlock.get(k - 1).getSeatNumber() + 1) {
                    isConsecutive = false;
                    break;
                }
            }

            if (isConsecutive) {
                // Calculate the seat number at the center of the current block
                double blockCenterSeatNumber = currentBlock.get(0).getSeatNumber() + (needed - 1) / 2.0;
                double distance = Math.abs(blockCenterSeatNumber - middlePoint);

                // For seat in the middle with equal distance on left or right
                // , we want the block with the lowest seat number on the left first.
                if (distance < minDistanceToMiddle) {
                    minDistanceToMiddle = distance;
                    bestBlock = currentBlock;
                }
            }
        }
        return bestBlock;
    }
}