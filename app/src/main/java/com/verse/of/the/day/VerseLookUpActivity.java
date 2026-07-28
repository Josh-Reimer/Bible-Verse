package com.verse.of.the.day;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Spanned;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.room.Room;

import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.LinearLayout;
import com.google.android.material.appbar.MaterialToolbar;

public class VerseLookUpActivity extends AppCompatActivity implements VerseActionsBottomSheet.Host {

	LinearLayout linear_layout;
	private bookmark_database db;
	private final Bible bible = new Bible();
	private final Tools tools = new Tools();
	private final RedLetter redLetter = new RedLetter();
	private final SimilarVerses similarVerses = new SimilarVerses();

	@Override
	protected void onCreate(Bundle SavedInstanceState){
		super.onCreate(SavedInstanceState);
		WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
		setContentView(R.layout.verse_lookup_activity);

		db = Room.databaseBuilder(getApplicationContext(),
				bookmark_database.class, "bookmarks-database").allowMainThreadQueries().build();

		MaterialToolbar toolbar = findViewById(R.id.topBar);
		setSupportActionBar(toolbar);
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);

		ViewCompat.setOnApplyWindowInsetsListener(toolbar, (v, insets) -> {
			int topInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
			v.setPadding(0, topInset, 0, 0);
			return insets;
		});

		ScrollView scrollView = findViewById(R.id.verse_lookup_scroll_view);
		ViewCompat.setOnApplyWindowInsetsListener(scrollView, (v, insets) -> {
			int bottomInset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
			v.setPadding(0, 0, 0, bottomInset);
			return insets;
		});
		linear_layout = findViewById(R.id.ll);

		String intentExtras = getIntent().getStringExtra("verse_ref");
		assert intentExtras != null;
		String[] parts = intentExtras.split(":");
		int bookIndex = Integer.parseInt(parts[0]);
		int chapterNum = Integer.parseInt(parts[1]);
		int targetVerse = Integer.parseInt(parts[2]);
		String book = bible.books[bookIndex];
		String properBook = Bible.getProperName(book);

		setTitle(properBook + " " + chapterNum);

		String[] str_verses = bible.getChapter(this, tools, book, chapterNum).split("\n");

		View targetRow = null;

		for (String line : str_verses) {
			String[] lineParts = line.split(":");
			if (lineParts.length < 2) continue;
			int verseNum;
			try { verseNum = Integer.parseInt(lineParts[1]); } catch (NumberFormatException e) { continue; }

			boolean isTarget = verseNum == targetVerse;
			View row = buildVerseRow(bookIndex, chapterNum, verseNum, line, isTarget);
			linear_layout.addView(row);
			if (isTarget) targetRow = row;
		}

		// Scroll so the highlighted verse sits near the top once layout is measured.
		final View finalTargetRow = targetRow;
		if (finalTargetRow != null) {
			scrollView.post(() -> {
				int target = finalTargetRow.getTop() - scrollView.getPaddingTop();
				scrollView.smoothScrollTo(0, Math.max(target, 0));
			});
		}
	}

	// Each verse is plain, tappable text; tapping opens a bottom sheet of actions for it.
	private View buildVerseRow(int bookIndex, int chapterNum, int verseNum, String rawLine, boolean isTarget) {
		float density = getResources().getDisplayMetrics().density;
		int pad = (int) (12 * density);

		TextView verseView = new TextView(this);
		LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT);

		String ref = bookIndex + ":" + chapterNum + ":" + verseNum;
		Spanned spanned = redLetter.getSpanned(this, ref);
		if (spanned != null) {
			verseView.setText(chapterNum + ":" + verseNum + ": ");
			verseView.append(spanned);
		} else {
			verseView.setText(rawLine);
		}

		if (isTarget) {
			verseView.setTextSize(21f);
			verseView.setTypeface(verseView.getTypeface(), Typeface.BOLD);
			// Outline the target verse rather than recolouring its text, so words-of-Christ
			// red highlighting stays legible within the selected verse.
			verseView.setBackgroundResource(R.drawable.verse_highlight_outline);
			verseView.setPadding(pad, pad, pad, pad);
			params.topMargin = pad;
			params.bottomMargin = pad;
		} else {
			verseView.setTextSize(20f);
			// Horizontal padding matches the target verse so the tap outline has room and the
			// text doesn't reflow when the outline appears; vertical padding spaces the verses.
			verseView.setPadding(pad, pad / 2, pad, pad / 2);
		}
		verseView.setLayoutParams(params);

		verseView.setClickable(true);
		// Ripple feedback drawn over the text (and over the target verse's outline).
		android.util.TypedValue fg = new android.util.TypedValue();
		getTheme().resolveAttribute(android.R.attr.selectableItemBackground, fg, true);
		verseView.setForeground(androidx.core.content.ContextCompat.getDrawable(this, fg.resourceId));
		verseView.setOnClickListener(v -> {
			highlightVerse(verseView);
			VerseActionsBottomSheet.newInstance(ref).show(getSupportFragmentManager(), "verse_actions");
		});

		return verseView;
	}

	// While a verse's actions sheet is open, outline that verse in orange so it's clear which
	// verse the sheet applies to; the outline is reverted when the sheet is dismissed.
	private View highlightedVerse;
	private android.graphics.drawable.Drawable highlightedVerseRestingBg;

	private void highlightVerse(View v) {
		clearVerseHighlight();
		highlightedVerse = v;
		highlightedVerseRestingBg = v.getBackground();
		swapBackgroundKeepingPadding(v, androidx.core.content.ContextCompat.getDrawable(
				this, R.drawable.verse_highlight_outline_tapped));
	}

	private void clearVerseHighlight() {
		if (highlightedVerse == null) return;
		swapBackgroundKeepingPadding(highlightedVerse, highlightedVerseRestingBg);
		highlightedVerse = null;
		highlightedVerseRestingBg = null;
	}

	// Setting a background can clobber a view's padding, so preserve and re-apply it.
	private void swapBackgroundKeepingPadding(View v, android.graphics.drawable.Drawable bg) {
		int l = v.getPaddingLeft(), t = v.getPaddingTop(), r = v.getPaddingRight(), b = v.getPaddingBottom();
		v.setBackground(bg);
		v.setPadding(l, t, r, b);
	}

	// ----- VerseActionsBottomSheet.Host -----

	@Override
	public boolean isVerseBookmarked(String ref) {
		return !db.bookmark_dao().getBookmark(ref).toString().equals("[]");
	}

	@Override
	public boolean toggleVerseBookmark(String ref) {
		if (isVerseBookmarked(ref)) {
			db.bookmark_dao().deleteBookmark(ref);
			return false;
		}
		Verse verse = new Verse(this, ref);
		db.bookmark_dao().insertAll(new bookmark(
				verse.full_text, verse.reference, verse.proper_book, verse.scripture_text));
		return true;
	}

	@Override
	public void shareVerse(String ref) {
		Verse verse = new Verse(this, ref);
		Intent sharingIntent = new Intent(Intent.ACTION_SEND);
		sharingIntent.setType("text/plain");
		sharingIntent.putExtra(Intent.EXTRA_TEXT, verse.full_text);
		startActivity(Intent.createChooser(sharingIntent, "Share via"));
	}

	@Override
	public String verseActionLabel(String ref) {
		String[] p = ref.split(":");
		int bi = Integer.parseInt(p[0]);
		return Bible.getProperName(bible.books[bi]) + " " + p[1] + ":" + p[2];
	}

	// The verse itself, for the top of the actions sheet: red-letter aware, current translation.
	@Override
	public CharSequence verseActionText(String ref) {
		Spanned spanned = redLetter.getSpanned(this, ref);
		return spanned != null ? spanned : stripReferencePrefix(new Verse(this, ref).scripture_text);
	}

	@Override
	public void onVerseActionsDismissed() {
		clearVerseHighlight();
	}

	// Called off the main thread by the sheet: resolve each precomputed neighbor into display
	// data (label + red-letter-aware text) in the user's current translation. The translation
	// carried alongside is the one whose wording surfaced the pairing offline, which is not
	// necessarily the one the text is rendered in.
	@Override
	public java.util.List<VerseActionsBottomSheet.SimilarVerse> loadSimilarVerses(String ref) {
		java.util.List<VerseActionsBottomSheet.SimilarVerse> out = new java.util.ArrayList<>();
		for (SimilarVerses.Neighbor neighbor : similarVerses.getSimilar(this, ref)) {
			out.add(new VerseActionsBottomSheet.SimilarVerse(
					neighbor.ref,
					verseActionLabel(neighbor.ref),
					neighbor.translation.toUpperCase(java.util.Locale.US),
					verseActionText(neighbor.ref)));
		}
		return out;
	}

	@Override
	public void openSimilarVerse(String ref) {
		Intent intent = new Intent(this, VerseLookUpActivity.class);
		intent.putExtra("verse_ref", ref);
		startActivity(intent);
	}

	// A raw verse line is "chapter:verse: text"; strip the reference prefix for clean display.
	private static String stripReferencePrefix(String verseLine) {
		int first = verseLine.indexOf(':');
		int second = first < 0 ? -1 : verseLine.indexOf(':', first + 1);
		return second < 0 ? verseLine : verseLine.substring(second + 1).trim();
	}

	@Override
	public boolean onSupportNavigateUp() {
		finish();
		return true;
	}

}
