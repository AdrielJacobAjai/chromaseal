package com.cineconnect;

import com.cineconnect.db.Database;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

final class TestSupport {
    private static final AtomicInteger COUNTER = new AtomicInteger();

    private TestSupport() { }

    static AppContext newContext(MutableClock clock) {
        String url = "jdbc:h2:mem:cc" + COUNTER.incrementAndGet() + ";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=20000";
        Database db = Database.configure(url, "sa", "");
        db.initSchema();
        return new AppContext(db, clock, 120);
    }

    /** A clock the test can move forward by hand. */
    static class MutableClock extends Clock {
        private final AtomicLong millis = new AtomicLong(Instant.parse("2030-01-01T10:00:00Z").toEpochMilli());

        void advance(Duration d) { millis.addAndGet(d.toMillis()); }

        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return Instant.ofEpochMilli(millis.get()); }
        @Override public long millis() { return millis.get(); }
    }
}
