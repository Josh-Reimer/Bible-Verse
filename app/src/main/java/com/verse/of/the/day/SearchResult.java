package com.verse.of.the.day;

public class SearchResult {
    public String displayReference;
    public String verseReference;
    public String text;

    public SearchResult(String displayReference, String verseReference, String text) {
        this.displayReference = displayReference;
        this.verseReference = verseReference;
        this.text = text;
    }
}
