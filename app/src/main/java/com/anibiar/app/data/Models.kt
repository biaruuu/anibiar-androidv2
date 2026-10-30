package com.anibiar.app.data

import kotlinx.serialization.Serializable

@Serializable
data class Title(
    val romaji: String? = null,
    val english: String? = null,
)

@Serializable
data class Cover(
    val extraLarge: String? = null,
    val large: String? = null,
    val color: String? = null,
)

@Serializable
data class FuzzyDate(val year: Int? = null)

@Serializable
data class NextAiring(
    val episode: Int? = null,
    val airingAt: Long? = null,
)

@Serializable
data class Media(
    val id: Int,
    val title: Title = Title(),
    val description: String? = null,
    val episodes: Int? = null,
    val format: String? = null,
    val status: String? = null,
    val seasonYear: Int? = null,
    val startDate: FuzzyDate? = null,
    val genres: List<String>? = null,
    val averageScore: Int? = null,
    val bannerImage: String? = null,
    val coverImage: Cover = Cover(),
    val nextAiringEpisode: NextAiring? = null,
    val countryOfOrigin: String? = null,
    val isAdult: Boolean? = null,
)

// ---- GraphQL response wrappers ----

@Serializable
data class MediaPage(val media: List<Media> = emptyList())

@Serializable
data class DiscoverData(
    val top: MediaPage = MediaPage(),
    val upcoming: MediaPage = MediaPage(),
)

@Serializable
data class Schedule(
    val episode: Int = 0,
    val airingAt: Long = 0,
    val media: Media? = null,
)

@Serializable
data class SchedulePage(val airingSchedules: List<Schedule> = emptyList())

@Serializable
data class LatestData(val latest: SchedulePage = SchedulePage())

@Serializable
data class GraphQlResponse<T>(val data: T? = null)

// ---- UI-facing models ----

data class LatestEpisode(
    val media: Media,
    val episode: Int,
    val airingAt: Long,
)

// ---- Helpers ----

val Media.displayTitle: String
    get() = title.english?.takeIf { it.isNotBlank() } ?: title.romaji ?: "Unknown"

val Media.year: Int?
    get() = seasonYear ?: startDate?.year

/** Episodes that have actually aired (AniList leaves `episodes` null for long runners). */
val Media.airedEpisodes: Int?
    get() {
        val next = nextAiringEpisode?.episode
        return if (next != null) (next - 1).coerceAtLeast(0) else episodes
    }

val Media.formatLabel: String
    get() = when (format) {
        "TV", "TV_SHORT" -> "TV"
        "MOVIE" -> "MOVIE"
        "ONA" -> "ONA"
        "OVA" -> "OVA"
        "SPECIAL" -> "SPECIAL"
        "MUSIC" -> "MUSIC"
        null -> "TV"
        else -> format ?: "TV"
    }

val Media.posterUrl: String?
    get() = coverImage.extraLarge ?: coverImage.large

val Media.backdropUrl: String?
    get() = bannerImage ?: coverImage.extraLarge ?: coverImage.large

val Media.cleanDescription: String
    get() {
        val raw = description ?: return ""
        return raw
            .replace(Regex("(?i)<br\\s*/?>"), " ")
            .replace(Regex("<[^>]+>"), "")
            .replace("&quot;", "\"")
            .replace("&amp;", "&")
            .replace("&#039;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

fun timeAgo(epochSeconds: Long): String {
    val diff = (System.currentTimeMillis() / 1000 - epochSeconds).coerceAtLeast(0)
    return when {
        diff < 3600 -> "${(diff / 60).coerceAtLeast(1)}m ago"
        diff < 86400 -> "${diff / 3600}h ago"
        diff < 604800 -> "${diff / 86400}d ago"
        else -> "${diff / 604800}w ago"
    }
}
