package com.cineconnect;

import com.cineconnect.model.Movie;
import com.cineconnect.model.Show;
import com.cineconnect.model.Theatre;
import com.cineconnect.model.User;
import com.cineconnect.service.DashboardService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DashboardServiceTest {
    @Test
    void reportsOccupancyRevenueAndBusiestSlot() {
        AppContext ctx = TestSupport.newContext(new TestSupport.MutableClock());
        Movie m = ctx.getCatalogService().addMovie("Dash Movie", "Drama", 90, "U");
        Theatre t = ctx.getCatalogService().addTheatre("Dash Theatre", "Kochi");
        long start = Instant.parse("2030-01-02T18:00:00Z").toEpochMilli();
        Show s = ctx.getCatalogService().addShow(m.getId(), t.getId(), "S1", start, 3, 4, 100);
        User u = ctx.getAuthService().register("dash", "password", "Dash");

        ctx.getBookingService().bookSeats(u.getId(), s.getId(), List.of("A1", "A2"));

        DashboardService dash = ctx.getDashboardService();
        DashboardService.ShowStats stats = dash.getShowStats().get(0);
        assertEquals(12, stats.totalSeats());
        assertEquals(2, stats.bookedSeats());
        assertEquals(200.0, stats.revenue());
        assertEquals(200.0, dash.getTotalRevenue());
        assertEquals(2 * 100.0 / 12, dash.getOverallOccupancy(), 0.001);
        assertEquals(2, dash.getBusiestTimeSlots().get(0).bookedSeats());
        assertEquals("Dash Movie", dash.getRevenueByMovie().get(0).movieTitle());
    }

    @Test
    void cancelledBookingsDoNotCountTowardsRevenue() {
        AppContext ctx = TestSupport.newContext(new TestSupport.MutableClock());
        Movie m = ctx.getCatalogService().addMovie("Dash Movie", "Drama", 90, "U");
        Theatre t = ctx.getCatalogService().addTheatre("Dash Theatre", "Kochi");
        long start = Instant.parse("2030-01-02T18:00:00Z").toEpochMilli();
        Show s = ctx.getCatalogService().addShow(m.getId(), t.getId(), "S1", start, 3, 4, 100);
        User u = ctx.getAuthService().register("dash", "password", "Dash");
        var b = ctx.getBookingService().bookSeat(u.getId(), s.getId(), "A1");
        ctx.getBookingService().cancelBooking(b.getId(), u.getId());
        assertEquals(0.0, ctx.getDashboardService().getTotalRevenue());
        assertEquals(0, ctx.getDashboardService().getShowStats().get(0).bookedSeats());
    }
}
