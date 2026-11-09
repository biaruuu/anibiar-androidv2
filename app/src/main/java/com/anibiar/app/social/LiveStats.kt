package com.anibiar.app.social

import com.anibiar.app.AniBiarApp
import com.anibiar.app.data.AppState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Live "online" and "watching" counts: every open app announces itself on one shared realtime channel. */
object LiveStats {
    private const val CHANNEL = "site-presence"
    private val key = java.util.UUID.randomUUID().toString()
    private val _online = MutableStateFlow(0)
    private val _watching = MutableStateFlow(0)
    val online: StateFlow<Int> = _online
    val watching: StateFlow<Int> = _watching
    @Volatile private var me = false
    @Volatile private var rt: Realtime? = null

    private fun state(): JsonObject = buildJsonObject { put("w", if (me) 1 else 0); put("p", "app") }

    /** Called from the screen that is playing a video. */
    fun setWatching(on: Boolean) { me = on; rt?.track(CHANNEL, state()) }

    fun start(app: AniBiarApp, scope: CoroutineScope) {
        scope.launch {
            AppState.fg.collectLatest { fg ->
                if (!fg) { _online.value = 0; _watching.value = 0; return@collectLatest }
                val realtime = app.social.realtime
                rt = realtime
                realtime.join(CHANNEL, key, state())
                try {
                    realtime.presence(CHANNEL)?.collect { m ->
                        _online.value = m.size
                        _watching.value = m.values.count { it["w"]?.jsonPrimitive?.intOrNull == 1 }
                    }
                } finally { realtime.leave(CHANNEL); rt = null }
            }
        }
    }
}
