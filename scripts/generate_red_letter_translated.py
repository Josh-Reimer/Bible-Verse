#!/usr/bin/env python3
"""Generate red-letter JSON for the Spanish and Mandarin translations.

Companion to generate_red_letter.py, which derives the ASV and BSB spans by
diffing their wording against the KJV's word by word. That cannot work here —
there is no word-level correspondence to find between English and Spanish, let
alone between English and Chinese — so this script uses the KJV data only for
*which verses* contain words of Christ and *roughly where in the verse* they
start, and finds the actual boundary using a marker the target text carries
itself:

  whole-verse   The KJV span covers the entire verse (1403 of the 2027 entries),
                so no boundary has to be found at all: the whole target verse is
                the words of Christ. This is the majority case and is exact.

  CUV           The Chinese Union Version punctuates direct speech explicitly
                with 「…」 (and “…” in places), which 97% of the partial verses
                carry. Those spans are the quotation, so they are what turns red.

  RV1909        Spanish has no such marks, but the RV1909 introduces speech with
                a colon — "Y Jesús le dijo: …" — and 98% of the partial verses
                have one. For the 519 verses whose KJV span runs to the end of
                the verse (the overwhelmingly common shape), everything after the
                first colon is the quotation.

Anything left over falls back to colouring the whole verse, which over-colours
the narration around the speech but never leaves the words of Christ black. Those
verses are listed in the review files for spot-checking.

Usage: python3 scripts/generate_red_letter_translated.py
Output: overwrites app/src/main/assets/red_letter_rvr1909.json and
        red_letter_cuvs.json, plus red_letter_rvr1909_review.json and
        red_letter_cuvs_review.json (not read by the app).
"""

import json
import re
from pathlib import Path

ASSETS = Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "assets"
SCRIPT_DIR = Path(__file__).resolve().parent

RED_OPEN = '<font color="#CC0000">'
RED_CLOSE = '</font>'

# A KJV span covering this much of the verse is treated as covering all of it —
# the remainder is a trailing quotation mark or a stray word of narration.
WHOLE_VERSE = 0.97

# Paired marks the CUV uses for direct speech. Outermost pairs only; 『…』 nests
# inside 「…」 and is already covered by the span around it.
QUOTE_PAIRS = [("「", "」"), ("“", "”")]

# The RV1909's placeholder for a verse it does not carry — never coloured.
PLACEHOLDER_PREFIX = "["

BOOKS = json.loads((SCRIPT_DIR / "books.json").read_text(encoding="utf-8")) \
    if (SCRIPT_DIR / "books.json").exists() else None


def load_books():
    """Book filenames in Bible.books order, shared with the USFM converter."""
    import convert_usfm
    return convert_usfm.BOOKS


def load_translation(code, books):
    """{(book_index, chapter, verse): text} for a whole translation."""
    verses = {}
    for book_index, book_file in enumerate(books):
        path = ASSETS / code / book_file
        with path.open(encoding="utf-8") as handle:
            for line in handle:
                match = re.match(r'^(\d+):(\d+): ?(.*)', line)
                if match:
                    key = (book_index, int(match.group(1)), int(match.group(2)))
                    verses[key] = match.group(3).strip()
    return verses


def escape(text):
    """The JSON values are parsed by Html.fromHtml, so bare markup characters bite."""
    return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


def kjv_spans(markup):
    """(spans, plain text) for a red-letter entry."""
    spans = re.findall(re.escape(RED_OPEN) + "(.*?)" + re.escape(RED_CLOSE), markup, re.S)
    plain = markup.replace(RED_OPEN, "").replace(RED_CLOSE, "")
    return spans, plain


def span_shape(markup, spans, plain):
    """How the KJV span sits in its verse: whole, suffix, prefix, middle or multi."""
    if not spans:
        return "none"
    if len("".join(spans)) / max(len(plain), 1) >= WHOLE_VERSE:
        return "whole"
    if len(spans) > 1:
        return "multi"
    if markup.endswith(RED_CLOSE) and not markup.startswith(RED_OPEN):
        return "suffix"
    if markup.startswith(RED_OPEN) and not markup.endswith(RED_CLOSE):
        return "prefix"
    return "middle"


def whole_verse(text):
    return RED_OPEN + escape(text) + RED_CLOSE


def quoted_regions(text):
    """Character ranges of the outermost quotation marks, in order.

    Speech that carries across several verses leaves each middle verse with an
    unbalanced mark — an opener with no closer in the last verse of the quote's
    first half, or a closer with no opener where it resumes. An unclosed opener
    therefore runs to the end of the verse and an unopened closer from its start,
    which is what those verses mean.
    """
    regions = []
    for opener, closer in QUOTE_PAIRS:
        depth, start = 0, None
        for i, char in enumerate(text):
            if char == opener:
                if depth == 0:
                    start = i
                depth += 1
            elif char == closer:
                if depth:
                    depth -= 1
                    if depth == 0 and start is not None:
                        regions.append((start, i + 1))
                        start = None
                elif not regions and start is None:
                    # Closes a quotation opened in an earlier verse.
                    regions.append((0, i + 1))
        if depth and start is not None:
            regions.append((start, len(text)))
    return sorted(regions)


def colour_regions(text, regions):
    """Wrap the given ranges in red, escaping everything as it goes."""
    out, cursor = [], 0
    for start, end in regions:
        out.append(escape(text[cursor:start]))
        out.append(RED_OPEN + escape(text[start:end]) + RED_CLOSE)
        cursor = end
    out.append(escape(text[cursor:]))
    return "".join(out)


def cuv_markup(text, shape):
    """Chinese: the quotation marks are the boundary, whatever the KJV span shape."""
    regions = quoted_regions(text)
    if not regions:
        return None
    # A quotation running the length of the verse is just a whole-verse span.
    covered = sum(end - start for start, end in regions)
    if covered / max(len(text), 1) >= WHOLE_VERSE:
        return whole_verse(text)
    return colour_regions(text, regions)


def rvr_markup(text, shape):
    """Spanish: for speech that runs to the end of the verse, the colon is the boundary."""
    if shape != "suffix":
        return None
    colon = text.find(":")
    if colon == -1 or colon >= len(text) - 2:
        return None
    start = colon + 1
    while start < len(text) and text[start] == " ":
        start += 1
    # A colon in the last fifth of the verse is punctuation inside the narration
    # rather than the one introducing the speech; don't trust it.
    if (len(text) - start) / max(len(text), 1) < 0.2:
        return None
    return colour_regions(text, [(start, len(text))])


TARGETS = [
    ("rvr1909", rvr_markup),
    ("cuvs", cuv_markup),
]


def main():
    books = load_books()
    kjv_red = json.loads((ASSETS / "red_letter_kjv.json").read_text(encoding="utf-8"))

    for code, markup_for in TARGETS:
        verses = load_translation(code, books)
        output, review = {}, {"fallback_whole_verse": [], "no_target_verse": [],
                              "placeholder": [], "counts": {}}
        counts = {"whole": 0, "anchored": 0, "fallback": 0, "skipped": 0}

        for ref, markup in kjv_red.items():
            book_index, chapter, verse = (int(part) for part in ref.split(":"))
            text = verses.get((book_index, chapter, verse))
            if not text:
                review["no_target_verse"].append(ref)
                counts["skipped"] += 1
                continue
            if text.startswith(PLACEHOLDER_PREFIX):
                review["placeholder"].append(ref)
                counts["skipped"] += 1
                continue

            spans, plain = kjv_spans(markup)
            shape = span_shape(markup, spans, plain)

            if shape == "whole":
                output[ref] = whole_verse(text)
                counts["whole"] += 1
                continue

            rendered = markup_for(text, shape)
            if rendered:
                output[ref] = rendered
                counts["anchored"] += 1
            else:
                output[ref] = whole_verse(text)
                counts["fallback"] += 1
                review["fallback_whole_verse"].append(f"{ref} ({shape})")

        review["counts"] = counts
        (ASSETS / f"red_letter_{code}.json").write_text(
            json.dumps(output, ensure_ascii=False, indent=0), encoding="utf-8")
        (SCRIPT_DIR / f"red_letter_{code}_review.json").write_text(
            json.dumps(review, ensure_ascii=False, indent=1), encoding="utf-8")

        total = len(output)
        exact = counts["whole"] + counts["anchored"]
        print(f"{code}: {total} verses — {counts['whole']} whole-verse, "
              f"{counts['anchored']} anchored, {counts['fallback']} fell back "
              f"({exact / max(total, 1):.1%} placed), {counts['skipped']} skipped")


if __name__ == "__main__":
    main()
