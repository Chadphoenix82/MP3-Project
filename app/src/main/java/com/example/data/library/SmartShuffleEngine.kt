package com.example.data.library

import com.example.data.db.TrackEntity
import kotlin.math.roundToInt
import kotlin.random.Random

data class GenreTrackCount(
    val genre: String,
    val trackCount: Int
)

data class SmartShufflePreview(
    val totalEstimatedTracks: Int = 0,
    val recentlyImportedCount: Int = 0,
    val genreMatchedCount: Int = 0,
    val overlapCount: Int = 0,
    val previewTracks: List<TrackEntity> = emptyList(),
    val activeGenres: Set<String> = emptySet()
)

data class SmartShuffleResult(
    val tracks: List<TrackEntity>,
    val recentlyImportedCount: Int,
    val genreMatchedCount: Int,
    val overlapCount: Int,
    val activeGenres: Set<String>,
    val sourceLabel: String
)

/**
 * Dynamically constructs a Smart Shuffle queue blending the user's Recently Imported
 * tracks with user-defined genre preferences, weighted by [recentWeight] and smoothed
 * with an artist-separation pass for cohesive playback flow.
 */
object SmartShuffleEngine {

    private const val DEFAULT_QUEUE_LIMIT = 30
    private const val MIN_RECENT_POOL = 12
    private const val MAX_RECENT_POOL = 25

    fun extractAvailableGenres(allTracks: List<TrackEntity>): List<GenreTrackCount> {
        if (allTracks.isEmpty()) return emptyList()
        val countsByNormalized = linkedMapOf<String, Pair<String, Int>>()
        for (track in allTracks) {
            val raw = track.genre.trim()
            if (raw.isEmpty() || raw.equals("Unknown Genre", ignoreCase = true)) continue
            val key = raw.lowercase()
            val existing = countsByNormalized[key]
            if (existing == null) {
                countsByNormalized[key] = raw to 1
            } else {
                countsByNormalized[key] = existing.first to (existing.second + 1)
            }
        }
        return countsByNormalized.values
            .map { (display, count) -> GenreTrackCount(genre = display, trackCount = count) }
            .sortedWith(compareByDescending<GenreTrackCount> { it.trackCount }.thenBy { it.genre.lowercase() })
    }

    fun matchesPreferredGenres(trackGenre: String, preferredGenres: Set<String>): Boolean {
        if (preferredGenres.isEmpty()) return false
        val normalizedTrack = trackGenre.trim().lowercase()
        if (normalizedTrack.isEmpty() || normalizedTrack == "unknown genre") return false
        return preferredGenres.any { pref ->
            val normalizedPref = pref.trim().lowercase()
            normalizedPref.isNotEmpty() && (
                normalizedTrack == normalizedPref ||
                    normalizedTrack.contains(normalizedPref) ||
                    normalizedPref.contains(normalizedTrack)
                )
        }
    }

    fun computeRecentPool(allTracks: List<TrackEntity>): List<TrackEntity> {
        if (allTracks.isEmpty()) return emptyList()
        val targetSize = (allTracks.size * 0.40f).roundToInt()
            .coerceIn(MIN_RECENT_POOL, MAX_RECENT_POOL)
            .coerceAtMost(allTracks.size)
        return allTracks
            .sortedByDescending { it.dateAdded }
            .take(targetSize)
    }

    fun buildPreview(
        allTracks: List<TrackEntity>,
        preferredGenres: Set<String>,
        recentWeight: Float,
        queueLimit: Int = DEFAULT_QUEUE_LIMIT
    ): SmartShufflePreview {
        if (allTracks.isEmpty()) {
            return SmartShufflePreview(activeGenres = preferredGenres)
        }
        // Deterministic seed so preview stays stable across recompositions until library/prefs change
        val previewResult = buildQueue(
            allTracks = allTracks,
            preferredGenres = preferredGenres,
            recentWeight = recentWeight,
            queueLimit = queueLimit,
            random = Random(allTracks.size * 31 + preferredGenres.hashCode())
        )
        return SmartShufflePreview(
            totalEstimatedTracks = previewResult.tracks.size,
            recentlyImportedCount = previewResult.recentlyImportedCount,
            genreMatchedCount = previewResult.genreMatchedCount,
            overlapCount = previewResult.overlapCount,
            previewTracks = previewResult.tracks.take(4),
            activeGenres = preferredGenres
        )
    }

    fun buildQueue(
        allTracks: List<TrackEntity>,
        preferredGenres: Set<String>,
        recentWeight: Float = 0.55f,
        queueLimit: Int = DEFAULT_QUEUE_LIMIT,
        random: Random = Random.Default
    ): SmartShuffleResult {
        if (allTracks.isEmpty()) {
            return SmartShuffleResult(
                tracks = emptyList(),
                recentlyImportedCount = 0,
                genreMatchedCount = 0,
                overlapCount = 0,
                activeGenres = preferredGenres,
                sourceLabel = buildSourceLabel(preferredGenres)
            )
        }

        val clampedWeight = recentWeight.coerceIn(0.20f, 0.85f)
        val effectiveLimit = queueLimit.coerceAtMost(allTracks.size)
        val recentPool = computeRecentPool(allTracks)
        val recentRankById = recentPool.withIndex().associate { (index, track) -> track.id to index }
        val recentIds = recentRankById.keys

        val cleanedGenres = preferredGenres.map { it.trim() }.filter { it.isNotEmpty() }.toSet()

        if (cleanedGenres.isEmpty()) {
            // No user genres selected yet: blend Recently Imported with broader catalog discovery
            val recentScored = recentPool
                .map { track ->
                    val rank = recentRankById[track.id] ?: recentPool.size
                    val recencyScore = 1f - (rank.toFloat() / recentPool.size.coerceAtLeast(1).toFloat())
                    val jitter = random.nextFloat() * 0.65f
                    track to (recencyScore * clampedWeight + jitter)
                }
                .sortedByDescending { it.second }
                .map { it.first }

            val catalogPool = allTracks
                .filter { it.id !in recentIds }
                .shuffled(random)

            val recentTarget = (effectiveLimit * clampedWeight).roundToInt()
                .coerceAtLeast(1)
                .coerceAtMost(recentScored.size)
            val catalogTarget = (effectiveLimit - recentTarget).coerceAtMost(catalogPool.size)

            val combined = interleaveBuckets(
                primary = recentScored.take(recentTarget),
                secondary = catalogPool.take(catalogTarget),
                primaryRatio = clampedWeight
            ).toMutableList()

            if (combined.size < effectiveLimit) {
                val usedIds = combined.mapTo(HashSet()) { it.id }
                val extras = (recentScored + catalogPool).filter { it.id !in usedIds }
                combined.addAll(extras.take(effectiveLimit - combined.size))
            }

            val finalTracks = spreadConsecutiveArtists(combined.take(effectiveLimit))
            val recentCount = finalTracks.count { it.id in recentIds }
            return SmartShuffleResult(
                tracks = finalTracks,
                recentlyImportedCount = recentCount,
                genreMatchedCount = 0,
                overlapCount = 0,
                activeGenres = emptySet(),
                sourceLabel = buildSourceLabel(emptySet())
            )
        }

        val genreMatchedTracks = allTracks.filter { matchesPreferredGenres(it.genre, cleanedGenres) }
        val genreIds = genreMatchedTracks.mapTo(HashSet()) { it.id }

        // Tier 1: Sweet spot — both Recently Imported AND matches user genre preferences
        val overlap = allTracks
            .filter { it.id in recentIds && it.id in genreIds }
            .map { track ->
                val rank = recentRankById[track.id] ?: recentPool.size
                val recencyBoost = 1f - (rank.toFloat() / recentPool.size.coerceAtLeast(1).toFloat())
                track to (1.2f + recencyBoost * 0.5f + random.nextFloat() * 0.6f)
            }
            .sortedByDescending { it.second }
            .map { it.first }

        // Tier 2A: Recently Imported (outside preferred genres)
        val recentOnly = recentPool
            .filter { it.id !in genreIds }
            .map { track ->
                val rank = recentRankById[track.id] ?: recentPool.size
                val recencyBoost = 1f - (rank.toFloat() / recentPool.size.coerceAtLeast(1).toFloat())
                track to (recencyBoost * 0.7f + random.nextFloat() * 0.65f)
            }
            .sortedByDescending { it.second }
            .map { it.first }

        // Tier 2B: Preferred Genres (older catalog gems)
        val genreOnly = genreMatchedTracks
            .filter { it.id !in recentIds }
            .map { track ->
                val favoriteBoost = if (track.isFavorite) 0.25f else 0f
                track to (favoriteBoost + random.nextFloat())
            }
            .sortedByDescending { it.second }
            .map { it.first }

        val selected = mutableListOf<TrackEntity>()
        val selectedIds = HashSet<Long>()

        // Always prioritize intersection tracks first
        for (track in overlap) {
            if (selected.size >= effectiveLimit) break
            if (selectedIds.add(track.id)) selected.add(track)
        }

        val remainingSlots = (effectiveLimit - selected.size).coerceAtLeast(0)
        if (remainingSlots > 0) {
            val desiredRecentSlots = (remainingSlots * clampedWeight).roundToInt()
            val desiredGenreSlots = remainingSlots - desiredRecentSlots

            val pickedRecent = recentOnly.take(desiredRecentSlots)
            val pickedGenre = genreOnly.take(desiredGenreSlots)

            val interleaved = interleaveBuckets(
                primary = pickedRecent,
                secondary = pickedGenre,
                primaryRatio = clampedWeight
            )
            for (track in interleaved) {
                if (selected.size >= effectiveLimit) break
                if (selectedIds.add(track.id)) selected.add(track)
            }

            // Fill remaining budget from whichever curated bucket still has tracks
            if (selected.size < effectiveLimit) {
                val leftoverCurated = if (clampedWeight >= 0.5f) {
                    recentOnly.drop(pickedRecent.size) + genreOnly.drop(pickedGenre.size)
                } else {
                    genreOnly.drop(pickedGenre.size) + recentOnly.drop(pickedRecent.size)
                }
                for (track in leftoverCurated) {
                    if (selected.size >= effectiveLimit) break
                    if (selectedIds.add(track.id)) selected.add(track)
                }
            }
        }

        // Only spill into non-matching catalog if the combined curated pool is tiny (< 5 tracks)
        if (selected.size < minOf(5, effectiveLimit)) {
            val fallback = allTracks
                .filter { it.id !in selectedIds }
                .shuffled(random)
            for (track in fallback) {
                if (selected.size >= minOf(5, effectiveLimit)) break
                if (selectedIds.add(track.id)) selected.add(track)
            }
        }

        val finalTracks = spreadConsecutiveArtists(selected)
        val finalRecentCount = finalTracks.count { it.id in recentIds }
        val finalGenreCount = finalTracks.count { it.id in genreIds }
        val finalOverlapCount = finalTracks.count { it.id in recentIds && it.id in genreIds }

        return SmartShuffleResult(
            tracks = finalTracks,
            recentlyImportedCount = finalRecentCount,
            genreMatchedCount = finalGenreCount,
            overlapCount = finalOverlapCount,
            activeGenres = cleanedGenres,
            sourceLabel = buildSourceLabel(cleanedGenres)
        )
    }

    private fun interleaveBuckets(
        primary: List<TrackEntity>,
        secondary: List<TrackEntity>,
        primaryRatio: Float
    ): List<TrackEntity> {
        if (primary.isEmpty()) return secondary
        if (secondary.isEmpty()) return primary

        val result = ArrayList<TrackEntity>(primary.size + secondary.size)
        var i = 0
        var j = 0
        var accumulator = 0f

        while (i < primary.size && j < secondary.size) {
            accumulator += primaryRatio
            if (accumulator >= 0.5f) {
                result.add(primary[i++])
                accumulator -= 1f
            } else {
                result.add(secondary[j++])
            }
        }
        while (i < primary.size) result.add(primary[i++])
        while (j < secondary.size) result.add(secondary[j++])
        return result
    }

    /**
     * Lightly reorders tracks so consecutive entries avoid repeating the same artist
     * while preserving overall priority ordering.
     */
    private fun spreadConsecutiveArtists(tracks: List<TrackEntity>): List<TrackEntity> {
        if (tracks.size <= 2) return tracks
        val remaining = tracks.toMutableList()
        val result = ArrayList<TrackEntity>(tracks.size)
        result.add(remaining.removeAt(0))

        while (remaining.isNotEmpty()) {
            val prevArtist = result.last().artist.trim().lowercase()
            val candidateIndex = remaining.indexOfFirst { track ->
                val artist = track.artist.trim().lowercase()
                artist.isEmpty() || artist == "unknown artist" || artist != prevArtist
            }
            val nextIdx = if (candidateIndex >= 0) candidateIndex else 0
            result.add(remaining.removeAt(nextIdx))
        }
        return result
    }

    private fun buildSourceLabel(activeGenres: Set<String>): String {
        val sorted = activeGenres.sortedBy { it.lowercase() }
        return when {
            sorted.isEmpty() -> "Smart Shuffle · Recent"
            sorted.size <= 2 -> "Smart Shuffle · ${sorted.joinToString(", ")}"
            else -> "Smart Shuffle · ${sorted.first()} +${sorted.size - 1}"
        }
    }
}
