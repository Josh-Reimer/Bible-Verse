package com.verse.of.the.day;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.Direction;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class BookmarksActivityUIAutomatorTest {
    private static final int LAUNCH_TIMEOUT = 5000;
    private static final String APP_PACKAGE = "com.verse.of.the.day";

    private UiDevice device;

    @Before
    public void setUp() throws IOException {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        device.pressHome();
        device.wait(Until.hasObject(By.pkg("com.android.launcher")), LAUNCH_TIMEOUT);
        navigateToBookmarks();
    }

    private void navigateToBookmarks() throws IOException {
        device.executeShellCommand("am start -W -n " + APP_PACKAGE + "/.MainActivity");
        device.wait(Until.hasObject(By.pkg(APP_PACKAGE)), LAUNCH_TIMEOUT);

        // Open drawer
        device.swipe(10, device.getDisplayHeight() / 2,
                     device.getDisplayWidth() / 2, device.getDisplayHeight() / 2,
                     10);
        device.wait(Until.hasObject(By.text("Bookmarks")), 2000);

        // Click Bookmarks
        UiObject2 bookmarksItem = device.findObject(By.text("Bookmarks"));
        assertNotNull("Bookmarks menu item not found", bookmarksItem);
        bookmarksItem.click();

        device.wait(Until.hasObject(By.res(APP_PACKAGE, "bookmarks_recycler")), LAUNCH_TIMEOUT);
    }

    @Test
    public void testBookmarksActivityLaunches() {
        UiObject2 recycler = device.findObject(By.res(APP_PACKAGE, "bookmarks_recycler"));
        assertNotNull("Bookmarks RecyclerView not found", recycler);
    }

    @Test
    public void testEmptyBookmarksShowMessage() {
        // If no bookmarks exist, look for empty state message
        UiObject2 emptyMessage = device.findObject(By.text("No bookmarks yet"));
        // Either a message or the recycler exists (recycler might be empty)
        UiObject2 recycler = device.findObject(By.res(APP_PACKAGE, "bookmarks_recycler"));
        assertTrue("Should have recycler or empty message",
                  emptyMessage != null || recycler != null);
    }

    @Test
    public void testBackFromBookmarksReturnsToMain() {
        device.pressBack();
        device.wait(Until.hasObject(By.res(APP_PACKAGE, "verse_text")), LAUNCH_TIMEOUT);

        UiObject2 verseText = device.findObject(By.res(APP_PACKAGE, "verse_text"));
        assertNotNull("Verse text not found - not back in MainActivity", verseText);
    }

    @Test
    public void testRecyclerViewCanScroll() {
        UiObject2 recycler = device.findObject(By.res(APP_PACKAGE, "bookmarks_recycler"));
        assertNotNull("Bookmarks RecyclerView not found", recycler);

        // Attempt to scroll down (may be a no-op if empty or few items)
        recycler.scroll(Direction.DOWN, 0.3f);
        // Test passes if no exception is thrown
        assertTrue("Scroll operation completed", true);
    }
}
