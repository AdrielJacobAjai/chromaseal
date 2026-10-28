package com.cineconnect.model;

public class RegularSeat extends Seat {
    public RegularSeat(String label, int row, int col, double price) {
        super(label, row, col, price);
    }

    @Override
    public String getType() { return SeatFactory.REGULAR; }

    @Override
    public double getPriceMultiplier() { return 1.0; }
}
