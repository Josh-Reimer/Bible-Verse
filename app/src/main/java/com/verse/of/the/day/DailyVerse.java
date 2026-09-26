package com.verse.of.the.day;

import android.content.Context;
import android.content.SharedPreferences;

import java.time.LocalDate;

/**
 * The one verse of the day shared by the home-screen widget, the daily notification and
 * {@code MainActivity}'s cold start, so all three show the same verse.
 *
 * <p>Stored in {@code "settings"} as a reference plus the epoch day it was picked on and
 * rerolled the first time it is asked for on a new day. The keys keep their original
 * {@code widget_} names — the widget owned this verse before the other two shared it —
 * so an existing install keeps today's verse across the update.
 *
 * <p>Picking reads book files, so callers off the main thread are expected; methods are
 * synchronized because the widget's and the notification's executors can both hit a new
 * day at once and must not each store a different verse.
 */
final class DailyVerse {

    private static final String PREF_REF = "widget_verse_ref";
    private static final String PREF_DAY = "widget_verse_day";

    private static final Bible bible = new Bible();
    private static final Tools tools = new Tools();

    private DailyVerse() {}

    /** Today's verse, picking (and storing) a new one if the stored one is from another day. */
    static synchronized String today(Context context) {
        SharedPreferences sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE);
        String ref = sp.getString(PREF_REF, null);
        if (ref != null && sp.getLong(PREF_DAY, 0) == LocalDate.now().toEpochDay()) {
            return ref;
        }
        ref = reroll(context);
        // A new day's verse picked here by the app or the notification would otherwise sit
        // unseen until the widget's next hourly update, showing yesterday's verse meanwhile.
        VerseWidgetProvider.refresh(context);
        return ref;
    }

    /** Replaces today's verse with a new random one (the widget's die). */
    static synchronized String reroll(Context context) {
        String ref = new VerseOfTheDay(null, context).getRandomRef(bible, tools, context).reference;
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
                .putString(PREF_REF, ref)
                .putLong(PREF_DAY, LocalDate.now().toEpochDay())
                .apply();
        return ref;
    }
}
