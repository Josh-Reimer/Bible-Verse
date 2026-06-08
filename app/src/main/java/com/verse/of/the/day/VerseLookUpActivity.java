package com.verse.of.the.day;

import android.graphics.Typeface;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.widget.TextView;
import android.widget.LinearLayout;

public class VerseLookUpActivity extends AppCompatActivity{

	LinearLayout linear_layout;

	@Override
	protected void onCreate(Bundle SavedInstanceState){
		super.onCreate(SavedInstanceState);
		setContentView(R.layout.verse_lookup_activity);
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

		String pre_verse_textview_text = "";
		String verse_textview_text = "";
		String post_verse_textview_text = "";

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

		boolean pastTarget = false;
		for(int i = 0; i < str_verses.length; i++){
			if (pastTarget) {
				post_verse_textview_text += str_verses[i]+"\n";
				continue;
			}
			String[] lineParts = str_verses[i].split(":");
			if (lineParts.length < 2) continue;
			int verseNum = Integer.parseInt(lineParts[1]);
			if (verseNum < targetVerse){
				pre_verse_textview_text += "\n"+str_verses[i];
			} else if (verseNum == targetVerse) {
				verse_textview_text += str_verses[i];
				pastTarget = true;
			} else {
				post_verse_textview_text += str_verses[i]+"\n";
			}
		}
		pre_verse_textview.setText(pre_verse_textview_text);
		verse_textview.setText(verse_textview_text);
		post_verse_textview.setText(post_verse_textview_text);
	}

}