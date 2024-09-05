package com.verse.of.the.day;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "bookmarks")
public class bookmark {
    @PrimaryKey(autoGenerate = true)
    public int id;
    @ColumnInfo(name = "bookmark")
    public String bookmark;

    public bookmark(String bookmark){
        this.bookmark = bookmark;
    }
}
