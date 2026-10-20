package com.verse.of.the.day;

import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * The Easter easter egg, following the story from the cross to the empty tomb. On Good Friday
 * and Holy Saturday the main screen goes dark purple and the drawer shows three crosses on
 * Golgotha under a darkened sun. On Easter Sunday it turns gold, "He is risen!", with light
 * rising behind the verse and the empty tomb at sunrise in the drawer. Each day has its own
 * verse instead of a random one. Nothing is stored, so the next day everything goes back to
 * normal by itself.
 *
 * <p>Dates follow the Western (Gregorian) Easter, using the device's local date.
 */
final class Easter {

    enum Day {
        // John 19:30, "It is finished"
        GOOD_FRIDAY("42:19:30", R.string.good_friday, R.style.Theme_VerseApp_Passion,
                R.drawable.easter_golgotha, R.drawable.bible_passion),
        // Isaiah 53:5, "But he was wounded for our transgressions…"
        HOLY_SATURDAY("22:53:5", R.string.holy_saturday, R.style.Theme_VerseApp_Passion,
                R.drawable.easter_golgotha, R.drawable.bible_passion),
        // Matthew 28:6, "He is not here: for he is risen, as he said."
        EASTER_SUNDAY("39:28:6", R.string.he_is_risen, R.style.Theme_VerseApp_Resurrection,
                R.drawable.easter_empty_tomb, R.drawable.bible_easter);

        final String verseRef;
        final int title, theme, drawerScene, logo;

        Day(String verseRef, int title, int theme, int drawerScene, int logo) {
            this.verseRef = verseRef;
            this.title = title;
            this.theme = theme;
            this.drawerScene = drawerScene;
            this.logo = logo;
        }
    }

    private Easter() {}

    /** Today's day of the Easter story, or {@code null} on every other day of the year. */
    static Day today() {
        return on(LocalDate.now());
    }

    static Day on(LocalDate date) {
        long daysToEaster = date.until(sunday(date.getYear()), java.time.temporal.ChronoUnit.DAYS);
        if (daysToEaster == 2) return Day.GOOD_FRIDAY;
        if (daysToEaster == 1) return Day.HOLY_SATURDAY;
        if (daysToEaster == 0) return Day.EASTER_SUNDAY;
        return null;
    }

    /** Western Easter Sunday for {@code year} (the anonymous Gregorian computus). */
    static LocalDate sunday(int year) {
        int a = year % 19;
        int b = year / 100, c = year % 100;
        int d = b / 4, e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4, k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int month = (h + l - 7 * m + 114) / 31;
        int day = (h + l - 7 * m + 114) % 31 + 1;
        LocalDate easter = LocalDate.of(year, month, day);
        assert easter.getDayOfWeek() == DayOfWeek.SUNDAY;
        return easter;
    }
}
