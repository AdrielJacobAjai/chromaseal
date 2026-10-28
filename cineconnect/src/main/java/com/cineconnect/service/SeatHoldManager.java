package com.cineconnect.service;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Background timer that releases seat holds nobody paid for within the hold period. */
public class SeatHoldManager {
    private final BookingService bookingService;
    private final long intervalSeconds;
    private ScheduledExecutorService scheduler;

    public SeatHoldManager(BookingService bookingService, long intervalSeconds) {
        this.bookingService = bookingService;
        this.intervalSeconds = intervalSeconds;
    }

    public synchronized void start() {
        if (scheduler != null) {
            return;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "seat-hold-sweeper");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleWithFixedDelay(() -> {
            try {
                bookingService.releaseExpiredHolds();
            } catch (RuntimeException e) {
                System.err.println("Hold sweep failed: " + e.getMessage());
            }
        }, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
    }

    public synchronized void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }
}
