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
import kotlinx.coroutines.launch

data class HomeUiState(
    val hero: List<Media> = emptyList(),
    val topAiring: List<Media> = emptyList(),
    val upcoming: List<Media> = emptyList(),
    val latest: List<LatestEpisode> = emptyList(),
    val popular: List<Media> = emptyList(),
    val history: List<HistoryItem> = emptyList(),
    val art: Map<Int, TmdbArt> = emptyMap(),
    val loading: Boolean = true,
    val error: String? = null,
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as AniBiarApp
    private val repository = app.repository
    private val history = app.history

    private val requestedArt = HashSet<Int>()
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun refresh() = load()

    fun removeFromHistory(item: HistoryItem) {
        history.remove(item.mediaId)
        _state.update { it.copy(history = history.load()) }
    }

    private fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = it.hero.isEmpty() && it.latest.isEmpty(), error = null, history = history.load()) }

            // 1) Show whatever we cached last time, instantly.
            repository.cachedDiscover()?.let { applyDiscover(it.top.media, it.upcoming.media) }
            val cachedLatest = repository.cachedLatest()
            if (cachedLatest.isNotEmpty()) _state.update { it.copy(latest = cachedLatest, loading = false) }

            repository.cachedPopular().takeIf { it.isNotEmpty() }?.let { c -> _state.update { it.copy(popular = c) } }

            // 2) Refresh from AniList in parallel.
            val popularJob = async { runCatching { repository.fetchPopular() } }
            val discoverJob = async { runCatching { repository.fetchDiscover() } }
            val latestJob = async { runCatching { repository.fetchLatest() } }

            val discover = discoverJob.await()
            val latest = latestJob.await()
            popularJob.await().getOrNull()?.takeIf { it.isNotEmpty() }?.let { list ->
                _state.update { it.copy(popular = list) }
                repository.preloadImages(list.take(12).map { it.posterUrl })
            }

            discover.getOrNull()?.let { applyDiscover(it.top.media, it.upcoming.media) }
            latest.getOrNull()?.let { list ->
                _state.update { it.copy(latest = list) }
                repository.preloadImages(list.take(12).map { it.media.posterUrl })
            }

            _state.update { current ->
                val nothingToShow = current.hero.isEmpty() && current.latest.isEmpty()
                current.copy(
                    loading = false,
                    error = if (nothingToShow) "Couldn't reach AniList. Check your connection and try again." else null,
                )
            }
        }
    }

    private fun applyDiscover(top: List<Media>, upcoming: List<Media>) {
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
