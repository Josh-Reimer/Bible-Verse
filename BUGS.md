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
**Effort: Low**

`app/src/main/res/drawable/spinner_box.xml` and
`app/src/main/res/drawable/spinner_popup_background.xml` are byte-for-byte
identical (same git blob hash). They're referenced separately as the Spinner's
`android:background` (wrapped in `spinner_background.xml`) and
`android:popupBackground`.

**Fix:** delete one and point both references at the same file, or rename
`spinner_box.xml` to something shared like `spinner_surface.xml` and use it in
both places.

---

## Issue #17 — spinner_fill/spinner_border duplicate colorSurfaceVariant/colorOutline
**Effort: Medium**

`values/colors.xml` and `values-night/colors.xml` add new `spinner_fill`/
`spinner_border` colors for contrast. `values-night/themes.xml` already defines
`colorSurfaceVariant` (#444444) and `colorOutline` (#888888) — close in intent to
the new night-mode `spinner_fill`/`spinner_border` (#3A3A3A/#8A8A8A) — but
`values/themes.xml` (light) never defines those Material3 attributes at all.

**Fix:** define `colorSurfaceVariant`/`colorOutline` for light theme too, and
have `spinner_box.xml` reference `?attr/colorSurfaceVariant`/`?attr/colorOutline`
directly instead of the new standalone colors — fixes the gap app-wide instead
of just for spinners.

---

## Issue #18 — Redundant tint + fillColor on dropdown arrow
**Effort: Low**

`app/src/main/res/drawable/ic_dropdown_arrow.xml` sets both
`android:tint="?attr/colorOnSurface"` on the vector root and a hardcoded
`android:fillColor="@android:color/white"` on its path. Tint overrides the
rendered color of all opaque pixels, so the literal fillColor value has no
visible effect and could mislead a future editor into thinking it controls the
color.

**Fix:** drop the tint attribute and set `fillColor="?attr/colorOnSurface"`
directly, or drop the fillColor override and leave a comment that tint is the
only thing that matters.

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
