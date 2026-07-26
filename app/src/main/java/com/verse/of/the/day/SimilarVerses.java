package com.verse.of.the.day;

import android.content.Context;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;

// Precomputed nearest-neighbor lookup for "similar verses". The whole table is
// generated offline (scripts/generate_similar_verses.py) and bundled as a compact
// binary, so a lookup here is just a HashMap hit — no model, no math at runtime.
//
// Static cache mirrors RedLetter: the ~560KB table is parsed once and shared, and
// the parse happens off the main thread (the actions sheet loads on a worker).
public class SimilarVerses {

    private static HashMap<String, String[]> cache; // null until first load attempt
    private static final Object lock = new Object();

    private void ensureLoaded(Context context) {
        synchronized (lock) {
            if (cache != null) return;
            HashMap<String, String[]> map = new HashMap<>();
            try (InputStream is = context.getAssets().open("similar_verses.bin");
                 DataInputStream in = new DataInputStream(new BufferedInputStream(is))) {
                int count = in.readInt();
                for (int i = 0; i < count; i++) {
                    String key = in.readUnsignedByte() + ":" + in.readUnsignedByte() + ":" + in.readUnsignedByte();
                    ArrayList<String> neighbors = new ArrayList<>(5);
                    for (int k = 0; k < 5; k++) {
                        int b = in.readUnsignedByte(), c = in.readUnsignedByte(), v = in.readUnsignedByte();
                        if (b == 0 && c == 0 && v == 0) continue; // sentinel: no neighbor
                        neighbors.add(b + ":" + c + ":" + v);
                    }
                    map.put(key, neighbors.toArray(new String[0]));
                }
            } catch (Exception e) {
                // Table missing or corrupt — feature degrades to "no similar verses".
            }
            cache = map; // non-null (possibly empty) so we don't retry a broken file each tap
        }
    }

    // Returns neighbor references ("book:chapter:verse"), best match first; empty if none.
    // Call off the main thread — the first call parses the whole table.
    public String[] getSimilar(Context context, String ref) {
        ensureLoaded(context);
        String[] result = cache.get(ref);
        return result == null ? new String[0] : result;
    }
}
