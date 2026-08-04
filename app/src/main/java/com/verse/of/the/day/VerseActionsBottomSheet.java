package com.verse.of.the.day;

import android.content.Context;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.facebook.shimmer.ShimmerFrameLayout;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.Collections;
import java.util.List;

// A swipe-away sheet of actions for a single verse, raised when a verse is tapped in the
// chapter lookup. The host activity owns the bookmark DB and share intent; the sheet only
// carries the verse reference and reflects/toggles its state. Below the actions it shows a
// short list of semantically similar verses, loaded on a worker thread and shimmered in.
public class VerseActionsBottomSheet extends BottomSheetDialogFragment {

	private static final String ARG_REF = "verse_ref";
	private static final int MAX_SIMILAR = 10;
	// The neighbor lookup is essentially instant, so hold the shimmer for a deliberate
	// minimum so the animation actually registers as an animation and not a flicker.
	private static final long MIN_SHIMMER_MS = 800L;

	private Host host;

	// A similar verse prepared for display by the host (off the main thread): its reference,
	// a human label ("JOHN 3:16"), the translation whose wording surfaced it ("KJV"), and the
	// verse text as a CharSequence (red-letter Spanned when applicable, plain text otherwise).
	public static class SimilarVerse {
		final String ref;
		final String label;
		final String translation;
		final CharSequence text;

		public SimilarVerse(String ref, String label, String translation, CharSequence text) {
			this.ref = ref;
			this.label = label;
			this.translation = translation;
			this.text = text;
		}
	}

	public interface Host {
		boolean isVerseBookmarked(String ref);
		boolean toggleVerseBookmark(String ref); // returns the new bookmarked state
		void shareVerse(String ref);
		String verseActionLabel(String ref); // e.g. "JOHN 4:27"
		CharSequence verseActionText(String ref); // the verse itself, red-letter aware
		void onVerseActionsDismissed(); // clear the tapped-verse highlight
		List<SimilarVerse> loadSimilarVerses(String ref); // heavy (file/red-letter reads) — off main thread
		void openSimilarVerse(String ref); // open that verse's chapter
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

		TextView verseText = view.findViewById(R.id.verse_actions_text);
		verseText.setText(host.verseActionText(ref));

		ImageView bookmarkIcon = view.findViewById(R.id.bookmark_icon);
		TextView bookmarkLabel = view.findViewById(R.id.bookmark_label);
		updateBookmarkRow(bookmarkIcon, bookmarkLabel, host.isVerseBookmarked(ref));

		view.findViewById(R.id.action_bookmark).setOnClickListener(v ->
				updateBookmarkRow(bookmarkIcon, bookmarkLabel, host.toggleVerseBookmark(ref)));

		view.findViewById(R.id.action_share).setOnClickListener(v -> {
			host.shareVerse(ref);
			dismiss();
		});

		loadSimilarVerses(view, ref);
	}

	private void updateBookmarkRow(ImageView icon, TextView label, boolean bookmarked) {
		icon.setImageResource(bookmarked ? R.drawable.bookmark_solid_48 : R.drawable.bookmark_border_48);
		label.setText(bookmarked ? R.string.remove_bookmark : R.string.add_bookmark);
	}

	// Kick off the neighbor lookup on a worker thread; shimmer runs meanwhile, then the real
	// verses fade in. Host reference is captured up front so a detach mid-load can't NPE.
	private void loadSimilarVerses(View root, String ref) {
		ShimmerFrameLayout shimmer = root.findViewById(R.id.shimmer_similar);
		LinearLayout container = root.findViewById(R.id.similar_verses_container);
		shimmer.startShimmer();

		final Host h = host;
		final long startAt = SystemClock.uptimeMillis();
		new Thread(() -> {
			final List<SimilarVerse> results = h == null ? Collections.emptyList() : h.loadSimilarVerses(ref);
			long remaining = MIN_SHIMMER_MS - (SystemClock.uptimeMillis() - startAt);
			if (remaining > 0) {
				try { Thread.sleep(remaining); } catch (InterruptedException ignored) {}
			}
			View v = getView();
			if (v == null) return; // sheet already gone
			v.post(() -> revealSimilarVerses(shimmer, container, results));
		}).start();
	}

	private void revealSimilarVerses(ShimmerFrameLayout shimmer, LinearLayout container, List<SimilarVerse> results) {
		if (!isAdded()) return;
		Context ctx = getContext();
		if (ctx == null) return;

		LayoutInflater inflater = LayoutInflater.from(ctx);
		container.removeAllViews();

		if (results.isEmpty()) {
			TextView empty = new TextView(ctx);
			empty.setText("No similar verses found.");
			empty.setTextSize(15f);
			android.util.TypedValue onSurface = new android.util.TypedValue();
			ctx.getTheme().resolveAttribute(com.google.android.material.R.attr.colorOnSurface, onSurface, true);
			empty.setTextColor(onSurface.data);
			int pad = Math.round(20 * ctx.getResources().getDisplayMetrics().density);
			empty.setPadding(pad, pad / 2, pad, pad / 2);
			empty.setAlpha(0.7f);
			container.addView(empty);
		} else {
			int shown = 0;
			for (SimilarVerse sv : results) {
				if (shown >= MAX_SIMILAR) break;
				View row = inflater.inflate(R.layout.similar_verse_row, container, false);
				((TextView) row.findViewById(R.id.similar_ref)).setText(sv.label);
				((TextView) row.findViewById(R.id.similar_translation)).setText(sv.translation);
				((TextView) row.findViewById(R.id.similar_text)).setText(sv.text);
				row.setOnClickListener(v -> {
					if (host != null) host.openSimilarVerse(sv.ref);
					dismiss();
				});
				container.addView(row);
				shown++;
			}
		}

		shimmer.stopShimmer();
		shimmer.setVisibility(View.GONE);
		container.setAlpha(0f);
		container.animate().alpha(1f).setDuration(450).start();
	}
}
