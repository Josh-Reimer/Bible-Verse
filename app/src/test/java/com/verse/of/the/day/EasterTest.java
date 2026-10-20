package com.verse.of.the.day;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.time.LocalDate;

import org.junit.Test;

public class EasterTest {

    @Test
    public void sundayMatchesKnownDates() {
        assertEquals(LocalDate.of(2008, 3, 23), Easter.sunday(2008));  // early as it gets in a lifetime
        assertEquals(LocalDate.of(2019, 4, 21), Easter.sunday(2019));
        assertEquals(LocalDate.of(2024, 3, 31), Easter.sunday(2024));
        assertEquals(LocalDate.of(2025, 4, 20), Easter.sunday(2025));
        assertEquals(LocalDate.of(2026, 4, 5), Easter.sunday(2026));
        assertEquals(LocalDate.of(2027, 3, 28), Easter.sunday(2027));
        assertEquals(LocalDate.of(2038, 4, 25), Easter.sunday(2038));  // latest possible date
    }

    @Test
    public void onCoversFridayThroughSundayOnly() {
        assertNull(Easter.on(LocalDate.of(2027, 3, 25)));               // Maundy Thursday
        assertEquals(Easter.Day.GOOD_FRIDAY, Easter.on(LocalDate.of(2027, 3, 26)));
        assertEquals(Easter.Day.HOLY_SATURDAY, Easter.on(LocalDate.of(2027, 3, 27)));
        assertEquals(Easter.Day.EASTER_SUNDAY, Easter.on(LocalDate.of(2027, 3, 28)));
        assertNull(Easter.on(LocalDate.of(2027, 3, 29)));
        assertNull(Easter.on(LocalDate.of(2026, 12, 25)));
    }
}
