package com.verse.of.the.day;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.room.Room;

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
    TextView noBookmarksIndicator;
    List<bookmark> bookmarks_list;
    void deleteBookmark(int position){
        //delete from database
        bookmark_database db = Room.databaseBuilder(getApplicationContext(),
                bookmark_database.class,"bookmarks-database").allowMainThreadQueries().build();
        db.bookmark_dao().deleteBookmark(bookmarks_list.get(position).bible_reference);
        //remove from ui
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
        sharingIntent.putExtra(Intent.EXTRA_TEXT, bookmarks_list.get(position).full_text);
        startActivity(android.content.Intent.createChooser(sharingIntent, "Share via"));

        // hide share and delete buttons
        share_bookmark.setVisibility(View.GONE);
        delete_bookmark.setVisibility(View.GONE);
    }

    void hideFabs(){
        share_bookmark.hide();
        delete_bookmark.hide();
        hide_fabs.hide();
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
        setContentView(R.layout.activity_bookmarks);
        bookmark_database db = Room.databaseBuilder(getApplicationContext(),
                bookmark_database.class,"bookmarks-database").allowMainThreadQueries().build();

        bookmark_activity_main_layout = findViewById(R.id.mainBookmarkLayout);
        bookmark_recyclerview = findViewById(R.id.bookmark_recyclerview);
        share_bookmark = findViewById(R.id.bookmark_share);
        delete_bookmark = findViewById(R.id.bookmark_delete);
        hide_fabs = findViewById(R.id.hidefabs);
        noBookmarksIndicator = findViewById(R.id.isbookmarksempty);

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

        Bookmark_recyclerview_adapter adapter = new Bookmark_recyclerview_adapter(data,listener);
        bookmarks_list = db.bookmark_dao().getAllBookmarks();

        if (bookmarks_list.isEmpty()){
            noBookmarksIndicator.setVisibility(View.VISIBLE);
        }

        for(bookmark list:bookmarks_list){
            data.add(new Bookmark_recyclerview_model(list.scripture_text,list.book,list.bible_reference));
        }

        bookmark_recyclerview.setAdapter(adapter);
        bookmark_recyclerview.setLayoutManager(new LinearLayoutManager(this,RecyclerView.VERTICAL,false));
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
    }
}