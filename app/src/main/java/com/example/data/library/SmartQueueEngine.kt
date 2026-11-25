package com.example.data.library

import com.example.data.db.GenrePlayStat
import com.example.data.db.ListeningHistoryEntity
import com.example.data.db.TrackEntity
import com.example.data.db.TrackPlayStat
import kotlin.math.abs

data class SmartQueueSuggestion(
    val track: TrackEntity,
    val matchPercent: Int,
    val reason: String,
    val playCount: Int
)

/**
 * Computes next-track recommendations directly from Room library metadata and
 * persisted Room listening history (genre affinity, artist/label continuity,
 * personal play frequency, and recency cooldown).
 */
object SmartQueueEngine {

    private const val RECENT_COOLDOWN_MS = 10 * 60 * 1000L // 10 minutes
    private const val REDISCOVER_WINDOW_MS = 24 * 60 * 60 * 1000L // 24 hours

    fun buildSuggestions(
        allTracks: List<TrackEntity>,
        currentTrack: TrackEntity?,
        upcomingQueueTrackIds: Set<Long>,
        recentHistory: List<ListeningHistoryEntity>,
        trackStats: List<TrackPlayStat>,
        genreStats: List<GenrePlayStat>,
        nowMs: Long = System.currentTimeMillis(),
        limit: Int = 10
    ): List<SmartQueueSuggestion> {
        if (allTracks.isEmpty()) return emptyList()

        val statByTrackId = trackStats.associateBy { it.trackId }
        val totalGenrePlays = genreStats.sumOf { it.playCount }.coerceAtLeast(1)
        val genreAffinityMap = genreStats.associate {
            it.genre.trim().lowercase() to (it.playCount.toFloat() / totalGenrePlays.toFloat())
        }
        val artistPlayCounts = recentHistory.groupingBy { it.artist.trim().lowercase() }.eachCount()
        val recentTrackIds = recentHistory.take(3).map { it.trackId }.toSet()

        val currentGenre = currentTrack?.genre?.trim()?.lowercase().orEmpty()
        val currentArtist = currentTrack?.artist?.trim()?.lowercase().orEmpty()
        val currentPublisher = currentTrack?.publisher?.trim()?.lowercase().orEmpty()
        val currentYear = currentTrack?.releaseYear

        val candidates = allTracks.filter { track ->
            track.id != currentTrack?.id
        }

        val scored = candidates.map { track ->
            val trackGenre = track.genre.trim().lowercase()
            val trackArtist = track.artist.trim().lowercase()
            val trackPublisher = track.publisher.trim().lowercase()
            val stat = statByTrackId[track.id]
            val plays = stat?.playCount ?: 0

            var score = 15f
            val reasons = mutableListOf<Pair<Float, String>>()

            // 1. Current track genre match (primary Smart Queue signal)
            if (currentGenre.isNotBlank() && currentGenre != "unknown genre" && trackGenre.isNotBlank()) {
                when {
                    trackGenre == currentGenre -> {
                        score += 42f
                        reasons.add(42f to "Same Genre · ${track.genre}")
                    }
                    trackGenre.contains(currentGenre) || currentGenre.contains(trackGenre) -> {
                        score += 24f
                        reasons.add(24f to "Related Genre · ${track.genre}")
                    }
                }
            }

            // 2. Listening history genre affinity
            val historyGenreWeight = genreAffinityMap[trackGenre] ?: 0f
            if (historyGenreWeight > 0f) {
                val boost = (historyGenreWeight * 24f).coerceAtMost(24f)
                score += boost
                reasons.add(boost to "Top Genre in History")
            }

            // 3. Track listening frequency & rediscovery
            if (plays > 0) {
                val freqBoost = (plays * 5f).coerceAtMost(20f)
                score += freqBoost
                val lastPlayed = stat?.lastPlayedAt ?: 0L
                val ageMs = (nowMs - lastPlayed).coerceAtLeast(0L)
                when {
                    ageMs < RECENT_COOLDOWN_MS || track.id in recentTrackIds -> {
                        score -= 28f
                    }
                    ageMs > REDISCOVER_WINDOW_MS -> {
                        score += 10f
                        reasons.add(freqBoost + 10f to "Rediscover · $plays plays")
                    }
                    else -> {
                        reasons.add(freqBoost to "Frequent Favorite · $plays plays")
                    }
                }
            } else {
                // Slight discovery boost for unplayed tracks matching current genre
                score += 6f
                reasons.add(6f to "Fresh Discovery")
            }

            // 4. Artist affinity from current track & listening history
            if (currentArtist.isNotBlank() && currentArtist != "unknown artist" && trackArtist == currentArtist) {
                score += 18f
                reasons.add(18f to "Same Artist · ${track.artist}")
            } else {
                val artistPlays = artistPlayCounts[trackArtist] ?: 0
                if (artistPlays >= 2) {
                    val artistBoost = (artistPlays * 3.5f).coerceAtMost(14f)
                    score += artistBoost
                    reasons.add(artistBoost to "Liked Artist")
                }
            }

            // 5. Publisher & release era continuity
            if (
                currentPublisher.isNotBlank() &&
                currentPublisher != "independent" &&
                trackPublisher == currentPublisher
            ) {
                score += 9f
                reasons.add(9f to "Same Label · ${track.publisher}")
            }
            if (currentYear != null && track.releaseYear != null && abs(currentYear - track.releaseYear) <= 3) {
                score += 6f
            }

            // Slightly deprioritize tracks already sitting in the upcoming queue
            if (track.id in upcomingQueueTrackIds) {
                score -= 18f
            }

            val topReason = reasons.maxByOrNull { it.first }?.second
                ?: if (track.genre.isNotBlank()) "${track.genre} Vibe" else "Library Pick"

            val matchPercent = ((score / 100f) * 100f).toInt().coerceIn(48, 99)

            SmartQueueSuggestion(
                track = track,
                matchPercent = matchPercent,
                reason = topReason,
                playCount = plays
            ) to score
        }

        return scored
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
    }
}
