package com.cineconnect.exception;

public class SeatAlreadyBookedException extends CineConnectException {
    private final String seatLabel;

    public SeatAlreadyBookedException(String seatLabel) {
        super("Seat " + seatLabel + " is no longer available");
        this.seatLabel = seatLabel;
    }

    public String getSeatLabel() { return seatLabel; }
}
