package com.anibiar.app.ui.home

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anibiar.app.AniBiarApp
import com.anibiar.app.data.Media
import com.anibiar.app.data.displayTitle
import kotlinx.coroutines.launch

/** TikTok-style yellow for a saved bookmark. */
internal val FavYellow = androidx.compose.ui.graphics.Color(0xFFFFC400)

/** Whether [media] is in my favourites, plus a toggle that saves / removes it (and toasts on failure, e.g. not logged in). */
@Composable
internal fun rememberFav(media: Media): Pair<Boolean, () -> Unit> {
    val context = LocalContext.current
    val app = context.applicationContext as AniBiarApp
    val favs by app.social.myFavorites.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val tick = com.anibiar.app.ui.theme.rememberTick()
    return (media.id in favs) to {
        tick()
        scope.launch {
            runCatching { app.social.toggleFavorite(media.id, media.displayTitle, media.coverImage.extraLarge ?: media.coverImage.large) }
                .onFailure { Toast.makeText(context, it.message ?: "Couldn't save", Toast.LENGTH_SHORT).show() }
        }
    }
}
