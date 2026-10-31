@file:OptIn(ExperimentalLayoutApi::class)

package com.anibiar.app.ui.details

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import kotlin.math.sqrt
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import com.anibiar.app.AniBiarApp
import com.anibiar.app.data.CharacterEdge
import com.anibiar.app.data.Media
import com.anibiar.app.data.TmdbImages
import com.anibiar.app.data.airedEpisodes
import com.anibiar.app.data.airedLabel
import com.anibiar.app.data.backdropUrl
import com.anibiar.app.data.cleanDescription
import com.anibiar.app.data.displayName
import com.anibiar.app.data.displayTitle
import com.anibiar.app.data.formatLabel
import com.anibiar.app.data.formatName
import com.anibiar.app.data.posterUrl
import com.anibiar.app.data.score10
import com.anibiar.app.data.seasonLabel
import com.anibiar.app.data.statusLabel
import com.anibiar.app.data.studioNames
import com.anibiar.app.data.year
import com.anibiar.app.ui.home.AniTopBar
import com.anibiar.app.ui.home.ErrorState
import com.anibiar.app.ui.home.InfoBadge
import com.anibiar.app.ui.home.PosterImage
import com.anibiar.app.ui.theme.Ani
import com.anibiar.app.ui.theme.AniIcons
import com.anibiar.app.ui.theme.inter
import java.text.NumberFormat
import java.util.Locale

private val StatusGreen = Color(0xFF4ADE80)
private val StatusBlue = Color(0xFF60A5FA)

@Composable
fun DetailsScreen(
    id: Int,
    onBack: () -> Unit,
    onOpen: (Int) -> Unit,
    onHome: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as AniBiarApp
    val viewModel: DetailsViewModel = viewModel(
        key = "details-$id",
        factory = DetailsViewModelFactory(app, id),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val saved by app.watchlist.ids.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var topBarPx by remember { mutableStateOf(0) }
    var artFilter by rememberSaveable { mutableStateOf("All") }
    var artExpanded by rememberSaveable { mutableStateOf(false) }
    val comingSoon = { Toast.makeText(context, "Coming soon", Toast.LENGTH_SHORT).show() }

    val scrolled by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 120 }
    }
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val media = state.media

    BoxWithConstraints(Modifier.fillMaxSize().background(Ani.Onyx)) {
        val maxContent = if (maxWidth >= 900.dp) 900.dp else 720.dp
        val side = ((maxWidth - maxContent) / 2).coerceAtLeast(16.dp)
        val content = maxWidth - side * 2
        val cardW = (content * 0.42f).coerceIn(132.dp, 230.dp)
        val epW = (content * 0.71f).coerceIn(230.dp, 380.dp)
        val posterW = (content * 0.31f).coerceIn(96.dp, 220.dp)
        val titleSp = (content.value / 328f * 22f).coerceIn(20f, 32f)

        if (media != null) {
            DetailsBackdrop(media, listState)
        }

        when {
            media != null -> LazyColumn(
                state = listState,
                contentPadding = PaddingValues(
                    bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                ),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(key = "top") {
                    val minTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 56.dp
                    Spacer(Modifier.height(maxOf(with(androidx.compose.ui.platform.LocalDensity.current) { topBarPx.toDp() }, minTop) + 14.dp))
                }
                item(key = "header") {
                    Header(
                        media = media,
                        posterWidth = posterW,
                        titleSize = titleSp,
                        inList = media.id in saved,
                        onWatch = comingSoon,
                        onToggleList = { app.watchlist.toggle(media.id) },
                        modifier = Modifier.padding(horizontal = side),
                    )
                }
                item(key = "about") {
                    About(media, state.malScore, state.imdbId, state.tvdbId, Modifier.padding(horizontal = side))
                }
                media.trailer?.takeIf { !it.id.isNullOrBlank() }?.let { trailer ->
                    item(key = "trailer") {
                        Trailer(media, trailer.id.orEmpty(), trailer.site, trailer.thumbnail, Modifier.padding(horizontal = side))
                    }
                }
                val episodeCount = media.airedEpisodes ?: media.episodes ?: media.streamingEpisodes.size
                if (episodeCount > 0) {
                    item(key = "episodes") {
                        Episodes(media, episodeCount, side, epW, onClick = comingSoon)
                    }
                }

                val relations = media.relations?.edges.orEmpty().filter { it.node?.type == "ANIME" }
                val showEdges = state.franchise?.map { it to "" }
                    ?: relations.filter { it.relationType == "PREQUEL" || it.relationType == "SEQUEL" }
                        .mapNotNull { e -> e.node?.let { it to e.relationType.orEmpty() } }
                val relatedEdges = relations.filter { it.relationType != "PREQUEL" && it.relationType != "SEQUEL" }
                if (showEdges.isNotEmpty()) {
                    item(key = "show") {
                        ShowSection(media, showEdges, side, cardW, onOpen)
                    }
                }
                if (relatedEdges.isNotEmpty()) {
                    item(key = "related") {
                        RelatedSection(relatedEdges.mapNotNull { e -> e.node?.let { it to e.relationType.orEmpty() } }, side, cardW, onOpen)
                    }
                }

                val chars = state.characters
                if (chars != null && chars.edges.isNotEmpty()) {
                    item(key = "characters") {
                        Characters(chars.pageInfo?.total ?: chars.edges.size, chars.edges, Modifier.padding(horizontal = side))
                    }
                }

                val images = state.images
                if (images != null && images.total > 0) {
                    artworkItems(
                        images = images,
                        filter = artFilter,
                        onFilter = { artFilter = it; artExpanded = false },
                        expanded = artExpanded,
                        onToggle = { artExpanded = !artExpanded },
                        side = side,
                    )
                }

                val recs = media.recommendations?.nodes.orEmpty().mapNotNull { it.mediaRecommendation }
                    .filter { it.type == "ANIME" || it.type == null }
                if (recs.isNotEmpty()) {
                    item(key = "recs") {
                        Column(Modifier.fillMaxWidth().padding(top = 36.dp)) {
                            SectionHeader("More like this", null, null, Modifier.padding(horizontal = side))
                            Spacer(Modifier.height(14.dp))
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = side),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(recs, key = { it.id }) { rec ->
                                    MediaCard(rec, badge = null, label = null, width = cardW, onClick = { onOpen(rec.id) })
                                }
                            }
                        }
                    }
                }
                item(key = "footer") { Footer(side) }
            }

            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Ani.Lavender, strokeWidth = 3.dp, modifier = Modifier.size(34.dp))
            }

            else -> ErrorState(
                message = state.error ?: "Something went wrong.",
                onRetry = viewModel::retry,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        Box(Modifier.onSizeChanged { topBarPx = it.height }) {
            AniTopBar(scrolled = scrolled, onSearch = comingSoon, onMenu = comingSoon, onLogo = onHome)
        }
    }
}

// ------------------------------------------------------------------ backdrop

@Composable
private fun DetailsBackdrop(media: Media, listState: LazyListState) {
    val context = LocalContext.current
    val url = media.bannerImage ?: media.posterUrl
    val request = remember(url) {
        ImageRequest.Builder(context).data(url).size(720, 720).crossfade(true).build()
    }
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    // Eased (smoothstep) target, quantised so we only recompose a few dozen times per fade.
    val target by remember {
        derivedStateOf {
            val px = if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset else 100_000
            val t = (1f - px / density / 1100f).coerceIn(0f, 1f)
            Math.round(t * t * (3f - 2f * t) * 60f) / 60f
        }
    }
    val fade by androidx.compose.animation.core.animateFloatAsState(
        targetValue = target,
        animationSpec = androidx.compose.animation.core.tween(280, easing = androidx.compose.animation.core.LinearOutSlowInEasing),
        label = "backdropFade",
    )
    // Fixed behind the page like Miruro: strong at the top, dimming as you scroll, gone further down.
    Box(
        Modifier
            .fillMaxWidth()
            .height(720.dp)
            .graphicsLayer { alpha = fade },
    ) {
        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            filterQuality = FilterQuality.High,
            alpha = 0.9f,
            modifier = Modifier.fillMaxSize().blur(18.dp),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Ani.Onyx.copy(alpha = 0.30f),
                    0.35f to Ani.Onyx.copy(alpha = 0.50f),
                    0.60f to Ani.Onyx.copy(alpha = 0.78f),
                    0.82f to Ani.Onyx.copy(alpha = 0.94f),
                    1f to Ani.Onyx,
                ),
            ),
        )
    }
}

// ------------------------------------------------------------------ header

@Composable
private fun Header(
    media: Media,
    posterWidth: Dp,
    titleSize: Float,
    inList: Boolean,
    onWatch: () -> Unit,
    onToggleList: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            PosterImage(
                url = media.posterUrl,
                contentDescription = media.displayTitle,
                modifier = Modifier
                    .width(posterWidth)
                    .height(posterWidth * 1.5f)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, Ani.Border, RoundedCornerShape(12.dp)),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = media.displayTitle,
                    style = inter(titleSize, FontWeight.Bold, lineHeight = titleSize * 1.22f),
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    media.seasonLabel?.let { MetaItem(AniIcons.Calendar, it) }
                    media.score10?.let { MetaItem(AniIcons.Star, it) }
                    MetaItem(AniIcons.Tv, media.formatName)
                    media.duration?.takeIf { it > 0 }?.let { MetaItem(AniIcons.Schedule, "$it min") }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton(
                label = "Watch Now",
                icon = AniIcons.Play,
                filled = true,
                onClick = onWatch,
                modifier = Modifier.weight(1f),
            )
            ActionButton(
                label = if (inList) "In List" else "Add to List",
                icon = if (inList) AniIcons.Check else AniIcons.Add,
                filled = false,
                onClick = onToggleList,
                modifier = Modifier.weight(1f),
            )
        }

        val description = media.cleanDescription
        if (description.isNotBlank()) {
            Spacer(Modifier.height(16.dp))
            Description(description)
        }
    }
}

@Composable
private fun MetaItem(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = Ani.Muted, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(5.dp))
        Text(text, style = inter(13f, FontWeight.Medium), color = Ani.Soft, maxLines = 1)
    }
}

@Composable
private fun ActionButton(
    label: String,
    icon: ImageVector,
    filled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(10.dp)
    val content = if (filled) Color.Black else Color.White
    Row(
        modifier = modifier
            .height(40.dp)
            .clip(shape)
            .background(if (filled) Color.White else Color.White.copy(alpha = 0.10f))
            .then(if (filled) Modifier else Modifier.border(1.dp, Color.White.copy(alpha = 0.20f), shape))
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = inter(14f, FontWeight.SemiBold), color = content, maxLines = 1)
    }
}

@Composable
private fun Description(text: String) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var overflow by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = text,
            style = inter(14f, lineHeight = 23f),
            color = Ani.Soft,
            maxLines = if (expanded) Int.MAX_VALUE else 4,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { if (!expanded) overflow = it.hasVisualOverflow },
        )
        if (overflow || expanded) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (expanded) "Show less" else "Read more",
                style = inter(14f, FontWeight.SemiBold),
                color = Color.White,
                modifier = Modifier.clickable { expanded = !expanded },
            )
        }
    }
}

// ------------------------------------------------------------------ about

@Composable
private fun SectionHeader(title: String, subtitle: String?, right: String?, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = inter(18f, FontWeight.SemiBold))
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = inter(13f), color = Ani.Muted)
            }
        }
        if (right != null) Text(right, style = inter(13.5f), color = Ani.Muted)
    }
}

@Composable
private fun About(
    media: Media,
    malScore: String?,
    imdbId: String?,
    tvdbId: String?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val number = remember { NumberFormat.getIntegerInstance(Locale.US) }
    var showAllStudios by rememberSaveable { mutableStateOf(false) }

    Column(modifier.fillMaxWidth().padding(top = 32.dp)) {
        Text("About this anime", style = inter(18f, FontWeight.SemiBold))
        Spacer(Modifier.height(12.dp))

        val statusColor = when (media.status) {
            "RELEASING" -> StatusGreen
            "FINISHED" -> StatusBlue
            "NOT_YET_RELEASED" -> Ani.Amber
            else -> Color.White
        }
        AboutRow("Status", media.statusLabel, statusColor)

        val aired = media.airedEpisodes
        val total = media.episodes
        val episodesText = when {
            media.status == "RELEASING" && aired != null -> "$aired / ${total ?: "?"} Sub"
            total != null -> "$total Sub"
            aired != null -> "$aired Sub"
            else -> null
        }
        episodesText?.let { AboutRow("Episodes", it) }
        media.averageScore?.let { AboutRow("AniList score", media.score10.orEmpty()) }
        malScore?.let { AboutRow("MAL score", it) }
        media.airedLabel?.let { AboutRow("Aired", it) }
        media.popularity?.let { AboutRow("Popularity", "#" + number.format(it)) }
        media.title.native?.takeIf { it.isNotBlank() }?.let { AboutRow("Native", it) }

        val genres = media.genres.orEmpty()
        if (genres.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Text("Genres", style = inter(13f), color = Ani.Muted)
            Spacer(Modifier.height(10.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                genres.forEach { genre ->
                    Text(
                        text = genre,
                        style = inter(11.5f, FontWeight.Medium),
                        color = Color.White,
                        maxLines = 1,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Ani.Chip)
                            .padding(horizontal = 9.dp, vertical = 5.dp),
                    )
                }
            }
        }

        val studios = media.studioNames.distinctBy { it.first.lowercase() }
        if (studios.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            Text("Studios", style = inter(13f), color = Ani.Muted)
            Spacer(Modifier.height(10.dp))
            val visible = if (showAllStudios) studios else studios.take(3)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                visible.forEachIndexed { index, (name, isMain) ->
                    val shape = RoundedCornerShape(10.dp)
                    Text(
                        text = name,
                        style = inter(11.5f, FontWeight.Medium),
                        color = if (isMain && index == 0) Color.White else Ani.Soft,
                        modifier = Modifier
                            .clip(shape)
                            .then(
                                if (isMain && index == 0) Modifier.background(Ani.Chip)
                                else Modifier.border(1.dp, Ani.Border, shape),
                            )
                            .padding(horizontal = 9.dp, vertical = 5.dp),
                    )
                }
                if (studios.size > 3) {
                    Text(
                        text = if (showAllStudios) "Show less" else "+${studios.size - 3} more",
                        style = inter(11.5f, FontWeight.Medium),
                        color = Ani.Soft,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, Ani.Border, RoundedCornerShape(10.dp))
                            .clickable { showAllStudios = !showAllStudios }
                            .padding(horizontal = 9.dp, vertical = 5.dp),
                    )
                }
            }
        }

        // Link tiles with each site's real logo (AniList supplies icons for its external links).
        val links = buildList {
            media.siteUrl?.let { add(LinkTile("AniList", "https://www.google.com/s2/favicons?domain=anilist.co&sz=128", null, it)) }
            media.idMal?.let { add(LinkTile("MyAnimeList", "https://www.google.com/s2/favicons?domain=myanimelist.net&sz=128", null, "https://myanimelist.net/anime/$it")) }
            media.externalLinks
                .filter { (it.type == "INFO" || it.type == "SOCIAL") && !it.url.isNullOrBlank() && !it.site.isNullOrBlank() }
                .forEach { link -> add(LinkTile(link.site.orEmpty(), link.icon, link.color, link.url.orEmpty())) }
            imdbId?.let { add(LinkTile("IMDb", "https://www.google.com/s2/favicons?domain=imdb.com&sz=128", null, "https://www.imdb.com/title/$it")) }
            tvdbId?.let { add(LinkTile("TheTVDB", "https://www.google.com/s2/favicons?domain=thetvdb.com&sz=128", null, "https://thetvdb.com/dereferrer/series/$it")) }
        }.distinctBy { it.name.lowercase() }
        if (links.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                links.forEach { tile -> LinkTileView(tile) { openUrl(context, tile.url) } }
            }
        }
    }
}

private data class LinkTile(val name: String, val icon: String?, val color: String?, val url: String)

@Composable
private fun LinkTileView(tile: LinkTile, onClick: () -> Unit) {
    val shape = RoundedCornerShape(9.dp)
    val brand = remember(tile.color) {
        tile.color?.let { c -> runCatching { Color(android.graphics.Color.parseColor(c)) }.getOrNull() }
    }
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(shape)
            .background(brand ?: Ani.Item)
            .then(if (brand == null) Modifier.border(1.dp, Ani.Border, shape) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (tile.icon != null) {
            val context = LocalContext.current
            val request = remember(tile.icon) {
                ImageRequest.Builder(context).data(tile.icon).size(Size.ORIGINAL).crossfade(true).build()
            }
            AsyncImage(
                model = request,
                contentDescription = tile.name,
                filterQuality = FilterQuality.High,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(if (brand == null) 24.dp else 20.dp).clip(RoundedCornerShape(5.dp)),
            )
        } else {
            Icon(AniIcons.ExternalLink, contentDescription = tile.name, tint = Ani.Soft, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun AboutRow(label: String, value: String, valueColor: Color = Color.White) {
    Row(Modifier.fillMaxWidth().height(32.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = inter(13.5f), color = Ani.Muted)
        Spacer(Modifier.width(16.dp))
        Text(
            text = value,
            style = inter(13f, FontWeight.Medium),
            color = valueColor,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

private fun initials(site: String): String {
    val words = site.split(" ", ".", "-").filter { it.isNotBlank() }
    return when {
        words.size >= 2 -> (words[0].take(1) + words[1].take(1)).uppercase()
        else -> site.take(2).replaceFirstChar { it.uppercase() }
    }
}

private fun colorFor(site: String): Color {
    val palette = listOf(
        Color(0xFF7C3AED), Color(0xFFDB2777), Color(0xFF0891B2), Color(0xFFEA580C),
        Color(0xFF16A34A), Color(0xFF4F46E5), Color(0xFFB91C1C), Color(0xFF0F766E),
    )
    return palette[(site.lowercase().hashCode() and 0x7fffffff) % palette.size]
}

private fun openUrl(context: Context, url: String?) {
    if (url.isNullOrBlank()) return
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        Toast.makeText(context, "Couldn't open the link", Toast.LENGTH_SHORT).show()
    }
}

// ------------------------------------------------------------------ trailer

private tailrec fun Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Trailer card that plays inside the page. The embed is loaded as the WebView's top-level page (a nested
 * iframe rendered black on some phones), with the player's own controls and a real fullscreen mode.
 */
@Composable
@android.annotation.SuppressLint("SetJavaScriptEnabled")
private fun Trailer(media: Media, videoId: String, site: String?, thumbnail: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val kind = site?.lowercase()
    if (kind != "youtube" && kind != "dailymotion") return
    val shape = RoundedCornerShape(14.dp)
    var playing by remember(videoId) { mutableStateOf(false) }
    var fullscreenView by remember { mutableStateOf<android.view.View?>(null) }
    var fullscreenCallback by remember { mutableStateOf<android.webkit.WebChromeClient.CustomViewCallback?>(null) }
    val activity = remember(context) { context.findActivity() }

    val embedUrl = if (kind == "youtube") {
        "https://www.youtube.com/embed/$videoId?autoplay=1&playsinline=1&rel=0&modestbranding=1&fs=1&origin=https://anibiartv.vercel.app"
    } else {
        "https://www.dailymotion.com/embed/video/$videoId?autoplay=1"
    }

    fun systemBars(hidden: Boolean) {
        val window = activity?.window ?: return
        val controller = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
        if (hidden) {
            controller.systemBarsBehavior =
                androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
    }

    fun exitFullscreen() {
        fullscreenView?.let { view -> (view.parent as? android.view.ViewGroup)?.removeView(view) }
        fullscreenCallback?.onCustomViewHidden()
        val wasFullscreen = fullscreenView != null
        fullscreenView = null
        fullscreenCallback = null
        if (wasFullscreen) {
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            systemBars(false)
        }
    }

    BackHandler(enabled = fullscreenView != null) { exitFullscreen() }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { exitFullscreen() } }

    val chrome = remember {
        object : android.webkit.WebChromeClient() {
            override fun onShowCustomView(view: android.view.View, callback: android.webkit.WebChromeClient.CustomViewCallback) {
                if (fullscreenView != null) {
                    callback.onCustomViewHidden()
                    return
                }
                view.setBackgroundColor(android.graphics.Color.BLACK)
                (activity?.window?.decorView as? android.widget.FrameLayout)?.addView(
                    view,
                    android.widget.FrameLayout.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    ),
                )
                fullscreenView = view
                fullscreenCallback = callback
                activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                systemBars(true)
            }

            override fun onHideCustomView() = exitFullscreen()
        }
    }

    Column(modifier.fillMaxWidth().padding(top = 32.dp)) {
        Text("Trailer", style = inter(18f, FontWeight.SemiBold))
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .then(if (playing) Modifier else Modifier.clip(shape))
                .border(1.dp, Ani.Border, shape)
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            if (playing) {
                androidx.compose.ui.viewinterop.AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        android.webkit.WebView(ctx).apply {
                            setBackgroundColor(android.graphics.Color.BLACK)
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.mediaPlaybackRequiresUserGesture = false
                            webChromeClient = chrome
                            webViewClient = android.webkit.WebViewClient()
                            loadUrl(embedUrl, mapOf("Referer" to "https://anibiartv.vercel.app"))
                        }
                    },
                    onRelease = { it.destroy() },
                )
            } else {
                val full = if (kind == "youtube") "https://img.youtube.com/vi/$videoId/maxresdefault.jpg" else thumbnail
                var art by remember(videoId) { mutableStateOf(full ?: thumbnail) }
                val request = remember(art) {
                    ImageRequest.Builder(context).data(art).size(Size.ORIGINAL).crossfade(true).build()
                }
                AsyncImage(
                    model = request,
                    contentDescription = "${media.displayTitle} trailer",
                    contentScale = ContentScale.Crop,
                    filterQuality = FilterQuality.High,
                    onError = { if (art != thumbnail && thumbnail != null) art = thumbnail },
                    modifier = Modifier.fillMaxSize().clickable { playing = true },
                )
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.20f)))
                Box(
                    Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f))
                        .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                        .clickable { playing = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(AniIcons.Play, contentDescription = "Play trailer", tint = Color.White, modifier = Modifier.size(28.dp))
                }
            }
        }
    }
}

// ------------------------------------------------------------------ episodes

@Composable
private fun Episodes(media: Media, count: Int, side: Dp, cardWidth: Dp, onClick: () -> Unit) {
    // AniList lists streaming titles as "Episode 5 - Name"; use them when present.
    val streaming = remember(media.id) {
        val regex = Regex("""(?i)episode\s+(\d+)\s*[-:–]?\s*(.*)""")
        media.streamingEpisodes.mapNotNull { ep ->
            val match = regex.find(ep.title.orEmpty()) ?: return@mapNotNull null
            val number = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
            number to (match.groupValues[2].trim() to ep.thumbnail)
        }.toMap()
    }
    val fallbackArt = media.bannerImage ?: media.posterUrl
    val numbers = remember(count) { (1..count).toList() }

    Column(Modifier.fillMaxWidth().padding(top = 36.dp)) {
        SectionHeader("Episodes", null, "$count episodes", Modifier.padding(horizontal = side))
        Spacer(Modifier.height(14.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = side),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(numbers, key = { it }) { number ->
                val info = streaming[number]
                val shape = RoundedCornerShape(12.dp)
                Column(Modifier.width(cardWidth).clickable(onClick = onClick)) {
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(shape)
                            .border(1.dp, Ani.Border, shape).background(Ani.Item),
                    ) {
                        PosterImage(
                            url = info?.second ?: fallbackArt,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                        )
                        Box(
                            Modifier.fillMaxSize().background(
                                Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.55f)),
                            ),
                        )
                        InfoBadge(
                            text = "EP $number",
                            background = Color.Black.copy(alpha = 0.6f),
                            textSize = 11f,
                            modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = info?.first?.takeIf { it.isNotBlank() } ?: "Episode $number",
                        style = inter(13.5f, FontWeight.Medium, lineHeight = 18f),
                        color = Ani.Soft,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------ show / related

private fun Media.sortKey(): Int {
    val y = startDate?.year ?: return Int.MAX_VALUE
    return y * 10_000 + (startDate?.month ?: 0) * 100 + (startDate?.day ?: 0)
}

@Composable
private fun ShowSection(
    current: Media,
    others: List<Pair<Media, String>>,
    side: Dp,
    cardWidth: Dp,
    onOpen: (Int) -> Unit,
) {
    var newestFirst by rememberSaveable { mutableStateOf(false) }
    val entries = remember(current.id, others, newestFirst) {
        val sorted = (others.map { it.first } + current).distinctBy { it.id }.sortedBy { it.sortKey() }
        if (newestFirst) sorted.reversed() else sorted
    }
    val currentIndex = entries.indexOfFirst { it.id == current.id }
    // Number the TV entries in release order: Season 1, Season 2 ...
    val seasonNumbers = remember(entries) {
        var n = 0
        entries.sortedBy { it.sortKey() }.associate { m -> m.id to if (m.format == "TV") ++n else null }
    }

    Column(Modifier.fillMaxWidth().padding(top = 36.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = side), verticalAlignment = Alignment.CenterVertically) {
            SectionHeader("Show", "${entries.size} entries", null, Modifier.weight(1f))
            Spacer(Modifier.width(10.dp))
            OutlinedDropdown(
                current = if (newestFirst) "Newest first" else "Release order",
                options = listOf("Release order", "Newest first"),
            ) { newestFirst = it == "Newest first" }
        }
        Spacer(Modifier.height(14.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = side),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(entries, key = { it.id }) { media ->
                val index = entries.indexOfFirst { it.id == media.id }
                val isCurrent = media.id == current.id
                val before = if (newestFirst) index > currentIndex else index < currentIndex
                val badge = when {
                    isCurrent -> "Current"
                    before -> "Prequel"
                    else -> "Sequel"
                }
                MediaCard(
                    media = media,
                    badge = badge,
                    badgeIcon = if (isCurrent) AniIcons.Check else null,
                    label = seasonNumbers[media.id]?.let { "Season $it" } ?: media.formatName,
                    labelColor = Ani.Amber,
                    width = cardWidth,
                    onClick = { if (!isCurrent) onOpen(media.id) },
                )
            }
        }
    }
}

@Composable
private fun RelatedSection(items: List<Pair<Media, String>>, side: Dp, cardWidth: Dp, onOpen: (Int) -> Unit) {
    val entries = remember(items) { items.distinctBy { it.first.id } }
    Column(Modifier.fillMaxWidth().padding(top = 36.dp)) {
        SectionHeader(
            "Related",
            "Side stories and franchise connections",
            "${entries.size} entries",
            Modifier.padding(horizontal = side),
        )
        Spacer(Modifier.height(14.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = side),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(entries, key = { it.first.id }) { (media, relation) ->
                val label = relationLabel(relation)
                MediaCard(
                    media = media,
                    badge = label,
                    label = label,
                    labelColor = if (label == "Alternative") Color(0xFFFB923C) else Color(0xFFFDBA74),
                    width = cardWidth,
                    onClick = { onOpen(media.id) },
                )
            }
        }
    }
}

private fun relationLabel(type: String): String = when (type) {
    "ALTERNATIVE" -> "Alternative"
    "SIDE_STORY" -> "Side Story"
    "SPIN_OFF" -> "Spin-off"
    "PARENT" -> "Parent"
    "SUMMARY" -> "Summary"
    "ADAPTATION" -> "Adaptation"
    "CHARACTER" -> "Character"
    "COMPILATION" -> "Compilation"
    "CONTAINS" -> "Contains"
    else -> "Related"
}

@Composable
private fun MediaCard(
    media: Media,
    badge: String?,
    label: String?,
    onClick: () -> Unit,
    width: Dp = 151.dp,
    badgeIcon: ImageVector? = null,
    labelColor: Color = Ani.Amber,
) {
    val shape = RoundedCornerShape(14.dp)
    Column(Modifier.width(width).clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(shape).background(Ani.Item)) {
            PosterImage(
                url = media.posterUrl,
                contentDescription = media.displayTitle,
                modifier = Modifier.fillMaxSize(),
            )
            if (badge != null) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.62f))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (badgeIcon != null) {
                        Icon(badgeIcon, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(badge, style = inter(11.5f, FontWeight.SemiBold), color = Color.White, maxLines = 1)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        // CC count + format + year always sit on ONE row: the text size shrinks with the card width and label length.
        val eps = (media.airedEpisodes ?: media.episodes)?.takeIf { it > 0 }
        val formatText = media.formatLabel
        val yearText = media.year?.toString()
        val chipCount = listOfNotNull(eps, formatText, yearText).size
        val chars = (eps?.toString()?.length ?: 0) + formatText.length + (yearText?.length ?: 0)
        val available = width.value - chipCount * 12f - (chipCount - 1) * 3f - (if (eps != null) 15f else 0f)
        val badgeText = (available / (chars.coerceAtLeast(1) * 0.70f)).coerceIn(8.5f, 11.5f)
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            eps?.let {
                InfoBadge(it.toString(), icon = AniIcons.ClosedCaption, textSize = badgeText, iconSize = (badgeText + 1f).dp, hPad = 6.dp, vPad = 4.dp)
            }
            InfoBadge(formatText, textSize = badgeText, hPad = 6.dp, vPad = 4.dp)
            yearText?.let { InfoBadge(it, textSize = badgeText, hPad = 6.dp, vPad = 4.dp) }
        }
        Spacer(Modifier.height(6.dp))
        if (label != null) {
            Text(label, style = inter(13.5f, FontWeight.SemiBold), color = labelColor, maxLines = 1)
            Spacer(Modifier.height(2.dp))
        }
        Text(
            text = media.displayTitle,
            style = inter(13.5f, FontWeight.SemiBold, lineHeight = 18f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ------------------------------------------------------------------ characters

@Composable
private fun Characters(total: Int, edges: List<CharacterEdge>, modifier: Modifier = Modifier) {
    val languages = remember(edges) {
        edges.flatMap { it.voiceActors }.mapNotNull { it.languageV2 }.distinct()
            .sortedByDescending { it == "Japanese" }
    }
    var language by rememberSaveable { mutableStateOf("Japanese") }
    val selected = if (language in languages) language else languages.firstOrNull() ?: "Japanese"
    var showAll by rememberSaveable { mutableStateOf(false) }
    val visible = if (showAll) edges else edges.take(6)

    Column(modifier.fillMaxWidth().padding(top = 36.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Characters", style = inter(18f, FontWeight.SemiBold), modifier = Modifier.weight(1f))
            Text("$total", style = inter(13.5f), color = Ani.Muted)
            Spacer(Modifier.width(10.dp))
            if (languages.size > 1) {
                OutlinedDropdown(selected, languages) { language = it }
            }
        }
        Spacer(Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            visible.forEach { edge -> CharacterRow(edge, selected) }
        }
        if (edges.size > 6) {
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Ani.Item)
                    .clickable { showAll = !showAll },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (showAll) "Show less" else "Show all $total",
                    style = inter(13.5f, FontWeight.Medium),
                    color = Ani.Soft,
                )
            }
        }
    }
}

@Composable
private fun CharacterRow(edge: CharacterEdge, language: String) {
    val shape = RoundedCornerShape(12.dp)
    val actor = edge.voiceActors.firstOrNull { it.languageV2 == language }
    Row(
        Modifier
            .fillMaxWidth()
            .height(73.dp)
            .clip(shape)
            .background(Ani.Panel)
            .border(1.dp, Ani.Border, shape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PosterImage(
            url = edge.node?.image?.large,
            contentDescription = edge.node?.displayName,
            modifier = Modifier.width(48.dp).fillMaxHeight(),
        )
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                edge.node?.displayName.orEmpty(),
                style = inter(14f, FontWeight.Medium, lineHeight = 18f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                if (edge.role == "MAIN") "Main" else "Supporting",
                style = inter(12f),
                color = Ani.Muted,
            )
        }
        if (actor != null) {
            Column(Modifier.weight(1f).padding(end = 12.dp), horizontalAlignment = Alignment.End) {
                Text(
                    actor.displayName,
                    style = inter(13.5f, FontWeight.Medium, lineHeight = 17f),
                    textAlign = TextAlign.End,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(actor.languageV2.orEmpty(), style = inter(12f), color = Ani.Muted)
            }
            PosterImage(
                url = actor.image?.large,
                contentDescription = actor.displayName,
                modifier = Modifier.width(48.dp).fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun OutlinedDropdown(current: String, options: List<String>, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Box {
        Row(
            Modifier
                .clip(shape)
                .border(1.dp, Ani.Border, shape)
                .clickable { open = true }
                .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(current, style = inter(13f, FontWeight.Medium))
            Spacer(Modifier.width(2.dp))
            Icon(AniIcons.ArrowDown, contentDescription = null, tint = Ani.Muted, modifier = Modifier.size(20.dp))
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier.background(Ani.Item),
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option, style = inter(13.5f)) },
                    onClick = {
                        open = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}

// ------------------------------------------------------------------ artwork

private const val TMDB_IMG = "https://image.tmdb.org/t/p/"

private fun LazyListScope.artworkItems(
    images: TmdbImages,
    filter: String,
    onFilter: (String) -> Unit,
    expanded: Boolean,
    onToggle: () -> Unit,
    side: Dp,
) {
    val list = when (filter) {
        "Backdrops" -> images.backdrops
        "Posters" -> images.posters
        "Logos" -> images.logos
        else -> images.backdrops + images.posters + images.logos
    }
    val logos = images.logos.toSet()
    val pad = Modifier.padding(horizontal = side)

    item(key = "art-header") {
        Row(pad.fillMaxWidth().padding(top = 36.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(Ani.Chip),
                contentAlignment = Alignment.Center,
            ) {
                Icon(AniIcons.Photo, contentDescription = null, tint = Ani.Soft, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Artwork & Posters", style = inter(18f, FontWeight.SemiBold))
                Text("${images.total} images", style = inter(13f), color = Ani.Muted)
            }
            OutlinedDropdown(filter, listOf("All", "Backdrops", "Posters", "Logos"), onFilter)
        }
        Spacer(Modifier.height(14.dp))
    }

    val first = list.firstOrNull()
    if (first == null) {
        item(key = "art-empty") {
            Text("No images in this category.", style = inter(14f), color = Ani.Muted, modifier = pad)
        }
        return
    }

    item(key = "art-first-$filter") {
        ArtImage(first, isLogo = first in logos, original = true, fade = !expanded, modifier = pad.fillMaxWidth())
    }

    if (expanded) {
        val rows = buildArtRows(list.drop(1))
        items(rows.size, key = { "art-row-$filter-$it" }) { i ->
            val row = rows[i]
            Row(
                pad.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                row.forEach { image ->
                    ArtImage(image, isLogo = image in logos, original = false, fade = false, modifier = Modifier.weight(1f))
                }
                if (row.size == 1 && row[0].aspect < 1f) Spacer(Modifier.weight(1f))
            }
        }
    }

    if (list.size > 1) {
        item(key = "art-toggle") {
            Spacer(Modifier.height(10.dp))
            Row(
                pad
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onToggle),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (expanded) "Show fewer images" else "View all ${list.size} images",
                    style = inter(14f, FontWeight.Medium),
                    color = Ani.Soft,
                )
                Icon(
                    AniIcons.ArrowDown,
                    contentDescription = null,
                    tint = Ani.Muted,
                    modifier = Modifier.size(22.dp).graphicsLayer { rotationZ = if (expanded) 180f else 0f },
                )
            }
        }
    }
}

/** Landscape art takes a full row; portrait posters pair up two to a row. */
private fun buildArtRows(list: List<com.anibiar.app.data.TmdbImage>): List<List<com.anibiar.app.data.TmdbImage>> {
    val rows = ArrayList<List<com.anibiar.app.data.TmdbImage>>()
    var pending: com.anibiar.app.data.TmdbImage? = null
    for (image in list) {
        if (image.aspect >= 1f) {
            pending?.let { rows += listOf(it) }
            pending = null
            rows += listOf(image)
        } else {
            val p = pending
            if (p == null) pending = image else {
                rows += listOf(p, image)
                pending = null
            }
        }
    }
    pending?.let { rows += listOf(it) }
    return rows
}

@Composable
private fun ArtImage(
    image: com.anibiar.app.data.TmdbImage,
    isLogo: Boolean,
    original: Boolean,
    fade: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(14.dp)
    val size = when {
        original -> "original"
        isLogo -> "w780"
        image.aspect >= 1f -> "w1280"
        else -> "w780"
    }
    val request = remember(image.path, size) {
        ImageRequest.Builder(context).data(TMDB_IMG + size + image.path).size(Size.ORIGINAL).crossfade(true).build()
    }
    Box(
        modifier
            .aspectRatio(image.aspect.coerceIn(0.5f, 2.4f))
            .clip(shape)
            .border(1.dp, Ani.Border, shape)
            .background(Ani.Item),
    ) {
        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = if (isLogo) ContentScale.Fit else ContentScale.Crop,
            filterQuality = FilterQuality.High,
            modifier = Modifier.fillMaxSize(),
        )
        if (fade) {
            Box(
                Modifier.fillMaxWidth().height(80.dp).align(Alignment.BottomCenter).background(
                    Brush.verticalGradient(0f to Color.Transparent, 1f to Ani.Onyx.copy(alpha = 0.6f)),
                ),
            )
        }
    }
}

// ------------------------------------------------------------------ footer

@Composable
private fun Footer(side: Dp) {
    Column(
        Modifier.fillMaxWidth().padding(top = 44.dp, bottom = 28.dp).padding(horizontal = side),
    ) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Ani.Border))
        Spacer(Modifier.height(18.dp))
        Text("AniBiar TV", style = inter(15f, FontWeight.SemiBold))
        Spacer(Modifier.height(4.dp))
        Text("Data: AniList · TMDB · MyAnimeList", style = inter(12.5f), color = Ani.Muted)
    }
}
