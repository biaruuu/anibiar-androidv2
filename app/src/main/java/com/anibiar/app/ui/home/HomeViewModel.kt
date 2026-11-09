package com.anibiar.app.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.anibiar.app.AniBiarApp
import com.anibiar.app.data.HistoryItem
import com.anibiar.app.data.LatestEpisode
import com.anibiar.app.data.Media
import com.anibiar.app.data.TmdbArt
import com.anibiar.app.data.posterUrl
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

data class HomeUiState(
    val hero: List<Media> = emptyList(),
    val topAiring: List<Media> = emptyList(),
    val upcoming: List<Media> = emptyList(),
    val latest: List<LatestEpisode> = emptyList(),
    val popular: List<Media> = emptyList(),
    val trending: List<Media> = emptyList(),
    val history: List<HistoryItem> = emptyList(),
    val reco: List<Media> = emptyList(),
    val recoBecause: String? = null,
    val offline: Boolean = false,
    val art: Map<Int, TmdbArt> = emptyMap(),
    val loading: Boolean = true,
    val error: String? = null,
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as AniBiarApp
    private val repository = app.repository
    private val history = app.history
    private val catalog = app.catalog

    private val requestedArt = HashSet<Int>()
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        load()
        viewModelScope.launch { history.version.collect { _state.update { it.copy(history = history.load()) }; recommend() } }
        viewModelScope.launch { app.watchlist.ids.collect { recommend() } }
        // Connection came back: reload straight away instead of waiting for the next tick.
        viewModelScope.launch { com.anibiar.app.data.AppState.netUp.drop(1).collect { load() } }
    }

    fun refresh() = load()

    private var loadedAt = 0L
    /** Refreshes only when the data is older than [maxAgeMs]: called every time Home comes back on screen. */
    fun refreshIfStale(maxAgeMs: Long) { if (System.currentTimeMillis() - loadedAt > maxAgeMs) load() }

    fun removeFromHistory(item: HistoryItem) {
        history.remove(item.mediaId)
        _state.update { it.copy(history = history.load()) }
    }

    private fun load() {
        loadedAt = System.currentTimeMillis()
        viewModelScope.launch {
            _state.update { it.copy(loading = it.hero.isEmpty() && it.latest.isEmpty(), error = null, history = history.load()) }

            // 1) Show whatever we cached last time, instantly.
            repository.cachedDiscover()?.let { applyDiscover(it.top.media, it.upcoming.media) }
            val cachedLatest = repository.cachedLatest()
            if (cachedLatest.isNotEmpty()) _state.update { it.copy(latest = cachedLatest, loading = false) }

            repository.cachedPopular().takeIf { it.isNotEmpty() }?.let { c -> _state.update { it.copy(popular = c) } }

            repository.cachedTrending().takeIf { it.isNotEmpty() }?.let { c -> _state.update { it.copy(trending = c) } }

            // 2) Refresh from AniList in parallel.
            val trendingJob = async { runCatching { repository.fetchTrending() } }
            val popularJob = async { runCatching { repository.fetchPopular() } }
            val discoverJob = async { runCatching { repository.fetchDiscover() } }
            val latestJob = async { runCatching { repository.fetchLatest() } }

            val discover = discoverJob.await()
            val latest = latestJob.await()
            trendingJob.await().getOrNull()?.takeIf { it.isNotEmpty() }?.let { list ->
                _state.update { it.copy(trending = list) }
                remember(list)
            }
            popularJob.await().getOrNull()?.takeIf { it.isNotEmpty() }?.let { list ->
                _state.update { it.copy(popular = list) }
                remember(list)
            }

            discover.getOrNull()?.let { applyDiscover(it.top.media, it.upcoming.media) }
            latest.getOrNull()?.let { list ->
                _state.update { it.copy(latest = list) }
                remember(list.map { it.media })
                repository.preloadImages(list.take(12).map { it.media.posterUrl })
            }

            val offlineNow = discover.isFailure && latest.isFailure
            _state.update { it.copy(offline = offlineNow) }
            _state.update { current ->
                val nothingToShow = current.hero.isEmpty() && current.latest.isEmpty()
                current.copy(
                    loading = false,
                    error = if (nothingToShow) "Couldn't reach AniList. Check your connection and try again." else null,
                )
            }
        }
    }

    /** "Because you watched X": scored in Rust from the on-disk catalog. */
    private fun recommend() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            val hist = history.load()
            val picks = catalog.recommend(hist, app.watchlist.ids.value)
            val because = hist.firstOrNull { catalog.get(it.mediaId) != null }?.title
            _state.update { it.copy(reco = picks, recoBecause = because) }
        }
    }

    private fun remember(list: List<Media>) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) { catalog.add(list); recommend() }
    }

    private fun applyDiscover(top: List<Media>, upcoming: List<Media>) {
        remember(top + upcoming)
        val hero = top.filter { it.bannerImage != null || it.posterUrlAvailable() }.take(HERO_COUNT)
        _state.update { it.copy(hero = hero, topAiring = top, upcoming = upcoming, loading = false) }

        // TMDB artwork (logo + backdrop) for the hero slides, fetched in parallel.
        hero.forEach { media ->
            if (!requestedArt.add(media.id)) return@forEach
            viewModelScope.launch {
                // An empty TmdbArt marks "resolved, nothing found" so the UI can fall back to text.
                val art = repository.art(media) ?: TmdbArt(null, null)
                _state.update { it.copy(art = it.art + (media.id to art)) }
            }
        }
    }

    private fun Media.posterUrlAvailable(): Boolean =
        coverImage.extraLarge != null || coverImage.large != null

    private companion object {
        const val HERO_COUNT = 8
    }
}
