package com.verse.of.the.day;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class QueryTokenizer {
    private static final Pattern QUOTED_PATTERN = Pattern.compile("[\"\\u201c]([^\"\\u201d]*)[\"\\u201d]");

    public static class Token {
        public String text;
        public boolean isWholeWord;
        public boolean isPhrase;

        public Token(String text, boolean isWholeWord, boolean isPhrase) {
            this.text = text;
            this.isWholeWord = isWholeWord;
            this.isPhrase = isPhrase;
        }

        @Override
        public String toString() {
            return String.format("%s%s%s", isWholeWord ? "+" : "", text, isPhrase ? " (phrase)" : "");
        }
    }

    public static List<Token> tokenize(String query) {
        List<Token> tokens = new ArrayList<>();
        if (query == null || query.trim().isEmpty()) {
            return tokens;
        }

        List<String> quotedPhrases = extractQuotedPhrases(query);
        String remaining = removeQuotedPhrases(query);

        for (String phrase : quotedPhrases) {
            tokens.add(new Token(phrase.toLowerCase(), true, true));
        }

        List<String> plusWords = new ArrayList<>();
        String[] parts = remaining.trim().split("\\s+");

        for (String part : parts) {
            if (part.isEmpty()) continue;

            if (part.startsWith("+")) {
                String word = part.substring(1).toLowerCase();
                if (!word.isEmpty()) {
                    plusWords.add(word);
                }
            } else {
                if (!plusWords.isEmpty()) {
                    String multiwordPhrase = String.join(" ", plusWords);
                    tokens.add(new Token(multiwordPhrase, true, true));
                    plusWords.clear();
                }
                tokens.add(new Token(part.toLowerCase(), false, false));
            }
        }

        if (!plusWords.isEmpty()) {
            String multiwordPhrase = String.join(" ", plusWords);
            tokens.add(new Token(multiwordPhrase, true, true));
        }

        tokens.sort((t1, t2) -> Integer.compare(t2.text.length(), t1.text.length()));

        return tokens;
    }

    private static List<String> extractQuotedPhrases(String query) {
        List<String> phrases = new ArrayList<>();
        Matcher matcher = QUOTED_PATTERN.matcher(query);
        while (matcher.find()) {
            phrases.add(matcher.group(1));
        }
        return phrases;
    }

    private static String removeQuotedPhrases(String query) {
        return QUOTED_PATTERN.matcher(query).replaceAll("").trim();
    }

    public static String tokenWithoutPlus(String token) {
        return token.startsWith("+") ? token.substring(1) : token;
    }

    public static boolean isPlussedToken(String token) {
        return token.startsWith("+");
    }

    public static String[] tokenizeMultiwordToken(String token) {
        return token.split("\\s+");
    }
}
