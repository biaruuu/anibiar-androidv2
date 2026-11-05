package com.anibiar.app.social

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** What the player should do right now, already compensated for network delay. */
data class SyncCommand(val playing: Boolean, val positionMs: Long, val hard: Boolean, val at: Long = System.currentTimeMillis(), val speed: Float = 1f, val boost: Int = 0, val cap: String? = null, val capSize: Int = 0, val capStyle: Int = 0)

/** A guest asking the host for another anime or episode. */
data class RoomRequest(val from: String, val animeId: Int?, val title: String, val cover: String?, val episode: Int)

/** An episode/audio change made by someone in the room. */
data class RoomChange(val episode: Int, val audio: String)

/**
 * Keeps everyone in a room on the same frame.
 *
 * Every state message carries the sender's position and a server-clock timestamp. Receivers compute where the
 * video should be *now* (position + time since the message, if playing), so network delay doesn't accumulate.
 * The last person to press play / pause / seek becomes the "leader" and re-announces every 2 s so drift is
 * corrected; a small drift is smoothed with playback speed, a big one with a seek.
 */
class WatchTogether(
    private val social: SocialRepository,
    private val scope: CoroutineScope,
    val room: Room,
) {
    private val channel = "room-${room.id}"
    private val me = social.myId.orEmpty()
    @Volatile private var offset = 0L
    private val _commands = MutableSharedFlow<SyncCommand>(replay = 0, extraBufferCapacity = 8)
    val commands: SharedFlow<SyncCommand> = _commands
    private val _changes = MutableSharedFlow<RoomChange>(replay = 0, extraBufferCapacity = 4)
    private val _requests = MutableSharedFlow<RoomRequest>(extraBufferCapacity = 8)
    val requests: SharedFlow<RoomRequest> = _requests
    @Volatile var speed = 1f
    /** Fullscreen belongs to the host: it is shared with everyone. */
    @Volatile var fullscreen = false
    private var remoteFs = false
    private val _fs = MutableSharedFlow<Boolean>(extraBufferCapacity = 4)
    val fsEvents: SharedFlow<Boolean> = _fs
    fun shareFullscreen(on: Boolean) { fullscreen = on; send("fs", buildJsonObject { put("on", on.toString()) }) }
    private fun noteFs(on: Boolean) { if (on != remoteFs) { remoteFs = on; _fs.tryEmit(on) } }
    /** How far (ms) this phone was from the host the last time it synced. */
    @Volatile var driftMs = 0L
    /** Where I was when I left the watch page, so coming back resumes at the right moment. */
    @Volatile var resumeEp = -1
    @Volatile var resumePos = 0L
    @Volatile var resumePlaying = false
    @Volatile var resumeAt = 0L
    fun resumeSeconds(ep: Int?): Double? {
        if (ep == null || ep != resumeEp || resumeAt == 0L) return null
        val ms = resumePos + if (resumePlaying) System.currentTimeMillis() - resumeAt else 0L
        return (ms / 1000.0).takeIf { it > 1.0 }
    }
    @Volatile var boost = 0
    @Volatile var captionLabel = ""
    @Volatile var captionSize = 0
    @Volatile var captionStyle = 0
    // Guests report when their video stalls; the host pauses for everyone until they're back (or 8 s pass).
    private val buffering = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val _waiting = MutableStateFlow(false)
    val waiting: StateFlow<Boolean> = _waiting.asStateFlow()
    fun reportBuffering(on: Boolean) = send("buf", buildJsonObject { put("on", on.toString()) })
    private fun refreshWaiting() {
        val cutoff = System.currentTimeMillis() - 8000
        buffering.entries.removeAll { it.value < cutoff }
        val w = buffering.isNotEmpty()
        if (_waiting.value != w) _waiting.value = w
    }
    fun request(animeId: Int?, title: String, cover: String?, episode: Int) = send("request", buildJsonObject {
        if (animeId != null) put("id", animeId); put("title", title); put("cover", cover.orEmpty()); put("ep", episode)
    })
    private val _moved = MutableSharedFlow<Room>(replay = 0, extraBufferCapacity = 2)
    /** The host switched the room to another anime. */
    val moved: SharedFlow<Room> = _moved
    /** Voice-chat signalling goes through the same channel. */
    @Volatile var rtcListener: ((JsonObject) -> Unit)? = null
    fun sendRtc(extra: JsonObject) = send("rtc", extra)
    val changes: SharedFlow<RoomChange> = _changes
    private val _members = MutableStateFlow<Set<String>>(setOf(me))
    val members: StateFlow<Set<String>> = _members.asStateFlow()
    private val _ended = MutableStateFlow(false)
    val ended: StateFlow<Boolean> = _ended.asStateFlow()
    private val seen = HashMap<String, Long>()
    private val _muted = MutableStateFlow<Set<String>>(emptySet())
    /** People whose microphone is muted. */
    val mutedUsers: StateFlow<Set<String>> = _muted.asStateFlow()
    @Volatile private var myMuted = false
    fun setMuted(m: Boolean) { myMuted = m; send("mic", buildJsonObject { put("muted", m.toString()) }) }
    private fun noteMuted(from: String, p: JsonObject) {
        val m = p["muted"]?.jsonPrimitive?.contentOrNull ?: return
        _muted.value = if (m == "true") _muted.value + from else _muted.value - from
    }
    private var jobs = mutableListOf<Job>()

    /** True when I was the last to control playback, so I should keep announcing. */
    @Volatile var leader = room.hostId == me
        private set
    @Volatile var episode = room.episode
    @Volatile var audio = room.audio

    private fun now() = System.currentTimeMillis() + offset

    fun start() {
        // Keep my clock aligned with the server's so every position is compared on the same timeline.
        jobs += scope.launch { while (true) { social.clockOffset()?.let { offset = it }; delay(20_000) } }
        val flow = social.realtime.join(channel)
        jobs += scope.launch {
            flow.collect { b ->
                val p = b.payload
                val from = p["from"]?.jsonPrimitive?.contentOrNull ?: return@collect
                if (from == me) return@collect
                touch(from)
                noteMuted(from, p)
                when (b.event) {
                    "state" -> onState(p)
                    "hello" -> { send("here", buildJsonObject { put("muted", myMuted.toString()) }); if (leader || room.hostId == me) { /* newcomer needs the position */ requestAnnounce = true } }
                    "here" -> Unit
                    "bye" -> { buffering.remove(from); refreshWaiting(); seen.remove(from); _muted.value = _muted.value - from; publishMembers() }
                    "change" -> {
                        val ep = p["ep"]?.jsonPrimitive?.intOrNull ?: return@collect
                        val au = p["audio"]?.jsonPrimitive?.contentOrNull ?: "sub"
                        episode = ep; audio = au
                        _changes.tryEmit(RoomChange(ep, au))
                    }
                    "buf" -> { if (p["on"]?.jsonPrimitive?.contentOrNull == "true") buffering[from] = System.currentTimeMillis() else buffering.remove(from); refreshWaiting() }
                    "fs" -> noteFs(p["on"]?.jsonPrimitive?.contentOrNull == "true")
                    "rtc" -> rtcListener?.invoke(p)
                    "request" -> if (room.hostId == me) _requests.tryEmit(RoomRequest(from, p["id"]?.jsonPrimitive?.intOrNull, p["title"]?.jsonPrimitive?.contentOrNull.orEmpty(), p["cover"]?.jsonPrimitive?.contentOrNull?.ifEmpty { null }, p["ep"]?.jsonPrimitive?.intOrNull ?: 1))
                    "anime" -> {
                        val id = p["id"]?.jsonPrimitive?.intOrNull ?: return@collect
                        episode = p["ep"]?.jsonPrimitive?.intOrNull ?: 1; audio = p["audio"]?.jsonPrimitive?.contentOrNull ?: "sub"
                        _moved.tryEmit(Room(room.id, room.hostId, id, p["title"]?.jsonPrimitive?.contentOrNull.orEmpty(), p["cover"]?.jsonPrimitive?.contentOrNull,
                            p["ep"]?.jsonPrimitive?.intOrNull ?: 1, p["audio"]?.jsonPrimitive?.contentOrNull ?: "sub", false))
                    }
                    "end" -> _ended.value = true
                }
            }
        }
        // Say hello until the channel is up, then expire people who went quiet.
        jobs += scope.launch {
            send("hello", buildJsonObject { })
            var n = 0
            while (true) {
                delay(2000)
                if (n++ < 3) send("hello", buildJsonObject { })
                val cutoff = System.currentTimeMillis() - 6000
                if (seen.entries.removeAll { it.value < cutoff }) publishMembers()
                send("here", buildJsonObject { put("muted", myMuted.toString()) })
            }
        }
    }

    @Volatile private var requestAnnounce = false
    @Volatile private var lastPlaying = false
    @Volatile private var lastPos = 0L

    private fun touch(uid: String) {
        val isNew = seen.put(uid, System.currentTimeMillis()) == null
        if (isNew) publishMembers()
    }

    private fun publishMembers() { _members.value = seen.keys.toSet() + me }

    private fun onState(p: JsonObject) {
        val ep = p["ep"]?.jsonPrimitive?.intOrNull ?: episode
        if (ep != episode) return // different episode: wait for its "change"
        val playing = p["playing"]?.jsonPrimitive?.contentOrNull == "true"
        val pos = p["pos"]?.jsonPrimitive?.longOrNull ?: return
        val ts = p["ts"]?.jsonPrimitive?.longOrNull ?: now()
        val hard = p["hard"]?.jsonPrimitive?.contentOrNull == "true"
        leader = false
        val target = if (playing) pos + (now() - ts).coerceAtLeast(0) else pos
        p["fs"]?.jsonPrimitive?.contentOrNull?.let { noteFs(it == "true") }
        val spd = p["spd"]?.jsonPrimitive?.contentOrNull?.toFloatOrNull() ?: 1f
        _commands.tryEmit(SyncCommand(playing, target, hard, speed = spd, boost = p["bst"]?.jsonPrimitive?.intOrNull ?: 0,
            cap = p["cap"]?.jsonPrimitive?.contentOrNull, capSize = p["cs"]?.jsonPrimitive?.intOrNull ?: 0, capStyle = p["cst"]?.jsonPrimitive?.intOrNull ?: 0))
    }

    private fun send(event: String, extra: JsonObject) {
        val payload = buildJsonObject {
            put("from", me)
            extra.forEach { (k, v) -> put(k, v) }
        }
        social.realtime.send(channel, event, payload)
    }

    /** I pressed play / pause or sought: tell everyone immediately. */
    fun announce(playing: Boolean, positionMs: Long, hard: Boolean = true) {
        leader = true
        lastPlaying = playing; lastPos = positionMs
        send("state", buildJsonObject {
            put("playing", playing.toString()); put("pos", positionMs); put("ts", now()); put("ep", episode); put("hard", hard.toString()); put("spd", speed.toString()); put("bst", boost); put("fs", fullscreen.toString()); put("cap", captionLabel); put("cs", captionSize); put("cst", captionStyle)
        })
    }

    /** Called by the player every tick; the leader re-announces every ~2 s so others stay locked on. */
    private var lastBeat = 0L
    fun tick(playing: Boolean, positionMs: Long) {
        refreshWaiting()
        val t = System.currentTimeMillis()
        if ((leader && t - lastBeat > 1000) || requestAnnounce) {
            lastBeat = t; requestAnnounce = false
            send("state", buildJsonObject {
                put("playing", playing.toString()); put("pos", positionMs); put("ts", now()); put("ep", episode); put("hard", "false"); put("spd", speed.toString()); put("bst", boost); put("fs", fullscreen.toString()); put("cap", captionLabel); put("cs", captionSize); put("cst", captionStyle)
            })
        }
    }

    /** Anyone can switch episode / audio for the whole room. */
    fun changeEpisode(ep: Int, au: String) {
        episode = ep; audio = au; leader = true
        send("change", buildJsonObject { put("ep", ep); put("audio", au) })
        scope.launch { social.updateRoom(room.id, ep, au) }
    }

    /** Host moves everyone to another anime. */
    fun changeAnime(next: Room) {
        episode = next.episode; audio = next.audio
        send("anime", buildJsonObject {
            put("id", next.animeId); put("title", next.title); put("ep", next.episode); put("audio", next.audio)
            put("cover", next.cover.orEmpty())
        })
        scope.launch { social.switchRoomAnime(next) }
    }

    /** Stop this instance without telling anyone (the room carries on in a new Watch page). */
    fun detach() {
        jobs.forEach { it.cancel() }; jobs.clear()
        val name = channel
        scope.launch { delay(2500); social.realtime.leave(name) }
    }

    fun leave(endForAll: Boolean) {
        if (endForAll) {
            send("end", buildJsonObject { })
            scope.launch { social.endRoom(room.id) }
        } else send("bye", buildJsonObject { })
        val name = channel
        scope.launch { delay(600); social.realtime.leave(name) }
        jobs.forEach { it.cancel() }; jobs.clear()
    }
}
