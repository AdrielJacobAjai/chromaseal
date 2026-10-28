package com.cineconnect.observer;

public interface SeatEventListener {
    /** Called whenever the seats of a show change (hold, release, booking, cancellation). */
    void onSeatsChanged(int showId);
}
