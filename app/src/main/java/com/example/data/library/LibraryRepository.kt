package com.example.data.library

import android.net.Uri
import com.example.data.db.ArtistPlayStat
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
import com.example.data.model.BatchMetadataPatch
import com.example.data.model.MetadataSuggestion
import com.example.data.model.ReplayGainCalculator
import com.example.data.model.ReplayGainScanProgress
import com.example.data.network.MetadataAutoCompleteRepository
import com.example.data.prefs.SettingsRepository
import com.example.player.ReplayGainAnalyzer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.log10
import kotlin.math.pow

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
    private val settingsRepository: SettingsRepository,
    private val replayGainAnalyzer: ReplayGainAnalyzer
) {
    val allTracks: Flow<List<TrackEntity>> = trackDao.observeAllTracks()
    val playlists: Flow<List<PlaylistWithSummary>> = playlistDao.observePlaylistsWithSummary()
    val watchedFolders: Flow<List<WatchedFolderEntity>> = watchedFolderDao.observeWatchedFolders()
    val excludedFolders: Flow<List<ExcludedFolderEntity>> = excludedFolderDao.observeExcludedFolders()
    val recentHistory: Flow<List<ListeningHistoryEntity>> = listeningHistoryDao.observeRecentHistory(150)
    val trackPlayStats: Flow<List<TrackPlayStat>> = listeningHistoryDao.observeTrackPlayStats()
    val genrePlayStats: Flow<List<GenrePlayStat>> = listeningHistoryDao.observeGenrePlayStats()
    val artistPlayStats: Flow<List<ArtistPlayStat>> = listeningHistoryDao.observeArtistPlayStats()
    val totalPlayCount: Flow<Int> = listeningHistoryDao.observeTotalPlayCount()
    val monitorStatus: StateFlow<FolderMonitorStatus> = folderMonitorManager.status

    private val _replayGainScanProgress = MutableStateFlow(ReplayGainScanProgress())
    val replayGainScanProgress: StateFlow<ReplayGainScanProgress> = _replayGainScanProgress.asStateFlow()
    private val replayGainScanMutex = Mutex()

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

    suspend fun updateBatchTrackMetadata(
        trackIds: List<Long>,
        patch: BatchMetadataPatch
    ): Int {
        val distinctIds = trackIds.distinct()
        if (distinctIds.isEmpty() || !patch.hasAnyChanges) return 0

        val cleanedArtist = patch.artist?.trim()?.ifBlank { "Unknown Artist" }
        val cleanedAlbum = patch.album?.trim()?.ifBlank { "Unknown Album" }
        val cleanedGenre = patch.genre?.trim()?.ifBlank { "Music" }
        val cleanedPublisher = patch.publisher?.trim()?.ifBlank { "Independent" }
        val cleanedReleaseDate = patch.releaseDate?.trim()
        val updateReleaseYear = cleanedReleaseDate != null
        val extractedYear = cleanedReleaseDate?.let { MetadataAutoCompleteRepository.extractYear(it) }
        val cleanedComposer = patch.composer?.trim()
        val cleanedNotes = patch.notes?.trim()
        val cleanedArtwork = if (patch.updateArtwork) {
            patch.artworkUri?.trim()?.takeIf { it.isNotBlank() }
        } else null

        trackDao.updateBatchMetadata(
            trackIds = distinctIds,
            artist = cleanedArtist,
            album = cleanedAlbum,
            genre = cleanedGenre,
            publisher = cleanedPublisher,
            releaseDate = cleanedReleaseDate,
            updateReleaseYear = updateReleaseYear,
            releaseYear = extractedYear,
            autoNumberTracks = patch.autoNumberTracks,
            composer = cleanedComposer,
            notes = cleanedNotes,
            updateArtwork = patch.updateArtwork,
            artworkUri = cleanedArtwork
        )

        if (!cleanedAlbum.isNullOrBlank() && !cleanedAlbum.equals("Unknown Album", ignoreCase = true)) {
            recomputeAlbumLoudnessFor(cleanedAlbum)
        }
        return distinctIds.size
    }

    suspend fun reorderLibraryTracks(orderedTrackIds: List<Long>) {
        trackDao.updateSortOrders(orderedTrackIds)
    }

    suspend fun setTrackFavorite(trackId: Long, isFavorite: Boolean) {
        trackDao.setFavorite(trackId, isFavorite)
    }

    suspend fun updateTrackSoundProfile(trackId: Long, customGainDb: Float, preferredEqPreset: String?) {
        trackDao.updateTrackSoundProfile(
            trackId = trackId,
            customGainDb = customGainDb.coerceIn(-6f, 6f),
            preferredEqPreset = preferredEqPreset?.trim()?.takeIf { it.isNotEmpty() }
        )
    }

    // ---- ReplayGain 2.0 & ITU-R BS.1770-4 LUFS Loudness Scanning ----

    suspend fun scanTrackLoudness(
        track: TrackEntity,
        forceAudioScan: Boolean = false
    ): TrackEntity = withContext(Dispatchers.IO) {
        val result = replayGainAnalyzer.analyzeTrack(
            uriString = track.uri,
            forceAudioScan = forceAudioScan
        )
        if (!result.hasData) return@withContext track

        trackDao.updateTrackLoudness(
            trackId = track.id,
            trackGainDb = result.trackGainDb,
            albumGainDb = result.albumGainDb,
            trackPeak = result.trackPeak,
            albumPeak = result.albumPeak,
            integratedLufs = result.integratedLufs,
            source = result.source.code
        )

        if (track.album.isNotBlank() && !track.album.equals("Unknown Album", ignoreCase = true)) {
            recomputeAlbumLoudnessFor(track.album)
        }

        trackDao.getTrackById(track.id) ?: track.copy(
            replayGainTrackDb = result.trackGainDb,
            replayGainAlbumDb = result.albumGainDb ?: track.replayGainAlbumDb,
            replayGainTrackPeak = result.trackPeak,
            replayGainAlbumPeak = result.albumPeak ?: track.replayGainAlbumPeak,
            integratedLufs = result.integratedLufs,
            replayGainSource = result.source.code
        )
    }

    suspend fun scanLibraryLoudness(forceRescan: Boolean = false): Int = withContext(Dispatchers.IO) {
        if (replayGainScanMutex.isLocked) return@withContext 0
        replayGainScanMutex.withLock {
            val tracks = trackDao.getAllTracksOnce()
            val targets = if (forceRescan) {
                tracks
            } else {
                tracks.filter { it.replayGainTrackDb == null && it.integratedLufs == null }
            }
            if (targets.isEmpty()) {
                recomputeAllAlbumLoudness()
                return@withLock 0
            }

            _replayGainScanProgress.value = ReplayGainScanProgress(
                isScanning = true,
                scannedCount = 0,
                totalCount = targets.size,
                currentTrackTitle = targets.first().title
            )

            var analyzedCount = 0
            try {
                targets.forEachIndexed { idx, track ->
                    _replayGainScanProgress.value = ReplayGainScanProgress(
                        isScanning = true,
                        scannedCount = idx,
                        totalCount = targets.size,
                        currentTrackTitle = track.title
                    )
                    val result = runCatching {
                        replayGainAnalyzer.analyzeTrack(
                            uriString = track.uri,
                            forceAudioScan = forceRescan
                        )
                    }.getOrNull()

                    if (result != null && result.hasData) {
                        trackDao.updateTrackLoudness(
                            trackId = track.id,
                            trackGainDb = result.trackGainDb,
                            albumGainDb = result.albumGainDb,
                            trackPeak = result.trackPeak,
                            albumPeak = result.albumPeak,
                            integratedLufs = result.integratedLufs,
                            source = result.source.code
                        )
                        analyzedCount++
                    }
                    _replayGainScanProgress.value = ReplayGainScanProgress(
                        isScanning = true,
                        scannedCount = idx + 1,
                        totalCount = targets.size,
                        currentTrackTitle = track.title
                    )
                }
                recomputeAllAlbumLoudness()
            } finally {
                _replayGainScanProgress.value = ReplayGainScanProgress(
                    isScanning = false,
                    scannedCount = analyzedCount,
                    totalCount = targets.size,
                    currentTrackTitle = ""
                )
            }
            analyzedCount
        }
    }

    private suspend fun recomputeAlbumLoudnessFor(albumName: String) {
        val albumTracks = trackDao.getAllTracksOnce().filter { it.album == albumName }
        persistAlbumAggregation(albumName, albumTracks)
    }

    private suspend fun recomputeAllAlbumLoudness() {
        val grouped = trackDao.getAllTracksOnce()
            .filter { it.album.isNotBlank() && !it.album.equals("Unknown Album", ignoreCase = true) }
            .groupBy { it.album }
        grouped.forEach { (albumName, albumTracks) ->
            persistAlbumAggregation(albumName, albumTracks)
        }
    }

    private suspend fun persistAlbumAggregation(albumName: String, albumTracks: List<TrackEntity>) {
        if (albumTracks.isEmpty()) return
        // Compute energy-averaged album integrated loudness across all analyzed tracks in the album
        val measuredLufs = albumTracks.mapNotNull { t ->
            t.integratedLufs ?: t.replayGainTrackDb?.let { ReplayGainCalculator.RG2_REFERENCE_LUFS - it }
        }
        if (measuredLufs.isEmpty()) return

        // Convert LUFS to linear acoustic energy, average across tracks, and convert back to LUFS
        val meanEnergy = measuredLufs.map { lufs -> 10.0.pow(lufs.toDouble() / 10.0) }.average()
        if (meanEnergy <= 0.0) return
        val albumLufs = (10.0 * log10(meanEnergy)).toFloat()
        val albumGainDb = (ReplayGainCalculator.RG2_REFERENCE_LUFS - albumLufs).coerceIn(-18f, 18f)
        val albumPeak = albumTracks.mapNotNull { it.replayGainTrackPeak ?: it.replayGainAlbumPeak }
            .maxOrNull() ?: 1.0f

        trackDao.updateAlbumLoudness(
            albumName = albumName,
            albumGainDb = albumGainDb,
            albumPeak = albumPeak
        )
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

    suspend fun createPlaylistFromTracks(name: String, description: String, trackIds: List<Long>): Long {
        val playlistId = createPlaylist(name, description)
        trackIds.distinct().forEachIndexed { idx, trackId ->
            playlistDao.insertPlaylistTrack(
                PlaylistTrackCrossRef(playlistId = playlistId, trackId = trackId, position = idx)
            )
        }
        return playlistId
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
