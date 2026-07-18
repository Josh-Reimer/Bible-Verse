package com.verse.of.the.day;

import androidx.constraintlayout.widget.ConstraintLayout;

import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.customview.widget.ViewDragHelper;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.room.Room;
import androidx.appcompat.widget.SearchView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.text.Spanned;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.AlignmentSpan;
import android.text.Layout;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;
import android.content.Context;
import android.util.Log;

import java.lang.reflect.Field;
import java.util.Objects;
import java.util.Scanner;

import android.content.SharedPreferences;

import android.os.Bundle;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.Menu;
import android.view.MenuInflater;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {

    public DrawerLayout drawerLayout;
    public ActionBarDrawerToggle actionBarDrawerToggle;
    Verse verse_displayed;

    MaterialToolbar toolbar; // declare only – DO NOT call findViewById here

    private FloatingActionButton menuFab, bookmarkFab, verseLookupFab, newVerseFab, shareFab;
    private boolean fabsExpanded = false;
    private TextView verseview;
    private final Scanner mainScanner = new Scanner(System.in);
    private Context thisapp;
    private VerseOfTheDay vod;
    private final Tools tools = new Tools();
    private final Bible bible = new Bible();
    private bookmark_database db;
    boolean verse_displayed_is_bookmarked;
    private GestureDetector gestureDetector;
    private MenuItem searchMenuItem;
    // The options menu is rebuilt when the results sheet is dismissed, which drops the
    // expanded SearchView without firing onMenuItemActionCollapse; these fields let
    // onCreateOptionsMenu restore the search UI so it only closes on explicit collapse.
    private boolean searchUiActive = false;
    private String searchQueryText = "";
    private boolean refocusSearch = false;
    private final RedLetter redLetter = new RedLetter();
    // Search runs off the UI thread (androidbible-style); one app-wide worker is enough.
    private static final java.util.concurrent.ExecutorService searchExecutor = java.util.concurrent.Executors.newSingleThreadExecutor();

    void applyTheme(SharedPreferences sp) {
        String mode = sp.getString("theme_mode", "system");
        switch (mode) {
            case "dark":
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            case "light":
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            default:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
        }
    }

    void showVerse(Verse v) {
        SharedPreferences sp = getSharedPreferences("settings", MODE_PRIVATE);
        boolean showTranslationInfo = sp.getBoolean("show_translation_info", false);
        String translation = sp.getString("translation", "kjv").toUpperCase();
        Spanned spanned = redLetter.getSpanned(thisapp, v.reference);

        if (spanned != null) {
            verseview.setText(v.proper_book + "\n" + v.chapter + ":" + v.verse + ": ");
            verseview.append(spanned);
        } else {
            verseview.setText(v.full_text);
        }

        if (showTranslationInfo) {
            SpannableStringBuilder builder = new SpannableStringBuilder(verseview.getText());
            builder.append("\n\n");
            int translationStart = builder.length();
            builder.append(translation);
            int translationEnd = builder.length();

            builder.setSpan(new ForegroundColorSpan(0xFF808080), translationStart, translationEnd, 0);
            builder.setSpan(new RelativeSizeSpan(0.7f), translationStart, translationEnd, 0);
            builder.setSpan(new AlignmentSpan.Standard(Layout.Alignment.ALIGN_OPPOSITE), translationStart, translationEnd, 0);

            verseview.setText(builder);
        }
    }


    void shareVerse(Verse verse){
        Intent sharingIntent = new Intent(Intent.ACTION_SEND);
        sharingIntent.setType("text/plain");
        sharingIntent.putExtra(Intent.EXTRA_TEXT, verse.full_text);
        startActivity(android.content.Intent.createChooser(sharingIntent, "Share via"));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        SharedPreferences shared_preferences = getSharedPreferences("settings", MODE_PRIVATE);
        applyTheme(shared_preferences);

        db = Room.databaseBuilder(getApplicationContext(),
                bookmark_database.class, "bookmarks-database").allowMainThreadQueries().build();
        setContentView(R.layout.activity_main);
        // ----- MATERIAL TOOLBAR SETUP -----
        toolbar = findViewById(R.id.main_toolbar);
        setSupportActionBar(toolbar);


        MaterialToolbar toolbar = findViewById(R.id.main_toolbar);

        ViewCompat.setOnApplyWindowInsetsListener(toolbar, (v, insets) -> {
            int topInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            v.setPadding(0, topInset, 0, 0);
            // Fix the height to inset + actionBarSize so the content strip exactly fits
            // the nav/menu buttons (they measure to actionBarSize); with wrap_content the
            // toolbar under-measures and the icons overflow, sitting below the title's
            // centerline.
            android.util.TypedValue tv = new android.util.TypedValue();
            getTheme().resolveAttribute(androidx.appcompat.R.attr.actionBarSize, tv, true);
            int actionBarSize = android.util.TypedValue.complexToDimensionPixelSize(
                    tv.data, getResources().getDisplayMetrics());
            android.view.ViewGroup.LayoutParams lp = v.getLayoutParams();
            lp.height = topInset + actionBarSize;
            v.setLayoutParams(lp);
            return insets;
        });


        ConstraintLayout mainLayoutView = findViewById(R.id.mainLayoutView);
        verseview = findViewById(R.id.verse);

        mainLayoutView.setOnClickListener(v -> {
            if (fabsExpanded) toggleFabs();
        });

        menuFab = findViewById(R.id.menu_fab);
        bookmarkFab = findViewById(R.id.bookmark_fab);
        verseLookupFab = findViewById(R.id.verselookup);
        newVerseFab = findViewById(R.id.newverse);
        shareFab = findViewById(R.id.share_fab);

        thisapp = getApplicationContext();
        vod = new VerseOfTheDay(mainScanner, thisapp);

            if(savedInstanceState == null) {
                verse_displayed = vod.getRandomRef(bible, tools, thisapp);  // generate new verse if the savedInstanceState is null (when the app cold starts)
            } else {
                verse_displayed = new Verse(
                        thisapp,
                        Objects.requireNonNull(savedInstanceState.getString("verse_ref"))
                );
                // retrieve verse displayed from before the app paused
            }
            showVerse(verse_displayed);
            verse_displayed_is_bookmarked = !db.bookmark_dao().getBookmark(verse_displayed.reference).toString().equals("[]");

        setupFabs();


        setNavigationViewListener();

        drawerLayout = findViewById(R.id.my_drawer_layout);
        actionBarDrawerToggle = new ActionBarDrawerToggle(this, drawerLayout, R.string.nav_open, R.string.nav_close);

        NavigationView navigationView = findViewById(R.id.nv);
        drawerLayout.addDrawerListener(actionBarDrawerToggle);
        actionBarDrawerToggle.syncState();
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);

        // Set up the gesture detector to detect swipes
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            private static final int SWIPE_THRESHOLD_VELOCITY = 100; // Velocity threshold
            private static final int SWIPE_THRESHOLD_DISTANCE = 100; // Distance threshold

            @Override
          public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                // Detect left-to-right swipe (open drawer)
                if (e1.getX() < e2.getX() && Math.abs(velocityX) > SWIPE_THRESHOLD_VELOCITY &&
                        Math.abs(e1.getX() - e2.getX()) > SWIPE_THRESHOLD_DISTANCE) {
                    // Open the drawer if swipe is detected
                    drawerLayout.openDrawer(GravityCompat.START);
                    return true;
                }
                return super.onFling(e1, e2, velocityX, velocityY);
           }
        });

        // Set up the content view's touch listener to detect swipes

        mainLayoutView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return gestureDetector.onTouchEvent(event);
            }
        });


    }        //end of oncreate method

    @Override
    public void onDestroy() {
        super.onDestroy();
        mainScanner.close();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (fabsExpanded) {
            fabsExpanded = false;
            newVerseFab.setVisibility(View.GONE);
            verseLookupFab.setVisibility(View.GONE);
            bookmarkFab.setVisibility(View.GONE);
            shareFab.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState){
        Log.i("verse","onSavedInstanceState fired");
        outState.putString("verse_ref",verse_displayed.reference);

        super.onSaveInstanceState(outState);
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.i("verse-main", "onResume method was called!");

        SharedPreferences shared_preferences = getSharedPreferences("settings", MODE_PRIVATE);
        applyTheme(shared_preferences);
        verse_displayed = new Verse(thisapp, verse_displayed.reference);
        showVerse(verse_displayed);

        verse_displayed_is_bookmarked = !db.bookmark_dao().getBookmark(verse_displayed.reference).toString().equals("[]");
        updateBookmarkIcon();
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_activity_menu, menu);
        searchMenuItem = menu.findItem(R.id.action_search);
        SearchView searchView = (SearchView) searchMenuItem.getActionView();
        searchView.setQueryHint("Search verses...");
        searchMenuItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem item) {
                searchUiActive = true;
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem item) {
                searchUiActive = false;
                return true;
            }
        });
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                // Saved at submit time: the menu rebuild after a sheet dismissal clears
                // the old SearchView's text, so a change listener can't be trusted here.
                searchQueryText = query;
                performSearch(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                return false;
            }
        });
        if (searchUiActive) {
            searchMenuItem.expandActionView();
            searchView.setQuery(searchQueryText, false);
            if (refocusSearch) {
                refocusSearch = false; // expandActionView already focused the query field
            } else {
                searchView.clearFocus();
            }
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (actionBarDrawerToggle.onOptionsItemSelected(item)) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }


    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.settings) {
            goToSettings();
        } else if (itemId == R.id.bookmarks) {
            Intent i = new Intent(this, bookmarks_activity.class);
            startActivity(i);
        }
        return true;
    }

    private void setNavigationViewListener() {
        NavigationView nv = findViewById(R.id.nv);
        nv.setNavigationItemSelectedListener(this);
    }

    void setupFabs() {
        menuFab.setOnClickListener(v -> toggleFabs());
        newVerseFab.setOnClickListener(v -> onNewVerse());
        verseLookupFab.setOnClickListener(v -> onLookUp());
        bookmarkFab.setOnClickListener(v -> onToggleBookmark());
        shareFab.setOnClickListener(v -> onShare());
        updateBookmarkIcon();
    }

    private void toggleFabs() {
        fabsExpanded = !fabsExpanded;
        if (fabsExpanded) {
            showFabsWithAnimation();
        } else {
            hideFabsWithAnimation();
        }
    }

    private void showFabsWithAnimation() {
        FloatingActionButton[] fabs = {shareFab, newVerseFab, verseLookupFab, bookmarkFab};
        long startDelay = 0;
        long delayBetween = 60;
        long duration = 250;

        for (FloatingActionButton fab : fabs) {
            fab.setVisibility(View.VISIBLE);
            fab.setAlpha(0f);
            fab.setScaleX(0f);
            fab.setScaleY(0f);

            AnimatorSet animatorSet = new AnimatorSet();
            animatorSet.playTogether(
                    ObjectAnimator.ofFloat(fab, "alpha", 0f, 1f),
                    ObjectAnimator.ofFloat(fab, "scaleX", 0f, 1f),
                    ObjectAnimator.ofFloat(fab, "scaleY", 0f, 1f)
            );
            animatorSet.setDuration(duration);
            animatorSet.setStartDelay(startDelay);
            animatorSet.start();

            startDelay += delayBetween;
        }
    }

    private void hideFabsWithAnimation() {
        FloatingActionButton[] fabs = {bookmarkFab, verseLookupFab, newVerseFab, shareFab};
        long startDelay = 0;
        long delayBetween = 45;
        long duration = 200;

        for (FloatingActionButton fab : fabs) {
            AnimatorSet animatorSet = new AnimatorSet();
            animatorSet.playTogether(
                    ObjectAnimator.ofFloat(fab, "alpha", 1f, 0f),
                    ObjectAnimator.ofFloat(fab, "scaleX", 1f, 0f),
                    ObjectAnimator.ofFloat(fab, "scaleY", 1f, 0f)
            );
            animatorSet.setDuration(duration);
            animatorSet.setStartDelay(startDelay);
            animatorSet.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(android.animation.Animator animation) {
                    fab.setVisibility(View.GONE);
                }
            });
            animatorSet.start();

            startDelay += delayBetween;
        }
    }

    private void onNewVerse() {
        verse_displayed = vod.getRandomRef(bible, tools, thisapp);
        showVerse(verse_displayed);
        verse_displayed_is_bookmarked = !db.bookmark_dao().getBookmark(verse_displayed.reference).toString().equals("[]");
        updateBookmarkIcon();
        if (fabsExpanded) toggleFabs();
    }

    private void onLookUp() {
        goToVerseLookUpActivity(verse_displayed.reference);
        if (fabsExpanded) toggleFabs();
    }

    private void onToggleBookmark() {
        if (verse_displayed_is_bookmarked) {
            db.bookmark_dao().deleteBookmark(verse_displayed.reference);
            verse_displayed_is_bookmarked = false;
        } else {
            bookmark new_bookmark = new bookmark(verse_displayed.full_text, verse_displayed.reference, verse_displayed.proper_book, verse_displayed.scripture_text);
            db.bookmark_dao().insertAll(new_bookmark);
            verse_displayed_is_bookmarked = true;
        }
        updateBookmarkIcon();
    }

    private void onShare() {
        shareVerse(verse_displayed);
        if (fabsExpanded) toggleFabs();
    }

    void updateBookmarkIcon() {
        if (verse_displayed_is_bookmarked) {
            bookmarkFab.setImageResource(R.drawable.bookmark_solid_48);
        } else {
            bookmarkFab.setImageResource(R.drawable.bookmark_border_48);
        }
    }

    void goToSettings() {
        Intent i = new Intent(this, SettingsActivity.class);
        startActivity(i);

    }

    void goToVerseLookUpActivity(String verse) {
        Intent intent = new Intent(this, VerseLookUpActivity.class);
        intent.putExtra("verse_ref", verse);
        startActivity(intent);
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        // Let the gesture detector handle touch events
        return gestureDetector.onTouchEvent(event) || super.onTouchEvent(event);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        // Tapping a non-focusable view never steals the SearchView's focus, so an
        // expanded search bar would stay open; collapse it on outside taps.
        if (ev.getAction() == MotionEvent.ACTION_DOWN && searchMenuItem != null && searchMenuItem.isActionViewExpanded()) {
            View searchView = searchMenuItem.getActionView();
            if (searchView != null && isOutsideView(searchView, ev.getRawX(), ev.getRawY())) {
                searchMenuItem.collapseActionView();
            }
        }
        return super.dispatchTouchEvent(ev);
    }

    private static boolean isOutsideView(View v, float rawX, float rawY) {
        int[] location = new int[2];
        v.getLocationOnScreen(location);
        return rawX < location[0] || rawX > location[0] + v.getWidth()
                || rawY < location[1] || rawY > location[1] + v.getHeight();
    }

    private void performSearch(String query) {
        if (query.trim().isEmpty()) {
            Toast.makeText(this, "Enter a search term", Toast.LENGTH_SHORT).show();
            return;
        }

        searchExecutor.execute(() -> {
            SearchEngineQuery searchQuery = new SearchEngineQuery(query);
            List<String> verseRefs = SearchEngine.searchByGrep(thisapp, searchQuery);
            List<SearchResult> results = new ArrayList<>();
            String lowerQuery = query.toLowerCase().trim();
            List<QueryTokenizer.Token> queryTokens = QueryTokenizer.tokenize(query);

            for (String verseRef : verseRefs) {
                String[] parts = verseRef.split(":");
                int bookIndex = Integer.parseInt(parts[0]);
                String displayRef = Bible.getProperName(bible.books[bookIndex]) + " " + parts[1] + ":" + parts[2];
                SearchResult result = new SearchResult(displayRef, verseRef, SearchEngine.getVerseText(thisapp, verseRef), query);
                result.relevanceScore = SearchEngine.relevanceScore(result.text, lowerQuery, queryTokens);
                results.add(result);
            }
            // Stable sort: equal scores keep canonical Bible order.
            results.sort((a, b) -> Integer.compare(a.relevanceScore, b.relevanceScore));

            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (results.isEmpty()) {
                    Toast.makeText(this, "No verses found matching \"" + query + "\"", Toast.LENGTH_SHORT).show();
                } else {
                    showSearchResultsBottomSheet(results);
                }
                saveSearchHistory(query);
            });
        });
    }

    private void saveSearchHistory(String query) {
        SharedPreferences sp = getSharedPreferences("settings", MODE_PRIVATE);
        String historyJson = sp.getString("search_history", "[]");

        try {
            org.json.JSONArray history = new org.json.JSONArray(historyJson);

            for (int i = 0; i < history.length(); i++) {
                if (history.getString(i).equals(query)) {
                    history.remove(i);
                    break;
                }
            }

            org.json.JSONArray newHistory = new org.json.JSONArray();
            newHistory.put(query);
            for (int i = 0; i < Math.min(19, history.length()); i++) {
                newHistory.put(history.getString(i));
            }

            sp.edit().putString("search_history", newHistory.toString()).apply();
        } catch (org.json.JSONException e) {
            e.printStackTrace();
        }
    }

    private void showSearchResultsBottomSheet(List<SearchResult> results) {
        // Searching again while a sheet is open must replace it, not stack a second one.
        androidx.fragment.app.Fragment existing = getSupportFragmentManager().findFragmentByTag("search_results");
        if (existing instanceof SearchResultsBottomSheet) {
            ((SearchResultsBottomSheet) existing).dismiss();
        }
        SearchResultsBottomSheet bottomSheet = SearchResultsBottomSheet.newInstance(results, result -> {
            goToVerseLookUpActivity(result.verseReference);
        }, searchResultBookmarkListener, this::onSearchSheetOutsideTap, this::collapseSearchBar);
        bottomSheet.show(getSupportFragmentManager(), "search_results");
    }

    private final SearchResultsAdapter.BookmarkListener searchResultBookmarkListener = new SearchResultsAdapter.BookmarkListener() {
        @Override
        public boolean isBookmarked(SearchResult result) {
            return !db.bookmark_dao().getBookmark(result.verseReference).toString().equals("[]");
        }

        @Override
        public boolean toggleBookmark(SearchResult result) {
            boolean nowBookmarked;
            if (isBookmarked(result)) {
                db.bookmark_dao().deleteBookmark(result.verseReference);
                nowBookmarked = false;
            } else {
                Verse verse = new Verse(thisapp, result.verseReference);
                bookmark new_bookmark = new bookmark(verse.full_text, verse.reference, verse.proper_book, verse.scripture_text);
                db.bookmark_dao().insertAll(new_bookmark);
                nowBookmarked = true;
            }
            // Keep the main-screen bookmark FAB in sync when the search result
            // is the verse currently displayed.
            if (verse_displayed != null && verse_displayed.reference.equals(result.verseReference)) {
                verse_displayed_is_bookmarked = nowBookmarked;
                updateBookmarkIcon();
            }
            return nowBookmarked;
        }
    };

    private void collapseSearchBar() {
        if (searchMenuItem != null && searchMenuItem.isActionViewExpanded()) {
            searchMenuItem.collapseActionView();
        }
    }

    private void onSearchSheetOutsideTap(float rawX, float rawY) {
        if (searchMenuItem == null || !searchMenuItem.isActionViewExpanded()) {
            return;
        }
        View searchView = searchMenuItem.getActionView();
        if (searchView != null && !isOutsideView(searchView, rawX, rawY)) {
            // Tapping the search box only dismisses the sheet; keep the search UI
            // open and give the query field focus back after the menu rebuild.
            refocusSearch = true;
        } else {
            searchMenuItem.collapseActionView();
        }
    }
}