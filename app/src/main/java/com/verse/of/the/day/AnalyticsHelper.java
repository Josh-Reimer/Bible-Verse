package com.verse.of.the.day;

import android.content.Context;
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
 * nothing instead of throwing.
 */
public final class AnalyticsHelper {

    private static FirebaseAnalytics analytics;

    private AnalyticsHelper() {}

    public static synchronized void init(Context context) {
        if (analytics != null) return;
        Context app = context.getApplicationContext();
        if (FirebaseApp.getApps(app).isEmpty()) return;
        analytics = FirebaseAnalytics.getInstance(app);
    }

    /** @param verseId the verse's "bookIndex:chapter:verse" reference */
    public static void logVerseViewed(String verseId, String translation) {
        FirebaseAnalytics a = analytics;
        if (a == null) return;
        Bundle params = new Bundle();
        params.putString("verse_id", verseId);
        params.putString("translation", translation);
        a.logEvent("verse_viewed", params);
    }
}
