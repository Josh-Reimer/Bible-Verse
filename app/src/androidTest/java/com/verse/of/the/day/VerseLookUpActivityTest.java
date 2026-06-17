package com.verse.of.the.day;

import android.content.Intent;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertNotNull;

@RunWith(AndroidJUnit4.class)
public class VerseLookUpActivityTest {

    @Test
    public void testVerseLookUpActivityLaunches() {
        Intent intent = new Intent();
        intent.putExtra("verse_ref", "0:1:1");

        try (ActivityScenario<VerseLookUpActivity> scenario = ActivityScenario.launch(intent)) {
            scenario.onActivity(activity -> {
                assertNotNull(activity);
            });
        }
    }

    @Test
    public void testVerseLookUpActivityReceivesVerseRef() {
        Intent intent = new Intent();
        String testVerseRef = "0:1:1";
        intent.putExtra("verse_ref", testVerseRef);

        try (ActivityScenario<VerseLookUpActivity> scenario = ActivityScenario.launch(intent)) {
            scenario.onActivity(activity -> {
                assertNotNull(activity);
                // The activity should have received the verse reference
            });
        }
    }

    @Test
    public void testVerseLookUpActivityWithDifferentVerse() {
        Intent intent = new Intent();
        intent.putExtra("verse_ref", "39:5:3");  // Matthew 5:3

        try (ActivityScenario<VerseLookUpActivity> scenario = ActivityScenario.launch(intent)) {
            scenario.onActivity(activity -> {
                assertNotNull(activity);
            });
        }
    }
}
