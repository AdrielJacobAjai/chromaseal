package com.cineconnect.model;

import java.util.List;

public class Booking {
    public static final String CONFIRMED = "CONFIRMED";
    public static final String CANCELLED = "CANCELLED";

    private final int id;
    private final int userId;
    private final int showId;
    private final String movieTitle;
    private final String theatreName;
    private final long showStart;
    private final double total;
    private final long createdAt;
    private final String status;
    private final List<String> seats;

    public Booking(int id, int userId, int showId, String movieTitle, String theatreName, long showStart,
                   double total, long createdAt, String status, List<String> seats) {
        this.id = id;
        this.userId = userId;
        this.showId = showId;
        this.movieTitle = movieTitle;
        this.theatreName = theatreName;
        this.showStart = showStart;
        this.total = total;
        this.createdAt = createdAt;
        this.status = status;
        this.seats = List.copyOf(seats);
    }

    public int getId() { return id; }
    public int getUserId() { return userId; }
    public int getShowId() { return showId; }
    public String getMovieTitle() { return movieTitle; }
    public String getTheatreName() { return theatreName; }
    public long getShowStart() { return showStart; }
    public double getTotal() { return total; }
    public long getCreatedAt() { return createdAt; }
    public String getStatus() { return status; }
    public List<String> getSeats() { return seats; }
    public boolean isConfirmed() { return CONFIRMED.equals(status); }
}
