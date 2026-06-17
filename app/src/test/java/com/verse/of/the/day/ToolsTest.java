package com.verse.of.the.day;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class ToolsTest {
    private Tools tools;
    private Bible bible;

    @Before
    public void setUp() {
        tools = new Tools();
        bible = new Bible();
    }

    @Test
    public void testIsDigit_withValidNumber() {
        assertTrue(tools.isDigit("123"));
        assertTrue(tools.isDigit("0"));
        assertTrue(tools.isDigit("999"));
    }

    @Test
    public void testIsDigit_withInvalidInput() {
        assertFalse(tools.isDigit("abc"));
        assertFalse(tools.isDigit("12a3"));
        assertFalse(tools.isDigit(""));
    }

    @Test
    public void testIsBook_withValidBook() {
        assertTrue(tools.isBook("genesis", bible));
        assertTrue(tools.isBook("EXODUS", bible));
        assertTrue(tools.isBook("matthew", bible));
    }

    @Test
    public void testIsBook_withInvalidBook() {
        assertFalse(tools.isBook("invalidbook", bible));
        assertFalse(tools.isBook("", bible));
    }

    @Test
    public void testIsSpaceBook_withMultiWordBook() {
        assertTrue(tools.isSpaceBook("first samuel", bible));
        assertTrue(tools.isSpaceBook("second kings", bible));
        assertTrue(tools.isSpaceBook("first corinthians", bible));
    }

    @Test
    public void testIsSpaceBook_withInvalidInput() {
        assertFalse(tools.isSpaceBook("genesis", bible));
        assertFalse(tools.isSpaceBook("invalidbook", bible));
    }

    @Test
    public void testIsBookChapter_withValidInput() {
        assertTrue(tools.isBookChapter("genesis 1", bible));
        assertTrue(tools.isBookChapter("matthew 5", bible));
        assertTrue(tools.isBookChapter("first samuel 15", bible));
    }

    @Test
    public void testIsBookChapter_withInvalidInput() {
        assertFalse(tools.isBookChapter("genesis", bible));
        assertFalse(tools.isBookChapter("1 genesis", bible));
        assertFalse(tools.isBookChapter("invalidbook 1", bible));
    }

    @Test
    public void testIsBookChapterVerse_withValidInput() {
        assertTrue(tools.isBookChapterVerse("genesis 1 1", bible));
        assertTrue(tools.isBookChapterVerse("matthew 5 3", bible));
        assertTrue(tools.isBookChapterVerse("first samuel 15 22", bible));
    }

    @Test
    public void testIsBookChapterVerse_withInvalidInput() {
        assertFalse(tools.isBookChapterVerse("genesis 1", bible));
        assertFalse(tools.isBookChapterVerse("1 1 genesis", bible));
        assertFalse(tools.isBookChapterVerse("invalidbook 1 1", bible));
    }

    @Test
    public void testReplaceFirstSpace() {
        assertEquals("first_samuel", tools.replaceFirstSpace("first samuel"));
        assertEquals("second_kings", tools.replaceFirstSpace("second kings"));
        assertEquals("first_corinthians", tools.replaceFirstSpace("first corinthians"));
    }
}
