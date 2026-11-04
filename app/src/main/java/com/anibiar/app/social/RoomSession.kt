package com.anibiar.app.social

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The Watch Together room and its voice call, living for as long as you stay in the call: leaving the watch page,
 * browsing other screens or opening another anime does not end it. Only "leave" (or the host ending it) does.
 */
class RoomSession(private val context: Context, private val social: SocialRepository, private val scope: CoroutineScope) {
    var room by mutableStateOf<Room?>(null); private set
    var together by mutableStateOf<WatchTogether?>(null); private set
    var voice by mutableStateOf<VoiceChat?>(null); private set
    var profiles by mutableStateOf<Map<String, UserProfile>>(emptyMap()); private set

    private val jobs = mutableListOf<Job>()
    private val main = Handler(Looper.getMainLooper())
    private var prevMembers: Set<String>? = null

    private fun toast(text: String) = main.post { Toast.makeText(context, text, Toast.LENGTH_SHORT).show() }

    /** Join (or come back to) a room. Re-entering the room you're already in changes nothing. */
    fun enter(r: Room) {
        if (room?.id == r.id && together != null) return
        if (room != null) leave(false)
        room = r
        val t = WatchTogether(social, scope, r)
        together = t
        t.start()
        prevMembers = null
        jobs += scope.launch { t.ended.collect { if (it) { toast("The host ended the room"); leave(false) } } }
        jobs += scope.launch { t.moved.collect { next -> room = next } }
        jobs += scope.launch { t.changes.collect { c -> room = room?.copy(episode = c.episode, audio = c.audio) } }
        jobs += scope.launch {
            t.members.collect { members ->
                val missing = members.filter { it !in profiles }
                if (missing.isNotEmpty()) profiles = profiles + social.profiles(missing).associateBy { it.uid }
                val before = prevMembers
                prevMembers = members
                if (before == null) return@collect
                val me = social.myId
                val joined = (members - before).filter { it != me }
                val left = (before - members).filter { it != me }
                if (joined.isEmpty() && left.isEmpty()) return@collect
                runCatching {
                    val tone = ToneGenerator(if (voice != null) AudioManager.STREAM_VOICE_CALL else AudioManager.STREAM_MUSIC, 90)
                    tone.startTone(if (joined.isNotEmpty()) ToneGenerator.TONE_PROP_ACK else ToneGenerator.TONE_PROP_NACK, 350)
                    scope.launch { delay(800); tone.release() }
                }
                fun nameOf(u: String) = profiles[u]?.shownName ?: "Someone"
                joined.forEach { toast("${nameOf(it)} joined the room") }
                left.forEach { toast("${nameOf(it)} left the room") }
            }
        }
    }

    /** Start the voice call once the microphone is allowed. */
    fun ensureVoice() {
        val t = together ?: return
        if (voice == null) {
            CallService.start(context.applicationContext)
            voice = runCatching { VoiceChat(context.applicationContext, t, social.myId.orEmpty(), scope).also { it.start() } }.getOrNull()
        }
    }

    /** The host moves everyone to another anime; the call and room stay as they are. */
    fun moveTo(next: Room) {
        val t = together ?: return
        t.changeAnime(next)
        room = next
    }

    fun leave(endForAll: Boolean) {
        together?.leave(endForAll)
        voice?.stop()
        CallService.stop(context.applicationContext)
        jobs.forEach { it.cancel() }; jobs.clear()
        voice = null; together = null; room = null; prevMembers = null
    }
}
