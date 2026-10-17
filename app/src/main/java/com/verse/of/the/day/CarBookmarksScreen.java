package com.verse.of.the.day;

import androidx.annotation.NonNull;
import androidx.car.app.CarContext;
import androidx.car.app.Screen;
import androidx.car.app.constraints.ConstraintManager;
import androidx.car.app.model.Action;
import androidx.car.app.model.ItemList;
import androidx.car.app.model.ListTemplate;
import androidx.car.app.model.Row;
import androidx.car.app.model.Template;
import androidx.car.app.versioning.CarAppApiLevels;
import androidx.room.Room;

import java.util.List;

/** The bookmarks list on the car screen; tapping one opens it in a {@link CarVerseScreen}. */
final class CarBookmarksScreen extends Screen {

    // What the host allowed before ConstraintManager existed (car API level 2).
    private static final int LEGACY_LIST_LIMIT = 6;

    CarBookmarksScreen(@NonNull CarContext carContext) {
        super(carContext);
    }

    @NonNull
    @Override
    public Template onGetTemplate() {
        CarContext context = getCarContext();
        // Opened per render and closed straight away, so the car session holds no
        // database open beside MainActivity's.
        bookmark_database db = Room.databaseBuilder(context.getApplicationContext(),
                bookmark_database.class, "bookmarks-database").allowMainThreadQueries().build();
        List<bookmark> bookmarks;
        try {
            bookmarks = db.bookmark_dao().getAllBookmarks();
        } finally {
            db.close();
        }

        int limit = LEGACY_LIST_LIMIT;
        if (context.getCarAppApiLevel() >= CarAppApiLevels.LEVEL_2) {
            limit = context.getCarService(ConstraintManager.class)
                    .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST);
        }

        ItemList.Builder list = new ItemList.Builder()
                .setNoItemsMessage(context.getString(R.string.no_bookmarks_yet));
        for (int i = 0; i < bookmarks.size() && i < limit; i++) {
            bookmark b = bookmarks.get(i);
            String[] parts = b.bible_reference.split(":");
            list.addItem(new Row.Builder()
                    .setTitle(b.book + " " + parts[1] + ":" + parts[2])
                    .addText(VerseWidgetProvider.scriptureOnly(b.scripture_text))
                    .setOnClickListener(() -> getScreenManager()
                            .push(new CarVerseScreen(context, b.bible_reference, false)))
                    .build());
        }

        return new ListTemplate.Builder()
                .setSingleList(list.build())
                .setTitle(context.getString(R.string.bookmarks))
                .setHeaderAction(Action.BACK)
                .build();
    }
}
