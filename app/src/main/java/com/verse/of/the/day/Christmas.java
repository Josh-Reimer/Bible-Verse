package com.verse.of.the.day;

import java.time.Month;
import java.time.MonthDay;

/**
 * The Christmas Day easter egg: on December 25 (the device's local date) the main screen
 * takes a red toolbar, a "Merry Christmas" title and falling snow, and the day's verse is
 * Luke 2:11 rather than a random one. Nothing is stored — the next day everything is back
 * to normal on its own.
 */
final class Christmas {

    /** Luke 2:11 — "For unto you is born this day in the city of David a Saviour…" */
    static final String NATIVITY_REF = "41:2:11";

    private static final MonthDay CHRISTMAS_DAY = MonthDay.of(Month.DECEMBER, 25);

    private Christmas() {}

    static boolean isToday() {
        return MonthDay.now().equals(CHRISTMAS_DAY);
    }
}
