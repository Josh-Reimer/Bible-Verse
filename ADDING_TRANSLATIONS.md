# Adding a new Bible translation

This walks through everything needed to add a new translation to the app, with or
without red-letter (words of Christ) highlighting. See `CLAUDE.md` for the
surrounding architecture this builds on.

## 1. Pick a translation code

A short lowercase code identifies the translation everywhere: `"kjv"`, `"asv"`,
`"bsb"`. This code is the `assets/` subfolder name, the `SharedPreferences("settings")`
`"translation"` value, and the suffix on `red_letter_<code>.json`. Pick one for the
new translation (e.g. `"web"` for the World English Bible) and use it consistently —
there is no separate mapping table.

Confirm the translation is actually public domain or otherwise licensed for
redistribution before bundling its text as an asset — the app ships all Bible text
locally with no network calls or attribution screen.

## 2. Add the verse text assets

Create `app/src/main/assets/<code>/` containing one `.txt` file per book, named
exactly as in `Bible.books[]` in `Bible.java` (66 entries, e.g. `genesis.txt`,
`first_samuel.txt`, `song_of_solomon.txt` — copy the list from that file rather than
retyping it, the spelling is idiosyncratic in places, e.g. `eccliasiastes.txt`,
`ezekial.txt`, `philipians.txt`). `Tools.getFile()` has no fallback for a missing
file — every book must be present or that book will fail to load for this
translation.

Each file needs a short human-readable header line (anything, it's never parsed) and
then one line per verse:

```
chapter:verse: verse text
```

Notes that affect parsing:
- 13 of the existing books have no header at all and start directly at `1:1:` —
  either is fine, `getChapter()`/`getBookLength()` don't assume a fixed offset.
- `getBookLength()` finds the chapter number from the **last non-whitespace line**,
  so make sure the file doesn't end with a trailing blank line containing only
  whitespace after the final verse (a single trailing `\n` after the last verse line
  is fine either way — `stripTrailing()` handles both the KJV-style no-trailing-newline
  files and the ASV/BSB-style trailing-newline files).
- `getChapter()` walks lines looking for `chapnum.equals(chapnumstring)` and stops at
  the next *parseable* chapter number that doesn't match — non-numeric annotation
  lines are tolerated, but don't insert lines that look like `12:` with a numeric
  chapter you don't intend.

## 3. Wire the translation into Settings

In `SettingsActivity.java`, add the new code to all three parallel arrays/values in
the translation spinner section:

```java
String[] translations = {"KJV", "ASV", "BSB", "WEB"};
String[] translationFullNames = {
        "KJV — King James Version",
        "ASV — American Standard Version",
        "BSB — Berean Standard Bible",
        "WEB — World English Bible"
};
```

`translations[i].toLowerCase()` must equal the asset folder name from step 1 — the
spinner's selection-matching and the value written to `SharedPreferences` both derive
the pref string this way, there's no separate code mapping.

That's it for a translation with no red-letter content — `RedLetter.load()` will
fail to find `red_letter_web.json`, catch the exception, cache `null`, and
`getSpanned()` will return `null` for every verse, so `MainActivity`/`VerseLookUpActivity`
fall back to plain (non-highlighted) text automatically. No other code changes
needed.

## 4. Adding red-letter highlighting (optional)

`red_letter_<code>.json` is a flat JSON object: keys are `"bookIndex:chapter:verse"`
(0-based book index into `Bible.books[]`, matching the `Verse` reference format),
values are an HTML fragment using `<font color="#CC0000">...</font>` to wrap the
words Christ spoke, rendered via `Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY)`.
A verse with no red-letter content simply has no key — don't emit empty/`null`
values. Tags must be balanced and the visible text (tags stripped) must exactly
match the verse text in `assets/<code>/<book>.txt`, since it's spliced directly into
the UI next to plain-text neighboring verses.

There are two ways to produce this file, depending on whether the new translation
has a real public-domain red-letter edition:

### 4a. A genuine red-letter source exists for this translation

This is what KJV uses: `red_letter_kjv.json` (2027 entries) was parsed once from the
CrossWire KJV OSIS XML (Klopsch 1901 edition), which marks Christ's words with
`<q who="Jesus">` at the word/phrase level, including spans that cross verse
boundaries. If the new translation has an equivalent OSIS/USFM/SWORD-module edition
with that kind of markup, write a one-off script that parses it the same way KJV's
was parsed and emits the same `{"b:c:v": "<font...>...</font>"}` contract directly —
there's no generic source-format parser in this repo to reuse, each source format
needs its own one-off conversion.

### 4b. No red-letter source exists (the common case)

This is what ASV and BSB use. No red-letter ASV/BSB edition exists anywhere
(confirmed by research when this was built — OpenScriptures, eBible, and CrossWire's
own ASV SWORD module all lack the markup), so their red-letter spans are derived
algorithmically from KJV's spans by `scripts/generate_red_letter.py`. See
`PLANS.md`, Issue #14, for the full design rationale; in short, it:

1. Splits each `red_letter_kjv.json` entry into red/non-red text segments.
2. Tokenizes the KJV verse and the target verse and aligns them with
   `difflib.SequenceMatcher` to map each red word-range onto the target translation.
3. Falls back to matching against `"..."` quote spans in the target text when
   confidence is low (helps with translations that paraphrase heavily or reorder
   speech vs. narration relative to the KJV).
4. Falls back to coloring the whole verse if neither strategy is confident, rather
   than silently omitting the verse.

To extend it to a new translation:

1. Add the new code to the tuple in `main()`:
   ```python
   for translation in ('asv', 'bsb', 'web'):
   ```
2. Make sure `assets/<code>/*.txt` exists already (step 2) — the script reads verse
   text straight from those files via `load_book()`/`get_verse_text()`.
3. Run it from the repo root:
   ```bash
   python3 scripts/generate_red_letter.py
   ```
   This overwrites `app/src/main/assets/red_letter_<code>.json` for every translation
   in the tuple (it regenerates ASV and BSB too — that's expected, they're
   deterministic) and writes `scripts/red_letter_<code>_review.json`, listing every
   verse that fell back to whole-verse coloring or was missing, with a reason.
4. Check the printed coverage line (`full=... precise=... fallback=... missing=...`)
   and skim the review file. ASV/BSB landed at ~98–99.6% precise; if the new
   translation's fallback rate is notably higher (heavy paraphrase translations will
   align worse), that's a signal the per-translation warning dialog below is
   warranted.
5. Spot-check a sample of non-fallback verses by eye — high alignment confidence can
   still produce slightly-off span boundaries, and there's no automated test suite to
   catch that for you.

`scripts/red_letter_*_review.json` are dev-tooling artifacts for manual
spot-checking only; they are not read by the app and don't need to ship.

### Accuracy warning dialog (recommended for algorithmically-derived translations)

`SettingsActivity`'s translation listener special-cases BSB to show a one-time
confirmation dialog ("Red-letter highlighting ... is algorithmically generated and
may occasionally be inaccurate") before committing the switch, since BSB's spans are
derived rather than sourced. If the new translation's red-letter data also comes from
step 4b, extend the same check rather than adding a parallel special case:

```java
if (!selected.equals("bsb") && !selected.equals("web")) {
    spEditor.putString("translation", selected).apply();
    committedIndex = position;
    return;
}
```

A translation with a genuine source-derived `red_letter_<code>.json` (step 4a) — or
no red-letter file at all — doesn't need this warning.

## 5. Build and verify

There is no test suite in this project; verify on a connected device:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew installDebug
```

Then in the running app: open Settings, switch to the new translation, confirm the
spinner shows it and (if applicable) the warning dialog appears once and doesn't
re-show spuriously on theme switches; back out to the main screen and confirm a
verse renders (with red-letter highlighting, if added) without a "could not load
file" string appearing (that string is `Tools.getFile()`'s catch-all for a missing
or misnamed asset); open "View Chapter" on a verse to confirm the surrounding
chapter also renders correctly in the new translation.
