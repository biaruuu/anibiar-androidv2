@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.anibiar.app.ui.admin

import com.anibiar.app.ui.theme.liquidGlass
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
    Column(Modifier.fillMaxSize().background(Ani.Onyx).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassBack(onBack)
            Spacer(Modifier.width(12.dp))
            Text("Admin", style = inter(22f, FontWeight.ExtraBold), color = Color.White)
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Stats", "Announce", "Updates", "Events").forEachIndexed { i, t -> Pill(t, tab == i) { tab = i } }
        }
        Spacer(Modifier.height(10.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 60.dp)) {
            when (tab) {
                0 -> StatsTab()
                1 -> AnnounceTab()
                2 -> UpdatesTab()
                else -> EventsTab()
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
    Column(Modifier.fillMaxWidth().padding(bottom = 10.dp).liquidGlass(RoundedCornerShape(20.dp)).padding(14.dp)) { content() }
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

/* ---------------- announcements ---------------- */
@Composable private fun AnnounceTab() {
    val rest0 = rememberRest(); val rest: suspend (String, String, String?) -> Pair<Int, String> = { m, p, b -> rest0(m, p, b, "return=minimal") }; val scope = rememberCoroutineScope(); val ctx = LocalContext.current
    var list by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var title by remember { mutableStateOf("") }; var body by remember { mutableStateOf("") }
    var important by remember { mutableStateOf(false) }
    fun toast(m: String) = Toast.makeText(ctx, m, Toast.LENGTH_SHORT).show()
    val appCtx = ctx.applicationContext as AniBiarApp
    fun loadNow() { scope.launch { val (c, t) = rest("GET", "/rest/v1/announcements?select=*&order=created_at.desc", null); if (c in 200..299) list = rows(t) else toast("Couldn't load ($c)") } }
    fun load() { loadNow(); com.anibiar.app.social.LiveSync.ping(appCtx) }
    LaunchedEffect(Unit) { loadNow() }

    Heading("New announcement")
    Card {
        Field("Title", title, { title = it.take(80) })
        Field("Message", body, { body = it.take(500) }, lines = 4)
        Check("Mark as important (amber highlight)", important) { important = it }
        Spacer(Modifier.height(12.dp))
        Note("It is saved as a draft first. A draft shows on Home and in Inbox only for you, labelled Preview, so you can check it. Publish sends it to every user.")
        Spacer(Modifier.height(10.dp))
        Btn("Save as draft", primary = true) {
            if (title.isBlank()) { toast("A title is needed"); return@Btn }
            val json = buildJsonObject { put("title", title.trim()); put("body", body.trim()); put("important", important); put("status", "draft") }.toString()
            scope.launch { val (c, _) = rest("POST", "/rest/v1/announcements", json); if (c in 200..299) { toast("Saved. Check Home to preview"); title = ""; body = ""; important = false; load() } else toast("Failed ($c)") }
        }
    }
    Heading("Announcements (${list.size})")
    if (list.isEmpty()) Note("Nothing yet.")
    list.forEach { a ->
        val id = a.l("id"); val live = a.s("status") == "published"
        Card {
            Text(a.s("title"), style = inter(15f, FontWeight.SemiBold), color = Color.White)
            if (a.s("body").isNotEmpty()) Text(a.s("body"), style = inter(13f, lineHeight = 18f), color = Ani.Soft, modifier = Modifier.padding(vertical = 4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (live) Badge("Published to everyone", Color(0xFF6FE39A)) else Badge("Draft (only you)", Color(0xFFFFC94D))
                if (a.b("important")) Badge("Important", Color(0xFFFFC94D))
            }
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!live) Btn("Publish to everyone", primary = true) {
                    scope.launch { val (c, _) = rest("PATCH", "/rest/v1/announcements?id=eq.$id", """{"status":"published","published_at":"${java.time.Instant.now()}"}"""); toast(if (c in 200..299) "Published" else "Failed ($c)"); load() }
                } else Btn("Pull back to draft") { scope.launch { rest("PATCH", "/rest/v1/announcements?id=eq.$id", """{"status":"draft","published_at":null}"""); load() } }
                Btn("Delete", danger = true) { scope.launch { rest("DELETE", "/rest/v1/announcements?id=eq.$id", null); load() } }
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
    val appCtx = ctx.applicationContext as AniBiarApp
    fun loadNow() {
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
    fun load() { loadNow(); com.anibiar.app.social.LiveSync.ping(appCtx) }
    LaunchedEffect(Unit) { loadNow() }
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

@Composable private fun EventsTab() {
    val rest0 = rememberRest(); val rest: suspend (String, String, String?) -> Pair<Int, String> = { m, p, b -> rest0(m, p, b, "return=minimal") }; val scope = rememberCoroutineScope(); val ctx = LocalContext.current
    var list by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var title by remember { mutableStateOf("") }; var body by remember { mutableStateOf("") }; var image by remember { mutableStateOf("") }; var days by remember { mutableStateOf("7") }
    fun toast(m: String) = Toast.makeText(ctx, m, Toast.LENGTH_SHORT).show()
    fun load() { scope.launch { val (c, t) = rest("GET", "/rest/v1/events?select=*&order=ends_at.desc&limit=30", null); if (c in 200..299) list = rows(t) else toast("Couldn't load ($c)") } }
    LaunchedEffect(Unit) { load() }
    Heading("New seasonal event")
    Card {
        Field("Title", title, { title = it.take(80) })
        Field("Message", body, { body = it.take(300) }, lines = 3)
        Field("Banner image link (optional)", image, { image = it.take(300) })
        Field("Runs for how many days", days, { days = it.filter { c -> c.isDigit() }.take(3) })
        Spacer(Modifier.height(10.dp))
        Note("It shows as a banner at the top of Home for everyone, right away, and disappears by itself when the days run out.")
        Spacer(Modifier.height(10.dp))
        Btn("Start event", primary = true) {
            val d = days.toIntOrNull() ?: 0
            if (title.isBlank() || d < 1) { toast("Add a title and 1+ days"); return@Btn }
            val now = java.time.Instant.now()
            val json = buildJsonObject {
                put("title", title.trim()); put("body", body.trim()); if (image.isNotBlank()) put("image", image.trim())
                put("starts_at", now.toString()); put("ends_at", now.plusSeconds(d * 86400L).toString())
            }.toString()
            scope.launch { val (c, _) = rest("POST", "/rest/v1/events", json); if (c in 200..299) { toast("Event started"); title = ""; body = ""; image = ""; load() } else toast("Failed ($c)") }
        }
    }
    Heading("Events (${list.size})")
    if (list.isEmpty()) Note("Nothing yet.")
    list.forEach { e ->
        val live = runCatching { java.time.Instant.parse(e.s("ends_at")).toEpochMilli() > System.currentTimeMillis() }.getOrDefault(false)
        Card {
            Text(e.s("title"), style = inter(15f, FontWeight.SemiBold), color = Color.White)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) { if (live) Badge("Live", Color(0xFF6FE39A)) else Badge("Ended", Color(0xFF8E8E93)) }
            Spacer(Modifier.height(8.dp))
            Btn("Delete", danger = true) { scope.launch { rest("DELETE", "/rest/v1/events?id=eq.${e.l("id")}", null); load() } }
        }
    }
}
