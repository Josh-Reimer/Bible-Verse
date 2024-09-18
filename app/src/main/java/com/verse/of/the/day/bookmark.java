package com.verse.of.the.day;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "bookmarks",
        indices = {@Index(value = {"bible_reference"}, unique = true)})
public class bookmark {
    @PrimaryKey(autoGenerate = true)
    public int item_id;

    @ColumnInfo
    public String bible_reference;

    @ColumnInfo(name = "bookmark")
    public String bookmark;

    public bookmark(String bookmark){
        this.bookmark = bookmark;
    }
}
