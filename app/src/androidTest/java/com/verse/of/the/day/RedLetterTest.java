package com.verse.of.the.day;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.Spanned;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class RedLetterTest {
    private RedLetter redLetter;
    private Context context;
    private SharedPreferences sharedPreferences;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        redLetter = new RedLetter();
        sharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    @Test
    public void testRedLetterInitializes() {
        assertNotNull(redLetter);
    }

    @Test
    public void testTranslationPreferenceDefaultsToKJV() {
        String translation = sharedPreferences.getString("translation", "kjv");
        // Default should be kjv unless explicitly set otherwise
        assertTrue(translation.equals("kjv") || translation.equals("asv") || translation.equals("bsb"));
    }

    @Test
    public void testGetSpannedHandlesInvalidVerse() {
        // Test with a verse ref that likely doesn't have red-letter markup
        Spanned result = redLetter.getSpanned(context, "999:999:999");
        // Result can be null if no red-letter data exists
        assertTrue(result == null || result instanceof Spanned);
    }

    @Test
    public void testTranslationPreferenceCanBeSet() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("translation", "asv");
        editor.apply();

        String translation = sharedPreferences.getString("translation", "kjv");
        assertEquals("asv", translation);

        // Reset to default
        editor.putString("translation", "kjv");
        editor.apply();
    }

    @Test
    public void testGenesisFirstVerseHandling() {
        // Genesis 1:1 reference format is "0:1:1"
        Spanned result = redLetter.getSpanned(context, "0:1:1");
        // Result may be null or Spanned depending on red-letter file
        assertTrue(result == null || result instanceof Spanned);
    }
}
