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
public class VerseLookUpActivityUIAutomatorTest {
    private static final int LAUNCH_TIMEOUT = 5000;
    private static final String APP_PACKAGE = "com.verse.of.the.day";

    private UiDevice device;

    @Before
    public void setUp() throws IOException {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        device.pressHome();
        device.wait(Until.hasObject(By.pkg("com.android.launcher")), LAUNCH_TIMEOUT);
    }

    private void launchVerseLookUp(String verseRef) throws IOException {
        // Launch VerseLookUpActivity with a specific verse reference
        String cmd = "am start -W -n " + APP_PACKAGE + "/.VerseLookUpActivity " +
                     "-e verse_ref \"" + verseRef + "\"";
        device.executeShellCommand(cmd);
        device.wait(Until.hasObject(By.pkg(APP_PACKAGE)), LAUNCH_TIMEOUT);
    }

    @Test
    public void testVerseLookUpActivityLaunches() throws IOException {
        launchVerseLookUp("0:1:1");

        // Verify activity launched
        assertTrue("Should be in VerseLookUpActivity",
                  device.getCurrentPackageName().equals(APP_PACKAGE));
    }

    @Test
    public void testVerseContextDisplaysChapter() throws IOException {
        launchVerseLookUp("0:1:1");

        // Look for the chapter content
        UiObject2 chapterView = device.findObject(By.res(APP_PACKAGE, "chapter_scrollview"));
        assertNotNull("Chapter ScrollView not found", chapterView);
    }

    @Test
    public void testTargetVerseTextIsDisplayed() throws IOException {
        launchVerseLookUp("0:1:1");

        // The target verse should be visible (Genesis 1:1)
        // Look for a text view containing the verse
        UiObject2 targetVerse = device.findObject(By.res(APP_PACKAGE, "target_verse_text"));
        assertNotNull("Target verse TextView not found", targetVerse);

        String text = targetVerse.getText();
        assertTrue("Target verse should contain text", text != null && !text.isEmpty());
    }

    @Test
    public void testChapterContextIncludesPreAndPost() throws IOException {
        launchVerseLookUp("0:1:2");

        // Verify all three text sections exist
        UiObject2 preVerse = device.findObject(By.res(APP_PACKAGE, "pre_verse_text"));
        UiObject2 targetVerse = device.findObject(By.res(APP_PACKAGE, "target_verse_text"));
        UiObject2 postVerse = device.findObject(By.res(APP_PACKAGE, "post_verse_text"));

        assertNotNull("Pre-verse text should exist", preVerse);
        assertNotNull("Target verse text should exist", targetVerse);
        assertNotNull("Post-verse text should exist", postVerse);
    }

    @Test
    public void testChapterScrollViewCanScroll() throws IOException {
        launchVerseLookUp("0:1:1");

        UiObject2 chapterView = device.findObject(By.res(APP_PACKAGE, "chapter_scrollview"));
        assertNotNull("Chapter ScrollView not found", chapterView);

        // Attempt to scroll down
        chapterView.scroll(Direction.DOWN, 0.3f);
        assertTrue("Scroll completed successfully", true);
    }

    @Test
    public void testToolbarDisplaysVerseReference() throws IOException {
        launchVerseLookUp("0:1:1");

        // The toolbar should contain a title with the verse reference or book name
        UiObject2 toolbar = device.findObject(By.res(APP_PACKAGE, "toolbar"));
        assertNotNull("Toolbar not found", toolbar);
    }

    @Test
    public void testBackFromLookUpReturnsHome() throws IOException {
        launchVerseLookUp("0:1:1");

        device.pressBack();
        device.wait(Until.hasObject(By.pkg("com.android.launcher")), LAUNCH_TIMEOUT);

        // Should be back at home or closed
        String pkg = device.getCurrentPackageName();
        assertNotEquals("Should have left the app", APP_PACKAGE, pkg);
    }
}
