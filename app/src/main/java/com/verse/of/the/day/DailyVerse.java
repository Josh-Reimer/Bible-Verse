package com.verse.of.the.day;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.Wearable;

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
 *
 * <p>The verse is also published to the Wear OS app as a Data Layer item ({@link #WEAR_PATH}),
 * so a paired watch shows the same one. Only the reference and the day travel — the watch
 * renders the text in its own translation.
 */
final class DailyVerse {

    private static final String PREF_REF = "widget_verse_ref";
    private static final String PREF_DAY = "widget_verse_day";

    // Must match WearDailyVerse in the :wear module.
    private static final String WEAR_PATH = "/daily_verse";
    private static final String WEAR_KEY_REF = "ref";
    private static final String WEAR_KEY_DAY = "day";

    private static final Bible bible = new Bible();
    private static final Tools tools = new Tools();

    private DailyVerse() {}

    /** Today's verse, picking (and storing) a new one if the stored one is from another day. */
    static synchronized String today(Context context) {
        SharedPreferences sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE);
        String ref = sp.getString(PREF_REF, null);
        long day = LocalDate.now().toEpochDay();
        if (ref != null && sp.getLong(PREF_DAY, 0) == day) {
            // Republished on every read, not just on a pick: a watch paired (or an app
            // updated) after today's verse was picked would otherwise wait until tomorrow.
            // An unchanged item is not re-sent to the watch.
            publishToWatch(context, ref, day);
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
        long day = LocalDate.now().toEpochDay();
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
                .putString(PREF_REF, ref)
                .putLong(PREF_DAY, day)
                .apply();
        publishToWatch(context, ref, day);
        return ref;
    }

    /**
     * Fire-and-forget: on a phone with no watch, or without the Wear OS app, the Data Layer
     * API is simply unavailable and the task fails — nothing here depends on it succeeding.
     */
    private static void publishToWatch(Context context, String ref, long day) {
        try {
            PutDataMapRequest request = PutDataMapRequest.create(WEAR_PATH);
            request.getDataMap().putString(WEAR_KEY_REF, ref);
            request.getDataMap().putLong(WEAR_KEY_DAY, day);
            Wearable.getDataClient(context.getApplicationContext())
                    .putDataItem(request.asPutDataRequest().setUrgent())
                    .addOnFailureListener(e -> { });
        } catch (RuntimeException e) {
            // Play services missing entirely — the phone app works the same without a watch.
        }
    }
}
