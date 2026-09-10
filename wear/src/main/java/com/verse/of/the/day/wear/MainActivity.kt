package com.verse.of.the.day.wear

import android.content.res.AssetManager
import android.os.Bundle
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The whole watch app: one screen showing a verse and a die to reroll it — see CLAUDE.md's
 * "minimal verse view" scope. Standalone (no phone pairing required): the Bible text, book
 * names and red-letter spans are bundled straight from the phone app's assets (kjv/rvr1909/cuvs
 * only — the three the phone would pick automatically by device language; see
 * `WearTranslations`), so a fresh random verse is just an asset read, same as the phone app's
 * cold start.
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
                WearApp(assets = assets)
            }
        }
    }
}

@Composable
fun WearApp(assets: AssetManager) {
    val translation = remember { WearTranslations.current() }
    var verse by remember { mutableStateOf<WearVerse?>(null) }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    suspend fun reroll() {
        verse = withContext(Dispatchers.IO) { WearVerseRepository.randomVerse(assets, translation) }
        // Every new verse should open at its top, not wherever the previous one was scrolled to.
        scrollState.scrollTo(0)
    }

    LaunchedEffect(Unit) { reroll() }

    Scaffold(timeText = { TimeText() }) {
        val current = verse
        if (current == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
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
                onClick = { scope.launch { reroll() } },
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
