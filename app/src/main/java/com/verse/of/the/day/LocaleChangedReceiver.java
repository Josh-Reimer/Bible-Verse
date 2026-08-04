package com.verse.of.the.day;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Moves the translation to match the device language when the system language changes.
 *
 * <p>{@code ACTION_LOCALE_CHANGED} is a protected system broadcast, so the receiver can
 * stay {@code exported="false"} and still be reached — the same arrangement
 * {@link VerseNotificationReceiver} uses for {@code BOOT_COMPLETED}. It is also one of
 * the implicit broadcasts still delivered to manifest receivers under the Android 8+
 * background restrictions, so no runtime registration is needed.
 *
 * <p>The work is a couple of SharedPreferences reads, so it runs inline; only the widget
 * redraw is dispatched, and {@link VerseWidgetProvider#refresh} already moves that to its
 * own executor.
 */
public class LocaleChangedReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_LOCALE_CHANGED.equals(intent.getAction())) return;

        Context appContext = context.getApplicationContext();
        if (Translations.syncWithDeviceLanguage(appContext)) {
            VerseWidgetProvider.refresh(appContext);
        }
    }
}
