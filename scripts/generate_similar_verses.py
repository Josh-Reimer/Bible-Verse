#!/usr/bin/env python3
"""Generate assets/similar_verses.bin — a precomputed nearest-neighbor table.

For every KJV verse we embed its text with a sentence-transformer and store the
top-K most semantically similar *other* verses. The retrieval corpus (the whole
Bible) never changes, so this is computed once, offline, and committed — the app
does a plain table lookup at runtime (no model, no NPU). See CLAUDE.md's note on
generate_red_letter.py for the sibling pattern.

Similarity is computed on KJV text only and stored as translation-independent
"book:chapter:verse" references; the app renders each neighbor in whatever
translation the user currently has selected.

Verses in the *same chapter* as the query are excluded as candidates — they are
usually near-duplicates of the verse the user is already reading, so surfacing
them as "similar verses" is useless; we want cross-references from elsewhere.

Binary format (big-endian, matched by SimilarVerses.java via DataInputStream):
    uint32  count
    repeated count times:
        uint8 book, uint8 chapter, uint8 verse           # the target verse
        K * (uint8 book, uint8 chapter, uint8 verse)      # neighbors, best first
    A neighbor slot of (0,0,0) is a sentinel for "no neighbor" (chapters and
    verses are 1-based, so it never collides with a real reference).

Run:  scripts/.venv-embed/bin/python scripts/generate_similar_verses.py
"""

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
K = 5  # neighbors stored per verse

# We want *thematically* similar verses that a keyword/text search would NOT surface, so we
# score each candidate as (semantic cosine) - LEXICAL_PENALTY * (lexical cosine) and hard-drop
# any candidate sharing too much distinctive vocabulary. Lexical similarity is TF-IDF cosine,
# so shared *rare* words (proper nouns, stock phrases like "the LORD spake unto Moses") — the
# very things text search keys on — are penalized hardest. Raise these to make results vaguer.
LEXICAL_PENALTY = 0.45  # weight subtracted for word-overlap; higher => broader/less obvious
LEXICAL_MAX = 0.28      # candidates above this TF-IDF cosine are excluded outright

REPO = Path(__file__).resolve().parent.parent
ASSETS = REPO / "app" / "src" / "main" / "assets"
KJV = ASSETS / "kjv"
OUT_BIN = ASSETS / "similar_verses.bin"
OUT_REVIEW = REPO / "scripts" / "similar_verses_review.json"


def load_verses():
    """Return (refs, texts): refs is a list of (book_idx, chapter, verse)."""
    refs, texts = [], []
    for book_idx, book in enumerate(BOOKS):
        for line in (KJV / f"{book}.txt").read_text(encoding="utf-8").splitlines():
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


def main():
    print("Loading verses...")
    refs, texts = load_verses()
    n = len(refs)
    print(f"  {n} verses")

    book_arr = np.array([r[0] for r in refs], dtype=np.int32)
    chap_arr = np.array([r[1] for r in refs], dtype=np.int32)
    verse_arr = np.array([r[2] for r in refs], dtype=np.int32)
    assert chap_arr.max() < 256 and verse_arr.max() < 256 and book_arr.max() < 256, \
        "reference component exceeds one byte"

    # Unique id per (book, chapter) so we can mask out same-chapter candidates.
    chap_key = book_arr * 1000 + chap_arr

    device = "mps" if torch.backends.mps.is_available() else ("cuda" if torch.cuda.is_available() else "cpu")
    print(f"Embedding with {MODEL_NAME} on {device} ...")
    model = SentenceTransformer(MODEL_NAME, device=device)
    emb = model.encode(
        texts, batch_size=128, normalize_embeddings=True,
        show_progress_bar=True, convert_to_numpy=True,
    ).astype(np.float32)

    # Lexical (word-overlap) similarity via TF-IDF cosine. Rows are L2-normalized, so a dot
    # product is the cosine; min_df=2 drops one-off words that can never be "shared" anyway.
    print("Building TF-IDF lexical index...")
    tfidf = TfidfVectorizer(stop_words="english", sublinear_tf=True, min_df=2).fit_transform(texts)

    print(f"Computing neighbors (semantic - {LEXICAL_PENALTY}*lexical, drop lexical>{LEXICAL_MAX})...")
    neighbors = np.zeros((n, K, 3), dtype=np.uint8)
    chunk = 512
    for start in range(0, n, chunk):
        end = min(start + chunk, n)
        sem = emb[start:end] @ emb.T                          # (rows, n) semantic cosine
        lex = np.asarray((tfidf[start:end] @ tfidf.T).todense(), dtype=np.float32)  # lexical cosine
        combined = sem - LEXICAL_PENALTY * lex
        combined[chap_key[None, :] == chap_key[start:end, None]] = -1.0e9  # self + same-chapter
        combined[lex > LEXICAL_MAX] = -1.0e9                  # too text-discoverable
        top = np.argpartition(-combined, K, axis=1)[:, :K]    # K best (unordered)
        rows = np.arange(end - start)[:, None]
        order = np.argsort(-combined[rows, top], axis=1)      # sort those K best-first
        top = top[rows, order]
        for i in range(end - start):
            for k in range(K):
                j = int(top[i, k])
                if combined[i, j] <= -1.0e8:
                    continue  # excluded candidate — leave the (0,0,0) sentinel
                neighbors[start + i, k] = (book_arr[j], chap_arr[j], verse_arr[j])
        print(f"  {end}/{n}", end="\r")
    print()

    print(f"Writing {OUT_BIN} ...")
    with open(OUT_BIN, "wb") as f:
        f.write(struct.pack(">I", n))
        for i in range(n):
            f.write(struct.pack(">BBB", refs[i][0], refs[i][1], refs[i][2]))
            for k in range(K):
                b, c, v = neighbors[i, k]
                f.write(struct.pack(">BBB", int(b), int(c), int(v)))
    print(f"  {OUT_BIN.stat().st_size} bytes")

    # Human-readable spot-check sample for a handful of well-known verses.
    ref_to_text = {refs[i]: texts[i] for i in range(n)}
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
            entry["similar"].append({
                "ref": f"{BOOKS[nb]} {nc}:{nv}",
                "text": ref_to_text.get((nb, nc, nv), "?"),
            })
        review[key] = entry
    OUT_REVIEW.write_text(json.dumps(review, indent=2, ensure_ascii=False), encoding="utf-8")
    print(f"  wrote review sample -> {OUT_REVIEW}")
    print("Done.")


if __name__ == "__main__":
    main()
