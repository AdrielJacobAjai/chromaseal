# CineConnect

Multi-theatre movie ticket booking and management system - Java 17, Swing, JDBC and MySQL.
PBCST 304 Project, Group 8, Model Engineering College.

## Features
- Browse movies and shows across several theatres, search by movie, genre or theatre.
- Seat map with regular and premium seats; weekend shows are priced 20% higher.
- **No double booking**: every hold and booking is one atomic conditional SQL `UPDATE`, so only one of any number of simultaneous requests wins (tested with 40 threads on one seat).
- **2-minute seat hold**: selected seats are held for the customer; a background timer frees unpaid holds.
- **Best-seat recommendation**: "Recommend best seats" suggests a block of seats together, near the centre and mid-hall.
- **Admin dashboard**: overall occupancy, per-show occupancy and revenue, busiest show timings, revenue by movie.
- Admin and Customer roles; PBKDF2-hashed passwords.
- Live updates: if another customer takes a seat, your seat map refreshes.

## Run it
You need JDK 17+, Maven (or let IntelliJ/Eclipse handle Maven) and a MySQL server.

1. Copy `config/db.properties.example` to `config/db.properties` and put in your MySQL password.
   (Alternatively set the environment variables `CINECONNECT_DB_URL`, `CINECONNECT_DB_USER`, `CINECONNECT_DB_PASSWORD`.)
2. Start the app:
   ```bash
   mvn package -DskipTests
   java -jar target/cineconnect-1.0.0.jar
   ```
   Or open the folder in IntelliJ/Eclipse as a Maven project and run `com.cineconnect.Main`.

Tables are created on first start and filled with demo data. Log in with:

| Role | Username | Password |
|---|---|---|
| Admin | `admin` | `admin123` |
| Customer | `customer` | `customer123` |

## Run the tests
```bash
mvn test
```
Tests use an in-memory H2 database in MySQL mode, so no MySQL is needed. They cover concurrent booking, hold expiry, all-or-nothing multi-seat holds, cancellation, authentication, the dashboard figures and seat recommendation.

## OOP concepts and design patterns
| Concept | Where |
|---|---|
| Encapsulation | private fields with getters in `Seat`, `Show`, `Booking`, `User` |
| Inheritance | `Admin`, `Customer` extend abstract `User`; `RegularSeat`, `PremiumSeat` extend abstract `Seat` |
| Polymorphism | overriding: `getDashboardTitle()`, `getPriceMultiplier()`; overloading: `searchMovies(...)`, `listShows(...)`, `bookSeat/bookSeats`, `cancelBooking(...)` |
| Abstraction | `BookingService` exposes `bookSeat()`, `getAvailableSeats()` and hides JDBC |
| Interfaces | `Bookable`, `Cancellable`, `SeatRecommendationStrategy`, `PricingStrategy`, `SeatEventListener` |
| Custom exceptions | `SeatAlreadyBookedException`, `HoldExpiredException`, `AuthenticationException` |
| **Singleton** | `Database` |
| **Factory** | `SeatFactory` |
| **Observer** | `SeatEventBus` / `SeatEventListener` (seat maps refresh live) |
| **Strategy** | `PricingStrategy` (standard / weekend) and `SeatRecommendationStrategy` (`CentreFocusStrategy`) |

The UML class diagram is in [docs/CLASS_DIAGRAM.md](docs/CLASS_DIAGRAM.md).

## Project layout
```
src/main/java/com/cineconnect/
  model/      User, Admin, Customer, Seat, RegularSeat, PremiumSeat, SeatFactory, Movie, Theatre, Show, Booking
  service/    BookingService, CatalogService, AuthService, DashboardService, SeatHoldManager
  strategy/   pricing and seat-recommendation strategies
  observer/   SeatEventBus, SeatEventListener
  db/         Database (Singleton)
  exception/  custom exceptions
  ui/         Swing screens
src/main/resources/schema.sql
```
