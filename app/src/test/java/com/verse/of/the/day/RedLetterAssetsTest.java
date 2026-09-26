package com.verse.of.the.day;

import org.json.JSONObject;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;

/**
 * RedLetter renders a red-letter entry's markup as the whole verse in place of the verse
 * asset, so the markup's visible text must be exactly that verse. red_letter_kjv.json was
 * parsed from a different KJV edition than the bundled text and once disagreed with it in 17
 * verses (Cæsar for Caesar, Lord for LORD).
 */
public class RedLetterAssetsTest {

    private static final Path ASSETS = Paths.get("src/main/assets");
    // The Wear module bundles its own copies of these three; they must not drift apart.
    private static final Path WEAR_ASSETS = Paths.get("../wear/src/main/assets");
    private static final String[] BOOKS = new Bible().books;
    private static final Pattern VERSE_LINE = Pattern.compile("^(\\d+):(\\d+):\\s?(.*)$");

    @Test
    public void markupTextMatchesVerseAssets() throws Exception {
        for (Translations.Entry entry : Translations.ALL) {
            String code = entry.code;
            JSONObject markup = new JSONObject(read(ASSETS.resolve("red_letter_" + code + ".json")));
            Map<Integer, Map<String, String>> books = new HashMap<>();
            List<String> mismatches = new ArrayList<>();
            for (Iterator<String> it = markup.keys(); it.hasNext(); ) {
                String ref = it.next();
                String[] parts = ref.split(":");
                int book = Integer.parseInt(parts[0]);
                Map<String, String> verses = books.get(book);
                if (verses == null) {
                    verses = readVerses(ASSETS.resolve(code).resolve(BOOKS[book]));
                    books.put(book, verses);
                }
                String expected = verses.get(parts[1] + ":" + parts[2]);
                String shown = markup.getString(ref).replaceAll("<[^>]+>", "").trim();
                if (expected == null || !shown.equals(expected.trim())) mismatches.add(ref);
            }
            assertTrue(code + " red-letter text differs from the verse asset: " + mismatches,
                    mismatches.isEmpty());
        }
    }

    @Test
    public void wearCopiesMatchPhone() throws IOException {
        for (String code : new String[] {"kjv", "rvr1909", "cuvs"}) {
            String name = "red_letter_" + code + ".json";
            assertArrayEquals(name, Files.readAllBytes(ASSETS.resolve(name)),
                    Files.readAllBytes(WEAR_ASSETS.resolve(name)));
        }
    }

    private static Map<String, String> readVerses(Path file) throws IOException {
        Map<String, String> verses = new HashMap<>();
        for (String line : read(file).split("\r?\n")) {
            Matcher m = VERSE_LINE.matcher(line);
            if (m.matches()) verses.put(m.group(1) + ":" + m.group(2), m.group(3));
        }
        return verses;
    }

    private static String read(Path file) throws IOException {
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }
}
