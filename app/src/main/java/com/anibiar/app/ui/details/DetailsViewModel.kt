package com.anibiar.app.ui.details

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.anibiar.app.AniBiarApp
import com.anibiar.app.data.CharacterConn
import com.anibiar.app.data.Media
import com.anibiar.app.data.TmdbImages
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DetailsUiState(
    val media: Media? = null,
    val characters: CharacterConn? = null,
    val malScore: String? = null,
    val images: TmdbImages? = null,
    val franchise: List<Media>? = null,
    val imdbId: String? = null,
    val tvdbId: String? = null,
    val loading: Boolean = true,
    val error: String? = null,
)

class DetailsViewModel(application: Application, private val id: Int) : AndroidViewModel(application) {
    private val repository = (application as AniBiarApp).repository

    private val _state = MutableStateFlow(DetailsUiState())
    val state: StateFlow<DetailsUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun retry() = load()

    private fun load() {
        viewModelScope.launch {
            _state.update { it.copy(error = null, loading = it.media == null) }

            // Cached copy first so a revisit is instant.
            repository.cachedDetails(id)?.let { cached ->
                _state.update { it.copy(media = cached, loading = false) }
                secondary(cached)
            }
            repository.cachedCharacters(id)?.let { c -> _state.update { it.copy(characters = c) } }

            val charsJob = async { runCatching { repository.fetchCharacters(id) }.getOrNull() }
            val result = runCatching { repository.fetchDetails(id) }
            val media = result.getOrNull()
            if (media != null) {
                _state.update { it.copy(media = media, loading = false) }
                secondary(media)
            } else {
                _state.update {
                    it.copy(
                        loading = false,
                        error = if (it.media == null) "Couldn't load this anime. Check your connection and try again." else null,
                    )
                }
            }
            charsJob.await()?.let { c -> _state.update { it.copy(characters = c) } }
        }
    }

    private var secondaryStarted = false

    /** MAL score and TMDB artwork depend on the main record, and load without blocking it. */
    private fun secondary(media: Media) {
        if (secondaryStarted) return
        secondaryStarted = true
        media.idMal?.let { mal ->
            viewModelScope.launch {
                repository.malScore(mal)?.let { score -> _state.update { it.copy(malScore = score) } }
            }
        }
        viewModelScope.launch {
            repository.tmdbImages(media)?.let { imgs -> _state.update { it.copy(images = imgs) } }
        }
        viewModelScope.launch {
            repository.externalIds(media)?.let { (imdb, tvdb) -> _state.update { it.copy(imdbId = imdb, tvdbId = tvdb) } }
        }
        viewModelScope.launch {
            runCatching { repository.fetchFranchise(media) }.getOrNull()
                ?.let { list -> _state.update { it.copy(franchise = list) } }
        }
    }
}

class DetailsViewModelFactory(private val app: Application, private val id: Int) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = DetailsViewModel(app, id) as T
}
