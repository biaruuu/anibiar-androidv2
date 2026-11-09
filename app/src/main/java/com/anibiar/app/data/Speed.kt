package com.anibiar.app.data

import android.app.Activity
import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.os.Bundle
import android.view.Choreographer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.net.InetAddress

/** Whether the app is on screen, and a counter that ticks every time the network comes back. */
object AppState {
    @Volatile var foreground = false
        private set
    private val _netUp = MutableStateFlow(0)
    val netUp: StateFlow<Int> = _netUp
    private var started = 0

    fun init(app: Application) {
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(a: Activity) { started++; foreground = true; FrameMonitor.start() }
            override fun onActivityStopped(a: Activity) { started = (started - 1).coerceAtLeast(0); if (started == 0) { foreground = false; FrameMonitor.stop() } }
            override fun onActivityCreated(a: Activity, b: Bundle?) {}
            override fun onActivityResumed(a: Activity) {}
            override fun onActivityPaused(a: Activity) {}
            override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
            override fun onActivityDestroyed(a: Activity) {}
        })
        runCatching {
            val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            var seenOnce = false
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) { if (seenOnce) _netUp.value = _netUp.value + 1; seenOnce = true }
            })
        }
    }

    fun metered(c: Context): Boolean = runCatching { (c.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).isActiveNetworkMetered }.getOrDefault(true)
}

/** Counts slow frames while the app is visible and writes a line to Diagnostics when scrolling/animation got choppy. */
object FrameMonitor : Choreographer.FrameCallback {
    private var running = false
    private var last = 0L
    private var frames = 0
    private var slow = 0
    private var worst = 0L

    fun start() { if (running) return; running = true; last = 0L; Choreographer.getInstance().postFrameCallback(this) }
    fun stop() { running = false; flush() }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        if (last != 0L) {
            val ms = (frameTimeNanos - last) / 1_000_000
            // Gaps over a second are the app being idle or paused, not jank.
            if (ms in 1..999) { frames++; if (ms > 32) { slow++; if (ms > worst) worst = ms } }
            if (frames >= 600) flush()
        }
        last = frameTimeNanos
        Choreographer.getInstance().postFrameCallback(this)
    }

    private fun flush() {
        if (frames >= 120 && slow * 100 / frames >= 8) Diag.log("frames", "$slow of $frames frames were slow (worst ${worst} ms)")
        frames = 0; slow = 0; worst = 0
    }
}

/** Retries a failed GET once or twice with a short pause, so a flaky mobile connection doesn't show an error. */
class RetryInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request()
        if (req.method != "GET") return chain.proceed(req)
        var attempt = 0
        while (true) {
            try {
                val res = chain.proceed(req)
                if (res.code in 502..504 && attempt < 1 && !chain.call().isCanceled()) { res.close(); attempt++; Thread.sleep(350); continue }
                return res
            } catch (e: IOException) {
                if (chain.call().isCanceled() || attempt >= 2) throw e
                attempt++
                Thread.sleep(300L * attempt)
            }
        }
    }
}

/** Looks up the hosts the app always talks to before the first request needs them. */
object DnsWarm {
    fun run(hosts: List<String>) { hosts.forEach { h -> runCatching { InetAddress.getAllByName(h) } } }
}

/** Remembers which stream providers have been failing so they stop slowing down playback start. */
object StreamHealth {
    private class H(var fails: Int = 0, var lastFail: Long = 0)
    private val map = java.util.concurrent.ConcurrentHashMap<String, H>()

    fun ok(p: String) { map[p]?.fails = 0 }
    fun fail(p: String) { val h = map.getOrPut(p) { H() }; h.fails++; h.lastFail = System.currentTimeMillis() }

    /** Skipped for five minutes after three failures in a row. */
    fun benched(p: String): Boolean {
        val h = map[p] ?: return false
        return h.fails >= 3 && System.currentTimeMillis() - h.lastFail < 300_000
    }
}
