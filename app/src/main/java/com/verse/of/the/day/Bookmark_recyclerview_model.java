package com.verse.of.the.day;

public class Bookmark_recyclerview_model {
    CharSequence scripture_text;
    String book;
    String ref;
    public Bookmark_recyclerview_model(CharSequence scripture_text, String book, String ref){
        this.scripture_text = scripture_text;
        this.book = book;
        this.ref = ref;
    }
}
