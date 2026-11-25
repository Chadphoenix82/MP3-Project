package com.example.data.library

import android.net.Uri
import com.example.data.db.TrackDao
import com.example.data.db.TrackEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class SyncedLyricLine(
    val timestampMs: Long,
    val text: String
)

data class ParsedLyrics(
    val rawText: String = "",
    val syncedLines: List<SyncedLyricLine> = emptyList(),
    val plainLines: List<String> = emptyList(),
    val sourceLabel: String = ""
) {
    val hasSynced: Boolean get() = syncedLines.isNotEmpty()
    val hasAny: Boolean get() = syncedLines.isNotEmpty() || plainLines.isNotEmpty()
}

/**
 * Resolves time-synced .lrc lyrics and static lyrics with local sibling-file lookup,
 * Room caching (tracks.lyricsLrc), and public LRCLIB web metadata fetch with clean offline fallback.
 */
class LyricsRepository(
    private val trackDao: TrackDao
) {
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(7, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    suspend fun loadLocalOrCachedLyrics(track: TrackEntity): ParsedLyrics = withContext(Dispatchers.IO) {
        if (track.lyricsLrc.isNotBlank()) {
            return@withContext parseLyrics(track.lyricsLrc, sourceLabel = "Cached in Room")
        }

        // Check sibling .lrc file next to local file:// audio track
        val localLrc = readSiblingLrcFile(track.uri)
        if (!localLrc.isNullOrBlank()) {
            trackDao.updateLyrics(track.id, localLrc)
            return@withContext parseLyrics(localLrc, sourceLabel = "Local .lrc Sidecar")
        }

        // Fallback to static lyrics stored in track notes if user pasted them
        if (track.notes.lines().size >= 3 && track.notes.length > 40) {
            return@withContext parseLyrics(track.notes, sourceLabel = "Embedded Notes")
        }

        ParsedLyrics()
    }

    suspend fun saveCustomLyrics(trackId: Long, rawLyrics: String): ParsedLyrics = withContext(Dispatchers.IO) {
        val cleaned = rawLyrics.trim()
        trackDao.updateLyrics(trackId, cleaned)
        parseLyrics(cleaned, sourceLabel = if (cleaned.isNotEmpty()) "Saved Locally" else "")
    }

    suspend fun fetchWebLyricsAndCache(track: TrackEntity): Result<ParsedLyrics> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanTitle = track.title
                .replace(Regex("""\s*\(.*?\)\s*"""), " ")
                .replace(Regex("""\s*\[.*?\]\s*"""), " ")
                .trim()
            val cleanArtist = track.artist.takeUnless {
                it.equals("Unknown Artist", ignoreCase = true)
            }?.trim().orEmpty()

            val exact = queryLrcLibExact(cleanTitle, cleanArtist, track.album, (track.durationMs / 1000L).toInt())
            val raw = exact ?: queryLrcLibSearch(cleanTitle, cleanArtist)
                ?: throw IllegalStateException("No matching lyrics found online for \"${track.title}\"")

            trackDao.updateLyrics(track.id, raw)
            parseLyrics(raw, sourceLabel = "Web Metadata (LRCLIB · Cached)")
        }
    }

    private fun queryLrcLibExact(
        title: String,
        artist: String,
        album: String,
        durationSec: Int
    ): String? {
        if (title.isBlank() || artist.isBlank()) return null
        val url = buildString {
            append("https://lrclib.net/api/get?track_name=")
            append(URLEncoder.encode(title, "UTF-8"))
            append("&artist_name=")
            append(URLEncoder.encode(artist, "UTF-8"))
            if (album.isNotBlank() && !album.equals("Unknown Album", ignoreCase = true)) {
                append("&album_name=")
                append(URLEncoder.encode(album, "UTF-8"))
            }
            if (durationSec > 10) {
                append("&duration=")
                append(durationSec)
            }
        }
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "PhotonAudioDeck/4.0")
            .build()
        return runCatching {
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val body = resp.body?.string().orEmpty()
                if (body.isBlank()) return@use null
                val obj = JSONObject(body)
                obj.optString("syncedLyrics").takeIf { it.isNotBlank() }
                    ?: obj.optString("plainLyrics").takeIf { it.isNotBlank() }
            }
        }.getOrNull()
    }

    private fun queryLrcLibSearch(title: String, artist: String): String? {
        val q = "$artist $title".trim()
        if (q.isBlank()) return null
        val url = "https://lrclib.net/api/search?q=${URLEncoder.encode(q, "UTF-8")}"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "PhotonAudioDeck/4.0")
            .build()
        return runCatching {
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val body = resp.body?.string().orEmpty()
                if (body.isBlank()) return@use null
                val arr = JSONArray(body)
                var fallbackPlain: String? = null
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    val synced = item.optString("syncedLyrics").takeIf { it.isNotBlank() }
                    if (synced != null) return@use synced
                    if (fallbackPlain == null) {
                        fallbackPlain = item.optString("plainLyrics").takeIf { it.isNotBlank() }
                    }
                }
                fallbackPlain
            }
        }.getOrNull()
    }

    private fun readSiblingLrcFile(uriString: String): String? {
        return runCatching {
            val parsed = Uri.parse(uriString)
            if (parsed.scheme != "file") return@runCatching null
            val audioFile = File(parsed.path ?: return@runCatching null)
            val lrcFile = File(audioFile.parentFile, "${audioFile.nameWithoutExtension}.lrc")
            if (lrcFile.exists() && lrcFile.canRead() && lrcFile.length() in 1..512_000) {
                lrcFile.readText(Charsets.UTF_8)
            } else {
                null
            }
        }.getOrNull()
    }

    companion object {
        private val LRC_TAG_REGEX = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")

        fun parseLyrics(raw: String, sourceLabel: String = ""): ParsedLyrics {
            if (raw.isBlank()) return ParsedLyrics()
            val synced = mutableListOf<SyncedLyricLine>()
            val plain = mutableListOf<String>()

            raw.lineSequence().forEach { line ->
                val matches = LRC_TAG_REGEX.findAll(line).toList()
                if (matches.isNotEmpty()) {
                    val lyricText = line.replace(LRC_TAG_REGEX, "").trim()
                    if (lyricText.isNotEmpty()) {
                        matches.forEach { m ->
                            val mins = m.groupValues[1].toLongOrNull() ?: 0L
                            val secs = m.groupValues[2].toLongOrNull() ?: 0L
                            val fracStr = m.groupValues.getOrNull(3).orEmpty()
                            val millis = when (fracStr.length) {
                                1 -> (fracStr.toLongOrNull() ?: 0L) * 100L
                                2 -> (fracStr.toLongOrNull() ?: 0L) * 10L
                                3 -> fracStr.toLongOrNull() ?: 0L
                                else -> 0L
                            }
                            synced.add(
                                SyncedLyricLine(
                                    timestampMs = mins * 60_000L + secs * 1_000L + millis,
                                    text = lyricText
                                )
                            )
                        }
                    }
                } else {
                    val trimmed = line.trim()
                    if (trimmed.isNotEmpty() && !trimmed.startsWith("[")) {
                        plain.add(trimmed)
                    }
                }
            }

            synced.sortBy { it.timestampMs }
            return ParsedLyrics(
                rawText = raw,
                syncedLines = synced,
                plainLines = if (synced.isEmpty()) plain else synced.map { it.text },
                sourceLabel = sourceLabel
            )
        }
    }
}
