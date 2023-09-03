package com.verse.of.the.day;
import android.content.Context;
import java.util.Scanner;


public class Verse {
	private Scanner scanner;
	private Context context;
	Bible bible = new Bible();
	Tools toolbox = new Tools();
	
	String book;
	int chapter;
	int verse;
public Verse(int bo,int chap,int ver){
	book = bible.books[bo];
	chapter = chap;
	verse = ver;
}
	
	
	String scripture_text = bible.getVerse(toolbox,context,book,chapter,verse);
}