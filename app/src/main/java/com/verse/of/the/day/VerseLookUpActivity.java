package com.verse.of.the.day;

import android.os.Bundle;
import android.content.Context;
import androidx.appcompat.app.AppCompatActivity;

import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.LinearLayout;
import java.util.ArrayList;
public class VerseLookUpActivity extends AppCompatActivity{

	TextView scrolled_text;
	LinearLayout ll;
	Verse verse_for_lookup;

	@Override
	protected void onCreate(Bundle SavedInstanceState){
		super.onCreate(SavedInstanceState);
		setContentView(R.layout.verse_lookup_activity);
		Bible bible = new Bible();
		Tools tools = new Tools();
		ll = findViewById(R.id.ll);

		ArrayList<Verse> verses = new ArrayList<Verse>();
		ArrayList<TextView> tvs;

		
		TextView scrolled_text = new TextView(this);
		LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.MATCH_PARENT
		);

		//scrolled_text.setId(0);
		scrolled_text.setLayoutParams(params);
		ll.addView(scrolled_text);
		String intentExtras = getIntent().getStringExtra("verse_ref");

		verse_for_lookup = getVerseFromMainActivity(this,intentExtras);

		String chapter = bible.getChapter(this,tools,verse_for_lookup.book, verse_for_lookup.chapter);
		String[] str_verses = chapter.split("\n");
		for(int i = 0; i < str_verses.length; i++){
			Verse c = new Verse(this,verse_for_lookup.book_int,str_verses[i]);
			verses.add(c);
		}
		scrolled_text.setText(chapter);
	}

	Verse getVerseFromMainActivity(Context c,String intent_extras){
		String[] thirds;
		thirds = intent_extras.split(":");
		Verse v = new Verse(c,Integer.parseInt(thirds[0]),Integer.parseInt(thirds[1]),Integer.parseInt(thirds[2]));
		return v;
	}

}