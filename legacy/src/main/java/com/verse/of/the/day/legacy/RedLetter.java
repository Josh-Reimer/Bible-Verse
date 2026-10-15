package com.verse.of.the.day.legacy;

import android.content.Context;
import android.text.Html;
import android.text.Spanned;
import org.json.JSONObject;
import java.io.InputStream;
import java.util.HashMap;

public class RedLetter {

    // Static so all RedLetter instances share one parsed copy per translation —
    // the JSON assets are ~330KB each and several screens create their own instance.
    private static final HashMap<String, JSONObject> cache = new HashMap<>();

    private JSONObject load(Context context, String translation) {
        synchronized (cache) {
            if (cache.containsKey(translation)) return cache.get(translation);
            JSONObject data = null;
            try {
                InputStream is = context.getAssets().open("red_letter_" + translation + ".json");
                byte[] bytes = Tools.readFully(is);
                is.close();
                data = new JSONObject(new String(bytes, Tools.UTF_8));
            } catch (Exception e) {
                // no red-letter file for this translation
            }
            cache.put(translation, data);
            return data;
        }
    }

    // Returns a Spanned (HTML) if this verse has red-letter markup, null otherwise.
    @SuppressWarnings("deprecation") // the flags overload is API 24; this is what it defaults to
    public Spanned getSpanned(Context context, String verseRef) {
        JSONObject data = load(context, Translations.current(context));
        if (data == null) return null;
        String html = data.optString(verseRef, null);
        if (html == null) return null;
        return Html.fromHtml(html);
    }
}
