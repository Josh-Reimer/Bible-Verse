package com.verse.of.the.day.wear

import android.content.res.AssetManager
import androidx.compose.ui.text.AnnotatedString
import kotlin.random.Random

/** One verse, addressed the same way the phone app's `Verse` is: book index + chapter + verse. */
data class WearVerse(
    val bookIndex: Int,
    val chapter: Int,
    val verse: Int,
    val properBook: String,
    val text: AnnotatedString,
) {
    val reference: String get() = "$properBook $chapter:$verse"
}

object WearVerseRepository {

    /** A pseudo-random verse, the same way the phone app's `VerseOfTheDay.getRandomRef` picks one. */
    fun randomVerse(assets: AssetManager, translation: String): WearVerse {
        val bookIndex = Random.nextInt(WearBible.books.size)
        val book = WearBible.books[bookIndex]
        val chapter = Random.nextInt(WearBible.getBookLength(assets, translation, book)) + 1
        val verse = Random.nextInt(WearBible.getChapterLength(assets, translation, book, chapter)) + 1
        return load(assets, translation, bookIndex, chapter, verse)
    }

    fun load(assets: AssetManager, translation: String, bookIndex: Int, chapter: Int, verse: Int): WearVerse {
        val book = WearBible.books[bookIndex]
        val properBook = WearTranslations.properBook(assets, translation, book)
        // The asset line carries its own "chapter:verse: " prefix (see CLAUDE.md's asset
        // format); the phone app leaves it in and shows it inline with the verse body
        // instead of in its header. This app's header already reads "BOOK chapter:verse",
        // so the same prefix here would just repeat it — strip it for the body text. The
        // red-letter JSON's spans never carried this prefix to begin with, so stripping it
        // here (rather than in WearBible.getVerse) keeps both sources agreeing.
        val plain = WearBible.getVerse(assets, translation, book, chapter, verse)
            .removePrefix("$chapter:$verse: ")
        val verseRef = "$bookIndex:$chapter:$verse"
        val annotated = WearRedLetter.annotated(assets, translation, verseRef, plain)
        return WearVerse(bookIndex, chapter, verse, properBook, annotated)
    }
}
