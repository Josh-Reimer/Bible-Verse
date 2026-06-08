# Issue Plans

## Issue #13 — Show book name + chapter in VerseLookUpActivity
**Status: DONE**

**What was done:**
- Added `MaterialToolbar` to `verse_lookup_activity.xml` above the `ScrollView`
- Called `setSupportActionBar()` + `setDisplayHomeAsUpEnabled(true)` in `VerseLookUpActivity.onCreate()`
- Added `onSupportNavigateUp()` to finish the activity on back arrow tap
- Added `WindowCompat.setDecorFitsSystemWindows(getWindow(), false)` for edge-to-edge
- Applied status bar top inset to toolbar via `ViewCompat.setOnApplyWindowInsetsListener`
- Applied nav bar bottom inset to ScrollView so content clears the transparent navigation bar
- Added `24dp` padding to the content `LinearLayout`

---

## Issue #6 — Fix fuzzy icon
**Effort: Low**

The `mipmap-anydpi-v26/` folder uses vector drawables (crisp at any size), but pre-API 26 devices fall back to the `.webp` rasters in `mipmap-hdpi/` etc. — these appear to be the unmodified default Android template icons, not your `verse_logo_foreground.xml`.

**Fix:**
1. In Android Studio → right-click `res/` → New → Image Asset
2. Select "Launcher Icons (Adaptive and Legacy)", use `verse_logo_foreground.xml` as the foreground layer
3. Regenerate all mipmap `.webp` files — this replaces the defaults with your actual design at the correct density

---

## Issue #7 — Words of Christ in red (JSON + HTML)
**Effort: High**

This requires two independent things:
1. **Format change**: switching from plain `.txt` assets to JSON (one object per verse, keyed by `"bookIndex:chapter:verse"`)
2. **Red-letter data**: a separate dataset tagging which verse ranges are spoken by Jesus — this data must be sourced (e.g. public-domain red-letter KJV datasets)

**Recommended approach (phased):**
- **Phase A**: Convert the 66 `.txt` files to JSON and update `Bible.java` to parse via `JSONObject` instead of `BufferedReader`. `VerseLookUpActivity` renders via `Html.fromHtml()` on each `TextView`.
- **Phase B**: Overlay the red-letter tag data — wrap tagged words in `<font color='red'>...</font>` during a one-time preprocessing step, baking the HTML into the JSON values.

Defer until #13 and #6 are done; it's the largest scope change and touches every layer.

---

## Issue #12 — Other Bible translations
**Effort: High**

Requires sourcing translation files (must be public domain — KJV is already there; good candidates: ASV, WEB, YLT), storing a translation preference, and threading it through every asset load.

**Fix:**
1. Add translation asset directories, e.g. `assets/kjv/genesis.txt`, `assets/asv/genesis.txt`
2. Add `"translation"` key to `SharedPreferences("settings")` (default `"kjv"`)
3. Add a `Spinner` or `AlertDialog` selector to `SettingsActivity`
4. Update `Tools.getFile()` to prepend the translation folder — one change propagates everywhere

Recommend starting with one additional translation (ASV) as a proof of concept before adding more.

---

## Suggested order
#13 → #6 → #12 → #7
