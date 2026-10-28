package com.cineconnect.observer;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Observer pattern: screens subscribe here to be told when seat availability changes. */
public class SeatEventBus {
    private final List<SeatEventListener> listeners = new CopyOnWriteArrayList<>();

    public void subscribe(SeatEventListener listener) { listeners.add(listener); }

    public void unsubscribe(SeatEventListener listener) { listeners.remove(listener); }

    public void publish(int showId) {
        for (SeatEventListener l : listeners) {
            l.onSeatsChanged(showId);
        }
    }
}
