#!/usr/bin/env python3
"""Convert a USFM Bible download into this app's plain-text asset format.

Used to produce `assets/rvr1909/` (Spanish, Reina-Valera 1909) and `assets/cuvs/`
(Mandarin, Chinese Union Version) from the public-domain USFM releases at
ebible.org (`spaRV1909_usfm.zip`, `cmn-cu89s_usfm.zip`). Run manually; output is
committed.

    python3 scripts/convert_usfm.py <usfm-dir> <out-dir> [--report report.json]

Two things make this more than a marker strip:

1. The app addresses verses *positionally* — `Bible.getVerse()` splits the chapter
   into lines and takes `lines[verse - 1]`. So every chapter must carry the same
   number of lines, in the same order, as the KJV assets, or a reference resolved
   in one translation lands on the wrong verse in another. Chapters are therefore
   built onto the KJV grid, and any verse the source omits (the CUV drops the
   dozen verses absent from the critical text: Matthew 18:11, John 5:4, Acts 8:37,
   …) is filled rather than skipped.

2. Those omissions are usually not really absent — the CUV keeps the text in a
   footnote ("some manuscripts add: …") tagged with the verse number it belongs
   to. `harvest_footnotes()` pulls those back out so the filled line carries the
   real verse instead of a placeholder. Only the leftovers get PLACEHOLDER.

Verses the source numbers *beyond* the KJV grid (Romans 16:25-27, which the KJV
asset ends at 24; 3 John 15; Revelation 12:18) are kept and appended in order —
the ASV and BSB assets already carry the Romans doxology the same way.

The third problem is versification proper, and it only bites the RV1909, which
numbers by the Hebrew text: twelve chapters end on an *empty* verse marker
because that verse's text has moved to the front of the next chapter, pushing
the whole chapter along by one (or by five, at Job 40). The chapter still ends
level with the KJV because somewhere in it the source merges the verses it is
running ahead by back into one. Where that merge falls is not fixed — Jonah 2
merges at the end, Acts 20 at the start — and getting it wrong silently shows
the reader the neighbouring verse, so `resolve_displacement()` picks the merge
point by evidence instead: it scores every candidate on how well proper nouns
and numbers line up between the KJV verse and the Spanish one, and takes the
best. `--report` dumps those chapters verse by verse for spot-checking.
"""

import argparse
import json
import os
import re
import sys

KJV_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                       "..", "app", "src", "main", "assets", "kjv")

# Same order as Bible.books — the book index in a "book:chapter:verse" reference.
BOOKS = [
    "genesis.txt", "exodus.txt", "leviticus.txt", "numbers.txt", "deuteronomy.txt",
    "joshua.txt", "judges.txt", "ruth.txt", "first_samuel.txt", "second_samuel.txt",
    "first_kings.txt", "second_kings.txt", "first_chronicles.txt", "second_chronicles.txt",
    "ezra.txt", "nehemiah.txt", "esther.txt", "job.txt", "psalms.txt", "proverbs.txt",
    "eccliasiastes.txt", "song_of_solomon.txt", "isaiah.txt", "jeremiah.txt",
    "lamentations.txt", "ezekial.txt", "daniel.txt", "hosea.txt", "joel.txt", "amos.txt",
    "obadiah.txt", "jonah.txt", "micah.txt", "nahum.txt", "habakkuk.txt", "zephaniah.txt",
    "haggai.txt", "zechariah.txt", "malachi.txt", "matthew.txt", "mark.txt", "luke.txt",
    "john.txt", "acts.txt", "romans.txt", "first_corinthians.txt", "second_corinthians.txt",
    "galatians.txt", "ephesians.txt", "philipians.txt", "colossians.txt",
    "first_thesselonians.txt", "second_thesselonians.txt", "first_timothy.txt",
    "second_timothy.txt", "titus.txt", "philemon.txt", "hebrews.txt", "james.txt",
    "first_peter.txt", "second_peter.txt", "first_john.txt", "second_john.txt",
    "third_john.txt", "jude.txt", "revelation.txt",
]

# USFM book codes, parallel to BOOKS.
USFM_CODES = [
    "GEN", "EXO", "LEV", "NUM", "DEU", "JOS", "JDG", "RUT", "1SA", "2SA", "1KI", "2KI",
    "1CH", "2CH", "EZR", "NEH", "EST", "JOB", "PSA", "PRO", "ECC", "SNG", "ISA", "JER",
    "LAM", "EZK", "DAN", "HOS", "JOL", "AMO", "OBA", "JON", "MIC", "NAM", "HAB", "ZEP",
    "HAG", "ZEC", "MAL", "MAT", "MRK", "LUK", "JHN", "ACT", "ROM", "1CO", "2CO", "GAL",
    "EPH", "PHP", "COL", "1TH", "2TH", "1TI", "2TI", "TIT", "PHM", "HEB", "JAS", "1PE",
    "2PE", "1JN", "2JN", "3JN", "JUD", "REV",
]

# Stands in for a verse the source has neither in the text nor in a footnote. Kept
# short: it occupies a real verse slot and can be reached by a random-verse roll.
PLACEHOLDER = {
    "es": "[Este versículo no aparece en esta traducción.]",
    "zh": "[本节经文不在此译本中。]",
}

# --- USFM marker handling -------------------------------------------------

# \w gloss|strong="G1234"\w*  →  gloss   (also \+w nested inside footnotes)
CHAR_WITH_ATTRS = re.compile(r'\\\+?(?:w|rb|fig)\s+([^|\\]*?)(?:\|[^\\]*?)?\\\+?(?:w|rb|fig)\*')
# \add supplied\add* and friends — keep the text, drop the tag.
CHAR_KEEP = re.compile(r'\\\+?(?:add|pn|nd|wj|qt|tl|bk|sls|dc|em|bd|it|no|sc|ord|k|va|vp)\s*'
                       r'(.*?)\\\+?(?:add|pn|nd|wj|qt|tl|bk|sls|dc|em|bd|it|no|sc|ord|k|va|vp)\*')
# Footnotes and cross references — dropped from the verse (harvested separately).
NOTE = re.compile(r'\\(f|fe|x)\s.*?\\\1\*', re.DOTALL)
# Any remaining marker, opening or closing.
ANY_MARKER = re.compile(r'\\\+?[a-z]+\d*\*?')
WHITESPACE = re.compile(r'[ \t\u00a0\u3000]+')


def clean(text):
    """Strip USFM markup down to the readable verse text."""
    text = NOTE.sub(" ", text)
    for _ in range(4):  # nested character markers, innermost first
        text, n = CHAR_WITH_ATTRS.subn(r"\1", text)
        text, m = CHAR_KEEP.subn(r"\1", text)
        if not n and not m:
            break
    text = ANY_MARKER.sub(" ", text)
    text = WHITESPACE.sub(" ", text)
    # A space before CJK punctuation is an artifact of the markers we just removed.
    text = re.sub(r'\s+([，。；：、？！」）】])', r'\1', text)
    text = re.sub(r'([「（【])\s+', r'\1', text)
    return text.strip()


def harvest_footnotes(raw, chapter):
    """Verse text the source moved into a footnote, keyed by verse number.

    The CUV footnotes its critical-text omissions as e.g.
    `\\f - \\fr 5:3 \\ft 有古卷加：\\+fv 4\\+fv*因为有天使…\\f*` — the `\\+fv N\\+fv*`
    marks which verse the following text is. Only footnotes carrying such a marker
    are harvested; ordinary explanatory notes have none and are ignored.
    """
    found = {}
    for note in NOTE.finditer(raw):
        body = note.group(0)
        parts = re.split(r'\\\+?fv\s*(\d+)\s*\\\+?fv\*', body)
        if len(parts) < 3:
            continue
        # parts = [before, num, text, num, text, ...]
        for i in range(1, len(parts) - 1, 2):
            verse_num = int(parts[i])
            text = clean(parts[i + 1])
            # Drop the "some manuscripts add:" lead-in that precedes the number.
            text = re.sub(r'^[：:，,、\s]+', "", text)
            if text and verse_num not in found:
                found[verse_num] = text
    return found


def parse_usfm(path):
    """{chapter: {verse: text}} plus {chapter: {verse: text}} harvested from footnotes.

    Verse text runs from a `\\v` marker until the next `\\v` or `\\c`, which is what
    makes poetry work — the CUV breaks a verse across several `\\q1` lines, and the
    RV1909 psalm titles sit inside verse 1 rather than on a `\\d` line as the CUV's
    do. A `\\d` line is prepended to the verse that follows it so both read alike.
    """
    with open(path, encoding="utf-8-sig") as handle:
        lines = handle.read().split("\n")

    verses, notes = {}, {}
    chapter, verse, buf, pending_title = None, None, [], ""

    def flush():
        if chapter is None or verse is None:
            return
        raw = " ".join(buf)
        text = clean(raw)
        for num in expand(verse):
            # A merged range (\v 25-27) puts the whole merged text on every verse it
            # covers, so no slot in the grid ends up blank.
            verses.setdefault(chapter, {})[num] = text
        harvested = harvest_footnotes(raw, chapter)
        if harvested:
            notes.setdefault(chapter, {}).update(harvested)

    def expand(token):
        if "-" in token:
            first, last = token.split("-")[0], token.split("-")[-1]
            return range(int(re.sub(r'\D', "", first)), int(re.sub(r'\D', "", last)) + 1)
        return [int(re.sub(r'\D', "", token))]

    for line in lines:
        chapter_match = re.match(r'\\c\s+(\d+)', line)
        if chapter_match:
            flush()
            chapter, verse, buf, pending_title = int(chapter_match.group(1)), None, [], ""
            continue
        verse_match = re.match(r'\\v\s+([\d\-,]+)\s*(.*)', line, re.DOTALL)
        if verse_match:
            flush()
            verse, buf = verse_match.group(1), [verse_match.group(2)]
            if pending_title:
                buf.insert(0, pending_title)
                pending_title = ""
            continue
        title_match = re.match(r'\\d\s+(.*)', line)
        if title_match:
            # Psalm ascription: belongs to the verse it introduces.
            pending_title = title_match.group(1)
            continue
        if verse is not None:
            if re.match(r'\\(id|h|toc\d|mt\d|ms\d|s\d?|r|sp|ide|rem|cl|cp|b)\b', line.strip()):
                continue  # headings and metadata are not verse text
            buf.append(line)

    flush()
    return verses, notes


# --- Hebrew-versification displacement ------------------------------------

ACCENTS = str.maketrans("áàâäãéèêëíìîïóòôöõúùûüñç", "aaaaaeeeeiiiiooooouuuunc")


def anchors(text):
    """Tokens that survive translation: numbers, and the stems of proper nouns.

    Four characters is enough to bridge Jehová/Jehovah, Nínive/Nineveh and
    Sodoma/Sodom while still telling David from Daniel.
    """
    found = set(re.findall(r'\d+', text))
    for word in re.findall(r'\b[A-ZÁÉÍÓÚÑÜ][\wáéíóúñü]{3,}', text):
        found.add(word.lower().translate(ACCENTS)[:4])
    return found


def score_pair(kjv_text, src_text):
    """How much two verses look like the same verse in different languages."""
    kjv_anchors, src_anchors = anchors(kjv_text), anchors(src_text)
    shared = len(kjv_anchors & src_anchors)
    longer = max(len(kjv_text), len(src_text)) or 1
    ratio = 1.0 - abs(len(kjv_text) - len(src_text)) / longer
    return shared * 2.0 + ratio


def resolve_displacement(displaced_kjv, chapter_kjv, source, shift=None):
    """Map a shifted chapter's source verses back onto the KJV numbering.

    `displaced_kjv` is the text of the previous chapter's trailing verses as the
    KJV numbers them (their slots are empty in the source), `chapter_kjv` the KJV
    text of this chapter, `source` the source's verses for it. The source runs
    ahead by `shift` verses and gives that lead back at one merge point; every
    possible merge point is scored and the best-fitting one wins.

    `shift` defaults to the number of displaced verses. It is passed explicitly
    for a book's *last* chapter (2 Corinthians 13), where there is no next
    chapter to have taken the text and the merge is inside the chapter itself.

    Returns (texts for the displaced slots, {verse -> text} for this chapter).
    """
    if shift is None:
        shift = len(displaced_kjv)
    src = [source[n] for n in sorted(source)]
    # The KJV verses these source verses have to cover, in order.
    targets = displaced_kjv + chapter_kjv
    if shift == 0 or len(src) < shift or not targets:
        return [], {}

    best_at, best_score = None, float("-inf")
    for merge_at in range(len(src)):
        # src[merge_at] absorbs shift + 1 KJV verses; every other source verse
        # takes one, so the two sequences end level.
        total, target_index = 0.0, 0
        for i, text in enumerate(src):
            span = shift + 1 if i == merge_at else 1
            chunk = " ".join(targets[target_index:target_index + span])
            total += score_pair(chunk, text)
            target_index += span
        if target_index == len(targets) and total > best_score:
            best_at, best_score = merge_at, total

    if best_at is None:
        return [], {}

    # Lay the source verses back down over the KJV slots. A merged source verse
    # is repeated across every slot it covers, so none of them come out blank.
    resolved, target_index = [], 0
    for i, text in enumerate(src):
        span = shift + 1 if i == best_at else 1
        for _ in range(span):
            resolved.append(text)
            target_index += 1
    return resolved[:shift], {n + 1: t for n, t in enumerate(resolved[shift:])}


def kjv_book(book_file):
    """{chapter: {verse: text}} for a book as the KJV assets number it."""
    text = {}
    with open(os.path.join(KJV_DIR, book_file), encoding="utf-8") as handle:
        for line in handle:
            match = re.match(r'^(\d+):(\d+): ?(.*)', line)
            if match:
                chapter, verse = int(match.group(1)), int(match.group(2))
                text.setdefault(chapter, {})[verse] = match.group(3).strip()
    return text


def convert_book(usfm_path, book_file, lang, report):
    verses, notes = parse_usfm(usfm_path)
    kjv = kjv_book(book_file)
    grid = {c: max(v) for c, v in kjv.items()}
    chapters = sorted(set(grid) | set(verses))
    lines = []

    for chapter in chapters:
        source = {n: t for n, t in verses.get(chapter, {}).items() if t.strip()}

        # Trailing slots the source left empty mean this chapter's tail was
        # renumbered into the next one — pull it back before anything else.
        empty_tail = []
        for verse in range(grid.get(chapter, 0), 0, -1):
            if verse in verses.get(chapter, {}) and not verses[chapter][verse].strip():
                empty_tail.insert(0, verse)
            else:
                break
        if empty_tail and chapter + 1 in verses:
            displaced_kjv = [kjv[chapter][v] for v in empty_tail]
            chapter_kjv = [kjv[chapter + 1][v] for v in sorted(kjv.get(chapter + 1, {}))]
            next_source = {n: t for n, t in verses[chapter + 1].items() if t.strip()}
            recovered, realigned = resolve_displacement(displaced_kjv, chapter_kjv, next_source)
            if recovered:
                for verse, text in zip(empty_tail, recovered):
                    source[verse] = text
                verses[chapter + 1] = realigned
                report["realigned"].append(
                    f"{book_file} {chapter}:{empty_tail[0]}-{empty_tail[-1]} "
                    f"reclaimed from chapter {chapter + 1}")
        elif empty_tail:
            # Last chapter of the book: the text never left, the chapter just runs
            # one verse short of the KJV because it merged two of them internally.
            chapter_kjv = [kjv[chapter][v] for v in sorted(kjv.get(chapter, {}))]
            _, realigned = resolve_displacement([], chapter_kjv, source, shift=len(empty_tail))
            if realigned:
                source = realigned
                report["realigned"].append(
                    f"{book_file} {chapter}:{empty_tail[0]}-{empty_tail[-1]} "
                    f"realigned within the chapter")

        highest = max([grid.get(chapter, 0)] + list(source))
        for verse in range(1, highest + 1):
            text = source.get(verse, "")
            if not text:
                recovered = notes.get(chapter, {}).get(verse, "")
                if recovered:
                    text = recovered
                    report["recovered"].append(f"{book_file} {chapter}:{verse}")
                else:
                    text = PLACEHOLDER[lang]
                    report["placeheld"].append(f"{book_file} {chapter}:{verse}")
            if verse > grid.get(chapter, 0):
                report["beyond_kjv"].append(f"{book_file} {chapter}:{verse}")
            lines.append(f"{chapter}:{verse}: {text}")

    return lines


def review_sample(out_dir, realigned):
    """KJV verse beside the converted one for every realigned chapter.

    These are the only verses whose slot was decided by a heuristic rather than by
    the source's own numbering, so they are the ones worth reading before trusting
    the output. Written into the report, not read by the app.
    """
    sample = {}
    for note in realigned:
        book_file, ref = note.split()[0], note.split()[1]
        chapter = int(ref.split(":")[0])
        kjv = kjv_book(book_file)
        converted = {}
        with open(os.path.join(out_dir, book_file), encoding="utf-8") as handle:
            for line in handle:
                match = re.match(r'^(\d+):(\d+): ?(.*)', line)
                if match and int(match.group(1)) in (chapter, chapter + 1):
                    converted[(int(match.group(1)), int(match.group(2)))] = match.group(3).strip()
        for (c, v), text in sorted(converted.items()):
            sample[f"{book_file} {c}:{v}"] = [kjv.get(c, {}).get(v, ""), text]
    return sample


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("usfm_dir")
    parser.add_argument("out_dir")
    parser.add_argument("--lang", required=True, choices=sorted(PLACEHOLDER),
                        help="language of the placeholder line for absent verses")
    parser.add_argument("--header", help="JSON file of book_file -> display name for the "
                                         "human-readable first line of each asset")
    parser.add_argument("--report", help="write a JSON conversion report here")
    args = parser.parse_args()

    headers = {}
    if args.header:
        with open(args.header, encoding="utf-8") as handle:
            headers = json.load(handle)

    available = os.listdir(args.usfm_dir)
    os.makedirs(args.out_dir, exist_ok=True)
    report = {"recovered": [], "placeheld": [], "beyond_kjv": [], "realigned": [],
              "missing_books": []}

    for book_file, code in zip(BOOKS, USFM_CODES):
        matches = [f for f in available
                   if f.endswith(".usfm") and re.match(r'^\d+-' + code + r'\D', f)]
        if not matches:
            report["missing_books"].append(book_file)
            print(f"!! no USFM file for {code} ({book_file})", file=sys.stderr)
            continue

        lines = convert_book(os.path.join(args.usfm_dir, matches[0]), book_file,
                             args.lang, report)
        header = headers.get(book_file, book_file.replace(".txt", "").replace("_", " ").upper())
        with open(os.path.join(args.out_dir, book_file), "w", encoding="utf-8") as out:
            out.write(header + "\n")
            out.write("\n".join(lines) + "\n")

    if report["realigned"]:
        report["realigned_sample"] = review_sample(args.out_dir, report["realigned"])

    print(f"wrote {len(BOOKS) - len(report['missing_books'])} books to {args.out_dir}")
    print(f"  recovered from footnotes: {len(report['recovered'])}")
    print(f"  placeholder lines:        {len(report['placeheld'])}")
    print(f"  verses beyond KJV grid:   {len(report['beyond_kjv'])}")
    print(f"  chapters realigned:       {len(report['realigned'])}")
    if args.report:
        with open(args.report, "w", encoding="utf-8") as out:
            json.dump(report, out, ensure_ascii=False, indent=1)


if __name__ == "__main__":
    main()
