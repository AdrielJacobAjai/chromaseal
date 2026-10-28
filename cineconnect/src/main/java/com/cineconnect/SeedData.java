package com.cineconnect;

import com.cineconnect.model.Movie;
import com.cineconnect.model.Theatre;
import com.cineconnect.service.AuthService;
import com.cineconnect.service.CatalogService;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

/** Fills an empty database with an admin account and some demo movies, theatres and shows. */
public final class SeedData {
    private SeedData() { }

    public static void seedIfEmpty(AuthService auth, CatalogService catalog) {
        if (auth.hasUsers()) {
            return;
        }
        auth.createAdmin("admin", "admin123", "Theatre Admin");
        auth.register("customer", "customer123", "Demo Customer");

        Theatre t1 = catalog.addTheatre("PVR Lulu Mall", "Kochi");
        Theatre t2 = catalog.addTheatre("Cinepolis Centre Square", "Kochi");
        Theatre t3 = catalog.addTheatre("Carnival Infopark", "Thrikkakara");

        Movie m1 = catalog.addMovie("Stellar Run", "Sci-Fi", 128, "U/A");
        Movie m2 = catalog.addMovie("The Last Monsoon", "Drama", 142, "U");
        Movie m3 = catalog.addMovie("Laugh Riot", "Comedy", 110, "U");
        Movie m4 = catalog.addMovie("Night Shift", "Thriller", 119, "A");

        Theatre[] theatres = {t1, t2, t3};
        Movie[] movies = {m1, m2, m3, m4};
        LocalTime[] times = {LocalTime.of(10, 30), LocalTime.of(14, 0), LocalTime.of(18, 30), LocalTime.of(21, 45)};
        LocalDate today = LocalDate.now();
        for (int day = 0; day < 3; day++) {
            for (int i = 0; i < times.length; i++) {
                Movie movie = movies[(i + day) % movies.length];
                Theatre theatre = theatres[(i + day) % theatres.length];
                long start = today.plusDays(day).atTime(times[i]).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                catalog.addShow(movie.getId(), theatre.getId(), "Screen " + (i % 2 + 1), start, 8, 12, 180);
            }
        }
    }
}
