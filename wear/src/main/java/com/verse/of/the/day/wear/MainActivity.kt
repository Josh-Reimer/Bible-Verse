package com.verse.of.the.day.wear

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The whole watch app: one screen showing a verse and a die to reroll it — see CLAUDE.md's
 * "minimal verse view" scope. It opens on the day's verse — the phone's, synced over the Data
 * Layer, when there is one (see `WearDailyVerse`) — and the die rolls a random verse for this
 * screen only, leaving the day's verse alone, the same as the phone's main-screen dice.
 * Standalone (no phone pairing required): the Bible text, book names and red-letter spans are
 * bundled straight from the phone app's assets (kjv/rvr1909/cuvs only — the three the phone
 * would pick automatically by device language; see `WearTranslations`), so a verse is just an
 * asset read.
 *
 * No share action: Wear OS has no share targets to send to without a Bluetooth-paired phone or
 * Nearby Share, and `ACTION_SEND` on a bare watch just opens and immediately closes an empty
 * chooser (confirmed live against a Wear OS emulator) — not a useful action here.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                WearApp()
            }
        }
    }
}

@Composable
fun WearApp() {
    val context = LocalContext.current.applicationContext
    val assets = context.assets
    val translation = remember { WearTranslations.current() }
    var verse by remember { mutableStateOf<WearVerse?>(null) }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var failed by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf<Job?>(null) }

    // Each load cancels the one before it, so a slower earlier read (a quick double tap on the
    // die, or a phone update landing mid-reroll) can never overwrite the newest verse.
    fun show(load: () -> WearVerse) {
        loading?.cancel()
        loading = scope.launch {
            try {
                verse = withContext(Dispatchers.IO) { load() }
                failed = false
                // Every new verse should open at its top, not wherever the previous one was scrolled to.
                scrollState.scrollTo(0)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A missing or unreadable asset. With a verse already on screen it stays there;
                // with none, the error screen below offers a retry instead of a spinner forever.
                Log.w("WearApp", "Couldn't load a verse", e)
                failed = true
            }
        }
    }

    fun showToday() = show { WearDailyVerse.today(context, translation) }

    LaunchedEffect(Unit) { showToday() }

    // Follow the phone while open: its day's verse can arrive after launch (the phone app
    // hadn't run yet today) or change (its widget's die rerolled it).
    DisposableEffect(Unit) {
        val listener = DataClient.OnDataChangedListener { events ->
            WearDailyVerse.refFromEvents(context, events)?.let { (book, chapter, verse) ->
                show { WearVerseRepository.load(assets, translation, book, chapter, verse) }
            }
        }
        val client = Wearable.getDataClient(context)
        client.addListener(listener)
        onDispose { client.removeListener(listener) }
    }

    Scaffold(timeText = { TimeText() }) {
        val current = verse
        if (current == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (failed) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.verse_load_failed),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 26.dp),
                        )
                        Button(
                            onClick = { showToday() },
                            modifier = Modifier
                                .padding(top = 14.dp)
                                .size(40.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = stringResource(R.string.retry),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                } else {
                    CircularProgressIndicator()
                }
            }
            return@Scaffold
        }

        // A plain scrollable Column rather than ScalingLazyColumn: this screen is short
        // (title, verse, one button), and ScalingLazyColumn's auto-centering pushed the
        // button down into the round bezel's curve, clipping it. The explicit top/bottom
        // padding here instead leaves clearance for TimeText above and the bezel curve
        // below, verified live against a round Wear OS emulator.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 26.dp)
                // A small round watch face (e.g. 192dp) leaves very little vertical room —
                // even a short verse's title+body can reach close to the full screen height,
                // and the round bezel clips a button well before the literal bottom of the
                // view. 40dp of bottom clearance plus a smaller (40dp) button were both
                // needed, tuned by screenshotting against a live emulator: at the default
                // ~52dp button size and 28dp padding it was cut by the curve on anything but
                // a very long (fully scrolled) verse.
                .padding(top = 30.dp, bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = current.reference,
                style = MaterialTheme.typography.title3,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = current.text,
                style = MaterialTheme.typography.body2,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
            Button(
                onClick = { show { WearVerseRepository.randomVerse(assets, translation) } },
                modifier = Modifier
                    .padding(top = 14.dp)
                    .size(40.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "New verse",
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
