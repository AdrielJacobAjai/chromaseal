package com.cineconnect.service;

import com.cineconnect.db.Database;
import com.cineconnect.exception.CineConnectException;
import com.cineconnect.exception.DataAccessException;
import com.cineconnect.model.Movie;
import com.cineconnect.model.Seat;
import com.cineconnect.model.SeatFactory;
import com.cineconnect.model.Show;
import com.cineconnect.model.Theatre;
import com.cineconnect.strategy.PricingStrategy;
import com.cineconnect.strategy.StandardPricing;
import com.cineconnect.strategy.WeekendPricing;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/** Movies, theatres and shows. */
public class CatalogService {
    private static final String SHOW_SELECT =
            "SELECT s.id,s.movie_id,m.title,m.genre,s.theatre_id,t.name,s.screen,s.start_time,"
                    + "s.row_count,s.col_count,s.base_price FROM shows s "
                    + "JOIN movies m ON m.id=s.movie_id JOIN theatres t ON t.id=s.theatre_id ";

    private final Database db;

    public CatalogService(Database db) {
        this.db = db;
    }

    // ---------------------------------------------------------------- movies and theatres

    public Movie addMovie(String title, String genre, int durationMin, String rating) {
        if (title == null || title.isBlank() || durationMin <= 0) {
            throw new CineConnectException("A movie needs a title and a positive duration");
        }
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO movies(title,genre,duration_min,rating) VALUES (?,?,?,?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, title.trim());
            ps.setString(2, genre.trim());
            ps.setInt(3, durationMin);
            ps.setString(4, rating.trim());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return new Movie(keys.getInt(1), title.trim(), genre.trim(), durationMin, rating.trim());
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not add movie: " + e.getMessage(), e);
        }
    }

    public Theatre addTheatre(String name, String city) {
        if (name == null || name.isBlank()) {
            throw new CineConnectException("A theatre needs a name");
        }
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO theatres(name,city) VALUES (?,?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name.trim());
            ps.setString(2, city.trim());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return new Theatre(keys.getInt(1), name.trim(), city.trim());
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not add theatre: " + e.getMessage(), e);
        }
    }

    public List<Movie> listMovies() {
        return searchMovies("");
    }

    public List<Movie> searchMovies(String text) {
        return searchMovies(text, null);
    }

    /** Overload: search by title text and, optionally, an exact genre. */
    public List<Movie> searchMovies(String text, String genre) {
        List<Movie> movies = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT id,title,genre,duration_min,rating FROM movies WHERE LOWER(title) LIKE ?");
        if (genre != null && !genre.isBlank()) {
            sql.append(" AND LOWER(genre)=?");
        }
        sql.append(" ORDER BY title");
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql.toString())) {
            ps.setString(1, "%" + (text == null ? "" : text.trim().toLowerCase()) + "%");
            if (genre != null && !genre.isBlank()) {
                ps.setString(2, genre.trim().toLowerCase());
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    movies.add(new Movie(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getInt(4), rs.getString(5)));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not load movies: " + e.getMessage(), e);
        }
        return movies;
    }

    public List<Theatre> listTheatres() {
        List<Theatre> theatres = new ArrayList<>();
        try (Connection c = db.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT id,name,city FROM theatres ORDER BY name")) {
            while (rs.next()) {
                theatres.add(new Theatre(rs.getInt(1), rs.getString(2), rs.getString(3)));
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not load theatres: " + e.getMessage(), e);
        }
        return theatres;
    }

    // ---------------------------------------------------------------- shows

    /**
     * Creates the show and all of its seats. The last third of the rows (at least one) are premium seats.
     * Weekend shows use {@link WeekendPricing}, other days {@link StandardPricing}.
     */
    public Show addShow(int movieId, int theatreId, String screen, long startTime, int rows, int cols, double basePrice) {
        if (rows < 1 || rows > 26 || cols < 1 || cols > 40 || basePrice <= 0) {
            throw new CineConnectException("Rows must be 1-26, columns 1-40 and the price positive");
        }
        PricingStrategy pricing = isWeekend(startTime) ? new WeekendPricing() : new StandardPricing();
        int premiumRows = Math.max(1, rows / 3);
        try (Connection c = db.getConnection()) {
            c.setAutoCommit(false);
            try {
                int showId;
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO shows(movie_id,theatre_id,screen,start_time,row_count,col_count,base_price) "
                                + "VALUES (?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, movieId);
                    ps.setInt(2, theatreId);
                    ps.setString(3, screen);
                    ps.setLong(4, startTime);
                    ps.setInt(5, rows);
                    ps.setInt(6, cols);
                    ps.setDouble(7, basePrice);
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        keys.next();
                        showId = keys.getInt(1);
                    }
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO show_seats(show_id,seat_label,row_idx,col_idx,seat_type,price,status) "
                                + "VALUES (?,?,?,?,?,?,'AVAILABLE')")) {
                    for (int r = 0; r < rows; r++) {
                        String type = r >= rows - premiumRows ? SeatFactory.PREMIUM : SeatFactory.REGULAR;
                        for (int col = 0; col < cols; col++) {
                            Seat seat = SeatFactory.create(type, SeatFactory.labelFor(r, col), r, col, 0);
                            ps.setInt(1, showId);
                            ps.setString(2, seat.getLabel());
                            ps.setInt(3, r);
                            ps.setInt(4, col);
                            ps.setString(5, seat.getType());
                            ps.setDouble(6, pricing.priceFor(basePrice, seat));
                            ps.addBatch();
                        }
                    }
                    ps.executeBatch();
                }
                c.commit();
                return getShow(showId);
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not add show: " + e.getMessage(), e);
        }
    }

    private static boolean isWeekend(long startTime) {
        DayOfWeek day = Instant.ofEpochMilli(startTime).atZone(ZoneId.systemDefault()).getDayOfWeek();
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
    }

    public Show getShow(int showId) {
        List<Show> shows = queryShows("WHERE s.id=?", showId);
        if (shows.isEmpty()) {
            throw new CineConnectException("Show not found");
        }
        return shows.get(0);
    }

    public List<Show> listShows() {
        return listShows("");
    }

    /** Overload: only shows whose movie, genre or theatre matches the text. */
    public List<Show> listShows(String text) {
        String like = "%" + (text == null ? "" : text.trim().toLowerCase()) + "%";
        List<Show> shows = new ArrayList<>();
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(SHOW_SELECT
                     + "WHERE LOWER(m.title) LIKE ? OR LOWER(m.genre) LIKE ? OR LOWER(t.name) LIKE ? "
                     + "ORDER BY s.start_time, s.id")) {
            ps.setString(1, like);
            ps.setString(2, like);
            ps.setString(3, like);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    shows.add(readShow(rs));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not load shows: " + e.getMessage(), e);
        }
        return shows;
    }

    private List<Show> queryShows(String where, int param) {
        List<Show> shows = new ArrayList<>();
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(SHOW_SELECT + where)) {
            ps.setInt(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    shows.add(readShow(rs));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not load shows: " + e.getMessage(), e);
        }
        return shows;
    }

    private static Show readShow(ResultSet rs) throws SQLException {
        return new Show(rs.getInt(1), rs.getInt(2), rs.getString(3), rs.getString(4), rs.getInt(5), rs.getString(6),
                rs.getString(7), rs.getLong(8), rs.getInt(9), rs.getInt(10), rs.getDouble(11));
    }
}
