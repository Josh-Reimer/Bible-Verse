# CLAUDE.md

before making ui breaking changes, think about how things will look and realize that you don't have the ability to know what looks good and what is bad ux.
preserve as much original logic and keep code diffs small unless refactors are necessary.


This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew assembleDebug          # build debug APK
./gradlew installDebug           # build and install on connected device/emulator
./gradlew clean                  # clean build outputs
./gradlew assembleRelease        # build release APK
```
a small note on debugging with adb screenshoting, do not open the file on the users machine, this breaks the development cycle,
never save the screenshots on the phone, always remove. only analyze the screenshots for your own use, the user does not need them, he has his own eyes.
`JAVA_HOME` must be set; `./gradlew` fails without it on this machine. There are no tests in this project. Verify UI changes live with `adb` (`adb shell input tap`, `adb shell screencap`, `uiautomator dump`) against a connected device — there is no other way to check layout/contrast/dialog behavior.

## Project Overview

Android app (Java, minSdk 27, targetSdk 36, Java 17) that displays a pseudo-random Bible verse on launch. Supports three translations: KJV (default), ASV, BSB, each with red-letter (words of Christ) rendering. Users can bookmark verses, view the full chapter context, and share verses. All Bible text is bundled as plain-text assets — no network calls.

**Key dependencies:** Room (local bookmark DB), Material Components, AppCompat, ConstraintLayout.

## Architecture

### Bible text storage (`assets/`)
Books are organised into translation subdirectories: `assets/kjv/`, `assets/asv/`, `assets/bsb/`. Each book is a `.txt` file (e.g. `kjv/genesis.txt`). Every line after the header is one verse:
```
chapter:verse_number: verse text
```
Files have a short human-readable header before the first verse line (13 books start directly at `1:1:` with no header — don't assume a fixed offset when parsing). KJV files have no trailing newline; ASV and BSB files do — `getBookLength()` uses `stripTrailing()` before parsing the last line to handle both cases.

### Translation selection
`Tools.getFile(context, filename)` reads `SharedPreferences("settings")` key `"translation"` (default `"kjv"`) and opens `translation + "/" + filename`. This single change propagates to all file reads. Uses `InputStream.readAllBytes()` — not the older `available()` + single `read()` pattern — to handle compressed assets in subdirectories reliably.

### Verse reference format
A `Verse` is identified by `"bookIndex:chapter:verse"` (e.g. `"0:1:1"` = Genesis 1:1), where `bookIndex` is the 0-based index into `Bible.books[]`. This string is stored in Room, passed via `Intent` extras, and saved to `savedInstanceState`.

### Core classes
- **`Bible`** — stateless; all methods take `Context`+`Tools`. `getChapter()` returns all verses in a chapter as a newline-joined string and breaks early at a valid chapter-number boundary. `getBookLength()` finds the chapter number on the last non-whitespace line. `getProperName(String bookFile)` is the single source of truth for filename → display name conversion (e.g. `"first_samuel.txt"` → `"FIRST SAMUEL"`).
- **`Tools`** — stateless utility. `getFile()` is the single point for reading any asset (prepends translation folder). Also contains legacy CLI-era input-parsing helpers (`isBook`, `isBookChapter`, etc.) and a dead `"theme"` boolean SharedPreferences read, both unused by the current UI (theme is `"theme_mode"`, see below).
- **`Verse`** — data holder constructed from `(bookIndex, chapter, verse)` ints or from the reference string. Sets `proper_book`, `scripture_text`, `full_text`, and `reference` in `finish()`.
- **`RedLetter`** — loads `assets/red_letter_<translation>.json` lazily (cached per-translation in a `HashMap`) and returns a `Spanned` via `Html.fromHtml()` for verses with words-of-Christ markup; returns `null` if the file is missing or the verse has no red-letter content. `red_letter_kjv.json` (2027 entries) was parsed directly from the CrossWire KJV OSIS XML (Klopsch 1901 edition) `<q who="Jesus">` spans — the authoritative source. `red_letter_asv.json`/`red_letter_bsb.json` have no equivalent source text (no red-letter ASV/BSB edition exists) and are instead derived algorithmically by `scripts/generate_red_letter.py`, which aligns each KJV red span onto the target translation's verse text via word-level diffing, with BSB-specific quote-anchoring as a fallback when alignment confidence is low (BSB paraphrases more heavily and reorders speech/narration). Coverage is ~99% precise per-span; the remainder falls back to whole-verse coloring. Because of this, `SettingsActivity` shows a one-time confirmation dialog when switching to BSB warning that red-letter highlighting may occasionally be inaccurate.
- **`SimilarVerses`** — precomputed nearest-neighbor lookup for the "similar verses" feature. Lazily loads `assets/similar_verses.bin` into a single static `HashMap<String, Neighbor[]>` (cached across instances, mirroring `RedLetter`) and returns each verse's neighbors — a reference plus the translation whose wording surfaced the pairing offline (shown as a small badge in the sheet; it is not necessarily the translation the user is reading in). The whole table is generated offline (see `scripts/` below), so `getSimilar()` is a plain map hit — no model, no math, no NPU at runtime. The `.bin` is a compact big-endian format read via `DataInputStream`: a `uint32` count, a `uint8` K (neighbors per verse, currently 10), a `uint8` translation count and then that many length-prefixed ASCII names (`"kjv"`, …), then per verse a `book,chapter,verse` triple (one unsigned byte each — every value is <256) followed by K neighbor quads (`book,chapter,verse,translation-index`), with `(0,0,0)` as a "no neighbor" sentinel (chapters/verses are 1-based so it never collides; its translation byte is ignored). K and the translation names come from the header rather than constants, so regenerating with a different K or translation set needs no Java change. Parsing happens off the main thread (the actions sheet loads on a worker); references are translation-independent (KJV verse numbering is canonical) while the displayed text is rendered in the user's current translation.
- **`VerseOfTheDay`** — random verse selection via `getRandomRef()`. Contains several dead-code methods (`setVerseOfDay`, `verseOfDayExists`, `verseOfDayIsCurrent`, `getVerseFromFile`, `getRandomVerse`) that reference file-system paths not valid on Android — legacy from a CLI prototype. `Bible.getRange()` is similarly dead.

### `scripts/` (dev tooling, not shipped in the APK)
`generate_red_letter.py` regenerates `assets/red_letter_asv.json`/`red_letter_bsb.json` from `red_letter_kjv.json` plus the plain-text verse assets — run manually, output committed. `red_letter_asv_review.json`/`red_letter_bsb_review.json` list fallback verses (and why) for manual spot-checking; they are not read by the app.

`generate_similar_verses.py` regenerates `assets/similar_verses.bin` (the `SimilarVerses` table) from the verse assets — run manually, output committed. It needs heavier deps than the red-letter script (`torch`, `sentence-transformers`, `scikit-learn`, `numpy`); use a Python 3.12 venv (torch has no 3.14 wheels), which is git-ignored (`scripts/.venv-embed/`). It embeds every verse with `all-mpnet-base-v2` — separately in *every* translation in `TRANSLATIONS`, with the others aligned onto KJV's reference list (verses a translation numbers differently, e.g. Romans 16:25-27 in ASV/BSB, or leaves blank are masked out of that translation's pass) — then — because the goal is *thematic* neighbors a keyword/text search would NOT surface — scores each candidate as `(semantic cosine) - LEXICAL_PENALTY * (TF-IDF lexical cosine)` and hard-drops any candidate above `LEXICAL_MAX` TF-IDF cosine. TF-IDF weighting penalizes shared *rare* words (proper nouns, stock phrases) hardest — exactly what text search keys on. A candidate keeps its *best* score across translations (so a pairing only one translation's wording makes obvious still surfaces), but the lexical veto applies if it trips `LEXICAL_MAX` in *any* translation. Same-chapter verses are excluded as near-duplicates, and the results are capped at one verse per chapter — adjacent verses score almost identically, so without the cap a single passage eats half the list; `POOL` over-fetches candidates so the cap still leaves K filled. Verses whose whole candidate pool clusters into fewer than K chapters end up with fewer than K neighbors (sentinels), which the app handles. `LEXICAL_PENALTY`/`LEXICAL_MAX` are the tunable knobs (raise for vaguer/broader results); embedding all three translations takes ~25 min, so the vectors are cached in the git-ignored `scripts/.cache-embed/` (keyed by model + verse text) and a knob-tuning re-run is then only the scoring pass. `similar_verses_review.json` dumps neighbors for a handful of well-known verses for spot-checking; it is not read by the app.

### Activities
- **`MainActivity`** — entry point. On cold start (`savedInstanceState == null`) generates a random verse; on restore reconstructs the same `Verse` from the saved reference. `showVerse(Verse)` is the single method for updating the verse `TextView` — it calls `RedLetter.getSpanned()` and falls back to plain text. `applyTheme(SharedPreferences)` reads `"theme_mode"` and calls `AppCompatDelegate.setDefaultNightMode()` — note this triggers a full Activity recreation when changed from Settings. `onResume` re-fetches the current verse text (handles translation changes from Settings). FABs are hidden by default, revealed by menu FAB tap. Drawer (swipe right or hamburger) opens settings/bookmarks.
- **`VerseLookUpActivity`** — receives `"verse_ref"` via `Intent` extra, calls `getChapter()` once, and partitions lines into pre/target/post `TextView`s. Pre and post use `SpannableStringBuilder` so red-letter markup can be applied per-verse. The toolbar uses `wrap_content` height with `android:minHeight="?attr/actionBarSize"` so it expands to absorb the status-bar inset. ScrollView has `android:clipToPadding="false"` for edge-to-edge scrolling.
- **`VerseActionsBottomSheet`** — the per-verse sheet raised by tapping a verse in `VerseLookUpActivity`. A `BottomSheetDialogFragment` that owns no data itself: it talks to the host activity through a `Host` interface (bookmark toggle, share, similar-verse loading/opening) so DB and intent logic stay in the activity. The tapped verse's own text sits at the top under its reference (`Host.verseActionText()`, red-letter aware, current translation) so the sheet reads on its own once it covers the chapter behind it. Below the bookmark/share rows it shows a "Similar verses" section: neighbors are loaded on a worker thread (`Host.loadSimilarVerses()` does the `SimilarVerses` lookup plus per-verse `RedLetter`/`Verse` text building, all off the main thread), a Facebook Shimmer skeleton (`com.facebook.shimmer:shimmer`) holds for a deliberate `MIN_SHIMMER_MS` (~800ms — the lookup is instant, so the dwell is what makes it read as an animation), then the real verses fade in (up to `MAX_SIMILAR` = 10, red-letter aware, current translation) and each is tappable to open its chapter. Each row's reference carries a muted uppercase badge naming the translation that surfaced the pairing (`SimilarVerses.Neighbor.translation`). Content is wrapped in a `NestedScrollView` so the sheet can grow past the fold. `onDismiss` clears the tapped-verse highlight for every dismissal path.
- **`bookmarks_activity`** — RecyclerView list of bookmarked verses backed by Room.
- **`SettingsActivity`** — theme and translation dropdowns, both in `SharedPreferences("settings")`. Each is a `TextInputLayout` (`style="@style/Widget.Material3.TextInputLayout.OutlinedBox.ExposedDropdownMenu"`) wrapping a `MaterialAutoCompleteTextView` — the native Material3 ExposedDropdownMenu gives the outlined-box + chevron appearance using `colorOutline`/`colorSurfaceVariant` theme attrs (both defined in `values/themes.xml` and `values-night/themes.xml`). Each adapter overrides `getFilter()` to return a no-op filter — without this, `AutoCompleteTextView` filters the dropdown list to only items matching the current text, hiding the other options. The translation adapter also overrides `getView()` to show full names (e.g. "KJV — King James Version") in the popup while `setText()` controls the short label in the collapsed field. The translation listener uses `setOnItemClickListener` (fires only on explicit user taps, no spurious Activity-recreation callbacks) with a `committedIndex` field on the anonymous class used only to guard the BSB re-showing dialog and to revert on Cancel. That dialog's buttons get an explicit text color in code (`R.color.app_on_surface`) because `colorPrimary` is intentionally repurposed app-wide to match `colorSurface` (for toolbar tinting), which would otherwise make default `AlertDialog` button text invisible in light theme.

### Persistence
- **Room DB** (`bookmark_database`, `bookmark_dao`, `bookmark` entity) — table `bookmarks` with unique index on `bible_reference`. Opened with `allowMainThreadQueries()`. Both `MainActivity` and `bookmarks_activity` hold `db` opened once in `onCreate` — do not open additional instances.
- **`SharedPreferences("settings")`** — keys: `"theme_mode"` (string: `"light"`/`"dark"`/`"system"`), `"translation"` (string: `"kjv"`/`"asv"`/`"bsb"`). The older `"theme"` boolean key is legacy/dead (still read in `Tools.java` but not written or used by any active code path).

### Edge-to-edge
Both `MainActivity` and `VerseLookUpActivity` call `WindowCompat.setDecorFitsSystemWindows(getWindow(), false)`. Insets are applied manually: status-bar top inset as padding on the toolbar, nav-bar bottom inset as padding on the scroll container. Note `android:fitsSystemWindows="true"` on a root `ConstraintLayout` (used in `settings_activity.xml`) overrides/replaces any explicit `android:padding` on that view with system-bar-inset-derived padding — work around it with margins on children rather than relying on the root's padding.

### Features that must exist
- share verse functionality on the main activity
- random verse generation (roll the dice) on the main activity
- bookmarks that are persistent through app updates
- dark/light/system theme persistence through app cold starts/warm starts
- there should be more than one bible translation with words in Christ being red
- a chapter lookup feature for viewing randomly generated verses in the chapter they are found in
- a settings activity with the theme and translation options available there

### Defaults
- theme should default to system theme
- bible translation should default to KJV (King James Version)
- bible translation label should default to off