package com.verse.of.the.day;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class BibleTest {
    private Bible bible;

    @Before
    public void setUp() {
        bible = new Bible();
    }

    @Test
    public void testGetProperName_withSingleWordBook() {
        assertEquals("GENESIS", Bible.getProperName("genesis.txt"));
        assertEquals("EXODUS", Bible.getProperName("exodus.txt"));
        assertEquals("MATTHEW", Bible.getProperName("matthew.txt"));
    }

    @Test
    public void testGetProperName_withMultiWordBook() {
        assertEquals("FIRST SAMUEL", Bible.getProperName("first_samuel.txt"));
        assertEquals("SECOND KINGS", Bible.getProperName("second_kings.txt"));
        assertEquals("FIRST CORINTHIANS", Bible.getProperName("first_corinthians.txt"));
    }

    // Five asset filenames are misspelled; the display name corrects them, and the
    // correction must not touch a book that merely starts the same way (Philemon).
    @Test
    public void testGetProperName_correctsMisspelledFilenames() {
        assertEquals("ECCLESIASTES", Bible.getProperName("eccliasiastes.txt"));
        assertEquals("EZEKIEL", Bible.getProperName("ezekial.txt"));
        assertEquals("PHILIPPIANS", Bible.getProperName("philipians.txt"));
        assertEquals("FIRST THESSALONIANS", Bible.getProperName("first_thesselonians.txt"));
        assertEquals("SECOND THESSALONIANS", Bible.getProperName("second_thesselonians.txt"));
        assertEquals("PHILEMON", Bible.getProperName("philemon.txt"));
    }

    @Test
    public void testGetProperName_withoutExtension() {
        assertEquals("GENESIS", Bible.getProperName("genesis"));
        assertEquals("FIRST SAMUEL", Bible.getProperName("first_samuel"));
    }

    @Test
    public void testBooksArrayNotEmpty() {
        assertTrue(bible.books.length > 0);
        assertEquals(66, bible.books.length);
    }

    @Test
    public void testBooksArrayContainsExpectedBooks() {
        assertTrue(containsBook("genesis.txt"));
        assertTrue(containsBook("matthew.txt"));
        assertTrue(containsBook("revelation.txt"));
        assertTrue(containsBook("first_samuel.txt"));
    }

    @Test
    public void testBooksArrayContainsAllNewTestamentBooks() {
        assertTrue(containsBook("matthew.txt"));
        assertTrue(containsBook("mark.txt"));
        assertTrue(containsBook("luke.txt"));
        assertTrue(containsBook("john.txt"));
        assertTrue(containsBook("acts.txt"));
        assertTrue(containsBook("romans.txt"));
    }

    private boolean containsBook(String bookName) {
        for (String book : bible.books) {
            if (book.equals(bookName)) {
                return true;
            }
        }
        return false;
    }
}
