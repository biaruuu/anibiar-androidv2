@file:OptIn(ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.anibiar.app.ui.home

import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.combinedClickable
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
import kotlinx.coroutines.launch
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
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.lifecycle.repeatOnLifecycle
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
import com.anibiar.app.ui.theme.favPop
import com.anibiar.app.ui.theme.staggerIn
import com.anibiar.app.ui.theme.glass
import com.anibiar.app.ui.theme.AniIcons
import com.anibiar.app.ui.theme.inter
import kotlinx.coroutines.delay

private const val EPISODES_PER_PAGE = 12
private const val MAX_EPISODE_PAGES = 5

@Composable
fun HomeScreen(onOpen: (Int) -> Unit, onSearch: () -> Unit = {}, onWatch: (Int) -> Unit = onOpen, onContinue: (Int) -> Unit = onOpen, viewModel: HomeViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val app = LocalContext.current.applicationContext as com.anibiar.app.AniBiarApp
    val saved by app.watchlist.ids.collectAsStateWithLifecycle()
    // Live data: refresh whenever Home is on screen, then every minute while it stays visible.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
            while (true) {
                viewModel.refreshIfStale(55_000)
                delay(60_000)
            }
        }
    }
    HomeContent(
        state = state,
        saved = saved,
        onToggleSaved = app.watchlist::toggle,
        onOpen = onOpen,
        onSearch = onSearch,
        onWatch = onWatch,
        onContinue = onContinue,
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
    onSearch: () -> Unit,
    onWatch: (Int) -> Unit,
    onContinue: (Int) -> Unit,
    onRetry: () -> Unit,
    onRemoveHistory: (HistoryItem) -> Unit,
) {
    val app = LocalContext.current.applicationContext as com.anibiar.app.AniBiarApp
    val annTick by com.anibiar.app.social.LiveSync.tick.collectAsStateWithLifecycle()
    val annList by androidx.compose.runtime.produceState(emptyList<com.anibiar.app.data.Announcement>(), annTick) { value = com.anibiar.app.data.Announcements.load(app) }
    var annGone by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(com.anibiar.app.data.Announcements.dismissed(app)) }
    var genre by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    val genrePool = remember(state.trending, state.popular, state.topAiring, state.upcoming, state.reco, state.added) {
        (state.trending + state.popular + state.topAiring + state.upcoming + state.reco + state.added).distinctBy { it.id }
    }
    val genreList = remember(genrePool) { genrePool.flatMap { it.genres.orEmpty() }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(10).map { it.key } }
    val context = LocalContext.current
    val comingSoon = { Toast.makeText(context, "Coming soon", Toast.LENGTH_SHORT).show() }

    val listState = rememberLazyListState()
    // The card showing its play/+ buttons after a long press; clears after 5s or on any other tap.
    var armedId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(armedId) {
        if (armedId != null) {
            delay(5000)
            armedId = null
        }
    }
    val scrolled by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 200 }
    }
    var latestPage by rememberSaveable { mutableStateOf(0) }
    var dubMode by rememberSaveable { mutableStateOf(false) }
    var popularPage by rememberSaveable { mutableStateOf(0) }
    var trendingPage by rememberSaveable { mutableStateOf(0) }
    var trendingDub by rememberSaveable { mutableStateOf(false) }
    var popularDub by rememberSaveable { mutableStateOf(false) }
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

        var refreshing by remember { mutableStateOf(false) }
        val refreshScope = androidx.compose.runtime.rememberCoroutineScope()
        val st = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState()
        androidx.compose.material3.pulltorefresh.PullToRefreshBox(
            isRefreshing = refreshing,
            state = st,
            onRefresh = { refreshing = true; onRetry(); refreshScope.launch { delay(1400); refreshing = false } },
            modifier = Modifier.fillMaxSize(),
            indicator = {
                Box(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 8.dp).graphicsLayer { alpha = if (refreshing) 1f else st.distanceFraction.coerceIn(0f, 1f); translationY = if (refreshing) 0f else (st.distanceFraction.coerceIn(0f, 1.3f) - 1f) * 60f }) {
                    com.anibiar.app.ui.splash.KiroLogo(44.dp)
                }
            },
        ) {
        when {
                state.loading && state.hero.isEmpty() && state.latest.isEmpty() -> {
                    com.anibiar.app.ui.theme.HomeSkeleton()
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
                        // extra room so the floating tab bar never covers the last row
                        contentPadding = PaddingValues(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 80.dp),
                        modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                            // Only a real tap on empty space clears the hover; scrolling/dragging never does.
                            val slop = viewConfiguration.touchSlop
                            awaitPointerEventScope {
                                var downAt: androidx.compose.ui.geometry.Offset? = null
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Final)
                                    for (c in event.changes) {
                                        if (c.pressed && !c.previousPressed) {
                                            downAt = if (c.isConsumed) null else c.position
                                        } else if (!c.pressed && c.previousPressed) {
                                            val start = downAt
                                            if (start != null && (c.position - start).getDistance() < slop) armedId = null
                                            downAt = null
                                        }
                                    }
                                }
                            }
                        },
                    ) {
                        if (state.offline) item(key = "offline") {
                            Text(
                                "You're offline. Showing what was saved on this device.",
                                style = inter(13f), color = Ani.Muted,
                                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
    
                        if (state.hero.isNotEmpty()) {
                            item(key = "hero") {
                              Box(
                                Modifier.graphicsLayer {
                                    val off = if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset.toFloat() else 99999f
                                    translationY = off * 0.45f
                                    alpha = (1f - off / (size.height.coerceAtLeast(1f) * 1.15f)).coerceIn(0f, 1f)
                                },
                              ) {
                                HeroPager(
                                    items = state.hero,
                                    art = state.art,
                                    height = heroHeight,
                                    onPageChanged = { heroIndex = it },
                                    saved = saved,
                                    onWatch = onWatch,
                                    onDetails = onOpen,
                                    onAdd = onToggleSaved,
                                )
                              }
                            }
                        }
    
                        annList.filter { it.id !in annGone || it.draft }.take(2).forEach { a ->
                            item(key = "ann-${a.id}") {
                                AnnouncementCard(a, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), onDismiss = if (a.draft) null else { { com.anibiar.app.data.Announcements.dismiss(app, a.id); annGone = annGone + a.id } })
                            }
                        }
    

                    if (genreList.isNotEmpty()) {
                        item(key = "genre-chips") {
                            androidx.compose.foundation.lazy.LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 14.dp)) {
                                items(genreList, key = { it }) { g ->
                                    val on = genre == g
                                    Text(g, style = inter(13.5f, FontWeight.SemiBold), color = if (on) Color.Black else Color.White,
                                        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) Ani.Lavender else Ani.ButtonDark).clickable { genre = if (on) null else g }.padding(horizontal = 16.dp, vertical = 8.dp))
                                }
                            }
                        }
                        genre?.let { g ->
                            val picks = genrePool.filter { g in it.genres.orEmpty() }
                            if (picks.isNotEmpty()) item(key = "genre-row-$g") { RecoRow("$g picks", picks, onOpen) }
                        }
                    }
    
                        if (state.history.isNotEmpty()) {
                            item(key = "continue") {
                                ContinueWatching(
                                    entries = state.history,
                                    onClick = { onContinue(it.mediaId) },
                                    onRemove = onRemoveHistory,
                                )
                            }
                        }
    
                        if (state.added.isNotEmpty()) item(key = "added") { RecoRow("Added by AniBiar", state.added, onOpen) }
    
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
                                EpisodeRow(rows[index], columns, saved, onToggleSaved, armedId, { armedId = it }, onClick = { onOpen(it.media.id) })
                                Spacer(Modifier.height(16.dp))
                            }
                        }
    
                        if (state.trending.isNotEmpty()) {
                            val trPages = state.trending.chunked(EPISODES_PER_PAGE).take(MAX_EPISODE_PAGES)
                            val trCount = trPages.size.coerceAtLeast(1)
                            val trCur = trendingPage.coerceIn(0, trCount - 1)
                            val trRows = trPages.getOrNull(trCur).orEmpty().chunked(columns)
                            item(key = "trending-header") {
                                Spacer(Modifier.height(12.dp))
                                LatestHeader(
                                    title = "Trending Now",
                                    dubMode = trendingDub,
                                    onModeChange = { trendingDub = it },
                                    page = trCur,
                                    pageCount = trCount,
                                    onPrev = { trendingPage = (trCur - 1).coerceAtLeast(0) },
                                    onNext = { trendingPage = (trCur + 1).coerceAtMost(trCount - 1) },
                                )
                                Spacer(Modifier.height(14.dp))
                            }
                            if (trendingDub) {
                                item(key = "trending-dub-empty") {
                                    Text(
                                        text = "Dubbed releases aren't available yet.",
                                        style = inter(15f),
                                        color = Ani.Muted,
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 32.dp),
                                    )
                                }
                            } else {
                                items(trRows.size, key = { "trend-$trCur-$it" }) { index ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(13.dp),
                                    ) {
                                        trRows[index].forEachIndexed { col, media ->
                                            val rank = trCur * EPISODES_PER_PAGE + index * columns + col + 1
                                            MediaGridCard(
                                                media = media,
                                                leftText = "#$rank",
                                                rightText = "",
                                                rating = media.averageScore?.let { it / 10f },
                                                ccText = (media.airedEpisodes ?: media.episodes)?.takeIf { it > 0 }?.toString(),
                                                onClick = { onOpen(media.id) },
                                                saved = media.id in saved,
                                                onToggleSaved = { onToggleSaved(media.id) },
                                                armed = armedId == "N${media.id}",
                                                onArm = { armedId = "N${media.id}" },
                                                modifier = Modifier.weight(1f),
                                            )
                                        }
                                        repeat((columns - trRows[index].size).coerceAtLeast(0)) { Spacer(Modifier.weight(1f)) }
                                    }
                                    Spacer(Modifier.height(16.dp))
                                }
                            }
                        }
    
                        if (state.popular.isNotEmpty()) {
                            val popPages = state.popular.chunked(EPISODES_PER_PAGE).take(MAX_EPISODE_PAGES)
                            val popPageCount = popPages.size.coerceAtLeast(1)
                            val popCur = popularPage.coerceIn(0, popPageCount - 1)
                            val popRows = popPages.getOrNull(popCur).orEmpty().chunked(columns)
                            item(key = "popular-header") {
                                Spacer(Modifier.height(12.dp))
                                LatestHeader(
                                    title = "Most Popular",
                                    dubMode = popularDub,
                                    onModeChange = { popularDub = it },
                                    page = popCur,
                                    pageCount = popPageCount,
                                    onPrev = { popularPage = (popCur - 1).coerceAtLeast(0) },
                                    onNext = { popularPage = (popCur + 1).coerceAtMost(popPageCount - 1) },
                                )
                                Spacer(Modifier.height(14.dp))
                            }
                            if (popularDub) {
                                item(key = "popular-dub-empty") {
                                    Text(
                                        text = "Dubbed releases aren't available yet.",
                                        style = inter(15f),
                                        color = Ani.Muted,
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 32.dp),
                                    )
                                }
                            } else {
                                items(popRows.size, key = { "pop-$popCur-$it" }) { index ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(13.dp),
                                    ) {
                                        popRows[index].forEachIndexed { col, media ->
                                            val rank = popCur * EPISODES_PER_PAGE + index * columns + col + 1
                                            MediaGridCard(
                                                media = media,
                                                leftText = "#$rank",
                                                rightText = "",
                                                rating = media.averageScore?.let { it / 10f },
                                                ccText = (media.airedEpisodes ?: media.episodes)?.takeIf { it > 0 }?.toString(),
                                                onClick = { onOpen(media.id) },
                                                saved = media.id in saved,
                                                onToggleSaved = { onToggleSaved(media.id) },
                                                armed = armedId == "P${media.id}",
                                                onArm = { armedId = "P${media.id}" },
                                                modifier = Modifier.weight(1f),
                                            )
                                        }
                                        repeat((columns - popRows[index].size).coerceAtLeast(0)) { Spacer(Modifier.weight(1f)) }
                                    }
                                    Spacer(Modifier.height(16.dp))
                                }
                            }
                        }
    
                        if (state.topAiring.isNotEmpty()) {
                            item(key = "top-airing") {
                                Spacer(Modifier.height(12.dp))
                                ListPanel(
                                    title = "TOP AIRING",
                                    keyPrefix = "T",
                                    items = state.topAiring,
                                    dot = Ani.Lime,
                                    onClick = { onOpen(it.id) },
                                    saved = saved,
                                    onToggleSaved = onToggleSaved,
                                    armedId = armedId,
                                    onArm = { armedId = it },
                                )
                            }
                        }
    
                        if (state.upcoming.isNotEmpty()) {
                            item(key = "upcoming") {
                                Spacer(Modifier.height(16.dp))
                                ListPanel(
                                    title = "UPCOMING",
                                    keyPrefix = "U",
                                    items = state.upcoming,
                                    dot = Ani.Lime,
                                    onClick = { onOpen(it.id) },
                                    saved = saved,
                                    onToggleSaved = onToggleSaved,
                                    armedId = armedId,
                                    onArm = { armedId = it },
                                )
                                Spacer(Modifier.height(32.dp))
                            }
                        }
                    }
                }
            }
        }

        AniTopBar(scrolled = scrolled, onSearch = onSearch)
    }
}

// ---------------------------------------------------------------- top bar

@Composable
internal fun AniTopBar(
    scrolled: Boolean,
    onSearch: () -> Unit,
    onLogo: (() -> Unit)? = null,
) {
    val background by animateColorAsState(
        targetValue = if (scrolled) Ani.Onyx.copy(alpha = 0.92f) else Color.Transparent,
        label = "topBarBackground",
    )
    Column(Modifier.fillMaxWidth().background(background)) {
        Spacer(Modifier.statusBarsPadding())
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

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier
                        .height(40.dp)
                        .clip(RoundedCornerShape(50))
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
                        .glass(RoundedCornerShape(50))
                        .clickable(onClick = onDetails)
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(AniIcons.Info, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Details", style = inter(13.5f, FontWeight.Medium), maxLines = 1, softWrap = false)
                }
                val (heroFav, toggleHeroFav) = rememberFav(media)
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .glass(CircleShape)
                        .clickable(onClick = toggleHeroFav),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(if (heroFav) AniIcons.BookmarkFilled else AniIcons.Bookmark, contentDescription = "Favorite", tint = if (heroFav) FavYellow else Color.White, modifier = Modifier.size(20.dp).favPop(heroFav))
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
            .background(Brush.linearGradient(listOf(Color(0x99202028), Color(0xB3050507), Color(0x99181820))))
            .border(1.dp, Brush.linearGradient(listOf(Color(0x80FFFFFF), Color(0x14FFFFFF), Color(0x4DFFFFFF))), RoundedCornerShape(50))
            .padding(horizontal = 11.dp, vertical = 5.dp),
    ) {
        Text(label, style = inter(11.5f, FontWeight.Medium), color = Color.White, maxLines = 1, softWrap = false)
    }
}

// ---------------------------------------------------------------- continue watching

@Composable
private fun RecoRow(title: String, items: List<Media>, onClick: (Int) -> Unit) {
    val style = com.anibiar.app.ui.theme.Appearance.cardStyle
    val cardW = if (style == 2) 172.dp else 132.dp
    var preview by remember { mutableStateOf<Media?>(null) }
    var animate by remember { mutableStateOf(true) }
    androidx.compose.runtime.LaunchedEffect(Unit) { kotlinx.coroutines.delay(1500); animate = false }
    Column(Modifier.fillMaxWidth().padding(top = 28.dp)) {
        Text(
            title,
            style = inter(20f, FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(14.dp))
        val rowState = androidx.compose.foundation.lazy.rememberLazyListState()
        val haptic = com.anibiar.app.ui.theme.rememberTick()
        LazyRow(
            state = rowState, contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
            flingBehavior = androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior(rowState),
        ) {
            itemsIndexed(items, key = { _, it -> it.id }) { index, m ->
                Column(Modifier.width(cardW).staggerIn(index, animate).combinedClickable(onClick = { onClick(m.id) }, onLongClick = { haptic(); preview = m })) {
                    Box {
                        PosterImage(
                            url = m.posterUrl,
                            contentDescription = m.displayTitle,
                            maxSide = if (style == 2) 520 else 400,
                            modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(if (style == 2) 16.dp else 12.dp)).background(Ani.Item),
                        )
                        if (style == 2) m.averageScore?.let { sc ->
                            Text("★ ${"%.1f".format(sc / 10f)}", style = inter(11.5f, FontWeight.Bold), color = Color.White,
                                modifier = Modifier.align(Alignment.TopStart).padding(8.dp).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 8.dp, vertical = 3.dp))
                        }
                    }
                    if (style != 0) {
                        Spacer(Modifier.height(8.dp))
                        Text(m.displayTitle, style = inter(14f, FontWeight.Medium, lineHeight = 18f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        m.genres?.firstOrNull()?.let { Text(it, style = inter(12f), color = Ani.Muted, maxLines = 1) }
                    }
                }
            }
        }
    }
    preview?.let { m -> QuickPreview(m, onClose = { preview = null }, onOpen = { preview = null; onClick(m.id) }) }
}

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
                                .glass(CircleShape)
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
internal fun LatestHeader(
    title: String = "Latest Episodes",
    dubMode: Boolean,
    onModeChange: (Boolean) -> Unit,
    showToggle: Boolean = true,
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
            text = title,
            style = inter(20f, FontWeight.SemiBold),
            color = Ani.Soft,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        if (showToggle) {
            SubDubToggle(dubMode, onModeChange)
            Spacer(Modifier.width(8.dp))
        }
        PagerPill(page, pageCount, onPrev, onNext)
    }
}

@Composable
internal fun SubDubToggle(dubMode: Boolean, onChange: (Boolean) -> Unit) {
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
internal fun PagerPill(page: Int, pageCount: Int, onPrev: () -> Unit, onNext: () -> Unit) {
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
private fun EpisodeRow(
    rowItems: List<LatestEpisode>,
    columns: Int,
    saved: Set<Int>,
    onToggleSaved: (Int) -> Unit,
    armedId: String?,
    onArm: (String) -> Unit,
    onClick: (LatestEpisode) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        rowItems.forEach { episode ->
            EpisodeCard(
                episode,
                { onClick(episode) },
                saved = episode.media.id in saved,
                onToggleSaved = { onToggleSaved(episode.media.id) },
                armed = armedId == "L${episode.media.id}",
                onArm = { onArm("L${episode.media.id}") },
                modifier = Modifier.weight(1f),
            )
        }
        repeat((columns - rowItems.size).coerceAtLeast(0)) {
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun EpisodeCard(
    item: LatestEpisode,
    onClick: () -> Unit,
    saved: Boolean,
    onToggleSaved: () -> Unit,
    armed: Boolean,
    onArm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // "5m ago" stays live: re-evaluated every 30s.
    val now by androidx.compose.runtime.produceState(System.currentTimeMillis()) {
        while (true) { delay(30_000); value = System.currentTimeMillis() }
    }
    val agoLabel = remember(now, item.airingAt) { timeAgo(item.airingAt) }
    MediaGridCard(
        media = item.media,
        leftText = "EP ${item.episode}",
        rightText = agoLabel,
        ccText = item.episode.toString(),
        onClick = onClick,
        saved = saved,
        onToggleSaved = onToggleSaved,
        armed = armed,
        onArm = onArm,
        modifier = modifier,
    )
}

/** The Latest-Episodes card style, reused by Most Popular (left/right overlay texts differ). */
@Composable
internal fun MediaGridCard(
    media: Media,
    leftText: String,
    rightText: String,
    overlay: Boolean = true,
    ccText: String?,
    rating: Float? = null,
    onClick: () -> Unit,
    saved: Boolean,
    onToggleSaved: () -> Unit,
    armed: Boolean,
    onArm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hover = rememberCardHover()
    val active = hover.pressed
    val accent = remember(media.coverImage.color) { accentOf(media.coverImage.color) }
    val chipText by accentColor(active, accent, Color(0xFFE4E1EA))
    val chipBg by accentColor(active, accent.copy(alpha = 0.28f), Color(0x2EFFFFFF))
    val titleColor by accentColor(active, accent, Color.White)
    BoxWithConstraints(modifier.hoverCard(hover, onClick, onArm = onArm)) {
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
                HoverOverlay(active, saved = saved, showAdd = true, accent = accent, onToggleSaved = onToggleSaved, playSize = (44f * fit).toInt(), media = media)
                if (overlay) Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(72.dp)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000)))),
                )
                if (overlay) Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        leftText,
                        style = inter(11f, FontWeight.SemiBold),
                        color = Color.White,
                        maxLines = 1,
                        softWrap = false,
                    )
                    Spacer(Modifier.width(4.dp))
                    if (rating != null) {
                        // Score tinted by tier (Absolute Cinema blue ... Garbage purple), star included.
                        val tier = ratingColor(rating)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(AniIcons.Star, contentDescription = null, tint = tier, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(3.dp))
                            Text(
                                "%.1f".format(rating),
                                style = inter(11.5f, FontWeight.Bold),
                                color = tier,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    } else {
                        Text(
                            rightText,
                            style = inter(10.5f, FontWeight.Medium),
                            color = Color.White.copy(alpha = 0.92f),
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }

            Spacer(Modifier.height(7.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                ccText?.let {
                    InfoBadge(
                        text = it,
                        icon = AniIcons.ClosedCaption,
                        textSize = badgeText,
                        iconSize = badgeIcon,
                        hPad = badgePad,
                        vPad = 4.dp,
                        background = chipBg,
                        textColor = chipText,
                    )
                }
                InfoBadge(text = media.formatLabel, textSize = badgeText, hPad = badgePad, vPad = 4.dp, background = chipBg, textColor = chipText)
                media.year?.let {
                    InfoBadge(text = it.toString(), textSize = badgeText, hPad = badgePad, vPad = 4.dp, background = chipBg, textColor = chipText)
                }
            }

            Spacer(Modifier.height(7.dp))

            Text(
                text = media.displayTitle,
                style = inter(13.5f, FontWeight.Medium, lineHeight = 18f),
                color = titleColor,
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
    textColor: Color = Ani.Muted,
    textSize: Float = 12f,
    iconSize: Dp = 13.dp,
    hPad: Dp = 8.dp,
    vPad: Dp = 5.dp,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .border(1.dp, Color(0x2BFFFFFF), RoundedCornerShape(50))
            .padding(horizontal = hPad + 1.dp, vertical = vPad),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = textColor, modifier = Modifier.size(iconSize))
            Spacer(Modifier.width(3.dp))
        }
        Text(
            text = text,
            style = inter(textSize, FontWeight.SemiBold),
            color = textColor,
            maxLines = 1,
            softWrap = false,
        )
    }
}

// ---------------------------------------------------------------- top airing / upcoming

@Composable
private fun ListPanel(
    title: String,
    keyPrefix: String,
    items: List<Media>,
    dot: Color,
    onClick: (Media) -> Unit,
    saved: Set<Int>,
    onToggleSaved: (Int) -> Unit,
    armedId: String?,
    onArm: (String) -> Unit,
) {
    var expanded by rememberSaveable(title) { mutableStateOf(false) }
    val visible = if (expanded) items else items.take(COLLAPSED_COUNT)
    val shape = RoundedCornerShape(16.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(shape)
            .background(com.anibiar.app.ui.theme.GlassFill)
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
            RankRow(
                media, dot, onClick,
                saved = media.id in saved,
                onToggleSaved = { onToggleSaved(media.id) },
                armed = armedId == "$keyPrefix${media.id}",
                onArm = { onArm("$keyPrefix${media.id}") },
            )
            Spacer(Modifier.height(8.dp))
        }

        if (items.size > COLLAPSED_COUNT) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .clip(RoundedCornerShape(16.dp))
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
private fun RankRow(
    media: Media,
    dot: Color,
    onClick: (Media) -> Unit,
    saved: Boolean,
    onToggleSaved: () -> Unit,
    armed: Boolean,
    onArm: () -> Unit,
) {
    val grayscale = remember { ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) }
    val hover = rememberCardHover()
    val active = hover.active() || armed
    val accent = remember(media.coverImage.color) { accentOf(media.coverImage.color) }
    val chipText by accentColor(active, accent, Color(0xFFE4E1EA))
    val chipBg by accentColor(active, accent.copy(alpha = 0.28f), Color(0x2EFFFFFF))
    val titleColor by accentColor(active, accent, Color.White)
    val tint by accentColor(active, accent.copy(alpha = 0.30f), Ani.Item)
    val rowBorder by accentColor(active, accent.copy(alpha = 0.55f), Color.Transparent)
    val shape = RoundedCornerShape(16.dp)

    Box(
        Modifier
            .fillMaxWidth()
            .height(96.dp)
            .clip(shape)
            .background(Ani.Item)
            .border(1.dp, rowBorder, shape)
            .hoverCard(hover, { onClick(media) }),
    ) {
        // Artwork on the right (grayscale at rest, full colour on hover), fading into the row colour.
        PosterImage(
            url = media.backdropUrl ?: media.posterUrl,
            contentDescription = null,
            colorFilter = if (active) null else grayscale,
            alpha = if (active) 0.30f else 0.20f,
            maxSide = 640,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.horizontalGradient(listOf(tint, Ani.Item.copy(alpha = 0.45f)))),
        )

        Row(Modifier.fillMaxSize()) {
            Box(Modifier.width(68.dp).fillMaxHeight()) {
                PosterImage(
                    url = media.posterUrl,
                    contentDescription = media.displayTitle,
                    modifier = Modifier.fillMaxSize(),
                )
                HoverOverlay(active, playSize = 30)
            }
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
                        color = titleColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(8.dp))
                androidx.compose.foundation.layout.FlowRow(
                    modifier = Modifier.padding(start = 17.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    val (isFav, toggleFav) = rememberFav(media)
                    if (active || isFav) {
                        Box(
                            Modifier
                                .glass(RoundedCornerShape(50))
                                .clickable(onClick = toggleFav)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                if (isFav) AniIcons.BookmarkFilled else AniIcons.Bookmark,
                                contentDescription = "Favorite",
                                tint = if (isFav) FavYellow else accent,
                                modifier = Modifier.size(14.dp).favPop(isFav),
                            )
                        }
                    }
                    InfoBadge(media.formatLabel, background = chipBg, textColor = chipText, textSize = 10.5f, hPad = 7.dp, vPad = 4.dp)
                    media.year?.let {
                        InfoBadge(it.toString(), icon = AniIcons.Calendar, background = chipBg, textColor = chipText, textSize = 10.5f, iconSize = 11.dp, hPad = 7.dp, vPad = 4.dp)
                    }
                    media.airedEpisodes?.takeIf { it > 0 }?.let {
                        InfoBadge(it.toString(), icon = AniIcons.ClosedCaption, background = chipBg, textColor = chipText, textSize = 10.5f, iconSize = 11.dp, hPad = 7.dp, vPad = 4.dp)
                    }
                    media.averageScore?.let {
                        InfoBadge("%.1f".format(java.util.Locale.US, it / 10f), icon = AniIcons.Star, background = chipBg, textColor = ratingColor(it / 10f), textSize = 10.5f, iconSize = 11.dp, hPad = 7.dp, vPad = 4.dp)
                    }
                }
            }
        }
    }
}

/** Poster art decoded at full resolution and scaled with high-quality filtering, so cards stay crisp. */
@Composable
internal fun PosterImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    colorFilter: ColorFilter? = null,
    alpha: Float = 1f,
    maxSide: Int = 0,
) {
    val context = LocalContext.current
    val request = remember(url, maxSide) {
        ImageRequest.Builder(context)
            .data(url)
            .size(if (maxSide > 0) Size(maxSide, maxSide) else Size.ORIGINAL)
            .crossfade(true)
            .build()
    }
    AsyncImage(
        model = request,
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        filterQuality = FilterQuality.High,
        colorFilter = colorFilter,
        alpha = alpha,
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
                .clip(RoundedCornerShape(14.dp))
                .background(Ani.Lavender)
                .clickable(onClick = onRetry)
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("Try again", style = inter(15f, FontWeight.SemiBold), color = Color.White)
        }
    }
}


/** Opens on a long press: a larger look at an anime with its score, genres and synopsis, and one tap to open it. */
@Composable
private fun QuickPreview(m: Media, onClose: () -> Unit, onOpen: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Ani.Panel).border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(24.dp)),
        ) {
            Box {
                PosterImage(
                    url = m.bannerImage ?: m.posterUrl, contentDescription = m.displayTitle, maxSide = 800,
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 10f).background(Ani.Item),
                )
                Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, Ani.Panel))))
            }
            Column(Modifier.padding(horizontal = 18.dp).padding(bottom = 18.dp)) {
                Text(m.displayTitle, style = inter(20f, FontWeight.Bold), color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(8.dp))
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    m.averageScore?.let { HeroChip("★ ${"%.1f".format(it / 10f)}") }
                    m.format?.let { HeroChip(it.replace('_', ' ')) }
                    m.seasonYear?.let { HeroChip(it.toString()) }
                    m.genres.orEmpty().take(3).forEach { HeroChip(it) }
                }
                val d = m.cleanDescription
                if (d.isNotBlank()) Text(d, style = inter(13.5f, lineHeight = 19f), color = Ani.Soft, maxLines = 5, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp))
                Spacer(Modifier.height(16.dp))
                Box(Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(50)).background(Color.White).clickable(onClick = onOpen), contentAlignment = Alignment.Center) {
                    Text("Open", style = inter(15f, FontWeight.Bold), color = Color.Black)
                }
            }
        }
    }
}
