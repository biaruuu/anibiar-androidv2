package com.anibiar.app.social

import com.anibiar.app.data.Http
import com.anibiar.app.nativecore.Native
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap

data class Preview(val url: String, val title: String, val image: String?, val host: String)

/** Title and picture of the first link in a chat message (Open Graph / <title>), cached for the session. */
object LinkPreview {
    private val cache = ConcurrentHashMap<String, Preview?>()
    private val urlRx = Regex("""https?://[^\s<>"']+""")
    private val metaRx = Regex("""<meta\s+[^>]*?(?:property|name)=["']([^"']+)["'][^>]*?content=["']([^"']*)["'][^>]*>""", RegexOption.IGNORE_CASE)
    private val metaRev = Regex("""<meta\s+[^>]*?content=["']([^"']*)["'][^>]*?(?:property|name)=["']([^"']+)["'][^>]*>""", RegexOption.IGNORE_CASE)
    private val titleRx = Regex("""<title[^>]*>([^<]*)</title>""", RegexOption.IGNORE_CASE)

    fun firstUrl(body: String): String? = urlRx.find(body)?.value?.trimEnd('.', ',', ')', '!', '?')

    private fun clean(s: String): String = Native.html(s, 2) ?: s.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'")

    suspend fun load(url: String): Preview? {
        if (cache.containsKey(url)) return cache[url]
        val p = withContext(Dispatchers.IO) {
            runCatching {
                val req = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124 Mobile Safari/537.36").build()
                Http.client.newCall(req).execute().use { r ->
                    if (!r.isSuccessful || r.body?.contentType()?.subtype?.contains("html") != true) return@use null
                    // Only the head matters: read at most 200 KB.
                    val src = r.body!!.source(); src.request(200_000)
                    val html = src.buffer.clone().readUtf8().take(200_000)
                    val meta = HashMap<String, String>()
                    metaRx.findAll(html).forEach { meta.putIfAbsent(it.groupValues[1].lowercase(), it.groupValues[2]) }
                    metaRev.findAll(html).forEach { meta.putIfAbsent(it.groupValues[2].lowercase(), it.groupValues[1]) }
                    val title = (meta["og:title"] ?: meta["twitter:title"] ?: titleRx.find(html)?.groupValues?.get(1))?.let(::clean)?.trim().orEmpty()
                    if (title.isBlank()) return@use null
                    val img = (meta["og:image"] ?: meta["twitter:image"])?.let(::clean)?.let { i -> runCatching { r.request.url.resolve(i)?.toString() }.getOrNull() }
                    Preview(url, title.take(120), img, r.request.url.host.removePrefix("www."))
                }
            }.getOrNull()
        }
        cache[url] = p
        return p
    }
}
