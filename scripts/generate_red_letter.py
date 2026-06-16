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
    """Find smart-quote-delimited spans in BSB text, merging spans only when a
    short narrator-tag bridge interrupts one continuous utterance.

    A bridge like '" he said, "' mid-sentence (the first quote ends with a
    comma, not terminal punctuation) signals the same speech continuing
    after the tag, so it's safe to merge. A bridge after terminal punctuation
    ('?"  "' or '."  "') signals the utterance actually ended — often a
    different speaker's turn in dialogue — so it must NOT be merged, even if
    short and attribution-shaped (e.g. "Jesus replied," can introduce a new
    speaker's quote just as easily as continue the previous one).
    """
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
        last_char = text[prev_end - 1] if prev_end > prev_start else ''
        if start - prev_end <= QUOTE_BRIDGE_MAX_GAP and last_char == ',':
            merged[-1] = (prev_start, end)
        else:
            merged.append((start, end))
    return merged


def get_runs(flags):
    """Group a flag list into contiguous (start, end, flag) runs."""
    runs = []
    if not flags:
        return runs
    run_start = 0
    for i in range(1, len(flags) + 1):
        if i == len(flags) or flags[i] != flags[run_start]:
            runs.append((run_start, i, flags[run_start]))
            run_start = i
    return runs


def match_run_to_quote(run_words, quote_spans, text, used_quotes):
    """Find the best-matching unused quote span for a run's words, by content
    similarity (robust to BSB's reordering of speech vs. narration, since it
    compares bag-of-words content rather than relying on word position)."""
    best_idx, best_ratio = None, 0.0
    for qi, (qs, qe) in enumerate(quote_spans):
        if qi in used_quotes:
            continue
        quote_words = WORD_RE.findall(text[qs:qe].lower())
        ratio = difflib.SequenceMatcher(None, run_words, quote_words, autojunk=False).ratio()
        if ratio > best_ratio:
            best_ratio, best_idx = ratio, qi
    if best_idx is not None and best_ratio >= QUOTE_THRESHOLD:
        return best_idx, best_ratio
    return None, 0.0


def match_run_to_words(run_words, target_words):
    """Locally align one KJV run's words against the full target word list.

    Returns (lo, hi, confidence): the convex hull word-index range [lo, hi)
    in target_words covering all matched words, and the fraction of the
    run's words that matched. Using a fresh SequenceMatcher per run (rather
    than one global alignment shared across the whole verse) keeps a run's
    alignment from being thrown off by how unrelated text elsewhere in the
    verse happens to match.
    """
    sm = difflib.SequenceMatcher(None, run_words, target_words, autojunk=False)
    blocks = [b for b in sm.get_matching_blocks() if b.size > 0]
    if not blocks:
        return None, None, 0.0
    matched = sum(b.size for b in blocks)
    confidence = matched / len(run_words)
    lo = min(b.b for b in blocks)
    hi = max(b.b + b.size for b in blocks)
    return lo, hi, confidence


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
    'precise' (every red run resolved via quote-matching and/or local alignment),
    'fallback' (whole-verse fallback because at least one red run couldn't be
    resolved confidently), or 'missing' if the target verse text is absent.

    Each red run is resolved independently: for BSB, a run is first matched
    against a smart-quote span by content similarity (the structurally
    reliable signal, since BSB consistently wraps direct speech in “ ” and
    this is robust to reordering); if that fails, a local SequenceMatcher
    aligns the run's own words against the full target word list and takes
    the convex hull of matched positions, coloring the whole hull (not just
    the words that happened to match) since the run is one continuous
    KJV speech act.
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

    quote_spans = find_quote_spans(target_text) if translation == 'bsb' else []
    used_quotes = set()
    char_red = [False] * len(target_text)

    for start, end, is_red in get_runs(kjv_flags):
        if not is_red:
            continue
        run_words = kjv_words[start:end]

        if quote_spans:
            qi, _ratio = match_run_to_quote(run_words, quote_spans, target_text, used_quotes)
            if qi is not None:
                used_quotes.add(qi)
                qs, qe = quote_spans[qi]
                for k in range(qs, qe):
                    char_red[k] = True
                continue

        lo, hi, confidence = match_run_to_words(run_words, target_words)
        if lo is not None and confidence >= SEQMATCH_THRESHOLD:
            cs = word_spans[lo][0]
            ce = word_spans[hi - 1][1]
            for k in range(cs, ce):
                char_red[k] = True
            continue

        return f'{RED_OPEN}{target_text}{RED_CLOSE}', 'fallback'

    return apply_char_flags(target_text, char_red), 'precise'


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
        stats = {'full': 0, 'precise': 0, 'fallback': 0, 'missing': 0}
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
        precise = stats['full'] + stats['precise']
        print(f"{translation}: {total} verses | full={stats['full']} precise={stats['precise']} "
              f"fallback={stats['fallback']} missing={stats['missing']} "
              f"| total_precise={precise}/{total} ({100*precise/total:.1f}%)")


if __name__ == '__main__':
    main()
