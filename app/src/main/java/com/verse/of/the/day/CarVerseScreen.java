package com.verse.of.the.day;

import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.car.app.CarContext;
import androidx.car.app.Screen;
import androidx.car.app.model.Action;
import androidx.car.app.model.ActionStrip;
import androidx.car.app.model.MessageTemplate;
import androidx.car.app.model.Template;

/**
 * One verse on the car screen. The root instance (today's verse) carries the dice, a way
 * back to today's verse and the bookmarks list; one pushed from the bookmarks list just
 * shows its verse with a back arrow.
 *
 * <p>Plain text only: the host draws {@code MessageTemplate}'s body itself and drops colour
 * spans, so there is no red letter here, as in the notification.
 */
final class CarVerseScreen extends Screen {

    private static final Bible bible = new Bible();
    private static final Tools tools = new Tools();

    private final boolean root;
    private String ref;

    CarVerseScreen(@NonNull CarContext carContext, String ref, boolean root) {
        super(carContext);
        this.ref = ref;
        this.root = root;
    }

    @NonNull
    @Override
    public Template onGetTemplate() {
        CarContext context = getCarContext();
        // Rebuilt on every render so a translation changed on the phone shows on the next one.
        Verse verse = new Verse(context, ref);
        String title = verse.proper_book + " " + verse.chapter + ":" + verse.verse;
        SharedPreferences sp = context.getSharedPreferences("settings", CarContext.MODE_PRIVATE);
        if (sp.getBoolean("show_translation_info", false)) {
            title += " · " + Translations.currentEntry(context).label;
        }

        MessageTemplate.Builder builder =
                new MessageTemplate.Builder(VerseWidgetProvider.scriptureOnly(verse.scripture_text))
                        .setTitle(title)
                        .setHeaderAction(root ? Action.APP_ICON : Action.BACK);

        if (root) {
            // Like MainActivity's dice: a random verse for this screen, the day's verse untouched.
            builder.addAction(new Action.Builder()
                    .setTitle(context.getString(R.string.car_new_verse))
                    .setOnClickListener(() -> {
                        ref = new VerseOfTheDay(null, context).getRandomRef(bible, tools, context).reference;
                        invalidate();
                    })
                    .build());
            builder.addAction(new Action.Builder()
                    .setTitle(context.getString(R.string.car_today))
                    .setOnClickListener(() -> {
                        ref = DailyVerse.today(context);
                        invalidate();
                    })
                    .build());
            builder.setActionStrip(new ActionStrip.Builder()
                    .addAction(new Action.Builder()
                            .setTitle(context.getString(R.string.bookmarks))
                            .setOnClickListener(() ->
                                    getScreenManager().push(new CarBookmarksScreen(context)))
                            .build())
                    .build());
        }
        return builder.build();
    }
}
