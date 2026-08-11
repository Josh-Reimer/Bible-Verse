package com.verse.of.the.day;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Locale;

import static org.junit.Assert.*;

// The search path folds case on both sides — the query and the chapter text — and the two
// folds have to agree no matter what locale the device is in. Under a Turkish or Azeri
// locale the default fold maps 'I' to 'ı' (dotless i), so a capitalized "In the beginning"
// stopped matching a query for "in": every sentence-initial word containing an i went
// missing from search results. These tests run with the default locale set to Turkish and
// fail if any fold on that path goes back to the no-arg toLowerCase().
public class TurkishLocaleSearchTest {
    private Locale original;

    @Before
    public void setUp() {
        original = Locale.getDefault();
        Locale.setDefault(new Locale("tr", "TR"));
    }

    @After
    public void tearDown() {
        Locale.setDefault(original);
    }

    @Test
    public void tokenizerFoldsIndependentlyOfLocale() {
        List<QueryTokenizer.Token> tokens = QueryTokenizer.tokenize("IN THE BEGINNING");
        for (QueryTokenizer.Token token : tokens) {
            assertFalse("token folded with the Turkish locale: " + token.text,
                    token.text.indexOf('ı') >= 0);
        }
    }

    @Test
    public void relevanceScoreMatchesCapitalizedVerseText() {
        String verse = "In the beginning God created the heaven and the earth.";
        // 0 = the query appears as a run of whole words, which is what a search for the
        // opening of Genesis 1:1 must score under any locale.
        assertEquals(0, SearchEngine.relevanceScore(verse, "in the beginning",
                QueryTokenizer.tokenize("in the beginning")));
    }

    @Test
    public void relevanceScoreFindsSentenceInitialWord() {
        assertEquals(0, SearchEngine.relevanceScore("I am the way, the truth, and the life",
                "i am", QueryTokenizer.tokenize("i am")));
    }

    @Test
    public void properNameUpcasesIndependentlyOfLocale() {
        // "PHİLİPPİANS" is what the default fold produces here.
        assertEquals("PHILIPIANS", Bible.getProperName("philipians.txt"));
    }
}
