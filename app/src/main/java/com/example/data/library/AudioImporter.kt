package com.example.data.library

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.example.data.db.TrackEntity
import com.example.data.model.AudioFormat
import com.example.data.network.MetadataAutoCompleteRepository
import com.example.player.ReplayGainAnalyzer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class AudioImporter(
    private val context: Context,
    private val replayGainAnalyzer: ReplayGainAnalyzer = ReplayGainAnalyzer(context)
) {

    fun takePersistablePermission(uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
    }

    suspend fun extractTrackEntity(
        uri: Uri,
        sortOrder: Int,
        folderUri: String? = null,
        explicitFileName: String? = null,
        explicitMimeType: String? = null
    ): TrackEntity? = withContext(Dispatchers.IO) {
        val fileName = explicitFileName ?: resolveDisplayName(uri) ?: uri.lastPathSegment ?: "Untitled Audio"
        val mimeType = explicitMimeType ?: runCatching { context.contentResolver.getType(uri) }.getOrNull()
        val format = AudioFormat.fromMimeOrName(mimeType, fileName)

        // Strictly support MP3, FLAC, and WAV (plus fallback if generic audio/* was explicitly picked by user)
        if (folderUri != null && format == AudioFormat.OTHER) {
            return@withContext null
        }

        val retriever = MediaMetadataRetriever()
        var title = MetadataAutoCompleteRepository.cleanSearchQuery(fileName).ifBlank { fileName }
        var artist = "Unknown Artist"
        var album = "Unknown Album"
        var genre = "Electronic"
        var releaseDate = ""
        var releaseYear: Int? = null
        var trackNumber: Int? = null
        var composer = ""
        var durationMs = 0L
        var bitrateKbps = 0
        var sampleRateHz = 44100
        var artworkUri: String? = null

        try {
            if (uri.scheme == "file" && uri.path != null) {
                retriever.setDataSource(uri.path)
            } else {
                retriever.setDataSource(context, uri)
            }

            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?.takeIf { it.isNotBlank() }?.let { title = it.trim() }

            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?.takeIf { it.isNotBlank() }?.let { artist = it.trim() }

            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                ?.takeIf { it.isNotBlank() }?.let { album = it.trim() }

            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
                ?.takeIf { it.isNotBlank() }?.let { genre = it.trim() }

            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COMPOSER)
                ?.takeIf { it.isNotBlank() }?.let { composer = it.trim() }

            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
                ?.trim()
                ?.substringBefore("/")
                ?.toIntOrNull()
                ?.takeIf { it > 0 }
                ?.let { trackNumber = it }

            val dateMeta = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)
            if (!dateMeta.isNullOrBlank()) {
                releaseDate = dateMeta.trim()
                releaseYear = MetadataAutoCompleteRepository.extractYear(releaseDate)
            }

            durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L

            val rawBitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                ?.toIntOrNull() ?: 0
            bitrateKbps = if (rawBitrate > 1000) rawBitrate / 1000 else rawBitrate

            val rawSampleRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)
                ?.toIntOrNull()
            if (rawSampleRate != null && rawSampleRate > 0) {
                sampleRateHz = rawSampleRate
            }

            // Parse Artist - Title if embedded title/artist were absent
            if (artist == "Unknown Artist" && title.contains(" - ")) {
                val parts = title.split(" - ", limit = 2)
                if (parts[0].isNotBlank() && parts[1].isNotBlank()) {
                    artist = parts[0].trim()
                    title = parts[1].trim()
                }
            }

            // Extract embedded cover artwork into local on-device storage
            retriever.embeddedPicture?.let { bytes ->
                artworkUri = saveEmbeddedArt(bytes, uri.toString().hashCode().toString())
            }
        } catch (_: Exception) {
            // Keep clean filename defaults if MediaMetadataRetriever cannot read tags
        } finally {
            runCatching { retriever.release() }
        }

        if (bitrateKbps == 0) {
            bitrateKbps = when (format) {
                AudioFormat.WAV -> 1411
                AudioFormat.FLAC -> 960
                AudioFormat.MP3 -> 320
                AudioFormat.AAC -> 256
                AudioFormat.OGG -> 192
                AudioFormat.OPUS -> 160
                AudioFormat.OTHER -> 256
            }
        }

        val rgTags = runCatching {
            replayGainAnalyzer.readEmbeddedReplayGainTags(uri.toString())
        }.getOrNull()

        TrackEntity(
            uri = uri.toString(),
            fileName = fileName,
            title = title,
            artist = artist,
            album = album,
            genre = genre,
            publisher = "Independent",
            releaseDate = releaseDate,
            releaseYear = releaseYear,
            trackNumber = trackNumber,
            composer = composer,
            notes = "",
            format = format.label,
            bitrateKbps = bitrateKbps,
            sampleRateHz = sampleRateHz,
            durationMs = durationMs.coerceAtLeast(1000L),
            artworkUri = artworkUri,
            sortOrder = sortOrder,
            folderUri = folderUri,
            metadataEnriched = false,
            replayGainTrackDb = rgTags?.trackGainDb,
            replayGainAlbumDb = rgTags?.albumGainDb,
            replayGainTrackPeak = rgTags?.trackPeak,
            replayGainAlbumPeak = rgTags?.albumPeak,
            integratedLufs = rgTags?.integratedLufs,
            replayGainSource = rgTags?.source?.code.orEmpty()
        )
    }

    /**
     * Copies a user-selected album cover image from Android Photo Picker directly into
     * private on-device storage (filesDir/custom_artwork) so it persists permanently offline.
     */
    suspend fun savePickedArtworkLocally(uri: Uri, trackId: Long): String? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.filesDir, "custom_artwork").apply { mkdirs() }
            val destFile = File(dir, "cover_${trackId}_${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext null
            Uri.fromFile(destFile).toString()
        }.getOrNull()
    }

    private fun resolveDisplayName(uri: Uri): String? {
        if (uri.scheme == "file") {
            return uri.path?.let { File(it).name }
        }
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) cursor.getString(idx) else null
                    } else null
                }
        }.getOrNull()
    }

    private fun saveEmbeddedArt(bytes: ByteArray, key: String): String? {
        return runCatching {
            val dir = File(context.filesDir, "embedded_art").apply { mkdirs() }
            val file = File(dir, "art_$key.jpg")
            FileOutputStream(file).use { it.write(bytes) }
            Uri.fromFile(file).toString()
        }.getOrNull()
    }
}
