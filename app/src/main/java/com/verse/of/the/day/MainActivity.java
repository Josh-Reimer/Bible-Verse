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

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationView;

import android.content.Intent;
import android.text.Spanned;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.AlignmentSpan;
import android.text.Layout;
import android.view.MenuItem;
import android.widget.TextView;
import android.content.Context;
import android.util.Log;

import java.lang.reflect.Field;
import java.util.Objects;
import java.util.Scanner;

import android.content.SharedPreferences;

import android.os.Bundle;
import android.view.GestureDetector;
import android.view.MotionEvent;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {

    public DrawerLayout drawerLayout;
    public ActionBarDrawerToggle actionBarDrawerToggle;
    Verse verse_displayed;

    MaterialToolbar toolbar; // declare only – DO NOT call findViewById here

    FloatingActionButton menu_fab;
    FloatingActionButton bookmark_fab;
    FloatingActionButton verselookup_fab;
    FloatingActionButton newverse_fab;
    FloatingActionButton share_button;
    private TextView verseview;
    private final Scanner mainScanner = new Scanner(System.in);
    private Context thisapp;
    private VerseOfTheDay vod;
    private final Tools tools = new Tools();
    private final Bible bible = new Bible();
    boolean fabs_visible;
    private bookmark_database db;
    boolean verse_displayed_is_bookmarked;
    private GestureDetector gestureDetector;
    private final RedLetter redLetter = new RedLetter();

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

    void hideFabs(){
        verselookup_fab.hide();
        newverse_fab.hide();
        bookmark_fab.hide();
        share_button.hide();
        fabs_visible = false;
    }

    void showFabs(){
        newverse_fab.show();
        verselookup_fab.show();
        bookmark_fab.show();
        share_button.show();
        fabs_visible = true;
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
            return insets;
        });


        ConstraintLayout mainLayoutView = findViewById(R.id.mainLayoutView);
        verseview = findViewById(R.id.verse);
        menu_fab = findViewById(R.id.menu_fab);
        bookmark_fab = findViewById(R.id.bookmark_fab);
        verselookup_fab = findViewById(R.id.verselookup);
        newverse_fab = findViewById(R.id.newverse);
        share_button = findViewById(R.id.verse_share);

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

        menu_fab.setOnClickListener(View -> {
            if (fabs_visible) {
                hideFabs();
            } else {

                if (verse_displayed_is_bookmarked) {
                    bookmark_fab.setImageResource(R.drawable.bookmark_solid_48);
                    bookmark_fab.show();
                } else {
                    bookmark_fab.setImageResource(R.drawable.bookmark_border_48);
                    bookmark_fab.show();
                }
                showFabs();
            }
        });

        newverse_fab.setOnClickListener(View -> {
            if (fabs_visible) {
                verse_displayed = vod.getRandomRef(bible, tools, thisapp);
                showVerse(verse_displayed);
                if (db.bookmark_dao().getBookmark(verse_displayed.reference).toString().equals("[]")){
                    verse_displayed_is_bookmarked = false;

                } else {
                    verse_displayed_is_bookmarked = true;

                }
                hideFabs();
            }
        });
        bookmark_fab.setOnClickListener(View -> {
            if (fabs_visible) {
                if (verse_displayed_is_bookmarked) {
                    bookmark_fab.setImageResource(R.drawable.bookmark_border_48);
                    //delete bookmark
                    db.bookmark_dao().deleteBookmark(verse_displayed.reference);
                    verse_displayed_is_bookmarked = false;
                } else {
                    bookmark_fab.setImageResource(R.drawable.bookmark_solid_48);
                    bookmark new_bookmark = new bookmark(verse_displayed.full_text,verse_displayed.reference,verse_displayed.proper_book,verse_displayed.scripture_text);
                    db.bookmark_dao().insertAll(new_bookmark);
                    verse_displayed_is_bookmarked = true;
                }
            }
        });
        verselookup_fab.setOnClickListener(View -> {
            if (fabs_visible) {
                hideFabs();
                goToVerseLookUpActivity(verse_displayed.reference);
            }
        });
        share_button.setOnClickListener(
                View -> {
                    shareVerse(verse_displayed);
                    hideFabs();
                }
        );
        mainLayoutView.setOnClickListener(View -> {
            hideFabs();
        });

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
        hideFabs();
        mainScanner.close();
    }

    @Override
    public void onPause() {
        super.onPause();
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

// also check if the bookmark displayed is still a bookmark; it could have been deleted
        verse_displayed_is_bookmarked = !db.bookmark_dao().getBookmark(verse_displayed.reference).toString().equals("[]");
        if(verse_displayed_is_bookmarked){
            bookmark_fab.setImageResource(R.drawable.bookmark_solid_48);
        } else {
            bookmark_fab.setImageResource(R.drawable.bookmark_border_48);
        }
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
}