package com.cineconnect.strategy;

import com.cineconnect.model.Seat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Prefers seats about 60% of the way back from the screen and close to the centre column.
 * Looks for a block of seats side by side first; if none exists, falls back to the best
 * individual seats.
 */
public class CentreFocusStrategy implements SeatRecommendationStrategy {
    private static final double ROW_WEIGHT = 1.5;
    private static final double IDEAL_ROW_FRACTION = 0.6;

    @Override
    public List<Seat> recommend(List<Seat> seatMap, int count) {
        if (count <= 0 || seatMap.isEmpty()) {
            return List.of();
        }
        int maxRow = 0;
        int maxCol = 0;
        for (Seat s : seatMap) {
            maxRow = Math.max(maxRow, s.getRow());
            maxCol = Math.max(maxCol, s.getCol());
        }
        double idealRow = maxRow * IDEAL_ROW_FRACTION;
        double centreCol = maxCol / 2.0;

        Map<Integer, List<Seat>> byRow = new TreeMap<>();
        for (Seat s : seatMap) {
            byRow.computeIfAbsent(s.getRow(), r -> new ArrayList<>()).add(s);
        }

        List<Seat> best = null;
        double bestScore = Double.MAX_VALUE;
        for (List<Seat> row : byRow.values()) {
            row.sort(Comparator.comparingInt(Seat::getCol));
            for (int start = 0; start + count <= row.size(); start++) {
                List<Seat> window = row.subList(start, start + count);
                if (!allAvailable(window)) {
                    continue;
                }
                double score = windowScore(window, idealRow, centreCol);
                if (score < bestScore) {
                    bestScore = score;
                    best = new ArrayList<>(window);
                }
            }
        }
        if (best != null) {
            return best;
        }

        List<Seat> available = new ArrayList<>();
        for (Seat s : seatMap) {
            if (s.isAvailable()) {
                available.add(s);
            }
        }
        if (available.size() < count) {
            return List.of();
        }
        available.sort(Comparator.comparingDouble(s -> seatScore(s, idealRow, centreCol)));
        return new ArrayList<>(available.subList(0, count));
    }

    private static boolean allAvailable(List<Seat> seats) {
        for (Seat s : seats) {
            if (!s.isAvailable()) {
                return false;
            }
        }
        return true;
    }

    private static double windowScore(List<Seat> window, double idealRow, double centreCol) {
        double sum = 0;
        for (Seat s : window) {
            sum += seatScore(s, idealRow, centreCol);
        }
        return sum / window.size();
    }

    private static double seatScore(Seat s, double idealRow, double centreCol) {
        return Math.abs(s.getRow() - idealRow) * ROW_WEIGHT + Math.abs(s.getCol() - centreCol);
    }
}
