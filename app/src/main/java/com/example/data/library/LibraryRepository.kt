package com.example.data.library

import android.net.Uri
import com.example.data.db.ExcludedFolderDao
import com.example.data.db.ExcludedFolderEntity
import com.example.data.db.GenrePlayStat
import com.example.data.db.ListeningHistoryDao
import com.example.data.db.ListeningHistoryEntity
import com.example.data.db.PlaylistDao
import com.example.data.db.PlaylistEntity
import com.example.data.db.PlaylistTrackCrossRef
import com.example.data.db.PlaylistWithSummary
import com.example.data.db.TrackDao
import com.example.data.db.TrackEntity
import com.example.data.db.TrackPlayStat
import com.example.data.db.WatchedFolderDao
import com.example.data.db.WatchedFolderEntity
import com.example.data.model.MetadataSuggestion
import com.example.data.network.MetadataAutoCompleteRepository
import com.example.data.prefs.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first

class LibraryRepository(
    private val trackDao: TrackDao,
    private val playlistDao: PlaylistDao,
    private val watchedFolderDao: WatchedFolderDao,
    private val listeningHistoryDao: ListeningHistoryDao,
    private val excludedFolderDao: ExcludedFolderDao,
    private val audioImporter: AudioImporter,
    private val metadataRepository: MetadataAutoCompleteRepository,
    private val folderMonitorManager: FolderMonitorManager,
    private val wavGenerator: StudioWavGenerator,
    private val settingsRepository: SettingsRepository
) {
    val allTracks: Flow<List<TrackEntity>> = trackDao.observeAllTracks()
    val playlists: Flow<List<PlaylistWithSummary>> = playlistDao.observePlaylistsWithSummary()
    val watchedFolders: Flow<List<WatchedFolderEntity>> = watchedFolderDao.observeWatchedFolders()
    val excludedFolders: Flow<List<ExcludedFolderEntity>> = excludedFolderDao.observeExcludedFolders()
    val recentHistory: Flow<List<ListeningHistoryEntity>> = listeningHistoryDao.observeRecentHistory(150)
    val trackPlayStats: Flow<List<TrackPlayStat>> = listeningHistoryDao.observeTrackPlayStats()
    val genrePlayStats: Flow<List<GenrePlayStat>> = listeningHistoryDao.observeGenrePlayStats()
    val totalPlayCount: Flow<Int> = listeningHistoryDao.observeTotalPlayCount()
    val monitorStatus: StateFlow<FolderMonitorStatus> = folderMonitorManager.status

    fun startFolderMonitoring() {
        folderMonitorManager.startMonitoring()
    }

    fun observePlaylistTracks(playlistId: Long): Flow<List<TrackEntity>> =
        playlistDao.observePlaylistTracks(playlistId)

    // ---- Listening History (Smart Queue Source) ----

    suspend fun recordTrackPlayed(track: TrackEntity) {
        listeningHistoryDao.insertEvent(
            ListeningHistoryEntity(
                trackId = track.id,
                genre = track.genre,
                artist = track.artist,
                playedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun clearListeningHistory() {
        listeningHistoryDao.clearHistory()
    }

    // ---- Audio Import, Auto-Scan & Exclusions ----

    suspend fun importPickedFiles(uris: List<Uri>): FolderScanResult {
        var addedCount = 0
        var skippedCount = 0
        var nextOrder = trackDao.getMaxSortOrder() + 1
        val settings = settingsRepository.settingsFlow.first()
        val autoEnrich = settings.autoEnrichMetadata
        val excludedTypes = settings.excludedFileTypes

        for (uri in uris) {
            audioImporter.takePersistablePermission(uri)
            if (trackDao.getTrackByUri(uri.toString()) != null) continue
            val entity = audioImporter.extractTrackEntity(uri = uri, sortOrder = nextOrder) ?: continue
            val ext = entity.fileName.substringAfterLast('.', "").uppercase()
            if (entity.format.uppercase() in excludedTypes || (ext.isNotEmpty() && ext in excludedTypes)) {
                skippedCount++
                continue
            }
            val id = trackDao.insertTrack(entity)
            if (id > 0L) {
                nextOrder++
                addedCount++
                if (autoEnrich) {
                    runCatching {
                        val query = if (entity.artist != "Unknown Artist") {
                            "${entity.artist} ${entity.title}"
                        } else {
                            entity.title
                        }
                        val best = metadataRepository.searchMetadata(
                            rawQuery = query,
                            hintTitle = entity.title,
                            hintArtist = entity.artist
                        ).firstOrNull()
                        if (best != null) {
                            applyMetadata(id, best)
                        }
                    }
                }
            }
        }
        return FolderScanResult(importedCount = addedCount, skippedByExclusionCount = skippedCount)
    }

    suspend fun addWatchedFolder(treeUri: Uri): String =
        folderMonitorManager.addWatchedSafFolder(treeUri)

    suspend fun removeWatchedFolder(folderUri: String) =
        folderMonitorManager.removeWatchedFolder(folderUri)

    suspend fun addExcludedSafFolder(treeUri: Uri): String =
        folderMonitorManager.addExcludedSafFolder(treeUri)

    suspend fun addExcludedFolderName(folderName: String): String =
        folderMonitorManager.addExcludedFolderName(folderName)

    suspend fun removeExcludedFolder(identifier: String) =
        folderMonitorManager.removeExcludedFolder(identifier)

    suspend fun scanWatchedFoldersNow(): FolderScanResult =
        folderMonitorManager.scanAllWatchedFolders(triggerMetadataEnrichment = true)

    suspend fun generateStarterStudioWavTracks(): FolderScanResult {
        wavGenerator.generateStarterPack()
        return folderMonitorManager.scanAllWatchedFolders(triggerMetadataEnrichment = true)
    }

    suspend fun dropTestTrackIntoWatchedFolder(): String {
        val created = wavGenerator.dropNextTestWavIntoWatchedFolder()
        folderMonitorManager.scanAllWatchedFolders(triggerMetadataEnrichment = true)
        return created.name
    }

    suspend fun searchWebMetadata(
        query: String,
        hintTitle: String? = null,
        hintArtist: String? = null
    ): List<MetadataSuggestion> = metadataRepository.searchMetadata(
        rawQuery = query,
        hintTitle = hintTitle,
        hintArtist = hintArtist
    )

    suspend fun savePickedArtworkLocally(uri: Uri, trackId: Long): String? =
        audioImporter.savePickedArtworkLocally(uri, trackId)

    suspend fun applyMetadata(trackId: Long, suggestion: MetadataSuggestion) {
        trackDao.updateMetadata(
            trackId = trackId,
            title = suggestion.title,
            artist = suggestion.artist,
            album = suggestion.album,
            genre = suggestion.genre,
            publisher = suggestion.publisher,
            releaseDate = suggestion.releaseDate,
            releaseYear = suggestion.releaseYear,
            trackNumber = suggestion.trackNumber,
            artworkUri = suggestion.artworkUrl,
            replaceArtwork = !suggestion.artworkUrl.isNullOrBlank()
        )
    }

    suspend fun updateTrackMetadataManual(
        trackId: Long,
        title: String,
        artist: String,
        album: String,
        genre: String,
        publisher: String,
        releaseDate: String,
        trackNumber: Int? = null,
        composer: String = "",
        notes: String = "",
        artworkUri: String? = null,
        replaceArtwork: Boolean = true
    ) {
        trackDao.updateMetadata(
            trackId = trackId,
            title = title.trim().ifBlank { "Untitled Track" },
            artist = artist.trim().ifBlank { "Unknown Artist" },
            album = album.trim().ifBlank { "Unknown Album" },
            genre = genre.trim().ifBlank { "Music" },
            publisher = publisher.trim().ifBlank { "Independent" },
            releaseDate = releaseDate.trim(),
            releaseYear = MetadataAutoCompleteRepository.extractYear(releaseDate),
            trackNumber = trackNumber,
            composer = composer.trim(),
            notes = notes.trim(),
            artworkUri = artworkUri?.trim()?.takeIf { it.isNotBlank() },
            replaceArtwork = replaceArtwork
        )
    }

    suspend fun reorderLibraryTracks(orderedTrackIds: List<Long>) {
        trackDao.updateSortOrders(orderedTrackIds)
    }

    suspend fun deleteTrack(trackId: Long) {
        trackDao.deleteTrack(trackId)
    }

    // ---- Custom Playlists ----

    suspend fun createPlaylist(name: String, description: String): Long {
        return playlistDao.insertPlaylist(
            PlaylistEntity(name = name.trim(), description = description.trim())
        )
    }

    suspend fun renamePlaylist(playlistId: Long, name: String, description: String) {
        playlistDao.updatePlaylist(playlistId, name.trim(), description.trim())
    }

    suspend fun deletePlaylist(playlistId: Long) {
        playlistDao.deletePlaylist(playlistId)
    }

    suspend fun addTrackToPlaylist(playlistId: Long, trackId: Long) {
        val nextPos = playlistDao.getMaxPosition(playlistId) + 1
        playlistDao.insertPlaylistTrack(
            PlaylistTrackCrossRef(playlistId = playlistId, trackId = trackId, position = nextPos)
        )
    }

    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: Long) {
        playlistDao.removeTrackFromPlaylist(playlistId, trackId)
    }

    suspend fun reorderPlaylistTracks(playlistId: Long, orderedTrackIds: List<Long>) {
        playlistDao.reorderPlaylistTracks(playlistId, orderedTrackIds)
    }
}
