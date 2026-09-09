package com.verse.of.the.day.wear

import android.content.res.AssetManager
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.Locale

/**
 * A small, read-only port of the phone app's `Bible`/`Tools` classes, trimmed to what a
 * quick-glance watch screen needs: pick a random verse and read its text back out of the
 * bundled assets. See `com.verse.of.the.day.Bible` in the phone module for the original —
 * the parsing rules here (verses addressed positionally within a chapter, chapters ended by
 * the next valid chapter-number line) are kept identical on purpose, since both modules read
 * the exact same asset files.
 */
object WearBible {

    // Same order, same five misspelled filenames, as the phone app's Bible.books — the
    // red-letter JSON and book_names.json bundled here are keyed on this order too.
    val books = arrayOf(
        "genesis.txt", "exodus.txt", "leviticus.txt", "numbers.txt", "deuteronomy.txt",
        "joshua.txt", "judges.txt", "ruth.txt", "first_samuel.txt", "second_samuel.txt",
        "first_kings.txt", "second_kings.txt", "first_chronicles.txt", "second_chronicles.txt",
        "ezra.txt", "nehemiah.txt", "esther.txt", "job.txt", "psalms.txt", "proverbs.txt",
        "eccliasiastes.txt", "song_of_solomon.txt", "isaiah.txt", "jeremiah.txt",
        "lamentations.txt", "ezekial.txt", "daniel.txt", "hosea.txt", "joel.txt", "amos.txt",
        "obadiah.txt", "jonah.txt", "micah.txt", "nahum.txt", "habakkuk.txt", "zephaniah.txt",
        "haggai.txt", "zechariah.txt", "malachi.txt", "matthew.txt", "mark.txt", "luke.txt",
        "john.txt", "acts.txt", "romans.txt", "first_corinthians.txt", "second_corinthians.txt",
        "galatians.txt", "ephesians.txt", "philipians.txt", "colossians.txt",
        "first_thesselonians.txt", "second_thesselonians.txt", "first_timothy.txt",
        "second_timothy.txt", "titus.txt", "philemon.txt", "hebrews.txt", "james.txt",
        "first_peter.txt", "second_peter.txt", "first_john.txt", "second_john.txt",
        "third_john.txt", "jude.txt", "revelation.txt"
    )

    private val MISSPELLED_STEMS = arrayOf(
        arrayOf("eccliasiastes", "ecclesiastes"),
        arrayOf("ezekial", "ezekiel"),
        arrayOf("philipians", "philippians"),
        arrayOf("thesselonians", "thessalonians"),
    )

    // Locale.ROOT for the same reason as the phone app: these are English filenames, and a
    // Turkish-locale uppercase would mangle the dotted/dotless I.
    fun getProperName(bookFile: String): String {
        var stem = bookFile.replace(".txt", "")
        for (fix in MISSPELLED_STEMS) {
            if (stem.endsWith(fix[0])) {
                stem = stem.substring(0, stem.length - fix[0].length) + fix[1]
                break
            }
        }
        return stem.replace("_", " ").uppercase(Locale.ROOT)
    }

    private fun readAsset(assets: AssetManager, translation: String, filename: String): String {
        assets.open("$translation/$filename").use { input ->
            val reader = BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8))
            return reader.readText()
        }
    }

    fun getChapter(assets: AssetManager, translation: String, book: String, chapter: Int): String {
        val text = readAsset(assets, translation, book)
        val chapterNumString = chapter.toString()
        val builder = StringBuilder()
        for (line in text.lineSequence()) {
            val chapNum = line.substringBefore(":")
            if (chapNum == chapterNumString) {
                builder.append(line).append("\n")
            } else if (builder.isNotEmpty()) {
                // A line that parses as a chapter number that isn't ours means we've read
                // past the target chapter; a blank line or annotation just gets skipped.
                if (chapNum.trim().toIntOrNull() != null) break
            }
        }
        return builder.toString()
    }

    fun getVerse(assets: AssetManager, translation: String, book: String, chapter: Int, verse: Int): String {
        val verses = getChapter(assets, translation, book, chapter).split("\n").filter { it.isNotEmpty() }
        if (verse < 1 || verses.isEmpty()) return ""
        // Translations don't all end a chapter on the same verse; clamp rather than overrun.
        return if (verse > verses.size) verses.last() else verses[verse - 1]
    }

    fun getChapterLength(assets: AssetManager, translation: String, book: String, chapter: Int): Int {
        return getChapter(assets, translation, book, chapter).split("\n").count { it.isNotEmpty() }
    }

    fun getBookLength(assets: AssetManager, translation: String, book: String): Int {
        val stripped = readAsset(assets, translation, book).trim()
        val lastLine = stripped.substring(stripped.lastIndexOf("\n") + 1)
        return lastLine.substringBefore(":").trim().toInt()
    }
}
