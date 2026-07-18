package com.verse.of.the.day;

// Serializable so search results survive Activity/Fragment recreation
// (rotation, theme change) via the results sheet's arguments Bundle.
public class SearchResult implements java.io.Serializable {
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
