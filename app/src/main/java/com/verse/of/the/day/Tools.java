package com.verse.of.the.day;

import android.content.res.AssetManager.*;
import android.content.SharedPreferences;
import android.content.Context.*;
import android.util.Log;
import java.io.*;
import android.content.res.*;
import android.content.*;

public class Tools {

boolean isNightMode(Context context) {
int nightModeFlags = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
return nightModeFlags == Configuration.UI_MODE_NIGHT_YES;
}

Boolean getTheme(SharedPreferences sharedP){
Boolean theme = sharedP.getBoolean("theme",true);
return theme;
}

String getFile(Context context, String filename) {
//NOTE To self:
//wondering about the efficiency of loading an entire book of the Bible into a single string
String filetext = "";
InputStream inputstream = null;
try {
AssetManager manager = context.getAssets();
inputstream = manager.open(filename);
int size = inputstream.available();
byte[] buffer = new byte[size];
inputstream.read(buffer);
inputstream.close();
filetext = new String(buffer, "UTF-8");

} catch (IOException ex) {
ex.printStackTrace();
return "could not load file";
}
return filetext;
}

public boolean isDigit(String letters) {

try {
Integer.parseInt(letters);
} catch (Exception e) {
return false;
}
return true;
}

public boolean isBook(String s, Bible bible) {
s = s.toLowerCase().trim();

for (String book : bible.books) {
if (s.equals(book.replace(".txt", ""))) {
return true;
}
}
return false;
}

public boolean isSpaceBook(String s, Bible b) {

for (String book : b.books) {
book = book.replace(".txt", "");
if (book.contains("_")) {
if (s.contains(book.replace("_", " "))) {
return true;
}
}
}
return false;
}

public boolean isBookChapter(String s, Bible bible) {
String str = s.toLowerCase().trim();
boolean containsbook = false;
boolean containsdigit = false;
boolean containsspace = false;
boolean isAligned = false;
boolean isTwoArgs = false;

for (String b : bible.books) {
if (str.contains(b.replace(".txt", ""))) {
containsbook = true;
} else {
if (isSpaceBook(str, bible)) {

containsbook = true;
str = replaceFirstSpace(str);

}
}
}

for (char letter : str.toCharArray()) {
if (isDigit(Character.toString(letter))) {
containsdigit = true;
}
if (Character.toString(letter).equals(" ")) {
containsspace = true;
}
}
if (str.split(" ").length == 2) {
isTwoArgs = true;
}
String[] sp = str.split(" ");

if (sp.length == 2 && isDigit(sp[1]) && !isDigit(sp[0])) {
isAligned = true;
}
if (containsdigit && containsbook && containsspace && isTwoArgs && isAligned) {
return true;
}
return false;
}

public boolean isBookChapterVerse(String str, Bible bible) {
boolean isAligned = false;
boolean containsbook = false;

for (String book : bible.books) {
book = book.replace(".txt", "");
if (str.contains(book)) {
containsbook = true;
} else {
if (isSpaceBook(str, bible)) {
containsbook = true;
str = replaceFirstSpace(str);
}
}
}
String[] sp = str.split(" ");
if (sp.length == 3 && isDigit(sp[2]) && isDigit(sp[1]) && !isDigit(sp[0])) {
isAligned = true;
}
boolean containsspace = str.contains(" ");
if (isAligned && containsbook && containsspace) {
return true;
}
return false;
}

public String replaceFirstSpace(String str) {
/*
		this function replaces the first space in str with an underscore
		*/
String[] s = str.split(" ");

char c = '_';
//remember, single quotes for chars, double quotes for strings
int charbeforespace = s[0].length();

String result = str.substring(0, charbeforespace) + c + str.substring(charbeforespace + 1);
return result;
}

}