package com.cineconnect.ui;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

final class Fmt {
    private static final DateTimeFormatter SHOW_TIME =
            DateTimeFormatter.ofPattern("EEE dd MMM, HH:mm", Locale.ENGLISH).withZone(ZoneId.systemDefault());

    private Fmt() { }

    static String time(long millis) { return SHOW_TIME.format(Instant.ofEpochMilli(millis)); }

    static String money(double amount) { return String.format(Locale.ENGLISH, "Rs. %.2f", amount); }
}
