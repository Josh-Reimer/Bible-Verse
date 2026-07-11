package com.verse.of.the.day;

import android.text.Spannable;
import android.text.SpannableString;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SearchResultsAdapter extends RecyclerView.Adapter<SearchResultsAdapter.ViewHolder> {
    private List<SearchResult> results;
    private OnResultClickListener listener;
    private Map<String, Spannable> highlightCache = new HashMap<>();

    public interface OnResultClickListener {
        void onResultClick(SearchResult result);
    }

    public SearchResultsAdapter(List<SearchResult> results, OnResultClickListener listener) {
        this.results = results;
        this.listener = listener;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.search_result_item, parent, false));
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        SearchResult result = results.get(position);
        holder.reference.setText(result.displayReference);

        String cacheKey = result.text + "|" + result.searchQuery;
        Spannable cached = highlightCache.get(cacheKey);
        if (cached == null) {
            cached = highlightText(result.text, result.searchQuery);
            highlightCache.put(cacheKey, cached);
        }
        holder.text.setText(cached);

        holder.itemView.setOnClickListener(v -> listener.onResultClick(result));
    }

    // If the verse contains the full query as a consecutive phrase, highlight only that;
    // otherwise fall back to highlighting each token separately, mirroring SearchEngine's
    // word-by-word matching — a verse can match all tokens without containing the phrase.
    private Spannable highlightText(String text, String searchQuery) {
        SpannableString spannable = new SpannableString(text);
        String lowerText = text.toLowerCase();
        String lowerQuery = searchQuery.toLowerCase().trim();

        if (!lowerQuery.isEmpty() && lowerText.contains(lowerQuery)) {
            highlightOccurrences(spannable, lowerText, lowerQuery);
            return spannable;
        }

        for (QueryTokenizer.Token token : QueryTokenizer.tokenize(searchQuery)) {
            highlightOccurrences(spannable, lowerText, token.text);
        }

        return spannable;
    }

    // Highlights whole-word occurrences of the needle; only when there are none (the verse
    // matched via a partial word, e.g. "begin" inside "beginning") highlights substrings
    // that at least start or end on a word boundary — never matches buried mid-word.
    private void highlightOccurrences(SpannableString spannable, String lowerText, String lowerNeedle) {
        if (!highlightPass(spannable, lowerText, lowerNeedle, true)) {
            highlightPass(spannable, lowerText, lowerNeedle, false);
        }
    }

    private boolean highlightPass(SpannableString spannable, String lowerText, String lowerNeedle, boolean wholeWordOnly) {
        boolean found = false;
        int startIdx = 0;
        while ((startIdx = lowerText.indexOf(lowerNeedle, startIdx)) != -1) {
            int endIdx = startIdx + lowerNeedle.length();
            boolean boundaryBefore = startIdx == 0 || !Character.isLetterOrDigit(lowerText.charAt(startIdx - 1));
            boolean boundaryAfter = endIdx >= lowerText.length() || !Character.isLetterOrDigit(lowerText.charAt(endIdx));
            boolean matches = wholeWordOnly ? (boundaryBefore && boundaryAfter) : (boundaryBefore || boundaryAfter);
            if (matches) {
                spannable.setSpan(new android.text.style.ForegroundColorSpan(0xFFFF6B35), startIdx, endIdx, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                found = true;
                startIdx = endIdx;
            } else {
                startIdx++;
            }
        }
        return found;
    }

    @Override
    public int getItemCount() {
        return results.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView reference;
        TextView text;

        public ViewHolder(android.view.View itemView) {
            super(itemView);
            reference = itemView.findViewById(R.id.result_reference);
            text = itemView.findViewById(R.id.result_text);
        }
    }
}
