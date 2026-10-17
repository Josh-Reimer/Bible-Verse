package com.verse.of.the.day;

import android.content.Intent;
import android.content.pm.ApplicationInfo;

import androidx.annotation.NonNull;
import androidx.car.app.CarAppService;
import androidx.car.app.Screen;
import androidx.car.app.Session;
import androidx.car.app.validation.HostValidator;

/**
 * Android Auto entry point (Car App Library). Opens on {@link DailyVerse#today}, the same
 * verse the widget, the notification and {@code MainActivity}'s cold start show.
 *
 * <p>Android Auto only lists apps in its supported categories, and a verse reader is none of
 * them — the manifest declares IOT so the host will bind at all, which means this shows up
 * only with Android Auto's developer setting "Unknown sources" turned on. It is not
 * something Play review would accept, so keep it off release builds headed for the store.
 */
public final class CarVerseService extends CarAppService {

    @NonNull
    @Override
    public HostValidator createHostValidator() {
        if ((getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR;
        }
        return new HostValidator.Builder(getApplicationContext())
                .addAllowedHosts(androidx.car.app.R.array.hosts_allowlist_sample)
                .build();
    }

    @NonNull
    @Override
    public Session onCreateSession() {
        return new Session() {
            @NonNull
            @Override
            public Screen onCreateScreen(@NonNull Intent intent) {
                return new CarVerseScreen(getCarContext(), DailyVerse.today(getCarContext()), true);
            }
        };
    }
}
