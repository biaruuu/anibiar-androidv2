@file:OptIn(ExperimentalLayoutApi::class)

package com.anibiar.app.ui.home

import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.coerceAtMost
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import com.anibiar.app.BuildConfig
import com.anibiar.app.R
import com.anibiar.app.data.HistoryItem
import com.anibiar.app.data.LatestEpisode
import com.anibiar.app.data.Media
import com.anibiar.app.data.TmdbArt
import com.anibiar.app.data.airedEpisodes
import com.anibiar.app.data.backdropUrl
import com.anibiar.app.data.cleanDescription
import com.anibiar.app.data.displayTitle
import com.anibiar.app.data.formatLabel
import com.anibiar.app.data.posterUrl
import com.anibiar.app.data.timeAgo
import com.anibiar.app.data.year
import com.anibiar.app.ui.theme.Ani
import com.anibiar.app.ui.theme.AniIcons
import com.anibiar.app.ui.theme.inter
import kotlinx.coroutines.delay

private const val EPISODES_PER_PAGE = 12
private const val MAX_EPISODE_PAGES = 5

@Composable
fun HomeScreen(onOpen: (Int) -> Unit, viewModel: HomeViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val app = LocalContext.current.applicationContext as com.anibiar.app.AniBiarApp
    val saved by app.watchlist.ids.collectAsStateWithLifecycle()
    HomeContent(
        state = state,
        saved = saved,
        onToggleSaved = app.watchlist::toggle,
        onOpen = onOpen,
        onRetry = viewModel::refresh,
        onRemoveHistory = viewModel::removeFromHistory,
    )
}

@Composable
private fun HomeContent(
    state: HomeUiState,
    saved: Set<Int>,
    onToggleSaved: (Int) -> Unit,
    onOpen: (Int) -> Unit,
    onRetry: () -> Unit,
    onRemoveHistory: (HistoryItem) -> Unit,
) {
    val context = LocalContext.current
    val comingSoon = { Toast.makeText(context, "Coming soon", Toast.LENGTH_SHORT).show() }

    val listState = rememberLazyListState()
    val scrolled by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 200 }
    }
    var latestPage by rememberSaveable { mutableStateOf(0) }
    var dubMode by rememberSaveable { mutableStateOf(false) }
    var heroIndex by remember { mutableIntStateOf(0) }

    val currentHero = state.hero.getOrNull(heroIndex)
    val backdrop = currentHero?.let { hero ->
        val art = state.art[hero.id]
        when {
            art?.backdrop != null -> art.backdrop
            art == null && BuildConfig.TMDB_API_KEY.isNotBlank() -> null // wait for the sharp TMDB art
            else -> hero.bannerImage ?: hero.posterUrl
        }
    }

    // Total distance scrolled, in px. Read only while drawing, so scrolling never recomposes the backdrop.
    var scrollPx by rememberSaveable { mutableStateOf(0f) }
    val scrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                scrollPx = (scrollPx - consumed.y).coerceAtLeast(0f)
                return Offset.Zero
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Ani.Onyx).nestedScroll(scrollConnection)) {
        // Everything below adapts to the real screen size.
        // Miruro's hero is ~1.48x as tall as it is wide; that proportion gives the same crop of the art.
        val heroHeight = (maxWidth * 1.48f).coerceAtMost(maxHeight * 0.88f).coerceAtLeast(300.dp)
        val columns = when {
            maxWidth >= 900.dp -> 6
            maxWidth >= 600.dp -> 4
            else -> 3
        }

        HeroBackdrop(url = backdrop, heroHeight = heroHeight, scrollPx = { scrollPx }, modifier = Modifier.fillMaxSize())

        when {
            state.loading && state.hero.isEmpty() && state.latest.isEmpty() -> {
                CircularProgressIndicator(
                    color = Ani.Lavender,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            state.error != null -> ErrorState(
                message = state.error,
                onRetry = onRetry,
                modifier = Modifier.align(Alignment.Center),
            )

            else -> {
                val pages = remember(state.latest) {
                    state.latest.chunked(EPISODES_PER_PAGE).take(MAX_EPISODE_PAGES)
                }
                val pageCount = pages.size.coerceAtLeast(1)
                val page = latestPage.coerceIn(0, pageCount - 1)
                val rows = pages.getOrNull(page).orEmpty().chunked(columns)

                LazyColumn(
                    state = listState,
                    contentPadding = WindowInsets.navigationBars.asPaddingValues(),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (state.hero.isNotEmpty()) {
                        item(key = "hero") {
                            HeroPager(
                                items = state.hero,
                                art = state.art,
                                height = heroHeight,
                                onPageChanged = { heroIndex = it },
                                saved = saved,
                                onWatch = onOpen,
                                onDetails = onOpen,
                                onAdd = onToggleSaved,
                            )
                        }
                    }

                    if (state.history.isNotEmpty()) {
                        item(key = "continue") {
                            ContinueWatching(
                                entries = state.history,
                                onClick = { onOpen(it.mediaId) },
                                onRemove = onRemoveHistory,
                            )
                        }
                    }

                    item(key = "latest-header") {
                        Spacer(Modifier.height(28.dp))
                        LatestHeader(
                            dubMode = dubMode,
                            onModeChange = { dubMode = it },
                            page = page,
                            pageCount = pageCount,
                            onPrev = { latestPage = (page - 1).coerceAtLeast(0) },
                            onNext = { latestPage = (page + 1).coerceAtMost(pageCount - 1) },
                        )
                        Spacer(Modifier.height(14.dp))
                    }

                    if (dubMode) {
                        item(key = "dub-empty") {
                            Text(
                                text = "Dubbed releases aren't available yet.",
                                style = inter(15f),
                                color = Ani.Muted,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 32.dp),
                            )
                        }
                    } else {
                        items(rows.size, key = { "row-$page-$it" }) { index ->
                            EpisodeRow(rows[index], columns, onClick = { onOpen(it.media.id) })
                            Spacer(Modifier.height(16.dp))
                        }
                    }

                    if (state.topAiring.isNotEmpty()) {
                        item(key = "top-airing") {
                            Spacer(Modifier.height(12.dp))
                            ListPanel(
                                title = "TOP AIRING",
                                items = state.topAiring,
                                dot = Ani.Lime,
                                onClick = { onOpen(it.id) },
                            )
                        }
                    }

                    if (state.upcoming.isNotEmpty()) {
                        item(key = "upcoming") {
                            Spacer(Modifier.height(16.dp))
                            ListPanel(
                                title = "UPCOMING",
                                items = state.upcoming,
                                dot = Ani.Amber,
                                onClick = { onOpen(it.id) },
                            )
                            Spacer(Modifier.height(32.dp))
                        }
                    }
                }
            }
        }

        AniTopBar(scrolled = scrolled, onSearch = comingSoon, onMenu = comingSoon)
    }
}

// ---------------------------------------------------------------- top bar

@Composable
internal fun AniTopBar(
    scrolled: Boolean,
    onSearch: () -> Unit,
    onMenu: () -> Unit,
    onLogo: (() -> Unit)? = null,
) {
    val background by animateColorAsState(
        targetValue = if (scrolled) Ani.Onyx.copy(alpha = 0.92f) else Color.Transparent,
        label = "topBarBackground",
    )
    Column(Modifier.fillMaxWidth().background(background)) {
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .height(56.dp)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.anibiar_wordmark),
                contentDescription = "AniBiar TV",
                contentScale = ContentScale.Fit,
                alignment = Alignment.CenterStart,
                modifier = Modifier.height(30.dp).width(78.dp).then(if (onLogo != null) Modifier.clickable(onClick = onLogo) else Modifier),
            )
            Spacer(Modifier.weight(1f))
            Icon(
                imageVector = AniIcons.Search,
                contentDescription = "Search",
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(28.dp).clickable(onClick = onSearch),
            )
            Spacer(Modifier.width(22.dp))
            Icon(
                imageVector = AniIcons.Menu,
                contentDescription = "Menu",
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(28.dp).clickable(onClick = onMenu),
            )
        }
        if (scrolled) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.10f)))
        }
    }
}

// ---------------------------------------------------------------- hero

@Composable
private fun HeroPager(
    items: List<Media>,
    art: Map<Int, TmdbArt>,
    height: Dp,
    onPageChanged: (Int) -> Unit,
    saved: Set<Int>,
    onWatch: (Int) -> Unit,
    onDetails: (Int) -> Unit,
    onAdd: (Int) -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { items.size })

    LaunchedEffect(pagerState.currentPage) { onPageChanged(pagerState.currentPage) }
    LaunchedEffect(items.size) {
        while (items.size > 1) {
            delay(8_000)
            if (!pagerState.isScrollInProgress) {
                pagerState.animateScrollToPage((pagerState.currentPage + 1) % items.size)
            }
        }
    }

    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxWidth().height(height),
    ) { index ->
        val media = items[index]
        HeroSlide(
            media = media,
            art = art[media.id],
            inList = media.id in saved,
            onWatch = { onWatch(media.id) },
            onDetails = { onDetails(media.id) },
            onAdd = { onAdd(media.id) },
        )
    }
}

/**
 * The hero art lives behind the whole page (like Miruro): sharp at the top, then it dims as the page
 * scrolls, stays faintly visible just below the hero, and fades out completely further down.
 */
@Composable
private fun HeroBackdrop(
    url: String?,
    heroHeight: Dp,
    scrollPx: () -> Float,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        // The art is exactly hero-sized and stays fixed while the page scrolls over it.
        Box(Modifier.fillMaxWidth().height(heroHeight)) {
            Crossfade(
                targetState = url,
                modifier = Modifier.fillMaxSize(),
                animationSpec = tween(600),
                label = "heroBackdrop",
            ) { target ->
                if (target != null) {
                    AsyncImage(
                        model = target,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.Center,
                        filterQuality = FilterQuality.High,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            // Darker under the top bar, fading into onyx behind the buttons.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Ani.Onyx.copy(alpha = 0.60f),
                            0.16f to Color.Transparent,
                            0.55f to Ani.Onyx.copy(alpha = 0.30f),
                            0.80f to Ani.Onyx.copy(alpha = 0.62f),
                            0.93f to Ani.Onyx.copy(alpha = 0.92f),
                            1f to Ani.Onyx,
                        ),
                    ),
            )
        }
        // Scroll scrim, tuned to Miruro: dim but visible just below the hero, almost gone after ~1.2 hero heights.
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    val s = scrollPx() / heroHeight.toPx()
                    val alpha = when {
                        s <= 0.35f -> 0.08f + 0.37f * (s / 0.35f)
                        s <= 0.71f -> 0.45f + 0.25f * ((s - 0.35f) / 0.36f)
                        s <= 1.16f -> 0.70f + 0.20f * ((s - 0.71f) / 0.45f)
                        s <= 1.6f -> 0.90f + 0.09f * ((s - 1.16f) / 0.44f)
                        else -> 1f
                    }
                    drawRect(Ani.Onyx.copy(alpha = alpha.coerceIn(0f, 1f)))
                },
        )
    }
}

@Composable
private fun HeroSlide(
    media: Media,
    art: TmdbArt?,
    inList: Boolean,
    onWatch: () -> Unit,
    onDetails: () -> Unit,
    onAdd: () -> Unit,
) {
    val logoWidth = (LocalConfiguration.current.screenWidthDp * 0.68f).coerceAtMost(340f).dp

    // The slide itself is transparent: the shared backdrop shows through.
    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 20.dp, end = 20.dp, bottom = 22.dp),
        ) {
            val logo = art?.logo
            val waitingForLogo = art == null && BuildConfig.TMDB_API_KEY.isNotBlank()
            when {
                logo != null -> AsyncImage(
                    model = logo,
                    contentDescription = media.displayTitle,
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.CenterStart,
                    modifier = Modifier.width(logoWidth).height(92.dp),
                )
                waitingForLogo -> Spacer(Modifier.height(92.dp))
                else -> Text(
                    text = media.displayTitle,
                    style = inter(27f, FontWeight.Bold, lineHeight = 32f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(16.dp))

            val chips = buildList {
                media.airedEpisodes?.takeIf { it > 0 }?.let { add(if (it == 1) "1 Episode" else "$it Episodes") }
                media.year?.let { add(it.toString()) }
                addAll(media.genres.orEmpty().take(3))
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                chips.forEach { label -> HeroChip(label) }
            }

            Spacer(Modifier.height(14.dp))

            Text(
                text = media.cleanDescription,
                style = inter(15f, lineHeight = 23f),
                color = Color(0xFFC3BFC7),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(20.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier
                        .height(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White)
                        .clickable(onClick = onWatch)
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(AniIcons.Play, contentDescription = null, tint = Color.Black, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Watch Now", style = inter(13.5f, FontWeight.Medium), color = Color.Black, maxLines = 1, softWrap = false)
                }
                Row(
                    modifier = Modifier
                        .height(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Ani.ButtonDark)
                        .clickable(onClick = onDetails)
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(AniIcons.Info, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Details", style = inter(13.5f, FontWeight.Medium), maxLines = 1, softWrap = false)
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Ani.ButtonDark)
                        .clickable(onClick = onAdd),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(if (inList) AniIcons.Check else AniIcons.Add, contentDescription = "Add to list", tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

@Composable
internal fun HeroChip(label: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.10f))
            .border(1.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(50))
            .padding(horizontal = 11.dp, vertical = 5.dp),
    ) {
        Text(label, style = inter(11.5f, FontWeight.Medium), color = Color(0xFFE3E0E6), maxLines = 1, softWrap = false)
    }
}

// ---------------------------------------------------------------- continue watching

@Composable
private fun ContinueWatching(
    entries: List<HistoryItem>,
    onClick: (HistoryItem) -> Unit,
    onRemove: (HistoryItem) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(top = 28.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Continue Watching",
                style = inter(20f, FontWeight.SemiBold),
                modifier = Modifier.weight(1f),
            )
            Text("View all", style = inter(15f), color = Ani.Muted)
            Icon(AniIcons.ChevronRight, contentDescription = null, tint = Ani.Muted, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(14.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(entries, key = { it.mediaId }) { item ->
                Column(Modifier.width(240.dp).clickable { onClick(item) }) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Ani.Item),
                    ) {
                        AsyncImage(
                            model = item.image,
                            contentDescription = item.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(10.dp)
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.40f))
                                .clickable { onRemove(item) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(AniIcons.Close, contentDescription = "Remove", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                        Box(
                            Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth(item.progress.coerceIn(0f, 1f))
                                .height(3.dp)
                                .background(Ani.Lavender),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        item.title,
                        style = inter(15f, FontWeight.Medium, lineHeight = 20f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text("EP ${item.episode}", style = inter(13f), color = Ani.Muted)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- latest episodes

@Composable
private fun LatestHeader(
    dubMode: Boolean,
    onModeChange: (Boolean) -> Unit,
    page: Int,
    pageCount: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Latest Episodes",
            style = inter(20f, FontWeight.SemiBold),
            color = Ani.Soft,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        SubDubToggle(dubMode, onModeChange)
        Spacer(Modifier.width(8.dp))
        PagerPill(page, pageCount, onPrev, onNext)
    }
}

@Composable
private fun SubDubToggle(dubMode: Boolean, onChange: (Boolean) -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier = Modifier
            .height(30.dp)
            .clip(shape)
            .border(1.dp, Color.White.copy(alpha = 0.28f), shape)
            .padding(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToggleSegment("Sub", selected = !dubMode) { onChange(false) }
        ToggleSegment("Dub", selected = dubMode) { onChange(true) }
    }
}

@Composable
private fun ToggleSegment(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(50))
            .background(if (selected) Ani.Lavender else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = inter(12.5f, if (selected) FontWeight.SemiBold else FontWeight.Medium),
            color = if (selected) Color.White else Color.White.copy(alpha = 0.85f),
        )
    }
}

@Composable
private fun PagerPill(page: Int, pageCount: Int, onPrev: () -> Unit, onNext: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier = Modifier
            .height(30.dp)
            .clip(shape)
            .border(1.dp, Color.White.copy(alpha = 0.28f), shape)
            .padding(horizontal = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = AniIcons.ChevronLeft,
            contentDescription = "Previous page",
            tint = Color.White.copy(alpha = if (page > 0) 0.9f else 0.35f),
            modifier = Modifier.size(20.dp).clip(CircleShape).clickable(enabled = page > 0, onClick = onPrev),
        )
        Text(
            text = "${page + 1}/$pageCount",
            style = inter(12.5f, FontWeight.Medium),
            color = Color.White.copy(alpha = 0.9f),
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Icon(
            imageVector = AniIcons.ChevronRight,
            contentDescription = "Next page",
            tint = Color.White.copy(alpha = if (page < pageCount - 1) 0.9f else 0.35f),
            modifier = Modifier.size(20.dp).clip(CircleShape).clickable(enabled = page < pageCount - 1, onClick = onNext),
        )
    }
}

@Composable
private fun EpisodeRow(rowItems: List<LatestEpisode>, columns: Int, onClick: (LatestEpisode) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        rowItems.forEach { episode ->
            EpisodeCard(episode, { onClick(episode) }, Modifier.weight(1f))
        }
        repeat((columns - rowItems.size).coerceAtLeast(0)) {
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun EpisodeCard(item: LatestEpisode, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val media = item.media
    BoxWithConstraints(modifier.clickable(onClick = onClick)) {
        // Badge size follows the card width so CC + format + year always fit on one line.
        val fit = (maxWidth / 106.dp).coerceIn(0.9f, 1.5f)
        val badgeText = 9.5f * fit
        val badgeIcon = (10f * fit).dp
        val badgePad = (5f * fit).dp

        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Ani.Item),
            ) {
                PosterImage(
                    url = media.posterUrl,
                    contentDescription = media.displayTitle,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(72.dp)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000)))),
                )
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "EP ${item.episode}",
                        style = inter(11f, FontWeight.SemiBold),
                        color = Color.White,
                        maxLines = 1,
                        softWrap = false,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        timeAgo(item.airingAt),
                        style = inter(10.5f, FontWeight.Medium),
                        color = Color.White.copy(alpha = 0.92f),
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }

            Spacer(Modifier.height(7.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                InfoBadge(
                    text = item.episode.toString(),
                    icon = AniIcons.ClosedCaption,
                    textSize = badgeText,
                    iconSize = badgeIcon,
                    hPad = badgePad,
                    vPad = 4.dp,
                )
                InfoBadge(text = media.formatLabel, textSize = badgeText, hPad = badgePad, vPad = 4.dp)
                media.year?.let {
                    InfoBadge(text = it.toString(), textSize = badgeText, hPad = badgePad, vPad = 4.dp)
                }
            }

            Spacer(Modifier.height(7.dp))

            Text(
                text = media.displayTitle,
                style = inter(13.5f, FontWeight.Medium, lineHeight = 18f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun InfoBadge(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    background: Color = Ani.Chip,
    textSize: Float = 12f,
    iconSize: Dp = 13.dp,
    hPad: Dp = 8.dp,
    vPad: Dp = 5.dp,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(7.dp))
            .background(background)
            .padding(horizontal = hPad, vertical = vPad),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Ani.Muted, modifier = Modifier.size(iconSize))
            Spacer(Modifier.width(3.dp))
        }
        Text(
            text = text,
            style = inter(textSize, FontWeight.Medium),
            color = Ani.Muted,
            maxLines = 1,
            softWrap = false,
        )
    }
}

// ---------------------------------------------------------------- top airing / upcoming

@Composable
private fun ListPanel(title: String, items: List<Media>, dot: Color, onClick: (Media) -> Unit) {
    var expanded by rememberSaveable(title) { mutableStateOf(false) }
    val visible = if (expanded) items else items.take(COLLAPSED_COUNT)
    val shape = RoundedCornerShape(16.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(shape)
            .background(Ani.Panel)
            .border(1.dp, Ani.Border, shape)
            .padding(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 2.dp, top = 4.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(AniIcons.ChevronRight, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text(title, style = inter(15f, FontWeight.SemiBold, letterSpacing = 0.6f))
        }

        visible.forEach { media ->
            RankRow(media, dot, onClick)
            Spacer(Modifier.height(8.dp))
        }

        if (items.size > COLLAPSED_COUNT) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Ani.Item)
                    .clickable { expanded = !expanded },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = AniIcons.ArrowDown,
                    contentDescription = if (expanded) "Show less" else "Show more",
                    tint = Ani.Muted,
                    modifier = Modifier.size(24.dp).rotate(if (expanded) 180f else 0f),
                )
            }
        }
    }
}

private const val COLLAPSED_COUNT = 5

@Composable
private fun RankRow(media: Media, dot: Color, onClick: (Media) -> Unit) {
    val grayscale = remember { ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) }

    Box(
        Modifier
            .fillMaxWidth()
            .height(96.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Ani.Item)
            .clickable { onClick(media) },
    ) {
        // Faint grayscale artwork on the right, fading into the row colour.
        AsyncImage(
            model = media.backdropUrl ?: media.posterUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            colorFilter = grayscale,
            alpha = 0.20f,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.horizontalGradient(listOf(Ani.Item, Ani.Item.copy(alpha = 0.45f)))),
        )

        Row(Modifier.fillMaxSize()) {
            PosterImage(
                url = media.posterUrl,
                contentDescription = media.displayTitle,
                modifier = Modifier.width(68.dp).fillMaxHeight(),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(9.dp).clip(CircleShape).background(dot))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = media.displayTitle,
                        style = inter(14.5f, FontWeight.SemiBold, lineHeight = 19f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.padding(start = 17.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    InfoBadge(media.formatLabel, background = Ani.ChipOnItem, textSize = 10.5f, hPad = 7.dp, vPad = 4.dp)
                    media.year?.let {
                        InfoBadge(it.toString(), icon = AniIcons.Calendar, background = Ani.ChipOnItem, textSize = 10.5f, iconSize = 11.dp, hPad = 7.dp, vPad = 4.dp)
                    }
                    media.airedEpisodes?.takeIf { it > 0 }?.let {
                        InfoBadge(it.toString(), icon = AniIcons.ClosedCaption, background = Ani.ChipOnItem, textSize = 10.5f, iconSize = 11.dp, hPad = 7.dp, vPad = 4.dp)
                    }
                    media.averageScore?.let {
                        InfoBadge(it.toString(), icon = AniIcons.Star, background = Ani.ChipOnItem, textSize = 10.5f, iconSize = 11.dp, hPad = 7.dp, vPad = 4.dp)
                    }
                }
            }
        }
    }
}

/** Poster art decoded at full resolution and scaled with high-quality filtering, so cards stay crisp. */
@Composable
internal fun PosterImage(url: String?, contentDescription: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val request = remember(url) {
        ImageRequest.Builder(context)
            .data(url)
            .size(Size.ORIGINAL)
            .crossfade(true)
            .build()
    }
    AsyncImage(
        model = request,
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        filterQuality = FilterQuality.High,
        modifier = modifier,
    )
}

// ---------------------------------------------------------------- error

@Composable
internal fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Something went wrong", style = inter(20f, FontWeight.SemiBold))
        Spacer(Modifier.height(8.dp))
        Text(message, style = inter(15f, lineHeight = 22f), color = Ani.Muted)
        Spacer(Modifier.height(20.dp))
        Box(
            modifier = Modifier
                .height(42.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Ani.Lavender)
                .clickable(onClick = onRetry)
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("Try again", style = inter(15f, FontWeight.SemiBold), color = Color.White)
        }
    }
}
