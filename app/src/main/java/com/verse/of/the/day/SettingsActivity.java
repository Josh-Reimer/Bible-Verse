package com.verse.of.the.day;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.text.format.DateFormat;
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

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.NavUtils;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;

import java.time.LocalTime;
import java.util.Calendar;

public class SettingsActivity extends AppCompatActivity {

    private static final String TIME_PICKER_TAG = "daily_verse_time_picker";

    public Tools tools = new Tools();

    /** Material's disabled-content opacity, for the time row while the notification is off. */
    private static final float DISABLED_ALPHA = 0.38f;

    private SwitchMaterial dailyVerseNotificationSwitch;
    private TextView notificationTimeTitle;
    private TextView notificationTimeValue;
    private ActivityResultLauncher<String> notificationPermissionLauncher;
    private boolean suppressNotificationListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Must be registered before the activity is started, so it stays out of the
        // listener wiring further down.
        notificationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), this::onNotificationPermissionResult);
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
        String[] themeLabels = {getString(R.string.theme_light), getString(R.string.theme_dark),
                getString(R.string.theme_system)};
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

        themeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            boolean firstCall = true;
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (firstCall) { firstCall = false; return; }
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
        String[] translations = new String[Translations.ALL.length];
        String[] translationFullNames = new String[Translations.ALL.length];
        for (int i = 0; i < Translations.ALL.length; i++) {
            translations[i] = Translations.ALL[i].label;
            translationFullNames[i] = Translations.ALL[i].fullName;
        }
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

        String currentTranslation = Translations.current(this);
        int translationIndex = 0;
        for (int i = 0; i < Translations.ALL.length; i++) {
            if (Translations.ALL[i].code.equals(currentTranslation)) { translationIndex = i; break; }
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

                Translations.Entry entry = Translations.ALL[position];
                String selected = entry.code;

                if (!entry.approximateRedLetter) {
                    Translations.choose(SettingsActivity.this, selected);
                    committedIndex = position;
                    VerseWidgetProvider.refresh(SettingsActivity.this);
                    return;
                }

                AlertDialog dialog = new AlertDialog.Builder(SettingsActivity.this)
                        .setTitle(getString(R.string.red_letter_accuracy_title, entry.label))
                        .setMessage(getString(R.string.red_letter_accuracy_message, entry.label))
                        .setCancelable(false)
                        .setPositiveButton(android.R.string.ok, (d, which) -> {
                            Translations.choose(SettingsActivity.this, selected);
                            committedIndex = position;
                            VerseWidgetProvider.refresh(SettingsActivity.this);
                        })
                        .setNegativeButton(R.string.cancel, (d, which) -> translationSpinner.setSelection(committedIndex))
                        .create();
                dialog.show();
                // colorPrimary is repurposed app-wide to match the surface color (so the
                // toolbar isn't tinted), which would otherwise make these buttons invisible
                // against the dialog's surface-colored background — force a visible color.
                int buttonColor = ContextCompat.getColor(SettingsActivity.this, R.color.app_on_surface);
                dialog.getButton(DialogInterface.BUTTON_POSITIVE).setTextColor(buttonColor);
                dialog.getButton(DialogInterface.BUTTON_NEGATIVE).setTextColor(buttonColor);
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // Show Translation Info switch
        SwitchMaterial showTranslationInfoSwitch = findViewById(R.id.showTranslationInfoSwitch);
        boolean showTranslationInfo = sp.getBoolean("show_translation_info", false);
        showTranslationInfoSwitch.setChecked(showTranslationInfo);
        showTranslationInfoSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            spEditor.putBoolean("show_translation_info", isChecked).apply();
            VerseWidgetProvider.refresh(SettingsActivity.this);
        });

        // Time of day for that notification — its own row, found before the switch below
        // because syncNotificationSwitch() greys this row out to match.
        notificationTimeTitle = findViewById(R.id.notificationTimeTitle);
        notificationTimeValue = findViewById(R.id.notificationTimeValue);
        updateNotificationTimeValue();
        notificationTimeValue.setOnClickListener(v -> showNotificationTimePicker());
        // A picker showing across a recreation (a theme change, a rotation) is rebuilt by
        // the FragmentManager without its listener, so re-attach one to the survivor.
        MaterialTimePicker restored =
                (MaterialTimePicker) getSupportFragmentManager().findFragmentByTag(TIME_PICKER_TAG);
        if (restored != null) attachTimePickerListener(restored);

        // Privacy policy — opens the published page in a browser. Unlike every other row
        // here it writes no preference, so it needs no state to sync.
        findViewById(R.id.privacyPolicyValue).setOnClickListener(v -> openPrivacyPolicy());

        // Daily Verse Notification switch — opt-in, and the notification permission is
        // only ever requested here, when the user turns it on themselves.
        dailyVerseNotificationSwitch = findViewById(R.id.dailyVerseNotificationSwitch);
        syncNotificationSwitch();
        dailyVerseNotificationSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (suppressNotificationListener) return;
            if (!isChecked) {
                VerseNotifier.setEnabled(SettingsActivity.this, false);
            } else if (VerseNotifier.hasPermission(SettingsActivity.this)) {
                VerseNotifier.setEnabled(SettingsActivity.this, true);
            } else {
                // Nothing is persisted until the permission comes back granted, so the
                // time row stays as it is until onNotificationPermissionResult.
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
                return;
            }
            syncNotificationTimeRow();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // The permission can be revoked from system settings while we were away.
        if (dailyVerseNotificationSwitch != null) syncNotificationSwitch();
    }

    /**
     * Points the switch at the real state, turning the setting back off if the permission
     * behind it has gone away — a switch left on would promise a notification that the OS
     * would never show.
     */
    private void syncNotificationSwitch() {
        if (VerseNotifier.isEnabled(this) && !VerseNotifier.hasPermission(this)) {
            VerseNotifier.setEnabled(this, false);
        }
        setNotificationSwitchChecked(VerseNotifier.isEnabled(this));
    }

    private void showNotificationTimePicker() {
        LocalTime current = VerseNotifier.notifyAt(this);
        MaterialTimePicker picker = new MaterialTimePicker.Builder()
                // Honour the reader's 12/24-hour system setting rather than picking one.
                .setTimeFormat(DateFormat.is24HourFormat(this) ? TimeFormat.CLOCK_24H : TimeFormat.CLOCK_12H)
                .setHour(current.getHour())
                .setMinute(current.getMinute())
                .setTitleText(R.string.daily_verse_notification_time_picker_title)
                .setTheme(R.style.ThemeOverlay_VerseApp_TimePicker)
                .build();
        attachTimePickerListener(picker);
        picker.show(getSupportFragmentManager(), TIME_PICKER_TAG);
    }

    private void attachTimePickerListener(MaterialTimePicker picker) {
        picker.addOnPositiveButtonClickListener(v -> {
            VerseNotifier.setNotifyAt(this, LocalTime.of(picker.getHour(), picker.getMinute()));
            updateNotificationTimeValue();
        });
    }

    /**
     * Writes the chosen time into the row's value, formatted the way the reader's locale
     * and 12/24-hour setting want it — 8:00 AM, 08:00, 上午8:00.
     */
    private void updateNotificationTimeValue() {
        LocalTime time = VerseNotifier.notifyAt(this);
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, time.getHour());
        calendar.set(Calendar.MINUTE, time.getMinute());
        notificationTimeValue.setText(DateFormat.getTimeFormat(this).format(calendar.getTime()));
    }

    /**
     * Opens the published privacy policy in whatever handles web links. The URL is served
     * by GitHub Pages from the {@code privacypolicy-gpage} branch — see CLAUDE.md, which
     * also explains why that branch must not be deleted.
     *
     * <p>This starts the intent and catches the failure rather than asking
     * {@code resolveActivity} first: package visibility on API 30+ hides browsers from
     * that query unless the manifest declares a {@code <queries>} element, so the check
     * would report "no browser" on a device that has one.
     */
    private void openPrivacyPolicy() {
        Intent browse = new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.privacy_policy_url)));
        try {
            startActivity(browse);
        } catch (android.content.ActivityNotFoundException e) {
            Snackbar.make(findViewById(android.R.id.content),
                    R.string.privacy_policy_no_browser, Snackbar.LENGTH_LONG).show();
        }
    }

    /**
     * Greys the time row out while the notification is off — it still says what time the
     * verse would arrive, but there is nothing to set until the switch is on. Disabling
     * the value is what actually stops the tap; the alpha is what shows it.
     */
    private void syncNotificationTimeRow() {
        boolean enabled = VerseNotifier.isEnabled(this);
        float alpha = enabled ? 1f : DISABLED_ALPHA;
        notificationTimeTitle.setAlpha(alpha);
        notificationTimeValue.setAlpha(alpha);
        notificationTimeValue.setEnabled(enabled);
    }

    /** Moves the switch without the listener treating it as a user toggle. */
    private void setNotificationSwitchChecked(boolean checked) {
        suppressNotificationListener = true;
        dailyVerseNotificationSwitch.setChecked(checked);
        suppressNotificationListener = false;
        syncNotificationTimeRow();
    }

    private void onNotificationPermissionResult(boolean granted) {
        if (granted) {
            VerseNotifier.setEnabled(this, true);
            syncNotificationTimeRow();
            return;
        }
        setNotificationSwitchChecked(false);
        boolean permanentlyDenied = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS);
        if (permanentlyDenied) {
            showNotificationSettingsDialog();
        } else {
            Snackbar.make(findViewById(android.R.id.content),
                    R.string.notification_permission_denied, Snackbar.LENGTH_LONG).show();
        }
    }

    /** Offered when the system will no longer show the permission prompt itself. */
    private void showNotificationSettingsDialog() {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.notification_permission_title)
                .setMessage(R.string.notification_permission_message)
                .setPositiveButton(R.string.open_settings, (d, which) -> {
                    Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
                    if (intent.resolveActivity(getPackageManager()) == null) {
                        intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                .setData(Uri.fromParts("package", getPackageName(), null));
                    }
                    startActivity(intent);
                })
                .setNegativeButton(R.string.cancel, null)
                .create();
        dialog.show();
        // Same reason as the BSB dialog above: colorPrimary matches the surface colour.
        int buttonColor = ContextCompat.getColor(this, R.color.app_on_surface);
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setTextColor(buttonColor);
        dialog.getButton(DialogInterface.BUTTON_NEGATIVE).setTextColor(buttonColor);
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
