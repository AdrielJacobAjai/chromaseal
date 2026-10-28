package com.cineconnect;

import com.cineconnect.db.Database;
import com.cineconnect.observer.SeatEventBus;
import com.cineconnect.service.AuthService;
import com.cineconnect.service.BookingService;
import com.cineconnect.service.CatalogService;
import com.cineconnect.service.DashboardService;
import com.cineconnect.service.SeatHoldManager;

import java.time.Clock;

/** Wires the services together once so every screen shares the same instances. */
public class AppContext {
    public static final long HOLD_SECONDS = 120;

    private final Database database;
    private final SeatEventBus eventBus = new SeatEventBus();
    private final AuthService authService;
    private final CatalogService catalogService;
    private final BookingService bookingService;
    private final DashboardService dashboardService;
    private final SeatHoldManager holdManager;

    public AppContext(Database database, Clock clock, long holdSeconds) {
        this.database = database;
        this.authService = new AuthService(database);
        this.catalogService = new CatalogService(database);
        this.bookingService = new BookingService(database, clock, holdSeconds * 1000, eventBus);
        this.dashboardService = new DashboardService(database);
        this.holdManager = new SeatHoldManager(bookingService, 5);
    }

    public Database getDatabase() { return database; }
    public SeatEventBus getEventBus() { return eventBus; }
    public AuthService getAuthService() { return authService; }
    public CatalogService getCatalogService() { return catalogService; }
    public BookingService getBookingService() { return bookingService; }
    public DashboardService getDashboardService() { return dashboardService; }
    public SeatHoldManager getHoldManager() { return holdManager; }
}
