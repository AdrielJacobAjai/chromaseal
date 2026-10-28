package com.cineconnect;

import com.cineconnect.exception.CineConnectException;
import com.cineconnect.exception.HoldExpiredException;
import com.cineconnect.exception.SeatAlreadyBookedException;
import com.cineconnect.model.Booking;
import com.cineconnect.model.Movie;
import com.cineconnect.model.Seat;
import com.cineconnect.model.Show;
import com.cineconnect.model.Theatre;
import com.cineconnect.model.User;
import com.cineconnect.service.BookingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class BookingServiceTest {
    private TestSupport.MutableClock clock;
    private AppContext ctx;
    private BookingService booking;
    private Show show;
    private User alice;
    private User bob;

    @BeforeEach
    void setUp() {
        clock = new TestSupport.MutableClock();
        ctx = TestSupport.newContext(clock);
        booking = ctx.getBookingService();
        Movie movie = ctx.getCatalogService().addMovie("Test Movie", "Drama", 100, "U");
        Theatre theatre = ctx.getCatalogService().addTheatre("Test Theatre", "Kochi");
        long start = Instant.parse("2030-01-02T18:00:00Z").toEpochMilli();
        show = ctx.getCatalogService().addShow(movie.getId(), theatre.getId(), "Screen 1", start, 6, 10, 100);
        alice = ctx.getAuthService().register("alice", "secret1", "Alice");
        bob = ctx.getAuthService().register("bob", "secret2", "Bob");
    }

    @Test
    void showCreatesAllSeatsWithPremiumRowsPricedHigher() {
        List<Seat> seats = booking.getSeatMap(show.getId());
        assertEquals(60, seats.size());
        assertEquals("REGULAR", seats.get(0).getType());
        assertEquals(100.0, seats.get(0).getPrice());
        Seat last = seats.get(seats.size() - 1);
        assertEquals("PREMIUM", last.getType());
        assertEquals(150.0, last.getPrice());
    }

    @Test
    void sameSeatCanOnlyBeBookedOnceUnderHeavyConcurrency() throws Exception {
        int threads = 40;
        List<User> users = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            users.add(ctx.getAuthService().register("user" + i, "password", "User " + i));
        }
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger wins = new AtomicInteger();
        AtomicInteger losses = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (User u : users) {
            futures.add(pool.submit(() -> {
                go.await();
                try {
                    booking.bookSeat(u.getId(), show.getId(), "C5");
                    wins.incrementAndGet();
                } catch (SeatAlreadyBookedException e) {
                    losses.incrementAndGet();
                }
                return null;
            }));
        }
        go.countDown();
        for (Future<?> f : futures) {
            f.get(60, TimeUnit.SECONDS);
        }
        pool.shutdown();
        assertEquals(1, wins.get(), "exactly one customer gets the seat");
        assertEquals(threads - 1, losses.get());
    }

    @Test
    void differentSeatsCanBeBookedInParallel() throws Exception {
        List<User> users = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            users.add(ctx.getAuthService().register("p" + i, "password", "P " + i));
        }
        ExecutorService pool = Executors.newFixedThreadPool(10);
        List<Future<Booking>> futures = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            final int n = i;
            futures.add(pool.submit(() -> booking.bookSeat(users.get(n).getId(), show.getId(), "A" + (n + 1))));
        }
        for (Future<Booking> f : futures) {
            assertTrue(f.get(60, TimeUnit.SECONDS).isConfirmed());
        }
        pool.shutdown();
        assertEquals(50, booking.getAvailableSeats(show.getId()).size());
    }

    @Test
    void holdBlocksOthersThenExpiresAfterTwoMinutes() {
        booking.holdSeat(alice.getId(), show.getId(), "B3");
        assertThrows(SeatAlreadyBookedException.class, () -> booking.holdSeat(bob.getId(), show.getId(), "B3"));

        clock.advance(Duration.ofSeconds(119));
        assertThrows(SeatAlreadyBookedException.class, () -> booking.holdSeat(bob.getId(), show.getId(), "B3"));

        clock.advance(Duration.ofSeconds(2));
        assertTrue(booking.getAvailableSeats(show.getId()).stream().anyMatch(s -> s.getLabel().equals("B3")));
        assertEquals(1, booking.releaseExpiredHolds());
        booking.holdSeat(bob.getId(), show.getId(), "B3");
    }

    @Test
    void cannotPayAfterHoldExpired() {
        booking.holdSeat(alice.getId(), show.getId(), "B4");
        clock.advance(Duration.ofSeconds(121));
        assertThrows(HoldExpiredException.class, () -> booking.confirmBooking(alice.getId(), show.getId()));
    }

    @Test
    void holdThenConfirmCreatesBookingAndMarksSeatsBooked() {
        booking.holdSeats(alice.getId(), show.getId(), List.of("A1", "A2"));
        Booking b = booking.confirmBooking(alice.getId(), show.getId());
        assertEquals(List.of("A1", "A2"), b.getSeats());
        assertEquals(200.0, b.getTotal());
        assertThrows(SeatAlreadyBookedException.class, () -> booking.holdSeat(bob.getId(), show.getId(), "A1"));
        assertEquals(1, booking.getBookingsForUser(alice.getId()).size());
    }

    @Test
    void holdingSeveralSeatsIsAllOrNothing() {
        booking.holdSeat(bob.getId(), show.getId(), "A2");
        assertThrows(SeatAlreadyBookedException.class,
                () -> booking.holdSeats(alice.getId(), show.getId(), List.of("A1", "A2", "A3")));
        List<String> available = booking.getAvailableSeats(show.getId()).stream().map(Seat::getLabel).toList();
        assertTrue(available.contains("A1"));
        assertTrue(available.contains("A3"));
    }

    @Test
    void cancellingFreesSeatsAndOnlyOwnerMayCancel() {
        Booking b = booking.bookSeats(alice.getId(), show.getId(), List.of("D1"));
        assertThrows(CineConnectException.class, () -> booking.cancelBooking(b.getId(), bob.getId()));
        booking.cancelBooking(b.getId(), alice.getId());
        assertFalse(booking.getBooking(b.getId()).isConfirmed());
        booking.bookSeat(bob.getId(), show.getId(), "D1");
        assertThrows(CineConnectException.class, () -> booking.cancelBooking(b.getId(), alice.getId()));
    }

    @Test
    void releaseHoldsFreesSeatsImmediately() {
        booking.holdSeats(alice.getId(), show.getId(), List.of("E1", "E2"));
        booking.releaseHolds(alice.getId(), show.getId());
        booking.holdSeat(bob.getId(), show.getId(), "E1");
    }

    @Test
    void observersAreToldWhenSeatsChange() {
        List<Integer> seen = new ArrayList<>();
        ctx.getEventBus().subscribe(seen::add);
        booking.holdSeat(alice.getId(), show.getId(), "F1");
        assertEquals(List.of(show.getId()), seen);
    }

    @Test
    void recommendationReturnsSeatsSideBySide() {
        List<Seat> picks = booking.recommendSeats(show.getId(), 3);
        assertEquals(3, picks.size());
        int row = picks.get(0).getRow();
        for (int i = 0; i < picks.size(); i++) {
            assertEquals(row, picks.get(i).getRow());
            assertEquals(picks.get(0).getCol() + i, picks.get(i).getCol());
        }
    }
}
