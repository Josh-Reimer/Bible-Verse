# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
./gradlew assembleDebug          # build debug APK
./gradlew installDebug           # build and install on connected device/emulator
./gradlew clean                  # clean build outputs
./gradlew assembleRelease        # build release APK
```

There are no tests in this project.

## Project Overview

Android app (Java, minSdk 33, targetSdk 36, Java 17) that displays a pseudo-random King James Bible verse on launch. The user can bookmark verses, view the full chapter context, and share verses. All Bible text is bundled as plain-text assets — no network calls.

**Key dependencies:** Room (local bookmark DB), Material Components, AppCompat, ConstraintLayout.

## Architecture

### Bible text storage (`assets/`)
Each book is a `.txt` file (e.g. `genesis.txt`). Every line is one verse in the format:
```
chapter:verse_number: verse text
```
Files have a short human-readable header (book title) before the first verse line. `Bible.java` reads these files via `Tools.getFile()` (which uses `AssetManager`) and parses them line-by-line with `BufferedReader`/`StringReader`.

### Verse reference format
A `Verse` is identified by the string `"bookIndex:chapter:verse"` (e.g. `"0:1:1"` = Genesis 1:1), where `bookIndex` is the 0-based index into `Bible.books[]`. This reference string is what gets stored in Room, passed between activities via `Intent` extras, and saved to `savedInstanceState`.

### Core classes
- **`Bible`** — stateless; all methods take `Context`+`Tools` to load assets. `getChapter()` returns all verses in a chapter as a newline-joined string and breaks early once it passes the target chapter; `getVerse()` calls `getChapter()` and indexes into the split array; `getBookLength()` finds the chapter number on the last line to count chapters. `getProperName(String bookFile)` is the single source of truth for converting a book filename (e.g. `"first_samuel.txt"`) to a display name (e.g. `"FIRST SAMUEL"`) — use it instead of duplicating the replace chain.
- **`Tools`** — stateless utility class. `getFile()` is the single point for reading any asset. Also contains input-parsing helpers (`isBook`, `isBookChapter`, etc.) left over from an earlier CLI-style interface.
- **`Verse`** — data holder constructed either from `(bookIndex, chapter, verse)` ints or from the `"bookIndex:chapter:verse"` reference string. Sets `proper_book`, `scripture_text`, `full_text`, and `reference` in `finish()`.
- **`VerseOfTheDay`** — random verse selection: picks a random book index, then a random chapter within that book, then a random verse within that chapter. `getRandomRef()` is the method called by the UI.

### Activities
- **`MainActivity`** — entry point. On cold start (`savedInstanceState == null`) generates a new random verse; on restore (rotation, dark-mode toggle) reconstructs the same `Verse` from the saved reference string. FABs are hidden by default and revealed by a menu FAB tap. Drawer navigation (swipe right or hamburger) opens settings/bookmarks.
- **`VerseLookUpActivity`** — receives a verse reference via `Intent` extra `"verse_ref"` (`"bookIndex:chapter:verse"`), splits it directly to extract book/chapter/verse integers without constructing a `Verse` object, then calls `getChapter()` once. The loop partitions lines into before/target/after `TextView`s; lines with fewer than two colon-separated parts are skipped (guards against the `"io exception"` sentinel from `getChapter`). Once the target verse is found a `pastTarget` flag is set, after which remaining lines skip `split`/`parseInt` and go straight to the post-verse text. Total: one file read per activity launch.
- **`bookmarks_activity`** — RecyclerView list of bookmarked verses backed by the Room DB.
- **`SettingsActivity`** — dark/light mode toggle stored in `SharedPreferences("settings")` under key `"theme"` (true = dark).

### Persistence
- **Room DB** (`bookmark_database`, `bookmark_dao`, `bookmark` entity) — table `bookmarks` with unique index on `bible_reference`. The DB is opened with `allowMainThreadQueries()`. Both `MainActivity` and `bookmarks_activity` hold `db` as an instance field opened once in `onCreate` — do not open additional instances.
- **`SharedPreferences("settings")`** — stores the theme boolean.

### Known dead code
`VerseOfTheDay` contains several methods (`setVerseOfDay`, `verseOfDayExists`, `verseOfDayIsCurrent`, `getVerseFromFile`, `getRandomVerse`) and `Bible.getRange()` that are unused by the UI and reference file-system paths that don't work on Android. These are legacy from the original CLI prototype.
