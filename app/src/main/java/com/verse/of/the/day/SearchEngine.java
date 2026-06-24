package com.verse.of.the.day;

import android.content.Context;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.graphics.Typeface;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public class SearchEngine {
    private static final Bible bible = new Bible();
    private static final Tools tools = new Tools();
    private static final Map<String, String> chapterCache = new HashMap<>();
    private static final Pattern WORD_BOUNDARY = Pattern.compile("\\b");

    public static class ReadyTokens {
        public List<QueryTokenizer.Token> tokens;
        public Map<String, List<Integer>> tokenMatches;

        public ReadyTokens(List<QueryTokenizer.Token> tokens) {
            this.tokens = tokens;
            this.tokenMatches = new HashMap<>();
        }
    }

    public static List<String> searchByGrep(Context context, SearchEngineQuery query) {
        List<QueryTokenizer.Token> tokens = QueryTokenizer.tokenize(query.queryString);
        if (tokens.isEmpty()) {
            return new ArrayList<>();
        }

        List<Integer> resultAris = null;

        for (QueryTokenizer.Token token : tokens) {
            List<Integer> tokenMatches = searchToken(context, query, token);

            if (resultAris == null) {
                resultAris = tokenMatches;
            } else {
                resultAris = intersect(resultAris, tokenMatches);
                if (resultAris.isEmpty()) {
                    break;
                }
            }
        }

        if (resultAris == null) {
            resultAris = new ArrayList<>();
        }

        List<String> verseRefs = new ArrayList<>();
        for (Integer ari : resultAris) {
            verseRefs.add(decodeARI(ari));
        }
        return verseRefs;
    }

    private static List<Integer> searchToken(Context context, SearchEngineQuery query, QueryTokenizer.Token token) {
        List<Integer> matches = new ArrayList<>();

        for (int bookIndex = 0; bookIndex < bible.books.length; bookIndex++) {
            if (!query.shouldSearchBook(bookIndex)) {
                continue;
            }

            String bookFile = bible.books[bookIndex];
            int maxChapters = bible.getBookLength(tools, context, bookFile);

            for (int chapter = 1; chapter <= maxChapters; chapter++) {
                String cacheKey = bookFile + ":" + chapter;
                String chapterText = chapterCache.get(cacheKey);
                if (chapterText == null) {
                    chapterText = bible.getChapter(context, tools, bookFile, chapter);
                    chapterCache.put(cacheKey, chapterText);
                }

                String[] verses = chapterText.split("\n");

                for (int verseIndex = 0; verseIndex < verses.length; verseIndex++) {
                    String verseText = verses[verseIndex].toLowerCase();

                    if (token.isPhrase && token.isWholeWord) {
                        if (indexOfWholeMultiword(verseText, token.text) != -1) {
                            int ari = encodeARI(bookIndex, chapter, verseIndex + 1);
                            matches.add(ari);
                        }
                    } else if (token.isWholeWord) {
                        if (indexOfWholeWordRegex(verseText, token.text) != -1) {
                            int ari = encodeARI(bookIndex, chapter, verseIndex + 1);
                            matches.add(ari);
                        }
                    } else {
                        if (verseText.contains(token.text)) {
                            int ari = encodeARI(bookIndex, chapter, verseIndex + 1);
                            matches.add(ari);
                        }
                    }
                }
            }
        }

        return matches;
    }

    private static int indexOfWholeWordRegex(String text, String word) {
        String pattern = "\\b" + Pattern.quote(word) + "\\b";
        Pattern p = Pattern.compile(pattern);
        java.util.regex.Matcher m = p.matcher(text);
        return m.find() ? m.start() : -1;
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

    private static int indexOfWholeMultiword(String text, String phrase) {
        String[] words = phrase.split("\\s+");
        int startPos = 0;
        int firstWordPos = indexOfWholeWord(text, words[0], startPos);

        while (firstWordPos != -1) {
            int checkPos = firstWordPos + words[0].length();
            boolean allWordsMatch = true;

            for (int i = 1; i < words.length; i++) {
                int nextWordPos = indexOfWholeWord(text, words[i], checkPos);
                if (nextWordPos == -1) {
                    allWordsMatch = false;
                    break;
                }
                checkPos = nextWordPos + words[i].length();
            }

            if (allWordsMatch) {
                return firstWordPos;
            }

            startPos = firstWordPos + 1;
            firstWordPos = indexOfWholeWord(text, words[0], startPos);
        }

        return -1;
    }

    private static boolean isLetterOrDigit(char c) {
        return Character.isLetterOrDigit(c);
    }

    private static List<Integer> intersect(List<Integer> list1, List<Integer> list2) {
        Set<Integer> set1 = new HashSet<>(list1);
        List<Integer> result = new ArrayList<>();
        for (Integer item : list2) {
            if (set1.contains(item)) {
                result.add(item);
            }
        }
        return result;
    }

    public static Spannable hilite(CharSequence text, ReadyTokens tokens, int highlightColor) {
        SpannableStringBuilder spannable = new SpannableStringBuilder(text);
        String lowerText = text.toString().toLowerCase();

        for (QueryTokenizer.Token token : tokens.tokens) {
            if (token.isPhrase && token.isWholeWord) {
                applyMultiwordHighlight(spannable, lowerText, token.text, highlightColor);
            } else if (token.isWholeWord) {
                applyWholeWordHighlight(spannable, lowerText, token.text, highlightColor);
            } else {
                applySubstringHighlight(spannable, lowerText, token.text, highlightColor);
            }
        }

        return spannable;
    }

    private static void applyWholeWordHighlight(SpannableStringBuilder spannable, String lowerText, String word, int highlightColor) {
        String pattern = "\\b" + Pattern.quote(word) + "\\b";
        Pattern p = Pattern.compile(pattern);
        java.util.regex.Matcher m = p.matcher(lowerText);
        while (m.find()) {
            spannable.setSpan(new ForegroundColorSpan(highlightColor), m.start(), m.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            spannable.setSpan(new StyleSpan(Typeface.BOLD), m.start(), m.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    private static void applySubstringHighlight(SpannableStringBuilder spannable, String lowerText, String substring, int highlightColor) {
        int pos = 0;
        while ((pos = lowerText.indexOf(substring, pos)) != -1) {
            int end = pos + substring.length();
            spannable.setSpan(new ForegroundColorSpan(highlightColor), pos, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            spannable.setSpan(new StyleSpan(Typeface.BOLD), pos, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            pos = end;
        }
    }

    private static void applyMultiwordHighlight(SpannableStringBuilder spannable, String lowerText, String phrase, int highlightColor) {
        String[] words = phrase.split("\\s+");
        int pos = 0;

        while ((pos = indexOfWholeMultiword(lowerText, phrase, pos)) != -1) {
            int checkPos = pos + words[0].length();

            for (int i = 1; i < words.length; i++) {
                int nextWordPos = indexOfWholeWord(lowerText, words[i], checkPos);
                checkPos = nextWordPos + words[i].length();
            }

            spannable.setSpan(new ForegroundColorSpan(highlightColor), pos, checkPos, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            spannable.setSpan(new StyleSpan(Typeface.BOLD), pos, checkPos, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            pos = checkPos;
        }
    }

    private static int indexOfWholeMultiword(String text, String phrase, int startPos) {
        String[] words = phrase.split("\\s+");
        int pos = startPos;
        int firstWordPos = indexOfWholeWord(text, words[0], pos);

        while (firstWordPos != -1) {
            int checkPos = firstWordPos + words[0].length();
            boolean allWordsMatch = true;

            for (int i = 1; i < words.length; i++) {
                int nextWordPos = indexOfWholeWord(text, words[i], checkPos);
                if (nextWordPos == -1) {
                    allWordsMatch = false;
                    break;
                }
                checkPos = nextWordPos + words[i].length();
            }

            if (allWordsMatch) {
                return firstWordPos;
            }

            pos = firstWordPos + 1;
            firstWordPos = indexOfWholeWord(text, words[0], pos);
        }

        return -1;
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
