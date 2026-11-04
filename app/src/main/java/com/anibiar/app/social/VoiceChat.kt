package com.anibiar.app.social

import android.content.Context
import android.media.AudioManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.webrtc.AudioTrack
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.audio.JavaAudioDeviceModule
import java.util.concurrent.ConcurrentHashMap

private open class Sdp : SdpObserver {
    override fun onCreateSuccess(d: SessionDescription) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(e: String?) {}
    override fun onSetFailure(e: String?) {}
}

/**
 * Voice call for a Watch Together room: a WebRTC audio connection between every pair of people in the room,
 * signalled over the room's realtime channel. It starts by itself when the room opens.
 */
class VoiceChat(
    context: Context,
    private val tg: WatchTogether,
    private val me: String,
    private val scope: CoroutineScope,
) {
    companion object {
        @Volatile private var inited = false
        private val ICE = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("turn:openrelay.metered.ca:80").setUsername("openrelayproject").setPassword("openrelayproject").createIceServer(),
            PeerConnection.IceServer.builder("turn:openrelay.metered.ca:443").setUsername("openrelayproject").setPassword("openrelayproject").createIceServer(),
            PeerConnection.IceServer.builder("turns:openrelay.metered.ca:443?transport=tcp").setUsername("openrelayproject").setPassword("openrelayproject").createIceServer(),
        )
    }

    private val app = context.applicationContext
    private val audio = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var oldMode = AudioManager.MODE_NORMAL
    private var oldSpeaker = false
    private val factory: PeerConnectionFactory
    private val track: AudioTrack
    private val peers = ConcurrentHashMap<String, PeerConnection>()
    private val started = ConcurrentHashMap<String, Long>()
    private val queuedIce = ConcurrentHashMap<String, MutableList<IceCandidate>>()
    private val jobs = mutableListOf<Job>()
    @Volatile private var stopped = false

    private val _muted = MutableStateFlow(false)
    val muted: StateFlow<Boolean> = _muted.asStateFlow()
    private val _connected = MutableStateFlow(0)
    /** How many people I'm voice-connected to right now. */
    val connected: StateFlow<Int> = _connected.asStateFlow()

    init {
        if (!inited) {
            PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(app).createInitializationOptions())
            inited = true
        }
        factory = PeerConnectionFactory.builder()
            .setAudioDeviceModule(JavaAudioDeviceModule.builder(app).setUseHardwareAcousticEchoCanceler(true).setUseHardwareNoiseSuppressor(true).createAudioDeviceModule())
            .createPeerConnectionFactory()
        val constraints = MediaConstraints().apply {
            for (k in listOf("googEchoCancellation", "googNoiseSuppression", "googAutoGainControl", "googHighpassFilter", "googEchoCancellation2", "googNoiseSuppression2"))
                mandatory.add(MediaConstraints.KeyValuePair(k, "true"))
        }
        val source = factory.createAudioSource(constraints)
        track = factory.createAudioTrack("kiro-mic", source)
    }

    fun start() {
        oldMode = audio.mode; oldSpeaker = audio.isSpeakerphoneOn
        audio.mode = AudioManager.MODE_IN_COMMUNICATION
        audio.isSpeakerphoneOn = true
        tg.rtcListener = { p -> scope.launch { onSignal(p) } }
        jobs += scope.launch {
            tg.members.collect { members ->
                members.forEach { uid -> if (uid != me && me < uid && peers[uid] == null) offerTo(uid) }
                peers.keys.filter { it !in members }.forEach { close(it) }
            }
        }
        jobs += scope.launch { while (!stopped) { pollLevels(); delay(250) } }
        // Retry connections that never came up (the other side may have joined the channel a moment late).
        jobs += scope.launch {
            while (!stopped) {
                delay(6000)
                var up = 0
                peers.forEach { (uid, pc) ->
                    val st = pc.iceConnectionState()
                    if (st == PeerConnection.IceConnectionState.CONNECTED || st == PeerConnection.IceConnectionState.COMPLETED) up++
                    else if (me < uid && System.currentTimeMillis() - (started[uid] ?: 0) > 9000) { close(uid); if (uid in tg.members.value) offerTo(uid) }
                }
                _connected.value = up
            }
        }
    }

    fun setMuted(m: Boolean) { _muted.value = m; track.setEnabled(!m); tg.setMuted(m) }

    private val _speaking = MutableStateFlow<Set<String>>(emptySet())
    /** Who is talking right now (from the call's audio levels). */
    val speaking: StateFlow<Set<String>> = _speaking.asStateFlow()
    private val lastSpoke = ConcurrentHashMap<String, Long>()
    private fun pollLevels() {
        peers.forEach { (uid, pc) ->
            runCatching {
                pc.getStats { report ->
                    report.statsMap.values.forEach { s ->
                        val kind = s.members["kind"] as? String
                        val level = (s.members["audioLevel"] as? Number)?.toDouble() ?: return@forEach
                        if (kind != "audio") return@forEach
                        val who = when (s.type) { "inbound-rtp" -> uid; "media-source" -> me; else -> null } ?: return@forEach
                        if (level > 0.025 && !(who == me && _muted.value)) lastSpoke[who] = System.currentTimeMillis()
                    }
                }
            }
        }
        val now = System.currentTimeMillis()
        _speaking.value = lastSpoke.filterValues { now - it < 500 }.keys.toSet()
    }

    fun stop() {
        if (stopped) return
        stopped = true
        tg.rtcListener = null
        jobs.forEach { it.cancel() }
        peers.keys.toList().forEach { close(it) }
        runCatching { track.dispose(); factory.dispose() }
        audio.mode = oldMode; audio.isSpeakerphoneOn = oldSpeaker
    }

    private fun close(uid: String) {
        peers.remove(uid)?.let { runCatching { it.close(); it.dispose() } }
        started.remove(uid); queuedIce.remove(uid)
    }

    private fun peer(uid: String): PeerConnection {
        peers[uid]?.let { return it }
        val cfg = PeerConnection.RTCConfiguration(ICE).apply { sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN }
        val pc = factory.createPeerConnection(cfg, object : PeerConnection.Observer {
            override fun onSignalingChange(s: PeerConnection.SignalingState) {}
            override fun onIceConnectionChange(s: PeerConnection.IceConnectionState) {
                _connected.value = peers.values.count { it.iceConnectionState() == PeerConnection.IceConnectionState.CONNECTED || it.iceConnectionState() == PeerConnection.IceConnectionState.COMPLETED }
            }
            override fun onIceConnectionReceivingChange(b: Boolean) {}
            override fun onIceGatheringChange(s: PeerConnection.IceGatheringState) {}
            override fun onIceCandidate(c: IceCandidate) {
                tg.sendRtc(buildJsonObject {
                    put("to", uid); put("kind", "ice"); put("sdp", c.sdp); put("mid", c.sdpMid.orEmpty()); put("idx", c.sdpMLineIndex)
                })
            }
            override fun onIceCandidatesRemoved(c: Array<out IceCandidate>) {}
            override fun onAddStream(s: MediaStream) {}
            override fun onRemoveStream(s: MediaStream) {}
            override fun onDataChannel(d: DataChannel) {}
            override fun onRenegotiationNeeded() {}
        })!!
        pc.addTrack(track, listOf("kiro"))
        peers[uid] = pc; started[uid] = System.currentTimeMillis()
        return pc
    }

    /** Opus tuned for clear speech: 64 kbps mono, in-band error correction, 10 ms packets for low delay. */
    private fun hq(sdp: String): String {
        val pt = Regex("a=rtpmap:(\\d+) opus/48000/2").find(sdp)?.groupValues?.get(1) ?: return sdp
        val params = "minptime=10;useinbandfec=1;stereo=0;sprop-stereo=0;maxaveragebitrate=64000;maxplaybackrate=48000;cbr=0"
        val fmtp = Regex("a=fmtp:$pt ([^\\r\\n]*)")
        return if (fmtp.containsMatchIn(sdp)) fmtp.replace(sdp) { m ->
            val keep = m.groupValues[1].split(";").filter { kv -> kv.isNotBlank() && params.split(";").none { it.substringBefore("=") == kv.substringBefore("=") } }
            "a=fmtp:$pt " + (keep + params.split(";")).joinToString(";")
        } else sdp.replace(Regex("(a=rtpmap:$pt opus/48000/2\\r?\\n)"), "$1a=fmtp:$pt $params\r\n")
    }

    private fun offerTo(uid: String) {
        val pc = peer(uid)
        pc.createOffer(object : Sdp() {
            override fun onCreateSuccess(orig: SessionDescription) {
                val d = SessionDescription(orig.type, hq(orig.description))
                pc.setLocalDescription(Sdp(), d)
                tg.sendRtc(buildJsonObject { put("to", uid); put("kind", "offer"); put("sdp", d.description) })
            }
        }, MediaConstraints())
    }

    private fun onSignal(p: JsonObject) {
        if (stopped) return
        if (p["to"]?.jsonPrimitive?.contentOrNull != me) return
        val from = p["from"]?.jsonPrimitive?.contentOrNull ?: return
        val sdp = p["sdp"]?.jsonPrimitive?.contentOrNull ?: return
        when (p["kind"]?.jsonPrimitive?.contentOrNull) {
            "offer" -> {
                close(from) // a fresh offer always wins
                val pc = peer(from)
                pc.setRemoteDescription(object : Sdp() {
                    override fun onSetSuccess() {
                        drain(from, pc)
                        pc.createAnswer(object : Sdp() {
                            override fun onCreateSuccess(orig: SessionDescription) {
                                val d = SessionDescription(orig.type, hq(orig.description))
                                pc.setLocalDescription(Sdp(), d)
                                tg.sendRtc(buildJsonObject { put("to", from); put("kind", "answer"); put("sdp", d.description) })
                            }
                        }, MediaConstraints())
                    }
                }, SessionDescription(SessionDescription.Type.OFFER, sdp))
            }
            "answer" -> peers[from]?.let { pc ->
                pc.setRemoteDescription(object : Sdp() { override fun onSetSuccess() { drain(from, pc) } }, SessionDescription(SessionDescription.Type.ANSWER, sdp))
            }
            "ice" -> {
                val c = IceCandidate(p["mid"]?.jsonPrimitive?.contentOrNull.orEmpty(), p["idx"]?.jsonPrimitive?.intOrNull ?: 0, sdp)
                val pc = peers[from]
                if (pc != null && pc.remoteDescription != null) pc.addIceCandidate(c)
                else queuedIce.getOrPut(from) { java.util.Collections.synchronizedList(mutableListOf()) }.add(c)
            }
        }
    }

    private fun drain(uid: String, pc: PeerConnection) {
        queuedIce.remove(uid)?.toList()?.forEach { pc.addIceCandidate(it) }
    }
}
