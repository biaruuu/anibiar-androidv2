package com.anibiar.app.ui.search

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.anibiar.app.AniBiarApp
import com.anibiar.app.data.Media
import com.anibiar.app.data.displayTitle
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class SearchFilters(
    val genres: Set<String> = emptySet(),
    val formats: Set<String> = emptySet(),
    val status: String? = null,
    val year: Int? = null,
    val sort: String? = null, // null = default (Best Match while typing, otherwise Most Popular)
    val language: String? = null,
    val source: String? = null,
    val country: String? = null,
    val adult: String = "NON", // NON | ADULT | ANY
    val score: Int? = null,
    val season: String? = null,
) {
    val activeCount: Int
        get() = listOf(
            genres.isNotEmpty(), formats.isNotEmpty(), status != null, year != null, sort != null,
            language != null, source != null, country != null, adult != "NON", score != null, season != null,
        ).count { it }
}

data class SearchUiState(
    val query: String = "",
    val filters: SearchFilters = SearchFilters(),
    val page: Int = 1,
    val lastPage: Int = 1,
    val hasNext: Boolean = false,
    val results: List<Media> = emptyList(),
    /** The page [results] actually belong to (can lag behind [page] while a new page loads). */
    val resultsPage: Int = 1,
    val loading: Boolean = true,
    val error: String? = null,
    /** Set when the results are on-device matches: the best title, offered as "Did you mean". */
    val suggest: String? = null,
    val offline: Boolean = false,
)

class SearchViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as AniBiarApp).repository
    private val catalog = (application as AniBiarApp).catalog
    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()
    private var job: Job? = null

    init { run(0) }

    fun setQuery(q: String) {
        _state.update { it.copy(query = q, page = 1) }
        run(400)
    }

    fun submit() = run(0)

    fun setFilters(f: SearchFilters) {
        _state.update { it.copy(filters = f, page = 1) }
        run(0)
    }

    fun reset() {
        _state.update { it.copy(filters = SearchFilters(), page = 1) }
        run(0)
    }

    fun goTo(page: Int) {
        _state.update { it.copy(page = page.coerceAtLeast(1)) }
        run(0)
    }

    fun retry() = run(0)

    private fun run(debounceMs: Long) {
        job?.cancel()
        job = viewModelScope.launch {
            if (debounceMs > 0) delay(debounceMs)
            val s = _state.value
            // Never keep showing the previous page under the new page number while the next one loads.
            val qNow = s.query.trim()
            val localNow = if (qNow.isNotEmpty() && s.filters.activeCount == 0 && s.page == 1)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { catalog.search(qNow, 30) } else emptyList()
            // Instant on-device matches (typo tolerant) while AniList answers.
            _state.update { it.copy(loading = true, error = null, results = localNow, resultsPage = 1, suggest = null, offline = false) }
            if (s.filters.language == "DUB") {
                _state.update { it.copy(results = emptyList(), loading = false, hasNext = false, lastPage = 1) }
                return@launch
            }
            val f = s.filters
            val q = s.query.trim()
            val sort = f.sort ?: if (q.isNotEmpty()) "SEARCH_MATCH" else "POPULARITY_DESC"
            val vars = buildJsonObject {
                put("page", s.page)
                put("perPage", 30)
                if (q.isNotEmpty()) put("search", q)
                put("sort", JsonArray(listOf(JsonPrimitive(sort))))
                if (f.genres.isNotEmpty()) put("genres", JsonArray(f.genres.map { JsonPrimitive(it) }))
                if (f.formats.isNotEmpty()) put("formats", JsonArray(f.formats.map { JsonPrimitive(it) }))
                f.status?.let { put("status", it) }
                f.year?.let { put("year", it) }
                f.season?.let { put("season", it) }
                f.country?.let { put("country", it) }
                f.source?.let { put("source", it) }
                when (f.adult) { "NON" -> put("adult", false); "ADULT" -> put("adult", true) }
                f.score?.let { put("score", it) }
            }
            try {
                val page = repository.search(vars)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { catalog.add(page.media) }
                if (page.media.isEmpty() && localNow.isNotEmpty()) {
                    _state.update { it.copy(results = localNow, resultsPage = 1, loading = false, hasNext = false, lastPage = 1, suggest = localNow.first().displayTitle) }
                    return@launch
                }
                _state.update {
                    it.copy(
                        results = page.media,
                        resultsPage = s.page,
                        loading = false,
                        suggest = null,
                        hasNext = page.pageInfo.hasNextPage == true,
                        lastPage = (page.pageInfo.lastPage ?: it.page).coerceAtLeast(1),
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (localNow.isNotEmpty()) _state.update { it.copy(results = localNow, loading = false, offline = true, hasNext = false, lastPage = 1) }
                else _state.update { it.copy(loading = false, error = "Couldn't load results. Check your connection.") }
            }
        }
    }
}
