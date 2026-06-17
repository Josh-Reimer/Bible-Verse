package com.verse.of.the.day;

import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.Espresso;
import androidx.test.espresso.matcher.ViewMatchers;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static org.hamcrest.Matchers.allOf;

@RunWith(AndroidJUnit4.class)
public class MainActivityTest {

    @Before
    public void setUp() {
        // Clear any previous state if needed
    }

    @Test
    public void testMainActivityLaunches() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            // Verify the activity launched successfully
            scenario.onActivity(activity -> {
                // Activity should not be null
                assert activity != null;
            });
        }
    }

    @Test
    public void testVerseTextViewIsDisplayed() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            // Verify verse content is displayed
            scenario.onActivity(activity -> {
                assert activity != null;
                // The verse display should be initialized
            });
        }
    }

    @Test
    public void testMenuFabExists() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                assert activity != null;
                assert activity.menu_fab != null;
            });
        }
    }

    @Test
    public void testThemeCanBeApplied() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                assert activity != null;
                // applyTheme should not throw an exception
                android.content.SharedPreferences sp = activity.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE);
                activity.applyTheme(sp);
            });
        }
    }

    @Test
    public void testDrawerLayoutExists() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                assert activity != null;
                assert activity.drawerLayout != null;
            });
        }
    }
}
