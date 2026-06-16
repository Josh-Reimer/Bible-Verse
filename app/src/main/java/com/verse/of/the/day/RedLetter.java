package com.verse.of.the.day;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.Html;
import android.text.Spanned;
import org.json.JSONObject;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

public class RedLetter {

    private final HashMap<String, JSONObject> cache = new HashMap<>();

    private JSONObject load(Context context, String translation) {
        if (cache.containsKey(translation)) return cache.get(translation);
        JSONObject data = null;
        try {
            InputStream is = context.getAssets().open("red_letter_" + translation + ".json");
            byte[] bytes = is.readAllBytes();
            is.close();
            data = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
        } catch (Exception e) {
            // no red-letter file for this translation
        }
        cache.put(translation, data);
        return data;
    }

    private String getTranslation(Context context) {
        SharedPreferences sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE);
        return sp.getString("translation", "kjv");
    }

    // Returns a Spanned (HTML) if this verse has red-letter markup, null otherwise.
    public Spanned getSpanned(Context context, String verseRef) {
        JSONObject data = load(context, getTranslation(context));
        if (data == null) return null;
        String html = data.optString(verseRef, null);
        if (html == null) return null;
        return Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY);
    }
}
