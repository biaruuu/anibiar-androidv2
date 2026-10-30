package com.anibiar.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.anibiar.app.data.AnimeRepository
import com.anibiar.app.data.Http
import com.anibiar.app.data.WatchHistory

class AniBiarApp : Application(), ImageLoaderFactory {

    val repository: AnimeRepository by lazy { AnimeRepository(this) }
    val history: WatchHistory by lazy { WatchHistory(this) }

    /** Large memory + disk image caches keep posters instant and scrolling smooth. */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .okHttpClient(Http.client)
            .crossfade(true)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("images"))
                    .maxSizeBytes(256L * 1024 * 1024)
                    .build()
            }
            .respectCacheHeaders(false)
            .build()
}
