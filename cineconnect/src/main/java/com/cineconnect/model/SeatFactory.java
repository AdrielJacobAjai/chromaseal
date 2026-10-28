package com.cineconnect.model;

/** Factory pattern: callers ask for a seat by type name and never construct subclasses directly. */
public final class SeatFactory {
    public static final String REGULAR = "REGULAR";
    public static final String PREMIUM = "PREMIUM";

    private SeatFactory() { }

    public static Seat create(String type, String label, int row, int col, double price) {
        return switch (type) {
            case PREMIUM -> new PremiumSeat(label, row, col, price);
            case REGULAR -> new RegularSeat(label, row, col, price);
            default -> throw new IllegalArgumentException("Unknown seat type: " + type);
        };
    }

    public static String labelFor(int row, int col) {
        return (char) ('A' + row) + String.valueOf(col + 1);
    }
}
