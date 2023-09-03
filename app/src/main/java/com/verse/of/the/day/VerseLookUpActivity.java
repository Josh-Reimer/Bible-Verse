package com.verse.of.the.day;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import android.widget.TextView;

public class VerseLookUpActivity extends AppCompatActivity{
	
	private TextView scrolled_text;
	
	@Override
	protected void onCreate(Bundle SavedInstanceState){
		super.onCreate(SavedInstanceState);
		setContentView(R.layout.verse_lookup_activity);
		scrolled_text = findViewById(R.id.scrolled_text);
		Tools tools = new Tools();
		String test_text = tools.getFile(this,"jude.txt");
		scrolled_text.setText(test_text);
	}	
}