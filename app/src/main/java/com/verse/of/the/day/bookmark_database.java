package com.verse.of.the.day;

import androidx.room.Database;
import androidx.room.RoomDatabase;

@Database(entities = {bookmark.class},version = 1)
public abstract class bookmark_database extends RoomDatabase {
    public abstract bookmark_dao bookmark_dao();
}
