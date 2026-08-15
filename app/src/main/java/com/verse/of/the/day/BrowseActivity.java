package com.verse.of.the.day;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Browse the whole Bible: the 66 books, then the chapters of the book picked, then the
 * chapter itself in {@link VerseLookUpActivity} — the same reader every other screen
 * opens, so red-letter text, the verse actions sheet, bookmarking and sharing all come
 * with it.
 *
 * Both stages live in this one Activity and swap the RecyclerView's adapter and layout
 * manager; back (and the toolbar's up arrow) steps from the grid out to the list before
 * it leaves the screen.
 */
public class BrowseActivity extends AppCompatActivity
		implements BrowseBooksAdapter.OnBookClickListener, BrowseChaptersAdapter.OnChapterClickListener {

	/** The book/chapter last opened from here, so browsing resumes where it left off. */
	public static final String PREF_LAST_BOOK = "browse_last_book";
	public static final String PREF_LAST_CHAPTER = "browse_last_chapter";

	private static final String STATE_OPEN_BOOK = "browse_open_book";
	// Matthew opens the New Testament; everything before it is the Old.
	private static final int FIRST_NEW_TESTAMENT_BOOK = 39;
	private static final int CHAPTER_GRID_COLUMNS = 5;
	private static final int CHAPTER_TILE_MIN_DP = 96;

	private final Bible bible = new Bible();
	private final Tools tools = new Tools();

	private EditText filterField;
	private RecyclerView recyclerView;
	private TextView emptyIndicator;

	private BrowseBooksAdapter booksAdapter;
	private BrowseChaptersAdapter chaptersAdapter;
	private OnBackPressedCallback backCallback;

	/** -1 while the book list is showing, otherwise the book whose chapters are. */
	private int openBook = -1;
	private String[] displayNames;
	private String[] filterNames;
	private String namesTranslation;
	private final Map<Integer, Integer> chapterCounts = new HashMap<>();

	// getBookLength() reads a whole book file, so the count behind the grid is fetched here.
	private final ExecutorService bookExecutor = Executors.newSingleThreadExecutor();

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
		setContentView(R.layout.browse_activity);

		MaterialToolbar toolbar = findViewById(R.id.browse_toolbar);
		setSupportActionBar(toolbar);
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);

		ViewCompat.setOnApplyWindowInsetsListener(toolbar, (v, insets) -> {
			int topInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
			v.setPadding(0, topInset, 0, 0);
			return insets;
		});

		filterField = findViewById(R.id.browse_filter);
		recyclerView = findViewById(R.id.browse_recyclerview);
		emptyIndicator = findViewById(R.id.browse_empty);

		ViewCompat.setOnApplyWindowInsetsListener(recyclerView, (v, insets) -> {
			int bottomInset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
			v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), bottomInset);
			return insets;
		});

		booksAdapter = new BrowseBooksAdapter(this);
		chaptersAdapter = new BrowseChaptersAdapter(this);

		loadBookNames();

		filterField.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) { }

			@Override
			public void afterTextChanged(Editable s) {
				if (openBook < 0) showBooks();
			}
		});

		// Back leaves the grid for the book list first. The callback is enabled only
		// while the grid is showing, so from the book list back still leaves the screen.
		backCallback = new OnBackPressedCallback(false) {
			@Override
			public void handleOnBackPressed() {
				showBooks();
			}
		};
		getOnBackPressedDispatcher().addCallback(this, backCallback);

		int restoreBook = savedInstanceState != null
				? savedInstanceState.getInt(STATE_OPEN_BOOK, -1)
				// Reopen at the book last read from here; up and back both still step
				// out to the full list.
				: prefs().getInt(PREF_LAST_BOOK, -1);

		if (restoreBook >= 0 && restoreBook < bible.books.length) {
			showChapters(restoreBook);
		} else {
			showBooks();
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		// Settings can change the translation while this screen is stacked under it; the
		// book names follow it, the same way the reader's text does.
		boolean translationChanged = !Translations.current(this).equals(namesTranslation);
		if (translationChanged) {
			loadBookNames();
			chapterCounts.clear();
		}

		if (openBook < 0) {
			if (translationChanged) showBooks();
		} else if (translationChanged) {
			showChapters(openBook); // re-titles the screen in the new language
		} else {
			// Coming back from the reader: outline the chapter it was just opened at.
			Integer count = chapterCounts.get(openBook);
			if (count != null) showChapterGrid(count, lastReadChapter(openBook));
		}
	}

	@Override
	protected void onSaveInstanceState(@NonNull Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putInt(STATE_OPEN_BOOK, openBook);
	}

	@Override
	protected void onDestroy() {
		bookExecutor.shutdownNow();
		super.onDestroy();
	}

	private SharedPreferences prefs() {
		return getSharedPreferences("settings", Context.MODE_PRIVATE);
	}

	/** The chapter last opened in this book from the browse screen, or -1. */
	private int lastReadChapter(int bookIndex) {
		SharedPreferences prefs = prefs();
		return prefs.getInt(PREF_LAST_BOOK, -1) == bookIndex ? prefs.getInt(PREF_LAST_CHAPTER, -1) : -1;
	}

	private void loadBookNames() {
		namesTranslation = Translations.current(this);
		String[] books = bible.books;
		displayNames = new String[books.length];
		filterNames = new String[books.length];
		for (int i = 0; i < books.length; i++) {
			displayNames[i] = Translations.properBook(this, books[i]);
			// Match the English name too, so "rom" still finds the book for a reader on
			// the Spanish or Mandarin text — the same reasoning that has
			// VerseReferenceParser register every translation's names at once.
			filterNames[i] = fold(displayNames[i]) + "\n" + fold(Bible.getProperName(books[i]));
		}
	}

	/**
	 * Lowercase and accent-free, for comparing a typed filter against a book name.
	 * Character.toLowerCase has no locale, unlike String.toLowerCase() — that one folds
	 * "I" to a dotless "ı" under a Turkish locale and the two sides stop agreeing.
	 */
	private static String fold(String text) {
		String folded = VerseReferenceParser.foldAccents(text);
		StringBuilder out = new StringBuilder(folded.length());
		for (int i = 0; i < folded.length(); i++) {
			out.append(Character.toLowerCase(folded.charAt(i)));
		}
		return out.toString();
	}

	private void showBooks() {
		openBook = -1;
		backCallback.setEnabled(false);
		// setTitle rather than toolbar.setTitle: AppCompat pushes the Activity title (the
		// manifest label) onto the toolbar after onCreate, which would undo a toolbar-only
		// title set while restoring the last book read.
		setTitle(R.string.browse);
		filterField.setVisibility(View.VISIBLE);

		String query = fold(filterField.getText().toString().trim());
		List<BrowseBooksAdapter.Row> rows = new ArrayList<>();
		int matched = 0;

		for (int section = 0; section < 2; section++) {
			int from = section == 0 ? 0 : FIRST_NEW_TESTAMENT_BOOK;
			int to = section == 0 ? FIRST_NEW_TESTAMENT_BOOK : bible.books.length;
			List<BrowseBooksAdapter.Row> matches = new ArrayList<>();

			for (int i = from; i < to; i++) {
				if (!query.isEmpty() && !filterNames[i].contains(query)) continue;
				matches.add(BrowseBooksAdapter.Row.book(displayNames[i], i));
			}
			// A header with nothing under it would read as an empty testament.
			if (matches.isEmpty()) continue;
			rows.add(BrowseBooksAdapter.Row.header(getString(
					section == 0 ? R.string.old_testament : R.string.new_testament)));
			rows.addAll(matches);
			matched += matches.size();
		}

		booksAdapter.submit(rows);
		if (recyclerView.getAdapter() != booksAdapter) {
			recyclerView.setLayoutManager(new LinearLayoutManager(this));
			recyclerView.setAdapter(booksAdapter);
		}
		emptyIndicator.setVisibility(matched == 0 ? View.VISIBLE : View.GONE);
	}

	private void showChapters(int bookIndex) {
		openBook = bookIndex;
		backCallback.setEnabled(true);
		setTitle(displayNames[bookIndex]);
		filterField.setVisibility(View.GONE);
		hideKeyboard();
		emptyIndicator.setVisibility(View.GONE);

		Integer cached = chapterCounts.get(bookIndex);
		if (cached != null) {
			showChapterGrid(cached, lastReadChapter(bookIndex));
			return;
		}
		// The book list stays up while the file is read rather than flashing an empty grid.
		bookExecutor.execute(() -> {
			int count = bible.getBookLength(tools, this, bible.books[bookIndex]);
			runOnUiThread(() -> {
				if (isFinishing() || isDestroyed()) return;
				if (openBook != bookIndex) return; // moved on while the file was being read
				chapterCounts.put(bookIndex, count);
				showChapterGrid(count, lastReadChapter(bookIndex));
			});
		});
	}

	private void showChapterGrid(int chapterCount, int currentChapter) {
		chaptersAdapter.submit(chapterCount, currentChapter);
		if (recyclerView.getAdapter() != chaptersAdapter) {
			recyclerView.setLayoutManager(new GridLayoutManager(this, chapterGridColumns()));
			recyclerView.setAdapter(chaptersAdapter);
		}
		if (currentChapter > 0 && currentChapter <= chapterCount) {
			recyclerView.scrollToPosition(currentChapter - 1);
		}
	}

	/**
	 * Five columns on a phone held upright, more as the screen gets wider — a fixed five
	 * stretches each chapter into a letterbox in landscape or on a tablet.
	 */
	private int chapterGridColumns() {
		int widthDp = getResources().getConfiguration().screenWidthDp;
		return Math.max(CHAPTER_GRID_COLUMNS, widthDp / CHAPTER_TILE_MIN_DP);
	}

	@Override
	public void onBookClicked(int bookIndex) {
		showChapters(bookIndex);
	}

	@Override
	public void onChapterClicked(int chapter) {
		prefs().edit()
				.putInt(PREF_LAST_BOOK, openBook)
				.putInt(PREF_LAST_CHAPTER, chapter)
				.apply();

		Intent intent = new Intent(this, VerseLookUpActivity.class);
		// Verse 0 matches no verse, so the chapter opens from the top with nothing
		// singled out — browsing to a chapter is not the same as arriving at a verse.
		intent.putExtra("verse_ref", openBook + ":" + chapter + ":0");
		startActivity(intent);
	}

	@Override
	public boolean onSupportNavigateUp() {
		if (openBook >= 0) {
			showBooks();
			return true;
		}
		finish();
		return true;
	}

	private void hideKeyboard() {
		InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
		if (imm != null && filterField.getWindowToken() != null) {
			imm.hideSoftInputFromWindow(filterField.getWindowToken(), 0);
		}
		filterField.clearFocus();
	}
}
