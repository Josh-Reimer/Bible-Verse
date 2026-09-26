package com.verse.of.the.day;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import com.google.firebase.FirebaseApp;
import com.google.firebase.analytics.FirebaseAnalytics;

/**
 * The one place the app talks to Firebase Analytics. Crashlytics needs no code of its own: with
 * both SDKs present it attaches the Analytics events logged here to each crash report as
 * breadcrumbs.
 *
 * Every method is safe to call when Firebase is not configured — a build made without
 * app/google-services.json has no FirebaseApp (see app/build.gradle), and logging then does
 * nothing instead of throwing. Methods take a Context and initialise on first use, because the
 * widget and the notification receiver can run in a process MainActivity never started.
 *
 * Nothing the reader types is ever logged — a search records whether it resolved as a reference
 * and how many results it found, never the query. The privacy policy (privacypolicy-gpage branch)
 * lists what is collected; an event added here belongs there too.
 */
public final class AnalyticsHelper {

    /** Intent extra naming where a screen was opened from; see the SOURCE_ values. */
    public static final String EXTRA_SOURCE = "analytics_source";

    public static final String SOURCE_MAIN = "main";
    public static final String SOURCE_SEARCH = "search";
    public static final String SOURCE_CHAPTER = "chapter";
    public static final String SOURCE_BOOKMARKS = "bookmarks";
    public static final String SOURCE_BROWSE = "browse";
    public static final String SOURCE_CONTINUE_READING = "continue_reading";
    public static final String SOURCE_SIMILAR_VERSE = "similar_verse";
    public static final String SOURCE_WIDGET = "widget";
    public static final String SOURCE_NOTIFICATION = "notification";

    private static FirebaseAnalytics analytics;

    private AnalyticsHelper() {}

    public static synchronized void init(Context context) {
        if (analytics != null) return;
        Context app = context.getApplicationContext();
        if (FirebaseApp.getApps(app).isEmpty()) return;
        analytics = FirebaseAnalytics.getInstance(app);
        syncUserProperties(app);
    }

    /**
     * User properties let every event be split by the reader's settings. Refreshed on init
     * (the translation can also move with the system language, outside Settings) and after
     * each change made in Settings.
     */
    public static void syncUserProperties(Context context) {
        FirebaseAnalytics a = instance(context);
        if (a == null) return;
        SharedPreferences sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE);
        a.setUserProperty("translation", Translations.current(context));
        a.setUserProperty("theme", sp.getString("theme_mode", "system"));
        a.setUserProperty("daily_notification", String.valueOf(VerseNotifier.isEnabled(context)));
    }

    /** @param verseId the verse's "bookIndex:chapter:verse" reference */
    public static void logVerseViewed(Context context, String verseId, String translation) {
        Bundle params = new Bundle();
        params.putString("verse_id", verseId);
        params.putString("translation", translation);
        log(context, "verse_viewed", params);
    }

    public static void logDiceRoll(Context context, String source) {
        log(context, "dice_roll", source(source));
    }

    public static void logBookmark(Context context, String verseId, boolean added, String source) {
        Bundle params = source(source);
        params.putString("verse_id", verseId);
        log(context, added ? "bookmark_added" : "bookmark_removed", params);
    }

    /** Firebase's recommended "share" event, so it lands in the standard reports. */
    public static void logShare(Context context, String verseId, String source) {
        Bundle params = source(source);
        params.putString(FirebaseAnalytics.Param.CONTENT_TYPE, "verse");
        params.putString(FirebaseAnalytics.Param.ITEM_ID, verseId);
        log(context, FirebaseAnalytics.Event.SHARE, params);
    }

    /** Deliberately not Firebase's "search" event, whose point is the search term. */
    public static void logSearch(Context context, boolean matchedReference, int resultCount) {
        Bundle params = new Bundle();
        params.putString("matched_reference", String.valueOf(matchedReference));
        params.putLong("result_count", resultCount);
        log(context, "search_performed", params);
    }

    public static void logChapterViewed(Context context, int bookIndex, int chapter, String source) {
        Bundle params = source(source);
        params.putString("chapter_id", bookIndex + ":" + chapter);
        log(context, "chapter_viewed", params);
    }

    public static void logVerseActionsOpened(Context context, String verseId) {
        Bundle params = new Bundle();
        params.putString("verse_id", verseId);
        log(context, "verse_actions_opened", params);
    }

    public static void logBrowseBookOpened(Context context, int bookIndex) {
        Bundle params = new Bundle();
        params.putLong("book_index", bookIndex);
        log(context, "browse_book_opened", params);
    }

    public static void logContinueReadingDismissed(Context context) {
        log(context, "continue_reading_dismissed", new Bundle());
    }

    /** MainActivity opened by tapping the widget or the daily notification. */
    public static void logAppOpenedFrom(Context context, String source) {
        log(context, "opened_from_" + source, new Bundle());
    }

    public static void logNotificationShown(Context context) {
        log(context, "daily_notification_shown", new Bundle());
    }

    public static void logWidget(Context context, boolean added) {
        log(context, added ? "widget_added" : "widget_removed", new Bundle());
    }

    /** @param setting theme, translation, translation_label, daily_notification or notification_time */
    public static void logSettingChanged(Context context, String setting, String value) {
        Bundle params = new Bundle();
        params.putString("setting", setting);
        params.putString("value", value);
        log(context, "setting_changed", params);
        syncUserProperties(context);
    }

    public static void logNotificationPermissionDenied(Context context) {
        log(context, "notification_permission_denied", new Bundle());
    }

    private static Bundle source(String source) {
        Bundle params = new Bundle();
        if (source != null) params.putString("source", source);
        return params;
    }

    private static FirebaseAnalytics instance(Context context) {
        if (analytics == null) init(context);
        return analytics;
    }

    private static void log(Context context, String event, Bundle params) {
        FirebaseAnalytics a = instance(context);
        if (a == null) return;
        a.logEvent(event, params);
    }
}
