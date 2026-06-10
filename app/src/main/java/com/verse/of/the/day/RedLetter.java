package com.verse.of.the.day;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.Html;
import android.text.Spanned;
import org.json.JSONObject;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class RedLetter {

    private JSONObject data = null;
    private boolean loaded = false;

    private void load(Context context) {
        if (loaded) return;
        loaded = true;
        try {
            InputStream is = context.getAssets().open("red_letter_kjv.json");
            byte[] bytes = is.readAllBytes();
            is.close();
            data = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
        } catch (Exception e) {
            data = null;
        }
    }

    private boolean isKjv(Context context) {
        SharedPreferences sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE);
        return "kjv".equals(sp.getString("translation", "kjv"));
    }

    // Returns a Spanned (HTML) if this verse has red-letter markup, null otherwise.
    public Spanned getSpanned(Context context, String verseRef) {
        if (!isKjv(context)) return null;
        load(context);
        if (data == null) return null;
        String html = data.optString(verseRef, null);
        if (html == null) return null;
        return Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY);
    }
}
