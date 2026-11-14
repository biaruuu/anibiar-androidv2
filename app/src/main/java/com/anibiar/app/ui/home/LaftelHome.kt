package com.anibiar.app.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.anibiar.app.data.HistoryItem
import com.anibiar.app.data.Media
import com.anibiar.app.data.TmdbArt
import com.anibiar.app.data.cleanDescription
import com.anibiar.app.data.displayTitle
import com.anibiar.app.data.posterUrl
import com.anibiar.app.ui.theme.Ani
import com.anibiar.app.ui.theme.FluentIcons
import com.anibiar.app.ui.theme.inter
import kotlinx.coroutines.delay

/** Section title: big, bold, white (Laftel style). */
@Composable
internal fun LaftelTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = inter(22f, FontWeight.Bold), color = Color.White, modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
}

private fun tagline(m: Media): String {
    val d = m.cleanDescription.trim()
    val first = d.split(Regex("(?<=[.!?])\\s")).firstOrNull().orEmpty().trim()
    return when {
        first.length in 8..90 -> first
        d.isNotEmpty() -> d.take(80).trimEnd() + "…"
        else -> m.genres.orEmpty().take(3).joinToString(" • ")
    }
}

/** Full-bleed hero pager: art, title logo, one-line tagline, a wide white Play button and page dots. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LaftelHero(
    items: List<Media>, art: Map<Int, TmdbArt>, height: Dp,
    onWatch: (Int) -> Unit, onDetails: (Int) -> Unit, onBell: () -> Unit, isFav: @Composable (Media) -> Pair<Boolean, () -> Unit>,
) {
    val pager = rememberPagerState(pageCount = { items.size })
    LaunchedEffect(items.size) {
        while (items.size > 1) {
            delay(6_000)
            if (!pager.isScrollInProgress) pager.animateScrollToPage((pager.currentPage + 1) % items.size)
        }
    }
    Box(Modifier.fillMaxWidth().height(height).background(Ani.Onyx)) {
        HorizontalPager(pager, Modifier.fillMaxSize()) { i ->
            val m = items[i]; val a = art[m.id]
            Box(Modifier.fillMaxSize().clickable { onDetails(m.id) }) {
                AsyncImage(
                    model = a?.backdrop ?: m.bannerImage ?: m.posterUrl, contentDescription = null,
                    contentScale = ContentScale.Crop, alignment = Alignment.Center, modifier = Modifier.fillMaxSize(),
                )
                Box(Modifier.fillMaxWidth().height(130.dp).background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent))))
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.6f).background(Brush.verticalGradient(listOf(Color.Transparent, Ani.Onyx.copy(alpha = 0.92f), Ani.Onyx))))
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 52.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (a?.logo != null) AsyncImage(
                        model = a.logo, contentDescription = m.displayTitle, contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth(0.74f).heightIn(max = 96.dp).height(96.dp),
                    ) else Text(m.displayTitle, style = inter(30f, FontWeight.ExtraBold, lineHeight = 36f), color = Color.White, textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(10.dp))
                    Text(tagline(m), style = inter(16f), color = Color.White, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Row(
                            Modifier.width(188.dp).height(52.dp).clip(RoundedCornerShape(6.dp)).background(Color.White).clickable { onWatch(m.id) },
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
                        ) {
                            Icon(FluentIcons.Play, null, tint = Color.Black, modifier = Modifier.size(26.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Play", style = inter(21f, FontWeight.Bold), color = Color.Black)
                        }
                        Box(Modifier.size(52.dp).clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = 0.18f)).clickable { onDetails(m.id) }, contentAlignment = Alignment.Center) {
                            Icon(FluentIcons.Info, "Details", tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                        val (fav, toggle) = isFav(m)
                        Box(Modifier.size(52.dp).clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = 0.18f)).clickable { toggle() }, contentAlignment = Alignment.Center) {
                            Icon(if (fav) FluentIcons.Check else FluentIcons.Plus, "Favorite", tint = if (fav) Color(0xFFFFC857) else Color.White, modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("KIRO", style = inter(28f, FontWeight.Black, letterSpacing = 1.5f).copy(fontStyle = FontStyle.Italic), color = Color.White)
            Icon(FluentIcons.Bell, "Notifications", tint = Color.White, modifier = Modifier.size(28.dp).clip(CircleShape).clickable(onClick = onBell))
        }
        Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(items.size) { i ->
                Box(Modifier.size(7.dp).clip(CircleShape).background(if (pager.currentPage == i) Color.White else Color.White.copy(alpha = 0.35f)))
            }
        }
    }
}

/** Continue Watching: 16:9 thumbnail with a play disc and a progress bar, then title, an info button and the episode. */
@Composable
internal fun LaftelContinue(entries: List<HistoryItem>, onClick: (HistoryItem) -> Unit, onInfo: (HistoryItem) -> Unit, onRemove: (HistoryItem) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 26.dp)) {
        LaftelTitle("Continue Watching")
        Spacer(Modifier.height(14.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(entries, key = { it.mediaId }) { item ->
                Column(Modifier.width(196.dp)) {
                    Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(4.dp)).background(Ani.Item).clickable { onClick(item) }) {
                        AsyncImage(item.image, item.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        Box(Modifier.align(Alignment.Center).size(40.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                            Icon(FluentIcons.Play, null, tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                        Box(Modifier.align(Alignment.BottomCenter).padding(horizontal = 8.dp, vertical = 8.dp).fillMaxWidth().height(4.dp).clip(CircleShape).background(Color.White)) {
                            Box(Modifier.fillMaxWidth(item.progress.coerceIn(0.03f, 1f)).fillMaxHeight().background(com.anibiar.app.ui.theme.Appearance.accent))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.Top) {
                        Text(item.title, style = inter(15f, lineHeight = 20f), color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        var menu by remember { mutableStateOf(false) }
                        Box {
                            Icon(FluentIcons.Info, "More", tint = Color.White, modifier = Modifier.padding(start = 6.dp).size(28.dp).clip(CircleShape).clickable { menu = true }.padding(2.dp))
                            DropdownMenu(menu, { menu = false }) {
                                DropdownMenuItem(text = { Text("Details") }, onClick = { menu = false; onInfo(item) })
                                DropdownMenuItem(text = { Text("Remove") }, onClick = { menu = false; onRemove(item) })
                            }
                        }
                    }
                    Text("Episode ${item.episode}", style = inter(15f, FontWeight.Bold), color = Color.White)
                }
            }
        }
    }
}

/** Portrait poster with the title under it. */
@Composable
internal fun LaftelPoster(m: Media, onClick: () -> Unit, modifier: Modifier = Modifier, titleLines: Int = 2) {
    Column(modifier.clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(0.7f).clip(RoundedCornerShape(4.dp)).background(Ani.Item)) {
            PosterImage(url = m.posterUrl, contentDescription = m.displayTitle, maxSide = 420, modifier = Modifier.fillMaxSize())
        }
        Spacer(Modifier.height(8.dp))
        Text(m.displayTitle, style = inter(15f, lineHeight = 21f), color = Color.White, maxLines = titleLines, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun LaftelPosterRow(list: List<Media>, onOpen: (Int) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(list, key = { it.id }) { m -> LaftelPoster(m, { onOpen(m.id) }, Modifier.width(124.dp)) }
    }
}

/** Ranked row: a huge outlined number next to each poster. */
@Composable
internal fun LaftelRankRow(list: List<Media>, onOpen: (Int) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        itemsIndexed(list, key = { _, m -> m.id }) { i, m ->
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "${i + 1}", maxLines = 1, softWrap = false,
                    style = inter(86f, FontWeight.ExtraBold, letterSpacing = -6f).copy(fontStyle = FontStyle.Italic, color = Color.White, drawStyle = Stroke(width = 4f)),
                    modifier = Modifier.padding(start = 4.dp, end = 6.dp).padding(bottom = 0.dp),
                )
                Column(Modifier.width(124.dp).clickable { onOpen(m.id) }) {
                    Box(Modifier.fillMaxWidth().aspectRatio(0.7f).clip(RoundedCornerShape(4.dp)).background(Ani.Item)) {
                        PosterImage(url = m.posterUrl, contentDescription = m.displayTitle, maxSide = 420, modifier = Modifier.fillMaxSize())
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(m.displayTitle, style = inter(15f, lineHeight = 21f), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
