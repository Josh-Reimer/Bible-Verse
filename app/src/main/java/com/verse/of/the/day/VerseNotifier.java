package com.verse.of.the.day;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * Opt-in daily notification carrying one random verse.
 *
 * <p>Scheduling is a one-shot {@link AlarmManager} alarm that
 * {@link VerseNotificationReceiver} re-arms for the next day every time it fires —
 * a repeating alarm would drift across a DST change, and an <em>exact</em> alarm
 * would need the restricted {@code SCHEDULE_EXACT_ALARM} permission for no real gain
 * on a once-a-day nudge. {@code setAndAllowWhileIdle} needs no permission and still
 * fires in doze, just with some slack.
 *
 * <p>Alarms do not survive a reboot or a force-stop, so the receiver also re-arms on
 * {@code BOOT_COMPLETED} / {@code MY_PACKAGE_REPLACED}.
 *
 * <p>The verse is picked fresh when the alarm fires and is deliberately independent of
 * both {@code MainActivity}'s verse and the widget's.
 */
final class VerseNotifier {

    static final String PREF_ENABLED = "daily_verse_notification";
    static final String ACTION_NOTIFY = "com.verse.of.the.day.DAILY_VERSE";

    private static final String CHANNEL_ID = "daily_verse";
    private static final int NOTIFICATION_ID = 1001;
    private static final int REQUEST_CODE = 2001;

    /** Local time of day the notification is posted. */
    private static final LocalTime NOTIFY_AT = LocalTime.of(8, 0);

    private static final Bible bible = new Bible();
    private static final Tools tools = new Tools();

    private VerseNotifier() {}

    static boolean isEnabled(Context context) {
        return context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                .getBoolean(PREF_ENABLED, false);
    }

    /** Persists the preference and arms or cancels the alarm to match. */
    static void setEnabled(Context context, boolean enabled) {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
                .putBoolean(PREF_ENABLED, enabled)
                .apply();
        if (enabled) {
            schedule(context);
        } else {
            cancel(context);
        }
    }

    /**
     * Arms the alarm for the next {@link #NOTIFY_AT}, but only if the feature is on —
     * safe to call unconditionally (boot, package replace, settings toggle).
     */
    static void scheduleIfEnabled(Context context) {
        if (isEnabled(context)) schedule(context);
    }

    static void schedule(Context context) {
        AlarmManager alarms = context.getSystemService(AlarmManager.class);
        if (alarms == null) return;
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTriggerMillis(), alarmIntent(context));
    }

    static void cancel(Context context) {
        AlarmManager alarms = context.getSystemService(AlarmManager.class);
        if (alarms != null) alarms.cancel(alarmIntent(context));
    }

    /** True once the user has granted (or the OS does not require) the post permission. */
    static boolean hasPermission(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true;
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Picks a verse and posts the notification. Reads a whole book file from assets, so
     * this must not run on the main thread.
     */
    static void notifyToday(Context context) {
        if (!hasPermission(context)) return;
        try {
            String ref = new VerseOfTheDay(null, context).getRandomRef(bible, tools, context).reference;
            Verse verse = new Verse(context, ref);
            createChannel(context);

            Intent open = new Intent(context, MainActivity.class)
                    .putExtra("verse_ref", ref)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent contentIntent = PendingIntent.getActivity(context, REQUEST_CODE, open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            String title = verse.proper_book + " " + verse.chapter + ":" + verse.verse;
            String text = scriptureOnly(verse.scripture_text);

            Notification notification = new NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification_verse)
                    .setColor(ContextCompat.getColor(context, R.color.green_700_primary))
                    .setContentTitle(title)
                    .setContentText(text)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true)
                    .setContentIntent(contentIntent)
                    .build();

            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification);
        } catch (SecurityException e) {
            // Permission revoked between the check above and the post.
            Log.w("verse-notify", "not allowed to post the daily verse", e);
        } catch (Exception e) {
            // A broken verse read must not take the process down from a background thread.
            Log.e("verse-notify", "could not post the daily verse", e);
        }
    }

    private static void createChannel(Context context) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription(context.getString(R.string.notification_channel_description));
        manager.createNotificationChannel(channel);
    }

    private static PendingIntent alarmIntent(Context context) {
        Intent intent = new Intent(context, VerseNotificationReceiver.class).setAction(ACTION_NOTIFY);
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** The next {@link #NOTIFY_AT} that is still in the future, as epoch millis. */
    private static long nextTriggerMillis() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime next = now.with(NOTIFY_AT);
        if (!next.isAfter(now)) next = next.plusDays(1);
        return next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    /** Bible.getVerse() hands back the raw asset line, which starts with "chapter:verse: ". */
    private static String scriptureOnly(String line) {
        String[] parts = line.split(":", 3);
        return parts.length == 3 ? parts[2].trim() : line.trim();
    }
}
