package com.verse.of.the.day;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import java.util.List;

public class SearchResultsBottomSheet extends BottomSheetDialogFragment {
    // Results live in an activity-scoped ViewModel (never in a Bundle — large
    // result sets exceed the binder transaction limit when the activity stops)
    // and callbacks are re-fetched from the host activity in onAttach, so the
    // sheet survives system recreation (rotation, dark/light theme change)
    // instead of coming back empty. After process death the ViewModel is empty
    // and the restored sheet dismisses itself.
    private List<SearchResult> results;
    private Host host;

    public interface Host {
        void onSearchResultSelected(SearchResult result);
        SearchResultsAdapter.BookmarkListener getSearchBookmarkListener();
        void onSearchSheetOutsideTap(float rawX, float rawY);
        void onSearchSheetCancelled();
    }

    public static SearchResultsBottomSheet newInstance() {
        return new SearchResultsBottomSheet();
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof Host) {
            host = (Host) context;
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        host = null;
    }

    @Override
    public void onCancel(android.content.DialogInterface dialog) {
        super.onCancel(dialog);
        // Fires on swipe-down and back-press but not on programmatic dismiss(),
        // so tap-outside handling and search-replacing stay unaffected.
        if (host != null) {
            host.onSearchSheetCancelled();
        }
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
                    if (host != null) {
                        host.onSearchSheetOutsideTap(event.getRawX(), event.getRawY());
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

        results = new ViewModelProvider(requireActivity()).get(SearchResultsViewModel.class).results;
        if (results == null || host == null) {
            dismissAllowingStateLoss();
            return;
        }

        TextView resultsTitle = view.findViewById(R.id.results_title);
        RecyclerView recyclerView = view.findViewById(R.id.results_recycler_view);

        resultsTitle.setText("Found " + results.size() + " result" + (results.size() == 1 ? "" : "s"));

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        // Keep the sheet open so it's still there when the user returns from the
        // verse lookup activity; swipe-down or tapping outside dismisses it.
        SearchResultsAdapter adapter = new SearchResultsAdapter(results,
                result -> host.onSearchResultSelected(result), host.getSearchBookmarkListener());
        recyclerView.setAdapter(adapter);
    }
}
