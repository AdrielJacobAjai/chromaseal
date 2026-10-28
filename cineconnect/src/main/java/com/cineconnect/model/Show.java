package com.cineconnect.model;

public class Show {
    private final int id;
    private final int movieId;
    private final String movieTitle;
    private final String genre;
    private final int theatreId;
    private final String theatreName;
    private final String screen;
    private final long startTime;
    private final int rows;
    private final int cols;
    private final double basePrice;

    public Show(int id, int movieId, String movieTitle, String genre, int theatreId, String theatreName,
                String screen, long startTime, int rows, int cols, double basePrice) {
        this.id = id;
        this.movieId = movieId;
        this.movieTitle = movieTitle;
        this.genre = genre;
        this.theatreId = theatreId;
        this.theatreName = theatreName;
        this.screen = screen;
        this.startTime = startTime;
        this.rows = rows;
        this.cols = cols;
        this.basePrice = basePrice;
    }

    public int getId() { return id; }
    public int getMovieId() { return movieId; }
    public String getMovieTitle() { return movieTitle; }
    public String getGenre() { return genre; }
    public int getTheatreId() { return theatreId; }
    public String getTheatreName() { return theatreName; }
    public String getScreen() { return screen; }
    public long getStartTime() { return startTime; }
    public int getRows() { return rows; }
    public int getCols() { return cols; }
    public double getBasePrice() { return basePrice; }
}
