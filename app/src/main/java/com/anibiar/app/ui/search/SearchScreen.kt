package com.anibiar.app.ui.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import com.anibiar.app.data.displayTitle
import com.anibiar.app.data.posterUrl
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.anibiar.app.AniBiarApp
import com.anibiar.app.data.Media
import androidx.compose.foundation.shape.CircleShape
import com.anibiar.app.data.cleanDescription
import com.anibiar.app.data.airedEpisodes
import com.anibiar.app.ui.home.LatestHeader
import com.anibiar.app.ui.home.MediaGridCard
import com.anibiar.app.ui.home.clearOnEmptyTap
import com.anibiar.app.ui.theme.Ani
import com.anibiar.app.ui.theme.glass
import com.anibiar.app.ui.theme.AniIcons
import com.anibiar.app.ui.theme.inter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

private val GENRES = listOf(
    "Action", "Adventure", "Comedy", "Drama", "Ecchi", "Fantasy", "Horror", "Mahou Shoujo", "Mecha", "Music",
    "Mystery", "Psychological", "Romance", "Sci-Fi", "Slice of Life", "Sports", "Supernatural", "Thriller",
).map { it to it }
private val FORMATS = listOf(
    "TV" to "TV", "TV_SHORT" to "TV Short", "MOVIE" to "Movie", "SPECIAL" to "Special", "OVA" to "OVA", "ONA" to "ONA", "MUSIC" to "Music",
)
private val STATUSES = listOf("RELEASING" to "Airing", "FINISHED" to "Finished", "NOT_YET_RELEASED" to "Not Yet Aired", "CANCELLED" to "Cancelled", "HIATUS" to "Hiatus")
private val SORTS = listOf(
    "POPULARITY_DESC" to "Most Popular", "TRENDING_DESC" to "Trending", "SCORE_DESC" to "Highest Score",
    "FAVOURITES_DESC" to "Most Favorited", "UPDATED_AT_DESC" to "Recently Updated", "START_DATE_DESC" to "Newest",
    "START_DATE" to "Oldest", "TITLE_ROMAJI" to "Title A–Z", "TITLE_ROMAJI_DESC" to "Title Z–A", "EPISODES_DESC" to "Most Episodes",
)
private val RANK_SORTS = setOf("POPULARITY_DESC", "TRENDING_DESC", "SCORE_DESC", "FAVOURITES_DESC")
private val LANGUAGES = listOf("SUB" to "Sub", "DUB" to "Dub")
private val SOURCES = listOf(
    "ORIGINAL" to "Original", "MANGA" to "Manga", "LIGHT_NOVEL" to "Light Novel", "WEB_NOVEL" to "Web Novel", "NOVEL" to "Novel",
    "VISUAL_NOVEL" to "Visual Novel", "VIDEO_GAME" to "Video Game", "DOUJINSHI" to "Doujinshi", "ANIME" to "Anime", "OTHER" to "Other",
)
private val COUNTRIES = listOf("JP" to "Japan", "CN" to "China", "KR" to "South Korea", "TW" to "Taiwan")
private val ADULT = listOf("NON" to "Non-adult", "ADULT" to "Adult only", "ANY" to "Include adult")
private val SCORES = listOf(90, 80, 70, 60, 50).map { it.toString() to "$it+" }
private val SEASONS = listOf("WINTER" to "Winter", "SPRING" to "Spring", "SUMMER" to "Summer", "FALL" to "Fall")

@Composable
fun SearchScreen(
    onOpen: (Int) -> Unit,
    onBack: () -> Unit,
    mode: String,
    onModeChange: (String) -> Unit,
    onOpenUser: (String) -> Unit,
) {
    if (mode == "users") UserSearchScreen(onBack, onModeChange, onOpenUser)
    else AnimeSearchScreen(onOpen, onBack, mode, onModeChange)
}

@Composable
private fun AnimeSearchScreen(onOpen: (Int) -> Unit, onBack: () -> Unit, mode: String, onModeChange: (String) -> Unit, viewModel: SearchViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as AniBiarApp
    val saved by app.watchlist.ids.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var armedId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(armedId) {
        if (armedId != null) {
            delay(5_000)
            armedId = null
        }
    }
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    // Back to the first card whenever a new page / filter set arrives.
    LaunchedEffect(state.page) { listState.scrollToItem(0) }

    val filters = state.filters
    val years = remember {
        val now = Calendar.getInstance().get(Calendar.YEAR)
        (now + 1 downTo 1970).map { it.toString() to it.toString() }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Ani.Onyx)) {
        val columns = when {
            maxWidth >= 900.dp -> 6
            maxWidth >= 600.dp -> 4
            else -> 3
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().clearOnEmptyTap { armedId = null },
            contentPadding = PaddingValues(
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 12.dp,
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 84.dp,
            ),
        ) {
            item(key = "bar") {
                val tick = com.anibiar.app.ui.theme.rememberTick()
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Search", style = inter(22f, FontWeight.Bold), color = Color.White)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ModeDropdown(mode, onModeChange)
                        val listOn = com.anibiar.app.ui.theme.Appearance.listView
                        val ctx = androidx.compose.ui.platform.LocalContext.current
                        Box(Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.05f)).clickable { tick(); com.anibiar.app.ui.theme.Appearance.setListView(ctx, !listOn) }.padding(8.dp), contentAlignment = Alignment.Center) {
                            Icon(if (listOn) com.anibiar.app.ui.theme.FeatherIcons.Grid else com.anibiar.app.ui.theme.FeatherIcons.List, if (listOn) "Switch to grid" else "Switch to list", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                        }
                        val on = showFilters
                        Box(
                            Modifier.clip(CircleShape).background(if (on) Color.White.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f))
                                .border(1.dp, if (on) Color.White.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.1f), CircleShape)
                                .clickable { tick(); showFilters = !showFilters }.padding(8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(com.anibiar.app.ui.theme.FeatherIcons.Sliders, "Filters", tint = if (on || filters.activeCount > 0) Color.White else Color.White.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                        }
                    }
                }
                SearchField(
                    value = state.query,
                    onValue = viewModel::setQuery,
                    onSubmit = { focus.clearFocus(); viewModel.submit() },
                    modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp),
                )
                val pills = buildList<Pair<String, () -> Unit>> {
                    fun nice(v: String) = v.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
                    filters.genres.forEach { g -> add(g to { viewModel.setFilters(filters.copy(genres = filters.genres - g)) }) }
                    filters.formats.forEach { f -> add(nice(f) to { viewModel.setFilters(filters.copy(formats = filters.formats - f)) }) }
                    filters.status?.let { add("Status: ${nice(it)}" to { viewModel.setFilters(filters.copy(status = null)) }) }
                    filters.year?.let { add("Year: $it" to { viewModel.setFilters(filters.copy(year = null)) }) }
                    filters.season?.let { add("Season: ${nice(it)}" to { viewModel.setFilters(filters.copy(season = null)) }) }
                    filters.country?.let { add("Country: $it" to { viewModel.setFilters(filters.copy(country = null)) }) }
                    filters.language?.let { add(nice(it) to { viewModel.setFilters(filters.copy(language = null)) }) }
                    filters.score?.let { add("Score: $it+" to { viewModel.setFilters(filters.copy(score = null)) }) }
                }
                if (pills.isNotEmpty()) androidx.compose.foundation.lazy.LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                ) {
                    items(pills.size) { i ->
                        val (label, cancel) = pills[i]
                        Row(
                            Modifier.clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.12f)).border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(12.dp)).clickable { cancel() }.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(label, style = inter(12f, FontWeight.Bold), color = Color.White)
                            Icon(com.anibiar.app.ui.theme.FeatherIcons.XCircle, "Remove $label", tint = Color.White, modifier = Modifier.size(14.dp))
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
            }

            item(key = "tools") {
                if (showFilters) ToolBar(
                    activeCount = filters.activeCount,
                    expanded = showFilters,
                    onApply = { focus.clearFocus(); viewModel.submit() },
                    onReset = viewModel::reset,
                    onToggle = { showFilters = !showFilters },
                )
                AnimatedVisibility(showFilters, enter = expandVertically(tween(300)) + fadeIn(tween(300)), exit = shrinkVertically(tween(260)) + fadeOut(tween(200))) {
                    Column(Modifier.padding(horizontal = 8.dp).padding(top = 8.dp)) {
                        FilterRow(
                            FilterDropdown("Genres", null, GENRES, filters.genres, true) { viewModel.setFilters(filters.copy(genres = it)) },
                            FilterDropdown("Formats", null, FORMATS, filters.formats, true) { viewModel.setFilters(filters.copy(formats = it)) },
                        )
                        FilterRow(
                            FilterDropdown("Status", null, STATUSES, setOfNotNull(filters.status), false) { viewModel.setFilters(filters.copy(status = it.firstOrNull())) },
                            FilterDropdown("Year", AniIcons.Calendar, years, setOfNotNull(filters.year?.toString()), false) { viewModel.setFilters(filters.copy(year = it.firstOrNull()?.toInt())) },
                        )
                        FilterRow(
                            FilterDropdown("Most Popular", null, SORTS, setOfNotNull(filters.sort), false, sortStyle = true) { viewModel.setFilters(filters.copy(sort = it.firstOrNull())) },
                            FilterDropdown("Language", null, LANGUAGES, setOfNotNull(filters.language), false) { viewModel.setFilters(filters.copy(language = it.firstOrNull())) },
                        )
                        FilterRow(
                            FilterDropdown("Source", null, SOURCES, setOfNotNull(filters.source), false) { viewModel.setFilters(filters.copy(source = it.firstOrNull())) },
                            FilterDropdown("Country", null, COUNTRIES, setOfNotNull(filters.country), false) { viewModel.setFilters(filters.copy(country = it.firstOrNull())) },
                        )
                        FilterRow(
                            FilterDropdown("Non-adult", null, ADULT, setOf(filters.adult), false, sortStyle = true, clearable = false) { viewModel.setFilters(filters.copy(adult = it.firstOrNull() ?: "NON")) },
                            FilterDropdown("Score", AniIcons.Star, SCORES, setOfNotNull(filters.score?.toString()), false) { viewModel.setFilters(filters.copy(score = it.firstOrNull()?.toInt())) },
                        )
                        FilterRow(
                            FilterDropdown("Season", null, SEASONS, setOfNotNull(filters.season), false) { viewModel.setFilters(filters.copy(season = it.firstOrNull())) },
                            null,
                        )
                    }
                }
                Spacer(Modifier.height(22.dp))
            }

            item(key = "pagehead") {
                LatestHeader(
                    title = "Results",
                    dubMode = filters.language == "DUB",
                    onModeChange = { dub -> viewModel.setFilters(filters.copy(language = if (dub) "DUB" else null)) },
                    page = state.page - 1,
                    pageCount = state.lastPage.coerceAtLeast(state.page),
                    onPrev = { viewModel.goTo(state.page - 1) },
                    onNext = { viewModel.goTo(state.page + 1) },
                )
                Spacer(Modifier.height(14.dp))
            }

            if (state.results.isNotEmpty() && (state.suggest != null || state.offline)) {
                item(key = "notice") {
                    Text(
                        if (state.suggest != null) "No exact match. Did you mean \"${state.suggest}\"? Showing close matches."
                        else "You're offline. Showing matches saved on this device.",
                        style = inter(14f), color = Ani.Muted,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }

            when {
                state.loading && state.results.isEmpty() -> item(key = "loading") { com.anibiar.app.ui.theme.GridSkeleton(Modifier.padding(top = 12.dp)) }
                state.error != null && state.results.isEmpty() -> item(key = "error") {
                    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.error.orEmpty(), style = inter(15f), color = Ani.Muted)
                        Spacer(Modifier.height(12.dp))
                        OutlineButton("Retry", true, viewModel::retry)
                    }
                }
                state.results.isEmpty() -> item(key = "empty") {
                    com.anibiar.app.ui.theme.EmptyState(
                        com.anibiar.app.ui.theme.AniIcons.Search,
                        if (filters.language == "DUB") "No dubs yet" else "No anime found",
                        if (filters.language == "DUB") "Dubbed releases aren't available yet." else "Try another spelling or clear a filter.",
                    )
                }
                else -> {
                    if (com.anibiar.app.ui.theme.Appearance.listView) {
                        items(state.results.size, key = { "l${state.resultsPage}-${state.results[it].id}" }) { i ->
                            SearchListRow(state.results[i]) { onOpen(state.results[i].id) }
                        }
                    } else {
                    val rows = state.results.chunked(columns)
                    val ranked = state.query.isBlank() && (filters.sort ?: "POPULARITY_DESC") in RANK_SORTS
                    items(rows.size, key = { "r${state.resultsPage}-$it-${rows[it].first().id}" }) { index ->
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            rows[index].forEachIndexed { col, media ->
                                val rank = (state.resultsPage - 1) * 30 + index * columns + col + 1
                                MediaGridCard(
                                    media = media,
                                    leftText = if (ranked) "#$rank" else "",
                                    rightText = "",
                                    rating = media.averageScore?.let { it / 10f },
                                    ccText = (media.airedEpisodes ?: media.episodes)?.takeIf { it > 0 }?.toString(),
                                    onClick = { onOpen(media.id) },
                                    saved = media.id in saved,
                                    onToggleSaved = { app.watchlist.toggle(media.id) },
                                    armed = armedId == "S${media.id}",
                                    onArm = { armedId = "S${media.id}" },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            repeat((columns - rows[index].size).coerceAtLeast(0)) { Spacer(Modifier.weight(1f)) }
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                    }
                    item(key = "bottompager") {
                        Spacer(Modifier.height(14.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            OutlineButton("Prev", state.page > 1, { viewModel.goTo(state.page - 1) }, leading = AniIcons.ChevronLeft)
                            Spacer(Modifier.width(12.dp))
                            OutlineButton("Next", state.hasNext, { viewModel.goTo(state.page + 1) }, trailing = AniIcons.ChevronRight)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text, style = inter(15f), color = Ani.Muted,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 40.dp),
    )
}

@Composable
private fun SearchField(value: String, onValue: (String) -> Unit, onSubmit: () -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(19.dp)
    Row(
        modifier.fillMaxWidth().height(38.dp).clip(shape).background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.15f), Color.White.copy(alpha = 0.03f))), shape)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(com.anibiar.app.ui.theme.FeatherIcons.Search, "Search", tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text("Titles, genres, specials...", style = inter(13f), color = Color.White.copy(alpha = 0.35f))
            BasicTextField(
                value = value, onValueChange = onValue, singleLine = true,
                textStyle = inter(13.5f).copy(color = Color.White), cursorBrush = SolidColor(Color.White),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) Icon(
            com.anibiar.app.ui.theme.FeatherIcons.XCircle, "Clear", tint = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.size(16.dp).clickable { onValue("") },
        )
    }
}

/** Result row: dark card with the banner faintly behind, a poster with its score, title, details and genre chips. */
@Composable
private fun SearchListRow(m: Media, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(Modifier.padding(horizontal = 16.dp, vertical = 5.dp).fillMaxWidth().clip(shape).background(Color(0xFF0C0C10)).border(0.5.dp, Color.White.copy(alpha = 0.08f), shape).clickable(onClick = onClick)) {
        if (!m.bannerImage.isNullOrEmpty()) {
            coil.compose.AsyncImage(model = m.bannerImage, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.matchParentSize().graphicsLayer { alpha = 0.18f })
            Box(Modifier.matchParentSize().background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.94f), Color.Black.copy(alpha = 0.55f)))))
        }
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(72.dp).aspectRatio(0.7f).clip(RoundedCornerShape(8.dp)).background(Color(0xFF141416))) {
                com.anibiar.app.ui.home.PosterImage(url = m.posterUrl, contentDescription = m.displayTitle, maxSide = 300, modifier = Modifier.fillMaxSize())
                m.averageScore?.takeIf { it > 0 }?.let { sc ->
                    val col = if (sc > 75) Color(0xFF4CAF50) else if (sc > 50) Color(0xFFFFC107) else Color(0xFFF44336)
                    Row(Modifier.align(Alignment.BottomStart).padding(4.dp).clip(RoundedCornerShape(4.dp)).background(Color.Black.copy(alpha = 0.8f)).padding(horizontal = 4.dp, vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(com.anibiar.app.ui.theme.FeatherIcons.Star, null, tint = col, modifier = Modifier.size(8.dp))
                        Spacer(Modifier.width(2.dp))
                        Text("$sc%", style = inter(8f, FontWeight.Bold), color = Color.White)
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(m.displayTitle, style = inter(13f, FontWeight.Bold), color = Color.White, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                val parts = listOfNotNull(m.format?.replace('_', ' '), m.seasonYear?.toString(), if (m.format != "MOVIE") m.episodes?.let { "$it Eps" } else null)
                if (parts.isNotEmpty()) Text(parts.joinToString(" • "), style = inter(11f), color = Color.White.copy(alpha = 0.6f), maxLines = 1, modifier = Modifier.padding(top = 2.dp))
                if (!m.genres.isNullOrEmpty()) Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    m.genres.take(3).forEach { g ->
                        Text(g, style = inter(9f), color = Color.White.copy(alpha = 0.7f), modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = 0.05f)).border(0.5.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
                m.cleanDescription.takeIf { it.isNotBlank() }?.let { Text(it, style = inter(11f, lineHeight = 14f), color = Color.White.copy(alpha = 0.45f), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp)) }
            }
        }
    }
}

@Composable
private fun ToolBar(activeCount: Int, expanded: Boolean, onApply: () -> Unit, onReset: () -> Unit, onToggle: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(40.dp).clip(shape).border(1.dp, Ani.Border, shape),
    ) {
        ToolCell(AniIcons.Checklist, if (activeCount > 0) Color.White else Ani.Soft, Modifier.weight(1f), onApply)
        Box(Modifier.width(1.dp).fillMaxSize().background(Ani.Border))
        ToolCell(AniIcons.Refresh, if (activeCount > 0) Color.White else Ani.Muted, Modifier.weight(1f), onReset)
        Box(Modifier.width(1.dp).fillMaxSize().background(Ani.Border))
        ToolCell(AniIcons.ArrowDown, Ani.Soft, Modifier.weight(1f), onToggle, turn = if (expanded) 180f else 0f)
    }
}

@Composable
private fun ToolCell(icon: ImageVector, tint: Color, modifier: Modifier, onClick: () -> Unit, turn: Float = 0f) {
    val angle by animateFloatAsState(turn, tween(300), label = "toolTurn")
    Box(modifier.fillMaxSize().clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = angle })
    }
}

private class FilterDropdown(
    val label: String,
    val icon: ImageVector?,
    val options: List<Pair<String, String>>,
    val selected: Set<String>,
    val multi: Boolean,
    val sortStyle: Boolean = false,
    val clearable: Boolean = true,
    val onChange: (Set<String>) -> Unit,
)

@Composable
private fun FilterRow(a: FilterDropdown, b: FilterDropdown?) {
    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.weight(1f)) { FilterBox(a) }
        Box(Modifier.weight(1f)) { if (b != null) FilterBox(b) }
    }
}

@Composable
private fun FilterBox(f: FilterDropdown) {
    var open by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    val active = f.selected.isNotEmpty() && !(f.sortStyle && !f.clearable && f.selected == setOf("NON"))
    val shown = when {
        f.selected.isEmpty() -> f.label
        f.multi && f.selected.size > 1 -> "${f.label} · ${f.selected.size}"
        else -> f.options.firstOrNull { it.first == f.selected.first() }?.second ?: f.label
    }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .height(40.dp)
                .clip(shape)
                .border(1.dp, if (active) Ani.Lavender.copy(alpha = 0.7f) else Ani.Border, shape)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { open = true }
                  .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (f.icon != null) {
                Icon(f.icon, null, tint = if (active) Ani.Lavender else Ani.Muted, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(
                shown, style = inter(14f), color = if (active) Color.White else Ani.Muted,
                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            val turn by animateFloatAsState(if (open) 180f else 0f, tween(250), label = "boxTurn")
            Icon(AniIcons.ArrowDown, null, tint = Ani.Muted, modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = turn })
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier.background(com.anibiar.app.ui.theme.GlassFill).heightIn(max = 360.dp).border(1.dp, Ani.Border, RoundedCornerShape(8.dp)),
        ) {
            if (f.clearable && f.selected.isNotEmpty()) {
                DropdownMenuItem(
                    text = { Text("Any", style = inter(15f), color = Ani.Muted) },
                    onClick = { f.onChange(emptySet()); open = false },
                )
            }
            f.options.forEach { (value, label) ->
                val on = value in f.selected
                DropdownMenuItem(
                    text = { Text(label, style = inter(15f), color = if (on) Ani.Lavender else Color.White) },
                    trailingIcon = if (on) {
                        { Icon(AniIcons.Check, null, tint = Ani.Lavender, modifier = Modifier.size(18.dp)) }
                    } else null,
                    onClick = {
                        if (f.multi) f.onChange(if (on) f.selected - value else f.selected + value)
                        else { f.onChange(setOf(value)); open = false }
                    },
                )
            }
        }
    }
}

@Composable
private fun OutlineButton(label: String, enabled: Boolean, onClick: () -> Unit, leading: ImageVector? = null, trailing: ImageVector? = null) {
    val shape = RoundedCornerShape(50)
    val tint = if (enabled) Color.White else Ani.Muted.copy(alpha = 0.6f)
    Row(
        Modifier.height(42.dp).width(if (leading != null || trailing != null) 116.dp else 90.dp)
            .then(if (enabled) Modifier.glass(shape) else Modifier.clip(shape).border(1.dp, Ani.Border, shape))
            .clickable(enabled = enabled, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (leading != null) Icon(leading, null, tint = tint, modifier = Modifier.size(18.dp))
        Text(label, style = inter(14f, FontWeight.Medium), color = tint, modifier = Modifier.padding(horizontal = 4.dp))
        if (trailing != null) Icon(trailing, null, tint = tint, modifier = Modifier.size(18.dp))
    }
}
