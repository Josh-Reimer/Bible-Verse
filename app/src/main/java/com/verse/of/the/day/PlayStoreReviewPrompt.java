package com.verse.of.the.day;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.android.gms.tasks.Task;
import com.google.android.play.core.review.ReviewInfo;
import com.google.android.play.core.review.ReviewManager;
import com.google.android.play.core.review.ReviewManagerFactory;

import java.util.concurrent.TimeUnit;

/**
 * Requests the official Google Play in-app review flow (com.google.android.play:review) at most
 * once, and only when the user is an established, engaged user who has just finished a task.
 *
 * Eligibility (all must hold):
 *  - the review flow has never been requested before (persisted flag — "only ever once"),
 *  - the app was first launched at least {@link #MIN_DAYS_SINCE_INSTALL} days ago,
 *  - the app has been opened on at least {@link #MIN_DISTINCT_LAUNCH_DAYS} distinct calendar days.
 *
 * The trigger is always a task-completion moment (e.g. right after a verse is bookmarked), never
 * mid-task, so the system dialog never interrupts something the user is doing. The Play API has its
 * own quota and may show nothing; our own flag guarantees we never even ask for it twice.
 */
public final class PlayStoreReviewPrompt {

    private static final String TAG = "review-prompt";

    private static final String PREFS = "review_prefs";
    private static final String KEY_FIRST_LAUNCH = "first_launch_time";
    private static final String KEY_LAUNCH_DAYS = "distinct_launch_days";
    private static final String KEY_LAST_LAUNCH_DAY = "last_launch_day";
    private static final String KEY_REVIEW_REQUESTED = "review_requested";

    private static final long MIN_DAYS_SINCE_INSTALL = 2;
    private static final int MIN_DISTINCT_LAUNCH_DAYS = 2;

    private PlayStoreReviewPrompt() {}

    /**
     * Records that the app was opened. Call once per app entry (e.g. MainActivity.onCreate). Seeds
     * the first-launch timestamp and counts distinct calendar days the app has been used. Cheap and
     * side-effect free beyond the SharedPreferences write.
     */
    public static void recordAppOpen(Context context) {
        SharedPreferences sp = prefs(context);
        long now = System.currentTimeMillis();
        long today = dayNumber(now);

        SharedPreferences.Editor editor = sp.edit();
        if (!sp.contains(KEY_FIRST_LAUNCH)) {
            editor.putLong(KEY_FIRST_LAUNCH, now);
            editor.putInt(KEY_LAUNCH_DAYS, 1);
            editor.putLong(KEY_LAST_LAUNCH_DAY, today);
        } else if (sp.getLong(KEY_LAST_LAUNCH_DAY, today) != today) {
            editor.putInt(KEY_LAUNCH_DAYS, sp.getInt(KEY_LAUNCH_DAYS, 1) + 1);
            editor.putLong(KEY_LAST_LAUNCH_DAY, today);
        }
        editor.apply();
    }

    /**
     * Call at a natural task-completion moment (e.g. after a verse is bookmarked). If the user is
     * eligible, launches the Play in-app review flow and permanently marks it as requested so it can
     * never appear again. No-op otherwise.
     */
    public static void maybeRequestReview(Activity activity) {
        if (activity == null || activity.isFinishing()) {
            return;
        }
        SharedPreferences sp = prefs(activity);
        if (!isEligible(sp)) {
            return;
        }
        // Flag before launching: the flow only ever runs once even if it fails, is dismissed, or
        // the Play quota suppresses it. We deliberately do not retry.
        sp.edit().putBoolean(KEY_REVIEW_REQUESTED, true).apply();

        ReviewManager manager = ReviewManagerFactory.create(activity);
        Task<ReviewInfo> request = manager.requestReviewFlow();
        request.addOnCompleteListener(task -> {
            if (activity.isFinishing() || activity.isDestroyed()) {
                return;
            }
            if (!task.isSuccessful()) {
                Log.i(TAG, "requestReviewFlow failed", task.getException());
                return;
            }
            ReviewInfo reviewInfo = task.getResult();
            manager.launchReviewFlow(activity, reviewInfo)
                    .addOnCompleteListener(flow -> Log.i(TAG, "review flow finished"));
        });
    }

    private static boolean isEligible(SharedPreferences sp) {
        if (sp.getBoolean(KEY_REVIEW_REQUESTED, false)) {
            return false;
        }
        long firstLaunch = sp.getLong(KEY_FIRST_LAUNCH, System.currentTimeMillis());
        long daysSinceInstall = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - firstLaunch);
        if (daysSinceInstall < MIN_DAYS_SINCE_INSTALL) {
            return false;
        }
        return sp.getInt(KEY_LAUNCH_DAYS, 1) >= MIN_DISTINCT_LAUNCH_DAYS;
    }

    private static long dayNumber(long millis) {
        // Distinct calendar day in the device's local time zone.
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTimeInMillis(millis);
        long year = cal.get(java.util.Calendar.YEAR);
        long dayOfYear = cal.get(java.util.Calendar.DAY_OF_YEAR);
        return year * 1000 + dayOfYear;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
