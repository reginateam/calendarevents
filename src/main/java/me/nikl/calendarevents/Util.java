package me.nikl.calendarevents;

import java.time.Duration;

public class Util {

    private static final int TICKS_PER_SECOND = 20;

    public static Duration ticksToSeconds(long ticks) {
        long seconds = ticks / TICKS_PER_SECOND;
        long remainingTicks = ticks % TICKS_PER_SECOND;

        long nanos = remainingTicks * 50_000_000;

        return Duration.ofSeconds(seconds, nanos);
    }
}
