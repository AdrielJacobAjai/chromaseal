package com.cineconnect.model;

public class Movie {
    private final int id;
    private final String title;
    private final String genre;
    private final int durationMin;
    private final String rating;

    public Movie(int id, String title, String genre, int durationMin, String rating) {
        this.id = id;
        this.title = title;
        this.genre = genre;
        this.durationMin = durationMin;
        this.rating = rating;
    }

    public int getId() { return id; }
    public String getTitle() { return title; }
    public String getGenre() { return genre; }
    public int getDurationMin() { return durationMin; }
    public String getRating() { return rating; }

    @Override
    public String toString() { return title; }
}
