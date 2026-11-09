package com.anibiar.app.ui.explore

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anibiar.app.AniBiarApp
import com.anibiar.app.data.Media
import com.anibiar.app.data.displayTitle
import com.anibiar.app.data.posterUrl
import com.anibiar.app.ui.home.PosterImage
import com.anibiar.app.ui.theme.Ani
import com.anibiar.app.ui.theme.GlassBack
import com.anibiar.app.ui.theme.inter
import kotlinx.serialization.json.*

private class Mood(val name: String, val genres: List<String>)
private val MOODS = listOf(
    Mood("Hype", listOf("Action", "Adventure")), Mood("Chill", listOf("Slice of Life")), Mood("Funny", listOf("Comedy")),
    Mood("Emotional", listOf("Drama")), Mood("Mind-bending", listOf("Psychological", "Mystery", "Sci-Fi")),
    Mood("Romantic", listOf("Romance")), Mood("Scary", listOf("Horror", "Thriller")), Mood("Magical", listOf("Fantasy", "Supernatural")),
)
private class Length(val name: String, val ok: (Media) -> Boolean, val formats: List<String>? = null)
private val LENGTHS = listOf(
    Length("Any length", { true }),
    Length("Quick (up to 13 ep)", { (it.episodes ?: 99) in 1..13 }),
    Length("One season (up to 26)", { (it.episodes ?: 99) in 1..26 }),
    Length("Long (26+)", { (it.episodes ?: 0) > 26 }),
    Length("Movies", { true }, listOf("MOVIE")),
)
private val GENRES = listOf(
    "Action", "Adventure", "Comedy", "Drama", "Ecchi", "Fantasy", "Horror", "Mahou Shoujo", "Mecha", "Music", "Mystery",
    "Psychological", "Romance", "Sci-Fi", "Slice of Life", "Sports", "Supernatural", "Thriller",
)
private val SORTS = listOf("Popular" to "POPULARITY_DESC", "Top rated" to "SCORE_DESC", "Trending" to "TRENDING_DESC", "Newest" to "START_DATE_DESC")

/** Pick a mood and how much time you have, or browse by genre. Results come from AniList and are filtered on the phone. */
@Composable
fun ExploreScreen(onBack: () -> Unit, onOpen: (Int) -> Unit, backEnabled: Boolean = true) {
    BackHandler(enabled = backEnabled, onBack = onBack)
    val app = LocalContext.current.applicationContext as AniBiarApp
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var mood by rememberSaveable { mutableIntStateOf(-1) }
    var length by rememberSaveable { mutableIntStateOf(0) }
    var genres by remember { mutableStateOf(setOf<String>()) }
    var sort by rememberSaveable { mutableIntStateOf(0) }
    var results by remember { mutableStateOf<List<Media>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(tab, mood, length, genres, sort) {
        val g = if (tab == 0) MOODS.getOrNull(mood)?.genres.orEmpty() else genres.toList()
        if (g.isEmpty()) { results = emptyList(); return@LaunchedEffect }
        loading = true; failed = false
        val vars = buildJsonObject {
            put("page", 1); put("perPage", 50)
            put("sort", JsonArray(listOf(JsonPrimitive(if (tab == 0) "SCORE_DESC" else SORTS[sort].second))))
            put("genres", JsonArray(g.map { JsonPrimitive(it) }))
            LENGTHS[length].formats?.takeIf { tab == 0 }?.let { f -> put("formats", JsonArray(f.map { JsonPrimitive(it) })) }
            put("adult", false)
            if (tab == 0) put("score", 65)
        }
        runCatching { app.repository.search(vars).media }
            .onSuccess { list -> app.catalog.add(list); results = if (tab == 0) list.filter(LENGTHS[length].ok) else list }
            .onFailure { failed = true; results = emptyList() }
        loading = false
    }

    Column(Modifier.fillMaxSize().background(Ani.Onyx).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassBack(onClick = onBack)
            Spacer(Modifier.width(12.dp))
            Text("Explore", style = inter(20f, FontWeight.SemiBold), color = Color.White)
        }
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("By mood", "By genre").forEachIndexed { i, t -> Chip(t, tab == i) { tab = i } }
        }
        Spacer(Modifier.height(10.dp))
        if (tab == 0) {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(MOODS.size) { i -> Chip(MOODS[i].name, mood == i) { mood = if (mood == i) -1 else i } }
            }
            Spacer(Modifier.height(8.dp))
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(LENGTHS.size) { i -> Chip(LENGTHS[i].name, length == i) { length = i } }
            }
        } else {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(GENRES) { g -> Chip(g, g in genres) { genres = if (g in genres) genres - g else genres + g } }
            }
            Spacer(Modifier.height(8.dp))
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(SORTS.size) { i -> Chip(SORTS[i].first, sort == i) { sort = i } }
            }
        }
        Spacer(Modifier.height(12.dp))
        when {
            loading -> Text("Finding anime…", style = inter(15f), color = Ani.Muted, modifier = Modifier.padding(24.dp))
            failed -> Text("Couldn't load. Check your connection.", style = inter(15f), color = Ani.Muted, modifier = Modifier.padding(24.dp))
            results.isEmpty() -> Text(if (tab == 0 && mood < 0 || tab == 1 && genres.isEmpty()) "Pick a ${if (tab == 0) "mood" else "genre"} to start." else "Nothing matched. Try another length.", style = inter(15f), color = Ani.Muted, modifier = Modifier.padding(24.dp))
            else -> LazyVerticalGrid(
                GridCells.Adaptive(110.dp), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 40.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(results, key = { it.id }) { m ->
                    Column(Modifier.clickable { onOpen(m.id) }) {
                        PosterImage(m.posterUrl, m.displayTitle, maxSide = 400, modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(12.dp)).background(Ani.Item))
                        Spacer(Modifier.height(6.dp))
                        Text(m.displayTitle, style = inter(13f, FontWeight.Medium, lineHeight = 17f), color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(listOfNotNull(m.averageScore?.let { "★ ${it / 10.0}" }, m.episodes?.let { "$it ep" }).joinToString(" · "), style = inter(12f), color = Ani.Muted)
                    }
                }
            }
        }
    }
}

@Composable
private fun Chip(text: String, on: Boolean, onClick: () -> Unit) {
    Box(Modifier.clip(RoundedCornerShape(20.dp)).background(if (on) Ani.Lavender else Color(0xFF1B1B1F)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp)) {
        Text(text, style = inter(13.5f, FontWeight.Medium), color = if (on) Color.Black else Color.White)
    }
}
