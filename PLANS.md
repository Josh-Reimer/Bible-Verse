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

## Issue #14 — Per-span red-letter alignment for ASV and BSB
**Status: DONE**

**What was done:**
- `scripts/generate_red_letter.py` implements the algorithm below and overwrites `app/src/main/assets/red_letter_asv.json` / `red_letter_bsb.json`
- Final coverage: ASV 99.6% precise (1403 full + 616 aligned, 8 whole-verse fallback), BSB 98.4% precise (1403 full + 592 aligned, 32 fallback)
- Fixed two bugs found during initial validation: (1) inter-word gaps (spaces/punctuation) weren't inheriting the red flag, causing every red word to render as its own separate `<font>` span instead of one continuous block; (2) ambiguous gap-filling originally split 50/50 at the midpoint, which let narration bleed into red spans (e.g. "Yes" or "Jesus replied," getting colored) — changed to default ambiguous gaps to non-red, only carrying red across a gap when both neighbors are confidently red.
- **Second round (after user reported incomplete/cut-off coloring inside BSB quotes):** root cause was a single global `difflib.SequenceMatcher` aligning the whole KJV word list against the whole target word list in one pass — fragile whenever BSB reorders speech vs. narration, which misplaced matched blocks and silently dropped trailing words. Rewrote `process_verse` to resolve each red run independently: try BSB quote-boundary matching first (bag-of-words ratio against `"..."` spans, robust to reordering), then a *local* `SequenceMatcher` per run (the run's own words vs. the full target word list, taking the convex hull of matched positions) as fallback. This removed the old global-alignment/gap-filling machinery entirely (`align_segment_to_target`, `build_target_flags`, `fill_gaps`, `try_quote_anchoring`, `reconstruct_html` all deleted).
- Also fixed a quote-merging regression surfaced by the rewrite: adjacent `"..."` spans were merged whenever the character gap between them was short, which incorrectly fused separate speakers' quotes across a complete utterance (e.g. BSB John 21:15-17, where Peter's reply was absorbed into Jesus's red span). Fixed by only merging when the first quote ends in a comma rather than terminal punctuation — a comma signals the same speech continuing past a narrator tag ("... he said, "), terminal punctuation signals the utterance actually ended.
- Validated: 0 tag-balance failures, 0 round-trip text-mismatch failures, per-book red-word ratios track the KJV baseline within tolerance (0 books flagged at a 15% deviation threshold). Of the 10 known multi-span KJV verses, 3 show only 1 span in one or both target translations (`41:8:45`, `65:1:11` both ASV/BSB; `43:1:4` BSB) — confirmed by direct text comparison these are genuine translation/versification differences (e.g. Revelation 1:11's "I am Alpha and Omega" clause exists only in the KJV's Textus Receptus source text, not in ASV/BSB's underlying Greek text), not alignment bugs.
- Review artifacts (`scripts/red_letter_asv_review.json`, `red_letter_bsb_review.json`) list every fallback verse with a reason, for future manual spot-checking — these are dev tooling output, not shipped in `assets/`.
- No Android app code changes needed — `RedLetter.java` already loaded `red_letter_<translation>.json` generically.

**Effort: High**

**Status quo:** `red_letter_asv.json` and `red_letter_bsb.json` currently wrap entire verses in `<font color="#CC0000">` whenever any part of the KJV verse is red-letter (2019 verses each). This over-colors narrative framing like "And Jesus answering said unto him," that isn't actually Christ's words.

**Why precise spans are hard:** no pre-tagged red-letter source exists for ASV or BSB anywhere (confirmed via web research — OpenScriptures, eBible, CrossWire's own ASV SWORD module all lack `<q who="Jesus">`/`\wj` markup; the ASV was never published as a red-letter edition). The only authoritative per-span source is the CrossWire KJV OSIS XML, already baked into `red_letter_kjv.json`. So precise ASV/BSB spans must be derived algorithmically from the KJV spans, not sourced directly.

**Approach — offline Python script, run once, output committed as assets:**

1. **Parse KJV reference spans.** For each of the 2027 `red_letter_kjv.json` entries, split the HTML on `<font>`/`</font>` via regex into ordered `(text_segment, is_red)` tuples. Classify each verse: fully red (1403, 69%), single partial span (614, 30%), or multi-span (10, 0.5%).

2. **Locate red span(s) in the KJV plain verse text.** Read the matching line from `kjv/<book>.txt` (regex `^(\d+):(\d+):\s*(.*)`, don't assume a fixed header offset — 13 books start directly at `1:1:` with no header). Find each red segment's word-index range by matching its first few words against the verse's tokenized word list.

3. **Align red word-ranges to ASV/BSB.** Tokenize both KJV and target verse text into lowercase word lists, run `difflib.SequenceMatcher.get_matching_blocks()`, and map the KJV red word-range onto the target via overlapping matched blocks. Accept if confidence (matched words / total red words) ≥ 0.4. Validated on real data: 98.9% of ASV partial/multi cases align this way.

4. **BSB-specific fallback: quote anchoring.** BSB paraphrases more heavily and often inverts speech/narration order, dropping SequenceMatcher confidence. When step 3 fails, extract `"..."` smart-quote spans from the BSB verse (merging adjacent quotes split by short narrator tags, e.g. `" he said, "`), then match red-span words against quote-span words with a lower threshold (≥0.25, since legitimate paraphrasing reduces word overlap even within real quotes). Recovers 89 of 126 otherwise-failing BSB cases, bringing BSB precise coverage to 97.8%.

5. **Reconstruct HTML.** Tokenize the target text into `(word, char_start, char_end)`, mark accepted red ranges in a boolean array over character positions, and walk the string inserting `<font>`/`</font>` at false→true / true→false transitions. Fully-red verses skip alignment entirely — just wrap the whole target text (correct by construction).

6. **Fallback on alignment failure.** If no strategy reaches threshold, fall back to whole-verse red (today's behavior) rather than omitting the verse — a missing entry is worse UX than mild over-coloring. Log every fallback (key + reason) to a separate `review_flags.json` for manual spot-checking. Expected fallback rate: ~0.7% ASV, ~1.8% BSB.

7. **Validate before shipping.** Round-trip check: every emitted red span's plain text must be a contiguous substring of the target verse (catches alignment bugs that scramble word order — hard fail, not a quality tradeoff). Word-count sanity: red-word ratio per book should track the KJV's own ~45% baseline; large per-book deviations flag systemic problems. Unit-test all 10 known multi-span KJV verses explicitly per translation. Manually eyeball a random sample of "precise" (non-fallback) results, since high confidence scores can still produce slightly-off boundaries. Ship only if fallback rate stays under ~2-3% and round-trip check passes 100%.

8. **Output.** Overwrite `red_letter_asv.json` / `red_letter_bsb.json` in `app/src/main/assets/`, same key format (`"bookIndex:chapter:verse"`) and same `<font color="#CC0000">` markup as today — a drop-in replacement.

**Android app changes:** none. `RedLetter.java` already loads `red_letter_<translation>.json` generically based on the `"translation"` SharedPreference and renders via `Html.fromHtml()` — it doesn't care whether spans are whole-verse or precise.

**Critical files:**
- `app/src/main/assets/red_letter_kjv.json` — alignment source
- `app/src/main/assets/kjv/*.txt`, `asv/*.txt`, `bsb/*.txt` — verse text (64 books each)
- `app/src/main/assets/red_letter_asv.json`, `red_letter_bsb.json` — generation output (current whole-verse versions to be overwritten)
- `app/src/main/java/com/verse/of/the/day/RedLetter.java` — confirms the JSON contract the script must produce; no edits needed

---

## Suggested order
#13 → #6 → #12 → #7 → #14
