package com.smartparking.poc.demo;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

/** A clock the demo can move forward, so "2 hours later" takes no time at all. */
public class DemoClock extends Clock {

    private final AtomicReference<Instant> now;

    public DemoClock(Instant start) {
        this.now = new AtomicReference<>(start);
    }

    public void advance(Duration duration) {
        now.updateAndGet(t -> t.plus(duration));
    }

    @Override
    public Instant instant() {
        return now.get();
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        throw new UnsupportedOperationException();
    }
}
