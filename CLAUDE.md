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
`JAVA_HOME` must be set; `./gradlew` fails without it on this machine. This is the normal build path — the Termux/proot section further down is only for building the APK on an Android phone directly, and applies nowhere else.

## Tests

```bash
./gradlew test                                                  # JVM unit tests
./gradlew testDebugUnitTest --tests "com.verse.of.the.day.BibleTest"   # one class
./gradlew testDebugUnitTest --tests "*.BibleTest.getChapter*"          # one method
./gradlew connectedDebugAndroidTest                             # instrumented; needs a device
```

`app/src/test` holds the JVM tests (`BibleTest`, `ToolsTest`, `RedLetterTest`, and activity tests) — plain JUnit 4 with Mockito for the `Context`/`AssetManager`, no Robolectric, so anything touching real framework classes belongs in the other source set. `app/src/androidTest` holds Espresso and UiAutomator tests (the `*UIAutomatorTest` classes drive the real UI).

Tests cover logic, not appearance. Verify UI changes live with `adb` (`adb shell input tap`, `adb shell screencap`, `uiautomator dump`) against a connected device — there is no other way to check layout/contrast/dialog behaviour.

### Building the APK on an Android phone in Termux (aarch64 proot) — not the normal build path

**This section applies only when the shell you are in is Termux on the Android phone itself.** It is *not* how this repo is normally built: on a desktop or laptop the `## Build Commands` above are the whole story, and none of the workarounds below apply — no portable JDK download, no `qemu-x86_64` aapt2 wrapper, no `local.properties` pointing at `/root/coding/android-sdk`. Don't run `scripts/build-termux.sh` or follow these steps on a normal dev machine; they'd be pointless at best. Confirm which environment you're in before using any of this — on-device is `uname -m` = `aarch64` *and* `/data/data/com.termux` exists.

In that on-device environment (a proot-distro container on aarch64, accessed via Termux) there is no JDK and only a partial Android SDK preinstalled, and it hits an architecture mismatch a normal dev machine never would. `scripts/build-termux.sh` automates all of the below (run with `bash scripts/build-termux.sh`, not `sh` — it uses `BASH_SOURCE`). It's idempotent: safe to re-run each session, since it detects what's already set up in stable (`~/tools`) paths and skips redoing it. This script is a port of the same one in the sibling `balloon-pop-game` repo, adjusted for this repo's plain single-module (`:app`) layout — no `android:` task prefix, `build.gradle` lives at `app/build.gradle` not `android/build.gradle`, and the APK lands at `app/build/outputs/apk/debug/app-debug.apk` not under an `android/` subdirectory. The manual steps it automates:

1. **JDK**: none is installed system-wide, and there's no root/sudo (`sudo` isn't even on `PATH`; `apt-get install` fails with "requested operation requires superuser privilege"). Download a portable Temurin 21 tarball for `linux/aarch64` from Adoptium and extract it (no root needed):
   ```sh
   curl -sL "https://api.adoptium.net/v3/binary/latest/21/ga/linux/aarch64/jdk/hotspot/normal/eclipse" -o jdk21.tar.gz
   tar xzf jdk21.tar.gz   # -> jdk-21.x.x+y/
   export JAVA_HOME=".../jdk-21.x.x+y"
   export PATH="$JAVA_HOME/bin:$PATH"
   ```
   These exports don't persist across shell invocations in this harness — set them before every `gradlew` call. `~/tools` is shared across every repo built on this device, so a JDK downloaded once (e.g. while building `balloon-pop-game`) is found and reused here without a second download.

2. **Android SDK**: lives at `/root/coding/android-sdk` (a sibling of every repo, not inside any of them), with only `cmdline-tools/latest` present initially. Install the rest and accept licenses via `sdkmanager`, using *this* repo's `compileSdk` (36, read from `app/build.gradle`, not `android/build.gradle`):
   ```sh
   yes | sh /root/coding/android-sdk/cmdline-tools/latest/bin/sdkmanager --sdk_root=/root/coding/android-sdk --licenses
   sh /root/coding/android-sdk/cmdline-tools/latest/bin/sdkmanager --sdk_root=/root/coding/android-sdk \
     "platform-tools" "platforms;android-36" "build-tools;36.0.0"
   ```
   Then create `local.properties` in the repo root with `sdk.dir=/root/coding/android-sdk`. Because the SDK dir is shared, packages another repo already pulled (platform-tools, matching build-tools) don't need re-downloading — only this repo's specific `compileSdk` level might be missing.

3. **The actual blocker — aapt2 is x86_64-only, this device is aarch64**: AGP's Maven-resolved `aapt2` binary (and the SDK's own `build-tools/*/aapt2`) only ship as x86_64 Linux ELF binaries; there is no aarch64 build for this AGP version. Running `./gradlew assembleDebug` fails at `:app:processDebugResources` with `AAPT2 ... Daemon startup failed`. Fix by running the x86_64 `aapt2` under `qemu-x86_64` (already installed on this device via Termux at `/data/data/com.termux/files/usr/bin/qemu-x86_64`) against a minimal x86_64 glibc sysroot:
   ```sh
   # Build a small x86_64 sysroot (no root needed — dpkg-deb -x just unpacks)
   mkdir -p amd64root && cd amd64root
   curl -sLO http://deb.debian.org/debian/pool/main/g/glibc/libc6_<ver>_amd64.deb
   curl -sLO http://deb.debian.org/debian/pool/main/g/gcc-14/libgcc-s1_<ver>_amd64.deb
   # (match <ver> to `dpkg -l libc6 libgcc-s1` on this arm64 host)
   dpkg-deb -x libc6_*_amd64.deb sysroot
   dpkg-deb -x libgcc-s1_*_amd64.deb sysroot
   ln -sfn usr/lib64 sysroot/lib64
   ln -sfn usr/lib   sysroot/lib

   # Wrap the Maven-cached aapt2 (path varies by content hash — find it first):
   AAPT2_DIR=$(dirname "$(find ~/.gradle/caches -path '*/transformed/aapt2-*-linux/aapt2' | head -1)")
   mv "$AAPT2_DIR/aapt2" "$AAPT2_DIR/aapt2.real"
   cat > "$AAPT2_DIR/aapt2" <<EOF
   #!/bin/sh
   exec /data/data/com.termux/files/usr/bin/qemu-x86_64 -L $(pwd)/amd64root/sysroot "$AAPT2_DIR/aapt2.real" "\$@"
   EOF
   chmod +x "$AAPT2_DIR/aapt2"
   ```
   This must target the *Maven-cached* copy under `~/.gradle/caches`, not the SDK's `build-tools/*/aapt2` — files under `/root/coding/android-sdk` are owned by a different uid than the build shell, and `chmod` on them silently no-ops (the exec bit never actually gets set), so they can't be made runnable this way. The `~/.gradle` cache is owned by the build shell's own user, so `chmod +x` there works normally. Since `~/.gradle/caches` is also shared across repos, this wrapper — once built for any repo on this device — is already in place here too.

   Once wrapped, `./gradlew assembleDebug` succeeds normally. Since the wrapper lives under `~/.gradle/caches` (content-hash-keyed, and untouched by a plain rebuild) it survives repeat builds within the same environment, but not across a fresh container/session — redo this setup if `assembleDebug` again fails at `processDebugResources` with a daemon startup error.

4. **`gh` isn't on `PATH` by default**: the `gh` CLI is installed on this device (already authenticated as `Josh-Reimer`) but lives at `~/tools/gh_<version>_linux_arm64/bin/gh`, not on `PATH`. There's no `apt`/`pkg` fallback either — `apt-get install gh` fails the same way as any other package (no root). Fix per-shell:
   ```sh
   export PATH="$HOME/tools/gh_2.97.0_linux_arm64/bin:$PATH"
   gh auth status        # confirms it's already logged in
   gh repo clone Josh-Reimer/Bible-Verse -- -b main   # e.g. cloning this repo fresh
   ```
   Needed for `gh repo clone`, and for `git push`/`git pull` over `https` (no stored git credentials otherwise) — run `gh auth setup-git` once per shell before the first push to wire `gh` in as git's credential helper.

5. **Installing over a differently-signed build**: if the device already has a copy of this app installed from a different signing key (e.g. a release build, or a debug build from a different machine/keystore), `adb install -r app-debug.apk` fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE: ... signatures do not match`. There's no way around this short of `adb uninstall com.verse.of.the.day` first — which wipes the app's local Room DB (bookmarks) and `SharedPreferences` (theme/translation/widget state) on the device. Confirm with whoever owns the device before doing this, since it's a real (if easily-repopulated) data loss, not just a build hiccup.

6. **Verifying a UI change live via adb**: this device's own screen is usually showing *this* Termux/Claude Code session, not the app under test — so a bare `adb shell input tap` can land on the terminal instead of the app if the app isn't actually frontmost at that moment (e.g. it lost focus between an earlier launch and a later tap in the same script). Guard every interaction:
   ```sh
   adb shell am start -n com.verse.of.the.day/.MainActivity
   adb shell dumpsys window | grep mCurrentFocus   # must show .../com.verse.of.the.day/...Activity before tapping
   ```
   Screenshot with `adb shell screencap -p /sdcard/x.png`, `adb pull` it off, then `adb shell rm /sdcard/x.png` immediately — per the note above, screenshots are for on-device verification only and must never be left on the phone or opened on the user's machine.

## Project Overview

Android app (Java, minSdk 27, targetSdk 36, Java 17) that displays a pseudo-random Bible verse on launch. Supports five translations — KJV (default), ASV, BSB, RVR1909 (Spanish) and CUVS (Mandarin) — each with red-letter (words of Christ) rendering. Users can bookmark verses, view the full chapter context, and share verses. All Bible text is bundled as plain-text assets — no network calls.

All five are public domain: the Reina-Valera 1909 and the Chinese Union Version (1919 text, `新标点和合本` punctuation) came from ebible.org's USFM releases, which state public domain for both.

**Key dependencies:** Room (local bookmark DB), Material Components, AppCompat, ConstraintLayout, Facebook Shimmer (the similar-verses skeleton), Play in-app review. Note `io.github.gawwr4v:radialmenu` is declared in `app/build.gradle` but referenced nowhere in the source — a leftover from an abandoned attempt; the FAB arc is `FabRadialAnimator`, which animates the secondary FABs out on a radius by hand.

## Architecture

### Bible text storage (`assets/`)
Books are organised into translation subdirectories: `assets/kjv/`, `assets/asv/`, `assets/bsb/`, `assets/rvr1909/`, `assets/cuvs/`. Each book is a `.txt` file (e.g. `kjv/genesis.txt`). Every line after the header is one verse:
```
chapter:verse_number: verse text
```
Files have a short human-readable header before the first verse line (13 books start directly at `1:1:` with no header — don't assume a fixed offset when parsing). KJV files have no trailing newline; ASV and BSB files do — `getBookLength()` uses `trim()` before parsing the last line to handle both cases (not `stripTrailing()` — that is API 33, above the minSdk 27 floor).

### Translation selection
`Translations` is the registry: one `Entry` per bundled translation (asset-folder code, short label, dropdown name, ISO language, and whether its red-letter spans were placed algorithmically). `SettingsActivity` builds its dropdown straight off `Translations.ALL`, so adding a translation there is the only edit a new one needs on the settings side.

`Translations.current(context)` is the single place the choice is resolved, and every asset read goes through it via `Tools.getFile(context, filename)`, which opens `translation + "/" + filename`. Uses `InputStream.readAllBytes()` — not the older `available()` + single `read()` pattern — to handle compressed assets in subdirectories reliably.

On a device where the setting has never been touched, `current()` picks the translation matching the **device language** — `es` → RVR1909, `zh` → CUVS, anything else → KJV — and writes it to `SharedPreferences("settings")` immediately, alongside the language it matched under `"translation_language"`. Persisting on first read rather than defaulting on every read keeps the widget, the notification receiver and the activities (any of which can run first) on the same answer. It reads `Resources.getSystem()`, not the app's own configuration, because a per-app language override shouldn't decide which translation is native to the reader.

The translation then **follows the system language when that changes**. `Translations.syncWithDeviceLanguage()` does the work, driven from `LocaleChangedReceiver` (a manifest receiver for `ACTION_LOCALE_CHANGED` — a protected system broadcast, so `exported="false"` still reaches it, and one of the implicit broadcasts still delivered to manifest receivers on Android 8+) and again from `MainActivity.onCreate`, since a force-stopped app receives no broadcasts until launched by hand — the same two-place arrangement the daily-verse alarm uses. Three rules keep it from being destructive:

- **Only a change of language counts.** `"translation_language"` records what the current translation was chosen under, so a region or ordering change (`en-US` → `en-GB`) is ignored; treating those as a switch would discard a translation the user picked themselves.
- **A language with no bundled translation leaves the choice alone** rather than dropping back to the KJV. The recorded language still updates, so moving on to a language that *is* bundled is detected normally.
- **An upgrade from a build without the key seeds it** from the current device language without changing anything, so an existing reader's choice survives and only the next real change moves it.
- **A translation the reader picks in Settings pins it.** `Translations.choose()` is what `SettingsActivity` writes through, and it sets `"translation_manual"`; from then on `syncWithDeviceLanguage()` keeps the recorded language current but stops moving the setting. Following the system language is the default, not a rule — a bilingual reader on an English phone who deliberately selects the RVR1909 means it.

A switch also refreshes the widget. `SearchEngine`'s chapter cache keys off the translation and rebuilds itself, and `RedLetter`/`Translations` cache per translation, so nothing else needs invalidating.

`Translations.properBook(context, bookFile)` gives the book's display name in the current translation's language, from `assets/book_names.json` (`{translation code: [66 names in Bible.books order]}`, parsed once into a static cache like `RedLetter`'s). The English translations have no entry there and fall through to `Bible.getProperName`, the filename-derived name that has always been used. Everything that shows a reference — `Verse.proper_book`, the lookup screen's title, the widget, the notification, search results — goes through it, so a Spanish reader sees `Génesis` and a Mandarin reader `创世记`.

App UI strings are localised in `values-es/` and `values-zh/`; `values/` remains the English default.

### Verse reference format
A `Verse` is identified by `"bookIndex:chapter:verse"` (e.g. `"0:1:1"` = Genesis 1:1), where `bookIndex` is the 0-based index into `Bible.books[]`. This string is stored in Room, passed via `Intent` extras, and saved to `savedInstanceState`.

### Core classes
- **`Bible`** — stateless; all methods take `Context`+`Tools`. `getChapter()` returns all verses in a chapter as a newline-joined string and breaks early at a valid chapter-number boundary. `getBookLength()` finds the chapter number on the last non-whitespace line. `getVerse()` indexes the chapter's lines *positionally* (`lines[verse - 1]`), which is why every translation's assets are built on the same verse grid; it clamps rather than throwing, because translations don't all end a chapter on the same verse (Romans 16 stops at 24 in the KJV assets and at 27 everywhere else) and a reference saved under one can overrun another. `getProperName(String bookFile)` is the single source of truth for filename → display name conversion (e.g. `"first_samuel.txt"` → `"FIRST SAMUEL"`).
- **`Tools`** — stateless utility. `getFile()` is the single point for reading any asset (prepends translation folder). Also contains legacy CLI-era input-parsing helpers (`isBook`, `isBookChapter`, etc.) and a dead `"theme"` boolean SharedPreferences read, both unused by the current UI (theme is `"theme_mode"`, see below).
- **`Verse`** — data holder constructed from `(bookIndex, chapter, verse)` ints or from the reference string. Sets `proper_book`, `scripture_text`, `full_text`, and `reference` in `finish()`.
- **`Translations`** — the translation registry and the single resolver for the current one; see "Translation selection" above.
- **`RedLetter`** — loads `assets/red_letter_<translation>.json` lazily (cached per-translation in a `HashMap`) and returns a `Spanned` via `Html.fromHtml()` for verses with words-of-Christ markup; returns `null` if the file is missing or the verse has no red-letter content. `red_letter_kjv.json` (2027 entries) was parsed directly from the CrossWire KJV OSIS XML (Klopsch 1901 edition) `<q who="Jesus">` spans — the authoritative source. `red_letter_asv.json`/`red_letter_bsb.json` have no equivalent source text (no red-letter ASV/BSB edition exists) and are instead derived algorithmically by `scripts/generate_red_letter.py`, which aligns each KJV red span onto the target translation's verse text via word-level diffing, with BSB-specific quote-anchoring as a fallback when alignment confidence is low (BSB paraphrases more heavily and reorders speech/narration). Coverage is ~99% precise per-span; the remainder falls back to whole-verse coloring. `red_letter_rvr1909.json`/`red_letter_cuvs.json` cross a language boundary, where word-level diffing has nothing to align, so `scripts/generate_red_letter_translated.py` takes only *which* verses have words of Christ from the KJV data and finds the boundary from a marker the target text carries itself: the 1403 entries whose KJV span covers the whole verse need no boundary at all, the CUV's 「…」 speech punctuation supplies it for Chinese (including unpaired marks, which is how a quotation running across several verses shows up), and the colon the RV1909 introduces speech with supplies it for the 519 verses whose span runs to the end. That places 99.6% of CUV spans and 94.1% of RVR ones; the rest colour the whole verse.

`SettingsActivity` shows a one-time confirmation dialog before switching to any translation whose `Translations.Entry.approximateRedLetter` is set — BSB, RVR1909 and CUVS. The ASV is excluded deliberately: its spans are aligned word-for-word off the KJV's and land almost exactly.
- **`SimilarVerses`** — precomputed nearest-neighbor lookup for the "similar verses" feature. Lazily loads `assets/similar_verses.bin` into a single static `HashMap<String, Neighbor[]>` (cached across instances, mirroring `RedLetter`) and returns each verse's neighbors — a reference plus the translation whose wording surfaced the pairing offline (shown as a small badge in the sheet; it is not necessarily the translation the user is reading in). The whole table is generated offline (see `scripts/` below), so `getSimilar()` is a plain map hit — no model, no math, no NPU at runtime. The `.bin` is a compact big-endian format read via `DataInputStream`: a `uint32` count, a `uint8` K (neighbors per verse, currently 10), a `uint8` translation count and then that many length-prefixed ASCII names (`"kjv"`, …), then per verse a `book,chapter,verse` triple (one unsigned byte each — every value is <256) followed by K neighbor quads (`book,chapter,verse,translation-index`), with `(0,0,0)` as a "no neighbor" sentinel (chapters/verses are 1-based so it never collides; its translation byte is ignored). K and the translation names come from the header rather than constants, so regenerating with a different K or translation set needs no Java change. Parsing happens off the main thread (the actions sheet loads on a worker); references are translation-independent (KJV verse numbering is canonical) while the displayed text is rendered in the user's current translation.
- **`VerseOfTheDay`** — random verse selection via `getRandomRef()`. Contains several dead-code methods (`setVerseOfDay`, `verseOfDayExists`, `verseOfDayIsCurrent`, `getVerseFromFile`, `getRandomVerse`) that reference file-system paths not valid on Android — legacy from a CLI prototype. `Bible.getRange()` is similarly dead.

### `scripts/` (dev tooling, not shipped in the APK)
`convert_usfm.py` produced `assets/rvr1909/` and `assets/cuvs/` from ebible.org's USFM downloads (`spaRV1909_usfm.zip`, `cmn-cu89s_usfm.zip`) — run manually, output committed. Beyond stripping USFM markers it solves three alignment problems, since `Bible.getVerse()` addresses verses by *position*: verses the CUV omits (the dozen absent from the critical text — Matthew 18:11, John 5:4, Acts 8:37, …) are filled rather than skipped, and 11 of the 12 are recovered from the footnotes the CUV keeps them in (`\+fv N\+fv*` marks which verse the note's text is); verses numbered beyond the KJV grid (Romans 16:25-27, 3 John 15, Revelation 12:18) are appended, as the ASV/BSB assets already do; and the RV1909's Hebrew versification — twelve chapters ending on an empty verse marker because that verse moved to the head of the next chapter — is undone by `resolve_displacement()`, which finds the point where the source merges the verses back by scoring proper-noun and number agreement against the KJV rather than assuming a fixed rule (Jonah 2 merges at the end, Acts 20 at the start). `convert_rvr1909_review.json`/`convert_cuvs_review.json` dump those realigned chapters verse by verse beside the KJV for spot-checking; they are not read by the app. The alignment was checked by measuring anchor agreement: realigned chapters score 64.7%, *above* the 54.8% of untouched chapters, and collapse to 25.6% if deliberately read one verse off.

`generate_red_letter_translated.py` regenerates `assets/red_letter_rvr1909.json`/`red_letter_cuvs.json` — see the `RedLetter` entry above for the method; needs no dependencies beyond the standard library.

`generate_red_letter.py` regenerates `assets/red_letter_asv.json`/`red_letter_bsb.json` from `red_letter_kjv.json` plus the plain-text verse assets — run manually, output committed. `red_letter_asv_review.json`/`red_letter_bsb_review.json` list fallback verses (and why) for manual spot-checking; they are not read by the app.

`generate_similar_verses.py` regenerates `assets/similar_verses.bin` (the `SimilarVerses` table) from the verse assets — run manually, output committed. It needs heavier deps than the red-letter script (`torch`, `sentence-transformers`, `scikit-learn`, `numpy`); use a Python 3.12 venv (torch has no 3.14 wheels), which is git-ignored (`scripts/.venv-embed/`). It embeds every verse with `all-mpnet-base-v2` — separately in *every* translation in `TRANSLATIONS`, with the others aligned onto KJV's reference list (verses a translation numbers differently, e.g. Romans 16:25-27 in ASV/BSB, or leaves blank are masked out of that translation's pass) — then — because the goal is *thematic* neighbors a keyword/text search would NOT surface — scores each candidate as `(semantic cosine) - LEXICAL_PENALTY * (TF-IDF lexical cosine)` and hard-drops any candidate above `LEXICAL_MAX` TF-IDF cosine. TF-IDF weighting penalizes shared *rare* words (proper nouns, stock phrases) hardest — exactly what text search keys on. A candidate keeps its *best* score across translations (so a pairing only one translation's wording makes obvious still surfaces), but the lexical veto applies if it trips `LEXICAL_MAX` in *any* translation. Same-chapter verses are excluded as near-duplicates, and the results are capped at one verse per chapter — adjacent verses score almost identically, so without the cap a single passage eats half the list; `POOL` over-fetches candidates so the cap still leaves K filled. Verses whose whole candidate pool clusters into fewer than K chapters end up with fewer than K neighbors (sentinels), which the app handles. `LEXICAL_PENALTY`/`LEXICAL_MAX` are the tunable knobs (raise for vaguer/broader results); embedding all three translations takes ~25 min, so the vectors are cached in the git-ignored `scripts/.cache-embed/` (keyed by model + verse text) and a knob-tuning re-run is then only the scoring pass. `similar_verses_review.json` dumps neighbors for a handful of well-known verses for spot-checking; it is not read by the app.

### Activities
- **`MainActivity`** — entry point. On cold start (`savedInstanceState == null`) generates a random verse — unless the launching `Intent` carries a `"verse_ref"` extra (the home-screen widget), in which case that verse is shown instead; `onNewIntent` handles the same extra for the already-running `singleTop` case. On restore reconstructs the same `Verse` from the saved reference. `showVerse(Verse)` is the single method for updating the verse `TextView` — it calls `RedLetter.getSpanned()` and falls back to plain text. `applyTheme(SharedPreferences)` reads `"theme_mode"` and calls `AppCompatDelegate.setDefaultNightMode()` — note this triggers a full Activity recreation when changed from Settings. `onResume` re-fetches the current verse text (handles translation changes from Settings). FABs are hidden by default, revealed by menu FAB tap. Drawer (swipe right or hamburger) opens settings/bookmarks.
- **`VerseLookUpActivity`** — receives `"verse_ref"` via `Intent` extra, calls `getChapter()` once, and partitions lines into pre/target/post `TextView`s. Pre and post use `SpannableStringBuilder` so red-letter markup can be applied per-verse. The toolbar uses `wrap_content` height with `android:minHeight="?attr/actionBarSize"` so it expands to absorb the status-bar inset. ScrollView has `android:clipToPadding="false"` for edge-to-edge scrolling.
- **`VerseActionsBottomSheet`** — the per-verse sheet raised by tapping a verse in `VerseLookUpActivity`. A `BottomSheetDialogFragment` that owns no data itself: it talks to the host activity through a `Host` interface (bookmark toggle, share, similar-verse loading/opening) so DB and intent logic stay in the activity. The tapped verse's own text sits at the top under its reference (`Host.verseActionText()`, red-letter aware, current translation) so the sheet reads on its own once it covers the chapter behind it. Below the bookmark/share rows it shows a "Similar verses" section: neighbors are loaded on a worker thread (`Host.loadSimilarVerses()` does the `SimilarVerses` lookup plus per-verse `RedLetter`/`Verse` text building, all off the main thread), a Facebook Shimmer skeleton (`com.facebook.shimmer:shimmer`) holds for a deliberate `MIN_SHIMMER_MS` (~800ms — the lookup is instant, so the dwell is what makes it read as an animation), then the real verses fade in (up to `MAX_SIMILAR` = 10, red-letter aware, current translation) and each is tappable to open its chapter. Each row's reference carries a muted uppercase badge naming the translation that surfaced the pairing (`SimilarVerses.Neighbor.translation`). Content is wrapped in a `NestedScrollView` so the sheet can grow past the fold. `onDismiss` clears the tapped-verse highlight for every dismissal path.
- **`bookmarks_activity`** — RecyclerView list of bookmarked verses backed by Room.
- **`SettingsActivity`** — theme and translation dropdowns, both in `SharedPreferences("settings")`. Each is a plain `Spinner` (`settings_activity.xml`) with an `ArrayAdapter` over `android.R.layout.simple_spinner_item`. The translation adapter overrides `getDropDownView()` so the popup shows full names ("KJV — King James Version") while the collapsed field keeps the short label; the entries, labels and full names all come from `Translations.ALL` rather than literal arrays. `Spinner` fires `onItemSelected` on the initial programmatic `setSelection` and again on Activity recreation (a theme change), so the listener carries a `committedIndex` and returns early when the position hasn't actually changed — without it the red-letter accuracy dialog (shown for any translation with `approximateRedLetter` set) re-shows on every recreation. Cancel reverts to `committedIndex`; OK writes through `Translations.choose()`. That dialog's buttons get an explicit text colour in code (`R.color.app_on_surface`) because `colorPrimary` is intentionally repurposed app-wide to match `colorSurface` (for toolbar tinting), which would otherwise make default `AlertDialog` button text invisible in light theme.

### Text search
The search bar's word search is a separate path from the reference parser below, spread over
`SearchEngine`, `QueryTokenizer`, `SearchEngineQuery`, `SearchResult`, `SearchResultsBottomSheet`,
`SearchResultsAdapter` and `SearchResultsViewModel`.

`SearchEngine` keeps the whole Bible in memory as `chaptersOrig`/`chaptersLower` (~4-5MB of text),
indexed `[bookIndex][chapter - 1]`, each entry a chapter's verse lines joined by `\n` — the same shape
`Bible.getChapter()` returns. The cache is keyed by translation and rebuilt when that changes, and
`getVerseText()` serves result rows from it rather than re-reading a book file per result the way
`new Verse(...)` would.

Verses are addressed internally as a packed `int` ARI (`bookIndex << 16 | chapter << 8 | verse`,
androidbible's scheme), which makes the per-token result lists cheap to intersect and lets
`searchChapter` scan a whole chapter as one string, mapping match offsets back to verse numbers by
walking `\n` positions — a chapter with no match costs a single `indexOf`. Multi-token queries
intersect progressively and bail as soon as a list comes back empty. Matching is substring-based, so
CJK queries work without word boundaries; `relevanceScore()` then ranks 0 (whole-word run), 1
(substring), else 2 + how far the smallest window covering every token overruns the query.

Searches run on a background executor. Results go in an Activity-scoped `SearchResultsViewModel`, never
into a saved-state `Bundle` — a broad search serialises to several MB and blows the ~1MB binder limit
(`TransactionTooLargeException`). After process death the ViewModel comes back empty and the restored
sheet calls `Host.onSearchSheetRestoredEmpty()` to re-run the search; a search that lands after the
Activity saved state sets `pendingShow` and `onResume` shows it. The sheet owns no data and reaches the
Activity through its `Host` interface, the same arrangement `VerseActionsBottomSheet` uses.

### Reference search
`VerseReferenceParser` lets the `MainActivity` search bar accept a reference instead of words: `parse()` returns a book/chapter/verse (or `null`), and `performSearch` puts the resolved verses at the top of the results sheet with the ordinary text matches below, de-duplicated. Reference results are built with an empty `searchQuery` so the adapter highlights nothing in a verse the user reached by reference rather than by its words; they keep the default `relevanceScore` of 0 and the sort is stable, so they stay on top without special-casing it.

Parsing treats every non-alphanumeric character as a separator and splits letter/digit boundaries, so `"John 3:16"`, `"john 3 16"`, `"JOHN, 3, 16"` and `"jn3:16"` are the same query. "Letter" means `Character.isLetter`, not `a-z`, and accents are folded away (NFD, combining marks dropped) before tokenizing, so `"Génesis 1:1"` and `"创世记 1:1"` parse too — Chinese names carry no spaces and fall out as a single token, which is what the prefix matching wants. Trailing numbers are the chapter and verse; what's left is the book, after an optional ordinal (`1`/`i`/`first`/`1st`). Book names come from the asset filenames (`first_samuel.txt` → ordinal 1 + stem `"samuel"`) plus every translation's localised names from `book_names.json` — registered for *all* translations regardless of which is being read, since the reference names the same verse either way — matched exact-first then by *unique* prefix — that gives `gen`, `matt`, `1 cor` for free, and an ambiguous prefix like `jo` resolves to nothing. `EXTRA_NAMES` only holds names a prefix can't reach: the correct spellings of the assets' misspelled filenames (`ecclesiastes`, `ezekiel`, `philippians`, `thessalonians`) and abbreviations that aren't prefixes (`jn`, `jas`, `kgs`, `phil` — ambiguous with Philemon by prefix alone).

The result must be a verse that actually exists (`getBookLength`/`getChapterLength`), which is what keeps ordinary searches out of this path — a bare book name is never a reference, so `"john"` still text-searches. A chapter with no verse (`"psalms 23"`) yields the whole chapter so the user can pick from it, and in the one-chapter books a lone number is the verse (`"jude 5"` = Jude 1:5).

### Home-screen widget
`VerseWidgetProvider` (+ `res/layout/verse_widget.xml`, `res/xml/verse_widget_info.xml`) is an `AppWidgetProvider` showing one verse a day: reference, verse text (red-letter aware, current translation), an optional translation badge, and a die button. Tapping the card opens `MainActivity` on that verse via a `"verse_ref"` intent extra; tapping the die broadcasts `ACTION_SHUFFLE` back to the provider for a new random verse.

The chosen verse lives in `SharedPreferences("settings")` under `"widget_verse_ref"`/`"widget_verse_day"` (epoch day), so every placed widget shows the same verse and a periodic update doesn't reshuffle it — `currentRef()` only rerolls when the stored day isn't today. This is deliberately *independent* of `MainActivity`'s verse, which rerolls on every cold start. `updatePeriodMillis` is one hour, purely so the day rollover lands within an hour of midnight; the render itself is idempotent.

Rendering reads a whole book file plus the red-letter JSON, so it runs on a private executor — `onUpdate`/`onReceive` call `goAsync()` and finish the `PendingResult` from the worker. Because a RemoteViews host inflates these views in *its* process, `AppCompatDelegate` has no say there: the widget follows the **system** light/dark setting via `values`/`values-night` (`widget_background`, `widget_text`, `widget_accent`, `widget_muted`, `widget_ripple`), not the app's `"theme_mode"` preference, so the two can legitimately disagree. For the same reason `widget_dice.xml` duplicates `dice_48`'s path with a literal `@color` fill instead of `?attr/colorControlNormal`. `values-v31/dimens.xml` swaps `widget_margin` to 0dp and the corner radius to `@android:dimen/system_app_widget_background_radius`, since only Android 12+ launchers inset and round widgets themselves. The layout doubles as the widget picker's `previewLayout`, which is why its placeholder strings are a real verse (John 3:16) — the provider overwrites every one on first update. `SettingsActivity` calls `VerseWidgetProvider.refresh()` when the translation or the translation-label switch changes.

### Daily verse notification
Opt-in, off by default, and the **only** place `POST_NOTIFICATIONS` is ever requested is the Settings switch the user just turned on — nothing prompts on launch.

`VerseNotifier` (static helper) owns the preference, the alarm and the notification; `VerseNotificationReceiver` is the manifest receiver that fires it. `setEnabled()` writes `"daily_verse_notification"` and arms/cancels in one step, so the pref and the alarm can't disagree. The alarm is a **one-shot** `setAndAllowWhileIdle(RTC_WAKEUP, …)` that the receiver re-arms for the next day each time it fires — a repeating alarm would drift across DST, and an *exact* alarm would need the restricted `SCHEDULE_EXACT_ALARM` permission for no real gain on a daily nudge. Delivery is therefore approximate (doze slack), which is fine for a once-a-day nudge.

The time of day is the reader's to pick. `"daily_verse_notification_time"` stores it as minutes since midnight — one key, so it can never be half-set — defaulting to `DEFAULT_NOTIFY_AT` = 08:00 local, and `setNotifyAt()` persists it and re-arms in one step the way `setEnabled()` does. Re-setting the same `PendingIntent` replaces the pending alarm, so moving the time needs no cancel first. A time earlier in the day than the one that already fired means a second verse today, which is what asking for it at that time reads as.

`SettingsActivity` opens a `MaterialTimePicker` from the notification row's summary line, which carries the chosen time — the settings idiom of a row whose value is its summary, rather than a second row. The picker follows the system 12/24-hour setting (`DateFormat.is24HourFormat`) and the summary is formatted with `DateFormat.getTimeFormat`, so it reads as `8:00 AM`, `08:00` or `上午8:00` depending on the reader. It is themed with `ThemeOverlay.VerseApp.TimePicker`, which puts a real `colorPrimary` back for the same reason the red-letter dialog forces its button colour: the app repurposes `colorPrimary` to match `colorSurface`, and the picker paints the clock hand, the selected number and the OK/Cancel text with it. A picker showing across an Activity recreation is rebuilt by the `FragmentManager` without its listener, so `onCreate` re-attaches one to any survivor found by tag.

Pending alarms don't survive a reboot, an app update, or a force-stop, so re-arming happens from three places: `BOOT_COMPLETED`/`MY_PACKAGE_REPLACED` in the receiver (its `intent-filter` is `exported="false"` — both are protected system broadcasts, which reach non-exported receivers), and `MainActivity.onCreate` for the force-stop case. All three go through `scheduleIfEnabled()`, and re-setting the same `PendingIntent` is a no-op.

The verse is picked fresh when the alarm fires — independent of both `MainActivity`'s verse and the widget's — and building it reads a book file, so `onReceive` uses `goAsync()` + a private executor like the widget. The notification is plain text, not red-letter `Spanned`: Android strips colour spans from notification content. Tapping it opens `MainActivity` on that verse via the same `"verse_ref"` extra the widget uses. `ic_notification_verse.xml` is an **outline** because the status bar keeps only a small icon's alpha channel — a filled book would render as a white block.

`SettingsActivity` holds the permission dance: an `ActivityResultLauncher` registered in `onCreate` (before the activity starts), a `suppressNotificationListener` flag so programmatic `setChecked()` reverts don't re-enter the listener, and `syncNotificationSwitch()` — called from `onCreate` and `onResume` — which turns the setting back **off** if the permission was revoked from system settings, so the switch never promises a notification the OS won't deliver. A denial that the system won't re-prompt for (`shouldShowRequestPermissionRationale` false) gets a dialog offering `ACTION_APP_NOTIFICATION_SETTINGS`; an ordinary denial just gets a Snackbar.

### Persistence
- **Room DB** (`bookmark_database`, `bookmark_dao`, `bookmark` entity) — table `bookmarks` with unique index on `bible_reference`. Opened with `allowMainThreadQueries()`. Both `MainActivity` and `bookmarks_activity` hold `db` opened once in `onCreate` — do not open additional instances.
- **`SharedPreferences("review_prefs")`** — a *separate* prefs file owned by `PlayStoreReviewPrompt`, which counts distinct launch days (`recordAppOpen()` from `MainActivity.onCreate`) and offers the Play in-app review once the install is old enough and the app has been opened on enough separate days. Its keys are deliberately not in `"settings"` — nothing in the app UI exposes them.
- **`SharedPreferences("settings")`** — keys: `"theme_mode"` (string: `"light"`/`"dark"`/`"system"`), `"translation"` (string: `"kjv"`/`"asv"`/`"bsb"`/`"rvr1909"`/`"cuvs"` — written on first read from the device language, and moved when the system language changes; see "Translation selection"), `"translation_language"` (string: the device language the stored translation was chosen under), `"translation_manual"` (boolean: set once the reader picks a translation in Settings, which stops the language sync), `"show_translation_info"` (boolean), `"widget_verse_ref"` + `"widget_verse_day"` (see Home-screen widget), `"daily_verse_notification"` (boolean, default false) + `"daily_verse_notification_time"` (int: minutes since midnight, default 480 = 08:00 — both see Daily verse notification). The older `"theme"` boolean key is legacy/dead (still read in `Tools.java` but not written or used by any active code path).

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
- bible translation should default to the device language where a bundled translation matches it (Spanish → RVR1909, Mandarin → CUVS), and to KJV (King James Version) otherwise
- bible translation label should default to off