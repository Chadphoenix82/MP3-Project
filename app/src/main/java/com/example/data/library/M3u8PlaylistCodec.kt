package com.example.data.library

import android.content.Context
import android.net.Uri
import com.example.data.db.TrackEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

data class M3u8ImportResult(
    val playlistName: String,
    val matchedTracks: List<TrackEntity>,
    val unmatchedCount: Int
)

/**
 * Standards-compliant UTF-8 M3U8 playlist serializer and importer.
 */
object M3u8PlaylistCodec {

    suspend fun exportToUri(
        context: Context,
        targetUri: Uri,
        playlistName: String,
        tracks: List<TrackEntity>
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openOutputStream(targetUri, "wt")?.use { out ->
                OutputStreamWriter(out, Charsets.UTF_8).use { writer ->
                    writer.appendLine("#EXTM3U")
                    writer.appendLine("#PLAYLIST:$playlistName")
                    tracks.forEach { track ->
                        val sec = (track.durationMs / 1000L).coerceAtLeast(0L)
                        writer.appendLine("#EXTINF:$sec,${track.artist} - ${track.title}")
                        writer.appendLine(track.uri)
                    }
                    writer.flush()
                }
            } ?: throw IllegalStateException("Could not open target .m3u8 file for writing")
            tracks.size
        }
    }

    suspend fun importFromUri(
        context: Context,
        sourceUri: Uri,
        libraryTracks: List<TrackEntity>
    ): Result<M3u8ImportResult> = withContext(Dispatchers.IO) {
        runCatching {
            val byUri = libraryTracks.associateBy { it.uri }
            val byFileName = libraryTracks.associateBy { it.fileName.lowercase() }
            val byArtistTitle = libraryTracks.associateBy {
                "${it.artist.lowercase()} - ${it.title.lowercase()}"
            }
            val byTitle = libraryTracks.associateBy { it.title.lowercase() }

            var parsedPlaylistName = sourceUri.lastPathSegment
                ?.substringAfterLast('/')
                ?.substringAfterLast(':')
                ?.substringBeforeLast('.')
                ?.ifBlank { null }
                ?: "Imported M3U8"

            val matched = mutableListOf<TrackEntity>()
            val seenIds = mutableSetOf<Long>()
            var unmatched = 0
            var pendingExtInfTitle: String? = null

            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                BufferedReader(InputStreamReader(input, Charsets.UTF_8)).useLines { lines ->
                    lines.forEach { rawLine ->
                        val line = rawLine.trim()
                        if (line.isEmpty()) return@forEach
                        when {
                            line.startsWith("#PLAYLIST:", ignoreCase = true) -> {
                                val name = line.substringAfter(':').trim()
                                if (name.isNotEmpty()) parsedPlaylistName = name
                            }
                            line.startsWith("#EXTINF:", ignoreCase = true) -> {
                                pendingExtInfTitle = line.substringAfter(',', "").trim().takeIf { it.isNotEmpty() }
                            }
                            line.startsWith("#") -> Unit
                            else -> {
                                val fileCandidate = line
                                    .substringAfterLast('/')
                                    .substringAfterLast('\\')
                                    .lowercase()
                                val found = byUri[line]
                                    ?: byFileName[fileCandidate]
                                    ?: pendingExtInfTitle?.lowercase()?.let { ext ->
                                        byArtistTitle[ext]
                                            ?: byTitle[ext.substringAfter(" - ", ext).trim()]
                                    }
                                if (found != null) {
                                    if (seenIds.add(found.id)) {
                                        matched.add(found)
                                    }
                                } else {
                                    unmatched++
                                }
                                pendingExtInfTitle = null
                            }
                        }
                    }
                }
            } ?: throw IllegalStateException("Unable to read selected .m3u8 file")

            M3u8ImportResult(
                playlistName = parsedPlaylistName,
                matchedTracks = matched,
                unmatchedCount = unmatched
            )
        }
    }
}
