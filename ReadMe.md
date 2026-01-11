# 🎬 Cinema Seat Booking System Prototype For Windows OS

## 🌟 Overview

This project is a Spring Boot prototype for a cinema seat booking system. It implements core seat selection logic using the **Strategy Pattern** to handle different booking methods (Default/Best-Fit and Manual/User-Specified). The system uses **Spring Data JPA** for persistence and manages transactional seat selection and booking creation.

## 🔑 Key Features

* **Strategy Pattern Implementation:** Uses the `BookingStrategy` interface with concrete implementations:
    * `DefaultBookingStrategy`: Automatically selects a cluster of consecutive, unbooked seats, prioritizing the cluster closest to the **middle** of the auditorium and starting from the **front rows** (lowest `rowIndex`).
    * `ManualBookingStrategy`: Allows users to specify a starting seat position (e.g., "A5"). It prioritizes consecutive, unbooked seats starting from the specified position and moves right within the same row.
* **Transactional Seat Selection:** The `createBooking` method in `CinemaBookingServiceImpl` is transactional (`@Transactional`) to ensure the seat selection, availability check, and final booking persistence are atomic.
* **Robust Seat Availability Logic:** Both strategies incorporate logic to find **consecutive** seats and handle **overflow** if the requested number of seats cannot be fulfilled in one optimal block or row.
* **Simple Initialization:** A dedicated method exists to initialize or reset the cinema hall layout (`initializeCinema`).
* **Domain Model:** Includes core entities: `Booking` (one-to-many relationship) and `Seat` (many-to-one relationship), using JPA annotations.

## 📐 Project Structure

The key components of the system are organized logically:

| Package | Purpose | Key Classes/Interfaces |
| :--- | :--- | :--- |
| `...prototype.model` | **JPA Entities** (Data Model) | `Booking.java`, `Seat.java` |
| `...prototype.repository` | **Spring Data JPA Repositories** | `BookingRepository.java`, `SeatRepository.java` |
| `...prototype.service` | **Business Logic Layer** (API) | `CinemaBookingService.java`, `CinemaBookingServiceImpl.java` |
| `...prototype.Strategy` | **Strategy Pattern Implementations** | `BookingStrategy.java`, `DefaultBookingStrategy.java`, `ManualBookingStrategy.java` |
| `...prototype.utils` | **Utility and Constants** | `CinemaConstants.java` |

---

## 🛠️ Booking Strategies Explained

### 1. Default Booking Strategy (`DefaultBookingStrategy`)

This strategy implements an **optimal best-fit** algorithm:

* **Row Priority:** Iterates through rows in **ascending `rowIndex`** order (i.e., rows closest to the screen first, assuming index 0 is the front).
* **Seat Priority:** Within an available row, it selects a block of seats that is **consecutive** and has its **center closest to the middle column** of the row.
* **Fallback:** If no consecutive block is found in a row, it defaults to taking the available seats starting from the left of the row, which will result in non-consecutive seat numbers.

### 2. Manual Booking Strategy (`ManualBookingStrategy`)

This strategy respects user input but provides an automatic overflow mechanism:

* **Input:** Requires a starting position, such as `"B03"`.
* **Primary Selection:** Tries to find the required number of seats **starting from the specified seat number and moving right** in the specified row.
* **Consecutiveness:** The seats taken in the primary selection are the available, unbooked seats immediately to the right of the start position.
* **Overflow:** If the required `count` is not met in the starting row, the remaining seats are booked using the **Default Strategy's "Middle Cluster" logic**, prioritizing the **next row closer to the screen** (higher `rowIndex`).

---

## 💻 Technical Details

### Dependencies (Assumed)

The project is built with:

* **Spring Boot:** For rapid application development.
* **Spring Data JPA / Hibernate:** For ORM and data persistence.
* **Database:** (H2 for persistence context).

### Seat Selection (Concurrency Handling)

The `createBooking` method employs a critical safety check within the transactional context:

1.  **Read:** `strategy.bookSeats()` executes a `readOnly = true` query to find and select potential seats based on the current transactional snapshot of unbooked seats.
2.  **Verify & Write:** Before persistence, the system performs a final loop over the selected seats to confirm no seat is marked as `booked() == true`. This helps detect potential **race conditions** or strategy logic failures, even under typical database isolation levels.
3.  **Persist:** The `Booking` is saved, which, due to the cascade, updates the associated `Seat` entities, setting `booked=true` and the `booking_id` foreign key.

I have combined all the information into a single, comprehensive, and well-structured `README.md` file content. This version is ready for you to copy and paste.

-----

# 🎬 Cinema Seat Booking System Prototype

## ✨ Project Summary

This project is a high-integrity **Spring Boot** application that implements a core **cinema seat booking system**. Its main architectural strength lies in its robust, concurrent-safe seat selection logic, achieved through the **Strategy Pattern**.

The system utilizes **Spring Data JPA** for persistence and ensures all booking operations are executed with **transactional integrity** (`@Transactional`) to prevent race conditions and guarantee data consistency in a multi-user environment. The prototype runs with a simple **console interface** for quick demonstration and testing of the core business logic.

-----

## 🔑 Core Features & Architectural Design

| Feature | Description | Implementation Detail |
| :--- | :--- | :--- |
| **Strategy Pattern** | Decouples multiple seat selection algorithms from the main booking service. | `BookingStrategy` interface with **Default (Best-Fit)** and **Manual (User-Specified)** concrete implementations. |
| **Transactional Booking** | Guarantees the atomicity of checking availability, selecting seats, and creating the final booking record. | `@Transactional` annotation on `CinemaBookingServiceImpl.createBooking()`. |
| **Optimal Best-Fit Logic** | The Default Strategy prioritizes **consecutive seats** closest to the **middle** of the row and starting from the **front rows** (lowest `rowIndex`). | Implemented in `DefaultBookingStrategy.java`. |
| **User-Driven Overflow** | The Manual Strategy respects user-specified start position, and efficiently handles any remaining seats using the optimal Default Strategy logic. | Implemented in `ManualBookingStrategy.java`. |
| **JPA Domain Model** | Entities manage the booking state and seating map structure. | **`Booking`** (Parent) and **`Seat`** (Child) entities with `@OneToMany` / `@ManyToOne` relationships. |

-----

## 📐 Project Structure

The codebase is organized following standard Spring conventions for separation of concerns:

| Package | Purpose | Key Classes/Interfaces |
| :--- | :--- | :--- |
| `...prototype.model` | **JPA Entities / Data Model** | `Booking.java`, `Seat.java` |
| `...prototype.repository` | **Spring Data JPA Repositories** | `BookingRepository.java`, `SeatRepository.java` |
| `...prototype.service` | **Business Logic Layer / API** | `CinemaBookingService.java`, `CinemaBookingServiceImpl.java` |
| `...prototype.strategy` | **Strategy Pattern Implementations** | `BookingStrategy.java`, `DefaultBookingStrategy.java`, `ManualBookingStrategy.java` |
| `...prototype.utils` | **Utility and Constants** | `CinemaConstants.java` |
| `...prototype.view` | **Console/Text-Based Output** | `CinemaView.java` |

-----

## 🛠️ Booking Strategy Deep Dive

### 1\. Default Booking Strategy (`DefaultBookingStrategy`)

This strategy implements the **optimal best-fit** algorithm, minimizing distance to the screen and the center aisle.

| Priority | Rule | Detail |
| :--- | :--- | :--- |
| **P1: Row Proximity** | Prioritize rows closest to the **screen**. | Iterates through seats by **ascending `rowIndex`** (index 0 = front row 'A'). |
| **P2: Seat Placement** | Finds the largest consecutive block that is **centered** closest to the physical middle column of the row. | Minimizes the distance between the block's center seat number and the row's middle point. |
| **P3: Fallback** | If no consecutive block is available, it defaults to taking the best-available non-consecutive seats starting from the row's left. | Ensures the maximum possible number of seats are booked in the most optimal row. |

### 2\. Manual Booking Strategy (`ManualBookingStrategy`)

This strategy requires a user-specified starting seat and provides intelligent overflow allocation.

| Priority | Rule | Detail |
| :--- | :--- | :--- |
| **P1: Primary Selection** | Fulfills the request by selecting **consecutive, unbooked seats** starting at the user's position and moving right. | Parses position (e.g., `A05`) and begins allocation at that physical seat number. |
| **P2: Overflow** | If the request cannot be fully met in the primary row, the remaining seats are allocated optimally in the **next row closer to the screen**. | Searches in rows with a **higher `rowIndex`** than the starting row. |
| **P3: Overflow Logic** | Overflow seats are allocated using the **Default Strategy's** optimal "Middle Cluster" logic. | Ensures the overflow seats are also highly desirable. |

-----

## 🔒 Concurrency and Persistence

The system handles concurrent booking requests through robust transactional logic:

1.  **Read Selection (Snapshot):** The selected `BookingStrategy` executes a `readOnly = true` query to select potential seats based on the current state of the database (`Seat.isBooked() == false`).
2.  **Write Verification:** Within the transaction in `CinemaBookingServiceImpl.createBooking()`, a final check confirms that no seat was booked by another process between the read-selection and the write phase.
3.  **Persist & Lock:** The `Booking` is saved, and a cascaded merge updates all associated `Seat` entities, setting `booked=true` and linking the `booking_id` foreign key. This persistence effectively **locks** the seats against future transactions.

### Dependencies

* **Spring Boot**
* **Spring Data JPA / Hibernate**
* **H2 Database** (Embedded for prototype testing)

-----

## ▶️ Getting Started

### Prerequisites

* Java 21
* Maven

### Running the Application

1.  **Clone the repository:**
    ```bash
    git clone [your-repository-url]
    cd [your-project-folder]
    ```
2.  **Build and Run:**
    ```bash
    ./mvnw spring-boot:run
    ```

### Using the Console Interface

1.  **Initialization:** The application will prompt you to initialize the cinema dimensions:

    ```
    Please define movie title and seating map in [Title] [Row] [SeatsPerRow] format:
    > Inception 8 10
    ```

    *(Note: Max limits are currently set to 26 rows and 50 seats per row.)*

2.  **Main Menu & Booking:**

    ```
    [1] Book tickets for Inception (80 seats available)
    [2] Check bookings
    [3] Exit
    Please enter your selection:
    > 1
    ```

    The booking prompt will guide you on using the Default (number of tickets only) or Manual (tickets and starting seat) strategies.