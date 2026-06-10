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

`JAVA_HOME` must be set; `./gradlew` fails without it on this machine. There are no tests in this project.

## Project Overview

Android app (Java, minSdk 33, targetSdk 36, Java 17) that displays a pseudo-random Bible verse on launch. Supports three translations: KJV (default), ASV, BSB. KJV includes red-letter (words of Christ) rendering. Users can bookmark verses, view the full chapter context, and share verses. All Bible text is bundled as plain-text assets — no network calls.

**Key dependencies:** Room (local bookmark DB), Material Components, AppCompat, ConstraintLayout.

## Architecture

### Bible text storage (`assets/`)
Books are organised into translation subdirectories: `assets/kjv/`, `assets/asv/`, `assets/bsb/`. Each book is a `.txt` file (e.g. `kjv/genesis.txt`). Every line after the header is one verse:
```
chapter:verse_number: verse text
```
Files have a short human-readable header before the first verse line. KJV files have no trailing newline; ASV and BSB files do — `getBookLength()` uses `stripTrailing()` before parsing the last line to handle both cases.

### Translation selection
`Tools.getFile(context, filename)` reads `SharedPreferences("settings")` key `"translation"` (default `"kjv"`) and opens `translation + "/" + filename`. This single change propagates to all file reads. Uses `InputStream.readAllBytes()` — not the older `available()` + single `read()` pattern — to handle compressed assets in subdirectories reliably.

### Verse reference format
A `Verse` is identified by `"bookIndex:chapter:verse"` (e.g. `"0:1:1"` = Genesis 1:1), where `bookIndex` is the 0-based index into `Bible.books[]`. This string is stored in Room, passed via `Intent` extras, and saved to `savedInstanceState`.

### Core classes
- **`Bible`** — stateless; all methods take `Context`+`Tools`. `getChapter()` returns all verses in a chapter as a newline-joined string and breaks early at a valid chapter-number boundary. `getBookLength()` finds the chapter number on the last non-whitespace line. `getProperName(String bookFile)` is the single source of truth for filename → display name conversion (e.g. `"first_samuel.txt"` → `"FIRST SAMUEL"`).
- **`Tools`** — stateless utility. `getFile()` is the single point for reading any asset (prepends translation folder). Also contains legacy CLI-era input-parsing helpers (`isBook`, `isBookChapter`, etc.) that are unused by the UI.
- **`Verse`** — data holder constructed from `(bookIndex, chapter, verse)` ints or from the reference string. Sets `proper_book`, `scripture_text`, `full_text`, and `reference` in `finish()`.
- **`RedLetter`** — loads `assets/red_letter_kjv.json` once (lazily) and returns a `Spanned` via `Html.fromHtml()` for KJV verses with words-of-Christ markup. Returns `null` for non-KJV translations or verses with no red-letter content. The JSON (2027 entries) was parsed from the CrossWire KJV OSIS XML (Klopsch 1901 edition) using `<q who="Jesus">` spans.
- **`VerseOfTheDay`** — random verse selection via `getRandomRef()`. Contains several dead-code methods (`setVerseOfDay`, `verseOfDayExists`, `verseOfDayIsCurrent`, `getVerseFromFile`, `getRandomVerse`) that reference file-system paths not valid on Android — legacy from a CLI prototype. `Bible.getRange()` is similarly dead.

### Activities
- **`MainActivity`** — entry point. On cold start (`savedInstanceState == null`) generates a random verse; on restore reconstructs the same `Verse` from the saved reference. `showVerse(Verse)` is the single method for updating the verse `TextView` — it calls `RedLetter.getSpanned()` and falls back to plain text. `onResume` re-fetches the current verse text (handles translation changes from Settings). FABs are hidden by default, revealed by menu FAB tap. Drawer (swipe right or hamburger) opens settings/bookmarks.
- **`VerseLookUpActivity`** — receives `"verse_ref"` via `Intent` extra, calls `getChapter()` once, and partitions lines into pre/target/post `TextView`s. Pre and post use `SpannableStringBuilder` so red-letter markup can be applied per-verse. The toolbar uses `wrap_content` height with `android:minHeight="?attr/actionBarSize"` so it expands to absorb the status-bar inset. ScrollView has `android:clipToPadding="false"` for edge-to-edge scrolling.
- **`bookmarks_activity`** — RecyclerView list of bookmarked verses backed by Room.
- **`SettingsActivity`** — dark/light mode toggle (`"theme"` boolean) and translation `Spinner` (KJV/ASV/BSB, `"translation"` string). Both stored in `SharedPreferences("settings")`.

### Persistence
- **Room DB** (`bookmark_database`, `bookmark_dao`, `bookmark` entity) — table `bookmarks` with unique index on `bible_reference`. Opened with `allowMainThreadQueries()`. Both `MainActivity` and `bookmarks_activity` hold `db` opened once in `onCreate` — do not open additional instances.
- **`SharedPreferences("settings")`** — keys: `"theme"` (boolean, true = dark), `"translation"` (string: `"kjv"` / `"asv"` / `"bsb"`).

### Edge-to-edge
Both `MainActivity` and `VerseLookUpActivity` call `WindowCompat.setDecorFitsSystemWindows(getWindow(), false)`. Insets are applied manually: status-bar top inset as padding on the toolbar, nav-bar bottom inset as padding on the scroll container.
