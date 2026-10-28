package com.cineconnect.exception;

public class CineConnectException extends RuntimeException {
    public CineConnectException(String message) { super(message); }
    public CineConnectException(String message, Throwable cause) { super(message, cause); }
}
