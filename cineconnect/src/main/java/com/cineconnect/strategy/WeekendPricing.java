package com.cineconnect.strategy;

import com.cineconnect.model.Seat;

public class WeekendPricing extends StandardPricing {
    private static final double SURCHARGE = 1.2;

    @Override
    public double priceFor(double basePrice, Seat seat) {
        return Math.round(super.priceFor(basePrice, seat) * SURCHARGE * 100.0) / 100.0;
    }
}
