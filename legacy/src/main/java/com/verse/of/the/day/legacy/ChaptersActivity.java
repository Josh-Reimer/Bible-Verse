package com.verse.of.the.day.legacy;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.GridView;

/** A book's chapters as a grid of numbers; picking one opens it from the top. */
public class ChaptersActivity extends LegacyActivity {

    static final String EXTRA_BOOK = "book";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chapters);
        int book = getIntent().getIntExtra(EXTRA_BOOK, 0);
        Bible bible = new Bible();
        setTitle(Translations.properBook(this, bible.books[book]));

        int count = bible.getBookLength(new Tools(), this, bible.books[book]);
        Integer[] chapters = new Integer[count];
        for (int i = 0; i < count; i++) chapters[i] = i + 1;

        GridView grid = findViewById(R.id.grid);
        grid.setAdapter(new ArrayAdapter<>(this, R.layout.chapter_item, chapters));
        // Verse 0 matches no verse, so the chapter opens at the top with nothing highlighted.
        grid.setOnItemClickListener((parent, view, position, id) ->
                openChapter(book + ":" + (position + 1) + ":0"));
    }
}
