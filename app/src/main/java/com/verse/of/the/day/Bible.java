package com.verse.of.the.day;
import android.util.Log;

import java.io.File;
import java.util.Scanner;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.StringReader;
import java.io.IOException;
import android.content.Context;

public class Bible {

public String[] books = {"genesis.txt", "exodus.txt", "leviticus.txt", "numbers.txt", "deuteronomy.txt", "joshua.txt", "judges.txt", "ruth.txt", "first_samuel.txt", "second_samuel.txt", "first_kings.txt", "second_kings.txt", "first_chronicles.txt", "second_chronicles.txt", "ezra.txt", "nehemiah.txt", "esther.txt", "job.txt", "psalms.txt", "proverbs.txt", "eccliasiastes.txt", "song_of_solomon.txt", "isaiah.txt", "jeremiah.txt", "lamentations.txt", "ezekial.txt", "daniel.txt", "hosea.txt", "joel.txt", "amos.txt", "obadiah.txt", "jonah.txt", "micah.txt", "nahum.txt", "habakkuk.txt", "zephaniah.txt", "haggai.txt", "zechariah.txt", "malachi.txt", "matthew.txt", "mark.txt", "luke.txt", "john.txt", "acts.txt", "romans.txt", "first_corinthians.txt", "second_corinthians.txt", "galatians.txt", "ephesians.txt", "philipians.txt", "colossians.txt", "first_thesselonians.txt", "second_thesselonians.txt", "first_timothy.txt", "second_timothy.txt", "titus.txt", "philemon.txt", "hebrews.txt", "james.txt", "first_peter.txt", "second_peter.txt", "first_john.txt", "second_john.txt", "third_john.txt", "jude.txt", "revelation.txt"};


public String getChapter(Context context,Tools tools,String book, int chapter) {
String chap = "";
String bookObject = tools.getFile(context,book);

BufferedReader bf = null;
try{
String line;
bf = new BufferedReader(new StringReader(bookObject));

while ((line = bf.readLine()) != null) {
String chapnum = line.split(":")[0];
String chapnumstring = Integer.toString(chapter);

if (chapnum.equals(chapnumstring)) {
chap = chap + line + "\n";
} else {
continue;
}
}
try{bf.close();}catch(IOException e){return "io exception";}
}catch(IOException e){
try{bf.close();}catch(IOException exc){return "io exception";}
return "io exception";}
return chap;
}
public String getVerse(Tools tools,Context context, String book, int chapter, int verse) {
String sChapter = getChapter(context,tools, book, chapter);
String[] verses = sChapter.split("\n");
return verses[verse - 1];
}

public int getChapterLength(Context context,Tools tools, String book, int chapter) {
String chapterstring = getChapter(context,tools, book, chapter);
String[] lines = chapterstring.split("\n");
int chapterlength = lines.length;
return chapterlength;
}

public int getBookLength(Tools tools,Context context, String book) {
//this function returns the number of chapters in the book asked for
String bookObject = tools.getFile(context,book);

String lastline = bookObject.substring(bookObject.lastIndexOf("\n"));
Log.i("verse: Bible class",lastline);
return Integer.parseInt(lastline.split(":")[0].trim());
}

public String getRange(Scanner sc, Verse firstRef, Verse secondRef) {
int firstChapNum = firstRef.chapter;
int firstVerseNum = firstRef.verse;
int secondChapNum = secondRef.chapter;
int secondVerseNum = secondRef.verse;
String range = "";
try {
boolean inRange = false;
int count = 0;
for (String book : books) {
count = count + 1;
if (!inRange){
if (!firstRef.book.equals(book)) {
continue;
}
}
File file = new File("file/path" + book);
sc = new Scanner(file);

while (sc.hasNextLine()) {
String l = sc.nextLine();
if (!l.contains(":")) {
continue;
}

int versenum = Integer.parseInt(l.split(":")[1]);
int chapnum = Integer.parseInt(l.split(":")[0]);

if (firstRef.book.equals(book)) {
if (firstChapNum == chapnum) {
if (firstVerseNum == versenum) {
inRange = true;

}
}
}

if (inRange) {
range = range + l + "\n";
}
if (secondRef.book.equals(book)) {
if (chapnum == secondChapNum) {
if (versenum == secondVerseNum) {
inRange = false;
break;
}
}
}
}
}
} catch (FileNotFoundException e) {
System.out.println("the required files could not be loaded");
}
return range;
}

}