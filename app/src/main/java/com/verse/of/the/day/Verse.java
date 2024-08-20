package com.verse.of.the.day;
import android.content.Context;
import java.util.Scanner;


public class Verse {
	private Scanner scanner;
	private Context context;
	Bible bible = new Bible();
	Tools toolbox = new Tools();

	String proper_book;
	String book;
	int chapter;
	int verse;
	int book_int;
	String scripture_text;
	String reference;
	String full_text;

	public Verse(Context con, int bo,int chap,int ver){
		context = con;
		book = bible.books[bo];
		chapter = chap;
		verse = ver;
		book_int = bo;
		finish();
	}

	public Verse(Context con,int book_of_bible,String verse_str) {
		String[] split = verse_str.split(":");
		book = bible.books[book_of_bible];
		book_int = book_of_bible;
		chapter = Integer.parseInt(split[0]);
		verse = Integer.parseInt(split[1]);
		context = con;
		book_int = book_of_bible;
		finish();
	}

	void finish(){
		proper_book = book.replace(".txt","").replace("_"," ").toUpperCase() + "\n";
		scripture_text = bible.getVerse(toolbox,context,book,chapter,verse);
		full_text = proper_book + scripture_text;
		reference = book_int+":"+chapter+":"+verse;
	}
}