# CineConnect - Viva Guide

Everything you need to explain the project: what each part does, why it was built that way, and the questions examiners usually ask.

---

## 1. The project in 30 seconds

> CineConnect is a Java desktop application for booking movie tickets across multiple theatres. Customers browse shows, pick seats on a seat map and pay. The main problem it solves is **double booking**: two customers can never get the same seat, even if they click at the same instant. It also holds seats for 2 minutes, recommends the best seats, and gives the admin a dashboard of occupancy and revenue. It is written in Java with Swing for the screens and JDBC with MySQL for storage, and uses encapsulation, inheritance, polymorphism, abstraction, interfaces, custom exceptions, and four design patterns.

---

## 2. Architecture: layers

```
  ui (Swing screens)            what the user sees and clicks
        |
  service (business logic)      BookingService, CatalogService, AuthService, DashboardService
        |
  db (Database singleton)       hands out JDBC connections
        |
  MySQL                         tables: users, movies, theatres, shows, show_seats, bookings, booking_seats
```

Supporting packages: `model` (plain objects such as Seat, Show), `strategy` (swappable algorithms), `observer` (live-update notifications), `exception` (custom errors).

**Why layers?** The screens never contain SQL. If the database changed, only the service layer changes. This is *abstraction* and *separation of concerns*.

**Flow of one booking:**
`CustomerFrame` (button click) → `BookingService.holdSeats()` → SQL UPDATE in MySQL → `SeatEventBus.publish()` → every open screen refreshes.

---

## 3. The four OOP pillars, with where they appear

### Encapsulation
Fields are `private`, accessed through getters. Example: `Seat` has private `label`, `row`, `col`, `price`, `status`. Nothing outside can set `status` to nonsense directly, and `Booking` makes its seat list immutable (`List.copyOf`).
*Files:* `Seat`, `Show`, `Booking`, `User`.
*Viva line:* "Encapsulation protects the data and lets us change the internals without breaking other classes."

### Inheritance
- `Admin` and `Customer` extend the abstract class `User`.
- `RegularSeat` and `PremiumSeat` extend the abstract class `Seat`.
- `WeekendPricing` extends `StandardPricing`.
- `SeatAlreadyBookedException` and others extend `CineConnectException`.

Shared fields (id, username, label, row...) are written once in the parent. *Reuse.*

### Polymorphism
- **Overriding (runtime):** `getPriceMultiplier()` returns 1.0 in `RegularSeat` and 1.5 in `PremiumSeat`. `getDashboardTitle()` is different for `Admin` and `Customer`. Code that holds a `Seat` or `User` reference calls the right version automatically.
- **Overloading (compile time):** several methods share a name but differ in parameters:
  - `searchMovies(String)` and `searchMovies(String, String genre)`
  - `listShows()` and `listShows(String)`
  - `bookSeat(user, show, label)` and `bookSeats(user, show, List)`
  - `cancelBooking(id, userId)` (customer) and `cancelBooking(id)` (admin)

### Abstraction
- `abstract class User` and `abstract class Seat` cannot be instantiated; they define *what* a user/seat must do (`getRole()`, `getType()`).
- `BookingService` exposes `bookSeat()` and `getAvailableSeats()` and **hides** all JDBC and SQL. The caller does not know a database exists.

### Interfaces
| Interface | Meaning |
|---|---|
| `Bookable` | something that can book seats (`bookSeats`) |
| `Cancellable` | something that can cancel (`cancelBooking`) |
| `PricingStrategy` | a way to compute a price |
| `SeatRecommendationStrategy` | a way to choose seats |
| `SeatEventListener` | something that wants to hear about seat changes |

`BookingService implements Bookable, Cancellable`, which shows a class implementing **multiple interfaces**.

### Abstract class vs interface (very common question)
- Abstract class: "is-a" relationship, can hold fields and shared code, a class extends only **one**. (`Seat`)
- Interface: a capability or contract, no state, a class can implement **many**. (`Bookable`)

### Custom exceptions
`SeatAlreadyBookedException`, `HoldExpiredException`, `AuthenticationException`, `DataAccessException`, all extending `CineConnectException`, which extends `RuntimeException` (unchecked). The UI catches `CineConnectException` and shows a friendly message instead of crashing.
*Why unchecked?* It avoids `throws` clutter through every layer; errors are caught once, at the UI.

---

## 4. The four design patterns

### 1. Singleton, `Database`
Only one `Database` object exists; `Database.getInstance()` returns it. Constructor is `private`, the instance is `static`, and `getInstance()` is `synchronized` so two threads cannot create two instances.
*Why?* One central place for the connection settings (URL, user, password). Tests use `Database.configure(...)` to point it at an in-memory database.
*Note:* it shares the **settings**, but each call to `getConnection()` makes a **new connection**, so threads never share a connection.

### 2. Factory, `SeatFactory`
`SeatFactory.create("PREMIUM", label, row, col, price)` returns a `PremiumSeat`; callers never write `new PremiumSeat(...)`. When we load a seat from the database we only know its type as text, and the factory turns that into the right object.
*Benefit:* adding a new seat type (e.g. "RECLINER") means changing one place.

### 3. Observer, `SeatEventBus` / `SeatEventListener`
`SeatEventBus` is the *subject*; screens are *observers*. A screen calls `subscribe(listener)`. When `BookingService` changes any seat it calls `publish(showId)`, and every subscribed screen is told `onSeatsChanged(showId)` and redraws its seat map.
*Effect:* when customer A takes a seat, customer B's screen turns it orange/red without pressing refresh.
*Implementation detail:* a `CopyOnWriteArrayList` so threads can add/remove listeners safely during notification. Swing updates are pushed onto the UI thread with `SwingUtilities.invokeLater`.

### 4. Strategy, pricing and recommendation
A *family of interchangeable algorithms* behind one interface.
- `PricingStrategy` → `StandardPricing` (base × seat multiplier), `WeekendPricing` (that × 1.2). `CatalogService` picks one depending on the show's day.
- `SeatRecommendationStrategy` → `CentreFocusStrategy`. `BookingService` receives it through its constructor, so a different recommender (e.g. "cheapest seats") could be plugged in without touching `BookingService`.

---

## 5. The core technical idea: preventing double booking

This is the part examiners will focus on.

### The naive (broken) approach
```
1. read seat -> is it free?   yes
2. write seat -> mark booked
```
Two users can both pass step 1 before either does step 2. Both think the seat is free. **Race condition.**

### Our approach: one atomic conditional UPDATE
```sql
UPDATE show_seats
SET status='HELD', held_by=?, hold_expires=?
WHERE show_id=? AND seat_label=?
  AND (status='AVAILABLE'
       OR (status='HELD' AND (hold_expires<=? OR held_by=?)))
```
"Check and change" happens as **one** database operation. The database locks the row, so competing requests queue up. The first one changes the row; the others re-check the `WHERE`, find the seat no longer `AVAILABLE`, and update **0 rows**. We read `executeUpdate()` — if it returns 0 we throw `SeatAlreadyBookedException`.

**Why this is better than Java `synchronized`:** a `synchronized` lock only works inside one running program. Two copies of the app (two PCs) would not see each other's locks. Locking in the database works for all of them. It is also called an *optimistic / conditional write*.

### Proof: the test
`BookingServiceTest.sameSeatCanOnlyBeBookedOnceUnderHeavyConcurrency` starts **40 threads** that all try to book seat C5 at the same moment. It asserts: exactly **1 success**, 39 `SeatAlreadyBookedException`.

### Transactions
`inTransaction(...)` in `BookingService` turns auto-commit off, runs the work, then **commits**; on any error it **rolls back**. This gives atomicity: `holdSeats` for A1, A2, A3 is *all or nothing*. If A2 is taken, A1's hold is undone.
*Also:* seat labels are sorted before updating so two users grabbing overlapping seats lock rows in the same order, which prevents **deadlock**.

**ACID in one line each:** Atomicity (all or nothing), Consistency (rules kept), Isolation (concurrent users don't see half-done work), Durability (committed data survives a crash).

---

## 6. The seat life cycle

```
 AVAILABLE --hold--> HELD --confirm/pay--> BOOKED
     ^                 |                      |
     |-- release / 2 min timeout              |
     |------------------ cancel booking ------|
```

- **Hold:** `holdSeats` sets `status=HELD`, `held_by=user`, `hold_expires = now + 120 s`.
- **Pay:** `confirmBooking` selects the user's still-valid holds (`hold_expires > now`), inserts a `bookings` row, flips seats to `BOOKED`, writes `booking_seats`. If the hold expired it throws `HoldExpiredException`.
- **Auto-release (2-minute timer), two layers:**
  1. **Lazy:** when seats are read or held, a `HELD` seat whose `hold_expires <= now` counts as available.
  2. **Background:** `SeatHoldManager` runs a daemon thread on a `ScheduledExecutorService` every 5 seconds and calls `releaseExpiredHolds()`, which sets stale holds back to `AVAILABLE` and notifies screens.
- **Cancel:** only the owner (or the admin overload) can cancel, only before the show starts. Seats go back to `AVAILABLE`.

**Why store the expiry time instead of a countdown thread per seat?** It survives app restarts and works for many users cheaply; the clock is injected (`java.time.Clock`) so tests can move time forward instead of waiting two real minutes.

---

## 7. Best-seat recommendation (`CentreFocusStrategy`)

Goal: suggest `n` seats together, close to the centre, not too near the screen.

1. Compute an ideal row (**60% of the way back**) and the centre column.
2. For each row, slide a window of `n` neighbouring seats along it; skip windows containing a non-available seat.
3. Score each window: `|row - idealRow| × 1.5 + |col - centreCol|`, averaged over the seats. **Lower is better.**
4. Return the lowest-scoring window.
5. **Fallback:** if no window of `n` seats together exists, return the `n` best individual seats. If fewer than `n` seats are free, return an empty list.

This is a simple *greedy / heuristic* algorithm. Complexity is O(rows × columns × n), trivial for a cinema.

---

## 8. Database design

| Table | Purpose | Key columns |
|---|---|---|
| `users` | accounts | username (unique), password_hash, role |
| `movies` | catalogue | title, genre, duration, rating |
| `theatres` | locations | name, city |
| `shows` | a movie at a theatre at a time | movie_id, theatre_id, start_time, rows, cols, base_price |
| `show_seats` | **one row per seat per show** | PK (show_id, seat_label), status, held_by, hold_expires, price |
| `bookings` | a confirmed purchase | user_id, show_id, total, status |
| `booking_seats` | which seats belong to which booking | booking_id, seat_label, price (history) |

- **Primary keys** identify rows, **foreign keys** link tables (e.g. `shows.movie_id → movies.id`).
- `show_seats` has a *composite* primary key: a seat label is unique only within a show.
- Time is stored as `BIGINT` epoch milliseconds for simplicity and so the same SQL works on MySQL and the H2 test database.
- Revenue counts only bookings with `status='CONFIRMED'`, so cancelled tickets are not included.

**JDBC basics:** `DriverManager.getConnection` → `PreparedStatement` (SQL with `?` placeholders) → `executeQuery` / `executeUpdate` → read `ResultSet`. We always use **try-with-resources** so connections close automatically.

**Why PreparedStatement?** It prevents **SQL injection**, because user input is sent as data, never glued into the SQL text.

---

## 9. Security

- Passwords are never stored. `PasswordHasher` uses **PBKDF2-HMAC-SHA256** with a random 16-byte **salt** and 65,536 iterations, stored as `salt$hash`.
- *Salt* means two users with the same password get different hashes. *Many iterations* make brute-forcing slow.
- Verification uses `MessageDigest.isEqual`, a constant-time comparison.
- Role-based access: after login, `LoginFrame` opens `AdminFrame` or `CustomerFrame` based on `user.getRole()`. Only admins get catalogue management and the dashboard, and the cancel method checks the booking belongs to the user.

---

## 10. The Swing UI

- `LoginFrame`: login and registration.
- `CustomerFrame`: two tabs, *Book tickets* (show list + seat map + hold/pay) and *My bookings* (cancel).
- `SeatPanel`: builds the grid of coloured `JButton`s (green regular, yellow premium, blue selected, purple your hold, orange held by others, red booked).
- `AdminFrame`: *Dashboard* (cards, per-show table, busiest timings, revenue by movie, auto-refresh every 10 s using a `javax.swing.Timer`) and *Manage catalogue*.
- **Threading rule:** Swing screens must only be touched on the *Event Dispatch Thread*. Background events (the Observer callbacks) use `SwingUtilities.invokeLater(...)` to hop onto it.
- The customer countdown uses a 1-second Swing `Timer`.

---

## 11. Dashboard calculations (`DashboardService`)

- **Occupancy** of a show = booked seats ÷ total seats × 100.
- **Revenue** = sum of `bookings.total` where status is CONFIRMED (a SQL subquery with `SUM`).
- **Busiest show timings:** group shows by start *hour* and sum booked seats, sorted descending.
- **Revenue by movie:** group and sum per title.
- Results are returned as Java `record`s (`ShowStats`, `TimeSlotStats`, `MovieRevenue`): small immutable data holders.

---

## 12. Testing (21 automated tests, run with `mvn test`)

Tests use **JUnit 5** and an in-memory **H2** database in MySQL-compatibility mode, so no MySQL is needed.

| Test | Proves |
|---|---|
| 40 threads on one seat | exactly one winner |
| 10 threads on different seats | all succeed in parallel |
| hold expiry (fake clock) | blocked for 119 s, free after 121 s |
| pay after expiry | throws `HoldExpiredException` |
| multi-seat hold with one taken | nothing is held (all or nothing) |
| cancel | frees seats; only the owner may cancel |
| observer | listener is told when seats change |
| dashboard | occupancy, revenue, busiest slot, cancelled excluded |
| recommendation | centre block, skips taken seats, fallback, not enough seats |
| auth | register/login, wrong password, duplicate username |

`MutableClock` is a test helper: because the service takes a `Clock` (dependency injection), tests can advance time by hand.

---

## 13. Maven in one paragraph

`pom.xml` lists the dependencies (MySQL driver, H2 and JUnit for tests). Maven downloads them and builds the project. `mvn test` runs the tests; `mvn package` builds a runnable jar (the *shade* plugin bundles the libraries and sets `Main` as the start class).

---

## 14. Likely viva questions and answers

**Q: How do you prevent double booking?**
Each hold or booking is one atomic conditional SQL UPDATE (`WHERE status='AVAILABLE' ...`). Only one concurrent request changes the row; the others update zero rows and get `SeatAlreadyBookedException`. We tested it with 40 threads.

**Q: Why not just use `synchronized` in Java?**
It only protects one running JVM. With two app instances, or a web server and a desktop app, the lock is invisible to the other. The database is the one shared authority, so the lock belongs there.

**Q: What is a race condition?**
When the result depends on the timing of threads; e.g. two threads both read "seat free" and both book it.

**Q: What happens if a user holds seats and walks away?**
The hold has an expiry time. After 2 minutes the seat counts as available immediately, and the background sweeper formally releases it within 5 seconds.

**Q: Why is the exception unchecked?**
To avoid `throws` declarations through every layer; the UI catches `CineConnectException` in one place.

**Q: Difference between overloading and overriding?**
Overloading: same method name, different parameters, decided at compile time (`searchMovies`). Overriding: subclass replaces a parent method, decided at runtime (`getPriceMultiplier`).

**Q: Where did you use abstraction?**
Abstract classes `User` and `Seat`, and `BookingService`, which hides JDBC behind `bookSeat()`.

**Q: Why use the Factory pattern?**
Seat type comes from the database as a string; the factory creates the right subclass in one place, so callers don't depend on concrete classes.

**Q: Why Singleton for the database?**
A single shared configuration point. (If asked about downsides: global state makes testing harder, which is why `configure()` exists.)

**Q: How does the seat map update live?**
Observer pattern: the service publishes on `SeatEventBus`; each open screen subscribed as a `SeatEventListener` and redraws.

**Q: What is Strategy and where is it used?**
Interchangeable algorithms behind an interface: pricing (standard/weekend) and seat recommendation. We can swap them without editing `BookingService`.

**Q: What is a transaction? Why rollback?**
A group of SQL statements treated as one unit. If seat 2 of 3 is taken, rollback undoes the hold on seat 1.

**Q: How are passwords stored?**
Salted PBKDF2 hashes, never plain text.

**Q: How do you stop SQL injection?**
`PreparedStatement` with `?` placeholders.

**Q: How is the weekend price calculated?**
`WeekendPricing` extends `StandardPricing` and multiplies by 1.2; `CatalogService` chooses it based on the day of the week of the show. Premium seats are 1.5× the base.

**Q: Why store time as a number?**
Epoch milliseconds are simple, timezone-neutral and behave identically on MySQL and H2.

**Q: Why is there no connection pool?**
Each operation opens a short-lived connection, which is fine at this scale and keeps threads from sharing connections. A pool (e.g. HikariCP) would be the production improvement.

**Q: Can this scale or be improved?**
Yes: connection pooling, a web or REST front end, a real payment gateway, email/SMS tickets, QR codes, seat categories per screen, and showing hold countdowns server-side.

**Q: Limitations?**
Payment is simulated (the "Pay & confirm" button); there is no real payment gateway. Hold expiry accuracy depends on the system clocks of the machines running the app (they share a MySQL server, so in real deployment you'd use the database's clock). The sweeper runs every 5 seconds.

**Q: What would you do for a web version?**
Keep the `service` layer unchanged and replace `ui` with controllers; this is exactly why logic and screens are separated.

---

## 15. Files to open when asked "show me the code"

| Topic | File |
|---|---|
| Double booking, holds, transactions | `service/BookingService.java` (`holdSeats`, `confirmBooking`, `inTransaction`) |
| Singleton | `db/Database.java` |
| Factory | `model/SeatFactory.java` |
| Observer | `observer/SeatEventBus.java`, used in `ui/CustomerFrame.java` |
| Strategy | `strategy/*`, `service/CatalogService.addShow`, `service/BookingService.recommendSeats` |
| Inheritance / abstraction | `model/User.java`, `Admin.java`, `Customer.java`, `Seat.java`, `RegularSeat.java`, `PremiumSeat.java` |
| Interfaces | `service/Bookable.java`, `Cancellable.java` |
| Exceptions | `exception/*` |
| Timer / threads | `service/SeatHoldManager.java` |
| Tests | `src/test/java/com/cineconnect/` |
| UML | `docs/CLASS_DIAGRAM.md` |

**Tip:** run the app on two copies, hold a seat on one, and show it turning orange on the other. That demonstrates the whole concurrency and Observer story in 10 seconds.
