package com.anibiar.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.anibiar.app.data.AnimeRepository
import com.anibiar.app.data.Http
import com.anibiar.app.data.WatchHistory
import com.anibiar.app.data.Watchlist
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AniBiarApp : Application(), ImageLoaderFactory {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val repository: AnimeRepository by lazy { AnimeRepository(this) }
    val history: WatchHistory by lazy { WatchHistory(this) }
    val watchlist: Watchlist by lazy { Watchlist(this) }
    val social: com.anibiar.app.social.SocialRepository by lazy { com.anibiar.app.social.SocialRepository() }

    override fun onCreate() {
        super.onCreate()
        Http.init(this)
        com.anibiar.app.social.FirebaseConfig.init(this)
        social.start()
        // Build the network stack and warm image caches off the main thread while the UI starts.
        appScope.launch {
            Http.client
            repository.prewarm()
        }
    }

    /** Large memory + disk image caches keep posters instant and scrolling smooth. */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .okHttpClient { Http.client }
            .components {
                if (android.os.Build.VERSION.SDK_INT >= 28) add(coil.decode.ImageDecoderDecoder.Factory()) else add(coil.decode.GifDecoder.Factory())
            }
            .crossfade(150)
            .allowRgb565(false)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.30)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("images"))
                    .maxSizeBytes(384L * 1024 * 1024)
                    .build()
            }
            .respectCacheHeaders(false)
            .build()
}
