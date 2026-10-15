package com.verse.of.the.day.legacy;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Calendar;
import java.util.Random;

/**
 * One verse per day, rerolled the first time it's asked for on a new day — :app's DailyVerse
 * without the widget refresh and the watch publishing, neither of which exists here.
 */
final class DailyVerse {

    private static final String PREF_REF = "widget_verse_ref";
    private static final String PREF_DAY = "widget_verse_day";

    private static final Bible bible = new Bible();
    private static final Tools tools = new Tools();

    private DailyVerse() {}

    static synchronized String today(Context context) {
        SharedPreferences sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE);
        String ref = sp.getString(PREF_REF, null);
        long day = epochDay();
        if (ref != null && sp.getLong(PREF_DAY, 0) == day) {
            return ref;
        }
        ref = random(context).reference;
        sp.edit().putString(PREF_REF, ref).putLong(PREF_DAY, day).apply();
        return ref;
    }

    /** A random verse — :app's VerseOfTheDay.getRandomRef(). */
    static Verse random(Context context) {
        Random dice = new Random();
        int randomBook = dice.nextInt(bible.books.length);
        int randomChapter = dice.nextInt(bible.getBookLength(tools, context, bible.books[randomBook])) + 1;
        int randomVerse = dice.nextInt(bible.getChapterLength(context, tools, bible.books[randomBook], randomChapter)) + 1;
        return new Verse(context, randomBook, randomChapter, randomVerse);
    }

    // The local date's epoch day, as LocalDate.now().toEpochDay() gives it — java.time is API 26.
    static long epochDay() {
        Calendar now = Calendar.getInstance();
        long local = now.getTimeInMillis() + now.get(Calendar.ZONE_OFFSET) + now.get(Calendar.DST_OFFSET);
        // Plain division is floor division for any date after 1970.
        return local / (24L * 60 * 60 * 1000);
    }
}
