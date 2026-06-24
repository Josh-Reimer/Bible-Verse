package com.verse.of.the.day;

import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.BackgroundColorSpan;
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

    private Spannable highlightText(String text, String searchQuery) {
        SpannableString spannable = new SpannableString(text);
        String lowerText = text.toLowerCase();
        String lowerQuery = searchQuery.toLowerCase();

        int startIdx = 0;
        while ((startIdx = lowerText.indexOf(lowerQuery, startIdx)) != -1) {
            int endIdx = startIdx + searchQuery.length();
            spannable.setSpan(new BackgroundColorSpan(0xFFFFFF00), startIdx, endIdx, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            startIdx = endIdx;
        }

        return spannable;
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
