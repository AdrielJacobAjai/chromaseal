package com.cineconnect.strategy;

import com.cineconnect.model.Seat;

import java.util.List;

public interface SeatRecommendationStrategy {
    /**
     * @param seatMap every seat of the show
     * @param count   how many seats the customer wants
     * @return the recommended seats, or an empty list if there are not enough available seats
     */
    List<Seat> recommend(List<Seat> seatMap, int count);
}
