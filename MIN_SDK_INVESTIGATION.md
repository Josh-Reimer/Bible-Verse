# Investigation: lowering `minSdk` from 33

Date: 2026-07-28
Branch: `verselookup-utilities`

Method: AGP lint (9.2.1) run against the real project with `minSdk` overridden via a Gradle
init script (`androidComponents.beforeVariants { vb.minSdk = N }`) and `--rerun-tasks`.
No repo files were modified. Findings below are actual lint output, not estimates.

---

## The hard floor is API 23 — set by dependencies

Manifest merge fails outright at 21:

```
uses-sdk:minSdkVersion 21 cannot be smaller than version 23
declared in library [androidx.room:room-runtime-android:2.8.4]
```

Scanning every cached AAR manifest, only three deps sit above 21:

| Dependency | minSdk |
|---|---|
| `androidx.room` 2.8.4 | 23 |
| `androidx.sqlite` (transitive) | 23 |
| `androidx.activity` 1.12.0 / `androidx.navigationevent` | 23 |

Everything else is 21 or lower — Material 1.13.0 (21), appcompat (21), constraintlayout (21),
`io.github.gawwr4v:radialmenu` (21), Play review (21), Shimmer (14), recyclerview (14),
fragment (14), drawerlayout (14).

Going below 23 would require downgrading Room (to 2.6.x) and androidx.activity (to 1.9.x).
Not worth it for the ~0.3% of devices on API 21–22.

---

## Code that actually breaks

At `minSdk 23`, lint reports **10 API issues**:

| Site | API needed | Fix |
|---|---|---|
| `Tools.java:32` — `InputStream.readAllBytes()` | **33** | Manual read loop / `ByteArrayOutputStream` |
| `RedLetter.java:26` — `InputStream.readAllBytes()` | **33** | Same |
| `RedLetter.java:48` — `Html.fromHtml(s, flags)` + `FROM_HTML_MODE_LEGACY` | 24 | `HtmlCompat.fromHtml()` (androidx.core, already transitive) |
| `MainActivity.java:543` — `List.sort()` | 24 | `Collections.sort(list, cmp)` (API 1) |
| `QueryTokenizer.java:67` — `List.sort()` | 24 | Same |
| `SearchEngine.java:129` — `List.sort()` | 24 | Same |
| `VerseOfTheDay.java:80` — `LocalDate.now()` | 26 | Inside dead method `setVerseOfDay` — delete |
| `VerseOfTheDay.java:108` — `LocalDate.now()` | 26 | Inside dead method `verseOfDayIsCurrent` — delete |
| `values/themes.xml:23` — `android:windowLightNavigationBar` | 27 | Move to `values-v27/` |
| `values-night/themes.xml:23` — same | 27 | Same |

### Not a problem despite appearances

- **`Bible.java:69` `stripTrailing()`** — listed as API 33 in the platform's `api-versions.xml`,
  but D8 backports it automatically. Confirmed via
  `java -cp r8.jar com.android.tools.r8.BackportedMethodList --min-api 23`, which lists
  `String#stripTrailing`, `strip`, `stripLeading`, `isBlank`, `repeat`, `join`.
  `InputStream#readAllBytes` is **not** on that list — which is exactly why it survives as an error.
- **Insets / edge-to-edge** — all of `MainActivity`, `VerseLookUpActivity`, `bookmarks_activity`
  go through `WindowCompat` / `ViewCompat.setOnApplyWindowInsetsListener` / `WindowInsetsCompat`.
  Compat-safe down to 21.
- **`ContextCompat.getColor`, `MaterialColors.getColor`** — compat-safe.
- **`android:enableOnBackInvokedCallback`** — API 33 manifest attribute, silently ignored below.
  Lint reports it as an `UnusedAttribute` warning only.
- **`drawable-v24/`** (`ic_launcher_foreground.xml`, `verse_logo_foreground.xml`) — referenced only
  from `mipmap-anydpi-v26/`, which itself only applies on API 26+. No missing-default-resource crash.
- **Java 17 source/target compatibility** — D8 desugars the language features; unrelated to `minSdk`.
- **`String.join`** (`QueryTokenizer.java:54,63`) — API 26, but also D8-backported.

---

## The choice that actually matters

At **`minSdk 27`** lint drops to exactly **two** findings — the two `readAllBytes()` calls.
Nothing else.

That gap between 23 and 27 is not just lint noise. API 23–26 carries a real UX problem lint
will not surface: `values/themes.xml` sets `android:navigationBarColor` to transparent, but
`android:windowLightNavigationBar` does not exist until API 27. On Android 6.0–8.0 in **light**
theme the nav-bar icons stay white against a light background — effectively invisible.
Supporting 23–26 means designing and verifying a second visual configuration (a scrim, or an
opaque nav bar below v27) on old emulators.

### Recommendation

Target **minSdk 26 or 27** (Android 8.0 / 8.1).

API 23–25 is roughly 2–3% of active devices combined (rough Play figures — check the Play Console
for this app's actual distribution). Buying that back costs the nav-bar workaround plus the
`Collections.sort` and `HtmlCompat` changes. At 27 the whole job is:

1. Delete the dead `VerseOfTheDay` methods (`setVerseOfDay`, `verseOfDayExists`,
   `verseOfDayIsCurrent`, `getVerseFromFile`, `getRandomVerse`) — already documented as dead in
   `CLAUDE.md`.
2. Replace the two `readAllBytes()` calls with a read loop.
3. Change `minSdk = 33` to `27` in `app/build.gradle`.

If 23 is wanted anyway, add: `Collections.sort` at three sites, `HtmlCompat.fromHtml`, move
`windowLightNavigationBar` to `values-v27/`, and solve the light-theme nav bar for 23–26.

An alternative to the manual fixes is enabling core library desugaring
(`coreLibraryDesugaringEnabled true` + `com.android.tools:desugar_jdk_libs`), which covers
`List.sort` and `java.time` — but **not** `readAllBytes`, and it adds APK size. For this few call
sites the manual fixes are simpler.

---

## Other things worth knowing

- **`targetSdk` stays 36.** Lowering `minSdk` changes nothing for users on modern devices.
- **APK size is 29 MB debug.** Assets are 15 MB: 12.6 MB Bible text (kjv 4.3M, asv 4.3M, bsb 4.0M),
  1.3 MB `similar_verses.bin`, ~1 MB red-letter JSON. This matters more on the low-storage devices
  being targeted than on modern ones. `installLocation="preferExternal"` is already set in the
  manifest; an App Bundle plus `minifyEnabled true` on release would help further.
- **`androidx.compose.ui:ui:1.8.0` and `org.jetbrains.kotlin:kotlin-stdlib` are unused** — no
  Compose imports anywhere in `app/src/main/java`. Dropping them shrinks the APK and removes two
  more version constraints.
- **Memory** — `Tools.getFile()` reads whole book files into memory and `RedLetter` holds a ~330 KB
  parsed `JSONObject` per translation in a static cache. Fine on modern devices; worth a look on an
  API 26 emulator with a small heap.
- **Verification** — needs API 26/27 emulator images. The riskiest path to smoke-test is
  `Tools.getFile()` reading assets from translation subdirectories, since `CLAUDE.md` already
  records that this path had asset-reading trouble (the `available()` + single `read()` pattern
  that `readAllBytes()` replaced). Whatever replaces `readAllBytes()` must keep handling
  compressed assets in subdirectories correctly — a plain `available()`-sized single read is the
  bug that was already fixed once.

---

## Reproducing this

```groovy
// /tmp/lowmin.gradle
gradle.beforeProject { p ->
    p.plugins.withId('com.android.application') {
        def ac = p.extensions.getByName('androidComponents')
        ac.beforeVariants(ac.selector().all()) { vb -> vb.minSdk = 27 }
    }
}
```

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:lintDebug --init-script /tmp/lowmin.gradle --rerun-tasks --continue
# report: app/build/reports/lint-results-debug.html
```

Note: setting `android.defaultConfig.minSdk` from `beforeProject` or `afterEvaluate` does **not**
work — `build.gradle` overwrites it during evaluation, and AGP locks the variant before
`afterEvaluate`. The `beforeVariants` variant API is the hook that takes effect.

---

## Google Play implications

**Play has no `minSdk` floor.** There is no minimum, no review trigger, no approval step.
Lowering it is an ordinary update.

What Play enforces is `targetSdk`, and that is already fine — 36, and unchanged by any of this
work. Relevant right now: the annual target-API requirement moves to **API 36 on Aug 31, 2026**
(about a month from the date of this investigation). Confirm in the Play Console, but the app is
ahead of it.

### What actually changes

**Device catalog grows.** The Console's supported-device count jumps substantially — Android 13+
to Android 8+ roughly triples the addressable device list. Happens automatically on the next
release.

**Android vitals is the real risk.** Old devices join the crash and ANR metrics. Play's
bad-behaviour thresholds (~1.09% user-perceived crash rate, ~0.47% ANR) affect store visibility,
and this adds a device population that has never been tested. The pre-launch report will also
start running on older hardware.

This is the strongest argument for **27 over 23**: fewer untested configurations, and it avoids
shipping the invisible light-theme nav bar (see above) to real users — which generates 1-star
reviews rather than crashes, and vitals will not catch it.

**It is a one-way door in practice.** Not policy, mechanics. Once old-device users install,
raising `minSdk` again later does not break them — it freezes them on the last compatible version.
No warning, no crash, they simply stop receiving updates.

### Build-config findings

**No `signingConfig` and no bundle config in `app/build.gradle`.** If the app is already on Play
as an APK from before Aug 2021, APK updates are still accepted; a new listing requires an App
Bundle. Note an AAB would **not** shrink the 15 MB of assets for anyone — assets are not split by
density or ABI. It would only split the four native ABIs (see next point).

**The APK does contain native code**, despite the app being pure Java:

```
lib/arm64-v8a/libandroidx.graphics.path.so
lib/armeabi-v7a/libandroidx.graphics.path.so
lib/x86/libandroidx.graphics.path.so
lib/x86_64/libandroidx.graphics.path.so
```

Checked against the **16 KB page-size requirement** (mandatory for apps targeting Android 15+
since Nov 2025):

```
$ zipalign -c -P 16 -v 4 app-debug.apk
lib/arm64-v8a/libandroidx.graphics.path.so (OK)
...
Verification successful
```

Compliant today. It arrives via `androidx.graphics:graphics-path:1.0.1`, pulled in by the unused
`androidx.compose.ui:ui:1.8.0`. **Dropping the dead Compose dependency removes native code from
the app entirely** and takes the 16 KB question off the table permanently.

**`versionCode` needs bumping** — currently `3` in `app/build.gradle`. Separately,
`AndroidManifest.xml` still declares a stale `versionCode="1" versionName="1.0"`; Gradle overrides
it so it is harmless, but it is confusing and worth deleting.

### Non-issues

- **No permissions are declared**, so moving to API 23+ raises no runtime-permission concerns
  (runtime permissions begin at 23 anyway).
- **`android:allowBackup="true"` with no `dataExtractionRules`** — an AGP lint warning
  (`MissingDataExtractionRules`), not a Play requirement. Auto Backup itself requires API 23,
  which matches the dependency floor.
- **Edge-to-edge enforcement** on `targetSdk` 35+ is already handled by the existing inset code.
