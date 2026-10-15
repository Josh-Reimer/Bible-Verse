package com.verse.of.the.day.legacy;

import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.PreferenceFragment;

/** Theme, text size and translation, in the same "settings" preferences file as :app. */
public class SettingsActivity extends LegacyActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null) {
            getFragmentManager().beginTransaction()
                    .replace(android.R.id.content, new SettingsFragment())
                    .commit();
        }
    }

    public static class SettingsFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            getPreferenceManager().setSharedPreferencesName("settings");
            addPreferencesFromResource(R.xml.settings);

            ListPreference translation = (ListPreference) findPreference(Translations.PREF_KEY);
            CharSequence[] names = new CharSequence[Translations.ALL.length];
            CharSequence[] codes = new CharSequence[Translations.ALL.length];
            for (int i = 0; i < Translations.ALL.length; i++) {
                names[i] = Translations.ALL[i].fullName;
                codes[i] = Translations.ALL[i].code;
            }
            translation.setEntries(names);
            translation.setEntryValues(codes);
            // current() first, so a never-touched setting shows the device-language default.
            translation.setValue(Translations.current(getActivity()));
            translation.setOnPreferenceChangeListener((pref, value) -> {
                // Through choose(), as :app's Settings does, so the pick is marked manual.
                Translations.choose(getActivity(), (String) value);
                return true;
            });

            // Changing the look recreates the screens behind this one when they resume;
            // this one has to be told directly.
            findPreference(LegacyActivity.PREF_THEME).setOnPreferenceChangeListener(this::recreateAfter);
            findPreference(LegacyActivity.PREF_TEXT_SIZE).setOnPreferenceChangeListener(this::recreateAfter);
        }

        private boolean recreateAfter(android.preference.Preference pref, Object value) {
            getView().post(() -> getActivity().recreate());
            return true;
        }
    }
}
