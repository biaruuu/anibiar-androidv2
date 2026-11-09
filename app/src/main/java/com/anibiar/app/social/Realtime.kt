package com.anibiar.app.social

import com.anibiar.app.data.Http
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** One broadcast message received on a channel. */
data class Broadcast(val event: String, val payload: JsonObject)

/**
 * A tiny Supabase Realtime (Phoenix) client: one shared WebSocket, many broadcast channels.
 * Reconnects and re-joins by itself, and holds outgoing messages until a channel is joined.
 */
class Realtime(private val scope: CoroutineScope) {
    private val json = Json { ignoreUnknownKeys = true }
    private val refs = AtomicInteger(1)
    private val channels = ConcurrentHashMap<String, Chan>()
    @Volatile private var socket: WebSocket? = null
    @Volatile private var open = false
    private var loop: Job? = null
    private val wsClient by lazy { Http.plain.newBuilder().readTimeout(0, java.util.concurrent.TimeUnit.MILLISECONDS).pingInterval(15, java.util.concurrent.TimeUnit.SECONDS).build() }

    inner class Chan(val topic: String) {
        val incoming = MutableSharedFlow<Broadcast>(extraBufferCapacity = 64)
        @Volatile var joined = false
        @Volatile var joinRef = ""
        val queue = ArrayDeque<String>()
        var users = 0
        @Volatile var presenceKey = ""
        @Volatile var track: JsonObject? = null
        /** Who is on the channel right now: presence key to the state that device is tracking. */
        val presence = MutableStateFlow<Map<String, JsonObject>>(emptyMap())
    }

    /** Joins [name] (shared by everyone who uses the same name) and returns its message flow. */
    fun join(name: String, presenceKey: String = "", track: JsonObject? = null): SharedFlow<Broadcast> {
        val topic = "realtime:$name"
        val c = channels.getOrPut(topic) { Chan(topic) }
        synchronized(c) { c.users++; if (presenceKey.isNotEmpty()) c.presenceKey = presenceKey; if (track != null) c.track = track }
        ensureRunning()
        if (open) sendJoin(c)
        return c.incoming
    }

    fun leave(name: String) {
        val topic = "realtime:$name"
        val c = channels[topic] ?: return
        val last = synchronized(c) { --c.users <= 0 }
        if (last) {
            channels.remove(topic)
            if (open) socket?.send(frame(topic, "phx_leave", JsonObject(emptyMap()), joinRef = c.joinRef))
        }
    }

    fun presence(name: String): StateFlow<Map<String, JsonObject>>? = channels["realtime:$name"]?.presence

    /** Changes what this device announces on the channel (sent now if joined, else on join). */
    fun track(name: String, payload: JsonObject) {
        val c = channels["realtime:$name"] ?: return
        c.track = payload
        if (c.joined && open) sendTrack(c)
    }

    private fun sendTrack(c: Chan) {
        val p = c.track ?: return
        socket?.send(frame(c.topic, "presence", buildJsonObject { put("type", "presence"); put("event", "track"); put("payload", p) }, null))
    }

    /** Sends a broadcast; queued until the channel is joined. */
    fun send(name: String, event: String, payload: JsonObject) {
        val c = channels["realtime:$name"] ?: return
        val msg = frame(
            c.topic, "broadcast",
            buildJsonObject { put("type", "broadcast"); put("event", event); put("payload", payload) },
            joinRef = null,
        )
        synchronized(c) {
            if (c.joined && open) { if (socket?.send(msg) != true) c.queue.addLast(msg) }
            else { c.queue.addLast(msg); while (c.queue.size > 20) c.queue.removeFirst() }
        }
    }

    @Volatile private var kicked = false

    /** The network came back: drop the old socket now and reconnect without waiting out the back-off. */
    fun reconnect() { kicked = true; socket?.cancel() }

    val connected: Boolean get() = open && channels.values.all { it.joined }

    private fun frame(topic: String, event: String, payload: JsonElement, joinRef: String?): String = buildJsonObject {
        put("topic", topic); put("event", event); put("payload", payload)
        put("ref", refs.getAndIncrement().toString())
        if (joinRef != null) put("join_ref", joinRef)
    }.toString()

    private fun sendJoin(c: Chan) {
        c.joined = false
        c.joinRef = refs.getAndIncrement().toString()
        val payload = buildJsonObject {
            put("config", buildJsonObject {
                put("broadcast", buildJsonObject { put("self", false); put("ack", false) })
                put("presence", buildJsonObject { put("key", c.presenceKey) })
                put("private", false)
            })
        }
        socket?.send(buildJsonObject {
            put("topic", c.topic); put("event", "phx_join"); put("payload", payload)
            put("ref", c.joinRef); put("join_ref", c.joinRef)
        }.toString())
    }

    private fun ensureRunning() {
        if (loop?.isActive == true) return
        loop = scope.launch(Dispatchers.IO) {
            var backoff = 500L
            while (isActive) {
                if (channels.isEmpty()) { delay(1000); if (channels.isEmpty()) { socket?.close(1000, null); socket = null; open = false; loop = null; return@launch } else continue }
                val closed = kotlinx.coroutines.CompletableDeferred<Unit>()
                val url = SupabaseConfig.URL.replace("https://", "wss://") + "/realtime/v1/websocket?apikey=${SupabaseConfig.PUBLISHABLE_KEY}&vsn=1.0.0"
                val ws = wsClient.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        socket = webSocket; open = true; backoff = 500L
                        channels.values.forEach { sendJoin(it) }
                    }
                    override fun onMessage(webSocket: WebSocket, text: String) { handle(text) }
                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { open = false; closed.complete(Unit) }
                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { open = false; closed.complete(Unit) }
                })
                // Heartbeat while the socket is alive.
                val hb = launch {
                    while (isActive) {
                        delay(20_000)
                        if (open) ws.send(frame("phoenix", "heartbeat", JsonObject(emptyMap()), null))
                    }
                }
                closed.await()
                hb.cancel(); open = false
                channels.values.forEach { it.joined = false; it.presence.value = emptyMap() }
                if (kicked) { kicked = false; backoff = 500L } else { delay(backoff); backoff = (backoff * 2).coerceAtMost(5000) }
            }
        }
    }

    private fun handle(text: String) {
        val o = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
        val topic = o["topic"]?.jsonPrimitive?.contentOrNull ?: return
        val c = channels[topic] ?: return
        when (o["event"]?.jsonPrimitive?.contentOrNull) {
            "phx_reply" -> {
                val p = o["payload"]?.jsonObject
                if (p?.get("status")?.jsonPrimitive?.contentOrNull == "ok" && o["ref"]?.jsonPrimitive?.contentOrNull == c.joinRef) {
                    synchronized(c) {
                        c.joined = true
                        while (c.queue.isNotEmpty()) socket?.send(c.queue.removeFirst())
                    }
                    sendTrack(c)
                }
            }
            "presence_state" -> {
                val p = o["payload"]?.jsonObject ?: return
                c.presence.value = p.mapValues { (_, v) -> v.jsonObject["metas"]?.jsonArray?.lastOrNull()?.jsonObject ?: JsonObject(emptyMap()) }
            }
            "presence_diff" -> {
                val p = o["payload"]?.jsonObject ?: return
                val m = c.presence.value.toMutableMap()
                // Leaves first: a changed state arrives as a leave plus a join in one diff.
                p["leaves"]?.jsonObject?.keys?.forEach { m.remove(it) }
                p["joins"]?.jsonObject?.forEach { (k, v) -> m[k] = v.jsonObject["metas"]?.jsonArray?.lastOrNull()?.jsonObject ?: JsonObject(emptyMap()) }
                c.presence.value = m
            }
            "phx_error", "phx_close" -> if (open) sendJoin(c)
            "broadcast" -> {
                val p = o["payload"]?.jsonObject ?: return
                val ev = p["event"]?.jsonPrimitive?.contentOrNull ?: return
                c.incoming.tryEmit(Broadcast(ev, p["payload"]?.jsonObject ?: JsonObject(emptyMap())))
            }
        }
    }
}
