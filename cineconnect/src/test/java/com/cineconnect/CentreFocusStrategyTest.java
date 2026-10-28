package com.cineconnect;

import com.cineconnect.model.Seat;
import com.cineconnect.model.SeatFactory;
import com.cineconnect.model.SeatStatus;
import com.cineconnect.strategy.CentreFocusStrategy;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CentreFocusStrategyTest {
    private static List<Seat> grid(int rows, int cols) {
        List<Seat> seats = new ArrayList<>();
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                seats.add(SeatFactory.create("REGULAR", SeatFactory.labelFor(r, c), r, c, 100));
            }
        }
        return seats;
    }

    @Test
    void picksCentralBlockOnEmptyHall() {
        List<Seat> picks = new CentreFocusStrategy().recommend(grid(8, 12), 2);
        assertEquals(2, picks.size());
        assertTrue(picks.get(0).getCol() >= 4 && picks.get(1).getCol() <= 7, "near centre columns");
        assertTrue(picks.get(0).getRow() >= 3 && picks.get(0).getRow() <= 5, "mid-to-back rows");
    }

    @Test
    void skipsTakenSeatsAndKeepsGroupTogether() {
        List<Seat> seats = grid(1, 6);
        seats.get(2).setStatus(SeatStatus.BOOKED);
        List<Seat> picks = new CentreFocusStrategy().recommend(seats, 3);
        assertEquals(List.of("A4", "A5", "A6"), picks.stream().map(Seat::getLabel).toList());
    }

    @Test
    void fallsBackToSeparateSeatsWhenNoBlockFits() {
        List<Seat> seats = grid(1, 5);
        seats.get(1).setStatus(SeatStatus.BOOKED);
        seats.get(3).setStatus(SeatStatus.BOOKED);
        assertEquals(3, new CentreFocusStrategy().recommend(seats, 3).size());
    }

    @Test
    void returnsNothingWhenNotEnoughSeats() {
        List<Seat> seats = grid(1, 3);
        seats.get(0).setStatus(SeatStatus.BOOKED);
        assertTrue(new CentreFocusStrategy().recommend(seats, 3).isEmpty());
    }
}
