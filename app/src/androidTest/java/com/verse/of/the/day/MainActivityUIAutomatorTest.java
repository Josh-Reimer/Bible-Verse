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
public class MainActivityUIAutomatorTest {
    private static final int LAUNCH_TIMEOUT = 5000;
    private static final String APP_PACKAGE = "com.verse.of.the.day";

    private UiDevice device;

    @Before
    public void setUp() throws IOException {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        device.pressHome();
        device.wait(Until.hasObject(By.pkg("com.android.launcher")), LAUNCH_TIMEOUT);
        launchApp();
    }

    private void launchApp() throws IOException {
        device.executeShellCommand("am start -W -n " + APP_PACKAGE + "/.MainActivity");
        device.wait(Until.hasObject(By.pkg(APP_PACKAGE)), LAUNCH_TIMEOUT);
    }

    @Test
    public void testMainActivityLaunches() {
        // Verify the app package is in focus
        assertTrue("App did not launch", device.getCurrentPackageName().equals(APP_PACKAGE));
    }

    @Test
    public void testVerseTextIsDisplayed() {
        // Look for the verse text view (check if any text is displayed)
        UiObject2 verseText = device.findObject(By.res(APP_PACKAGE, "verse_text"));
        assertNotNull("Verse text view not found", verseText);

        String text = verseText.getText();
        assertTrue("Verse text should not be empty", text != null && !text.isEmpty());
    }

    @Test
    public void testMenuFabIsDisplayed() {
        // Menu FAB should be visible by default
        UiObject2 menuFab = device.findObject(By.res(APP_PACKAGE, "menu_fab"));
        assertNotNull("Menu FAB not found", menuFab);
    }

    @Test
    public void testMenuFabOpensActionFabs() {
        // Click the menu FAB to open action FABs
        UiObject2 menuFab = device.findObject(By.res(APP_PACKAGE, "menu_fab"));
        assertNotNull("Menu FAB not found", menuFab);

        menuFab.click();
        device.wait(Until.hasObject(By.res(APP_PACKAGE, "fab_bookmark")), 2000);

        // Verify action FABs are now visible
        UiObject2 bookmarkFab = device.findObject(By.res(APP_PACKAGE, "fab_bookmark"));
        assertNotNull("Bookmark FAB not found after menu click", bookmarkFab);
    }

    @Test
    public void testShareFabIsAccessible() {
        // Open menu and verify share FAB
        UiObject2 menuFab = device.findObject(By.res(APP_PACKAGE, "menu_fab"));
        menuFab.click();
        device.wait(Until.hasObject(By.res(APP_PACKAGE, "fab_share")), 2000);

        UiObject2 shareFab = device.findObject(By.res(APP_PACKAGE, "fab_share"));
        assertNotNull("Share FAB not found", shareFab);
    }

    @Test
    public void testDrawerCanBeOpened() {
        // Swipe from left edge to open drawer
        device.swipe(10, device.getDisplayHeight() / 2,
                     device.getDisplayWidth() / 2, device.getDisplayHeight() / 2,
                     10);

        device.wait(Until.hasObject(By.res(APP_PACKAGE, "nav_view")), 2000);
        UiObject2 navView = device.findObject(By.res(APP_PACKAGE, "nav_view"));
        assertNotNull("Navigation drawer not found", navView);
    }

    @Test
    public void testSettingsMenuItemExists() {
        // Open drawer
        device.swipe(10, device.getDisplayHeight() / 2,
                     device.getDisplayWidth() / 2, device.getDisplayHeight() / 2,
                     10);
        device.wait(Until.hasObject(By.res(APP_PACKAGE, "nav_view")), 2000);

        // Look for settings menu item (typically has text "Settings")
        UiObject2 settingsItem = device.findObject(By.text("Settings"));
        assertNotNull("Settings menu item not found", settingsItem);
    }

    @Test
    public void testBookmarksMenuItemExists() {
        // Open drawer
        device.swipe(10, device.getDisplayHeight() / 2,
                     device.getDisplayWidth() / 2, device.getDisplayHeight() / 2,
                     10);
        device.wait(Until.hasObject(By.res(APP_PACKAGE, "nav_view")), 2000);

        // Look for bookmarks menu item
        UiObject2 bookmarksItem = device.findObject(By.text("Bookmarks"));
        assertNotNull("Bookmarks menu item not found", bookmarksItem);
    }

    @Test
    public void testBackPressClosesDrawer() {
        // Open drawer
        device.swipe(10, device.getDisplayHeight() / 2,
                     device.getDisplayWidth() / 2, device.getDisplayHeight() / 2,
                     10);
        device.wait(Until.hasObject(By.res(APP_PACKAGE, "nav_view")), 2000);

        // Verify nav view is displayed
        UiObject2 navView = device.findObject(By.res(APP_PACKAGE, "nav_view"));
        assertNotNull("Drawer should be open", navView);

        // Press back
        device.pressBack();
        device.wait(Until.gone(By.res(APP_PACKAGE, "nav_view")), 2000);

        // Nav view should no longer be found
        navView = device.findObject(By.res(APP_PACKAGE, "nav_view"));
        assertNull("Drawer should be closed after back press", navView);
    }
}
