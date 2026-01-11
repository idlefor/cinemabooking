package com.cinema.booking.prototype;

import com.cinema.booking.prototype.service.CinemaBookingServiceImpl;
import com.cinema.booking.prototype.utils.CinemaConstants;
import com.cinema.booking.prototype.view.CinemaView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.stereotype.Component;

import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


@Component
@SpringBootApplication
public class CinemaBookingApplication {

    @Autowired
    private CinemaBookingServiceImpl cinemaBookingService;

    @Autowired
    private CinemaView cinemaView;

    public static void main(String[] args) {
        SpringApplication.run(CinemaBookingApplication.class, args);
    }

    public void startApplicationLogic(Scanner scanner) throws Exception {
        cinemaView.printMessage("Please define movie title and seating map in [Title] [Row] [SeatsPerRow] format:");

        while (true) {
            System.out.print("> ");
            String input = scanner.nextLine();
            if (initializeSystem(input)) break;
            cinemaView.printMessage("Invalid format. Please try again (e.g., Inception 8 10). Max rows 26, Max seats 50.");
        }

        boolean running = true;
        while (running) {
            showMainMenu();
            String selection = scanner.nextLine().trim();

            switch (selection) {
                case "1":
                    // Delegated to service layer
                    cinemaBookingService.handleBooking(scanner);
                    break;
                case "2":
                    // Delegated to service layer
                    cinemaBookingService.handleCheckBooking(scanner);
                    break;
                case "3":
                    cinemaView.printMessage("Thank you for using GIC Cinemas system. Bye!");
                    running = false;
                    break;
                default:
                    cinemaView.printMessage("Invalid selection.");
            }
        }
    }

    private boolean initializeSystem(String input) {
        Pattern pattern = Pattern.compile(CinemaConstants.INITIAL_SETUP_REGEX);
        Matcher matcher = pattern.matcher(input);

        if (matcher.find()) {
            String title = matcher.group(1);
            int rows = Integer.parseInt(matcher.group(2));
            int seats = Integer.parseInt(matcher.group(3));

            if (rows > CinemaConstants.MAX_ROWS || seats > CinemaConstants.MAX_SEATS_PER_ROW) return false;
            cinemaBookingService.initializeCinema(title, rows, seats);

            return true;
        }
        return false;
    }

    private void showMainMenu() {
        cinemaView.printWelcome(cinemaBookingService.getMovieTitle(), cinemaBookingService.getAvailableSeatsCount());
    }
}