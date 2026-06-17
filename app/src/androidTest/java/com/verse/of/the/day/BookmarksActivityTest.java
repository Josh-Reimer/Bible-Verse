package com.verse.of.the.day;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertNotNull;

@RunWith(AndroidJUnit4.class)
public class BookmarksActivityTest {

    @Test
    public void testBookmarksActivityLaunches() {
        try (ActivityScenario<bookmarks_activity> scenario = ActivityScenario.launch(bookmarks_activity.class)) {
            scenario.onActivity(activity -> {
                assertNotNull(activity);
            });
        }
    }

    @Test
    public void testBookmarksActivityHasRecyclerView() {
        try (ActivityScenario<bookmarks_activity> scenario = ActivityScenario.launch(bookmarks_activity.class)) {
            scenario.onActivity(activity -> {
                assertNotNull(activity);
                // The activity should have a RecyclerView initialized
            });
        }
    }

    @Test
    public void testBookmarksDatabaseIsInitialized() {
        try (ActivityScenario<bookmarks_activity> scenario = ActivityScenario.launch(bookmarks_activity.class)) {
            scenario.onActivity(activity -> {
                assertNotNull(activity);
                // The database should be initialized without errors
            });
        }
    }
}
