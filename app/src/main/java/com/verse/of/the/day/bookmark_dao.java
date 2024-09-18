package com.verse.of.the.day;

import androidx.room.Dao;
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
}
