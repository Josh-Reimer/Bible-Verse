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
    private TextView verseview;
    private final Scanner mainScanner = new Scanner(System.in);
    private Context thisapp;
    private VerseOfTheDay vod;
    private final Tools tools = new Tools();
    private final Bible bible = new Bible();
    boolean fabs_visible;
    boolean verse_displayed_is_bookmarked;
    private GestureDetector gestureDetector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        SharedPreferences shared_preferences = getSharedPreferences("settings", MODE_PRIVATE);
        boolean theme = shared_preferences.getBoolean("theme", false);
        //true is dark theme on
        //false is light theme on
        //second parameter is the default value if there is no theme value in shaded preferences

        if (theme) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        }

        bookmark_database db = Room.databaseBuilder(getApplicationContext(),
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


        //drawerLayout.closeDrawers();
        ConstraintLayout mainLayoutView = findViewById(R.id.mainLayoutView);
        verseview = findViewById(R.id.verse);
        menu_fab = findViewById(R.id.menu_fab);
        bookmark_fab = findViewById(R.id.bookmark_fab);
        verselookup_fab = findViewById(R.id.verselookup);
        newverse_fab = findViewById(R.id.newverse);

        verselookup_fab.setVisibility(View.GONE);
        newverse_fab.setVisibility(View.GONE);
        bookmark_fab.setVisibility(View.GONE);

        fabs_visible = false;

        thisapp = getApplicationContext();
        vod = new VerseOfTheDay(mainScanner, thisapp);
        //String verseofday = vod.getVerseFromFile(mainScanner,thisapp,tools);
        //String verseofday = vod.getRandomVerse(mainScanner,tools,thisapp,bible);
        verse_displayed = vod.getRandomRef(bible, tools, thisapp);
        verseview.setText(verse_displayed.full_text);


        verse_displayed_is_bookmarked = !db.bookmark_dao().getBookmark(verse_displayed.reference).toString().equals("[]");


        menu_fab.setOnClickListener(View -> {
            if (fabs_visible) {
                verselookup_fab.setVisibility(android.view.View.GONE);
                newverse_fab.setVisibility(android.view.View.GONE);
                bookmark_fab.setVisibility(android.view.View.GONE);
                fabs_visible = false;

            } else {

                if (verse_displayed_is_bookmarked) {
                    //verse_displayed_is_bookmarked = false;
                    bookmark_fab.setImageResource(R.drawable.bookmark_solid_48);
                    bookmark_fab.show();
                } else {
                    //verse_displayed_is_bookmarked = true;
                    bookmark_fab.setImageResource(R.drawable.bookmark_border_48);
                    bookmark_fab.show();
                }
                newverse_fab.show();
                verselookup_fab.show();

                fabs_visible = true;
            }
        });

        newverse_fab.setOnClickListener(View -> {
            if (fabs_visible) {
                verse_displayed = vod.getRandomRef(bible, tools, thisapp);
                verseview.setText(verse_displayed.full_text);
                if (db.bookmark_dao().getBookmark(verse_displayed.reference).toString().equals("[]")){
                    verse_displayed_is_bookmarked = false;

                } else {
                    verse_displayed_is_bookmarked = true;

                }
                verselookup_fab.setVisibility(android.view.View.GONE);
                newverse_fab.setVisibility(android.view.View.GONE);
                bookmark_fab.setVisibility(android.view.View.GONE);
                fabs_visible = false;
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
                goToVerseLookUpActivity(verse_displayed.reference);
                verselookup_fab.setVisibility(android.view.View.GONE);
                newverse_fab.setVisibility(android.view.View.GONE);
                bookmark_fab.setVisibility(android.view.View.GONE);
                fabs_visible = false;
            }
        });

        mainLayoutView.setOnClickListener(View -> {
            verselookup_fab.setVisibility(android.view.View.GONE);
            newverse_fab.setVisibility(android.view.View.GONE);
            bookmark_fab.setVisibility(android.view.View.GONE);
            fabs_visible = false;
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
        mainScanner.close();
        Log.i("verse-main", "onDestroy method was called!");
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.i("verse-main", "onPause method was called!");
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.i("verse-main", "onResume method was called!");
        SharedPreferences shared_preferences = getSharedPreferences("settings", MODE_PRIVATE);
        boolean theme = shared_preferences.getBoolean("theme", false);
        //true is dark theme on
        //false is light theme on
        //second parameter is the defualt value if there is no theme value in shaded preferences

        if (theme) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
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