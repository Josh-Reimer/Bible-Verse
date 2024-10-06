package com.verse.of.the.day;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface bookmark_dao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertAll(bookmark bookmark);

    @Query("SELECT * FROM bookmarks")
    List<bookmark> getAllBookmarks();

    @Query("SELECT * FROM bookmarks WHERE bible_reference==:bible_reference")
    List<bookmark> getBookmark(String bible_reference);

    @Query("DELETE FROM bookmarks WHERE bible_reference = :bible_reference")
    void deleteBookmark(String bible_reference);

}
