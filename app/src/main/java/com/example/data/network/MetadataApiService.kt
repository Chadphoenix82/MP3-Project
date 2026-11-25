package com.example.data.network

import com.example.data.model.MetadataSuggestion
import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

// ---- iTunes Search API DTOs ----

data class ItunesSearchResponse(
    val resultCount: Int = 0,
    val results: List<ItunesTrackDto> = emptyList()
)

data class ItunesTrackDto(
    val trackId: Long? = null,
    val trackName: String? = null,
    val artistName: String? = null,
    val collectionName: String? = null,
    val artworkUrl100: String? = null,
    val releaseDate: String? = null,
    val primaryGenreName: String? = null,
    val copyright: String? = null,
    val trackNumber: Int? = null
)

interface ItunesSearchApi {
    @GET("search")
    suspend fun searchTracks(
        @Query("term") term: String,
        @Query("media") media: String = "music",
        @Query("entity") entity: String = "song",
        @Query("limit") limit: Int = 12
    ): ItunesSearchResponse
}

// ---- MusicBrainz WS/2 API DTOs ----

data class MusicBrainzSearchResponse(
    val recordings: List<MbRecordingDto> = emptyList()
)

data class MbRecordingDto(
    val id: String = "",
    val title: String = "",
    val score: Int = 0,
    @Json(name = "first-release-date") val firstReleaseDate: String? = null,
    @Json(name = "artist-credit") val artistCredit: List<MbArtistCreditDto> = emptyList(),
    val releases: List<MbReleaseStubDto> = emptyList(),
    val tags: List<MbTagDto> = emptyList()
)

data class MbArtistCreditDto(
    val name: String? = null,
    val joinphrase: String? = null
)

data class MbReleaseStubDto(
    val id: String = "",
    val title: String? = null,
    val date: String? = null,
    val status: String? = null,
    val media: List<MbMediaStubDto> = emptyList()
)

data class MbMediaStubDto(
    val track: List<MbTrackStubDto> = emptyList()
)

data class MbTrackStubDto(
    val number: String? = null
)

data class MbTagDto(
    val name: String = "",
    val count: Int = 0
)

data class MbReleaseDetailDto(
    val id: String = "",
    val title: String? = null,
    val date: String? = null,
    @Json(name = "label-info") val labelInfo: List<MbLabelInfoDto> = emptyList()
)

data class MbLabelInfoDto(
    val label: MbLabelDto? = null
)

data class MbLabelDto(
    val name: String? = null
)

interface MusicBrainzApi {
    @GET("ws/2/recording")
    suspend fun searchRecordings(
        @Query("query") query: String,
        @Query("fmt") fmt: String = "json",
        @Query("limit") limit: Int = 8
    ): MusicBrainzSearchResponse

    @GET("ws/2/release/{releaseId}")
    suspend fun getReleaseDetails(
        @Path("releaseId") releaseId: String,
        @Query("inc") inc: String = "labels",
        @Query("fmt") fmt: String = "json"
    ): MbReleaseDetailDto
}

class MetadataAutoCompleteRepository {

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "PhotonAudioPlayer/1.0 (https://photonaudio.app)")
                .header("Accept", "application/json")
                .build()
            chain.proceed(request)
        }
        .build()

    private val itunesApi: ItunesSearchApi = Retrofit.Builder()
        .baseUrl("https://itunes.apple.com/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(ItunesSearchApi::class.java)

    private val musicBrainzApi: MusicBrainzApi = Retrofit.Builder()
        .baseUrl("https://musicbrainz.org/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(MusicBrainzApi::class.java)

    /**
     * High-precision metadata lookup combining fielded MusicBrainz recording/release queries
     * (official release dates, exact record labels/publishers, Cover Art Archive) and iTunes
     * high-resolution catalog metadata, ranked strictly by accuracy against the query.
     * No personal user data is collected or transmitted — only the song search text is queried.
     */
    suspend fun searchMetadata(
        rawQuery: String,
        hintTitle: String? = null,
        hintArtist: String? = null
    ): List<MetadataSuggestion> = withContext(Dispatchers.IO) {
        val cleaned = cleanSearchQuery(rawQuery)
        if (cleaned.isBlank()) return@withContext emptyList()

        val mbQuery = buildFieldedMusicBrainzQuery(cleaned, hintTitle, hintArtist)

        coroutineScope {
            val itunesDeferred = async {
                runCatching { queryItunes(cleaned) }.getOrDefault(emptyList())
            }
            val mbDeferred = async {
                runCatching { queryMusicBrainz(mbQuery, fallbackQuery = cleaned) }.getOrDefault(emptyList())
            }

            val itunesResults = itunesDeferred.await()
            val mbResults = mbDeferred.await()

            // Cross-enrich iTunes matches with exact MusicBrainz publisher/label & precise release date
            val enrichedItunes = itunesResults.map { item ->
                val matchingMb = mbResults.firstOrNull { mb ->
                    normalizeCompare(mb.title) == normalizeCompare(item.title) &&
                        (normalizeCompare(mb.artist).contains(normalizeCompare(item.artist)) ||
                            normalizeCompare(item.artist).contains(normalizeCompare(mb.artist)))
                }
                if (matchingMb != null) {
                    val bestPublisher = matchingMb.publisher.takeIf { it.isNotBlank() && it != "Independent" }
                        ?: item.publisher
                    val bestDate = pickMorePreciseDate(matchingMb.releaseDate, item.releaseDate)
                    item.copy(
                        publisher = bestPublisher,
                        releaseDate = bestDate,
                        releaseYear = extractYear(bestDate) ?: item.releaseYear,
                        trackNumber = item.trackNumber ?: matchingMb.trackNumber,
                        source = "iTunes + MusicBrainz Verified"
                    )
                } else {
                    item
                }
            }

            val seenKeys = mutableSetOf<String>()
            val merged = mutableListOf<MetadataSuggestion>()
            for (suggestion in enrichedItunes + mbResults) {
                val key = "${normalizeCompare(suggestion.title)}|${normalizeCompare(suggestion.artist)}|${normalizeCompare(suggestion.album)}"
                if (seenKeys.add(key)) {
                    merged.add(suggestion)
                }
            }

            // Rank by exact match precision so the most accurate record rises to the top
            merged
                .map { s ->
                    val score = computeAccuracyScore(s, cleaned, hintTitle, hintArtist)
                    s.copy(confidencePercent = score)
                }
                .sortedByDescending { it.confidencePercent }
                .take(14)
        }
    }

    private suspend fun queryItunes(term: String): List<MetadataSuggestion> {
        val response = itunesApi.searchTracks(term = term)
        return response.results.mapNotNull { dto ->
            val title = dto.trackName?.trim()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val artist = dto.artistName?.trim()?.takeIf { it.isNotBlank() } ?: "Unknown Artist"
            val album = dto.collectionName?.trim()?.takeIf { it.isNotBlank() } ?: title
            val rawDate = dto.releaseDate.orEmpty().take(10)
            val year = extractYear(rawDate)
            val highResArt = dto.artworkUrl100?.replace("100x100bb", "600x600bb")
            val publisher = cleanCopyrightToPublisher(dto.copyright) ?: "Independent"
            val genre = dto.primaryGenreName?.trim()?.takeIf { it.isNotBlank() } ?: "Music"

            MetadataSuggestion(
                id = "itunes_${dto.trackId ?: title.hashCode()}",
                title = title,
                artist = artist,
                album = album,
                genre = genre,
                publisher = publisher,
                releaseDate = rawDate,
                releaseYear = year,
                trackNumber = dto.trackNumber,
                artworkUrl = highResArt,
                source = "iTunes Catalog"
            )
        }
    }

    private suspend fun queryMusicBrainz(fieldedQuery: String, fallbackQuery: String): List<MetadataSuggestion> {
        var response = musicBrainzApi.searchRecordings(query = fieldedQuery, limit = 7)
        if (response.recordings.isEmpty() && fieldedQuery != fallbackQuery) {
            response = musicBrainzApi.searchRecordings(query = fallbackQuery, limit = 7)
        }

        return response.recordings.take(5).mapNotNull { rec ->
            if (rec.title.isBlank()) return@mapNotNull null
            val artist = rec.artistCredit.joinToString("") { credit ->
                (credit.name.orEmpty()) + (credit.joinphrase.orEmpty())
            }.trim().ifBlank { "Unknown Artist" }

            // Prefer official releases with full YYYY-MM-DD dates over bootlegs/undated promos
            val primaryRelease = rec.releases
                .sortedWith(
                    compareByDescending<MbReleaseStubDto> { it.status.equals("Official", ignoreCase = true) }
                        .thenByDescending { (it.date?.length ?: 0) >= 10 }
                        .thenBy { it.date.orEmpty().ifBlank { "9999-99-99" } }
                )
                .firstOrNull()

            val album = primaryRelease?.title?.trim()?.ifBlank { rec.title } ?: rec.title
            val releaseDate = pickMorePreciseDate(primaryRelease?.date, rec.firstReleaseDate)
            val year = extractYear(releaseDate)
            val trackNum = primaryRelease?.media
                ?.firstOrNull()
                ?.track
                ?.firstOrNull()
                ?.number
                ?.toIntOrNull()

            val publisher = if (primaryRelease != null && primaryRelease.id.isNotBlank()) {
                runCatching {
                    val detail = musicBrainzApi.getReleaseDetails(primaryRelease.id)
                    detail.labelInfo.firstOrNull { !it.label?.name.isNullOrBlank() }?.label?.name?.trim()
                }.getOrNull().orEmpty().ifBlank { "Independent" }
            } else {
                "Independent"
            }

            val topGenre = rec.tags
                .filter { it.name.isNotBlank() }
                .maxByOrNull { it.count }
                ?.name
                ?.split(" ")
                ?.joinToString(" ") { part -> part.replaceFirstChar { it.uppercase() } }
                ?: "Music"

            val coverUrl = primaryRelease?.id?.takeIf { it.isNotBlank() }?.let {
                "https://coverartarchive.org/release/$it/front-500"
            }

            MetadataSuggestion(
                id = "mb_${rec.id}",
                title = rec.title.trim(),
                artist = artist,
                album = album,
                genre = topGenre,
                publisher = publisher,
                releaseDate = releaseDate,
                releaseYear = year,
                trackNumber = trackNum,
                artworkUrl = coverUrl,
                source = "MusicBrainz"
            )
        }
    }

    private fun buildFieldedMusicBrainzQuery(cleaned: String, hintTitle: String?, hintArtist: String?): String {
        val cleanTitle = hintTitle?.let { cleanSearchQuery(it) }?.takeIf { it.isNotBlank() }
        val cleanArtist = hintArtist?.trim()?.takeIf { it.isNotBlank() && !it.equals("Unknown Artist", ignoreCase = true) }
        return if (cleanTitle != null && cleanArtist != null && cleaned.contains(cleanTitle, ignoreCase = true)) {
            "recording:\"$cleanTitle\" AND artist:\"$cleanArtist\""
        } else {
            cleaned
        }
    }

    private fun computeAccuracyScore(
        suggestion: MetadataSuggestion,
        query: String,
        hintTitle: String?,
        hintArtist: String?
    ): Int {
        var score = 42
        val normQuery = normalizeCompare(query)
        val normTitle = normalizeCompare(suggestion.title)
        val normArtist = normalizeCompare(suggestion.artist)

        if (hintTitle != null && normalizeCompare(hintTitle) == normTitle) {
            score += 24
        } else if (normQuery.contains(normTitle) && normTitle.length >= 3) {
            score += 18
        }

        if (hintArtist != null && !hintArtist.equals("Unknown Artist", ignoreCase = true)) {
            val nhArtist = normalizeCompare(hintArtist)
            if (nhArtist == normArtist) score += 20
            else if (normArtist.contains(nhArtist) || nhArtist.contains(normArtist)) score += 12
        } else if (normQuery.contains(normArtist) && normArtist.length >= 3) {
            score += 14
        }

        if (!suggestion.artworkUrl.isNullOrBlank()) score += 6
        if (suggestion.publisher.isNotBlank() && suggestion.publisher != "Independent") score += 6
        if (suggestion.releaseDate.length >= 10) score += 5
        if (suggestion.source.contains("Verified")) score += 5

        return score.coerceIn(50, 99)
    }

    private fun pickMorePreciseDate(first: String?, second: String?): String {
        val a = first.orEmpty().trim()
        val b = second.orEmpty().trim()
        return when {
            a.length >= 10 -> a.take(10)
            b.length >= 10 -> b.take(10)
            a.length >= b.length -> a
            else -> b
        }
    }

    private fun normalizeCompare(value: String): String {
        return value.lowercase().replace(Regex("[^a-z0-9]"), "")
    }

    companion object {
        fun cleanSearchQuery(raw: String): String {
            return raw
                .replace(Regex("\\.(mp3|flac|wav|m4a|ogg|aac)$", RegexOption.IGNORE_CASE), "")
                .replace(Regex("^\\d{1,3}\\s*[-._]\\s*"), "")
                .replace(Regex("[_]+"), " ")
                .replace(Regex("\\s*\\((official|audio|hd|flac|wav|hq|remaster|lyrics).*?\\)", RegexOption.IGNORE_CASE), "")
                .replace(Regex("\\s*\\[.*?]"), "")
                .replace(Regex("\\s+"), " ")
                .trim()
        }

        fun extractYear(dateString: String?): Int? {
            if (dateString.isNullOrBlank()) return null
            return Regex("\\b(19\\d{2}|20\\d{2})\\b").find(dateString)?.value?.toIntOrNull()
        }

        private fun cleanCopyrightToPublisher(copyright: String?): String? {
            if (copyright.isNullOrBlank()) return null
            val cleaned = copyright
                .replace(Regex("^[℗©(P)(C)\\s\\d,-]+"), "")
                .trim()
            return cleaned.takeIf { it.isNotBlank() }
        }
    }
}
