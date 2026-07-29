package com.verse.of.the.day;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Fires the daily verse notification and re-arms tomorrow's alarm.
 *
 * <p>Also re-arms after a reboot or an app update, which both drop pending alarms.
 * Building the notification reads a book file from assets, so the work happens on a
 * private executor with {@code goAsync()} holding the broadcast open (same pattern as
 * {@link VerseWidgetProvider}).
 */
public class VerseNotificationReceiver extends BroadcastReceiver {

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        final Context appContext = context.getApplicationContext();

        if (VerseNotifier.ACTION_NOTIFY.equals(action)) {
            // A stale alarm can outlive the setting being turned off (cancel() misses an
            // alarm already in flight), so re-check before posting anything.
            if (!VerseNotifier.isEnabled(appContext)) return;
            final PendingResult pending = goAsync();
            executor.execute(() -> {
                try {
                    VerseNotifier.notifyToday(appContext);
                    VerseNotifier.schedule(appContext);
                } finally {
                    pending.finish();
                }
            });
            return;
        }

        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            VerseNotifier.scheduleIfEnabled(appContext);
        }
    }
}
