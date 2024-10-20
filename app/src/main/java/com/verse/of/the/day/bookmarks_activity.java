package com.verse.of.the.day;

import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.room.Room;

import org.w3c.dom.Text;

import java.util.ArrayList;
import java.util.List;


public class bookmarks_activity extends AppCompatActivity {

    RecyclerView bookmark_recyclerview;
    ArrayList<Bookmark_recyclerview_model> data = new ArrayList<>();
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bookmarks);
        bookmark_database db = Room.databaseBuilder(getApplicationContext(),
                bookmark_database.class,"bookmarks-database").allowMainThreadQueries().build();

        bookmark_recyclerview = findViewById(R.id.bookmark_recyclerview);
        Bookmark_recyclerview_adapter adapter = new Bookmark_recyclerview_adapter(data);
        List<bookmark> bookmarks_list = db.bookmark_dao().getAllBookmarks();


        for(bookmark list:bookmarks_list){
            data.add(new Bookmark_recyclerview_model(list.bookmark));
        }
        bookmark_recyclerview.setAdapter(adapter);
        bookmark_recyclerview.setLayoutManager(new LinearLayoutManager(this,RecyclerView.VERTICAL,false));

    }
}