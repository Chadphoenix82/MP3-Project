package com.example.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.db.ArtistPlayStat
import com.example.data.db.ExcludedFolderEntity
import com.example.data.db.GenrePlayStat
import com.example.data.db.PlaylistWithSummary
import com.example.data.db.TrackEntity
import com.example.data.db.WatchedFolderEntity
import com.example.data.library.FolderMonitorStatus
import com.example.data.library.GenreTrackCount
import com.example.data.library.LibraryRepository
import com.example.data.library.M3u8PlaylistCodec
import com.example.data.library.ParsedLyrics
import com.example.data.library.SmartQueueEngine
import com.example.data.library.SmartQueueSuggestion
import com.example.data.library.SmartShuffleEngine
import com.example.data.library.SmartShufflePreview
import com.example.data.library.StudioWavSampleType
import com.example.data.library.TrackUtilityActions
import com.example.data.model.BatchMetadataPatch
import com.example.data.model.CrossfadeCurveShape
import com.example.data.model.EqualizerPreset
import com.example.data.model.MetadataSuggestion
import com.example.data.model.OutputRouteCategory
import com.example.data.model.PhotonEqPresets
import com.example.data.model.ReplayGainMode
import com.example.data.model.ReplayGainScanProgress
import com.example.data.model.ReverbSpacePreset
import com.example.data.model.SortDirection
import com.example.data.model.SortOption
import com.example.data.model.WidgetThemeMode
import com.example.data.network.AppUpdateInfo
import com.example.data.network.GitHubUpdateChecker
import com.example.data.network.MetadataAutoCompleteRepository
import com.example.data.network.UpdateCheckOutcome
import com.example.data.prefs.AppSettings
import com.example.data.prefs.SettingsRepository
import com.example.player.PhotonPlayerController
import com.example.player.PlaybackUiState
import com.example.ui.theme.AppFontChoice
import com.example.ui.theme.AppWallpaperPreset
import com.example.ui.theme.CosmicThemeSkin
import com.example.ui.theme.DeckStylePreset
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MetadataSearchUiState(
    val targetTrack: TrackEntity? = null,
    val query: String = "",
    val isLoading: Boolean = false,
    val results: List<MetadataSuggestion> = emptyList(),
    val errorMessage: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class PhotonViewModel(
    private val libraryRepository: LibraryRepository,
    private val settingsRepository: SettingsRepository,
    val playerController: PhotonPlayerController,
    private val gitHubUpdateChecker: GitHubUpdateChecker
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AppSettings()
    )

    val playbackState: StateFlow<PlaybackUiState> = playerController.playbackState
    val spectrumState: StateFlow<com.example.player.AudioSpectrumFrame> = playerController.spectrumState
    val monitorStatus: StateFlow<FolderMonitorStatus> = libraryRepository.monitorStatus
    val replayGainScanProgress: StateFlow<ReplayGainScanProgress> = libraryRepository.replayGainScanProgress

    val watchedFolders: StateFlow<List<WatchedFolderEntity>> = libraryRepository.watchedFolders.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val excludedFolders: StateFlow<List<ExcludedFolderEntity>> = libraryRepository.excludedFolders.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val playlists: StateFlow<List<PlaylistWithSummary>> = libraryRepository.playlists.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val playlistCollageArtworks: StateFlow<Map<Long, List<String>>> = libraryRepository.playlistArtworks
        .map { rows ->
            rows.groupBy { it.playlistId }
                .mapValues { (_, items) -> items.map { it.artworkUri }.distinct().take(4) }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )

    val totalPlayCount: StateFlow<Int> = libraryRepository.totalPlayCount.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _formatFilter = MutableStateFlow<String?>(null)
    val formatFilter: StateFlow<String?> = _formatFilter.asStateFlow()

    val allTracks: StateFlow<List<TrackEntity>> = libraryRepository.allTracks.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val recentlyAddedTracks: StateFlow<List<TrackEntity>> = allTracks
        .map { tracks -> tracks.sortedByDescending { it.dateAdded }.take(20) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val availableGenres: StateFlow<List<GenreTrackCount>> = allTracks
        .map { tracks -> SmartShuffleEngine.extractAvailableGenres(tracks) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val smartShufflePreview: StateFlow<SmartShufflePreview> = combine(
        allTracks,
        settings
    ) { tracks, appSettings ->
        SmartShuffleEngine.buildPreview(
            allTracks = tracks,
            preferredGenres = appSettings.smartShuffleGenres,
            recentWeight = appSettings.smartShuffleRecentWeight
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SmartShufflePreview()
    )

    val favoriteTracks: StateFlow<List<TrackEntity>> = allTracks
        .map { list -> list.filter { it.isFavorite } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val recentlyPlayedTracks: StateFlow<List<TrackEntity>> = combine(
        allTracks,
        libraryRepository.recentHistory
    ) { tracks, history ->
        val byId = tracks.associateBy { it.id }
        val seen = LinkedHashSet<Long>()
        val result = mutableListOf<TrackEntity>()
        for (event in history) {
            if (seen.add(event.trackId)) {
                byId[event.trackId]?.let { result.add(it) }
            }
            if (result.size >= 20) break
        }
        result
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val trackPlayCountMap: StateFlow<Map<Long, Int>> = libraryRepository.trackPlayStats
        .map { stats -> stats.associate { it.trackId to it.playCount } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )

    val mostPlayedTracks: StateFlow<List<Pair<TrackEntity, Int>>> = combine(
        allTracks,
        libraryRepository.trackPlayStats
    ) { tracks, stats ->
        val byId = tracks.associateBy { it.id }
        stats.sortedByDescending { it.playCount }
            .mapNotNull { stat -> byId[stat.trackId]?.let { it to stat.playCount } }
            .take(20)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    /** High play count tracks not played in 30+ days (with graceful deep-cut fallback). */
    val forgottenFavorites: StateFlow<List<TrackEntity>> = combine(
        allTracks,
        libraryRepository.trackPlayStats
    ) { tracks, stats ->
        val byId = tracks.associateBy { it.id }
        val thirtyDaysAgoMs = System.currentTimeMillis() - 30L * 24L * 60L * 60L * 1000L
        val strictForgotten = stats
            .filter { it.playCount >= 2 && it.lastPlayedAt < thirtyDaysAgoMs }
            .sortedByDescending { it.playCount }
            .mapNotNull { byId[it.trackId] }
        if (strictForgotten.isNotEmpty()) {
            strictForgotten.take(16)
        } else {
            val statsById = stats.associateBy { it.trackId }
            val candidates = tracks.filter { t ->
                val st = statsById[t.id]
                (st != null && st.lastPlayedAt < thirtyDaysAgoMs) ||
                    (t.isFavorite && (st == null || st.lastPlayedAt < System.currentTimeMillis() - 7L * 86_400_000L))
            }
            if (candidates.isNotEmpty()) {
                candidates.sortedBy { statsById[it.id]?.lastPlayedAt ?: 0L }.take(16)
            } else {
                // Surface unplayed gems from library so carousel always gives meaningful rediscovery
                tracks.filter { it.id !in statsById }.sortedBy { it.dateAdded }.take(12)
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val genrePlayStats: StateFlow<List<GenrePlayStat>> = libraryRepository.genrePlayStats.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val artistPlayStats: StateFlow<List<ArtistPlayStat>> = libraryRepository.artistPlayStats.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val filteredSortedTracks: StateFlow<List<TrackEntity>> = combine(
        allTracks,
        settings,
        _searchQuery,
        _formatFilter,
        trackPlayCountMap
    ) { tracks, appSettings, query, format, playCounts ->
        val trimmed = query.trim().lowercase()
        val minDurationMs = appSettings.minTrackDurationSec * 1000L
        val filtered = tracks.filter { track ->
            val meetsMinDuration = minDurationMs <= 0L || track.durationMs <= 0L || track.durationMs >= minDurationMs
            val matchesFormat = format == null || track.format.equals(format, ignoreCase = true)
            val matchesQuery = trimmed.isEmpty() ||
                track.title.lowercase().contains(trimmed) ||
                track.artist.lowercase().contains(trimmed) ||
                track.album.lowercase().contains(trimmed) ||
                track.genre.lowercase().contains(trimmed) ||
                track.composer.lowercase().contains(trimmed) ||
                track.publisher.lowercase().contains(trimmed) ||
                (track.releaseYear?.toString()?.contains(trimmed) == true)
            meetsMinDuration && matchesFormat && matchesQuery
        }
        sortTracks(filtered, appSettings.sortOption, appSettings.sortDirection, playCounts)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Smart Queue reactive engine powered by Room listening history + current track genre
    val smartQueueSuggestions: StateFlow<List<SmartQueueSuggestion>> = combine(
        allTracks,
        playbackState,
        libraryRepository.recentHistory,
        libraryRepository.trackPlayStats,
        libraryRepository.genrePlayStats
    ) { tracks, playback, history, trackStats, genreStats ->
        val upcomingIds = if (playback.currentIndex >= 0 && playback.currentIndex < playback.queue.lastIndex) {
            playback.queue.drop(playback.currentIndex + 1).map { it.id }.toSet()
        } else {
            emptySet()
        }
        SmartQueueEngine.buildSuggestions(
            allTracks = tracks,
            currentTrack = playback.currentTrack,
            upcomingQueueTrackIds = upcomingIds,
            recentHistory = history,
            trackStats = trackStats,
            genreStats = genreStats
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _selectedPlaylistId = MutableStateFlow<Long?>(null)
    val selectedPlaylistId: StateFlow<Long?> = _selectedPlaylistId.asStateFlow()

    val selectedPlaylistTracks: StateFlow<List<TrackEntity>> = _selectedPlaylistId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else libraryRepository.observePlaylistTracks(id)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _metadataUiState = MutableStateFlow(MetadataSearchUiState())
    val metadataUiState: StateFlow<MetadataSearchUiState> = _metadataUiState.asStateFlow()

    // Dedicated Metadata Editor screen state
    private val _editorSelectedTrackId = MutableStateFlow<Long?>(null)
    val editorSelectedTrackId: StateFlow<Long?> = _editorSelectedTrackId.asStateFlow()

    private val _editorSearchState = MutableStateFlow(MetadataSearchUiState())
    val editorSearchState: StateFlow<MetadataSearchUiState> = _editorSearchState.asStateFlow()

    // Privacy-first GitHub Update Checker state
    private val _availableUpdate = MutableStateFlow<AppUpdateInfo?>(null)
    val availableUpdate: StateFlow<AppUpdateInfo?> = _availableUpdate.asStateFlow()

    private val _isCheckingUpdate = MutableStateFlow(false)
    val isCheckingUpdate: StateFlow<Boolean> = _isCheckingUpdate.asStateFlow()

    private val _updateCheckStatus = MutableStateFlow<String?>(null)
    val updateCheckStatus: StateFlow<String?> = _updateCheckStatus.asStateFlow()

    // Password-protected API access state (locks automatically whenever app restarts)
    private val _isApiUnlocked = MutableStateFlow(false)
    val isApiUnlocked: StateFlow<Boolean> = _isApiUnlocked.asStateFlow()

    private val _snackbarMessages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val snackbarMessages: SharedFlow<String> = _snackbarMessages.asSharedFlow()

    private var lastLoggedTrackKey: String? = null
    private var lastAutoAppendedForTrackId: Long? = null
    private var lastAppliedOutputCategory: OutputRouteCategory? = null
    private var lastAppliedTrackProfileId: Long? = null
    private val autoScannedTrackIds = mutableSetOf<Long>()

    init {
        // Keep PlaybackService synced with DataStore playback, crossfade, 10-band EQ, Studio DSP & Widgets
        viewModelScope.launch {
            settings.collect { appSettings ->
                playerController.setCrossfadeSeconds(appSettings.crossfadeSeconds)
                playerController.setCrossfadeCurve(appSettings.crossfadeCurve)
                playerController.setGaplessPlaybackEnabled(appSettings.gaplessPlaybackEnabled)
                playerController.updatePlaybackOptions(
                    speed = appSettings.playbackSpeed,
                    pitch = appSettings.playbackPitch,
                    skipSilence = appSettings.skipSilenceEnabled,
                    pauseOnDisconnect = appSettings.pauseOnAudioDisconnect,
                    audioFocus = appSettings.handleAudioFocus
                )
                playerController.updateEqualizer(
                    enabled = appSettings.eqEnabled,
                    ratios = appSettings.eqBandRatios,
                    bass = appSettings.bassBoostStrength,
                    virtualizer = appSettings.virtualizerStrength
                )
                val activeTrackGain = playbackState.value.currentTrack?.customGainDb ?: 0f
                playerController.updateStudioDsp(appSettings.toStudioDspConfig(activeTrackGain))
                playerController.refreshWidgets()
            }
        }

        // Check GitHub Releases once on startup if online and user enabled auto-check
        viewModelScope.launch {
            val initialSettings = settingsRepository.settingsFlow.first()
            if (initialSettings.checkUpdatesOnLaunch && gitHubUpdateChecker.isOnline()) {
                checkForGitHubUpdates(manual = false)
            }
        }

        // Keep active playback track metadata refreshed when Room updates tracks
        viewModelScope.launch {
            allTracks.collect { tracks ->
                val currentId = playbackState.value.currentTrack?.id ?: return@collect
                tracks.firstOrNull { it.id == currentId }?.let { updated ->
                    playerController.updateTrackInQueue(updated)
                }
            }
        }

        // Record listening history + auto-queue + Per-Track Sound Memory + Auto EQ by Output Route
        viewModelScope.launch {
            playbackState.collect { pb ->
                val current = pb.currentTrack
                val currentSettings = settings.value

                // 1) Auto EQ switch when output route changes (Bluetooth / Wired-USB / Speaker)
                val routeCategory = OutputRouteCategory.fromTransport(pb.signalInfo.outputTransport)
                if (currentSettings.autoEqByOutputEnabled && routeCategory != lastAppliedOutputCategory) {
                    lastAppliedOutputCategory = routeCategory
                    val hasTrackEqOverride = !current?.preferredEqPreset.isNullOrBlank()
                    if (!hasTrackEqOverride) {
                        val targetPresetName = currentSettings.eqPresetForOutput(routeCategory)
                        val preset = currentSettings.allEqPresets()
                            .firstOrNull { it.name.equals(targetPresetName, ignoreCase = true) }
                        if (preset != null && !currentSettings.eqPresetName.equals(preset.name, ignoreCase = true)) {
                            settingsRepository.setEqBands(preset.name, preset.bandRatios)
                            settingsRepository.setSpatialEffects(preset.bassBoost, preset.virtualizer)
                        }
                    }
                } else if (!currentSettings.autoEqByOutputEnabled) {
                    lastAppliedOutputCategory = null
                }

                // 2) Per-Track Sound Profile recall & On-Demand ReplayGain/LUFS auto-scan when track changes
                if (current != null && current.id != lastAppliedTrackProfileId) {
                    lastAppliedTrackProfileId = current.id
                    playerController.updateStudioDsp(currentSettings.toStudioDspConfig(current.customGainDb))
                    if (
                        currentSettings.replayGainMode != ReplayGainMode.OFF &&
                        current.replayGainTrackDb == null &&
                        current.integratedLufs == null &&
                        autoScannedTrackIds.add(current.id)
                    ) {
                        val targetTrack = current
                        viewModelScope.launch {
                            val updated = libraryRepository.scanTrackLoudness(targetTrack, forceAudioScan = false)
                            if (updated.replayGainTrackDb != null || updated.integratedLufs != null) {
                                playerController.updateTrackInQueue(updated)
                                if (playbackState.value.currentTrack?.id == updated.id) {
                                    playerController.updateStudioDsp(
                                        settings.value.toStudioDspConfig(updated.customGainDb)
                                    )
                                }
                            }
                        }
                    }
                    val trackEqName = current.preferredEqPreset
                    if (!trackEqName.isNullOrBlank()) {
                        val matched = currentSettings.allEqPresets()
                            .firstOrNull { it.name.equals(trackEqName, ignoreCase = true) }
                        if (matched != null && !currentSettings.eqPresetName.equals(matched.name, ignoreCase = true)) {
                            settingsRepository.setEqBands(matched.name, matched.bandRatios)
                            settingsRepository.setSpatialEffects(matched.bassBoost, matched.virtualizer)
                        }
                    } else if (currentSettings.autoEqByOutputEnabled) {
                        val routePresetName = currentSettings.eqPresetForOutput(routeCategory)
                        val routePreset = currentSettings.allEqPresets()
                            .firstOrNull { it.name.equals(routePresetName, ignoreCase = true) }
                        if (routePreset != null && !currentSettings.eqPresetName.equals(routePreset.name, ignoreCase = true)) {
                            settingsRepository.setEqBands(routePreset.name, routePreset.bandRatios)
                            settingsRepository.setSpatialEffects(routePreset.bassBoost, routePreset.virtualizer)
                        }
                    }
                }

                if (current != null && pb.isPlaying) {
                    val key = "${current.id}:${pb.currentIndex}"
                    if (key != lastLoggedTrackKey) {
                        lastLoggedTrackKey = key
                        libraryRepository.recordTrackPlayed(current)
                    }

                    // Auto-append top Smart Queue suggestion when reaching the end of the current queue
                    if (
                        currentSettings.smartQueueAutoPlay &&
                        pb.queue.isNotEmpty() &&
                        pb.currentIndex == pb.queue.lastIndex &&
                        lastAutoAppendedForTrackId != current.id
                    ) {
                        val nextPick = smartQueueSuggestions.value
                            .firstOrNull { it.track.id != current.id }
                        if (nextPick != null) {
                            lastAutoAppendedForTrackId = current.id
                            playerController.appendToQueue(listOf(nextPick.track))
                        }
                    }
                }
            }
        }
    }

    // ---- Smart Queue Controls ----

    fun playNextFromSmartQueue(track: TrackEntity) {
        playerController.enqueueNext(track)
        _snackbarMessages.tryEmit("Queued next: \"${track.title}\"")
    }

    fun addSuggestionToQueue(track: TrackEntity) {
        playerController.appendToQueue(listOf(track))
        _snackbarMessages.tryEmit("Added \"${track.title}\" to queue")
    }

    fun queueAllSmartSuggestions() {
        val tracks = smartQueueSuggestions.value.map { it.track }
        if (tracks.isEmpty()) return
        playerController.appendToQueue(tracks)
        _snackbarMessages.tryEmit("Added ${tracks.size} Smart Queue track(s)")
    }

    fun jumpToQueueIndex(index: Int) {
        playerController.jumpToQueueIndex(index)
    }

    fun reorderActiveQueue(orderedTracks: List<TrackEntity>) {
        playerController.reorderQueue(orderedTracks)
    }

    fun removeTrackFromActiveQueue(index: Int) {
        playerController.removeFromQueue(index)
    }

    fun clearUpcomingQueue() {
        playerController.clearUpcomingQueue()
        _snackbarMessages.tryEmit("Cleared upcoming tracks from queue")
    }

    fun saveActiveQueueAsPlaylist(name: String, description: String) {
        val currentQueue = playbackState.value.queue
        if (name.isBlank() || currentQueue.isEmpty()) return
        viewModelScope.launch {
            libraryRepository.createPlaylistFromTracks(
                name = name,
                description = description,
                trackIds = currentQueue.map { it.id }
            )
            _snackbarMessages.tryEmit("Saved ${currentQueue.size} queued tracks as \"$name\"")
        }
    }

    fun toggleFavorite(track: TrackEntity) {
        val next = !track.isFavorite
        viewModelScope.launch {
            libraryRepository.setTrackFavorite(track.id, next)
            playerController.updateTrackInQueue(track.copy(isFavorite = next))
            playerController.refreshWidgets()
            _snackbarMessages.tryEmit(
                if (next) "Added \"${track.title}\" to Favorites"
                else "Removed \"${track.title}\" from Favorites"
            )
        }
    }

    fun setAbLoopStart() {
        playerController.setAbLoopStart()
    }

    fun setAbLoopEnd() {
        playerController.setAbLoopEnd()
    }

    fun clearAbLoop() {
        playerController.clearAbLoop()
    }

    fun setSmartQueueAutoPlay(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setSmartQueueAutoPlay(enabled)
        }
    }

    fun clearListeningHistory() {
        viewModelScope.launch {
            libraryRepository.clearListeningHistory()
            lastLoggedTrackKey = null
            _snackbarMessages.tryEmit("Cleared Room listening history")
        }
    }

    // ---- Search, Filter & Sort ----

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun commitSearchQuery(query: String = _searchQuery.value) {
        val cleaned = query.trim()
        if (cleaned.isEmpty()) return
        viewModelScope.launch {
            settingsRepository.addSearchHistoryQuery(cleaned)
        }
    }

    fun removeSearchHistoryItem(query: String) {
        viewModelScope.launch {
            settingsRepository.removeSearchHistoryQuery(query)
        }
    }

    fun clearSearchHistory() {
        viewModelScope.launch {
            settingsRepository.clearSearchHistory()
            _snackbarMessages.tryEmit("Cleared search history")
        }
    }

    fun setFormatFilter(format: String?) {
        _formatFilter.value = if (_formatFilter.value == format) null else format
    }

    fun selectSortOption(option: SortOption) {
        viewModelScope.launch {
            val current = settings.value
            val newDir = if (current.sortOption == option && option != SortOption.CUSTOM_ORDER) {
                if (current.sortDirection == SortDirection.ASC) SortDirection.DESC else SortDirection.ASC
            } else {
                if (option == SortOption.RELEASE_YEAR || option == SortOption.DATE_ADDED) {
                    SortDirection.DESC
                } else {
                    SortDirection.ASC
                }
            }
            settingsRepository.setSort(option, newDir)
        }
    }

    fun toggleSortDirection() {
        viewModelScope.launch {
            val current = settings.value
            val toggled = if (current.sortDirection == SortDirection.ASC) SortDirection.DESC else SortDirection.ASC
            settingsRepository.setSort(current.sortOption, toggled)
        }
    }

    // ---- Audio Import, Auto-Scan & Exclusions ----

    fun importAudioFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val result = libraryRepository.importPickedFiles(uris)
            val msg = when {
                result.importedCount > 0 && result.skippedByExclusionCount > 0 ->
                    "Imported ${result.importedCount} file(s) · ${result.skippedByExclusionCount} skipped by file-type exclusion"
                result.importedCount > 0 ->
                    "Imported ${result.importedCount} audio file(s) into Photon Library"
                result.skippedByExclusionCount > 0 ->
                    "Skipped ${result.skippedByExclusionCount} file(s) matching your excluded file types"
                else ->
                    "Selected files are already in your library"
            }
            _snackbarMessages.tryEmit(msg)
        }
    }

    fun addWatchedFolder(treeUri: Uri) {
        viewModelScope.launch {
            val msg = libraryRepository.addWatchedFolder(treeUri)
            _snackbarMessages.tryEmit(msg)
        }
    }

    fun removeWatchedFolder(folderUri: String) {
        viewModelScope.launch {
            libraryRepository.removeWatchedFolder(folderUri)
            _snackbarMessages.tryEmit("Removed watched folder")
        }
    }

    fun addExcludedSafFolder(treeUri: Uri) {
        viewModelScope.launch {
            val msg = libraryRepository.addExcludedSafFolder(treeUri)
            _snackbarMessages.tryEmit(msg)
        }
    }

    fun addExcludedFolderName(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val msg = libraryRepository.addExcludedFolderName(name)
            _snackbarMessages.tryEmit(msg)
        }
    }

    fun removeExcludedFolder(identifier: String) {
        viewModelScope.launch {
            libraryRepository.removeExcludedFolder(identifier)
            _snackbarMessages.tryEmit("Removed folder exclusion")
        }
    }

    fun setAutoScanEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAutoScanEnabled(enabled)
        }
    }

    fun toggleExcludedFileType(fileType: String) {
        viewModelScope.launch {
            settingsRepository.toggleExcludedFileType(fileType)
        }
    }

    fun scanWatchedFoldersNow() {
        viewModelScope.launch {
            val result = libraryRepository.scanWatchedFoldersNow()
            val msg = when {
                result.importedCount > 0 && result.skippedByExclusionCount > 0 ->
                    "Auto-detected ${result.importedCount} new track(s) · ${result.skippedByExclusionCount} excluded"
                result.importedCount > 0 ->
                    "Auto-detected & imported ${result.importedCount} new track(s)"
                result.skippedByExclusionCount > 0 ->
                    "Scan complete · ${result.skippedByExclusionCount} item(s) skipped by exclusions"
                else ->
                    "Scan complete — library is up to date"
            }
            _snackbarMessages.tryEmit(msg)
        }
    }

    fun generateStarterWavTracks() {
        viewModelScope.launch {
            _snackbarMessages.tryEmit("Synthesizing 44.1kHz stereo WAV studio tracks...")
            val result = libraryRepository.generateStarterStudioWavTracks()
            _snackbarMessages.tryEmit(
                when {
                    result.importedCount > 0 -> "Added ${result.importedCount} studio WAV track(s) — auto-fetching web metadata!"
                    result.skippedByExclusionCount > 0 -> "WAV files were skipped because WAV is currently excluded"
                    else -> "Studio WAV tracks already loaded"
                }
            )
        }
    }

    fun dropTestTrackToWatchedFolder() {
        viewModelScope.launch {
            val name = libraryRepository.dropTestTrackIntoWatchedFolder()
            _snackbarMessages.tryEmit("Dropped \"$name\" into watched folder — scanning now!")
        }
    }

    fun deleteTrack(track: TrackEntity) {
        viewModelScope.launch {
            libraryRepository.deleteTrack(track.id)
            _snackbarMessages.tryEmit("Removed \"${track.title}\" from library")
        }
    }

    fun shareTrackAudio(context: Context, track: TrackEntity) {
        val outcome = TrackUtilityActions.shareTrack(context, track)
        if (!outcome.success) {
            _snackbarMessages.tryEmit(outcome.message)
        }
    }

    fun setTrackAsRingtone(context: Context, track: TrackEntity) {
        viewModelScope.launch {
            val outcome = TrackUtilityActions.setTrackAsRingtone(context, track)
            _snackbarMessages.tryEmit(outcome.message)
        }
    }

    fun deleteTrackFromDevice(context: Context, track: TrackEntity) {
        viewModelScope.launch {
            val fileDeleted = TrackUtilityActions.deletePhysicalAudioFile(context, track)
            libraryRepository.deleteTrack(track.id)
            val queue = playbackState.value.queue
            val idxInQueue = queue.indexOfFirst { it.id == track.id }
            if (idxInQueue >= 0) {
                playerController.removeFromQueue(idxInQueue)
            }
            val msg = if (fileDeleted) {
                "Deleted \"${track.title}\" from device and library"
            } else {
                "Removed \"${track.title}\" from library (device storage protected by system)"
            }
            _snackbarMessages.tryEmit(msg)
        }
    }

    // ---- Drag-and-Drop Reordering ----

    fun persistLibraryOrder(orderedTracks: List<TrackEntity>) {
        viewModelScope.launch {
            if (settings.value.sortOption != SortOption.CUSTOM_ORDER) {
                settingsRepository.setSort(SortOption.CUSTOM_ORDER, SortDirection.ASC)
            }
            libraryRepository.reorderLibraryTracks(orderedTracks.map { it.id })
            if (playbackState.value.queue.size == orderedTracks.size) {
                playerController.reorderQueue(orderedTracks)
            }
        }
    }

    fun persistPlaylistOrder(playlistId: Long, orderedTracks: List<TrackEntity>) {
        viewModelScope.launch {
            libraryRepository.reorderPlaylistTracks(playlistId, orderedTracks.map { it.id })
        }
    }

    // ---- Playback Controls ----

    fun playTracks(tracks: List<TrackEntity>, startIndex: Int, sourceLabel: String = "Library") {
        if (tracks.isEmpty()) return
        lastAutoAppendedForTrackId = null
        playerController.playQueue(tracks, startIndex, sourceLabel)
    }

    fun playShuffled(tracks: List<TrackEntity>, sourceLabel: String = "Shuffled") {
        if (tracks.isEmpty()) return
        lastAutoAppendedForTrackId = null
        val shuffled = tracks.shuffled()
        playerController.playQueue(shuffled, 0, sourceLabel)
    }

    // ---- Smart Auto-Mixes (Home Dashboard) ----

    fun playSmartMix25Shuffle() {
        val pool = allTracks.value
        if (pool.isEmpty()) {
            _snackbarMessages.tryEmit("Import or generate tracks first to launch 25-Track Shuffle")
            return
        }
        val mix = pool.shuffled().take(25)
        playTracks(mix, 0, sourceLabel = "25-Track Shuffle")
        _snackbarMessages.tryEmit("Queued 25-Track Shuffle (${mix.size} tracks)")
    }

    fun playSmartMixLosslessOnly() {
        val pool = allTracks.value.filter {
            it.format.equals("FLAC", ignoreCase = true) ||
                it.format.equals("WAV", ignoreCase = true) ||
                it.bitrateKbps >= 256
        }
        if (pool.isEmpty()) {
            _snackbarMessages.tryEmit("No FLAC, WAV, or high-bitrate (256+ kbps) tracks found yet")
            return
        }
        playTracks(pool.shuffled(), 0, sourceLabel = "Lossless / Hi-Res Mix")
        _snackbarMessages.tryEmit("Playing Lossless / Hi-Bitrate Auto-Mix (${pool.size} tracks)")
    }

    fun playSmartMixRecentlyImported() {
        val pool = allTracks.value.sortedByDescending { it.dateAdded }.take(25)
        if (pool.isEmpty()) {
            _snackbarMessages.tryEmit("Library is empty — import audio to start")
            return
        }
        playTracks(pool, 0, sourceLabel = "Recently Imported")
        _snackbarMessages.tryEmit("Queued ${pool.size} recently imported tracks")
    }

    // ---- Smart Shuffle (Recently Imported + User Genre Preferences) ----

    fun playSmartShuffle() {
        val pool = allTracks.value
        if (pool.isEmpty()) {
            _snackbarMessages.tryEmit("Import or generate tracks first to launch Smart Shuffle")
            return
        }
        val currentSettings = settings.value
        val result = SmartShuffleEngine.buildQueue(
            allTracks = pool,
            preferredGenres = currentSettings.smartShuffleGenres,
            recentWeight = currentSettings.smartShuffleRecentWeight
        )
        if (result.tracks.isEmpty()) {
            _snackbarMessages.tryEmit("No tracks available for Smart Shuffle yet")
            return
        }
        playTracks(result.tracks, 0, sourceLabel = result.sourceLabel)
        val detail = if (result.activeGenres.isEmpty()) {
            "${result.recentlyImportedCount} recent imports blended"
        } else {
            "${result.recentlyImportedCount} recent · ${result.genreMatchedCount} genre matches"
        }
        _snackbarMessages.tryEmit("Smart Shuffle queued ${result.tracks.size} tracks ($detail)")
    }

    fun toggleSmartShuffleGenre(genre: String) {
        viewModelScope.launch {
            settingsRepository.toggleSmartShuffleGenre(genre)
        }
    }

    fun addCustomSmartShuffleGenre(genre: String) {
        val cleaned = genre.trim()
        if (cleaned.isEmpty()) return
        viewModelScope.launch {
            settingsRepository.addSmartShuffleGenre(cleaned)
            _snackbarMessages.tryEmit("Added \"$cleaned\" to Smart Shuffle genres")
        }
    }

    fun clearSmartShuffleGenres() {
        viewModelScope.launch {
            settingsRepository.clearSmartShuffleGenres()
            _snackbarMessages.tryEmit("Cleared Smart Shuffle genre filters")
        }
    }

    fun setSmartShuffleRecentWeight(weight: Float) {
        viewModelScope.launch {
            settingsRepository.setSmartShuffleRecentWeight(weight)
        }
    }

    // ---- Audio Lab Studio WAV Samples ----

    fun generateStudioWavSample(type: StudioWavSampleType) {
        viewModelScope.launch {
            _snackbarMessages.tryEmit("Synthesizing ${type.displayName}...")
            val created = libraryRepository.generateStudioWavSample(type)
            _snackbarMessages.tryEmit("Imported \"$created\" into library")
        }
    }

    // ---- Synchronized Lyrics Engine ----

    suspend fun loadTrackLyrics(track: TrackEntity): ParsedLyrics =
        libraryRepository.loadTrackLyrics(track)

    suspend fun fetchTrackLyricsOnline(track: TrackEntity): Result<ParsedLyrics> {
        val result = libraryRepository.fetchTrackLyricsOnline(track)
        result.onSuccess {
            _snackbarMessages.tryEmit("Lyrics cached locally for \"${track.title}\"")
        }.onFailure { err ->
            _snackbarMessages.tryEmit(err.message ?: "Unable to fetch lyrics online")
        }
        return result
    }

    suspend fun saveCustomLyrics(trackId: Long, rawLyrics: String): ParsedLyrics {
        val parsed = libraryRepository.saveCustomLyrics(trackId, rawLyrics)
        _snackbarMessages.tryEmit("Saved lyrics locally to Room")
        return parsed
    }

    // ---- M3U8 Playlist Export & Import ----

    fun exportPlaylistToM3u8(context: Context, targetUri: Uri, playlistName: String, tracks: List<TrackEntity>) {
        viewModelScope.launch {
            val res = M3u8PlaylistCodec.exportToUri(context, targetUri, playlistName, tracks)
            res.onSuccess { count ->
                _snackbarMessages.tryEmit("Exported \"$playlistName\" ($count tracks) to .m3u8")
            }.onFailure { err ->
                _snackbarMessages.tryEmit("Export failed: ${err.message ?: "unknown error"}")
            }
        }
    }

    fun importPlaylistFromM3u8(context: Context, sourceUri: Uri) {
        viewModelScope.launch {
            val res = M3u8PlaylistCodec.importFromUri(context, sourceUri, allTracks.value)
            res.onSuccess { imported ->
                if (imported.matchedTracks.isEmpty()) {
                    _snackbarMessages.tryEmit("No tracks in .m3u8 matched your local library")
                } else {
                    libraryRepository.createPlaylistFromTracks(
                        name = imported.playlistName,
                        description = "Imported from .m3u8 (${imported.matchedTracks.size} matched)",
                        trackIds = imported.matchedTracks.map { it.id }
                    )
                    val extra = if (imported.unmatchedCount > 0) " · ${imported.unmatchedCount} unmatched" else ""
                    _snackbarMessages.tryEmit("Imported \"${imported.playlistName}\" (${imported.matchedTracks.size} tracks$extra)")
                }
            }.onFailure { err ->
                _snackbarMessages.tryEmit("Import failed: ${err.message ?: "invalid .m3u8"}")
            }
        }
    }

    // ---- Deck Theme Preset, Duration Filter & Gapless ----

    fun applyDeckStylePreset(preset: DeckStylePreset) {
        viewModelScope.launch {
            settingsRepository.applyDeckStylePreset(preset)
            _snackbarMessages.tryEmit("Applied ${preset.displayName} Deck Theme")
        }
    }

    fun setMinTrackDurationSec(seconds: Int) {
        viewModelScope.launch { settingsRepository.setMinTrackDurationSec(seconds) }
    }

    fun setGaplessPlaybackEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setGaplessPlaybackEnabled(enabled) }
    }

    // ---- Custom Playlists ----

    fun selectPlaylist(playlistId: Long?) {
        _selectedPlaylistId.value = playlistId
    }

    fun createPlaylist(name: String, description: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            libraryRepository.createPlaylist(name, description)
            _snackbarMessages.tryEmit("Created playlist \"$name\"")
        }
    }

    fun createPlaylistAndAddTrack(name: String, description: String = "", track: TrackEntity) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val playlistId = libraryRepository.createPlaylist(name, description)
            libraryRepository.addTrackToPlaylist(playlistId, track.id)
            _snackbarMessages.tryEmit("Created \"$name\" and added \"${track.title}\"")
        }
    }

    fun renamePlaylist(playlistId: Long, name: String, description: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            libraryRepository.renamePlaylist(playlistId, name, description)
            _snackbarMessages.tryEmit("Updated playlist \"$name\"")
        }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch {
            if (_selectedPlaylistId.value == playlistId) {
                _selectedPlaylistId.value = null
            }
            libraryRepository.deletePlaylist(playlistId)
            _snackbarMessages.tryEmit("Deleted playlist")
        }
    }

    fun addTrackToPlaylist(playlistId: Long, track: TrackEntity) {
        viewModelScope.launch {
            libraryRepository.addTrackToPlaylist(playlistId, track.id)
            _snackbarMessages.tryEmit("Added \"${track.title}\" to playlist")
        }
    }

    fun removeTrackFromPlaylist(playlistId: Long, track: TrackEntity) {
        viewModelScope.launch {
            libraryRepository.removeTrackFromPlaylist(playlistId, track.id)
        }
    }

    // ---- Dedicated Metadata Editor & High-Precision Web Auto-Complete ----

    fun selectTrackForEditor(track: TrackEntity) {
        _editorSelectedTrackId.value = track.id
        val defaultQuery = buildString {
            if (track.artist != "Unknown Artist" && track.artist.isNotBlank()) {
                append(track.artist)
                append(" ")
            }
            append(MetadataAutoCompleteRepository.cleanSearchQuery(track.title))
        }.trim()
        _editorSearchState.value = MetadataSearchUiState(
            targetTrack = track,
            query = defaultQuery,
            isLoading = false,
            results = emptyList(),
            errorMessage = null
        )
    }

    fun selectTrackForEditorById(trackId: Long) {
        val found = allTracks.value.firstOrNull { it.id == trackId } ?: return
        selectTrackForEditor(found)
    }

    fun updateEditorSearchQuery(query: String) {
        _editorSearchState.value = _editorSearchState.value.copy(query = query)
    }

    fun searchMetadataForEditor(query: String = _editorSearchState.value.query, targetTrack: TrackEntity? = null) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        val resolvedTrack = targetTrack ?: allTracks.value.firstOrNull { it.id == _editorSelectedTrackId.value }
        viewModelScope.launch {
            _editorSearchState.value = _editorSearchState.value.copy(
                targetTrack = resolvedTrack,
                query = trimmed,
                isLoading = true,
                errorMessage = null
            )
            try {
                val results = libraryRepository.searchWebMetadata(
                    query = trimmed,
                    hintTitle = resolvedTrack?.title,
                    hintArtist = resolvedTrack?.artist
                )
                _editorSearchState.value = _editorSearchState.value.copy(
                    isLoading = false,
                    results = results,
                    errorMessage = if (results.isEmpty()) {
                        "No exact web matches found. Edit any field manually below or refine the search query."
                    } else null
                )
            } catch (_: Exception) {
                _editorSearchState.value = _editorSearchState.value.copy(
                    isLoading = false,
                    errorMessage = "Offline or network unreachable — you can still edit and save all metadata locally on-device."
                )
            }
        }
    }

    fun saveFullTrackMetadata(
        trackId: Long,
        title: String,
        artist: String,
        album: String,
        trackNumber: Int?,
        genre: String,
        publisher: String,
        releaseDate: String,
        composer: String,
        notes: String,
        artworkUri: String?,
        replaceArtwork: Boolean = true
    ) {
        viewModelScope.launch {
            libraryRepository.updateTrackMetadataManual(
                trackId = trackId,
                title = title,
                artist = artist,
                album = album,
                genre = genre,
                publisher = publisher,
                releaseDate = releaseDate,
                trackNumber = trackNumber,
                composer = composer,
                notes = notes,
                artworkUri = artworkUri,
                replaceArtwork = replaceArtwork
            )
            _snackbarMessages.tryEmit("Saved metadata & album cover locally for \"${title.trim().ifBlank { "Track" }}\"")
        }
    }

    fun saveBatchTrackMetadata(
        trackIds: List<Long>,
        patch: BatchMetadataPatch,
        contextLabel: String
    ) {
        if (trackIds.isEmpty() || !patch.hasAnyChanges) return
        viewModelScope.launch {
            val updatedCount = libraryRepository.updateBatchTrackMetadata(trackIds, patch)
            if (updatedCount > 0) {
                val activeId = playbackState.value.currentTrack?.id
                if (activeId != null && activeId in trackIds) {
                    playerController.refreshWidgets()
                }
                _snackbarMessages.tryEmit("Updated metadata for $updatedCount track(s) · $contextLabel")
            }
        }
    }

    fun savePickedCoverArtwork(uri: Uri, trackId: Long, onSaved: (String) -> Unit) {
        viewModelScope.launch {
            val savedPath = libraryRepository.savePickedArtworkLocally(uri, trackId)
            if (savedPath != null) {
                onSaved(savedPath)
                _snackbarMessages.tryEmit("Album cover saved on device — tap Save Metadata to apply")
            } else {
                _snackbarMessages.tryEmit("Could not read selected image")
            }
        }
    }

    fun openMetadataAutoComplete(track: TrackEntity) {
        selectTrackForEditor(track)
        val defaultQuery = _editorSearchState.value.query
        _metadataUiState.value = MetadataSearchUiState(
            targetTrack = track,
            query = defaultQuery,
            isLoading = true,
            results = emptyList(),
            errorMessage = null
        )
        performMetadataSearch(defaultQuery)
    }

    fun dismissMetadataAutoComplete() {
        _metadataUiState.value = MetadataSearchUiState()
    }

    fun updateMetadataQuery(query: String) {
        _metadataUiState.value = _metadataUiState.value.copy(query = query)
    }

    fun performMetadataSearch(query: String = _metadataUiState.value.query) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        val target = _metadataUiState.value.targetTrack
        viewModelScope.launch {
            _metadataUiState.value = _metadataUiState.value.copy(
                query = trimmed,
                isLoading = true,
                errorMessage = null
            )
            try {
                val results = libraryRepository.searchWebMetadata(
                    query = trimmed,
                    hintTitle = target?.title,
                    hintArtist = target?.artist
                )
                _metadataUiState.value = _metadataUiState.value.copy(
                    isLoading = false,
                    results = results,
                    errorMessage = if (results.isEmpty()) "No exact web matches found. Try editing the search query." else null
                )
            } catch (_: Exception) {
                _metadataUiState.value = _metadataUiState.value.copy(
                    isLoading = false,
                    errorMessage = "Network search failed. Check connection and retry."
                )
            }
        }
    }

    fun applyMetadataSuggestion(suggestion: MetadataSuggestion) {
        val track = _metadataUiState.value.targetTrack ?: return
        viewModelScope.launch {
            libraryRepository.applyMetadata(track.id, suggestion)
            _metadataUiState.value = MetadataSearchUiState()
            _snackbarMessages.tryEmit("Updated metadata & album art for \"${suggestion.title}\"")
        }
    }

    fun saveManualMetadata(
        trackId: Long,
        title: String,
        artist: String,
        album: String,
        genre: String,
        publisher: String,
        releaseDate: String,
        artworkUri: String?
    ) {
        viewModelScope.launch {
            libraryRepository.updateTrackMetadataManual(
                trackId = trackId,
                title = title,
                artist = artist,
                album = album,
                genre = genre,
                publisher = publisher,
                releaseDate = releaseDate,
                artworkUri = artworkUri,
                replaceArtwork = !artworkUri.isNullOrBlank()
            )
            _metadataUiState.value = MetadataSearchUiState()
            _snackbarMessages.tryEmit("Saved custom metadata for \"$title\"")
        }
    }

    // ---- Password-Protected API Vault ----

    fun createApiPassword(password: String, confirmPassword: String, onResult: (String?) -> Unit) {
        when {
            password.length < 4 -> onResult("Password must be at least 4 characters.")
            password != confirmPassword -> onResult("Passwords do not match.")
            else -> {
                viewModelScope.launch {
                    settingsRepository.setApiPassword(password)
                    _isApiUnlocked.value = true
                    _snackbarMessages.tryEmit("API password set — API controls unlocked for this session")
                    onResult(null)
                }
            }
        }
    }

    fun unlockApiWithPassword(password: String, onResult: (String?) -> Unit) {
        if (password.isBlank()) {
            onResult("Enter your API password.")
            return
        }
        viewModelScope.launch {
            val valid = settingsRepository.verifyApiPassword(password)
            if (valid) {
                _isApiUnlocked.value = true
                _snackbarMessages.tryEmit("API access unlocked")
                onResult(null)
            } else {
                onResult("Incorrect password.")
            }
        }
    }

    fun changeApiPassword(
        currentPassword: String,
        newPassword: String,
        confirmPassword: String,
        onResult: (String?) -> Unit
    ) {
        when {
            currentPassword.isBlank() -> onResult("Enter your current password.")
            newPassword.length < 4 -> onResult("New password must be at least 4 characters.")
            newPassword != confirmPassword -> onResult("New passwords do not match.")
            else -> {
                viewModelScope.launch {
                    val valid = settingsRepository.verifyApiPassword(currentPassword)
                    if (!valid) {
                        onResult("Current password is incorrect.")
                    } else {
                        settingsRepository.setApiPassword(newPassword)
                        _snackbarMessages.tryEmit("API password updated on device")
                        onResult(null)
                    }
                }
            }
        }
    }

    fun lockApiAccess() {
        _isApiUnlocked.value = false
        _updateCheckStatus.value = null
        _snackbarMessages.tryEmit("Release API access locked and hidden")
    }

    // ---- Privacy-First GitHub Update Checker ----

    fun checkForGitHubUpdates(manual: Boolean = false) {
        if (manual && !_isApiUnlocked.value) {
            _snackbarMessages.tryEmit("Unlock API access with your password first")
            return
        }
        if (_isCheckingUpdate.value) return
        viewModelScope.launch {
            _isCheckingUpdate.value = true
            if (manual) {
                _updateCheckStatus.value = "Checking GitHub releases..."
            }
            val currentSettings = settingsRepository.settingsFlow.first()
            when (val outcome = gitHubUpdateChecker.checkForUpdate(currentSettings.githubUpdateRepo)) {
                is UpdateCheckOutcome.Offline -> {
                    _updateCheckStatus.value = "Offline — connect to the internet to check GitHub"
                    if (manual) _snackbarMessages.tryEmit("You are offline — connect to check GitHub updates")
                }
                is UpdateCheckOutcome.UpToDate -> {
                    _updateCheckStatus.value = "Up to date (v${outcome.currentVersion})"
                    if (manual) _snackbarMessages.tryEmit("Photon Audio v${outcome.currentVersion} is up to date")
                }
                is UpdateCheckOutcome.UpdateAvailable -> {
                    _updateCheckStatus.value = "New version ${outcome.update.latestTag} available on GitHub"
                    if (manual || currentSettings.dismissedUpdateTag != outcome.update.latestTag) {
                        _availableUpdate.value = outcome.update
                    }
                }
                is UpdateCheckOutcome.Unavailable -> {
                    _updateCheckStatus.value = outcome.reason
                    if (manual) _snackbarMessages.tryEmit(outcome.reason)
                }
            }
            _isCheckingUpdate.value = false
        }
    }

    fun dismissUpdateAlert(skipThisVersion: Boolean) {
        val current = _availableUpdate.value
        _availableUpdate.value = null
        if (skipThisVersion && current != null) {
            viewModelScope.launch {
                settingsRepository.setDismissedUpdateTag(current.latestTag)
            }
        }
    }

    fun setGithubUpdateRepo(repo: String) {
        if (!_isApiUnlocked.value) {
            _snackbarMessages.tryEmit("Unlock API access with your password first")
            return
        }
        viewModelScope.launch {
            val cleaned = repo.trim().removePrefix("https://github.com/").trim('/')
            if (!cleaned.contains("/")) {
                _snackbarMessages.tryEmit("Enter GitHub repository as owner/repo")
                return@launch
            }
            settingsRepository.setGithubUpdateRepo(cleaned)
            _snackbarMessages.tryEmit("Saved GitHub update source: $cleaned")
            if (gitHubUpdateChecker.isOnline()) {
                checkForGitHubUpdates(manual = true)
            }
        }
    }

    fun setCheckUpdatesOnLaunch(enabled: Boolean) {
        if (!_isApiUnlocked.value) return
        viewModelScope.launch { settingsRepository.setCheckUpdatesOnLaunch(enabled) }
    }

    // ---- Equalizer & Commonly Used Playback Settings ----

    fun setDarkMode(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setDarkMode(enabled) }
    }

    fun setCosmicThemeSkin(skin: CosmicThemeSkin) {
        viewModelScope.launch { settingsRepository.setCosmicThemeSkin(skin) }
    }

    fun setCustomThemeColors(primaryArgb: Long, secondaryArgb: Long, oledBlack: Boolean) {
        viewModelScope.launch {
            settingsRepository.setCustomThemeColors(primaryArgb, secondaryArgb, oledBlack)
        }
    }

    fun setAppFont(font: AppFontChoice) {
        viewModelScope.launch {
            settingsRepository.setAppFont(font)
            _snackbarMessages.tryEmit("Font updated to ${font.displayName}")
        }
    }

    fun setWallpaperPreset(preset: AppWallpaperPreset) {
        viewModelScope.launch {
            settingsRepository.setWallpaperPreset(preset)
        }
    }

    fun setWallpaperDimAlpha(alpha: Float) {
        viewModelScope.launch {
            settingsRepository.setWallpaperDimAlpha(alpha)
        }
    }

    fun saveCustomWallpaperPhoto(uri: Uri) {
        viewModelScope.launch {
            val saved = settingsRepository.saveCustomWallpaperFromUri(uri)
            if (saved != null) {
                _snackbarMessages.tryEmit("Applied custom wallpaper")
            } else {
                _snackbarMessages.tryEmit("Could not load selected wallpaper photo")
            }
        }
    }

    fun setDynamicAlbumColorsEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setDynamicAlbumColorsEnabled(enabled) }
    }

    fun setCrossfadeSeconds(seconds: Int) {
        viewModelScope.launch { settingsRepository.setCrossfadeSeconds(seconds) }
    }

    fun setCrossfadeCurve(curve: CrossfadeCurveShape) {
        viewModelScope.launch { settingsRepository.setCrossfadeCurve(curve) }
    }

    fun setPlaybackSpeed(speed: Float) {
        viewModelScope.launch { settingsRepository.setPlaybackSpeed(speed) }
    }

    fun setPlaybackPitch(pitch: Float) {
        viewModelScope.launch { settingsRepository.setPlaybackPitch(pitch) }
    }

    fun resetTempoAndPitch() {
        viewModelScope.launch {
            settingsRepository.setPlaybackSpeed(1.0f)
            settingsRepository.setPlaybackPitch(1.0f)
        }
    }

    fun setSkipSilenceEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setSkipSilenceEnabled(enabled) }
    }

    fun setPauseOnAudioDisconnect(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setPauseOnAudioDisconnect(enabled) }
    }

    fun setHandleAudioFocus(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setHandleAudioFocus(enabled) }
    }

    fun setKeepScreenOn(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setKeepScreenOn(enabled) }
    }

    fun setGestureControlsEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setGestureControlsEnabled(enabled) }
    }

    fun setAutoEnrichMetadata(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAutoEnrichMetadata(enabled) }
    }

    fun setEqEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setEqEnabled(enabled) }
    }

    fun applyEqPreset(preset: EqualizerPreset) {
        viewModelScope.launch {
            settingsRepository.setEqBands(preset.name, preset.bandRatios)
            settingsRepository.setSpatialEffects(preset.bassBoost, preset.virtualizer)
        }
    }

    fun updateEqBand(bandIndex: Int, normalizedRatio: Float) {
        val current = PhotonEqPresets.normalizeTo10Bands(settings.value.eqBandRatios).toMutableList()
        if (bandIndex !in current.indices) return
        current[bandIndex] = normalizedRatio.coerceIn(-1f, 1f)
        viewModelScope.launch {
            settingsRepository.setEqBands("Custom", current)
        }
    }

    fun saveCustomEqPreset(name: String, description: String = "") {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val current = settings.value
        val preset = EqualizerPreset(
            name = trimmed,
            description = description.ifBlank { "Custom 10-band studio curve" },
            bandRatios = PhotonEqPresets.normalizeTo10Bands(current.eqBandRatios),
            bassBoost = current.bassBoostStrength,
            virtualizer = current.virtualizerStrength,
            isCustom = true
        )
        viewModelScope.launch {
            settingsRepository.saveCustomEqPreset(preset)
            _snackbarMessages.tryEmit("Saved custom EQ preset \"$trimmed\"")
        }
    }

    fun deleteCustomEqPreset(name: String) {
        viewModelScope.launch {
            settingsRepository.deleteCustomEqPreset(name)
            _snackbarMessages.tryEmit("Deleted custom preset \"$name\"")
        }
    }

    fun updateSpatialEffects(bassBoost: Int, virtualizer: Int) {
        viewModelScope.launch {
            settingsRepository.setSpatialEffects(bassBoost, virtualizer)
        }
    }

    fun setPreAmpDb(db: Float) {
        viewModelScope.launch { settingsRepository.setPreAmpDb(db) }
    }

    fun setSoftLimiterEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setSoftLimiterEnabled(enabled) }
    }

    fun setLoudnessNormalizerEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setLoudnessNormalizerEnabled(enabled) }
    }

    fun setStereoBalance(balance: Float) {
        viewModelScope.launch { settingsRepository.setStereoBalance(balance) }
    }

    fun setMonoAudioEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setMonoAudioEnabled(enabled) }
    }

    fun setCrossfeedStrength(strength: Float) {
        viewModelScope.launch { settingsRepository.setCrossfeedStrength(strength) }
    }

    fun setReverbPreset(preset: ReverbSpacePreset) {
        viewModelScope.launch { settingsRepository.setReverbPreset(preset) }
    }

    fun setAutoEqByOutputEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAutoEqByOutputEnabled(enabled)
            if (enabled) {
                val category = OutputRouteCategory.fromTransport(playbackState.value.signalInfo.outputTransport)
                lastAppliedOutputCategory = category
                val presetName = settings.value.eqPresetForOutput(category)
                val matched = settings.value.allEqPresets()
                    .firstOrNull { it.name.equals(presetName, ignoreCase = true) }
                if (matched != null) {
                    settingsRepository.setEqBands(matched.name, matched.bandRatios)
                    settingsRepository.setSpatialEffects(matched.bassBoost, matched.virtualizer)
                }
                _snackbarMessages.tryEmit("Auto-EQ active for ${category.label}: $presetName")
            }
        }
    }

    fun setOutputRouteEqPreset(category: OutputRouteCategory, presetName: String) {
        viewModelScope.launch {
            settingsRepository.setOutputRouteEqPreset(category, presetName)
            val currentCategory = OutputRouteCategory.fromTransport(playbackState.value.signalInfo.outputTransport)
            if (settings.value.autoEqByOutputEnabled && currentCategory == category) {
                val matched = settings.value.allEqPresets()
                    .firstOrNull { it.name.equals(presetName, ignoreCase = true) }
                if (matched != null) {
                    settingsRepository.setEqBands(matched.name, matched.bandRatios)
                    settingsRepository.setSpatialEffects(matched.bassBoost, matched.virtualizer)
                }
            }
        }
    }

    fun updateTrackSoundProfile(track: TrackEntity, customGainDb: Float, preferredEqPreset: String?) {
        val clampedGain = customGainDb.coerceIn(-6f, 6f)
        val cleanedEq = preferredEqPreset?.takeIf { it.isNotBlank() }
        viewModelScope.launch {
            libraryRepository.updateTrackSoundProfile(track.id, clampedGain, cleanedEq)
            val updated = track.copy(customGainDb = clampedGain, preferredEqPreset = cleanedEq)
            playerController.updateTrackInQueue(updated)
            if (playbackState.value.currentTrack?.id == track.id) {
                playerController.updateStudioDsp(settings.value.toStudioDspConfig(clampedGain))
                if (cleanedEq != null) {
                    settings.value.allEqPresets()
                        .firstOrNull { it.name.equals(cleanedEq, ignoreCase = true) }
                        ?.let { matched ->
                            settingsRepository.setEqBands(matched.name, matched.bandRatios)
                            settingsRepository.setSpatialEffects(matched.bassBoost, matched.virtualizer)
                        }
                }
            }
        }
    }

    // ---- ReplayGain 2.0 & LUFS Normalization Controls ----

    fun setReplayGainMode(mode: ReplayGainMode) {
        viewModelScope.launch {
            settingsRepository.setReplayGainMode(mode)
            val current = playbackState.value.currentTrack
            if (
                mode != ReplayGainMode.OFF &&
                current != null &&
                current.replayGainTrackDb == null &&
                current.integratedLufs == null &&
                autoScannedTrackIds.add(current.id)
            ) {
                val updated = libraryRepository.scanTrackLoudness(current, forceAudioScan = false)
                if (updated.replayGainTrackDb != null || updated.integratedLufs != null) {
                    playerController.updateTrackInQueue(updated)
                }
            }
        }
    }

    fun setReplayGainTargetLufs(lufs: Float) {
        viewModelScope.launch { settingsRepository.setReplayGainTargetLufs(lufs) }
    }

    fun setReplayGainPreAmpDb(db: Float) {
        viewModelScope.launch { settingsRepository.setReplayGainPreAmpDb(db) }
    }

    fun setReplayGainFallbackPreAmpDb(db: Float) {
        viewModelScope.launch { settingsRepository.setReplayGainFallbackPreAmpDb(db) }
    }

    fun setPreventClipping(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setPreventClipping(enabled) }
    }

    fun scanLibraryLoudness(forceRescan: Boolean = false) {
        viewModelScope.launch {
            if (forceRescan) autoScannedTrackIds.clear()
            val count = libraryRepository.scanLibraryLoudness(forceRescan = forceRescan)
            val currentId = playbackState.value.currentTrack?.id
            if (currentId != null) {
                allTracks.value.firstOrNull { it.id == currentId }?.let { updated ->
                    playerController.updateTrackInQueue(updated)
                    playerController.updateStudioDsp(settings.value.toStudioDspConfig(updated.customGainDb))
                }
            }
            if (count > 0) {
                _snackbarMessages.tryEmit("ReplayGain / LUFS analyzed $count track(s)")
            } else {
                _snackbarMessages.tryEmit("All library tracks already have ReplayGain / LUFS data")
            }
        }
    }

    fun scanCurrentTrackLoudness() {
        val current = playbackState.value.currentTrack ?: return
        viewModelScope.launch {
            val updated = libraryRepository.scanTrackLoudness(current, forceAudioScan = true)
            playerController.updateTrackInQueue(updated)
            playerController.updateStudioDsp(settings.value.toStudioDspConfig(updated.customGainDb))
            val lufsText = updated.integratedLufs?.let { String.format("%.1f LUFS", it) }
            if (lufsText != null) {
                _snackbarMessages.tryEmit("\"${updated.title}\" measured at $lufsText")
            } else {
                _snackbarMessages.tryEmit("Could not decode audio peaks for \"${updated.title}\"")
            }
        }
    }

    fun setWidgetThemeMode(mode: WidgetThemeMode) {
        viewModelScope.launch {
            settingsRepository.setWidgetThemeMode(mode)
            playerController.refreshWidgets()
        }
    }

    fun setWidgetGlassOpacity(opacity: Float) {
        viewModelScope.launch {
            settingsRepository.setWidgetGlassOpacity(opacity)
            playerController.refreshWidgets()
        }
    }

    fun refreshHomeWidgets() {
        playerController.refreshWidgets()
    }

    fun resetSettingsToDefaults() {
        viewModelScope.launch {
            settingsRepository.resetToDefaults()
            _snackbarMessages.tryEmit("Restored default Photon Audio settings on device")
        }
    }

    // ---- Sleep Timer ----

    fun startSleepTimerMinutes(minutes: Int) {
        playerController.startSleepTimerMinutes(minutes)
        _snackbarMessages.tryEmit("Sleep timer set for $minutes min (with gentle fade-out)")
    }

    fun startSleepTimerEndOfTrack() {
        playerController.startSleepTimerEndOfTrack()
        _snackbarMessages.tryEmit("Playback will pause after the current track")
    }

    fun cancelSleepTimer() {
        playerController.cancelSleepTimer()
        _snackbarMessages.tryEmit("Sleep timer cancelled")
    }

    // ---- Sorting Comparator ----

    private fun sortTracks(
        tracks: List<TrackEntity>,
        option: SortOption,
        direction: SortDirection,
        playCounts: Map<Long, Int> = emptyMap()
    ): List<TrackEntity> {
        val comparator: Comparator<TrackEntity> = when (option) {
            SortOption.CUSTOM_ORDER -> compareBy<TrackEntity> { it.sortOrder }.thenBy { it.dateAdded }
            SortOption.TITLE -> compareBy<TrackEntity> { it.title.lowercase() }
            SortOption.DATE_ADDED -> compareBy<TrackEntity> { it.dateAdded }
            SortOption.DURATION -> compareBy<TrackEntity> { it.durationMs }.thenBy { it.title.lowercase() }
            SortOption.BITRATE_CODEC -> compareBy<TrackEntity> {
                when (it.format.uppercase()) {
                    "WAV" -> 4
                    "FLAC" -> 3
                    "OPUS", "AAC" -> 2
                    else -> 1
                }
            }.thenBy { it.bitrateKbps }.thenBy { it.sampleRateHz }
            SortOption.PLAY_COUNT -> compareBy<TrackEntity> { playCounts[it.id] ?: 0 }
                .thenBy { it.title.lowercase() }
            SortOption.ARTIST -> compareBy<TrackEntity> { it.artist.lowercase() }
                .thenBy { it.album.lowercase() }
                .thenBy { it.title.lowercase() }
            SortOption.GENRE -> compareBy<TrackEntity> { it.genre.lowercase() }
                .thenBy { it.artist.lowercase() }
                .thenBy { it.title.lowercase() }
            SortOption.ALBUM -> compareBy<TrackEntity> { it.album.lowercase() }
                .thenBy { it.artist.lowercase() }
                .thenBy { it.title.lowercase() }
            SortOption.PUBLISHER -> compareBy<TrackEntity> { it.publisher.lowercase() }
                .thenBy { it.artist.lowercase() }
                .thenBy { it.title.lowercase() }
            SortOption.RELEASE_YEAR -> compareBy<TrackEntity> { it.releaseYear ?: Int.MIN_VALUE }
                .thenBy { it.title.lowercase() }
        }

        val sorted = tracks.sortedWith(comparator)
        return if (option == SortOption.CUSTOM_ORDER || direction == SortDirection.ASC) {
            sorted
        } else {
            sorted.reversed()
        }
    }

    class Factory(
        private val libraryRepository: LibraryRepository,
        private val settingsRepository: SettingsRepository,
        private val playerController: PhotonPlayerController,
        private val gitHubUpdateChecker: GitHubUpdateChecker
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PhotonViewModel(
                libraryRepository = libraryRepository,
                settingsRepository = settingsRepository,
                playerController = playerController,
                gitHubUpdateChecker = gitHubUpdateChecker
            ) as T
        }
    }
}
