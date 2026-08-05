package com.verse.of.the.day;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;

/**
 * The set of bundled translations, and the single place the current one is resolved.
 *
 * Every asset read goes through {@link Tools#getFile}, which asks {@link #current}
 * for the folder to read from. On a device that has never had the setting touched,
 * that answer comes from the device language — Spanish gets the RV1909, Mandarin the
 * CUV, everything else the KJV — and is written to preferences the first time it is
 * asked for, so the choice stays put afterwards even if the user later changes the
 * system language.
 */
public class Translations {

    public static final String PREF_KEY = "translation";
    /** The device language the stored translation was chosen for; see {@link #syncWithDeviceLanguage}. */
    public static final String PREF_LANGUAGE = "translation_language";
    /** Set once the reader picks a translation themselves; see {@link #choose}. */
    public static final String PREF_MANUAL = "translation_manual";
    public static final String DEFAULT = "kjv";

    public static class Entry {
        public final String code;      // asset folder, and the stored preference value
        public final String label;     // short form, shown in the collapsed setting
        public final String fullName;  // shown in the dropdown
        public final String language;  // ISO 639-1, matched against the device language
        /**
         * True when the words of Christ were placed by an algorithm confident enough to
         * ship but not to leave unremarked — Settings warns once before switching to one
         * of these. The ASV's spans are aligned word-for-word off the KJV's and land
         * almost exactly, so it stays out of this; the BSB reorders speech enough to slip,
         * and the two non-English texts are matched across a language boundary.
         */
        public final boolean approximateRedLetter;

        Entry(String code, String label, String fullName, String language,
              boolean approximateRedLetter) {
            this.code = code;
            this.label = label;
            this.fullName = fullName;
            this.language = language;
            this.approximateRedLetter = approximateRedLetter;
        }
    }

    // Order is the order of the settings dropdown.
    public static final Entry[] ALL = {
            new Entry("kjv", "KJV", "KJV — King James Version", "en", false),
            new Entry("asv", "ASV", "ASV — American Standard Version", "en", false),
            new Entry("bsb", "BSB", "BSB — Berean Standard Bible", "en", true),
            new Entry("rvr1909", "RVR", "RVR — Reina-Valera 1909 (Español)", "es", true),
            new Entry("cuvs", "CUV", "CUV — 和合本 Chinese Union Version", "zh", true),
    };

    // Localised book names, keyed by translation code then book index. Parsed once and
    // shared, the way RedLetter caches its JSON — several screens build their own
    // instance and the widget builds one per update.
    private static final HashMap<String, String[]> bookNames = new HashMap<>();
    private static boolean bookNamesLoaded;

    public static Entry get(String code) {
        for (Entry entry : ALL) {
            if (entry.code.equals(code)) return entry;
        }
        return ALL[0];
    }

    /**
     * The translation to read, defaulting to the device language on first use.
     *
     * Resolving and persisting in one step keeps every caller — the activities, the
     * widget and the notification receiver, which can all run before Settings has ever
     * been opened — on the same answer.
     */
    public static String current(Context context) {
        SharedPreferences sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE);
        String stored = sp.getString(PREF_KEY, null);
        if (stored != null && hasCode(stored)) return stored;

        String language = deviceLanguage();
        String resolved = forLanguage(language);
        sp.edit().putString(PREF_KEY, resolved).putString(PREF_LANGUAGE, language).apply();
        return resolved;
    }

    /**
     * Records a translation the reader picked themselves, which pins it.
     *
     * The automatic follow-the-system-language behaviour is a sensible default, not a
     * rule: a bilingual reader on an English phone who deliberately selects the RVR1909
     * means it, and shouldn't be pulled back to the KJV the next time they change their
     * phone's language. Once this has been called, {@link #syncWithDeviceLanguage} stops
     * moving the setting.
     */
    public static void choose(Context context, String code) {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
                .putString(PREF_KEY, code)
                .putString(PREF_LANGUAGE, deviceLanguage())
                .putBoolean(PREF_MANUAL, true)
                .apply();
    }

    /**
     * Moves the translation to match the device language when that language changes.
     *
     * Called from the {@link LocaleChangedReceiver} and again from
     * {@code MainActivity.onCreate}, since a force-stopped app receives no broadcasts
     * until it is launched by hand — the same reason the daily-verse alarm is re-armed
     * from both places.
     *
     * Only a change of *language* counts. `ACTION_LOCALE_CHANGED` also fires for a
     * region or ordering change (en-US → en-GB), and treating those as a switch would
     * throw away a translation the user picked themselves. A language with no bundled
     * translation leaves the current one alone rather than dropping back to the KJV —
     * the reader keeps what they had until a language we can actually serve comes along.
     *
     * @return true if the stored translation changed
     */
    public static boolean syncWithDeviceLanguage(Context context) {
        SharedPreferences sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE);
        String language = deviceLanguage();

        if (sp.getBoolean(PREF_MANUAL, false)) {
            // The reader chose this translation; the system language no longer decides it.
            // The recorded language is still kept current so the two can't drift apart.
            sp.edit().putString(PREF_LANGUAGE, language).apply();
            return false;
        }
        if (!sp.contains(PREF_LANGUAGE)) {
            // Upgrading from a build that never recorded this: adopt the current language
            // as the baseline so an existing choice survives, and switch only on the next
            // real change.
            sp.edit().putString(PREF_LANGUAGE, language).apply();
            return false;
        }
        if (language.equals(sp.getString(PREF_LANGUAGE, null))) return false;

        sp.edit().putString(PREF_LANGUAGE, language).apply();

        String match = forLanguage(language);
        if (match.equals(DEFAULT) && !hasLanguage(language)) return false;
        if (match.equals(sp.getString(PREF_KEY, null))) return false;

        sp.edit().putString(PREF_KEY, match).apply();
        return true;
    }

    private static boolean hasLanguage(String language) {
        for (Entry entry : ALL) {
            if (entry.language.equals(language)) return true;
        }
        return false;
    }

    public static Entry currentEntry(Context context) {
        return get(current(context));
    }

    private static boolean hasCode(String code) {
        for (Entry entry : ALL) {
            if (entry.code.equals(code)) return true;
        }
        return false;
    }

    /**
     * The bundled translation matching the device's language, or the KJV.
     *
     * Reads the *system* configuration rather than the app's: the app's own locale can
     * have been narrowed by a per-app language setting, and what we want here is the
     * language the person's phone is in.
     */
    static String deviceLanguage() {
        Locale locale;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            locale = Resources.getSystem().getConfiguration().getLocales().get(0);
        } else {
            locale = Resources.getSystem().getConfiguration().locale;
        }
        return locale == null ? "" : locale.getLanguage();
    }

    /** The bundled translation for a language, or the KJV when none matches. */
    static String forLanguage(String language) {
        for (Entry entry : ALL) {
            if (entry.language.equals(language)) return entry.code;
        }
        return DEFAULT;
    }

    /**
     * The book's display name in the current translation's language.
     *
     * The English translations have no entry in book_names.json and fall back to the
     * filename, which is what {@link Bible#getProperName} has always produced.
     */
    public static String properBook(Context context, String bookFile) {
        String[] names = namesFor(context, current(context));
        if (names == null) return Bible.getProperName(bookFile);

        String[] books = new Bible().books;
        for (int i = 0; i < books.length; i++) {
            if (books[i].equals(bookFile)) return names[i];
        }
        return Bible.getProperName(bookFile);
    }

    /** Localised book names for a translation, or null if it has none (the English ones). */
    public static String[] namesFor(Context context, String code) {
        synchronized (bookNames) {
            if (!bookNamesLoaded) {
                bookNamesLoaded = true;
                try {
                    InputStream is = context.getAssets().open("book_names.json");
                    byte[] bytes = Tools.readFully(is);
                    is.close();
                    JSONObject root = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
                    int bookCount = new Bible().books.length;
                    for (Entry entry : ALL) {
                        JSONArray array = root.optJSONArray(entry.code);
                        if (array == null || array.length() != bookCount) continue;
                        String[] names = new String[bookCount];
                        for (int i = 0; i < bookCount; i++) {
                            names[i] = array.optString(i);
                        }
                        bookNames.put(entry.code, names);
                    }
                } catch (Exception e) {
                    // no localised names available — English filenames it is
                }
            }
            return bookNames.get(code);
        }
    }
}
