package com.anibiar.app.ui.admin

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anibiar.app.AniBiarApp
import com.anibiar.app.BuildConfig
import com.anibiar.app.data.AdminContent
import com.anibiar.app.data.Http
import com.anibiar.app.social.LiveStats
import com.anibiar.app.ui.theme.Ani
import com.anibiar.app.ui.theme.GlassBack
import com.anibiar.app.ui.theme.inter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.Request

const val ADMIN_EMAIL = "yurischevalier@gmail.com"
private val json = Json { ignoreUnknownKeys = true }
private val PANEL = Color(0xFF1B1B1F)

/** Owner-only panel. The app hides it for other accounts; the database also refuses every admin write from anyone else. */
@Composable
fun AdminScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var tab by remember { mutableStateOf(0) }
    Column(Modifier.fillMaxSize().background(Color(0xFF0B0B0D)).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassBack(onBack)
            Spacer(Modifier.width(12.dp))
            Text("Admin", style = inter(22f, FontWeight.ExtraBold), color = Color.White)
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Stats", "Anime", "Streams", "Updates").forEachIndexed { i, t -> Pill(t, tab == i) { tab = i } }
        }
        Spacer(Modifier.height(10.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 60.dp)) {
            when (tab) {
                0 -> StatsTab()
                1 -> AnimeTab()
                2 -> StreamsTab()
                else -> UpdatesTab()
            }
        }
    }
}

@Composable private fun Pill(text: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(if (on) Color.White else Ani.ButtonDark).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 9.dp),
    ) { Text(text, style = inter(14f, FontWeight.SemiBold), color = if (on) Color.Black else Color.White) }
}

@Composable private fun Btn(text: String, primary: Boolean = false, danger: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(if (primary) Color.White else Ani.ButtonDark).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = inter(13.5f, FontWeight.SemiBold), color = if (primary) Color.Black else if (danger) Color(0xFFFF8A80) else Color.White) }
}

@Composable private fun Card(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(18.dp)).background(PANEL).border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(18.dp)).padding(14.dp)) { content() }
}

@Composable private fun Heading(t: String) { Text(t, style = inter(17f, FontWeight.Bold), color = Color.White, modifier = Modifier.padding(top = 14.dp, bottom = 8.dp)) }
@Composable private fun Note(t: String) { Text(t, style = inter(12.5f, lineHeight = 18f), color = Ani.Muted) }

@Composable private fun Field(label: String, value: String, onChange: (String) -> Unit, lines: Int = 1, number: Boolean = false) {
    Text(label, style = inter(12f), color = Ani.Muted, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF0F0F12)).border(1.dp, Ani.Border, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 10.dp)) {
        BasicTextField(
            value, onChange, singleLine = lines == 1, minLines = lines, maxLines = if (lines == 1) 1 else 12,
            textStyle = inter(14.5f).copy(color = Color.White), cursorBrush = SolidColor(Ani.Lavender),
            keyboardOptions = if (number) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable private fun Check(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp).clickable { onChange(!on) }, verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 2.dp).width(20.dp).height(20.dp).clip(RoundedCornerShape(6.dp)).background(if (on) Color.White else Color.Transparent).border(2.dp, if (on) Color.White else Ani.Border, RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
            if (on) Text("✓", style = inter(12f, FontWeight.Bold), color = Color.Black)
        }
        Spacer(Modifier.width(10.dp))
        Text(label, style = inter(13.5f, lineHeight = 19f), color = Ani.Soft)
    }
}

@Composable private fun Badge(t: String, color: Color) {
    Text(t, style = inter(11.5f, FontWeight.SemiBold), color = color, modifier = Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.14f)).padding(horizontal = 9.dp, vertical = 3.dp))
}

private fun JsonObject.s(k: String) = this[k]?.jsonPrimitive?.contentOrNull?.takeIf { it != "null" }.orEmpty()
private fun JsonObject.i(k: String) = this[k]?.jsonPrimitive?.intOrNull
private fun JsonObject.l(k: String) = this[k]?.jsonPrimitive?.longOrNull ?: 0L
private fun JsonObject.b(k: String) = this[k]?.jsonPrimitive?.booleanOrNull ?: false

@Composable private fun rememberRest(): suspend (String, String, String?, String?) -> Pair<Int, String> {
    val social = (LocalContext.current.applicationContext as AniBiarApp).social
    return remember { { m, p, b, pr -> social.rest(m, p, b, pr) } }
}

private fun rows(text: String): List<JsonObject> = runCatching { (json.parseToJsonElement(text) as? JsonArray).orEmpty().map { it.jsonObject } }.getOrDefault(emptyList())

/* ---------------- stats ---------------- */
@Composable private fun StatsTab() {
    val rest0 = rememberRest(); val rest: suspend (String, String, String?) -> Pair<Int, String> = { m, p, b -> rest0(m, p, b, "return=minimal") }
    val online by LiveStats.online.collectAsStateWithLifecycle()
    val watching by LiveStats.watching.collectAsStateWithLifecycle()
    var stats by remember { mutableStateOf<JsonObject?>(null) }
    var err by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            val (c, t) = rest("POST", "/rest/v1/rpc/admin_stats", "{}")
            if (c in 200..299) { stats = runCatching { json.parseToJsonElement(t).jsonObject }.getOrNull(); err = "" } else err = "Couldn't load stats ($c)"
            kotlinx.coroutines.delay(30_000)
        }
    }
    Heading("Right now")
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.weight(1f)) { Stat("Online", online.toString(), "apps open now") }
        Box(Modifier.weight(1f)) { Stat("Watching", watching.toString(), "playing a video") }
    }
    Heading("Users")
    if (err.isNotEmpty()) Note(err)
    val st = stats
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.weight(1f)) { Stat("Registered", st?.i("users_total")?.toString() ?: "…") }
        Box(Modifier.weight(1f)) { Stat("New 24h", st?.i("users_24h")?.toString() ?: "…") }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.weight(1f)) { Stat("New 7 days", st?.i("users_7d")?.toString() ?: "…") }
        Box(Modifier.weight(1f)) { Stat("Messages 24h", st?.i("messages_24h")?.toString() ?: "…") }
    }
    Heading("App health")
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.weight(1f)) { Stat("Opens 24h", st?.i("reports_24h")?.toString() ?: "…") }
        Box(Modifier.weight(1f)) { Stat("Crashes 7d", st?.i("crashes_7d")?.toString() ?: "…") }
    }
    Card {
        Text("Versions (7 days)", style = inter(14f, FontWeight.SemiBold), color = Color.White)
        val v = (st?.get("versions") as? JsonArray).orEmpty().map { it.jsonObject }
        if (v.isEmpty()) Note("No reports yet.")
        v.forEach { Row(Modifier.fillMaxWidth().padding(top = 6.dp)) { Text(it.s("version"), style = inter(14f), color = Ani.Soft, modifier = Modifier.weight(1f)); Text(it.s("n"), style = inter(14f, FontWeight.Bold), color = Color.White) } }
    }
    Card {
        Text("Devices (7 days)", style = inter(14f, FontWeight.SemiBold), color = Color.White)
        val d = (st?.get("devices") as? JsonArray).orEmpty().map { it.jsonObject }
        if (d.isEmpty()) Note("No reports yet.")
        d.forEach { Row(Modifier.fillMaxWidth().padding(top = 6.dp)) { Text(it.s("device"), style = inter(14f), color = Ani.Soft, modifier = Modifier.weight(1f)); Text(it.s("n"), style = inter(14f, FontWeight.Bold), color = Color.White) } }
    }
    Note("Online and Watching count apps that are open right now, updated live. Registered counts accounts, not installs.")
}

@Composable private fun Stat(label: String, value: String, sub: String = "") {
    Column(Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(18.dp)).background(PANEL).border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(18.dp)).padding(14.dp)) {
        Text(label, style = inter(12.5f), color = Ani.Muted)
        Text(value, style = inter(28f, FontWeight.ExtraBold), color = Color.White)
        if (sub.isNotEmpty()) Text(sub, style = inter(11.5f), color = Ani.Muted)
    }
}

/* ---------------- anime ---------------- */
@Composable private fun AnimeTab() {
    val rest0 = rememberRest(); val rest: suspend (String, String, String?) -> Pair<Int, String> = { m, p, b -> rest0(m, p, b, "return=minimal") }; val scope = rememberCoroutineScope(); val ctx = LocalContext.current
    var list by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var editing by remember { mutableStateOf<Int?>(null) }
    var title by remember { mutableStateOf("") }; var desc by remember { mutableStateOf("") }
    var cover by remember { mutableStateOf("") }; var banner by remember { mutableStateOf("") }
    var genres by remember { mutableStateOf("") }; var year by remember { mutableStateOf("") }
    var eps by remember { mutableStateOf("") }; var score by remember { mutableStateOf("") }
    var format by remember { mutableStateOf("TV") }; var status by remember { mutableStateOf("RELEASING") }
    var published by remember { mutableStateOf(false) }
    fun toast(m: String) = Toast.makeText(ctx, m, Toast.LENGTH_SHORT).show()
    fun load() { scope.launch { val (c, t) = rest("GET", "/rest/v1/admin_anime?select=*&order=id.desc", null); if (c in 200..299) list = rows(t) else toast("Couldn't load ($c)") } }
    LaunchedEffect(Unit) { load() }
    fun clear() { editing = null; title = ""; desc = ""; cover = ""; banner = ""; genres = ""; year = ""; eps = ""; score = ""; format = "TV"; status = "RELEASING"; published = false }

    Heading(if (editing == null) "Add anime" else "Edit anime")
    Card {
        Field("Title", title, { title = it })
        Field("Description", desc, { desc = it }, lines = 4)
        Field("Cover image link", cover, { cover = it })
        Field("Banner image link", banner, { banner = it })
        Field("Genres (comma separated)", genres, { genres = it })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) { Field("Year", year, { year = it.filter(Char::isDigit).take(4) }, number = true) }
            Box(Modifier.weight(1f)) { Field("Episodes", eps, { eps = it.filter(Char::isDigit).take(4) }, number = true) }
            Box(Modifier.weight(1f)) { Field("Score 0-100", score, { score = it.filter(Char::isDigit).take(3) }, number = true) }
        }
        Text("Format", style = inter(12f), color = Ani.Muted, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { listOf("TV", "MOVIE", "OVA", "ONA", "SPECIAL").forEach { Pill(it, format == it) { format = it } } }
        Text("Status", style = inter(12f), color = Ani.Muted, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { listOf("RELEASING", "FINISHED", "NOT_YET_RELEASED").forEach { Pill(it, status == it) { status = it } } }
        Check("Published: everyone sees it. Off = only you see it in the app while signed in.", published) { published = it }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Btn(if (editing == null) "Add anime" else "Save", primary = true) {
                if (title.isBlank()) { toast("A title is needed"); return@Btn }
                val body = buildJsonObject {
                    put("title", title.trim()); put("description", desc.trim().ifEmpty { null })
                    put("cover_url", cover.trim().ifEmpty { null }); put("banner_url", banner.trim().ifEmpty { null })
                    put("genres", buildJsonArray { genres.split(',').map { it.trim() }.filter { it.isNotEmpty() }.forEach { add(JsonPrimitive(it)) } })
                    put("year", year.toIntOrNull()); put("episodes_total", eps.toIntOrNull()); put("score", score.toIntOrNull())
                    put("format", format); put("status", status); put("published", published)
                }.toString()
                scope.launch {
                    val id = editing
                    val (c, t) = if (id == null) rest("POST", "/rest/v1/admin_anime", body) else rest("PATCH", "/rest/v1/admin_anime?id=eq.$id", body)
                    if (c in 200..299) { toast(if (id == null) "Added" else "Saved"); clear(); AdminContent.refresh(); load() } else toast("Failed ($c) ${t.take(80)}")
                }
            }
            if (editing != null) Btn("Cancel") { clear() }
        }
    }
    Heading("Your anime (${list.size})")
    if (list.isEmpty()) Note("Nothing added yet.")
    list.forEach { a ->
        val id = a.i("id") ?: return@forEach
        Card {
            Text(a.s("title"), style = inter(15f, FontWeight.SemiBold), color = Color.White)
            Text("App id ${AdminContent.BASE + id} · ${a.s("format")} · ${a.s("status")}", style = inter(12f), color = Ani.Muted, modifier = Modifier.padding(vertical = 4.dp))
            if (a.b("published")) Badge("Published", Color(0xFF6FE39A)) else Badge("Draft (only you)", Color(0xFFFFC94D))
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Btn("Edit") {
                    editing = id; title = a.s("title"); desc = a.s("description"); cover = a.s("cover_url"); banner = a.s("banner_url")
                    genres = (a["genres"] as? JsonArray).orEmpty().joinToString(", ") { it.jsonPrimitive.content }
                    year = a.i("year")?.toString().orEmpty(); eps = a.i("episodes_total")?.toString().orEmpty(); score = a.i("score")?.toString().orEmpty()
                    format = a.s("format").ifEmpty { "TV" }; status = a.s("status").ifEmpty { "RELEASING" }; published = a.b("published")
                }
                Btn(if (a.b("published")) "Unpublish" else "Publish") {
                    scope.launch { rest("PATCH", "/rest/v1/admin_anime?id=eq.$id", """{"published":${!a.b("published")}}"""); AdminContent.refresh(); load() }
                }
                Btn("Delete", danger = true) {
                    scope.launch {
                        rest("DELETE", "/rest/v1/admin_sources?anime_ref=eq.${AdminContent.BASE + id}", null)
                        rest("DELETE", "/rest/v1/admin_anime?id=eq.$id", null); AdminContent.refresh(); load(); toast("Deleted")
                    }
                }
            }
        }
    }
}

/* ---------------- streams ---------------- */
@Composable private fun StreamsTab() {
    val rest0 = rememberRest(); val rest: suspend (String, String, String?) -> Pair<Int, String> = { m, p, b -> rest0(m, p, b, "return=minimal") }; val scope = rememberCoroutineScope(); val ctx = LocalContext.current
    var anime by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var ref by remember { mutableStateOf("") }
    var audio by remember { mutableStateOf("sub") }
    var label by remember { mutableStateOf("Kiro") }
    var bulk by remember { mutableStateOf("") }
    var referer by remember { mutableStateOf("") }
    var sub by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(true) }
    var links by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    fun toast(m: String) = Toast.makeText(ctx, m, Toast.LENGTH_SHORT).show()
    val refId = ref.toIntOrNull() ?: 0
    fun load() { scope.launch { if (refId == 0) { links = emptyList(); return@launch }
        val (c, t) = rest("GET", "/rest/v1/admin_sources?anime_ref=eq.$refId&select=*&order=audio,episode", null); if (c in 200..299) links = rows(t) } }
    LaunchedEffect(Unit) { val (c, t) = rest("GET", "/rest/v1/admin_anime?select=id,title&order=id.desc", null); if (c in 200..299) anime = rows(t) }
    LaunchedEffect(refId) { load() }

    Heading("Add stream links")
    Card {
        Note("Type an AniList id (the number at the end of its anilist.co link), or tap one of your own anime below.")
        Field("Anime id", ref, { ref = it.filter(Char::isDigit).take(8) }, number = true)
        if (anime.isNotEmpty()) FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            anime.forEach { a -> val rid = AdminContent.BASE + (a.i("id") ?: 0); Pill(a.s("title").take(22), refId == rid) { ref = rid.toString() } }
        }
        Text("Audio", style = inter(12f), color = Ani.Muted, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Pill("Sub", audio == "sub") { audio = "sub" }; Pill("Dub", audio == "dub") { audio = "dub" } }
        Field("Server name shown in the player", label, { label = it })
        Field("Episodes, one per line: episode then link", bulk, { bulk = it }, lines = 5)
        Note("Example: 1 https://site.com/ep1.mp4   (links ending in .m3u8 play as HLS)")
        Field("Referer (only if the link needs one)", referer, { referer = it })
        Field("Subtitle link .vtt/.srt (optional)", sub, { sub = it })
        Check("Visible to everyone. Off = only you see it while signed in.", visible) { visible = it }
        Spacer(Modifier.height(12.dp))
        Btn("Add links", primary = true) {
            if (refId == 0) { toast("Enter an anime id"); return@Btn }
            val arr = buildJsonArray {
                for (raw in bulk.lines()) {
                    val line = raw.trim(); if (line.isEmpty()) continue
                    val m = Regex("^(\\d+)\\s*[|,\\s]\\s*(https?://\\S+)$", RegexOption.IGNORE_CASE).find(line)
                    if (m == null) { toast("Can't read: ${line.take(40)}"); return@Btn }
                    val url = m.groupValues[2]
                    add(buildJsonObject {
                        put("anime_ref", refId); put("episode", m.groupValues[1].toInt()); put("audio", audio); put("label", label.trim().ifEmpty { "Kiro" })
                        put("url", url); put("format", if (Regex("\\.m3u8(\\?|$)", RegexOption.IGNORE_CASE).containsMatchIn(url)) "hls" else "file")
                        put("referer", referer.trim().ifEmpty { null }); put("subtitle_url", sub.trim().ifEmpty { null }); put("published", visible)
                    })
                }
            }
            if (arr.isEmpty()) { toast("Paste at least one line"); return@Btn }
            scope.launch {
                val (c, t) = rest("POST", "/rest/v1/admin_sources", arr.toString())
                if (c in 200..299) { toast("Added ${arr.size} link(s)"); bulk = ""; AdminContent.refresh(); load() } else toast("Failed ($c) ${t.take(80)}")
            }
        }
    }
    Heading("Links for this anime (${links.size})")
    if (links.isEmpty()) Note("Nothing here yet.")
    links.forEach { s ->
        val id = s.i("id") ?: return@forEach
        Card {
            Text("Episode ${s.i("episode")} · ${s.s("audio").uppercase()} · ${s.s("label")}", style = inter(14.5f, FontWeight.SemiBold), color = Color.White)
            Text(s.s("url"), style = inter(12f), color = Ani.Muted, maxLines = 2, modifier = Modifier.padding(vertical = 4.dp))
            if (s.b("published")) Badge("Visible · ${s.s("format")}", Color(0xFF6FE39A)) else Badge("Hidden · ${s.s("format")}", Color(0xFFFFC94D))
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Btn(if (s.b("published")) "Hide" else "Show") { scope.launch { rest("PATCH", "/rest/v1/admin_sources?id=eq.$id", """{"published":${!s.b("published")}}"""); AdminContent.refresh(); load() } }
                Btn("Delete", danger = true) { scope.launch { rest("DELETE", "/rest/v1/admin_sources?id=eq.$id", null); AdminContent.refresh(); load() } }
            }
        }
    }
}

/* ---------------- updates ---------------- */
private data class Build(val build: Int, val apk: String, val size: Long, val notes: String)

@Composable private fun UpdatesTab() {
    val rest0 = rememberRest(); val rest: suspend (String, String, String?) -> Pair<Int, String> = { m, p, b -> rest0(m, p, b, "return=minimal") }; val scope = rememberCoroutineScope(); val ctx = LocalContext.current
    var builds by remember { mutableStateOf<List<Build>>(emptyList()) }
    var rels by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    fun toast(m: String) = Toast.makeText(ctx, m, Toast.LENGTH_LONG).show()
    fun load() {
        scope.launch {
            loading = true
            val gh = withContext(Dispatchers.IO) {
                runCatching {
                    Http.plain.newCall(Request.Builder().url("https://api.github.com/repos/biaruuu/anibiar-androidv2/releases?per_page=12").header("Accept", "application/vnd.github+json").build()).execute().use { r ->
                        if (!r.isSuccessful) emptyList() else (json.parseToJsonElement(r.body!!.string()) as JsonArray).mapNotNull { e ->
                            val o = e.jsonObject
                            val n = o["tag_name"]?.jsonPrimitive?.contentOrNull?.removePrefix("build-")?.toIntOrNull() ?: return@mapNotNull null
                            val a = o["assets"]?.jsonArray?.map { it.jsonObject }?.firstOrNull { it["name"]?.jsonPrimitive?.contentOrNull == "Kiro.apk" } ?: return@mapNotNull null
                            Build(n, a["browser_download_url"]!!.jsonPrimitive.content, a["size"]?.jsonPrimitive?.longOrNull ?: 0L, o["body"]?.jsonPrimitive?.contentOrNull.orEmpty())
                        }
                    }
                }.getOrDefault(emptyList())
            }
            builds = gh
            val (c, t) = rest("GET", "/rest/v1/app_releases?select=*&order=build.desc", null)
            if (c in 200..299) rels = rows(t)
            loading = false
        }
    }
    LaunchedEffect(Unit) { load() }
    fun statusOf(n: Int) = rels.firstOrNull { it.i("build") == n }?.s("status").orEmpty()
    val live = rels.filter { it.s("status") == "published" }.maxByOrNull { it.i("build") ?: 0 }?.i("build")

    Heading("Updates")
    Card {
        Text("Installed here: build ${BuildConfig.VERSION_CODE}", style = inter(14f, FontWeight.SemiBold), color = Color.White)
        Text("Live for everyone: ${live?.let { "build $it" } ?: "none"}", style = inter(14f), color = Ani.Soft, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
        Note("A new build is offered to nobody until you publish it. Add it as a draft and only this app, signed in as you, is offered it (Settings → Check for updates). When it works, publish.")
    }
    if (loading && builds.isEmpty()) Note("Loading builds…")
    builds.forEach { b ->
        val st = statusOf(b.build)
        Card {
            Text("Build ${b.build}  ·  ${"%.1f".format(b.size / 1048576.0)} MB", style = inter(15f, FontWeight.SemiBold), color = Color.White)
            Spacer(Modifier.height(6.dp))
            when (st) { "published" -> Badge("Published", Color(0xFF6FE39A)); "draft" -> Badge("Draft (only you)", Color(0xFFFFC94D)); else -> Badge("Not added", Ani.Muted) }
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (st.isEmpty()) Btn("Add as draft to test") {
                    val body = buildJsonObject { put("build", b.build); put("version", "0.3.${b.build}"); put("apk_url", b.apk); put("size", b.size); put("notes", b.notes.take(2000)); put("status", "draft") }.toString()
                    scope.launch { val (c, _) = rest0("POST", "/rest/v1/app_releases?on_conflict=build", body, "resolution=merge-duplicates,return=minimal"); if (c in 200..299) toast("Draft added: check for updates in Settings") else toast("Failed ($c)"); load() }
                }
                if (st == "draft") Btn("Publish to everyone", primary = true) {
                    scope.launch { val (c, _) = rest("PATCH", "/rest/v1/app_releases?build=eq.${b.build}", """{"status":"published","published_at":"${java.time.Instant.now()}"}"""); toast(if (c in 200..299) "Published" else "Failed ($c)"); load() }
                }
                if (st == "published") Btn("Pull back to draft") {
                    scope.launch { rest("PATCH", "/rest/v1/app_releases?build=eq.${b.build}", """{"status":"draft","published_at":null}"""); load() }
                }
                if (st.isNotEmpty()) Btn("Remove", danger = true) { scope.launch { rest("DELETE", "/rest/v1/app_releases?build=eq.${b.build}", null); load() } }
            }
        }
    }
}
