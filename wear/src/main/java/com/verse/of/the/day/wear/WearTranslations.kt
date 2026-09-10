package com.verse.of.the.day.wear

import android.content.res.AssetManager
import android.content.res.Resources
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.Locale

/**
 * The watch app's trimmed translation set. Standalone with no settings screen, so there is
 * nothing to persist here — see the phone app's `Translations` for the full registry this is
 * cut down from. Kept: the three translations the phone picks *automatically* by device
 * language (KJV/RVR1909/CUVS); left out: ASV and BSB, which the phone only reaches by a
 * manual Settings choice this app has no UI for, so bundling them would only cost watch
 * storage for translations nobody here could ever select.
 */
object WearTranslations {

    data class Entry(val code: String, val language: String)

    val ALL = arrayOf(
        Entry("kjv", "en"),
        Entry("rvr1909", "es"),
        Entry("cuvs", "zh"),
    )
    const val DEFAULT = "kjv"

    private var bookNames: MutableMap<String, Array<String>>? = null

    /** The bundled translation matching the *system* language, or the KJV. */
    fun current(): String {
        val locale: Locale? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Resources.getSystem().configuration.locales.get(0)
        } else {
            @Suppress("DEPRECATION")
            Resources.getSystem().configuration.locale
        }
        val language = locale?.language ?: ""
        return ALL.firstOrNull { it.language == language }?.code ?: DEFAULT
    }

    /**
     * The book's display name in the given translation's language, or the English filename
     * form when that translation has no localised names (the phone's `Translations.properBook`,
     * cut down to this module's three translations).
     */
    fun properBook(assets: AssetManager, code: String, bookFile: String): String {
        val names = namesFor(assets, code) ?: return WearBible.getProperName(bookFile)
        val index = WearBible.books.indexOf(bookFile)
        return if (index in names.indices) names[index] else WearBible.getProperName(bookFile)
    }

    private fun namesFor(assets: AssetManager, code: String): Array<String>? {
        var cache = bookNames
        if (cache == null) {
            cache = mutableMapOf()
            try {
                assets.open("book_names.json").use { input ->
                    val json = input.readBytes().toString(StandardCharsets.UTF_8)
                    val root = JSONObject(json)
                    val bookCount = WearBible.books.size
                    for (entry in ALL) {
                        val array: JSONArray = root.optJSONArray(entry.code) ?: continue
                        if (array.length() != bookCount) continue
                        cache[entry.code] = Array(bookCount) { i -> array.optString(i) }
                    }
                }
            } catch (_: Exception) {
                // no localised names available — English filenames it is
            }
            bookNames = cache
        }
        return cache[code]
    }
}
