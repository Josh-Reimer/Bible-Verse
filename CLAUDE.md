# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew assembleDebug          # build debug APK
./gradlew installDebug           # build and install on connected device/emulator
./gradlew clean                  # clean build outputs
./gradlew assembleRelease        # build release APK
```

`JAVA_HOME` must be set; `./gradlew` fails without it on this machine. There are no tests in this project. Verify UI changes live with `adb` (`adb shell input tap`, `adb shell screencap`, `uiautomator dump`) against a connected device — there is no other way to check layout/contrast/dialog behavior.

## Project Overview

Android app (Java, minSdk 33, targetSdk 36, Java 17) that displays a pseudo-random Bible verse on launch. Supports three translations: KJV (default), ASV, BSB, each with red-letter (words of Christ) rendering. Users can bookmark verses, view the full chapter context, and share verses. All Bible text is bundled as plain-text assets — no network calls.

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
- **`VerseOfTheDay`** — random verse selection via `getRandomRef()`. Contains several dead-code methods (`setVerseOfDay`, `verseOfDayExists`, `verseOfDayIsCurrent`, `getVerseFromFile`, `getRandomVerse`) that reference file-system paths not valid on Android — legacy from a CLI prototype. `Bible.getRange()` is similarly dead.

### `scripts/` (dev tooling, not shipped in the APK)
`generate_red_letter.py` regenerates `assets/red_letter_asv.json`/`red_letter_bsb.json` from `red_letter_kjv.json` plus the plain-text verse assets — run manually, output committed. `red_letter_asv_review.json`/`red_letter_bsb_review.json` list fallback verses (and why) for manual spot-checking; they are not read by the app.

### Activities
- **`MainActivity`** — entry point. On cold start (`savedInstanceState == null`) generates a random verse; on restore reconstructs the same `Verse` from the saved reference. `showVerse(Verse)` is the single method for updating the verse `TextView` — it calls `RedLetter.getSpanned()` and falls back to plain text. `applyTheme(SharedPreferences)` reads `"theme_mode"` and calls `AppCompatDelegate.setDefaultNightMode()` — note this triggers a full Activity recreation when changed from Settings. `onResume` re-fetches the current verse text (handles translation changes from Settings). FABs are hidden by default, revealed by menu FAB tap. Drawer (swipe right or hamburger) opens settings/bookmarks.
- **`VerseLookUpActivity`** — receives `"verse_ref"` via `Intent` extra, calls `getChapter()` once, and partitions lines into pre/target/post `TextView`s. Pre and post use `SpannableStringBuilder` so red-letter markup can be applied per-verse. The toolbar uses `wrap_content` height with `android:minHeight="?attr/actionBarSize"` so it expands to absorb the status-bar inset. ScrollView has `android:clipToPadding="false"` for edge-to-edge scrolling.
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
- dark/ligh/system theme persistence through app cold starts/warm starts
- there should be more than one bible translation with words in Christ being red
- a chapter lookup feature for viewing randomly generated verses in the chapter they are found in
- a settings activity with the theme and translation options avaiable there