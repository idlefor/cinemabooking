package com.cinema.booking.prototype;


import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Scanner;

@Component
public class ApplicationRunner implements CommandLineRunner {

    @Autowired
    private CinemaBookingApplication application;

    @Override
    public void run(String... args) throws Exception {
        Scanner scanner = new Scanner(System.in);
        application.startApplicationLogic(scanner);
    }
}
