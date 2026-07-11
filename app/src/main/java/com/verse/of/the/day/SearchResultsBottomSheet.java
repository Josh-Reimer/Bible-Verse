package com.verse.of.the.day;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
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
    private OnOutsideTapListener outsideTapListener;

    public interface OnResultSelectedListener {
        void onResultSelected(SearchResult result);
    }

    public interface OnOutsideTapListener {
        void onOutsideTap(float rawX, float rawY);
    }

    public static SearchResultsBottomSheet newInstance(List<SearchResult> results, OnResultSelectedListener listener,
                                                       OnOutsideTapListener outsideTapListener) {
        SearchResultsBottomSheet fragment = new SearchResultsBottomSheet();
        fragment.results = results;
        fragment.listener = listener;
        fragment.outsideTapListener = outsideTapListener;
        return fragment;
    }

    @Override
    public void onStart() {
        super.onStart();
        // The dialog swallows taps outside the sheet, so the host never learns where
        // they landed; report the tap so it can keep the search bar open when the
        // user taps the search box. The sheet itself dismisses either way.
        View touchOutside = getDialog() == null ? null
                : getDialog().findViewById(com.google.android.material.R.id.touch_outside);
        if (touchOutside != null) {
            touchOutside.setOnTouchListener((v, event) -> {
                if (event.getAction() == MotionEvent.ACTION_DOWN && isCancelable()) {
                    dismiss();
                    if (outsideTapListener != null) {
                        outsideTapListener.onOutsideTap(event.getRawX(), event.getRawY());
                    }
                    return true;
                }
                return false;
            });
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        setCancelable(true);
        return inflater.inflate(R.layout.bottom_sheet_search_results, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Fields are lost if the system recreates this fragment (process death,
        // theme change); dismiss instead of crashing on null.
        if (results == null || listener == null) {
            dismiss();
            return;
        }

        TextView resultsTitle = view.findViewById(R.id.results_title);
        RecyclerView recyclerView = view.findViewById(R.id.results_recycler_view);

        resultsTitle.setText("Found " + results.size() + " result" + (results.size() == 1 ? "" : "s"));

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        // Keep the sheet open so it's still there when the user returns from the
        // verse lookup activity; swipe-down or tapping outside dismisses it.
        SearchResultsAdapter adapter = new SearchResultsAdapter(results, result -> listener.onResultSelected(result));
        recyclerView.setAdapter(adapter);
    }
}
