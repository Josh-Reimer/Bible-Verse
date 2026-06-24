package com.verse.of.the.day;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import java.util.List;

public class SearchResultsBottomSheet extends BottomSheetDialogFragment {
    private List<SearchResult> results;
    private OnResultSelectedListener listener;

    public interface OnResultSelectedListener {
        void onResultSelected(SearchResult result);
    }

    public static SearchResultsBottomSheet newInstance(List<SearchResult> results, OnResultSelectedListener listener) {
        SearchResultsBottomSheet fragment = new SearchResultsBottomSheet();
        fragment.results = results;
        fragment.listener = listener;
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_search_results, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        TextView resultsTitle = view.findViewById(R.id.results_title);
        RecyclerView recyclerView = view.findViewById(R.id.results_recycler_view);

        resultsTitle.setText("Found " + results.size() + " result" + (results.size() == 1 ? "" : "s"));

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        SearchResultsAdapter adapter = new SearchResultsAdapter(results, result -> {
            listener.onResultSelected(result);
            dismiss();
        });
        recyclerView.setAdapter(adapter);
    }
}
