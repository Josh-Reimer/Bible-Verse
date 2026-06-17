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
public class SettingsActivityUIAutomatorTest {
    private static final int LAUNCH_TIMEOUT = 5000;
    private static final String APP_PACKAGE = "com.verse.of.the.day";

    private UiDevice device;

    @Before
    public void setUp() throws IOException {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        device.pressHome();
        device.wait(Until.hasObject(By.pkg("com.android.launcher")), LAUNCH_TIMEOUT);
        navigateToSettings();
    }

    private void navigateToSettings() throws IOException {
        device.executeShellCommand("am start -W -n " + APP_PACKAGE + "/.MainActivity");
        device.wait(Until.hasObject(By.pkg(APP_PACKAGE)), LAUNCH_TIMEOUT);

        // Open drawer by swiping
        device.swipe(10, device.getDisplayHeight() / 2,
                     device.getDisplayWidth() / 2, device.getDisplayHeight() / 2,
                     10);
        device.wait(Until.hasObject(By.text("Settings")), 2000);

        // Click Settings
        UiObject2 settingsItem = device.findObject(By.text("Settings"));
        assertNotNull("Settings menu item not found", settingsItem);
        settingsItem.click();

        device.wait(Until.hasObject(By.res(APP_PACKAGE, "translation_dropdown")), LAUNCH_TIMEOUT);
    }

    @Test
    public void testSettingsActivityLaunches() {
        // Verify we're in settings (look for the translation dropdown)
        UiObject2 translationDropdown = device.findObject(By.res(APP_PACKAGE, "translation_dropdown"));
        assertNotNull("Translation dropdown not found", translationDropdown);
    }

    @Test
    public void testThemeModeDropdownExists() {
        // Verify theme dropdown is present
        UiObject2 themeModeDropdown = device.findObject(By.res(APP_PACKAGE, "theme_mode_dropdown"));
        assertNotNull("Theme mode dropdown not found", themeModeDropdown);
    }

    @Test
    public void testTranslationDropdownCanBeOpened() {
        // Find and click translation dropdown
        UiObject2 translationDropdown = device.findObject(By.res(APP_PACKAGE, "translation_dropdown"));
        assertNotNull("Translation dropdown not found", translationDropdown);

        translationDropdown.click();
        device.wait(Until.hasObject(By.text("King James Version")), 2000);

        // Verify dropdown menu items are visible
        UiObject2 kjvOption = device.findObject(By.text("King James Version"));
        assertNotNull("KJV option not found in dropdown", kjvOption);
    }

    @Test
    public void testTranslationDropdownShowsOptions() {
        UiObject2 translationDropdown = device.findObject(By.res(APP_PACKAGE, "translation_dropdown"));
        translationDropdown.click();
        device.wait(Until.hasObject(By.text("American Standard Version")), 2000);

        // Verify all translation options are visible
        UiObject2 asvOption = device.findObject(By.text("American Standard Version"));
        assertNotNull("ASV option not found", asvOption);

        UiObject2 bsbOption = device.findObject(By.text("Berean Standard Bible"));
        assertNotNull("BSB option not found", bsbOption);
    }

    @Test
    public void testThemeModeDropdownCanBeOpened() {
        UiObject2 themeModeDropdown = device.findObject(By.res(APP_PACKAGE, "theme_mode_dropdown"));
        assertNotNull("Theme mode dropdown not found", themeModeDropdown);

        themeModeDropdown.click();
        device.wait(Until.hasObject(By.text("Light")), 2000);

        // Verify theme options are visible
        UiObject2 lightOption = device.findObject(By.text("Light"));
        assertNotNull("Light theme option not found", lightOption);
    }

    @Test
    public void testSelectingTranslationUpdatesDisplay() {
        // Get current translation text
        UiObject2 translationDropdown = device.findObject(By.res(APP_PACKAGE, "translation_dropdown"));
        String initialTranslation = translationDropdown.getText();

        // Open dropdown and select ASV
        translationDropdown.click();
        device.wait(Until.hasObject(By.text("American Standard Version")), 2000);

        UiObject2 asvOption = device.findObject(By.text("American Standard Version"));
        asvOption.click();
        device.wait(Until.gone(By.text("American Standard Version")), 2000);

        // Verify the dropdown now shows ASV (or close enough match)
        UiObject2 updatedDropdown = device.findObject(By.res(APP_PACKAGE, "translation_dropdown"));
        String updatedTranslation = updatedDropdown.getText();
        assertNotEquals("Translation should have changed", initialTranslation, updatedTranslation);
    }

    @Test
    public void testBackFromSettingsReturnsToMain() {
        device.pressBack();
        device.wait(Until.hasObject(By.res(APP_PACKAGE, "verse_text")), LAUNCH_TIMEOUT);

        // Verify we're back in MainActivity
        UiObject2 verseText = device.findObject(By.res(APP_PACKAGE, "verse_text"));
        assertNotNull("Verse text not found - not back in MainActivity", verseText);
    }
}
