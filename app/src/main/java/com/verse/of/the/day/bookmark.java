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

    @ColumnInfo
    public String book;

    @ColumnInfo
    public String scripture_text;

    @ColumnInfo(name = "full_text")
    public String full_text;

    public bookmark(String full_text, String bible_reference, String book, String scripture_text){
        this.full_text = full_text;
        this.bible_reference = bible_reference;
        this.book = book;
        this.scripture_text = scripture_text;
    }
}
