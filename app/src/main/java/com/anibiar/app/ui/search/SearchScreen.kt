package com.anibiar.app.ui.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import com.anibiar.app.data.airedEpisodes
import com.anibiar.app.ui.home.MediaGridCard
import com.anibiar.app.ui.home.clearOnEmptyTap
import com.anibiar.app.ui.theme.Ani
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
fun SearchScreen(onOpen: (Int) -> Unit, onBack: () -> Unit, viewModel: SearchViewModel = viewModel()) {
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
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 90.dp,
            ),
        ) {
            item(key = "bar") {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        AniIcons.ArrowBack, contentDescription = "Back", tint = Ani.Soft,
                        modifier = Modifier.size(32.dp).clip(RoundedCornerShape(50)).clickable(onClick = onBack).padding(4.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    SearchField(
                        value = state.query,
                        onValue = viewModel::setQuery,
                        onSubmit = { focus.clearFocus(); viewModel.submit() },
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(14.dp))
            }

            item(key = "tools") {
                ToolBar(
                    activeCount = filters.activeCount,
                    expanded = showFilters,
                    onApply = { focus.clearFocus(); viewModel.submit() },
                    onReset = viewModel::reset,
                    onToggle = { showFilters = !showFilters },
                )
                AnimatedVisibility(showFilters, enter = expandVertically(), exit = shrinkVertically()) {
                    Column(Modifier.padding(horizontal = 16.dp).padding(top = 12.dp)) {
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
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Page ${state.page}", style = inter(17f), color = Ani.Muted, modifier = Modifier.weight(1f))
                    PagerButtons(state.page > 1, state.hasNext, { viewModel.goTo(state.page - 1) }, { viewModel.goTo(state.page + 1) })
                }
                Spacer(Modifier.height(18.dp))
            }

            when {
                state.loading && state.results.isEmpty() -> item(key = "loading") { Hint("Searching…") }
                state.error != null && state.results.isEmpty() -> item(key = "error") {
                    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.error.orEmpty(), style = inter(15f), color = Ani.Muted)
                        Spacer(Modifier.height(12.dp))
                        OutlineButton("Retry", true, viewModel::retry)
                    }
                }
                state.results.isEmpty() -> item(key = "empty") {
                    Hint(if (filters.language == "DUB") "Dubbed releases aren't available yet." else "No anime found.")
                }
                else -> {
                    val rows = state.results.chunked(columns)
                    items(rows.size, key = { "r${state.page}-$it-${rows[it].first().id}" }) { index ->
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(13.dp),
                        ) {
                            rows[index].forEach { media ->
                                MediaGridCard(
                                    media = media,
                                    leftText = "",
                                    rightText = "",
                                    overlay = false,
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
                        Spacer(Modifier.height(16.dp))
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
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier.height(48.dp).clip(shape).border(1.dp, Ani.Border, shape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f).padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) Text("Search anime…", style = inter(16f), color = Ani.Muted)
            BasicTextField(
                value = value,
                onValueChange = onValue,
                singleLine = true,
                textStyle = inter(16f).copy(color = Color.White),
                cursorBrush = SolidColor(Ani.Lavender),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Box(Modifier.width(1.dp).fillMaxSize().background(Ani.Border))
        Box(
            Modifier.width(52.dp).fillMaxSize().clickable(onClick = onSubmit),
            contentAlignment = Alignment.Center,
        ) { Icon(AniIcons.Search, contentDescription = "Search", tint = Ani.Muted, modifier = Modifier.size(24.dp)) }
    }
}

@Composable
private fun ToolBar(activeCount: Int, expanded: Boolean, onApply: () -> Unit, onReset: () -> Unit, onToggle: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(48.dp).clip(shape).border(1.dp, Ani.Border, shape),
    ) {
        ToolCell(AniIcons.Checklist, if (activeCount > 0) Color.White else Ani.Soft, Modifier.weight(1f), onApply)
        Box(Modifier.width(1.dp).fillMaxSize().background(Ani.Border))
        ToolCell(AniIcons.Refresh, if (activeCount > 0) Color.White else Ani.Muted, Modifier.weight(1f), onReset)
        Box(Modifier.width(1.dp).fillMaxSize().background(Ani.Border))
        ToolCell(if (expanded) AniIcons.ArrowUp else AniIcons.ArrowDown, Ani.Soft, Modifier.weight(1f), onToggle)
    }
}

@Composable
private fun ToolCell(icon: ImageVector, tint: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.fillMaxSize().clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
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
    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.weight(1f)) { FilterBox(a) }
        Box(Modifier.weight(1f)) { if (b != null) FilterBox(b) }
    }
}

@Composable
private fun FilterBox(f: FilterDropdown) {
    var open by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
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
                .height(48.dp)
                .clip(shape)
                .border(1.dp, if (active) Ani.Lavender.copy(alpha = 0.7f) else Ani.Border, shape)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { open = true }
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (f.icon != null) {
                Icon(f.icon, null, tint = if (active) Ani.Lavender else Ani.Muted, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
            }
            Text(
                shown, style = inter(16f), color = if (active) Color.White else Ani.Muted,
                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            Icon(AniIcons.ArrowDown, null, tint = Ani.Muted, modifier = Modifier.size(22.dp))
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier.background(Ani.Panel).heightIn(max = 360.dp).border(1.dp, Ani.Border, RoundedCornerShape(8.dp)),
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
private fun PagerButtons(canPrev: Boolean, canNext: Boolean, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SquareButton(AniIcons.ChevronLeft, canPrev, onPrev)
        SquareButton(AniIcons.ChevronRight, canNext, onNext)
    }
}

@Composable
private fun SquareButton(icon: ImageVector, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier.size(width = 58.dp, height = 44.dp).clip(shape)
            .border(1.dp, if (enabled) Color.White.copy(alpha = 0.7f) else Ani.Border, shape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = if (enabled) Color.White else Ani.Muted.copy(alpha = 0.5f), modifier = Modifier.size(24.dp)) }
}

@Composable
private fun OutlineButton(label: String, enabled: Boolean, onClick: () -> Unit, leading: ImageVector? = null, trailing: ImageVector? = null) {
    val shape = RoundedCornerShape(12.dp)
    val tint = if (enabled) Color.White else Ani.Muted.copy(alpha = 0.6f)
    Row(
        Modifier.height(54.dp).width(if (leading != null || trailing != null) 150.dp else 110.dp).clip(shape)
            .border(1.dp, if (enabled) Color.White.copy(alpha = 0.75f) else Ani.Border, shape)
            .clickable(enabled = enabled, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (leading != null) Icon(leading, null, tint = tint, modifier = Modifier.size(22.dp))
        Text(label, style = inter(17f, FontWeight.Medium), color = tint, modifier = Modifier.padding(horizontal = 6.dp))
        if (trailing != null) Icon(trailing, null, tint = tint, modifier = Modifier.size(22.dp))
    }
}
