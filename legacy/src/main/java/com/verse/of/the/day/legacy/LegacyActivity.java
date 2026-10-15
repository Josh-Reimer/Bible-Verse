package com.verse.of.the.day.legacy;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.MenuItem;

/**
 * Applies the reader's theme and text size, and recreates the screen when either changed
 * under Settings — the framework has no AppCompatDelegate to do it.
 */
abstract class LegacyActivity extends Activity {

    static final String PREF_THEME = "theme_mode";
    static final String PREF_TEXT_SIZE = "text_size";

    private String appliedLook;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        appliedLook = look();
        // Holo dark by default: it is Android 4's own default, and the easier read at night.
        setTheme(lightTheme()
                ? android.R.style.Theme_Holo_Light_DarkActionBar
                : android.R.style.Theme_Holo);
        super.onCreate(savedInstanceState);
        if (getActionBar() != null && !(this instanceof MainActivity)) {
            getActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!appliedLook.equals(look())) {
            recreate();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    SharedPreferences settings() {
        return getSharedPreferences("settings", MODE_PRIVATE);
    }

    boolean lightTheme() {
        return "light".equals(settings().getString(PREF_THEME, "dark"));
    }

    /** Verse text size in sp, from the Settings choice. */
    float verseTextSize() {
        switch (settings().getString(PREF_TEXT_SIZE, "normal")) {
            case "small": return 20;
            case "large": return 32;
            case "huge": return 40;
            default: return 26;
        }
    }

    void openChapter(String ref) {
        startActivity(new Intent(this, ChapterActivity.class).putExtra(ChapterActivity.EXTRA_REF, ref));
    }

    private String look() {
        SharedPreferences sp = settings();
        return sp.getString(PREF_THEME, "dark") + "/" + sp.getString(PREF_TEXT_SIZE, "normal");
    }
}
