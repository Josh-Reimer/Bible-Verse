package com.verse.of.the.day.wear

import android.content.Context
import android.net.Uri
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * The watch's verse of the day — the phone's, when it has one for today.
 *
 * The phone app's `DailyVerse` publishes its verse (the one its widget, notification and main
 * screen share) as a Data Layer item at [PATH]: a `"book:chapter:verse"` reference plus the
 * epoch day it belongs to. A phone verse for today always wins. Without one — no paired phone,
 * the phone app not installed, or not yet run today — the watch picks its own verse and keeps
 * it for the rest of the day, the same way the phone does; a phone verse arriving later still
 * replaces it.
 *
 * Only the reference crosses over; the text is read in the watch's own translation.
 */
object WearDailyVerse {

    // Must match DailyVerse in the :app module.
    private const val PATH = "/daily_verse"
    private const val KEY_REF = "ref"
    private const val KEY_DAY = "day"

    private const val PREFS = "settings"
    private const val PREF_REF = "daily_verse_ref"
    private const val PREF_DAY = "daily_verse_day"

    /** Today's verse. Blocks on the Data Layer and reads assets — call it off the main thread. */
    fun today(context: Context, translation: String): WearVerse {
        val day = LocalDate.now().toEpochDay()
        val assets = context.assets
        val ref = phoneRefFor(day, fetchFromPhone(context)) ?: storedRef(context, day)
        if (ref != null) {
            parse(ref)?.let { (book, chapter, verse) ->
                store(context, ref, day)
                return WearVerseRepository.load(assets, translation, book, chapter, verse)
            }
        }
        val picked = WearVerseRepository.randomVerse(assets, translation)
        store(context, "${picked.bookIndex}:${picked.chapter}:${picked.verse}", day)
        return picked
    }

    /**
     * Today's verse from a live Data Layer change (the phone picked or rerolled it while the
     * watch app was open), or null if the events carry nothing for today. Reads the buffer
     * synchronously, since it is released once the listener returns.
     */
    fun refFromEvents(context: Context, events: DataEventBuffer): Triple<Int, Int, Int>? {
        val day = LocalDate.now().toEpochDay()
        val ref = phoneRefFor(day, events
            .filter { it.type == DataEvent.TYPE_CHANGED }
            .map { it.dataItem })
            ?: return null
        store(context, ref, day)
        return parse(ref)
    }

    private fun fetchFromPhone(context: Context): List<DataItem> = try {
        // No host in the URI: matches the item whichever node (the phone) published it.
        val uri = Uri.Builder().scheme(PutDataRequest.WEAR_URI_SCHEME).path(PATH).build()
        val buffer = Tasks.await(Wearable.getDataClient(context).getDataItems(uri), 5, TimeUnit.SECONDS)
        try {
            buffer.map { it.freeze() }
        } finally {
            buffer.release()
        }
    } catch (_: Exception) {
        // No Play services, no Data Layer, or a timeout — fall back to the watch's own pick.
        emptyList()
    }

    private fun phoneRefFor(day: Long, items: List<DataItem>): String? = items
        .filter { it.uri.path == PATH }
        .map { DataMapItem.fromDataItem(it).dataMap }
        .firstOrNull { it.getLong(KEY_DAY) == day }
        ?.getString(KEY_REF)

    private fun storedRef(context: Context, day: Long): String? {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return if (sp.getLong(PREF_DAY, 0) == day) sp.getString(PREF_REF, null) else null
    }

    private fun store(context: Context, ref: String, day: Long) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(PREF_REF, ref)
            .putLong(PREF_DAY, day)
            .apply()
    }

    private fun parse(ref: String): Triple<Int, Int, Int>? {
        val parts = ref.split(":").mapNotNull { it.toIntOrNull() }
        if (parts.size != 3 || parts[0] !in WearBible.books.indices) return null
        return Triple(parts[0], parts[1], parts[2])
    }
}
