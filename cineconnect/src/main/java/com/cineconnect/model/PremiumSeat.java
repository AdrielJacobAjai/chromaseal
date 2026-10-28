package com.cineconnect.model;

public class PremiumSeat extends Seat {
    public PremiumSeat(String label, int row, int col, double price) {
        super(label, row, col, price);
    }

    @Override
    public String getType() { return SeatFactory.PREMIUM; }

    @Override
    public double getPriceMultiplier() { return 1.5; }
}
