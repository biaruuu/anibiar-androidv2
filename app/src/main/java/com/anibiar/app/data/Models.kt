package com.anibiar.app.data

import kotlinx.serialization.Serializable

@Serializable
data class Title(
    val romaji: String? = null,
    val english: String? = null,
    val native: String? = null,
)

@Serializable
data class Cover(
    val extraLarge: String? = null,
    val large: String? = null,
    val color: String? = null,
)

@Serializable
data class FuzzyDate(
    val year: Int? = null,
    val month: Int? = null,
    val day: Int? = null,
)

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
    val synonyms: List<String>? = null,
    val source: String? = null,
    // ---- details page only ----
    val type: String? = null,
    val idMal: Int? = null,
    val duration: Int? = null,
    val season: String? = null,
    val endDate: FuzzyDate? = null,
    val popularity: Int? = null,
    val siteUrl: String? = null,
    val trailer: Trailer? = null,
    val studios: StudioConn? = null,
    val externalLinks: List<ExternalLink> = emptyList(),
    val streamingEpisodes: List<StreamingEpisode> = emptyList(),
    val relations: RelationConn? = null,
    val recommendations: RecommendationConn? = null,
    val characters: CharacterConn? = null,
)

@Serializable
data class Trailer(val id: String? = null, val site: String? = null, val thumbnail: String? = null)

@Serializable
data class StudioNode(val name: String? = null)

@Serializable
data class StudioEdge(val isMain: Boolean = false, val node: StudioNode? = null)

@Serializable
data class StudioConn(val edges: List<StudioEdge> = emptyList())

@Serializable
data class ExternalLink(
    val site: String? = null,
    val url: String? = null,
    val type: String? = null,
    val icon: String? = null,
    val color: String? = null,
)

@Serializable
data class StreamingEpisode(
    val title: String? = null,
    val thumbnail: String? = null,
    val url: String? = null,
    val site: String? = null,
)

@Serializable
data class RelationEdge(val relationType: String? = null, val node: Media? = null)

@Serializable
data class RelationConn(val edges: List<RelationEdge> = emptyList())

@Serializable
data class Recommendation(val mediaRecommendation: Media? = null)

@Serializable
data class RecommendationConn(val nodes: List<Recommendation> = emptyList())

@Serializable
data class PageInfo(val total: Int? = null)

@Serializable
data class PersonName(val full: String? = null)

@Serializable
data class PersonImage(val large: String? = null, val medium: String? = null)

@Serializable
data class Person(
    val id: Int? = null,
    val name: PersonName? = null,
    val image: PersonImage? = null,
    val languageV2: String? = null,
)

@Serializable
data class CharacterEdge(
    val role: String? = null,
    val node: Person? = null,
    val voiceActors: List<Person> = emptyList(),
)

@Serializable
data class CharacterConn(
    val pageInfo: PageInfo? = null,
    val edges: List<CharacterEdge> = emptyList(),
)

@Serializable
data class MediaHolder(@kotlinx.serialization.SerialName("Media") val media: Media? = null)

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
data class PopularData(val popular: MediaPage = MediaPage())

@Serializable
data class TrendingData(val trending: MediaPage = MediaPage())

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

// ---- Details helpers ----

val Media.formatName: String
    get() = when (format) {
        "TV" -> "TV Series"
        "TV_SHORT" -> "TV Short"
        "MOVIE" -> "Movie"
        "ONA" -> "ONA"
        "OVA" -> "OVA"
        "SPECIAL" -> "Special"
        "MUSIC" -> "Music"
        else -> format ?: "TV Series"
    }

val Media.seasonLabel: String?
    get() {
        val y = seasonYear ?: startDate?.year ?: return null
        val s = season?.lowercase()?.replaceFirstChar { it.uppercase() }
        return if (s != null) "$s $y" else y.toString()
    }

val Media.statusLabel: String
    get() = when (status) {
        "RELEASING" -> "Airing"
        "FINISHED" -> "Finished"
        "NOT_YET_RELEASED" -> "Upcoming"
        "CANCELLED" -> "Cancelled"
        "HIATUS" -> "On Hiatus"
        else -> status ?: "Unknown"
    }

/** AniList score on a 10-point scale, e.g. 7.5. */
val Media.score10: String?
    get() = averageScore?.let { String.format(java.util.Locale.US, "%.1f", it / 10.0) }

private fun FuzzyDate.format(): String? {
    val y = year ?: return null
    val m = month ?: return y.toString()
    val d = day ?: return String.format(java.util.Locale.US, "%d-%02d", y, m)
    return String.format(java.util.Locale.US, "%d-%02d-%02d", y, m, d)
}

val Media.airedLabel: String?
    get() {
        val start = startDate?.format() ?: return null
        val end = endDate?.format()
        return if (end != null && end != start) "$start \u2013 $end" else start
    }

/** Studios: main studio first, then the rest. */
val Media.studioNames: List<Pair<String, Boolean>>
    get() = studios?.edges.orEmpty()
        .mapNotNull { e -> e.node?.name?.let { it to e.isMain } }
        .sortedByDescending { it.second }

val Person.displayName: String get() = name?.full ?: ""

/** One episode row for the Details page (title, thumbnail, release date and runtime when known). */
data class EpisodeInfo(
    val number: Int,
    val title: String?,
    val date: String?,
    val runtime: Int?,
    val image: String?,
)
