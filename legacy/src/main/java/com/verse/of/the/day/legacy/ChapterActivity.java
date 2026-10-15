package com.verse.of.the.day.legacy;

import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * A whole chapter, scrolled to and highlighting the verse it was opened on. Verse 0 opens
 * the chapter from the top with nothing highlighted, as :app's browse does.
 */
public class ChapterActivity extends LegacyActivity {

    static final String EXTRA_REF = "verse_ref";

    private final Bible bible = new Bible();
    private final Tools tools = new Tools();
    private final RedLetter redLetter = new RedLetter();

    private TextView textView;
    private ScrollView scroll;
    private Button previous;
    private Button next;
    private int book;
    private int chapter;
    private int verse;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chapter);
        textView = findViewById(R.id.chapter_text);
        scroll = findViewById(R.id.scroll);
        previous = findViewById(R.id.previous);
        next = findViewById(R.id.next);
        textView.setTextSize(verseTextSize() * 0.85f);

        String[] parts = getIntent().getStringExtra(EXTRA_REF).split(":");
        book = Integer.parseInt(parts[0]);
        chapter = Integer.parseInt(parts[1]);
        verse = Integer.parseInt(parts[2]);

        previous.setOnClickListener(v -> step(-1));
        next.setOnClickListener(v -> step(1));
    }

    @Override
    protected void onResume() {
        super.onResume();
        render(); // also picks up a translation change made under Settings
    }

    private void step(int direction) {
        chapter += direction;
        verse = 0;
        render();
    }

    private void render() {
        String bookFile = bible.books[book];
        setTitle(Translations.properBook(this, bookFile) + " " + chapter);
        previous.setEnabled(chapter > 1);
        next.setEnabled(chapter < bible.getBookLength(tools, this, bookFile));

        // A neutral tint: anything coloured fights the red letters sitting on it.
        int highlight = lightTheme() ? 0x1F000000 : 0x33FFFFFF;
        SpannableStringBuilder text = new SpannableStringBuilder();
        int targetStart = -1;
        for (String line : bible.getChapter(this, tools, bookFile, chapter).split("\n")) {
            String[] parts = line.split(":");
            if (parts.length < 3) continue;
            String number = parts[1].trim();
            String ref = book + ":" + chapter + ":" + number;

            if (text.length() > 0) text.append("\n\n");
            int start = text.length();
            text.append(number).append("  ");
            text.setSpan(new StyleSpan(Typeface.BOLD), start, start + number.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            text.setSpan(new RelativeSizeSpan(0.75f), start, start + number.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

            Spanned red = redLetter.getSpanned(this, ref);
            text.append(red != null ? red : MainActivity.verseText(line));
            if (number.equals(Integer.toString(verse))) {
                targetStart = start;
                text.setSpan(new BackgroundColorSpan(highlight), start, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        textView.setText(text);

        final int target = targetStart;
        scroll.post(() -> {
            Layout layout = textView.getLayout();
            int y = 0;
            if (target >= 0 && layout != null) {
                y = layout.getLineTop(layout.getLineForOffset(target)) + textView.getTop();
            }
            scroll.scrollTo(0, y);
        });
    }
}
