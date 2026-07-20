package com.verse.of.the.day;

import androidx.lifecycle.ViewModel;
import java.util.List;

// Activity-scoped holder for the current search results. Results must never go
// into a saved-state Bundle: a broad search serializes to multiple MB, which
// blows the ~1MB binder limit when the activity stops (TransactionTooLargeException).
// The ViewModel survives rotation/theme recreation; after process death it comes
// back empty and the results sheet dismisses itself.
public class SearchResultsViewModel extends ViewModel {
    public List<SearchResult> results;
}
