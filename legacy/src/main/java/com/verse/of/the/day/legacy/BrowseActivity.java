package com.verse.of.the.day.legacy;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;

/** The 66 books, in the current translation's names. */
public class BrowseActivity extends LegacyActivity {

    private ListView list;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        list = new ListView(this);
        setContentView(list);
        list.setOnItemClickListener((parent, view, position, id) ->
                startActivity(new Intent(this, ChaptersActivity.class)
                        .putExtra(ChaptersActivity.EXTRA_BOOK, position)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        String[] books = new Bible().books;
        String[] names = new String[books.length];
        for (int i = 0; i < books.length; i++) {
            names[i] = Translations.properBook(this, books[i]);
        }
        int position = list.getFirstVisiblePosition();
        list.setAdapter(new ArrayAdapter<>(this, R.layout.list_item, names));
        list.setSelection(position);
    }
}
