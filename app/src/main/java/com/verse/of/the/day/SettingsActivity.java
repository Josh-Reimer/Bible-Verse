package com.verse.of.the.day;

import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.widget.Spinner;
import android.widget.TextView;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.material.appbar.MaterialToolbar;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import android.view.MenuItem;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.NavUtils;

public class SettingsActivity extends AppCompatActivity {

    public Tools tools = new Tools();
    private AlertDialog bsbWarningDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_activity);
        MaterialToolbar toolbar = findViewById(R.id.topBar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        SharedPreferences sp = getSharedPreferences("settings", MODE_PRIVATE);
        SharedPreferences.Editor spEditor = sp.edit();

        ActionBar ab = this.getSupportActionBar();
        if (ab != null) {
            ab.setDisplayHomeAsUpEnabled(true);
        }

        // Theme spinner
        String[] themeLabels = {"Light", "Dark", "Follow System"};
        String[] themeValues = {"light", "dark", "system"};
        Spinner themeSpinner = findViewById(R.id.themeSpinner);
        ArrayAdapter<String> themeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, themeLabels);
        themeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        themeSpinner.setAdapter(themeAdapter);

        String currentTheme = sp.getString("theme_mode", "system");
        int themeIndex = 2; // default to system
        for (int i = 0; i < themeValues.length; i++) {
            if (themeValues[i].equals(currentTheme)) { themeIndex = i; break; }
        }
        themeSpinner.setSelection(themeIndex);
        final int initialThemeIndex = themeIndex;

        themeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            int committedIndex = initialThemeIndex;
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == committedIndex) return;
                committedIndex = position;
                String selected = themeValues[position];
                spEditor.putString("theme_mode", selected).apply();
                switch (selected) {
                    case "dark":
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                        break;
                    case "light":
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                        break;
                    default:
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                        break;
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // Translation spinner
        String[] translations = {"KJV", "ASV", "BSB"};
        String[] translationFullNames = {
                "KJV — King James Version",
                "ASV — American Standard Version",
                "BSB — Berean Standard Bible"
        };
        Spinner translationSpinner = findViewById(R.id.translationSpinner);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, translations) {
            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                TextView itemView = (TextView) super.getDropDownView(position, convertView, parent);
                itemView.setText(translationFullNames[position]);
                return itemView;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        translationSpinner.setAdapter(adapter);

        String currentTranslation = sp.getString("translation", "kjv");
        int translationIndex = 0;
        for (int i = 0; i < translations.length; i++) {
            if (translations[i].toLowerCase().equals(currentTranslation)) { translationIndex = i; break; }
        }
        translationSpinner.setSelection(translationIndex);
        final int initialTranslationIndex = translationIndex;

        translationSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            int committedIndex = initialTranslationIndex;

            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                // Selecting the already-committed translation is a no-op. This guards both
                // the initial programmatic selection in onCreate and any spurious repeat
                // callbacks Spinner can fire during activity recreation (e.g. a theme change),
                // which would otherwise re-show the BSB warning dialog.
                if (position == committedIndex) return;

                String selected = translations[position].toLowerCase();

                if (!selected.equals("bsb")) {
                    spEditor.putString("translation", selected).apply();
                    committedIndex = position;
                    return;
                }

                bsbWarningDialog = new AlertDialog.Builder(SettingsActivity.this)
                        .setTitle("BSB Red-Letter Accuracy")
                        .setMessage("Red-letter highlighting in BSB is algorithmically generated and may occasionally be inaccurate.")
                        .setCancelable(false)
                        .setPositiveButton("OK", (d, which) -> {
                            spEditor.putString("translation", selected).apply();
                            committedIndex = position;
                        })
                        .setNegativeButton("Cancel", (d, which) -> translationSpinner.setSelection(committedIndex))
                        .create();
                bsbWarningDialog.show();
                // colorPrimary is repurposed app-wide to match colorSurface in light theme (so
                // the toolbar isn't tinted), making default AlertDialog button text invisible
                // there; dark theme uses a distinct green colorPrimary so default text is
                // already visible, but forcing the color here keeps both themes consistent.
                int buttonColor = ContextCompat.getColor(SettingsActivity.this, R.color.app_on_surface);
                bsbWarningDialog.getButton(DialogInterface.BUTTON_POSITIVE).setTextColor(buttonColor);
                bsbWarningDialog.getButton(DialogInterface.BUTTON_NEGATIVE).setTextColor(buttonColor);
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    @Override
    protected void onDestroy() {
        if (bsbWarningDialog != null && bsbWarningDialog.isShowing()) {
            bsbWarningDialog.dismiss();
        }
        super.onDestroy();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            NavUtils.navigateUpFromSameTask(this);
        }
        return super.onOptionsItemSelected(item);
    }
}
