package com.verse.of.the.day.legacy;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.time.LocalDate;
import java.util.TimeZone;

// The two places this module re-implements something :app gets from a newer API.
public class LegacyHelpersTest {

    @Test
    public void epochDayMatchesLocalDate() {
        TimeZone saved = TimeZone.getDefault();
        try {
            // Either side of UTC, so a sign error in the offset would show.
            for (String zone : new String[]{"UTC", "America/St_Johns", "Asia/Tokyo", "Pacific/Kiritimati"}) {
                TimeZone.setDefault(TimeZone.getTimeZone(zone));
                assertEquals(zone, LocalDate.now().toEpochDay(), DailyVerse.epochDay());
            }
        } finally {
            TimeZone.setDefault(saved);
        }
    }

    @Test
    public void verseTextDropsChapterAndVersePrefix() {
        assertEquals("For God so loved the world", MainActivity.verseText("3:16: For God so loved the world"));
        assertEquals("Behold: a colon", MainActivity.verseText("1:2: Behold: a colon"));
        assertEquals("no prefix", MainActivity.verseText("no prefix"));
    }
}
