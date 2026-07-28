#!/usr/bin/env python3
"""Generate assets/similar_verses.bin — a precomputed nearest-neighbor table.

For every verse we embed its text with a sentence-transformer and store the top-K
most semantically similar *other* verses. The retrieval corpus (the whole Bible)
never changes, so this is computed once, offline, and committed — the app does a
plain table lookup at runtime (no model, no NPU). See CLAUDE.md's note on
generate_red_letter.py for the sibling pattern.

Every bundled translation is embedded separately and a candidate's score is the
*best* it achieves in any of them, so a pairing only one translation's wording
makes obvious still surfaces. Results are stored as translation-independent
"book:chapter:verse" references (KJV verse numbering is canonical); the app
renders each neighbor in whatever translation the user currently has selected.

Verses in the *same chapter* as the query are excluded as candidates — they are
usually near-duplicates of the verse the user is already reading, so surfacing
them as "similar verses" is useless; we want cross-references from elsewhere.

Binary format (big-endian, matched by SimilarVerses.java via DataInputStream):
    uint32  count
    uint8   K                                            # neighbors per verse
    repeated count times:
        uint8 book, uint8 chapter, uint8 verse           # the target verse
        K * (uint8 book, uint8 chapter, uint8 verse)      # neighbors, best first
    A neighbor slot of (0,0,0) is a sentinel for "no neighbor" (chapters and
    verses are 1-based, so it never collides with a real reference).

Run:  scripts/.venv-embed/bin/python scripts/generate_similar_verses.py
"""

import hashlib
import json
import struct
from pathlib import Path

import numpy as np
import torch
from sentence_transformers import SentenceTransformer
from sklearn.feature_extraction.text import TfidfVectorizer

# Must match Bible.books[] order exactly — book index is the 0-based position here.
BOOKS = [
    "genesis", "exodus", "leviticus", "numbers", "deuteronomy", "joshua", "judges",
    "ruth", "first_samuel", "second_samuel", "first_kings", "second_kings",
    "first_chronicles", "second_chronicles", "ezra", "nehemiah", "esther", "job",
    "psalms", "proverbs", "eccliasiastes", "song_of_solomon", "isaiah", "jeremiah",
    "lamentations", "ezekial", "daniel", "hosea", "joel", "amos", "obadiah", "jonah",
    "micah", "nahum", "habakkuk", "zephaniah", "haggai", "zechariah", "malachi",
    "matthew", "mark", "luke", "john", "acts", "romans", "first_corinthians",
    "second_corinthians", "galatians", "ephesians", "philipians", "colossians",
    "first_thesselonians", "second_thesselonians", "first_timothy", "second_timothy",
    "titus", "philemon", "hebrews", "james", "first_peter", "second_peter",
    "first_john", "second_john", "third_john", "jude", "revelation",
]

MODEL_NAME = "sentence-transformers/all-mpnet-base-v2"
K = 10  # neighbors stored per verse
POOL = K * 10  # candidates ranked per verse before the one-per-chapter cap trims them to K

# Every translation bundled in assets/. KJV is first and canonical: its verse list defines the
# rows of the table (the others carry a few verses KJV numbers differently, which we ignore).
TRANSLATIONS = ["kjv", "asv", "bsb"]

# We want *thematically* similar verses that a keyword/text search would NOT surface, so we
# score each candidate as (semantic cosine) - LEXICAL_PENALTY * (lexical cosine) and hard-drop
# any candidate sharing too much distinctive vocabulary. Lexical similarity is TF-IDF cosine,
# so shared *rare* words (proper nouns, stock phrases like "the LORD spake unto Moses") — the
# very things text search keys on — are penalized hardest. Raise these to make results vaguer.
LEXICAL_PENALTY = 0.45  # weight subtracted for word-overlap; higher => broader/less obvious
LEXICAL_MAX = 0.28      # candidates above this TF-IDF cosine are excluded outright

REPO = Path(__file__).resolve().parent.parent
ASSETS = REPO / "app" / "src" / "main" / "assets"
OUT_BIN = ASSETS / "similar_verses.bin"
OUT_REVIEW = REPO / "scripts" / "similar_verses_review.json"
# Embedding the whole Bible three times costs ~25 minutes; the vectors only depend on the
# model and the verse text, so cache them (git-ignored) and make knob-tuning re-runs cheap.
CACHE = REPO / "scripts" / ".cache-embed"


def load_verses(translation):
    """Return (refs, texts) for one translation: refs is a list of (book_idx, chapter, verse)."""
    refs, texts = [], []
    for book_idx, book in enumerate(BOOKS):
        for line in (ASSETS / translation / f"{book}.txt").read_text(encoding="utf-8").splitlines():
            head, sep, _ = line.partition(":")
            if not sep or not head.strip().isdigit():
                continue  # header / blank / non-verse line
            parts = line.split(":", 2)
            if len(parts) < 3:
                continue
            chap_s, verse_s, text = parts[0].strip(), parts[1].strip(), parts[2].strip()
            if not (chap_s.isdigit() and verse_s.isdigit()):
                continue
            refs.append((book_idx, int(chap_s), int(verse_s)))
            texts.append(text)
    return refs, texts


def embed(model, translation, texts):
    """Embed one translation, reusing the on-disk cache when the text is unchanged."""
    CACHE.mkdir(exist_ok=True)
    stamp = hashlib.sha256(("\n".join(texts) + MODEL_NAME).encode("utf-8")).hexdigest()[:16]
    path = CACHE / f"{translation}-{stamp}.npy"
    if path.exists():
        print(f"Embedding {translation}: reusing cache {path.name}")
        return np.load(path)
    print(f"Embedding {translation} with {MODEL_NAME} on {model.device} ...")
    vectors = model.encode(
        texts, batch_size=128, normalize_embeddings=True,
        show_progress_bar=True, convert_to_numpy=True,
    ).astype(np.float32)
    np.save(path, vectors)
    return vectors


def compute_neighbors(emb, tfidf, usable, chap_key, book_arr, chap_arr, verse_arr, chunk=512):
    """Top-K neighbors per verse, scored across every translation.

    Returns (neighbors, surfaced_by): an (n, K, 3) uint8 array of reference triples — (0,0,0)
    where no candidate survived — and, per slot, the translation that won it (review only).
    """
    n = len(chap_key)
    neighbors = np.zeros((n, K, 3), dtype=np.uint8)
    surfaced_by = [[None] * K for _ in range(n)]
    for start in range(0, n, chunk):
        end = min(start + chunk, n)
        rows_n = end - start
        # A candidate keeps its *best* score across translations, so a pairing that only one
        # translation's wording makes obvious still wins a slot; the lexical veto is the
        # opposite — it applies if the pair reads as text-discoverable in *any* translation.
        best = np.full((rows_n, n), -1.0e9, dtype=np.float32)
        best_src = np.zeros((rows_n, n), dtype=np.int8)
        vetoed = np.zeros((rows_n, n), dtype=bool)
        for ti, t in enumerate(TRANSLATIONS):
            sem = emb[t][start:end] @ emb[t].T                # (rows, n) semantic cosine
            lex = np.asarray((tfidf[t][start:end] @ tfidf[t].T).todense(), dtype=np.float32)
            vetoed |= lex > LEXICAL_MAX                       # too text-discoverable
            combined = sem - LEXICAL_PENALTY * lex
            combined[:, ~usable[t]] = -1.0e9                  # candidate absent from this translation
            combined[~usable[t][start:end], :] = -1.0e9       # query absent from this translation
            best_src[combined > best] = ti
            np.maximum(best, combined, out=best)
        best[chap_key[None, :] == chap_key[start:end, None]] = -1.0e9  # self + same-chapter
        best[vetoed] = -1.0e9
        top = np.argpartition(-best, POOL, axis=1)[:, :POOL]  # POOL best (unordered)
        rows = np.arange(rows_n)[:, None]
        order = np.argsort(-best[rows, top], axis=1)          # sort those POOL best-first
        top = top[rows, order]
        for i in range(rows_n):
            # Take the K best, but at most one per chapter: adjacent verses score almost
            # identically, so without this a single passage (Ezekiel 34:7/9/10) eats half
            # the list. Hence the over-fetched pool — dropped siblings still leave K slots.
            k = 0
            seen_chapters = set()
            for j in (int(x) for x in top[i]):
                if k == K:
                    break
                if best[i, j] <= -1.0e8:
                    break  # pool exhausted — leave the remaining (0,0,0) sentinels
                if int(chap_key[j]) in seen_chapters:
                    continue
                seen_chapters.add(int(chap_key[j]))
                neighbors[start + i, k] = (book_arr[j], chap_arr[j], verse_arr[j])
                surfaced_by[start + i][k] = TRANSLATIONS[best_src[i, j]]
                k += 1
        print(f"  {end}/{n}", end="\r")
    print()
    return neighbors, surfaced_by


def main():
    print("Loading verses...")
    refs, canon_texts = load_verses(TRANSLATIONS[0])
    n = len(refs)
    print(f"  {n} verses ({TRANSLATIONS[0]}, canonical)")

    # Every other translation is aligned onto that canonical reference list. A verse it doesn't
    # carry under the same number (or leaves blank) becomes "" and is masked out of its own
    # scoring pass, so it simply contributes nothing rather than scoring garbage.
    texts_by_translation = {TRANSLATIONS[0]: canon_texts}
    for t in TRANSLATIONS[1:]:
        t_refs, t_texts = load_verses(t)
        by_ref = dict(zip(t_refs, t_texts))
        aligned = [by_ref.get(r, "") for r in refs]
        print(f"  {t}: {len(t_refs)} verses, {sum(1 for s in aligned if not s)} unusable after alignment")
        texts_by_translation[t] = aligned

    book_arr = np.array([r[0] for r in refs], dtype=np.int32)
    chap_arr = np.array([r[1] for r in refs], dtype=np.int32)
    verse_arr = np.array([r[2] for r in refs], dtype=np.int32)
    assert chap_arr.max() < 256 and verse_arr.max() < 256 and book_arr.max() < 256, \
        "reference component exceeds one byte"

    # Unique id per (book, chapter) so we can mask out same-chapter candidates.
    chap_key = book_arr * 1000 + chap_arr

    device = "mps" if torch.backends.mps.is_available() else ("cuda" if torch.cuda.is_available() else "cpu")
    model = SentenceTransformer(MODEL_NAME, device=device)
    emb, tfidf, usable = {}, {}, {}
    for t in TRANSLATIONS:
        texts = texts_by_translation[t]
        emb[t] = embed(model, t, texts)

        # Lexical (word-overlap) similarity via TF-IDF cosine. Rows are L2-normalized, so a dot
        # product is the cosine; min_df=2 drops one-off words that can never be "shared" anyway.
        print(f"Building {t} TF-IDF lexical index...")
        tfidf[t] = TfidfVectorizer(stop_words="english", sublinear_tf=True, min_df=2).fit_transform(texts)
        usable[t] = np.array([bool(s) for s in texts])

    print(f"Computing neighbors (semantic - {LEXICAL_PENALTY}*lexical, drop lexical>{LEXICAL_MAX})...")
    neighbors, surfaced_by = compute_neighbors(
        emb, tfidf, usable, chap_key, book_arr, chap_arr, verse_arr)

    print(f"Writing {OUT_BIN} ...")
    with open(OUT_BIN, "wb") as f:
        f.write(struct.pack(">I", n))
        f.write(struct.pack(">B", K))
        for i in range(n):
            f.write(struct.pack(">BBB", refs[i][0], refs[i][1], refs[i][2]))
            for k in range(K):
                b, c, v = neighbors[i, k]
                f.write(struct.pack(">BBB", int(b), int(c), int(v)))
    print(f"  {OUT_BIN.stat().st_size} bytes")

    # Human-readable spot-check sample for a handful of well-known verses. Each neighbor is
    # shown in the translation that surfaced it, so cross-translation hits can be judged.
    ref_to_text = {refs[i]: canon_texts[i] for i in range(n)}
    samples = [(0, 1, 1), (42, 3, 16), (18, 23, 1), (45, 13, 4), (39, 5, 44),
               (43, 20, 35), (18, 119, 105), (49, 4, 13)]
    review = {}
    idx_of = {refs[i]: i for i in range(n)}
    for s in samples:
        if s not in idx_of:
            continue
        b, c, v = s
        key = f"{Path(BOOKS[b]).name} {c}:{v}"
        entry = {"text": ref_to_text[s], "similar": []}
        for k in range(K):
            nb, nc, nv = (int(x) for x in neighbors[idx_of[s], k])
            if (nb, nc, nv) == (0, 0, 0):
                continue
            src = surfaced_by[idx_of[s]][k]
            entry["similar"].append({
                "ref": f"{BOOKS[nb]} {nc}:{nv}",
                "via": src,
                "text": texts_by_translation[src][idx_of[(nb, nc, nv)]],
            })
        review[key] = entry
    OUT_REVIEW.write_text(json.dumps(review, indent=2, ensure_ascii=False), encoding="utf-8")
    print(f"  wrote review sample -> {OUT_REVIEW}")
    print("Done.")


if __name__ == "__main__":
    main()
