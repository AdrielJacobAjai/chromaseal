package com.cineconnect.strategy;

import com.cineconnect.model.Seat;

public interface PricingStrategy {
    double priceFor(double basePrice, Seat seat);
}
