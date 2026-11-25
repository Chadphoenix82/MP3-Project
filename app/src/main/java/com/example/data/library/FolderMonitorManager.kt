package com.example.data.library

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.FileObserver
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import com.example.data.db.ExcludedFolderDao
import com.example.data.db.ExcludedFolderEntity
import com.example.data.db.TrackDao
import com.example.data.db.WatchedFolderDao
import com.example.data.db.WatchedFolderEntity
import com.example.data.model.AudioFormat
import com.example.data.network.MetadataAutoCompleteRepository
import com.example.data.prefs.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

data class FolderScanResult(
    val importedCount: Int = 0,
    val skippedByExclusionCount: Int = 0
)

data class FolderMonitorStatus(
    val isMonitoring: Boolean = true,
    val isScanning: Boolean = false,
    val isEnrichingMetadata: Boolean = false,
    val watchedFolderCount: Int = 0,
    val lastEventMessage: String = "Monitoring folders for MP3, FLAC & WAV",
    val recentlyImportedCount: Int = 0,
    val lastScanSkippedCount: Int = 0,
    val lastScannedAt: Long = 0L
)

/**
 * Monitors all configured SAF document-tree folders (via ContentObserver + periodic recursive scan)
 * and the app's local PhotonWatchedMusic directory (via Android FileObserver).
 * Automatically detects and imports MP3, FLAC, and WAV audio files while enforcing
 * user-configured Folder Exclusions and File-Type Exclusions, and triggers web metadata
 * auto-complete for newly imported tracks.
 */
class FolderMonitorManager(
    private val context: Context,
    private val trackDao: TrackDao,
    private val watchedFolderDao: WatchedFolderDao,
    private val excludedFolderDao: ExcludedFolderDao,
    private val audioImporter: AudioImporter,
    private val metadataRepository: MetadataAutoCompleteRepository,
    private val settingsRepository: SettingsRepository,
    private val wavGenerator: StudioWavGenerator
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val scanMutex = Mutex()
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _status = MutableStateFlow(FolderMonitorStatus())
    val status: StateFlow<FolderMonitorStatus> = _status.asStateFlow()

    private var fileObserver: FileObserver? = null
    private val contentObservers = mutableMapOf<String, ContentObserver>()
    private var periodicScanJob: Job? = null

    fun startMonitoring() {
        scope.launch {
            ensureInternalWatchedFolderRegistered()
            setupLocalFileObserver()
            observeWatchedFoldersRegistry()
            startPeriodicWatcher()
            if (settingsRepository.settingsFlow.first().autoScanEnabled) {
                scanAllWatchedFolders(triggerMetadataEnrichment = true)
            }
        }
    }

    private suspend fun ensureInternalWatchedFolderRegistered() {
        val dir = wavGenerator.getWatchedAppMusicDir()
        val internalUri = Uri.fromFile(dir).toString()
        val existing = watchedFolderDao.getWatchedFoldersOnce().any { it.uri == internalUri }
        if (!existing) {
            watchedFolderDao.upsertFolder(
                WatchedFolderEntity(
                    uri = internalUri,
                    displayName = "Photon Watched Music (Device Folder)",
                    lastScannedAt = 0L,
                    trackCount = 0,
                    isAppInternal = true
                )
            )
        }
    }

    private fun setupLocalFileObserver() {
        fileObserver?.stopWatching()
        val watchedDir = wavGenerator.getWatchedAppMusicDir()
        val mask = FileObserver.CREATE or FileObserver.MOVED_TO or FileObserver.CLOSE_WRITE

        val onFileEvent: (String?) -> Unit = { path ->
            if (!path.isNullOrBlank() && isSupportedAudioFilename(path)) {
                scope.launch {
                    val settings = settingsRepository.settingsFlow.first()
                    if (!settings.autoScanEnabled) return@launch
                    delay(350) // allow file write to finish cleanly
                    val file = File(watchedDir, path)
                    if (file.exists() && file.length() > 128) {
                        val excludedFolders = excludedFolderDao.getExcludedFoldersOnce()
                        val folderUri = Uri.fromFile(watchedDir).toString()
                        if (
                            !isFolderExcluded(folderUri, watchedDir.name, excludedFolders) &&
                            !isFileTypeExcluded(file.name, null, settings.excludedFileTypes)
                        ) {
                            importDetectedFile(
                                uri = Uri.fromFile(file),
                                fileName = file.name,
                                mimeType = null,
                                folderUri = folderUri
                            )
                        }
                    }
                }
            }
        }

        fileObserver = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            object : FileObserver(watchedDir, mask) {
                override fun onEvent(event: Int, path: String?) = onFileEvent(path)
            }
        } else {
            @Suppress("DEPRECATION")
            object : FileObserver(watchedDir.absolutePath, mask) {
                override fun onEvent(event: Int, path: String?) = onFileEvent(path)
            }
        }
        fileObserver?.startWatching()
    }

    private fun observeWatchedFoldersRegistry() {
        scope.launch {
            watchedFolderDao.observeWatchedFolders().collect { folders ->
                _status.value = _status.value.copy(watchedFolderCount = folders.size)
                syncSafContentObservers(folders)
            }
        }
    }

    private fun syncSafContentObservers(folders: List<WatchedFolderEntity>) {
        val activeUris = folders.filter { !it.isAppInternal }.map { it.uri }.toSet()

        val toRemove = contentObservers.keys.filter { it !in activeUris }
        toRemove.forEach { uriStr ->
            contentObservers.remove(uriStr)?.let {
                runCatching { context.contentResolver.unregisterContentObserver(it) }
            }
        }

        folders.filter { !it.isAppInternal }.forEach { folder ->
            if (!contentObservers.containsKey(folder.uri)) {
                val treeUri = Uri.parse(folder.uri)
                val observer = object : ContentObserver(mainHandler) {
                    override fun onChange(selfChange: Boolean) {
                        scope.launch {
                            if (settingsRepository.settingsFlow.first().autoScanEnabled) {
                                scanAllWatchedFolders(triggerMetadataEnrichment = true)
                            }
                        }
                    }
                }
                runCatching {
                    val docId = DocumentsContract.getTreeDocumentId(treeUri)
                    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
                    context.contentResolver.registerContentObserver(childrenUri, true, observer)
                    contentObservers[folder.uri] = observer
                }
            }
        }
    }

    private fun startPeriodicWatcher() {
        periodicScanJob?.cancel()
        periodicScanJob = scope.launch {
            while (isActive) {
                delay(12_000)
                val enabled = settingsRepository.settingsFlow.first().autoScanEnabled
                _status.value = _status.value.copy(isMonitoring = enabled)
                if (enabled) {
                    scanAllWatchedFolders(triggerMetadataEnrichment = true)
                }
            }
        }
    }

    suspend fun addWatchedSafFolder(treeUri: Uri): String {
        audioImporter.takePersistablePermission(treeUri)
        val displayName = extractFolderDisplayName(treeUri)
        val entity = WatchedFolderEntity(
            uri = treeUri.toString(),
            displayName = displayName,
            lastScannedAt = 0L,
            trackCount = 0,
            isAppInternal = false
        )
        watchedFolderDao.upsertFolder(entity)
        val result = scanAllWatchedFolders(triggerMetadataEnrichment = true)
        return when {
            result.importedCount > 0 ->
                "Watching \"$displayName\" — auto-imported ${result.importedCount} track(s)"
            result.skippedByExclusionCount > 0 ->
                "Watching \"$displayName\" (${result.skippedByExclusionCount} file(s) skipped by exclusions)"
            else ->
                "Now watching \"$displayName\" for new audio files"
        }
    }

    suspend fun removeWatchedFolder(folderUri: String) {
        watchedFolderDao.deleteFolder(folderUri)
    }

    suspend fun addExcludedSafFolder(treeUri: Uri): String {
        val displayName = extractFolderDisplayName(treeUri)
        val docId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
        val identifier = docId?.takeIf { it.isNotBlank() } ?: treeUri.toString()
        excludedFolderDao.upsertExcludedFolder(
            ExcludedFolderEntity(
                identifier = identifier,
                displayName = displayName,
                isUriTree = true
            )
        )
        return "Excluded folder \"$displayName\" from music scans"
    }

    suspend fun addExcludedFolderName(folderName: String): String {
        val trimmed = folderName.trim()
        if (trimmed.isEmpty()) return "Folder name cannot be empty"
        excludedFolderDao.upsertExcludedFolder(
            ExcludedFolderEntity(
                identifier = "name:${trimmed.lowercase()}",
                displayName = trimmed,
                isUriTree = false
            )
        )
        return "Excluded folders named \"$trimmed\""
    }

    suspend fun removeExcludedFolder(identifier: String) {
        excludedFolderDao.deleteExcludedFolder(identifier)
    }

    suspend fun scanAllWatchedFolders(triggerMetadataEnrichment: Boolean = true): FolderScanResult {
        return scanMutex.withLock {
            _status.value = _status.value.copy(isScanning = true)
            var totalNew = 0
            var totalSkipped = 0
            try {
                val settings = settingsRepository.settingsFlow.first()
                val excludedFolders = excludedFolderDao.getExcludedFoldersOnce()
                val excludedTypes = settings.excludedFileTypes
                val folders = watchedFolderDao.getWatchedFoldersOnce()

                for (folder in folders) {
                    val res = if (folder.isAppInternal) {
                        scanInternalFolder(folder, excludedFolders, excludedTypes, triggerMetadataEnrichment)
                    } else {
                        scanSafFolder(folder, excludedFolders, excludedTypes, triggerMetadataEnrichment)
                    }
                    totalNew += res.importedCount
                    totalSkipped += res.skippedByExclusionCount
                }

                val now = System.currentTimeMillis()
                val msg = when {
                    totalNew > 0 && totalSkipped > 0 ->
                        "Auto-detected $totalNew new track(s) · $totalSkipped excluded"
                    totalNew > 0 ->
                        "Auto-detected & imported $totalNew new track(s)"
                    totalSkipped > 0 ->
                        "Scan complete · $totalSkipped item(s) skipped by exclusions"
                    else ->
                        "Scan complete · library is up to date"
                }

                _status.value = _status.value.copy(
                    recentlyImportedCount = totalNew,
                    lastScanSkippedCount = totalSkipped,
                    lastScannedAt = now,
                    lastEventMessage = msg
                )
            } finally {
                _status.value = _status.value.copy(isScanning = false)
            }
            FolderScanResult(totalNew, totalSkipped)
        }
    }

    private suspend fun scanInternalFolder(
        folder: WatchedFolderEntity,
        excludedFolders: List<ExcludedFolderEntity>,
        excludedFileTypes: Set<String>,
        triggerMetadataEnrichment: Boolean
    ): FolderScanResult {
        val dir = wavGenerator.getWatchedAppMusicDir()
        if (isFolderExcluded(folder.uri, dir.name, excludedFolders) ||
            isFolderExcluded(folder.uri, folder.displayName, excludedFolders)
        ) {
            return FolderScanResult(importedCount = 0, skippedByExclusionCount = 1)
        }

        var imported = 0
        var skipped = 0

        fun walkDir(current: File) {
            val children = current.listFiles().orEmpty()
            for (child in children) {
                if (child.isDirectory) {
                    if (isFolderExcluded(Uri.fromFile(child).toString(), child.name, excludedFolders)) {
                        skipped++
                    } else {
                        walkDir(child)
                    }
                } else if (child.isFile && isSupportedAudioFilename(child.name)) {
                    if (isFileTypeExcluded(child.name, null, excludedFileTypes)) {
                        skipped++
                    }
                }
            }
        }

        // Collect eligible files iteratively so we can suspend during Room/metadata import
        val pendingFiles = mutableListOf<File>()
        val stack = ArrayDeque<File>()
        stack.add(dir)
        while (stack.isNotEmpty()) {
            val current = stack.removeFirst()
            for (child in current.listFiles().orEmpty()) {
                if (child.isDirectory) {
                    if (isFolderExcluded(Uri.fromFile(child).toString(), child.name, excludedFolders)) {
                        skipped++
                    } else {
                        stack.add(child)
                    }
                } else if (child.isFile && isSupportedAudioFilename(child.name)) {
                    if (isFileTypeExcluded(child.name, null, excludedFileTypes)) {
                        skipped++
                    } else {
                        pendingFiles.add(child)
                    }
                }
            }
        }

        for (file in pendingFiles) {
            val added = importDetectedFile(
                uri = Uri.fromFile(file),
                fileName = file.name,
                mimeType = null,
                folderUri = folder.uri,
                allowAutoEnrich = triggerMetadataEnrichment
            )
            if (added) imported++
        }

        val count = trackDao.countTracksInFolder(folder.uri)
        watchedFolderDao.updateScanStats(folder.uri, System.currentTimeMillis(), count)
        return FolderScanResult(imported, skipped)
    }

    private suspend fun scanSafFolder(
        folder: WatchedFolderEntity,
        excludedFolders: List<ExcludedFolderEntity>,
        excludedFileTypes: Set<String>,
        triggerMetadataEnrichment: Boolean
    ): FolderScanResult {
        val treeUri = Uri.parse(folder.uri)
        var imported = 0
        var skipped = 0

        runCatching {
            val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
            if (isFolderExcluded(rootDocId, folder.displayName, excludedFolders) ||
                isFolderExcluded(folder.uri, folder.displayName, excludedFolders)
            ) {
                skipped++
                return@runCatching
            }

            val queue = ArrayDeque<String>()
            queue.add(rootDocId)
            val visited = mutableSetOf<String>()

            while (queue.isNotEmpty()) {
                val currentDocId = queue.removeFirst()
                if (!visited.add(currentDocId)) continue

                val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, currentDocId)
                val projection = arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE
                )
                context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                    val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)

                    while (cursor.moveToNext()) {
                        val docId = if (idIdx >= 0) cursor.getString(idIdx) else continue
                        val name = if (nameIdx >= 0) cursor.getString(nameIdx).orEmpty() else ""
                        val mime = if (mimeIdx >= 0) cursor.getString(mimeIdx).orEmpty() else ""

                        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                            if (isFolderExcluded(docId, name, excludedFolders)) {
                                skipped++
                            } else {
                                queue.add(docId)
                            }
                        } else if (isSupportedAudioFilename(name) || AudioFormat.fromMimeOrName(mime, name) != AudioFormat.OTHER) {
                            if (isFileTypeExcluded(name, mime, excludedFileTypes)) {
                                skipped++
                            } else {
                                val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                                val added = importDetectedFile(
                                    uri = docUri,
                                    fileName = name,
                                    mimeType = mime,
                                    folderUri = folder.uri,
                                    allowAutoEnrich = triggerMetadataEnrichment
                                )
                                if (added) imported++
                            }
                        }
                    }
                }
            }
        }
        val count = trackDao.countTracksInFolder(folder.uri)
        watchedFolderDao.updateScanStats(folder.uri, System.currentTimeMillis(), count)
        return FolderScanResult(imported, skipped)
    }

    private suspend fun importDetectedFile(
        uri: Uri,
        fileName: String,
        mimeType: String?,
        folderUri: String,
        allowAutoEnrich: Boolean = true
    ): Boolean {
        val uriString = uri.toString()
        if (trackDao.getTrackByUri(uriString) != null) return false

        val nextOrder = trackDao.getMaxSortOrder() + 1
        val entity = audioImporter.extractTrackEntity(
            uri = uri,
            sortOrder = nextOrder,
            folderUri = folderUri,
            explicitFileName = fileName,
            explicitMimeType = mimeType
        ) ?: return false

        val insertedId = trackDao.insertTrack(entity)
        if (insertedId <= 0L) return false

        val autoEnrich = allowAutoEnrich && settingsRepository.settingsFlow.first().autoEnrichMetadata
        _status.value = _status.value.copy(
            lastEventMessage = "Detected ${entity.format}: ${entity.title}"
        )

        if (autoEnrich) {
            scope.launch {
                autoEnrichTrackMetadata(insertedId, entity.title, entity.artist)
            }
        }
        return true
    }

    private suspend fun autoEnrichTrackMetadata(trackId: Long, title: String, artist: String) {
        _status.value = _status.value.copy(
            isEnrichingMetadata = true,
            lastEventMessage = "Fetching web metadata & art for \"$title\"..."
        )
        try {
            val query = if (artist != "Unknown Artist") "$artist $title" else title
            val suggestions = metadataRepository.searchMetadata(
                rawQuery = query,
                hintTitle = title,
                hintArtist = artist
            )
            val best = suggestions.firstOrNull()
            if (best != null) {
                trackDao.updateMetadata(
                    trackId = trackId,
                    title = best.title,
                    artist = best.artist,
                    album = best.album,
                    genre = best.genre,
                    publisher = best.publisher,
                    releaseDate = best.releaseDate,
                    releaseYear = best.releaseYear,
                    trackNumber = best.trackNumber,
                    artworkUri = best.artworkUrl,
                    replaceArtwork = !best.artworkUrl.isNullOrBlank()
                )
                _status.value = _status.value.copy(
                    lastEventMessage = "Auto-enriched \"${best.title}\" (${best.publisher})"
                )
            } else {
                _status.value = _status.value.copy(
                    lastEventMessage = "Imported \"$title\" (no web match found)"
                )
            }
        } catch (_: Exception) {
            _status.value = _status.value.copy(
                lastEventMessage = "Imported \"$title\" (offline — tap Auto-Complete later)"
            )
        } finally {
            _status.value = _status.value.copy(isEnrichingMetadata = false)
        }
    }

    private fun extractFolderDisplayName(treeUri: Uri): String {
        val docId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull().orEmpty()
        return docId.substringAfter(":", docId)
            .substringAfterLast("/")
            .ifBlank { "Selected Folder" }
    }

    private fun isFolderExcluded(
        pathOrDocId: String,
        folderName: String,
        excludedFolders: List<ExcludedFolderEntity>
    ): Boolean {
        val lowerPath = pathOrDocId.lowercase()
        val lowerName = folderName.trim().lowercase()
        return excludedFolders.any { excluded ->
            if (excluded.isUriTree) {
                val target = excluded.identifier.lowercase()
                lowerPath == target ||
                    lowerPath.startsWith("$target/") ||
                    lowerPath.contains(target) ||
                    (lowerName.isNotEmpty() && lowerName == excluded.displayName.trim().lowercase())
            } else {
                val pattern = excluded.displayName.trim().lowercase()
                pattern.isNotEmpty() && (lowerName == pattern || lowerPath.contains("/$pattern"))
            }
        }
    }

    private fun isFileTypeExcluded(
        fileName: String,
        mimeType: String?,
        excludedFileTypes: Set<String>
    ): Boolean {
        if (excludedFileTypes.isEmpty()) return false
        val ext = fileName.substringAfterLast('.', "").uppercase()
        if (ext.isNotEmpty() && ext in excludedFileTypes) return true
        val formatLabel = AudioFormat.fromMimeOrName(mimeType, fileName).label.uppercase()
        return formatLabel in excludedFileTypes
    }

    private fun isSupportedAudioFilename(name: String): Boolean {
        val lower = name.lowercase()
        return lower.endsWith(".mp3") ||
            lower.endsWith(".flac") ||
            lower.endsWith(".wav") ||
            lower.endsWith(".opus") ||
            lower.endsWith(".m4a") ||
            lower.endsWith(".ogg") ||
            lower.endsWith(".oga") ||
            lower.endsWith(".aac")
    }
}
