package com.verse.of.the.day

import android.content.Context
import io.github.gawwr4v.radialmenu.RadialMenuView
import io.github.gawwr4v.radialmenu.RadialMenuItem
import io.github.gawwr4v.radialmenu.RadialMenuTriggerMode

interface RadialMenuListener {
    fun onNewVerse()
    fun onLookUp()
    fun onToggleBookmark()
    fun onShare()
}

fun setupRadialMenu(
    context: Context,
    radialMenu: RadialMenuView,
    listener: RadialMenuListener,
    isBookmarked: Boolean
) {
    android.util.Log.d("RadialMenuSetup", "setupRadialMenu called")
    val items = listOf(
        RadialMenuItem(
            context = context,
            id = 1,
            iconRes = R.drawable.dice_48,
            label = "New Verse"
        ),
        RadialMenuItem(
            context = context,
            id = 2,
            iconRes = R.drawable.open_in_new_48,
            label = "Look Up"
        ),
        RadialMenuItem(
            context = context,
            id = 3,
            iconRes = if (isBookmarked) R.drawable.bookmark_solid_48 else R.drawable.bookmark_border_48,
            label = "Bookmark"
        ),
        RadialMenuItem(
            context = context,
            id = 4,
            iconRes = R.drawable.baseline_share_24,
            label = "Share"
        )
    )

    android.util.Log.d("RadialMenuSetup", "Created ${items.size} menu items")
    radialMenu.setItems(items)
    android.util.Log.d("RadialMenuSetup", "Items set on menu")

    radialMenu.triggerMode = RadialMenuTriggerMode.LongPress(positionAware = true)
    radialMenu.enableEdgeHugLayout = false

    radialMenu.onItemSelected = { item ->
        android.util.Log.d("RadialMenuSetup", "Item selected: ${item.id}")
        when (item.id) {
            1 -> listener.onNewVerse()
            2 -> listener.onLookUp()
            3 -> listener.onToggleBookmark()
            4 -> listener.onShare()
        }
    }

    android.util.Log.d("RadialMenuSetup", "Radial menu setup complete")
}

fun updateRadialMenuBookmarkIcon(
    context: Context,
    radialMenu: RadialMenuView,
    isBookmarked: Boolean
) {
    val items = listOf(
        RadialMenuItem(
            context = context,
            id = 1,
            iconRes = R.drawable.dice_48,
            label = "New Verse"
        ),
        RadialMenuItem(
            context = context,
            id = 2,
            iconRes = R.drawable.open_in_new_48,
            label = "Look Up"
        ),
        RadialMenuItem(
            context = context,
            id = 3,
            iconRes = if (isBookmarked) R.drawable.bookmark_solid_48 else R.drawable.bookmark_border_48,
            label = "Bookmark"
        ),
        RadialMenuItem(
            context = context,
            id = 4,
            iconRes = R.drawable.baseline_share_24,
            label = "Share"
        )
    )
    radialMenu.setItems(items)
}
