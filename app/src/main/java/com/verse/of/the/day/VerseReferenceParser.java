package com.verse.of.the.day;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

// Turns a free-form reference typed into the search bar into a book/chapter/verse.
// Everything that isn't a letter or a digit (commas, colons, periods, extra spaces)
// is treated as a separator, and letter/digit boundaries are split too, so
// "John 3:16", "john 3 16", "John, 3, 16" and "jn3:16" all parse the same way.
// Returns null for anything that doesn't resolve to a real verse, which is what
// keeps ordinary word searches ("love", "mark of the beast") out of this path.
public class VerseReferenceParser {

    public static class Reference {
        public final int bookIndex;
        public final int chapter;
        public final int verse; // 0 when the query named a chapter but no verse

        Reference(int bookIndex, int chapter, int verse) {
            this.bookIndex = bookIndex;
            this.chapter = chapter;
            this.verse = verse;
        }
    }

    // Extra spellings, keyed to the book-name stem derived from the asset filename
    // (see buildEntries). Only names a prefix match can't reach belong here: the
    // assets' misspellings mean the correct spelling isn't always a prefix of them,
    // and a few standard abbreviations aren't prefixes either. Applies to every
    // ordinal of that stem at once, so "kgs" covers both 1 and 2 Kings.
    private static final String[][] EXTRA_NAMES = {
            {"ecclesiastes", "eccliasiastes"},
            {"ezekiel", "ezekial"},
            {"philippians", "philipians"},
            {"phil", "philipians"},       // prefix alone is ambiguous with Philemon
            {"thessalonians", "thesselonians"},
            {"songofsongs", "songofsolomon"},
            {"sos", "songofsolomon"},
            {"canticles", "songofsolomon"},
            {"psalm", "psalms"},
            {"phlm", "philemon"},
            {"jas", "james"},
            {"mt", "matthew"},
            {"mk", "mark"},
            {"lk", "luke"},
            {"jn", "john"},
            {"kgs", "kings"},
    };

    private static class Entry {
        final int ordinal; // 0 for un-numbered books, 1/2/3 for "1 Samuel" and friends
        final String name; // compacted, e.g. "songofsolomon"
        final int bookIndex;

        Entry(int ordinal, String name, int bookIndex) {
            this.ordinal = ordinal;
            this.name = name;
            this.bookIndex = bookIndex;
        }
    }

    private static List<Entry> entries;

    public static Reference parse(Context context, Tools tools, Bible bible, String query) {
        if (query == null) {
            return null;
        }
        List<String> tokens = tokenize(query);

        // Pull the trailing chapter (and verse) numbers off the end; what's left is the book.
        List<Integer> numbers = new ArrayList<>(2);
        int bookEnd = tokens.size();
        while (bookEnd > 0 && numbers.size() < 2) {
            Integer number = asNumber(tokens.get(bookEnd - 1));
            if (number == null) {
                break;
            }
            numbers.add(0, number);
            bookEnd--;
        }
        // A bare book name is not a reference — it's a word the text search should handle.
        if (numbers.isEmpty() || bookEnd == 0) {
            return null;
        }

        int bookIndex = findBook(tokens.subList(0, bookEnd));
        if (bookIndex < 0) {
            return null;
        }

        int chapter = numbers.get(0);
        int verse = numbers.size() > 1 ? numbers.get(1) : 0;
        if (chapter < 1 || verse < 0) {
            return null;
        }

        int chapterCount;
        try {
            chapterCount = bible.getBookLength(tools, context, bible.books[bookIndex]);
        } catch (RuntimeException e) {
            return null;
        }
        if (chapter > chapterCount) {
            // The one-chapter books are cited by verse alone: "jude 5" means Jude 1:5.
            if (chapterCount == 1 && verse == 0) {
                verse = chapter;
                chapter = 1;
            } else {
                return null;
            }
        }
        if (verse > 0 && verse > bible.getChapterLength(context, tools, bible.books[bookIndex], chapter)) {
            return null;
        }
        return new Reference(bookIndex, chapter, verse);
    }

    // Lowercases, turns every non-alphanumeric character into a separator, and splits
    // letter/digit boundaries so "1john" and "john3" tokenize as "1 john" and "john 3".
    private static List<String> tokenize(String query) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean currentIsDigits = false;

        for (int i = 0; i < query.length(); i++) {
            char c = Character.toLowerCase(query.charAt(i));
            boolean digit = c >= '0' && c <= '9';
            boolean letter = c >= 'a' && c <= 'z';

            if (!digit && !letter) {
                flush(tokens, current);
                continue;
            }
            if (current.length() > 0 && digit != currentIsDigits) {
                flush(tokens, current);
            }
            current.append(c);
            currentIsDigits = digit;
        }
        flush(tokens, current);
        return tokens;
    }

    private static void flush(List<String> tokens, StringBuilder current) {
        if (current.length() > 0) {
            tokens.add(current.toString());
            current.setLength(0);
        }
    }

    private static Integer asNumber(String token) {
        if (token.length() > 3 || !Character.isDigit(token.charAt(0))) {
            return null;
        }
        for (int i = 0; i < token.length(); i++) {
            if (!Character.isDigit(token.charAt(i))) {
                return null;
            }
        }
        return Integer.parseInt(token);
    }

    // Reads a leading ordinal ("1", "i", "1st", "first") off the book tokens and matches
    // the rest, joined without separators, against the book table.
    private static int findBook(List<String> bookTokens) {
        int ordinal = 0;
        int start = 0;

        int leading = asOrdinal(bookTokens.get(0));
        if (leading > 0 && bookTokens.size() > 1) {
            ordinal = leading;
            start = 1;
            // "1st"/"2nd"/"3rd" split into a digit and a suffix token.
            if (bookTokens.size() > 2 && isOrdinalSuffix(bookTokens.get(1))) {
                start = 2;
            }
        }

        StringBuilder name = new StringBuilder();
        for (int i = start; i < bookTokens.size(); i++) {
            name.append(bookTokens.get(i));
        }
        if (name.length() == 0) {
            return -1;
        }
        return findBook(ordinal, name.toString());
    }

    private static int asOrdinal(String token) {
        switch (token) {
            case "1": case "i": case "first": return 1;
            case "2": case "ii": case "second": return 2;
            case "3": case "iii": case "third": return 3;
            default: return 0;
        }
    }

    private static boolean isOrdinalSuffix(String token) {
        return token.equals("st") || token.equals("nd") || token.equals("rd");
    }

    // Exact match first, then a unique-prefix match so ordinary abbreviations
    // ("gen", "matt", "rev", "1 cor") work without listing every one of them.
    // An ambiguous prefix ("jo", "phil" before its alias) resolves to nothing and
    // the query falls through to the text search.
    private static int findBook(int ordinal, String name) {
        List<Entry> table = entries();

        for (Entry entry : table) {
            if (entry.ordinal == ordinal && entry.name.equals(name)) {
                return entry.bookIndex;
            }
        }

        int match = -1;
        for (Entry entry : table) {
            if (entry.ordinal == ordinal && entry.name.startsWith(name)) {
                if (match != -1 && match != entry.bookIndex) {
                    return -1;
                }
                match = entry.bookIndex;
            }
        }
        return match;
    }

    private static synchronized List<Entry> entries() {
        if (entries == null) {
            entries = buildEntries();
        }
        return entries;
    }

    // The asset filenames are the source of the book names: "first_samuel.txt" becomes
    // ordinal 1 + stem "samuel", "song_of_solomon.txt" becomes ordinal 0 + "songofsolomon".
    private static List<Entry> buildEntries() {
        String[] books = new Bible().books;
        List<Entry> table = new ArrayList<>();
        String[] stems = new String[books.length];
        int[] ordinals = new int[books.length];

        for (int bookIndex = 0; bookIndex < books.length; bookIndex++) {
            String[] parts = books[bookIndex].replace(".txt", "").split("_");
            int ordinal = 0;
            int start = 0;
            if (parts.length > 1) {
                ordinal = asOrdinal(parts[0]);
                if (ordinal > 0) {
                    start = 1;
                }
            }
            StringBuilder stem = new StringBuilder();
            for (int i = start; i < parts.length; i++) {
                stem.append(parts[i]);
            }
            stems[bookIndex] = stem.toString();
            ordinals[bookIndex] = ordinal;
            table.add(new Entry(ordinal, stems[bookIndex], bookIndex));
        }

        for (String[] alias : EXTRA_NAMES) {
            for (int bookIndex = 0; bookIndex < books.length; bookIndex++) {
                if (stems[bookIndex].equals(alias[1])) {
                    table.add(new Entry(ordinals[bookIndex], alias[0], bookIndex));
                }
            }
        }
        return table;
    }
}
