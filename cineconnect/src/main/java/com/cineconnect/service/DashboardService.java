package com.cineconnect.service;

import com.cineconnect.db.Database;
import com.cineconnect.exception.DataAccessException;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Numbers for the admin dashboard: occupancy, busiest show timings and revenue. */
public class DashboardService {
    public record ShowStats(int showId, String movieTitle, String theatreName, long startTime,
                            int totalSeats, int bookedSeats, double revenue) {
        public double occupancyPercent() {
            return totalSeats == 0 ? 0 : bookedSeats * 100.0 / totalSeats;
        }
    }

    public record TimeSlotStats(int hour, int bookedSeats) { }

    public record MovieRevenue(String movieTitle, double revenue) { }

    private final Database db;

    public DashboardService(Database db) {
        this.db = db;
    }

    public List<ShowStats> getShowStats() {
        String sql = "SELECT s.id,m.title,t.name,s.start_time,"
                + "(SELECT COUNT(*) FROM show_seats x WHERE x.show_id=s.id),"
                + "(SELECT COUNT(*) FROM show_seats x WHERE x.show_id=s.id AND x.status='BOOKED'),"
                + "COALESCE((SELECT SUM(b.total) FROM bookings b WHERE b.show_id=s.id AND b.status='CONFIRMED'),0) "
                + "FROM shows s JOIN movies m ON m.id=s.movie_id JOIN theatres t ON t.id=s.theatre_id "
                + "ORDER BY s.start_time, s.id";
        List<ShowStats> stats = new ArrayList<>();
        try (Connection c = db.getConnection(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                stats.add(new ShowStats(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getLong(4),
                        rs.getInt(5), rs.getInt(6), rs.getDouble(7)));
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not load dashboard: " + e.getMessage(), e);
        }
        return stats;
    }

    public double getTotalRevenue() {
        double total = 0;
        for (ShowStats s : getShowStats()) {
            total += s.revenue();
        }
        return total;
    }

    /** Overall percentage of seats booked across all shows. */
    public double getOverallOccupancy() {
        int seats = 0;
        int booked = 0;
        for (ShowStats s : getShowStats()) {
            seats += s.totalSeats();
            booked += s.bookedSeats();
        }
        return seats == 0 ? 0 : booked * 100.0 / seats;
    }

    /** Hours of the day ranked by number of booked seats, busiest first. */
    public List<TimeSlotStats> getBusiestTimeSlots() {
        Map<Integer, Integer> byHour = new TreeMap<>();
        for (ShowStats s : getShowStats()) {
            int hour = Instant.ofEpochMilli(s.startTime()).atZone(ZoneId.systemDefault()).getHour();
            byHour.merge(hour, s.bookedSeats(), Integer::sum);
        }
        List<TimeSlotStats> slots = new ArrayList<>();
        byHour.forEach((hour, booked) -> slots.add(new TimeSlotStats(hour, booked)));
        slots.sort(Comparator.comparingInt(TimeSlotStats::bookedSeats).reversed());
        return slots;
    }

    public List<MovieRevenue> getRevenueByMovie() {
        Map<String, Double> byMovie = new TreeMap<>();
        for (ShowStats s : getShowStats()) {
            byMovie.merge(s.movieTitle(), s.revenue(), Double::sum);
        }
        List<MovieRevenue> result = new ArrayList<>();
        byMovie.forEach((title, revenue) -> result.add(new MovieRevenue(title, revenue)));
        result.sort(Comparator.comparingDouble(MovieRevenue::revenue).reversed());
        return result;
    }
}
