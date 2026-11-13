package com.anibiar.app.ui.watch

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.anibiar.app.data.Media
import com.anibiar.app.data.displayTitle
import com.anibiar.app.data.posterUrl
import com.anibiar.app.ui.theme.FeatherIcons
import com.anibiar.app.ui.theme.inter

private val CardLine = Color(0x24FFFFFF)
private val CardBg = Color(0xEB131317)
private val Mute = Color(0xFF9B98A0)

data class SeasonEntry(val id: Int, val title: String, val image: String?, val label: String, val part: String, val year: Int?)

private val SEASON_FORMATS = setOf("TV", "TV_SHORT", "ONA", "MOVIE", "OVA", "SPECIAL")
private val PART_RE = Regex("\\b(?:part|cour)\\s*(\\d+)\\b|\\b(\\d+)(?:st|nd|rd|th)\\s+cour\\b", RegexOption.IGNORE_CASE)

/** Season 1, Season 2, Part 2, Movie ... for the whole franchise, oldest first (same naming as the web app). */
fun buildSeasons(root: Media, franchise: List<Media>): List<SeasonEntry> {
    fun sortKey(m: Media) = (m.startDate?.year ?: 9999) * 10000 + (m.startDate?.month ?: 12) * 100 + (m.startDate?.day ?: 31)
    val all = (listOf(root) + franchise).distinctBy { it.id }
        .filter { (it.format in SEASON_FORMATS) || it.id == root.id }
        .sortedWith(compareBy<Media> { sortKey(it) }.thenBy { it.id })
    if (all.size < 2) return emptyList()
    val labels = HashMap<Int, String>(); val parts = HashMap<Int, String>()
    val groups = ArrayList<MutableList<Media>>()
    for (m in all) {
        when (m.format) {
            "MOVIE" -> labels[m.id] = "Movie"
            "OVA" -> labels[m.id] = "OVA"
            "SPECIAL" -> labels[m.id] = "Specials"
            else -> {
                val mt = PART_RE.find(m.displayTitle)
                val partNo = mt?.let { (it.groupValues[1].ifEmpty { it.groupValues[2] }).toIntOrNull() } ?: 0
                if (partNo > 1 && groups.isNotEmpty()) { groups.last().add(m); parts[m.id] = "Part $partNo" }
                else { groups.add(mutableListOf(m)); if (partNo == 1) parts[m.id] = "Part 1" }
            }
        }
    }
    groups.forEachIndexed { i, g ->
        g.forEach { labels[it.id] = "Season ${i + 1}" }
        if (g.size > 1) { var next = 1; for (e in g) { if (parts[e.id] == null) parts[e.id] = "Part $next"; next = parts[e.id]!!.filter { it.isDigit() }.toInt() + 1 } }
    }
    return all.map { SeasonEntry(it.id, it.displayTitle, it.posterUrl, labels[it.id].orEmpty(), parts[it.id].orEmpty(), it.seasonYear ?: it.startDate?.year) }
}

/** A rounded card like the web app's `Card`. */
@Composable
fun WebCard(modifier: Modifier = Modifier, padding: Dp = 16.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CardBg).border(1.5.dp, CardLine, RoundedCornerShape(16.dp)).padding(padding), content = content)
}


@Composable
fun SeasonsCard(seasons: List<SeasonEntry>, currentId: Int, onOpen: (Int) -> Unit) {
    WebCard {
        Row(Modifier.padding(bottom = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(FeatherIcons.List, null, tint = Color.White, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(10.dp))
            Text("Seasons", style = inter(16.8f, FontWeight.SemiBold), color = Color.White)
        }
        BoxWithConstraints {
            val cols = if (maxWidth > 560.dp) 3 else 2
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                seasons.chunked(cols).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { se ->
                            val cur = se.id == currentId; val s = RoundedCornerShape(13.dp)
                            Box(
                                Modifier.weight(1f).heightIn(min = 80.dp).clip(s).border(1.5.dp, if (cur) Color(0x8CFFFFFF) else Color(0x38FFFFFF), s).clickable(enabled = !cur) { onOpen(se.id) },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (se.image != null) AsyncImage(se.image, null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
                                Box(Modifier.matchParentSize().background(Color(0xA60A0A0E)))
                                Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(se.label, style = inter(16.8f, FontWeight.SemiBold).copy(shadow = androidx.compose.ui.graphics.Shadow(Color(0x99000000), androidx.compose.ui.geometry.Offset(0f, 2f), 8f)), color = Color.White, textAlign = TextAlign.Center)
                                    if (se.part.isNotEmpty()) Text(se.part, style = inter(13f), color = Color(0xB3FFFFFF))
                                    Text(se.year?.toString().orEmpty(), style = inter(13f), color = Color(0xB3FFFFFF))
                                }
                                if (cur) Text("Current", style = inter(11f, FontWeight.SemiBold), color = Color.White,
                                    modifier = Modifier.align(Alignment.TopStart).padding(6.dp).clip(RoundedCornerShape(7.dp)).background(Color(0xD90A0A0E)).padding(horizontal = 8.dp, vertical = 3.dp))
                            }
                        }
                        repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Chip(icon: ImageVector? = null, text: String) {
    Row(Modifier.clip(RoundedCornerShape(7.dp)).background(Color(0x17FFFFFF)).padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) { Icon(icon, null, tint = Color(0xD1FFFFFF), modifier = Modifier.size(11.5.dp)); Spacer(Modifier.width(4.dp)) }
        Text(text.uppercase(), style = inter(11.2f, FontWeight.SemiBold, lineHeight = 11.2f), color = Color(0xD1FFFFFF), maxLines = 1)
    }
}

private val RELATION = mapOf(
    "SEQUEL" to "Sequel", "PREQUEL" to "Prequel", "SIDE_STORY" to "Side story", "SPIN_OFF" to "Spin-off", "PARENT" to "Parent story",
    "ALTERNATIVE" to "Alternative", "SUMMARY" to "Summary", "CHARACTER" to "Character", "OTHER" to "Other", "COMPILATION" to "Compilation", "CONTAINS" to "Contains",
)
private fun statusDot(s: String?) = when (s) {
    "RELEASING" -> Color(0xFF22C55E); "FINISHED" -> Color(0xFF0EA5E9); "NOT_YET_RELEASED" -> Color(0xFFFBBF24)
    "CANCELLED" -> Color(0xFFF87171); "HIATUS" -> Color(0xFFFB923C); else -> Color(0xFF0EA5E9)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MiniRow(m: Media, relation: String?, onClick: () -> Unit) {
    val s = RoundedCornerShape(13.dp)
    Box(Modifier.fillMaxWidth().heightIn(min = 90.dp).height(IntrinsicSize.Min).clip(s).background(Color(0xFF18181C)).clickable(onClick = onClick)) {
        m.posterUrl?.let { AsyncImage(it, null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize()) }
        Box(Modifier.matchParentSize().background(Color(0xF218181C)))
        Row(Modifier.fillMaxWidth()) {
            Box(Modifier.width(66.dp).fillMaxHeight().background(Color(0xFF2A2A2E))) {
                m.posterUrl?.let { AsyncImage(it, null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize()) }
            }
            Column(Modifier.weight(1f).padding(start = 14.dp, end = 11.dp, top = 10.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterVertically)) {
                Row(verticalAlignment = Alignment.Top) {
                    Box(Modifier.padding(top = 6.dp).size(9.dp).clip(CircleShape).background(statusDot(m.status)))
                    Spacer(Modifier.width(9.dp))
                    Text(m.displayTitle, style = inter(15f, FontWeight.SemiBold, lineHeight = 19.5f), color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    m.format?.let { Chip(null, it.replace('_', ' ')) }
                    (m.seasonYear ?: m.startDate?.year)?.let { Chip(FeatherIcons.Calendar, it.toString()) }
                    m.averageScore?.takeIf { it > 0 }?.let { Chip(FeatherIcons.Star, it.toString()) }
                }
                relation?.let { Text(it, style = inter(12f, FontWeight.SemiBold), color = WebAccent) }
            }
        }
    }
}

/** Collapsible list card ("› RELATED"), like the web app's ListCard. */
@Composable
fun ListCard(title: String, startOpen: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    var open by remember { mutableStateOf(startOpen) }
    val s = RoundedCornerShape(16.dp)
    Column(Modifier.fillMaxWidth().clip(s).border(1.5.dp, Color(0x38FFFFFF), s).padding(14.dp)) {
        Row(Modifier.fillMaxWidth().clickable { open = !open }.padding(start = 3.dp, end = 3.dp, top = 5.dp, bottom = if (open) 13.dp else 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(FeatherIcons.ChevronRight, null, tint = Color.White, modifier = Modifier.size(20.dp).rotate(if (open) 90f else 0f))
            Spacer(Modifier.width(8.dp))
            Text(title.uppercase(), style = inter(16f, FontWeight.SemiBold).copy(letterSpacing = 0.64.sp), color = Color.White)
        }
        AnimatedVisibility(open) { Column(verticalArrangement = Arrangement.spacedBy(8.dp), content = content) }
    }
}

private val ANIME_FORMATS = setOf("TV", "TV_SHORT", "MOVIE", "SPECIAL", "OVA", "ONA", "MUSIC")

@Composable
fun RelatedSections(anime: Media?, onOpen: (Int) -> Unit) {
    val relations = anime?.relations?.edges.orEmpty().filter { it.node != null && it.node.type == "ANIME" && (it.node.format in ANIME_FORMATS) }
    val recs = anime?.recommendations?.nodes.orEmpty().mapNotNull { it.mediaRecommendation }.filter { it.format in ANIME_FORMATS }
    var more by remember { mutableStateOf(false) }
    if (relations.isNotEmpty()) ListCard("Related") {
        relations.forEach { e -> MiniRow(e.node!!, e.relationType?.let { RELATION[it] ?: it }) { onOpen(e.node!!.id) } }
    }
    if (recs.isNotEmpty()) ListCard("Recommendations") {
        (if (more) recs else recs.take(4)).forEach { r -> MiniRow(r, null) { onOpen(r.id) } }
        if (recs.size > 4) Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp)).background(Color(0x0DFFFFFF)).clickable { more = !more }.padding(11.dp), contentAlignment = Alignment.Center) {
            Icon(FeatherIcons.ChevronDown, null, tint = Mute, modifier = Modifier.size(20.dp).rotate(if (more) 180f else 0f))
        }
    }
}
