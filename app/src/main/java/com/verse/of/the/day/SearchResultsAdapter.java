package com.verse.of.the.day;

import android.content.Context;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SearchResultsAdapter extends RecyclerView.Adapter<SearchResultsAdapter.ViewHolder> {
    private List<SearchResult> results;
    private OnResultClickListener listener;
    private BookmarkListener bookmarkListener;
    private Map<String, Spannable> highlightCache = new HashMap<>();
    private final RedLetter redLetter = new RedLetter();

    public interface OnResultClickListener {
        void onResultClick(SearchResult result);
    }

    public interface BookmarkListener {
        boolean isBookmarked(SearchResult result);
        // Toggles the bookmark and returns the new bookmarked state.
        boolean toggleBookmark(SearchResult result);
    }

    public SearchResultsAdapter(List<SearchResult> results, OnResultClickListener listener, BookmarkListener bookmarkListener) {
        this.results = results;
        this.listener = listener;
        this.bookmarkListener = bookmarkListener;
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

        String cacheKey = result.verseReference + "|" + result.text + "|" + result.searchQuery;
        Spannable cached = highlightCache.get(cacheKey);
        if (cached == null) {
            cached = highlightText(baseText(holder.itemView.getContext(), result), result.searchQuery);
            highlightCache.put(cacheKey, cached);
        }
        holder.text.setText(cached);

        holder.itemView.setOnClickListener(v -> listener.onResultClick(result));

        setBookmarkIcon(holder.bookmark, bookmarkListener.isBookmarked(result));
        holder.bookmark.setOnClickListener(v ->
                setBookmarkIcon(holder.bookmark, bookmarkListener.toggleBookmark(result)));
    }

    private void setBookmarkIcon(ImageButton button, boolean bookmarked) {
        button.setImageResource(bookmarked ? R.drawable.bookmark_solid_48 : R.drawable.bookmark_border_48);
    }

    // Red-letter (words of Christ) rendering, same source as the main verse view;
    // falls back to the raw verse line when the verse has no red-letter markup.
    // The "chapter:verse: " prefix is re-added to match the plain result.text format.
    private CharSequence baseText(Context context, SearchResult result) {
        Spanned spanned = redLetter.getSpanned(context, result.verseReference);
        if (spanned == null) return result.text;
        String[] parts = result.verseReference.split(":");
        SpannableStringBuilder builder = new SpannableStringBuilder(parts[1] + ":" + parts[2] + ": ");
        builder.append(spanned);
        return builder;
    }

    // If the verse contains the full query as a consecutive phrase, highlight only that;
    // otherwise fall back to highlighting each token separately, mirroring SearchEngine's
    // word-by-word matching — a verse can match all tokens without containing the phrase.
    private Spannable highlightText(CharSequence text, String searchQuery) {
        SpannableString spannable = new SpannableString(text);
        String lowerText = spannable.toString().toLowerCase();
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

    // Highlights whole-word occurrences of the needle; when there are none, falls back to
    // substrings starting or ending on a word boundary (e.g. "begin" inside "beginning"),
    // and finally to buried mid-word occurrences (e.g. "mag" inside "image") — the search
    // matches those too, so every result must show why it matched.
    private void highlightOccurrences(SpannableString spannable, String lowerText, String lowerNeedle) {
        for (int requiredBoundaries = 2; requiredBoundaries >= 0; requiredBoundaries--) {
            if (highlightPass(spannable, lowerText, lowerNeedle, requiredBoundaries)) {
                return;
            }
        }
    }

    private boolean highlightPass(SpannableString spannable, String lowerText, String lowerNeedle, int requiredBoundaries) {
        boolean found = false;
        int startIdx = 0;
        while ((startIdx = lowerText.indexOf(lowerNeedle, startIdx)) != -1) {
            int endIdx = startIdx + lowerNeedle.length();
            boolean boundaryBefore = startIdx == 0 || !Character.isLetterOrDigit(lowerText.charAt(startIdx - 1));
            boolean boundaryAfter = endIdx >= lowerText.length() || !Character.isLetterOrDigit(lowerText.charAt(endIdx));
            if ((boundaryBefore ? 1 : 0) + (boundaryAfter ? 1 : 0) >= requiredBoundaries) {
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
        ImageButton bookmark;

        public ViewHolder(android.view.View itemView) {
            super(itemView);
            reference = itemView.findViewById(R.id.result_reference);
            text = itemView.findViewById(R.id.result_text);
            bookmark = itemView.findViewById(R.id.result_bookmark);
        }
    }
}
