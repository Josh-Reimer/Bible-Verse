package com.verse.of.the.day;

import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.room.Room;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import android.content.Intent;
import java.util.ArrayList;
import java.util.List;


public class bookmarks_activity extends AppCompatActivity {

    ConstraintLayout bookmark_activity_main_layout;
    RecyclerView bookmark_recyclerview;
    ArrayList<Bookmark_recyclerview_model> data = new ArrayList<>();
    FloatingActionButton share_bookmark;
    FloatingActionButton delete_bookmark;
    FloatingActionButton hide_fabs;
    int bookmarkPosition;
    Bookmark_recyclerview_adapter adapter;
    bookmark_database db;
    TextView noBookmarksIndicator;
    List<bookmark> bookmarks_list;
    private final java.util.concurrent.ExecutorService rowExecutor =
            java.util.concurrent.Executors.newSingleThreadExecutor();

    // Reads the current translation's text for each bookmark off the main thread,
    // then swaps the finished rows in on the UI thread.
    private void loadBookmarkRows(){
        List<bookmark> snapshot = bookmarks_list;
        rowExecutor.execute(() -> {
            RedLetter redLetter = new RedLetter();
            ArrayList<Bookmark_recyclerview_model> rows = new ArrayList<>();
            for(bookmark list:snapshot){
                CharSequence text = redLetter.getSpanned(this, list.bible_reference);
                if (text == null) text = new Verse(this, list.bible_reference).scripture_text;
                // The stored `book` is the name as it read when the bookmark was saved.
                // Resolve it from the reference instead, so the row follows the current
                // translation's language the same way its verse text already does.
                int bookIndex = Integer.parseInt(list.bible_reference.split(":")[0]);
                String book = Translations.properBook(this, new Bible().books[bookIndex]);
                rows.add(new Bookmark_recyclerview_model(text,book,list.bible_reference));
            }
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                data.clear();
                data.addAll(rows);
                adapter.notifyDataSetChanged();
            });
        });
    }
    void deleteBookmark(int position){
        db.bookmark_dao().deleteBookmark(bookmarks_list.get(position).bible_reference);
        //remove from ui
        adapter.clearSelection(); // clear before removal so the stale position is not re-bound
        bookmarks_list.remove(position); // keep in sync with data so later positions still line up
        data.remove(position);
        assert bookmark_recyclerview.getAdapter() != null;
        bookmark_recyclerview.getAdapter().notifyItemRemoved(position);

        if(data.isEmpty()){
            noBookmarksIndicator.setVisibility(View.VISIBLE);
            //as the user deletes bookmarks, check to see if there are any left and show the no bookmarks text if there are none
        }
        // hide share and delete buttons
        share_bookmark.setVisibility(View.GONE);
        delete_bookmark.setVisibility(View.GONE);
    }
    void shareBookmark(int position){
        Intent sharingIntent = new Intent(Intent.ACTION_SEND);
        sharingIntent.setType("text/plain");
        // Share the current translation's text so it matches what the row displays.
        sharingIntent.putExtra(Intent.EXTRA_TEXT, new Verse(this, bookmarks_list.get(position).bible_reference).full_text);
        startActivity(android.content.Intent.createChooser(sharingIntent, getString(R.string.share_via)));
    }

    void hideFabs(){
        share_bookmark.hide();
        delete_bookmark.hide();
        hide_fabs.hide();
        if (adapter != null) adapter.clearSelection();
    }
    // using the .hide() and .show() functions instead of View.visible and View.Gone makes those fabs animate up and down nicely  (yes the simpler functions are better :)
    void showFabs(){
        share_bookmark.show();
        delete_bookmark.show();
        hide_fabs.show();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_bookmarks);

        MaterialToolbar toolbar = findViewById(R.id.bookmarks_toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        ViewCompat.setOnApplyWindowInsetsListener(toolbar, (v, insets) -> {
            int topInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            v.setPadding(0, topInset, 0, 0);
            return insets;
        });
        db = Room.databaseBuilder(getApplicationContext(),
                bookmark_database.class,"bookmarks-database").allowMainThreadQueries().build();

        bookmark_activity_main_layout = findViewById(R.id.mainBookmarkLayout);
        bookmark_recyclerview = findViewById(R.id.bookmark_recyclerview);
        share_bookmark = findViewById(R.id.bookmark_share);
        delete_bookmark = findViewById(R.id.bookmark_delete);
        hide_fabs = findViewById(R.id.hidefabs);
        noBookmarksIndicator = findViewById(R.id.isbookmarksempty);

        ViewCompat.setOnApplyWindowInsetsListener(bookmark_recyclerview, (v, insets) -> {
            int bottomInset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), bottomInset);
            return insets;
        });

        hideFabs();


/*
when a bookmark in the bookmark page is short tapped, open that verse in the verselookup page. If it is long tapped, open a menu to offer to delete or share
 */


        Bookmark_recyclerview_adapter.onBookmarkLongClickListener listener = new Bookmark_recyclerview_adapter.onBookmarkLongClickListener(){
            public void onBookmarkLongClicked(int position, String str) {
                showFabs();
                bookmarkPosition = position;
            }
        };

        adapter = new Bookmark_recyclerview_adapter(data,listener);
        bookmarks_list = db.bookmark_dao().getAllBookmarks();

        if (bookmarks_list.isEmpty()){
            noBookmarksIndicator.setVisibility(View.VISIBLE);
        }

        bookmark_recyclerview.setAdapter(adapter);
        bookmark_recyclerview.setLayoutManager(new LinearLayoutManager(this,RecyclerView.VERTICAL,false));

        // Build rows off the main thread: each row is derived from the current
        // translation (red-letter markup only exists per current translation, so
        // mixing it with the DB-stored text would show two translations in one
        // list after a switch in Settings). The fallback re-reads the whole book
        // .txt file per verse, so doing this synchronously here would jank/ANR
        // onCreate for users with many bookmarks. Html.fromHtml is view-free, so
        // it is safe to run here and keeps it out of onBindViewHolder.
        loadBookmarkRows();
        delete_bookmark.setOnClickListener(View ->{
            deleteBookmark(bookmarkPosition);
            hideFabs();
        });

        share_bookmark.setOnClickListener(
                View -> {
                    shareBookmark(bookmarkPosition);
                    hideFabs();
                }
        );

        hide_fabs.setOnClickListener(View -> {
            hideFabs();
        });

        // tapping anywhere that is not a bookmark dismisses the fab ui
        bookmark_activity_main_layout.setOnClickListener(View -> hideFabs());
        bookmark_recyclerview.addOnItemTouchListener(new RecyclerView.SimpleOnItemTouchListener() {
            @Override
            public boolean onInterceptTouchEvent(RecyclerView rv, MotionEvent e) {
                if (e.getActionMasked() == MotionEvent.ACTION_DOWN
                        && rv.findChildViewUnder(e.getX(), e.getY()) == null) {
                    hideFabs();
                }
                return false;
            }
        });
    }

    @Override
    protected void onDestroy() {
        rowExecutor.shutdownNow();
        super.onDestroy();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}