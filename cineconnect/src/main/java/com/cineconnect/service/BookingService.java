package com.cineconnect.service;

import com.cineconnect.db.Database;
import com.cineconnect.exception.CineConnectException;
import com.cineconnect.exception.DataAccessException;
import com.cineconnect.exception.HoldExpiredException;
import com.cineconnect.exception.SeatAlreadyBookedException;
import com.cineconnect.model.Booking;
import com.cineconnect.model.Seat;
import com.cineconnect.model.SeatFactory;
import com.cineconnect.model.SeatStatus;
import com.cineconnect.observer.SeatEventBus;
import com.cineconnect.strategy.CentreFocusStrategy;
import com.cineconnect.strategy.SeatRecommendationStrategy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Hides all JDBC/MySQL detail behind methods such as {@link #bookSeat} and {@link #getAvailableSeats}.
 *
 * <p>Double booking is prevented inside the database: every hold and every booking is one atomic
 * conditional {@code UPDATE ... WHERE status = 'AVAILABLE'}. Only one of several competing requests
 * can change the row, the others see zero rows updated and get a {@link SeatAlreadyBookedException}.
 * This stays correct under many threads and even many running copies of the application.
 */
public class BookingService implements Bookable, Cancellable {
    private final Database db;
    private final Clock clock;
    private final long holdMillis;
    private final SeatEventBus bus;
    private final SeatRecommendationStrategy recommender;

    public BookingService(Database db, Clock clock, long holdMillis, SeatEventBus bus) {
        this(db, clock, holdMillis, bus, new CentreFocusStrategy());
    }

    public BookingService(Database db, Clock clock, long holdMillis, SeatEventBus bus,
                          SeatRecommendationStrategy recommender) {
        this.db = db;
        this.clock = clock;
        this.holdMillis = holdMillis;
        this.bus = bus;
        this.recommender = recommender;
    }

    public long getHoldMillis() { return holdMillis; }

    // ---------------------------------------------------------------- reading seats

    /** Every seat of the show; a hold whose timer ran out is shown as AVAILABLE. */
    public List<Seat> getSeatMap(int showId) {
        long now = clock.millis();
        List<Seat> seats = new ArrayList<>();
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT seat_label,row_idx,col_idx,seat_type,price,status,held_by,hold_expires "
                             + "FROM show_seats WHERE show_id=? ORDER BY row_idx, col_idx")) {
            ps.setInt(1, showId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Seat seat = SeatFactory.create(rs.getString("seat_type"), rs.getString("seat_label"),
                            rs.getInt("row_idx"), rs.getInt("col_idx"), rs.getDouble("price"));
                    SeatStatus status = SeatStatus.valueOf(rs.getString("status"));
                    long expires = rs.getLong("hold_expires");
                    if (status == SeatStatus.HELD && expires <= now) {
                        status = SeatStatus.AVAILABLE;
                    } else if (status == SeatStatus.HELD) {
                        seat.setHeldBy(rs.getInt("held_by"));
                        seat.setHoldExpires(expires);
                    }
                    seat.setStatus(status);
                    seats.add(seat);
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not load seats: " + e.getMessage(), e);
        }
        return seats;
    }

    public List<Seat> getAvailableSeats(int showId) {
        List<Seat> available = new ArrayList<>();
        for (Seat s : getSeatMap(showId)) {
            if (s.isAvailable()) {
                available.add(s);
            }
        }
        return available;
    }

    /** Suggests the best {@code count} seats for the show; empty if there are not enough. */
    public List<Seat> recommendSeats(int showId, int count) {
        return recommender.recommend(getSeatMap(showId), count);
    }

    // ---------------------------------------------------------------- holding seats

    public void holdSeat(int userId, int showId, String seatLabel) {
        holdSeats(userId, showId, List.of(seatLabel));
    }

    /** Holds all the seats or none of them. Throws SeatAlreadyBookedException naming the first lost seat. */
    public void holdSeats(int userId, int showId, List<String> seatLabels) {
        if (seatLabels.isEmpty()) {
            throw new IllegalArgumentException("Select at least one seat");
        }
        long now = clock.millis();
        long expires = now + holdMillis;
        // Sorted order means two customers grabbing overlapping seats never lock rows in opposite order.
        Set<String> ordered = new TreeSet<>(seatLabels);
        inTransaction(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE show_seats SET status='HELD', held_by=?, hold_expires=? "
                            + "WHERE show_id=? AND seat_label=? AND (status='AVAILABLE' "
                            + "OR (status='HELD' AND (hold_expires<=? OR held_by=?)))")) {
                for (String label : ordered) {
                    ps.setInt(1, userId);
                    ps.setLong(2, expires);
                    ps.setInt(3, showId);
                    ps.setString(4, label);
                    ps.setLong(5, now);
                    ps.setInt(6, userId);
                    if (ps.executeUpdate() == 0) {
                        throw new SeatAlreadyBookedException(label);
                    }
                }
            }
            return null;
        });
        bus.publish(showId);
    }

    public void releaseHolds(int userId, int showId) {
        inTransaction(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE show_seats SET status='AVAILABLE', held_by=NULL, hold_expires=0 "
                            + "WHERE show_id=? AND held_by=? AND status='HELD'")) {
                ps.setInt(1, showId);
                ps.setInt(2, userId);
                ps.executeUpdate();
            }
            return null;
        });
        bus.publish(showId);
    }

    /** Frees every hold whose timer has run out. Returns how many seats were freed. */
    public int releaseExpiredHolds() {
        long now = clock.millis();
        Set<Integer> shows = new LinkedHashSet<>();
        int freed = inTransaction(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT DISTINCT show_id FROM show_seats WHERE status='HELD' AND hold_expires<=?")) {
                ps.setLong(1, now);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        shows.add(rs.getInt(1));
                    }
                }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE show_seats SET status='AVAILABLE', held_by=NULL, hold_expires=0 "
                            + "WHERE status='HELD' AND hold_expires<=?")) {
                ps.setLong(1, now);
                return ps.executeUpdate();
            }
        });
        for (int showId : shows) {
            bus.publish(showId);
        }
        return freed;
    }

    // ---------------------------------------------------------------- booking

    /** Books the seats the user is currently holding for this show. */
    public Booking confirmBooking(int userId, int showId) {
        long now = clock.millis();
        int bookingId = inTransaction(c -> {
            List<String> labels = new ArrayList<>();
            List<Double> prices = new ArrayList<>();
            double total = 0;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT seat_label,price FROM show_seats "
                            + "WHERE show_id=? AND held_by=? AND status='HELD' AND hold_expires>? "
                            + "ORDER BY row_idx, col_idx FOR UPDATE")) {
                ps.setInt(1, showId);
                ps.setInt(2, userId);
                ps.setLong(3, now);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        labels.add(rs.getString(1));
                        prices.add(rs.getDouble(2));
                        total += rs.getDouble(2);
                    }
                }
            }
            if (labels.isEmpty()) {
                throw new HoldExpiredException();
            }
            int id;
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO bookings(user_id,show_id,total,created_at,status) VALUES (?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, userId);
                ps.setInt(2, showId);
                ps.setDouble(3, Math.round(total * 100.0) / 100.0);
                ps.setLong(4, now);
                ps.setString(5, Booking.CONFIRMED);
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    keys.next();
                    id = keys.getInt(1);
                }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE show_seats SET status='BOOKED', booking_id=?, held_by=NULL, hold_expires=0 "
                            + "WHERE show_id=? AND held_by=? AND status='HELD' AND hold_expires>?")) {
                ps.setInt(1, id);
                ps.setInt(2, showId);
                ps.setInt(3, userId);
                ps.setLong(4, now);
                if (ps.executeUpdate() != labels.size()) {
                    throw new HoldExpiredException();
                }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO booking_seats(booking_id,seat_label,price) VALUES (?,?,?)")) {
                for (int i = 0; i < labels.size(); i++) {
                    ps.setInt(1, id);
                    ps.setString(2, labels.get(i));
                    ps.setDouble(3, prices.get(i));
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            return id;
        });
        bus.publish(showId);
        return getBooking(bookingId);
    }

    public Booking bookSeat(int userId, int showId, String seatLabel) {
        return bookSeats(userId, showId, List.of(seatLabel));
    }

    @Override
    public Booking bookSeats(int userId, int showId, List<String> seatLabels) {
        holdSeats(userId, showId, seatLabels);
        try {
            return confirmBooking(userId, showId);
        } catch (CineConnectException e) {
            releaseHolds(userId, showId);
            throw e;
        }
    }

    // ---------------------------------------------------------------- cancelling

    @Override
    public void cancelBooking(int bookingId, int userId) {
        cancel(bookingId, userId);
    }

    /** Administrator version: may cancel any customer's booking. */
    public void cancelBooking(int bookingId) {
        cancel(bookingId, null);
    }

    private void cancel(int bookingId, Integer userId) {
        long now = clock.millis();
        int showId = inTransaction(c -> {
            int show;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT b.user_id,b.show_id,b.status,s.start_time FROM bookings b "
                            + "JOIN shows s ON s.id=b.show_id WHERE b.id=? FOR UPDATE")) {
                ps.setInt(1, bookingId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new CineConnectException("Booking not found");
                    }
                    if (userId != null && rs.getInt("user_id") != userId) {
                        throw new CineConnectException("This booking belongs to another customer");
                    }
                    if (!Booking.CONFIRMED.equals(rs.getString("status"))) {
                        throw new CineConnectException("Booking is already cancelled");
                    }
                    if (rs.getLong("start_time") <= now) {
                        throw new CineConnectException("This show has already started");
                    }
                    show = rs.getInt("show_id");
                }
            }
            try (PreparedStatement ps = c.prepareStatement("UPDATE bookings SET status=? WHERE id=?")) {
                ps.setString(1, Booking.CANCELLED);
                ps.setInt(2, bookingId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE show_seats SET status='AVAILABLE', booking_id=NULL WHERE booking_id=?")) {
                ps.setInt(1, bookingId);
                ps.executeUpdate();
            }
            return show;
        });
        bus.publish(showId);
    }

    // ---------------------------------------------------------------- reading bookings

    public Booking getBooking(int bookingId) {
        List<Booking> found = queryBookings("WHERE b.id=?", bookingId);
        if (found.isEmpty()) {
            throw new CineConnectException("Booking not found");
        }
        return found.get(0);
    }

    public List<Booking> getBookingsForUser(int userId) {
        return queryBookings("WHERE b.user_id=?", userId);
    }

    private List<Booking> queryBookings(String where, int param) {
        List<Booking> result = new ArrayList<>();
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT b.id,b.user_id,b.show_id,b.total,b.created_at,b.status,m.title,t.name,s.start_time "
                             + "FROM bookings b JOIN shows s ON s.id=b.show_id "
                             + "JOIN movies m ON m.id=s.movie_id JOIN theatres t ON t.id=s.theatre_id "
                             + where + " ORDER BY b.created_at DESC, b.id DESC");
             PreparedStatement seatPs = c.prepareStatement(
                     "SELECT seat_label FROM booking_seats WHERE booking_id=? ORDER BY seat_label")) {
            ps.setInt(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    List<String> seats = new ArrayList<>();
                    seatPs.setInt(1, rs.getInt("id"));
                    try (ResultSet srs = seatPs.executeQuery()) {
                        while (srs.next()) {
                            seats.add(srs.getString(1));
                        }
                    }
                    Collections.sort(seats);
                    result.add(new Booking(rs.getInt("id"), rs.getInt("user_id"), rs.getInt("show_id"),
                            rs.getString("title"), rs.getString("name"), rs.getLong("start_time"),
                            rs.getDouble("total"), rs.getLong("created_at"), rs.getString("status"), seats));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not load bookings: " + e.getMessage(), e);
        }
        return result;
    }

    // ---------------------------------------------------------------- plumbing

    @FunctionalInterface
    private interface TxWork<T> {
        T run(Connection c) throws SQLException;
    }

    private <T> T inTransaction(TxWork<T> work) {
        try (Connection c = db.getConnection()) {
            c.setAutoCommit(false);
            try {
                T result = work.run(c);
                c.commit();
                return result;
            } catch (SQLException e) {
                rollbackQuietly(c);
                throw new DataAccessException("Database error: " + e.getMessage(), e);
            } catch (RuntimeException e) {
                rollbackQuietly(c);
                throw e;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Database error: " + e.getMessage(), e);
        }
    }

    private static void rollbackQuietly(Connection c) {
        try {
            c.rollback();
        } catch (SQLException ignored) {
            // connection is closed right after, nothing more to do
        }
    }
}
