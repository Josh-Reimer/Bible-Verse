package com.verse.of.the.day.wear

import android.content.res.AssetManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import org.json.JSONObject
import java.nio.charset.StandardCharsets

/**
 * Words-of-Christ colouring, ported from the phone app's `RedLetter`. The bundled
 * `red_letter_<translation>.json` files are shared byte-for-byte with the phone module: a map
 * of "book:chapter:verse" to a verse string with the red span(s) wrapped in
 * `<font color="#RRGGBB">...</font>`. The phone renders that through `Html.fromHtml`; Compose
 * has no text view to hand a `Spanned` to, so this walks the same tag by hand into an
 * `AnnotatedString` instead. The generator scripts never emit nested tags or entities, so a
 * single non-greedy regex over the font tag is all parsing this needs.
 */
object WearRedLetter {

    private val FONT_TAG = Regex("<font color=\"(#[0-9A-Fa-f]{6})\">(.*?)</font>", RegexOption.DOT_MATCHES_ALL)

    private val cache = mutableMapOf<String, JSONObject?>()

    private fun load(assets: AssetManager, translation: String): JSONObject? {
        cache[translation]?.let { return it }
        if (cache.containsKey(translation)) return null // parsed before and came up empty
        val data = try {
            assets.open("red_letter_$translation.json").use { input ->
                JSONObject(input.readBytes().toString(StandardCharsets.UTF_8))
            }
        } catch (_: Exception) {
            null // no red-letter file for this translation
        }
        cache[translation] = data
        return data
    }

    // org.json's optString(key, null) is a Java API that returns a plain null just fine at
    // runtime, but its signature declares a non-null default, so Kotlin only sees it through
    // a platform type; a small has()-guarded wrapper avoids leaning on that.
    private fun htmlFor(data: JSONObject, verseRef: String): String? =
        if (data.has(verseRef)) data.optString(verseRef) else null

    /**
     * The verse as an [AnnotatedString] with red-letter spans coloured in, or a plain
     * [AnnotatedString] of [fallback] if this verse has no red-letter markup.
     */
    fun annotated(assets: AssetManager, translation: String, verseRef: String, fallback: String): AnnotatedString {
        val data = load(assets, translation)
        val html = data?.let { htmlFor(it, verseRef) }
            ?: return AnnotatedString(fallback)

        return buildAnnotatedString(html)
    }

    private fun buildAnnotatedString(html: String): AnnotatedString {
        val builder = AnnotatedString.Builder()
        var last = 0
        for (match in FONT_TAG.findAll(html)) {
            if (match.range.first > last) builder.append(html.substring(last, match.range.first))
            val (colorHex, text) = match.destructured
            builder.withStyle(SpanStyle(color = parseColor(colorHex))) {
                append(text)
            }
            last = match.range.last + 1
        }
        if (last < html.length) builder.append(html.substring(last))
        return builder.toAnnotatedString()
    }

    private fun parseColor(hex: String): Color = Color(android.graphics.Color.parseColor(hex))
}
