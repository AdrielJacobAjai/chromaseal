package com.cineconnect.service;

public interface Cancellable {
    void cancelBooking(int bookingId, int userId);
}
