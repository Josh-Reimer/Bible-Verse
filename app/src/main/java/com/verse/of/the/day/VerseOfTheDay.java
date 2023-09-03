package com.verse.of.the.day;

import java.util.Random;
import java.util.Scanner;
import java.io.File;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.util.Formatter;
import java.time.LocalDate;
import android.content.Context;

public class VerseOfTheDay {

	public VerseOfTheDay(Scanner sc, Context context) {
		/*
		if (verseOfDayIsCurrent(sc)) {
			System.out.println(getVerseFromFile(sc,context));
		} else {
			setVerseOfDay(sc);
			System.out.println(getVerseFromFile(sc,context));
		}
		//	if there is no verse of the day, create a verse of the day
*/
	}

	String getRandomVerse(Scanner sc,Tools tools,Context context,Bible bible) {

		Random random = new Random();
		int randomBookNum = random.nextInt(bible.books.length);

		//System.out.println("randomBookNum is: " + randomBookNum);

		String randomBook = bible.books[randomBookNum];

		int randomChapterNum = random.nextInt(bible.getBookLength(tools,context, bible.books[randomBookNum])) + 1;

		//System.out.println("randomChapterNum is: " + randomChapterNum);

		int randomVerseNum = random.nextInt(bible.getChapterLength(context,tools, bible.books[randomBookNum], randomChapterNum)) + 1;

		//System.out.println("randomVerseNum is: " + randomVerseNum);

		String bookOfVerse = bible.books[randomBookNum].replace(".txt", "").replace("_", " ").toUpperCase() + "\n";

		String randomVerse = bookOfVerse + bible.getVerse(tools,context, randomBook, randomChapterNum, randomVerseNum);
		return randomVerse;
	}

Verse getRandomRef(Bible bible,Tools tools,Context context){
	Random dice = new Random();
	int randomBook = dice.nextInt(bible.books.length);
	int randomChapter = dice.nextInt(bible.getBookLength(tools,context, bible.books[randomBook])) + 1;
	int randomVerse = dice.nextInt(bible.getChapterLength(context,tools, bible.books[randomBook], randomChapter)) + 1;
	
	return new Verse(randomBook,randomChapter,randomVerse);
}

	public String getVerseFromFile(Scanner sc, Context context, Tools tools) {
		
			String f = tools.getFile(context, "verseoftheday.txt");
			sc = new Scanner(f);
			int count = 0;
			String result = "";
			while (sc.hasNext()) {
				String line = sc.nextLine();

				count += 1;
				if (count == 2 || count == 3) {
					result += "\n" + line;
				}
				if (count == 3) {
					return result;
				}
			}

		return "";
	}

	public void setVerseOfDay(Scanner sc, Tools tools,Context context,Bible bible) {
		LocalDate today = LocalDate.now();
		try {
			Formatter todaysVerse = new Formatter("verseoftheday.txt");

			todaysVerse.format("%s", today + "\n" + getRandomVerse(sc,tools,context,bible));
			todaysVerse.close();

		} catch (IOException e) {
			System.out.println("verse of the day is having troubles with files");
		}
	}


	public boolean verseOfDayExists() {
		//checks if file verseoftheday.txt exists

		try {
			File file = new File("verseoftheday.txt");
		} catch (Exception e) {
			return false;
		}
		return true;
	}

	public boolean verseOfDayIsCurrent(Scanner sc) {
		if (verseOfDayExists()) {
			try {
				File f = new File("verseoftheday.txt");
				LocalDate today = LocalDate.now();
				String now = today + "";
				sc = new Scanner(f);
				while (sc.hasNextLine()) {
					String l = sc.nextLine();
					if (l.equals(now)) {
						return true;

					}

				}
			} catch (IOException e) {
				System.out.println("file error");
			}
		}
		return false;
	}

}