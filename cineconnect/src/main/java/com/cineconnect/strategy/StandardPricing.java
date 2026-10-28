package com.cineconnect.strategy;

import com.cineconnect.model.Seat;

public class StandardPricing implements PricingStrategy {
    @Override
    public double priceFor(double basePrice, Seat seat) {
        return Math.round(basePrice * seat.getPriceMultiplier() * 100.0) / 100.0;
    }
}
