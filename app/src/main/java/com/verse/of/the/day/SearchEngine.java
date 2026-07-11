package com.verse.of.the.day;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

public class SearchEngine {
    private static final Bible bible = new Bible();
    private static final Tools tools = new Tools();

    // Whole-Bible text cache, built once per translation (~4-5MB of text), modeled after
    // androidbible's Version chapter loading. chaptersOrig keeps original case for result
    // display; chaptersLower is the pre-lowercased copy tokens are matched against.
    // Indexed [bookIndex][chapter - 1]; each entry is the chapter's verse lines
    // ("chapter:verse: text") joined by '\n', identical to Bible.getChapter() output.
    private static String[][] chaptersOrig;
    private static String[][] chaptersLower;
    private static String cachedTranslation;

    public static List<String> searchByGrep(Context context, SearchEngineQuery query) {
        List<QueryTokenizer.Token> tokens = QueryTokenizer.tokenize(query.queryString);
        if (tokens.isEmpty()) {
            return new ArrayList<>();
        }

        ensureCache(context);

        List<Integer> resultAris = null;

        for (QueryTokenizer.Token token : tokens) {
            List<Integer> tokenMatches = searchToken(query, token, resultAris);

            if (resultAris == null) {
                resultAris = tokenMatches;
            } else {
                resultAris = intersectSorted(resultAris, tokenMatches);
            }
            if (resultAris.isEmpty()) {
                break;
            }
        }

        if (resultAris == null) {
            resultAris = new ArrayList<>();
        }

        List<String> verseRefs = new ArrayList<>(resultAris.size());
        for (Integer ari : resultAris) {
            verseRefs.add(decodeARI(ari));
        }
        return verseRefs;
    }

    // Verse text for search results, served from the in-memory cache instead of re-reading
    // the whole book file per result (which is what new Verse(...) does).
    public static String getVerseText(Context context, String verseRef) {
        ensureCache(context);
        String[] parts = verseRef.split(":");
        int bookIndex = Integer.parseInt(parts[0]);
        int chapter = Integer.parseInt(parts[1]);
        int verse = Integer.parseInt(parts[2]);

        String chapterText = chaptersOrig[bookIndex][chapter - 1];
        int lineStart = 0;
        for (int i = 1; i < verse && lineStart != -1; i++) {
            int posN = chapterText.indexOf('\n', lineStart);
            lineStart = (posN == -1) ? -1 : posN + 1;
        }
        if (lineStart == -1 || lineStart >= chapterText.length()) {
            return "";
        }
        int lineEnd = chapterText.indexOf('\n', lineStart);
        return lineEnd == -1 ? chapterText.substring(lineStart) : chapterText.substring(lineStart, lineEnd);
    }

    // Ranks how closely a verse contains the whole query: 0 = contains it as consecutive
    // whole words, 1 = as a consecutive substring; otherwise 2 plus the extra characters
    // the smallest window containing every token spans beyond the query's own length.
    // Lower is better. Called per result on the search executor, one scan per verse.
    public static int relevanceScore(String verseText, String lowerQuery, List<QueryTokenizer.Token> tokens) {
        if (lowerQuery.isEmpty()) {
            return Integer.MAX_VALUE;
        }
        String lowerText = verseText.toLowerCase();

        int pos = lowerText.indexOf(lowerQuery);
        if (pos != -1) {
            while (pos != -1) {
                int end = pos + lowerQuery.length();
                boolean boundaryBefore = pos == 0 || !isLetterOrDigit(lowerText.charAt(pos - 1));
                boolean boundaryAfter = end >= lowerText.length() || !isLetterOrDigit(lowerText.charAt(end));
                if (boundaryBefore && boundaryAfter) {
                    return 0;
                }
                pos = lowerText.indexOf(lowerQuery, pos + 1);
            }
            return 1;
        }

        return 2 + minWindowBeyondQuery(lowerText, lowerQuery, tokens);
    }

    private static final int NO_WINDOW = 1_000_000;

    // Smallest character span in lowerText covering one occurrence of every distinct token,
    // minus the query's own length (0 = the tokens sit as tightly as the query itself).
    private static int minWindowBeyondQuery(String lowerText, String lowerQuery, List<QueryTokenizer.Token> tokens) {
        List<String> needles = new ArrayList<>();
        for (QueryTokenizer.Token token : tokens) {
            if (!needles.contains(token.text)) {
                needles.add(token.text);
            }
        }
        int k = needles.size();
        if (k == 0) {
            return NO_WINDOW;
        }

        List<int[]> events = new ArrayList<>(); // {position, needleIndex}, later sorted by position
        for (int i = 0; i < k; i++) {
            int p = 0;
            while ((p = lowerText.indexOf(needles.get(i), p)) != -1) {
                events.add(new int[]{p, i});
                p++;
            }
        }
        events.sort((a, b) -> Integer.compare(a[0], b[0]));

        int[] count = new int[k];
        int covered = 0;
        int left = 0;
        int best = NO_WINDOW;
        for (int right = 0; right < events.size(); right++) {
            if (count[events.get(right)[1]]++ == 0) {
                covered++;
            }
            while (covered == k) {
                int[] l = events.get(left);
                int span = events.get(right)[0] + needles.get(events.get(right)[1]).length() - l[0];
                if (span < best) {
                    best = span;
                }
                if (--count[l[1]] == 0) {
                    covered--;
                }
                left++;
            }
        }
        return best == NO_WINDOW ? NO_WINDOW : Math.max(0, best - lowerQuery.length());
    }

    private static synchronized void ensureCache(Context context) {
        SharedPreferences sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE);
        String translation = sp.getString("translation", "kjv");
        if (translation.equals(cachedTranslation) && chaptersLower != null) {
            return;
        }

        String[][] orig = new String[bible.books.length][];
        String[][] lower = new String[bible.books.length][];

        for (int bookIndex = 0; bookIndex < bible.books.length; bookIndex++) {
            String[] bookChapters = splitIntoChapters(tools.getFile(context, bible.books[bookIndex]));
            String[] lowerChapters = new String[bookChapters.length];
            for (int i = 0; i < bookChapters.length; i++) {
                lowerChapters[i] = bookChapters[i].toLowerCase();
            }
            orig[bookIndex] = bookChapters;
            lower[bookIndex] = lowerChapters;
        }

        chaptersOrig = orig;
        chaptersLower = lower;
        cachedTranslation = translation;
    }

    // One pass over the book file, splitting verse lines into per-chapter strings. Lines
    // whose text before the first ':' is not a number (headers, blanks) are skipped — same
    // effective filtering as Bible.getChapter().
    private static String[] splitIntoChapters(String bookText) {
        List<StringBuilder> chapters = new ArrayList<>();
        int len = bookText.length();
        int lineStart = 0;

        while (lineStart < len) {
            int lineEnd = bookText.indexOf('\n', lineStart);
            if (lineEnd == -1) lineEnd = len;

            int colon = bookText.indexOf(':', lineStart);
            if (colon > lineStart && colon < lineEnd) {
                int chapterNum = parsePositiveInt(bookText, lineStart, colon);
                if (chapterNum > 0) {
                    while (chapters.size() < chapterNum) {
                        chapters.add(new StringBuilder());
                    }
                    chapters.get(chapterNum - 1).append(bookText, lineStart, lineEnd).append('\n');
                }
            }
            lineStart = lineEnd + 1;
        }

        String[] result = new String[chapters.size()];
        for (int i = 0; i < chapters.size(); i++) {
            result[i] = chapters.get(i).toString();
        }
        return result;
    }

    private static int parsePositiveInt(String s, int from, int to) {
        int value = 0;
        for (int i = from; i < to; i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') return -1;
            value = value * 10 + (c - '0');
        }
        return from == to ? -1 : value;
    }

    // Searches one token across the Bible. When source is non-null (ascending ARIs matched
    // by the previous tokens), only the chapters present in it are scanned — this is
    // androidbible's pruning: a rare first token makes every later token near-free.
    private static List<Integer> searchToken(SearchEngineQuery query, QueryTokenizer.Token token, List<Integer> source) {
        List<Integer> matches = new ArrayList<>();
        String[] phraseWords = (token.isPhrase && token.isWholeWord) ? token.text.split("\\s+") : null;

        if (source == null) {
            for (int bookIndex = 0; bookIndex < chaptersLower.length; bookIndex++) {
                if (!query.shouldSearchBook(bookIndex)) {
                    continue;
                }
                String[] bookChapters = chaptersLower[bookIndex];
                for (int chapter = 1; chapter <= bookChapters.length; chapter++) {
                    searchChapter(bookChapters[chapter - 1], token, phraseWords, encodeARI(bookIndex, chapter, 0), matches);
                }
            }
        } else {
            int lastAriBc = -1;
            for (Integer ari : source) {
                int ariBc = ari & 0xFFFF00;
                if (ariBc == lastAriBc) {
                    continue;
                }
                lastAriBc = ariBc;
                int bookIndex = (ariBc >> 16) & 0xFF;
                int chapter = (ariBc >> 8) & 0xFF;
                searchChapter(chaptersLower[bookIndex][chapter - 1], token, phraseWords, ariBc, matches);
            }
        }

        return matches;
    }

    // Scans a whole chapter as a single string and maps match positions to verse numbers by
    // walking '\n' positions — a chapter with no match costs one indexOf, with no per-verse
    // substring/toLowerCase allocations.
    private static void searchChapter(String chapterText, QueryTokenizer.Token token, String[] phraseWords, int ariBc, List<Integer> res) {
        int[] consumedPtr = new int[1];
        int pos = indexOfToken(chapterText, token, phraseWords, 0, consumedPtr);
        if (pos == -1) return;

        int verse0 = 0;
        int lastVerse0 = -1;
        int posN = chapterText.indexOf('\n');

        while (true) {
            if (posN != -1 && posN < pos) {
                verse0++;
                posN = chapterText.indexOf('\n', posN + 1);
            } else {
                if (verse0 != lastVerse0) {
                    res.add(ariBc + verse0 + 1);
                    lastVerse0 = verse0;
                }
                pos = indexOfToken(chapterText, token, phraseWords, pos + consumedPtr[0], consumedPtr);
                if (pos == -1) return;
            }
        }
    }

    private static int indexOfToken(String text, QueryTokenizer.Token token, String[] phraseWords, int start, int[] consumedPtr) {
        if (phraseWords != null) {
            return indexOfWholeMultiword(text, phraseWords, start, consumedPtr);
        }
        consumedPtr[0] = token.text.length();
        if (token.isWholeWord) {
            return indexOfWholeWord(text, token.text, start);
        }
        return text.indexOf(token.text, start);
    }

    private static int indexOfWholeWord(String text, String word, int startPos) {
        int pos = startPos;
        while ((pos = text.indexOf(word, pos)) != -1) {
            boolean isWordBoundaryBefore = (pos == 0) || !isLetterOrDigit(text.charAt(pos - 1));
            boolean isWordBoundaryAfter = (pos + word.length() >= text.length()) || !isLetterOrDigit(text.charAt(pos + word.length()));

            if (isWordBoundaryBefore && isWordBoundaryAfter) {
                return pos;
            }
            pos++;
        }
        return -1;
    }

    // Finds the phrase's words as consecutive whole words, allowing punctuation/whitespace
    // between them but never crossing a verse boundary ('\n'). Writes the total matched
    // length into consumedPtr[0].
    private static int indexOfWholeMultiword(String text, String[] words, int start, int[] consumedPtr) {
        int len = text.length();
        String firstWord = words[0];
        int s = start;

        findAllWords:
        while (true) {
            int firstPos = indexOfWholeWord(text, firstWord, s);
            if (firstPos == -1) {
                consumedPtr[0] = 0;
                return -1;
            }
            int pos = firstPos + firstWord.length();

            for (int i = 1; i < words.length; i++) {
                while (pos < len) {
                    char c = text.charAt(pos);
                    if (c == '\n') {
                        s = pos + 1;
                        continue findAllWords;
                    }
                    if (isLetterOrDigit(c)) break;
                    pos++;
                }
                int found = indexOfWholeWord(text, words[i], pos);
                if (found != pos) {
                    s = firstPos + 1;
                    continue findAllWords;
                }
                pos = found + words[i].length();
            }

            consumedPtr[0] = pos - firstPos;
            return firstPos;
        }
    }

    private static boolean isLetterOrDigit(char c) {
        return Character.isLetterOrDigit(c);
    }

    // Both lists are in ascending ARI order, so a linear merge replaces the old HashSet.
    private static List<Integer> intersectSorted(List<Integer> a, List<Integer> b) {
        List<Integer> res = new ArrayList<>(Math.min(a.size(), b.size()));
        int apos = 0;
        int bpos = 0;

        while (apos < a.size() && bpos < b.size()) {
            int av = a.get(apos);
            int bv = b.get(bpos);

            if (av == bv) {
                res.add(av);
                apos++;
                bpos++;
            } else if (av < bv) {
                apos++;
            } else {
                bpos++;
            }
        }

        return res;
    }

    private static int encodeARI(int bookIndex, int chapter, int verse) {
        return (bookIndex << 16) | (chapter << 8) | verse;
    }

    private static String decodeARI(int ari) {
        int bookIndex = (ari >> 16) & 0xFF;
        int chapter = (ari >> 8) & 0xFF;
        int verse = ari & 0xFF;
        return bookIndex + ":" + chapter + ":" + verse;
    }
}
