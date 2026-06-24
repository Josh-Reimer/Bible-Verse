package com.verse.of.the.day;

import android.util.SparseBooleanArray;

public class SearchEngineQuery {
    public String queryString;
    public SparseBooleanArray bookIds;

    public SearchEngineQuery(String queryString) {
        this.queryString = queryString;
        this.bookIds = null;
    }

    public SearchEngineQuery(String queryString, SparseBooleanArray bookIds) {
        this.queryString = queryString;
        this.bookIds = bookIds;
    }

    public boolean shouldSearchBook(int bookIndex) {
        if (bookIds == null) return true;
        return bookIds.get(bookIndex, false);
    }
}
