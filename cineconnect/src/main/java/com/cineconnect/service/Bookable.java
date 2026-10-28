package com.cineconnect.service;

import com.cineconnect.model.Booking;

import java.util.List;

public interface Bookable {
    Booking bookSeats(int userId, int showId, List<String> seatLabels);
}
