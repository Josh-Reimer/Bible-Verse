package com.verse.of.the.day;

import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.room.Room;

import org.w3c.dom.Text;

import java.util.List;


public class bookmarks_activity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bookmarks);
        bookmark_database db = Room.databaseBuilder(getApplicationContext(),
                bookmark_database.class,"bookmarks-database").allowMainThreadQueries().build();

        TextView bookmarkview = findViewById(R.id.bookmarks_view);

        StringBuilder textview_string = new StringBuilder();
        List<bookmark> bookmarks_list = db.bookmark_dao().getAllBookmarks();
        for(bookmark list:bookmarks_list){
            textview_string.append(list.bookmark);
            textview_string.append("\n");
        }
        bookmarkview.setText(textview_string);
    }

}