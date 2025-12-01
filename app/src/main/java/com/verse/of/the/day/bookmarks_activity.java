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

import org.w3c.dom.Text;

import java.util.ArrayList;
import java.util.List;


public class bookmarks_activity extends AppCompatActivity {

    ConstraintLayout bookmark_activity_main_layout;
    RecyclerView bookmark_recyclerview;
    ArrayList<Bookmark_recyclerview_model> data = new ArrayList<>();
    FloatingActionButton share_bookmark;
    FloatingActionButton delete_bookmark;
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

        share_bookmark.setVisibility(View.GONE);
        delete_bookmark.setVisibility(View.GONE);

        bookmark_recyclerview.setOnClickListener(View -> {
            share_bookmark.setVisibility(android.view.View.GONE);
            delete_bookmark.setVisibility(android.view.View.GONE);
        });

/*
when a bookmark in the bookmark page is short tapped, open that verse in the verselookup page. If it is long tapped, open a menu to offer to delete or share
 */

        Bookmark_recyclerview_adapter.onBookmarkLongClickListener listener = new Bookmark_recyclerview_adapter.onBookmarkLongClickListener(){
            public void onBookmarkLongClicked(int position, String str) {
                share_bookmark.setVisibility(View.VISIBLE);
                delete_bookmark.setVisibility(View.VISIBLE);
                Toast toast=Toast.makeText(getApplicationContext(),"showing fabs",Toast.LENGTH_LONG);
                //toast.show();
            }
        };

        Bookmark_recyclerview_adapter adapter = new Bookmark_recyclerview_adapter(data,listener);
        List<bookmark> bookmarks_list = db.bookmark_dao().getAllBookmarks();

        for(bookmark list:bookmarks_list){
            data.add(new Bookmark_recyclerview_model(list.scripture_text,list.book,list.bible_reference));
        }

        bookmark_recyclerview.setAdapter(adapter);
        bookmark_recyclerview.setLayoutManager(new LinearLayoutManager(this,RecyclerView.VERTICAL,false));


    }
}