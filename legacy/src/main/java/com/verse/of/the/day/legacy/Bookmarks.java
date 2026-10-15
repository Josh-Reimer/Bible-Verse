package com.verse.of.the.day.legacy;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

/**
 * Bookmarked verse references, newest first. :app keeps these in Room, which needs API 23;
 * a handful of short strings fits a JSON array in preferences just as well.
 */
final class Bookmarks {

    private static final String PREFS = "bookmarks";
    private static final String KEY = "refs";

    private Bookmarks() {}

    static List<String> all(Context context) {
        List<String> refs = new ArrayList<>();
        String json = prefs(context).getString(KEY, "[]");
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                refs.add(array.getString(i));
            }
        } catch (JSONException e) {
            // unreadable — treat as empty rather than crash every screen that asks
        }
        return refs;
    }

    static boolean contains(Context context, String ref) {
        return all(context).contains(ref);
    }

    static void add(Context context, String ref) {
        List<String> refs = all(context);
        if (refs.contains(ref)) return;
        refs.add(0, ref);
        save(context, refs);
    }

    static void remove(Context context, String ref) {
        List<String> refs = all(context);
        if (refs.remove(ref)) save(context, refs);
    }

    private static void save(Context context, List<String> refs) {
        prefs(context).edit().putString(KEY, new JSONArray(refs).toString()).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
