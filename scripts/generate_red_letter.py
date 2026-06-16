#!/usr/bin/env python3
"""Generate per-span red-letter JSON for ASV and BSB from the KJV reference data.

See PLANS.md, Issue #14, for the design this implements. No pre-tagged
red-letter source exists for ASV/BSB, so spans are derived by aligning each
KJV red-letter span onto the target translation's verse text.

Usage: python3 scripts/generate_red_letter.py
Output: overwrites app/src/main/assets/red_letter_asv.json and
        app/src/main/assets/red_letter_bsb.json, plus a review_flags.json
        listing verses that fell back to whole-verse coloring.
"""

import json
import re
import difflib
from pathlib import Path

ASSETS = Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "assets"
SCRIPT_DIR = Path(__file__).resolve().parent

BOOKS = ["genesis.txt", "exodus.txt", "leviticus.txt", "numbers.txt", "deuteronomy.txt", "joshua.txt",
         "judges.txt", "ruth.txt", "first_samuel.txt", "second_samuel.txt", "first_kings.txt",
         "second_kings.txt", "first_chronicles.txt", "second_chronicles.txt", "ezra.txt", "nehemiah.txt",
         "esther.txt", "job.txt", "psalms.txt", "proverbs.txt", "eccliasiastes.txt", "song_of_solomon.txt",
         "isaiah.txt", "jeremiah.txt", "lamentations.txt", "ezekial.txt", "daniel.txt", "hosea.txt",
         "joel.txt", "amos.txt", "obadiah.txt", "jonah.txt", "micah.txt", "nahum.txt", "habakkuk.txt",
         "zephaniah.txt", "haggai.txt", "zechariah.txt", "malachi.txt", "matthew.txt", "mark.txt",
         "luke.txt", "john.txt", "acts.txt", "romans.txt", "first_corinthians.txt", "second_corinthians.txt",
         "galatians.txt", "ephesians.txt", "philipians.txt", "colossians.txt", "first_thesselonians.txt",
         "second_thesselonians.txt", "first_timothy.txt", "second_timothy.txt", "titus.txt", "philemon.txt",
         "hebrews.txt", "james.txt", "first_peter.txt", "second_peter.txt", "first_john.txt",
         "second_john.txt", "third_john.txt", "jude.txt", "revelation.txt"]

RED_OPEN = '<font color="#CC0000">'
RED_CLOSE = '</font>'
WORD_RE = re.compile(r"[A-Za-z']+(?:-[A-Za-z']+)*")
SEQMATCH_THRESHOLD = 0.4
QUOTE_THRESHOLD = 0.25
QUOTE_BRIDGE_MAX_GAP = 25

_verse_cache = {}


def load_book(translation, book_idx):
    key = (translation, book_idx)
    if key in _verse_cache:
        return _verse_cache[key]
    path = ASSETS / translation / BOOKS[book_idx]
    verses = {}
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.rstrip("\n")
            m = re.match(r'^(\d+):(\d+):\s*(.*)', line)
            if m:
                verses[(int(m.group(1)), int(m.group(2)))] = m.group(3)
    _verse_cache[key] = verses
    return verses


def get_verse_text(translation, book_idx, chapter, verse):
    return load_book(translation, book_idx).get((chapter, verse))


def parse_kjv_segments(html):
    """Split KJV red-letter HTML into ordered (text, is_red) segments."""
    tokens = re.split(r'(<font[^>]*>|</font>)', html)
    segments = []
    in_red = False
    for tok in tokens:
        if tok == '':
            continue
        if tok.startswith('<font'):
            in_red = True
            continue
        if tok == '</font>':
            in_red = False
            continue
        segments.append((tok, in_red))
    return segments


def words_with_flags(segments):
    """Tokenize segments into a flat word list with a parallel is_red flag list."""
    words, flags = [], []
    for text, is_red in segments:
        for w in WORD_RE.findall(text):
            words.append(w.lower())
            flags.append(is_red)
    return words, flags


def find_quote_spans(text):
    """Find smart-quote-delimited spans in BSB text, merging spans separated by short bridges."""
    spans = []
    i = 0
    n = len(text)
    while i < n:
        if text[i] == '“':
            close = text.find('”', i + 1)
            end = close if close != -1 else n
            spans.append((i, end))
            i = end + 1
        else:
            i += 1
    if not spans:
        return []
    merged = [spans[0]]
    for start, end in spans[1:]:
        prev_start, prev_end = merged[-1]
        if start - prev_end <= QUOTE_BRIDGE_MAX_GAP:
            merged[-1] = (prev_start, end)
        else:
            merged.append((start, end))
    return merged


def align_segment_to_target(seg_start, seg_end, matching_blocks):
    """Map a KJV word-index range onto target word-index space via matching blocks.

    Returns (target_ranges, matched_word_count) where target_ranges is a list
    of (start, end) word-index ranges in the target word list.
    """
    target_ranges = []
    matched = 0
    for a, b, size in matching_blocks:
        if size == 0:
            continue
        lo = max(a, seg_start)
        hi = min(a + size, seg_end)
        if lo >= hi:
            continue
        shift = b - a
        target_ranges.append((lo + shift, hi + shift))
        matched += hi - lo
    return target_ranges, matched


def build_target_flags(kjv_words, kjv_flags, target_words):
    """Run word-level SequenceMatcher and propagate red flags onto target words.

    Returns (flags, confidence_by_segment) where flags is a list of
    True/False/None (None = unresolved gap) of length len(target_words), and
    confidence_by_segment is a list of (is_red, matched, total) per KJV run.
    """
    sm = difflib.SequenceMatcher(None, kjv_words, target_words, autojunk=False)
    blocks = sm.get_matching_blocks()

    # group kjv words into contiguous runs of identical is_red flag
    runs = []
    if kjv_flags:
        run_start = 0
        for i in range(1, len(kjv_flags) + 1):
            if i == len(kjv_flags) or kjv_flags[i] != kjv_flags[run_start]:
                runs.append((run_start, i, kjv_flags[run_start]))
                run_start = i

    target_flags = [None] * len(target_words)
    confidences = []
    for start, end, is_red in runs:
        ranges, matched = align_segment_to_target(start, end, blocks)
        total = end - start
        confidences.append((is_red, matched, total))
        if is_red:
            for lo, hi in ranges:
                for j in range(lo, hi):
                    if 0 <= j < len(target_flags):
                        target_flags[j] = True
        else:
            for lo, hi in ranges:
                for j in range(lo, hi):
                    if 0 <= j < len(target_flags):
                        if target_flags[j] is None:
                            target_flags[j] = False

    return target_flags, confidences


def fill_gaps(flags):
    """Fill unresolved (None) target word flags between matched anchors.

    Only carries the red flag across a gap when both neighboring matched
    words are red — an ambiguous gap (mixed or unknown neighbors) defaults
    to non-red, since coloring narration red is a worse error than missing
    the edge of a red span.
    """
    n = len(flags)
    filled = list(flags)
    i = 0
    while i < n:
        if filled[i] is not None:
            i += 1
            continue
        j = i
        while j < n and filled[j] is None:
            j += 1
        left = filled[i - 1] if i > 0 else None
        right = filled[j] if j < n else None
        value = True if (left is True and right is True) else False
        for k in range(i, j):
            filled[k] = value
        i = j
    return filled


def red_span_confidence(confidences):
    red_matched = sum(m for is_red, m, t in confidences if is_red)
    red_total = sum(t for is_red, m, t in confidences if is_red)
    if red_total == 0:
        return 1.0
    return red_matched / red_total


def try_quote_anchoring(text, kjv_words, kjv_flags):
    """BSB-specific fallback: align red runs to smart-quote spans in the target text."""
    quote_spans = find_quote_spans(text)
    if not quote_spans:
        return None

    runs = []
    run_start = 0
    for i in range(1, len(kjv_flags) + 1):
        if i == len(kjv_flags) or kjv_flags[i] != kjv_flags[run_start]:
            runs.append((run_start, i, kjv_flags[run_start]))
            run_start = i

    red_runs = [(s, e) for s, e, is_red in runs if is_red]
    if not red_runs:
        return None

    word_spans = [(m.start(), m.end()) for m in WORD_RE.finditer(text)]
    used_quotes = set()
    char_flags = [False] * len(text)
    total_confidence = []

    for seg_start, seg_end in red_runs:
        seg_words = kjv_words[seg_start:seg_end]
        best_idx, best_ratio = None, 0.0
        for qi, (qs, qe) in enumerate(quote_spans):
            if qi in used_quotes:
                continue
            quote_words = WORD_RE.findall(text[qs:qe].lower())
            ratio = difflib.SequenceMatcher(None, seg_words, quote_words, autojunk=False).ratio()
            if ratio > best_ratio:
                best_ratio, best_idx = ratio, qi
        if best_idx is None or best_ratio < QUOTE_THRESHOLD:
            total_confidence.append(0.0)
            continue
        used_quotes.add(best_idx)
        qs, qe = quote_spans[best_idx]
        # mark the whole quote span red, not just individual word ranges, so the
        # quote marks and inter-word punctuation/spacing stay inside the span too
        for k in range(qs, qe):
            char_flags[k] = True
        total_confidence.append(best_ratio)

    if not total_confidence or min(total_confidence) < QUOTE_THRESHOLD:
        return None
    return char_flags


def reconstruct_html(text, target_words_with_flags_resolved, word_spans):
    """Insert <font> tags into text based on per-word red flags."""
    char_red = [False] * len(text)
    for (cs, ce), is_red in zip(word_spans, target_words_with_flags_resolved):
        if is_red:
            for k in range(cs, ce):
                char_red[k] = True
    # fill inter-word gaps (spaces, punctuation) when both neighboring words are red,
    # so adjacent red words merge into one continuous span instead of toggling per-word
    flags = target_words_with_flags_resolved
    for i in range(len(word_spans) - 1):
        if flags[i] and flags[i + 1]:
            for k in range(word_spans[i][1], word_spans[i + 1][0]):
                char_red[k] = True
    return apply_char_flags(text, char_red)


def apply_char_flags(text, char_red):
    out = []
    in_red = False
    for i, ch in enumerate(text):
        red_here = char_red[i] if i < len(char_red) else False
        if red_here and not in_red:
            out.append(RED_OPEN)
            in_red = True
        elif not red_here and in_red:
            out.append(RED_CLOSE)
            in_red = False
        out.append(ch)
    if in_red:
        out.append(RED_CLOSE)
    return ''.join(out)


def process_verse(ref, kjv_html, translation):
    """Returns (html, status) where status is one of:
    'full' (whole verse was red in KJV, no alignment needed),
    'aligned' (seqmatch alignment succeeded),
    'quote' (BSB quote-anchoring succeeded),
    'fallback' (whole-verse fallback), or None if target verse text missing.
    """
    parts = ref.split(':')
    book_idx, chapter, vs = int(parts[0]), int(parts[1]), int(parts[2])
    target_text = get_verse_text(translation, book_idx, chapter, vs)
    if target_text is None:
        return None, 'missing'

    segments = parse_kjv_segments(kjv_html)
    non_red_text = ''.join(t for t, is_red in segments if not is_red).strip()
    if non_red_text == '':
        return f'{RED_OPEN}{target_text}{RED_CLOSE}', 'full'

    kjv_words, kjv_flags = words_with_flags(segments)
    target_words = WORD_RE.findall(target_text.lower())
    word_spans = [(m.start(), m.end()) for m in WORD_RE.finditer(target_text)]

    target_flags, confidences = build_target_flags(kjv_words, kjv_flags, target_words)
    confidence = red_span_confidence(confidences)

    if confidence >= SEQMATCH_THRESHOLD:
        resolved = fill_gaps(target_flags)
        return reconstruct_html(target_text, resolved, word_spans), 'aligned'

    if translation == 'bsb':
        char_flags = try_quote_anchoring(target_text, kjv_words, kjv_flags)
        if char_flags is not None:
            return apply_char_flags(target_text, char_flags), 'quote'

    return f'{RED_OPEN}{target_text}{RED_CLOSE}', 'fallback'


def main():
    with open(ASSETS / 'red_letter_kjv.json', encoding='utf-8') as f:
        kjv_data = json.load(f)

    def sort_key(ref):
        b, c, v = ref.split(':')
        return (int(b), int(c), int(v))

    refs = sorted(kjv_data.keys(), key=sort_key)

    for translation in ('asv', 'bsb'):
        out = {}
        review_flags = []
        stats = {'full': 0, 'aligned': 0, 'quote': 0, 'fallback': 0, 'missing': 0}
        for ref in refs:
            html, status = process_verse(ref, kjv_data[ref], translation)
            stats[status] += 1
            if html is not None:
                out[ref] = html
            if status in ('fallback', 'missing'):
                review_flags.append({'ref': ref, 'reason': status})

        out_sorted = {ref: out[ref] for ref in refs if ref in out}
        with open(ASSETS / f'red_letter_{translation}.json', 'w', encoding='utf-8') as f:
            json.dump(out_sorted, f, ensure_ascii=False, separators=(',', ':'))

        with open(SCRIPT_DIR / f'red_letter_{translation}_review.json', 'w', encoding='utf-8') as f:
            json.dump(review_flags, f, ensure_ascii=False, indent=2)

        total = len(refs)
        precise = stats['full'] + stats['aligned'] + stats['quote']
        print(f"{translation}: {total} verses | full={stats['full']} aligned={stats['aligned']} "
              f"quote={stats['quote']} fallback={stats['fallback']} missing={stats['missing']} "
              f"| precise={precise}/{total} ({100*precise/total:.1f}%)")


if __name__ == '__main__':
    main()
