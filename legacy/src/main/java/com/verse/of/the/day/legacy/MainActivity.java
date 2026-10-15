package com.verse.of.the.day.legacy;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.Spanned;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Today's verse on a cold start, a die for a random one, and the way into everything else.
 */
public class MainActivity extends LegacyActivity {

    static final String EXTRA_REF = "verse_ref";
    private static final String STATE_REF = "current_ref";

    private final RedLetter redLetter = new RedLetter();
    private final Tools tools = new Tools();
    private final Bible bible = new Bible();

    private TextView referenceView;
    private TextView verseView;
    private TextView translationView;
    private ScrollView scroll;
    private Button bookmarkButton;
    private String currentRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        referenceView = findViewById(R.id.reference);
        verseView = findViewById(R.id.verse);
        translationView = findViewById(R.id.translation);
        scroll = findViewById(R.id.scroll);
        bookmarkButton = findViewById(R.id.bookmark);

        float size = verseTextSize();
        verseView.setTextSize(size);
        referenceView.setTextSize(size * 0.8f);

        findViewById(R.id.random).setOnClickListener(v -> show(DailyVerse.random(this)));
        findViewById(R.id.chapter).setOnClickListener(v -> openChapter(currentRef));
        findViewById(R.id.share).setOnClickListener(v -> share());
        bookmarkButton.setOnClickListener(v -> toggleBookmark());

        String ref = savedInstanceState != null ? savedInstanceState.getString(STATE_REF)
                : getIntent().getStringExtra(EXTRA_REF);
        currentRef = ref != null ? ref : DailyVerse.today(this);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        String ref = intent.getStringExtra(EXTRA_REF);
        if (ref != null) show(new Verse(this, ref));
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Rendered here rather than in onCreate so a translation changed under Settings
        // is picked up on the way back.
        show(new Verse(this, currentRef));
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_REF, currentRef);
    }

    void show(Verse v) {
        currentRef = v.reference;
        referenceView.setText(v.proper_book + " " + v.chapter + ":" + v.verse);
        Spanned spanned = redLetter.getSpanned(this, v.reference);
        verseView.setText(spanned != null ? spanned : verseText(v.scripture_text));
        translationView.setText(Translations.currentEntry(this).fullName);
        bookmarkButton.setText(Bookmarks.contains(this, v.reference)
                ? R.string.remove_bookmark : R.string.bookmark);
        scroll.scrollTo(0, 0);
    }

    /** A verse line without its leading "chapter:verse:" — the reference is shown above it. */
    static String verseText(String line) {
        int first = line.indexOf(':');
        int second = first < 0 ? -1 : line.indexOf(':', first + 1);
        return second < 0 ? line : line.substring(second + 1).trim();
    }

    private void toggleBookmark() {
        if (Bookmarks.contains(this, currentRef)) {
            Bookmarks.remove(this, currentRef);
            bookmarkButton.setText(R.string.bookmark);
        } else {
            Bookmarks.add(this, currentRef);
            bookmarkButton.setText(R.string.remove_bookmark);
            Toast.makeText(this, R.string.bookmarked, Toast.LENGTH_SHORT).show();
        }
    }

    private void share() {
        String text = verseView.getText() + "\n— " + referenceView.getText();
        Intent send = new Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, text);
        try {
            startActivity(Intent.createChooser(send, getString(R.string.share)));
        } catch (ActivityNotFoundException e) {
            // A head unit may have nothing that accepts text.
            Toast.makeText(this, R.string.nothing_to_share_with, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.go_to) {
            showGoTo();
        } else if (id == R.id.browse) {
            startActivity(new Intent(this, BrowseActivity.class));
        } else if (id == R.id.bookmarks) {
            startActivity(new Intent(this, BookmarksActivity.class));
        } else if (id == R.id.settings) {
            startActivity(new Intent(this, SettingsActivity.class));
        } else {
            return super.onOptionsItemSelected(item);
        }
        return true;
    }

    /** "John 3:16" opens the verse here; "Psalms 23" opens the chapter. */
    private void showGoTo() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint(R.string.go_to_hint);
        input.setImeOptions(EditorInfo.IME_ACTION_GO);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.go_to)
                .setView(input)
                .setPositiveButton(R.string.go, null)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        View.OnClickListener go = v -> {
            if (goTo(input.getText().toString())) {
                dialog.dismiss();
            } else {
                input.setError(getString(R.string.not_a_reference));
            }
        };
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                go.onClick(v);
                return true;
            }
            return false;
        });
        // Set after show() so a bad reference keeps the dialog open to correct it.
        dialog.setOnShowListener(d -> dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(go));
        dialog.show();
    }

    private boolean goTo(String query) {
        VerseReferenceParser.Reference ref = VerseReferenceParser.parse(this, tools, bible, query);
        if (ref == null) return false;
        if (ref.verse == 0) {
            openChapter(ref.bookIndex + ":" + ref.chapter + ":0");
        } else {
            show(new Verse(this, ref.bookIndex, ref.chapter, ref.verse));
        }
        return true;
    }
}
