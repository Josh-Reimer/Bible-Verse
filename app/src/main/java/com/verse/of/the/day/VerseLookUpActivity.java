package com.verse.of.the.day;

import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Spanned;
import android.text.SpannableStringBuilder;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.LinearLayout;
import com.google.android.material.appbar.MaterialToolbar;

public class VerseLookUpActivity extends AppCompatActivity{

	LinearLayout linear_layout;

	@Override
	protected void onCreate(Bundle SavedInstanceState){
		super.onCreate(SavedInstanceState);
		WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
		setContentView(R.layout.verse_lookup_activity);

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
		Bible bible = new Bible();
		Tools tools = new Tools();
		linear_layout = findViewById(R.id.ll);

		TextView pre_verse_textview = new TextView(this);
		TextView verse_textview = new TextView(this);
		TextView post_verse_textview = new TextView(this);

		linear_layout.addView(pre_verse_textview);
		linear_layout.addView(verse_textview);
		linear_layout.addView(post_verse_textview);

		pre_verse_textview.setTextSize(20f);

		verse_textview.setTextSize(21f);
		verse_textview.setTypeface(verse_textview.getTypeface(), Typeface.BOLD);

		int color = ContextCompat.getColor(this, R.color.green_700_primary);
		verse_textview.setTextColor(color);

		post_verse_textview.setTextSize(20f);

		String intentExtras = getIntent().getStringExtra("verse_ref");
		assert intentExtras != null;
		String[] parts = intentExtras.split(":");
		int bookIndex = Integer.parseInt(parts[0]);
		int chapterNum = Integer.parseInt(parts[1]);
		int targetVerse = Integer.parseInt(parts[2]);
		String book = bible.books[bookIndex];
		String properBook = Bible.getProperName(book);

		setTitle(properBook + " " + chapterNum);

		RedLetter redLetter = new RedLetter();
		String[] str_verses = bible.getChapter(this, tools, book, chapterNum).split("\n");

		SpannableStringBuilder preBuilder = new SpannableStringBuilder();
		SpannableStringBuilder postBuilder = new SpannableStringBuilder();
		String verse_textview_text = "";
		boolean pastTarget = false;

		for (String line : str_verses) {
			String[] lineParts = line.split(":");
			if (lineParts.length < 2) continue;
			int verseNum;
			try { verseNum = Integer.parseInt(lineParts[1]); } catch (NumberFormatException e) { continue; }

			if (!pastTarget && verseNum == targetVerse) {
				verse_textview_text = line;
				pastTarget = true;
				continue;
			}

			SpannableStringBuilder target = pastTarget ? postBuilder : preBuilder;
			String ref = bookIndex + ":" + chapterNum + ":" + verseNum;
			Spanned spanned = redLetter.getSpanned(this, ref);
			if (!pastTarget) target.append("\n");
			if (spanned != null) {
				target.append(chapterNum + ":" + verseNum + ": ");
				target.append(spanned);
			} else {
				target.append(line);
			}
			if (pastTarget) target.append("\n");
		}

		// Target verse
		String targetRef = bookIndex + ":" + chapterNum + ":" + targetVerse;
		Spanned targetSpanned = redLetter.getSpanned(this, targetRef);
		if (targetSpanned != null) {
			verse_textview.setText(chapterNum + ":" + targetVerse + ": ");
			verse_textview.append(targetSpanned);
		} else {
			verse_textview.setText(verse_textview_text);
		}

		pre_verse_textview.setText(preBuilder);
		post_verse_textview.setText(postBuilder);
	}

	@Override
	public boolean onSupportNavigateUp() {
		finish();
		return true;
	}

}