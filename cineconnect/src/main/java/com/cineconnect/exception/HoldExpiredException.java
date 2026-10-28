package com.cineconnect.exception;

public class HoldExpiredException extends CineConnectException {
    public HoldExpiredException() {
        super("Your seat hold has expired. Please select your seats again.");
    }
}
