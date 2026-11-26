package com.example.data.library

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.Settings
import androidx.core.content.FileProvider
import com.example.data.db.TrackEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class TrackUtilityOutcome(
    val success: Boolean,
    val message: String
)

/**
 * Handles device-level audio utility actions triggered from TrackActionBottomSheet:
 * Share Audio File, Set as Ringtone, and Delete physical file from device storage.
 */
object TrackUtilityActions {

    fun mimeTypeForFormat(format: String): String = when (format.trim().uppercase()) {
        "FLAC" -> "audio/flac"
        "WAV" -> "audio/wav"
        "MP3" -> "audio/mpeg"
        "OPUS" -> "audio/opus"
        "OGG" -> "audio/ogg"
        "M4A", "AAC", "ALAC" -> "audio/mp4"
        else -> "audio/*"
    }

    fun fileExtensionForFormat(format: String): String = when (format.trim().uppercase()) {
        "FLAC" -> "flac"
        "WAV" -> "wav"
        "MP3" -> "mp3"
        "OPUS" -> "opus"
        "OGG" -> "ogg"
        "M4A", "AAC", "ALAC" -> "m4a"
        else -> "mp3"
    }

    /**
     * Launches Android's system share sheet with Intent.ACTION_SEND and read URI grant.
     */
    fun shareTrack(context: Context, track: TrackEntity): TrackUtilityOutcome {
        return runCatching {
            val shareUri = resolveShareableUri(context, track.uri)
                ?: return TrackUtilityOutcome(false, "Unable to locate audio file to share")
            val mimeType = mimeTypeForFormat(track.format)
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, shareUri)
                putExtra(Intent.EXTRA_SUBJECT, "${track.title} — ${track.artist}")
                putExtra(Intent.EXTRA_TEXT, "${track.title} by ${track.artist}")
                clipData = ClipData.newUri(context.contentResolver, track.title, shareUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(sendIntent, "Share \"${track.title}\"").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            TrackUtilityOutcome(true, "Opening share sheet for \"${track.title}\"")
        }.getOrElse { err ->
            TrackUtilityOutcome(false, "Unable to share file: ${err.localizedMessage ?: "unknown error"}")
        }
    }

    /**
     * Sets the selected track as the device's default ringtone.
     * Prompts the user to enable Modify System Settings if not yet granted.
     */
    suspend fun setTrackAsRingtone(context: Context, track: TrackEntity): TrackUtilityOutcome {
        if (!Settings.System.canWrite(context)) {
            withContext(Dispatchers.Main) {
                runCatching {
                    val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }
            }
            return TrackUtilityOutcome(
                success = false,
                message = "Allow \"Modify system settings\" for Photon Audio, then tap Set as Ringtone again"
            )
        }

        return withContext(Dispatchers.IO) {
            runCatching {
                val parsedUri = Uri.parse(track.uri)
                val mimeType = mimeTypeForFormat(track.format)
                val ext = fileExtensionForFormat(track.format)
                val safeTitle = track.title
                    .ifBlank { track.fileName.substringBeforeLast('.') }
                    .replace(Regex("[^a-zA-Z0-9._\\- ]"), "_")
                    .trim()
                    .take(64)
                    .ifBlank { "photon_ringtone" }

                val targetRingtoneUri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Audio.Media.DISPLAY_NAME, "${safeTitle}_${track.id}.$ext")
                        put(MediaStore.Audio.Media.TITLE, track.title.ifBlank { safeTitle })
                        put(MediaStore.Audio.Media.ARTIST, track.artist)
                        put(MediaStore.Audio.Media.MIME_TYPE, mimeType)
                        put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_RINGTONES)
                        put(MediaStore.Audio.Media.IS_RINGTONE, true)
                        put(MediaStore.Audio.Media.IS_MUSIC, false)
                        put(MediaStore.Audio.Media.IS_PENDING, 1)
                    }
                    val inserted = context.contentResolver.insert(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        values
                    ) ?: error("MediaStore refused ringtone entry")

                    try {
                        openTrackInputStream(context, parsedUri)?.use { input ->
                            context.contentResolver.openOutputStream(inserted)?.use { output ->
                                input.copyTo(output)
                            } ?: error("Cannot write ringtone stream")
                        } ?: error("Source audio file is unreachable")

                        val finalize = ContentValues().apply {
                            put(MediaStore.Audio.Media.IS_PENDING, 0)
                        }
                        context.contentResolver.update(inserted, finalize, null, null)
                        inserted
                    } catch (copyError: Throwable) {
                        context.contentResolver.delete(inserted, null, null)
                        throw copyError
                    }
                } else {
                    if (parsedUri.scheme == "content" && parsedUri.authority == MediaStore.AUTHORITY) {
                        parsedUri
                    } else {
                        resolveShareableUri(context, track.uri)
                            ?: error("Audio URI cannot be assigned as ringtone on this Android version")
                    }
                }

                RingtoneManager.setActualDefaultRingtoneUri(
                    context,
                    RingtoneManager.TYPE_RINGTONE,
                    targetRingtoneUri
                )
                TrackUtilityOutcome(true, "Set \"${track.title}\" as default ringtone")
            }.getOrElse { err ->
                TrackUtilityOutcome(
                    success = false,
                    message = "Could not set ringtone: ${err.localizedMessage ?: "unsupported audio source"}"
                )
            }
        }
    }

    /**
     * Attempts to delete the underlying physical audio file from app storage or SAF storage.
     * Returns true if the physical file was deleted, false if only the library entry could be removed.
     */
    suspend fun deletePhysicalAudioFile(context: Context, track: TrackEntity): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val uri = Uri.parse(track.uri)
                when (uri.scheme?.lowercase()) {
                    "file", null, "" -> {
                        val path = uri.path ?: track.uri
                        val file = File(path)
                        file.exists() && file.delete()
                    }
                    "content" -> {
                        val docDeleted = runCatching {
                            if (DocumentsContract.isDocumentUri(context, uri)) {
                                DocumentsContract.deleteDocument(context.contentResolver, uri)
                            } else {
                                false
                            }
                        }.getOrDefault(false)
                        if (docDeleted) {
                            true
                        } else {
                            context.contentResolver.delete(uri, null, null) > 0
                        }
                    }
                    else -> false
                }
            }.getOrDefault(false)
        }

    private fun resolveShareableUri(context: Context, rawUri: String): Uri? {
        val parsed = Uri.parse(rawUri)
        return when (parsed.scheme?.lowercase()) {
            "content" -> parsed
            "file", null, "" -> {
                val path = parsed.path ?: rawUri
                val file = File(path)
                if (!file.exists()) return null
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
            }
            else -> parsed
        }
    }

    private fun openTrackInputStream(context: Context, uri: Uri) = when (uri.scheme?.lowercase()) {
        "file", null, "" -> {
            val file = File(uri.path ?: uri.toString())
            if (file.exists()) file.inputStream() else null
        }
        else -> context.contentResolver.openInputStream(uri)
    }
}
