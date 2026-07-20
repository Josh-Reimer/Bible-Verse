package com.verse.of.the.day;

public class SearchResult {
    public String displayReference;
    public String verseReference;
    public String text;
    public String searchQuery;
    public int relevanceScore;

    public SearchResult(String displayReference, String verseReference, String text, String searchQuery) {
        this.displayReference = displayReference;
        this.verseReference = verseReference;
        this.text = text;
        this.searchQuery = searchQuery;
    }
}
