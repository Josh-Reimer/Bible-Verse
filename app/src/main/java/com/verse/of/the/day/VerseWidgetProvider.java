package com.verse.of.the.day;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.text.Spanned;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;

import java.time.LocalDate;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Home-screen widget showing one verse a day.
 *
 * <p>The verse is picked once per calendar day and cached in the shared
 * {@code "settings"} preferences, so every placed widget shows the same verse and a
 * periodic update doesn't reshuffle it. The die button picks a new one on demand.
 * This is deliberately independent of the verse {@code MainActivity} is showing —
 * that one rerolls on every cold start.
 *
 * <p>Colours come from {@code values}/{@code values-night}, i.e. the widget follows the
 * system light/dark setting rather than the app's {@code "theme_mode"} preference: a
 * RemoteViews host inflates these views in its own process, where
 * {@code AppCompatDelegate} has no say.
 */
public class VerseWidgetProvider extends AppWidgetProvider {

    static final String ACTION_SHUFFLE = "com.verse.of.the.day.WIDGET_SHUFFLE";

    private static final String PREF_REF = "widget_verse_ref";
    private static final String PREF_DAY = "widget_verse_day";

    // Rendering reads a whole book file from assets plus the red-letter JSON; that must
    // stay off the broadcast's main thread, which has only a few seconds before an ANR.
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    private static final Bible bible = new Bible();
    private static final Tools tools = new Tools();
    private static final RedLetter redLetter = new RedLetter();

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        // Safe here: onUpdate runs inside AppWidgetProvider's own onReceive call.
        final PendingResult pending = goAsync();
        final Context appContext = context.getApplicationContext();
        executor.execute(() -> {
            try {
                render(appContext, currentRef(appContext));
            } finally {
                pending.finish();
            }
        });
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (ACTION_SHUFFLE.equals(intent.getAction())) {
            final PendingResult pending = goAsync();
            final Context appContext = context.getApplicationContext();
            executor.execute(() -> {
                try {
                    render(appContext, pickNewVerse(appContext));
                } finally {
                    pending.finish();
                }
            });
            return;
        }
        super.onReceive(context, intent);
    }

    /**
     * Re-renders every placed widget on a worker thread. Called when a setting that
     * affects the widget's text (the translation) changes.
     */
    static void refresh(Context context) {
        final Context appContext = context.getApplicationContext();
        executor.execute(() -> render(appContext, currentRef(appContext)));
    }

    private static void render(Context context, String ref) {
        try {
            AppWidgetManager manager = AppWidgetManager.getInstance(context);
            int[] ids = manager.getAppWidgetIds(new ComponentName(context, VerseWidgetProvider.class));
            if (ids.length == 0) return;
            manager.updateAppWidget(ids, buildViews(context, ref));
        } catch (Exception e) {
            // A broken render must not take the app's process down from a background thread.
            Log.e("verse-widget", "could not render widget for " + ref, e);
        }
    }

    private static RemoteViews buildViews(Context context, String ref) {
        Verse verse = new Verse(context, ref);
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.verse_widget);

        views.setTextViewText(R.id.widget_reference,
                verse.proper_book + " " + verse.chapter + ":" + verse.verse);

        // ForegroundColorSpan is parcelable, so the words of Christ survive the trip
        // into the host process.
        Spanned spanned = redLetter.getSpanned(context, ref);
        views.setTextViewText(R.id.widget_verse,
                spanned != null ? spanned : scriptureOnly(verse.scripture_text));

        SharedPreferences sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE);
        if (sp.getBoolean("show_translation_info", false)) {
            views.setTextViewText(R.id.widget_translation,
                    Translations.currentEntry(context).label);
            views.setViewVisibility(R.id.widget_translation, View.VISIBLE);
        } else {
            views.setViewVisibility(R.id.widget_translation, View.GONE);
        }

        Intent open = new Intent(context, MainActivity.class)
                .putExtra("verse_ref", ref)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        views.setOnClickPendingIntent(R.id.widget_card, PendingIntent.getActivity(
                context, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));

        Intent shuffle = new Intent(context, VerseWidgetProvider.class).setAction(ACTION_SHUFFLE);
        views.setOnClickPendingIntent(R.id.widget_shuffle, PendingIntent.getBroadcast(
                context, 1, shuffle, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));

        return views;
    }

    /** Today's verse, picking (and storing) a new one if the stored one is from another day. */
    private static String currentRef(Context context) {
        SharedPreferences sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE);
        String ref = sp.getString(PREF_REF, null);
        if (ref != null && sp.getLong(PREF_DAY, 0) == LocalDate.now().toEpochDay()) {
            return ref;
        }
        return pickNewVerse(context);
    }

    private static String pickNewVerse(Context context) {
        String ref = new VerseOfTheDay(null, context).getRandomRef(bible, tools, context).reference;
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
                .putString(PREF_REF, ref)
                .putLong(PREF_DAY, LocalDate.now().toEpochDay())
                .apply();
        return ref;
    }

    /** Bible.getVerse() hands back the raw asset line, which starts with "chapter:verse: ". */
    private static String scriptureOnly(String line) {
        String[] parts = line.split(":", 3);
        return parts.length == 3 ? parts[2].trim() : line.trim();
    }
}
