package com.verse.of.the.day;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

// A swipe-away sheet of actions for a single verse, raised when a verse is tapped in the
// chapter lookup. The host activity owns the bookmark DB and share intent; the sheet only
// carries the verse reference and reflects/toggles its state.
public class VerseActionsBottomSheet extends BottomSheetDialogFragment {

	private static final String ARG_REF = "verse_ref";
	private Host host;

	public interface Host {
		boolean isVerseBookmarked(String ref);
		boolean toggleVerseBookmark(String ref); // returns the new bookmarked state
		void shareVerse(String ref);
		String verseActionLabel(String ref); // e.g. "JOHN 4:27"
		void onVerseActionsDismissed(); // clear the tapped-verse highlight
	}

	static VerseActionsBottomSheet newInstance(String ref) {
		VerseActionsBottomSheet sheet = new VerseActionsBottomSheet();
		Bundle args = new Bundle();
		args.putString(ARG_REF, ref);
		sheet.setArguments(args);
		return sheet;
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
	public void onDismiss(android.content.DialogInterface dialog) {
		// Fires for every dismissal — swipe-down, tap-outside, back, and the share tap's
		// programmatic dismiss() — so the tapped-verse highlight always gets cleared.
		if (host != null) {
			host.onVerseActionsDismissed();
		}
		super.onDismiss(dialog);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		setCancelable(true);
		return inflater.inflate(R.layout.bottom_sheet_verse_actions, container, false);
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		String ref = getArguments() == null ? null : getArguments().getString(ARG_REF);
		if (host == null || ref == null) {
			dismissAllowingStateLoss();
			return;
		}

		TextView title = view.findViewById(R.id.verse_actions_title);
		title.setText(host.verseActionLabel(ref));

		ImageView bookmarkIcon = view.findViewById(R.id.bookmark_icon);
		TextView bookmarkLabel = view.findViewById(R.id.bookmark_label);
		updateBookmarkRow(bookmarkIcon, bookmarkLabel, host.isVerseBookmarked(ref));

		view.findViewById(R.id.action_bookmark).setOnClickListener(v ->
				updateBookmarkRow(bookmarkIcon, bookmarkLabel, host.toggleVerseBookmark(ref)));

		view.findViewById(R.id.action_share).setOnClickListener(v -> {
			host.shareVerse(ref);
			dismiss();
		});
	}

	private void updateBookmarkRow(ImageView icon, TextView label, boolean bookmarked) {
		icon.setImageResource(bookmarked ? R.drawable.bookmark_solid_48 : R.drawable.bookmark_border_48);
		label.setText(bookmarked ? "Remove bookmark" : "Add bookmark");
	}
}
