# Known Issues (cleanup / altitude)

Findings from a `/code-review` pass over the settings-UI contrast/dropdown work
(commit range `5ecc440...HEAD` at the time of writing). The correctness bugs from
that review (AlertDialog rotation leak, theme-spinner spurious-callback guard,
label/spinner overlap risk, inaccurate colorPrimary comment) were already fixed.
These are lower-severity cleanup/altitude items, left open for later.

---

## Issue #15 — BSB-only hardcoded special case in translation listener
**Status: DONE**

`SettingsActivity.java`'s translation listener gated the accuracy-warning dialog
with a single hardcoded `if (!selected.equals("bsb"))` check instead of a
data-driven flag. `ADDING_TRANSLATIONS.md` documented extending this by hand with
another `&& !selected.equals("web")` clause per future translation, meaning the
onboarding doc itself perpetuated the special case rather than generalizing it.

**What was done:**
- Added a `private static final Set<String> ALGORITHMIC_RED_LETTER_TRANSLATIONS`
  constant (currently `Set.of("bsb")`) and replaced the `equals("bsb")` check with
  `ALGORITHMIC_RED_LETTER_TRANSLATIONS.contains(selected)`
- Dialog title/message now interpolate the selected translation's display name
  instead of hardcoding "BSB", so the dialog reads correctly for any translation
  added to the set
- Renamed `bsbWarningDialog` field to `redLetterWarningDialog` since it's no
  longer BSB-specific
- Updated `ADDING_TRANSLATIONS.md`'s "Accuracy warning dialog" section to show
  adding a translation code to the `Set.of(...)` instead of hand-extending an
  `if` chain

---

## Issue #16 — Duplicate spinner shape drawables
**Status: DONE**

`app/src/main/res/drawable/spinner_box.xml` and
`app/src/main/res/drawable/spinner_popup_background.xml` were byte-for-byte
identical (same git blob hash). They were referenced separately as the
Spinner's `android:background` (wrapped in `spinner_background.xml`) and
`android:popupBackground`.

**What was done:**
- Renamed `spinner_box.xml` to `spinner_surface.xml`
- Deleted `spinner_popup_background.xml`
- Updated `spinner_background.xml`'s layer-list item and both Spinners'
  `android:popupBackground` in `settings_activity.xml` to reference
  `@drawable/spinner_surface`

---

## Issue #17 — spinner_fill/spinner_border duplicate colorSurfaceVariant/colorOutline
**Status: DONE**

`values/colors.xml` and `values-night/colors.xml` added standalone `spinner_fill`/
`spinner_border` colors for contrast. `values-night/themes.xml` already defined
`colorSurfaceVariant` (#444444) and `colorOutline` (#888888) — close in intent to
the night-mode `spinner_fill`/`spinner_border` (#3A3A3A/#8A8A8A) — but
`values/themes.xml` (light) never defined those Material3 attributes at all.

Note: an earlier version of `spinner_background.xml` (pre-dating this contrast
fix, see commit `29ad16d`) *did* reference `?attr/colorSurfaceVariant`/
`?attr/colorOutline` directly, and was deliberately switched to standalone
colors because the then-current theme values read too close to the screen
background in dark mode. So the fix here updates the *values* of
`colorSurfaceVariant`/`colorOutline` to match the already-verified-good
`spinner_fill`/`spinner_border` numbers, rather than reverting to the old
values.

**What was done:**
- `values/themes.xml`: added `colorSurfaceVariant` (#EAEAEA) and `colorOutline`
  (#9E9E9E) — previously undefined for light theme
- `values-night/themes.xml`: changed `colorSurfaceVariant`/`colorOutline` from
  #444444/#888888 to #3A3A3A/#8A8A8A (the values already verified to give good
  dark-theme contrast)
- `spinner_surface.xml`: now references `?attr/colorSurfaceVariant`/
  `?attr/colorOutline` instead of `@color/spinner_fill`/`@color/spinner_border`
- Deleted the now-unused `spinner_fill`/`spinner_border` colors from both
  `colors.xml` files
- Checked `bookmark_recyclerview_item.xml`'s `MaterialCardView` (the only other
  Material3 component touching these attributes) — it sets no `strokeWidth` and
  uses `colorSurface` for its background, so it's unaffected by this change
- Verified visually identical contrast in both light and dark theme on device

---

## Issue #18 — Redundant tint + fillColor on dropdown arrow
**Status: DONE**

`app/src/main/res/drawable/ic_dropdown_arrow.xml` set both
`android:tint="?attr/colorOnSurface"` on the vector root and a hardcoded
`android:fillColor="@android:color/white"` on its path. Tint overrode the
rendered color of all opaque pixels, so the literal fillColor value had no
visible effect and could mislead a future editor into thinking it controls the
color.

**What was done:**
- Dropped the `android:tint` attribute on the vector root
- Set the path's `android:fillColor` directly to `?attr/colorOnSurface`
- Confirmed `minSdk` is 33 (theme-attribute references in vector paths need
  API 24+) and that the drawable has no other (programmatic) consumers
- Verified visually identical chevron color in both light and dark theme on
  device

---

## Issue #19 — Parallel translations[] / translationFullNames[] arrays
**Effort: Low**

`SettingsActivity.java`'s translation spinner setup uses two parallel
`String[]` arrays (`translations`, `translationFullNames`) indexed by position,
with no check that they stay the same length/order.
`getDropDownView()` indexes `translationFullNames[position]` directly.

**Fix:** replace with a single array/list of small `Translation { code, label,
fullName }` records, or at minimum assert the two arrays' lengths match in
`onCreate()`.

---

## Issue #20 — Custom spinner chrome reimplements stock dropdown affordance
**Effort: Low**

`spinner_background.xml` hand-builds a box + chevron layer-list to give the
Spinner a "this is a dropdown" look, duplicating something the platform/Material
Spinner style already provides natively. Every future palette change now needs
to keep `ic_dropdown_arrow.xml`/`spinner_box.xml` in sync by hand instead of
inheriting it from a theme attribute.

**Fix:** evaluate switching to a stock Material `Spinner`/`ExposedDropdownMenu`
style with only color attributes overridden, if it can match the current visual
contrast requirements (Issue #17) without the custom drawables.
