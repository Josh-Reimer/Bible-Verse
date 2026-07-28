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
// Static cache mirrors RedLetter: the table is parsed once and shared, and the
// parse happens off the main thread (the actions sheet loads on a worker).
//
// Neighbors-per-verse (K) and the translation-name table are read from the file
// header rather than hardcoded, so regenerating the table with a different K or a
// different translation set needs no change here.
public class SimilarVerses {

    // One neighbor: its reference plus the translation whose wording surfaced the
    // pairing (a candidate keeps its best score across all bundled translations, so
    // this is not always the translation the user is currently reading in).
    public static class Neighbor {
        public final String ref;         // "book:chapter:verse"
        public final String translation; // "kjv" / "asv" / "bsb"

        Neighbor(String ref, String translation) {
            this.ref = ref;
            this.translation = translation;
        }
    }

    private static final Neighbor[] NONE = new Neighbor[0];

    private static HashMap<String, Neighbor[]> cache; // null until first load attempt
    private static final Object lock = new Object();

    private void ensureLoaded(Context context) {
        synchronized (lock) {
            if (cache != null) return;
            HashMap<String, Neighbor[]> map = new HashMap<>();
            try (InputStream is = context.getAssets().open("similar_verses.bin");
                 DataInputStream in = new DataInputStream(new BufferedInputStream(is))) {
                int count = in.readInt();
                int perVerse = in.readUnsignedByte(); // neighbors stored per verse (K)
                String[] translations = new String[in.readUnsignedByte()];
                for (int t = 0; t < translations.length; t++) {
                    byte[] name = new byte[in.readUnsignedByte()];
                    in.readFully(name);
                    translations[t] = new String(name, java.nio.charset.StandardCharsets.US_ASCII);
                }
                for (int i = 0; i < count; i++) {
                    String key = in.readUnsignedByte() + ":" + in.readUnsignedByte() + ":" + in.readUnsignedByte();
                    ArrayList<Neighbor> neighbors = new ArrayList<>(perVerse);
                    for (int k = 0; k < perVerse; k++) {
                        int b = in.readUnsignedByte(), c = in.readUnsignedByte(), v = in.readUnsignedByte();
                        int t = in.readUnsignedByte();
                        if (b == 0 && c == 0 && v == 0) continue; // sentinel: no neighbor
                        neighbors.add(new Neighbor(b + ":" + c + ":" + v,
                                t < translations.length ? translations[t] : ""));
                    }
                    map.put(key, neighbors.toArray(NONE));
                }
            } catch (Exception e) {
                // Table missing or corrupt — feature degrades to "no similar verses".
            }
            cache = map; // non-null (possibly empty) so we don't retry a broken file each tap
        }
    }

    // Returns the verse's neighbors, best match first; empty if none.
    // Call off the main thread — the first call parses the whole table.
    public Neighbor[] getSimilar(Context context, String ref) {
        ensureLoaded(context);
        Neighbor[] result = cache.get(ref);
        return result == null ? NONE : result;
    }
}
