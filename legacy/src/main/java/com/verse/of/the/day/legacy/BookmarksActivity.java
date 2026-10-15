package com.verse.of.the.day.legacy;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.graphics.Typeface;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Bookmarked verses, newest first. Tap to read in context, long-press to remove. */
public class BookmarksActivity extends LegacyActivity {

    private ListView list;
    private TextView empty;
    private List<String> refs = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bookmarks);
        list = findViewById(R.id.list);
        empty = findViewById(R.id.empty);
        list.setEmptyView(empty);

        list.setOnItemClickListener((parent, view, position, id) -> openChapter(refs.get(position)));
        list.setOnItemLongClickListener((parent, view, position, id) -> {
            String ref = refs.get(position);
            new AlertDialog.Builder(this)
                    .setMessage(R.string.remove_bookmark_question)
                    .setPositiveButton(R.string.remove, (d, which) -> {
                        Bookmarks.remove(this, ref);
                        load();
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
            return true;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
    }

    private void load() {
        refs = Bookmarks.all(this);
        List<CharSequence> rows = new ArrayList<>();
        for (String ref : refs) {
            Verse v = new Verse(this, ref);
            SpannableStringBuilder row = new SpannableStringBuilder(v.proper_book + " " + v.chapter + ":" + v.verse);
            row.setSpan(new StyleSpan(Typeface.BOLD), 0, row.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            row.append("\n").append(MainActivity.verseText(v.scripture_text));
            rows.add(row);
        }
        list.setAdapter(new ArrayAdapter<>(this, R.layout.list_item, rows));
    }
}
