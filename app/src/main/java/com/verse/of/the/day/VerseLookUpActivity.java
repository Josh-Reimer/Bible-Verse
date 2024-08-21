package com.verse.of.the.day;

import android.graphics.Typeface;
import android.os.Bundle;
import android.content.Context;
import androidx.appcompat.app.AppCompatActivity;

import android.util.TypedValue;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.LinearLayout;
import java.util.ArrayList;
import java.util.Objects;

public class VerseLookUpActivity extends AppCompatActivity{

	LinearLayout linear_layout;
	Verse verse_for_lookup;

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
		TypedValue typedValue = new TypedValue();
		getTheme().resolveAttribute(android.R.attr.colorPrimary, typedValue, true);
		int color = typedValue.data;
		verse_textview.setTextColor(color);

		post_verse_textview.setTextSize(20f);

		String pre_verse_textview_text = "";
		String verse_textview_text = "";
		String post_verse_textview_text = "";

		String intentExtras = getIntent().getStringExtra("verse_ref");

        assert intentExtras != null;
        verse_for_lookup = getVerseFromMainActivity(this,intentExtras);

		String chapter = bible.getChapter(this,tools,verse_for_lookup.book, verse_for_lookup.chapter);
		String[] str_verses = chapter.split("\n");

		for(int i = 0; i < str_verses.length; i++){
			Verse current_verse = new Verse(this,verse_for_lookup.book_int,str_verses[i]);
			if (current_verse.verse < verse_for_lookup.verse){
				pre_verse_textview_text += "\n"+str_verses[i];
			}
			if(Objects.equals(verse_for_lookup.reference, current_verse.reference)) {
				verse_textview_text += str_verses[i];
			}
			if (current_verse.verse > verse_for_lookup.verse){
				post_verse_textview_text += str_verses[i]+"\n";
			}
		}
		pre_verse_textview.setText(pre_verse_textview_text);
		verse_textview.setText(verse_textview_text);
		post_verse_textview.setText(post_verse_textview_text);
	}

	Verse getVerseFromMainActivity(Context c,String intent_extras){
		String[] thirds;
		thirds = intent_extras.split(":");
		Verse v = new Verse(c,Integer.parseInt(thirds[0]),Integer.parseInt(thirds[1]),Integer.parseInt(thirds[2]));
		return v;
	}

}