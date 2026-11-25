package com.example

import android.app.Application
import com.example.data.db.PhotonDatabase
import com.example.data.library.AudioImporter
import com.example.data.library.FolderMonitorManager
import com.example.data.library.LibraryRepository
import com.example.data.library.StudioWavGenerator
import com.example.data.network.GitHubUpdateChecker
import com.example.data.network.MetadataAutoCompleteRepository
import com.example.data.prefs.SettingsRepository
import com.example.player.PhotonPlayerController

class PhotonApplication : Application() {

    lateinit var database: PhotonDatabase
        private set
    lateinit var settingsRepository: SettingsRepository
        private set
    lateinit var libraryRepository: LibraryRepository
        private set
    lateinit var playerController: PhotonPlayerController
        private set
    lateinit var gitHubUpdateChecker: GitHubUpdateChecker
        private set

    override fun onCreate() {
        super.onCreate()
        database = PhotonDatabase.getInstance(this)
        settingsRepository = SettingsRepository(this)
        gitHubUpdateChecker = GitHubUpdateChecker(this)
        val metadataRepository = MetadataAutoCompleteRepository()
        val audioImporter = AudioImporter(this)
        val wavGenerator = StudioWavGenerator(this)
        val folderMonitorManager = FolderMonitorManager(
            context = this,
            trackDao = database.trackDao(),
            watchedFolderDao = database.watchedFolderDao(),
            excludedFolderDao = database.excludedFolderDao(),
            audioImporter = audioImporter,
            metadataRepository = metadataRepository,
            settingsRepository = settingsRepository,
            wavGenerator = wavGenerator
        )
        libraryRepository = LibraryRepository(
            trackDao = database.trackDao(),
            playlistDao = database.playlistDao(),
            watchedFolderDao = database.watchedFolderDao(),
            listeningHistoryDao = database.listeningHistoryDao(),
            excludedFolderDao = database.excludedFolderDao(),
            audioImporter = audioImporter,
            metadataRepository = metadataRepository,
            folderMonitorManager = folderMonitorManager,
            wavGenerator = wavGenerator,
            settingsRepository = settingsRepository
        )
        playerController = PhotonPlayerController(this)
        libraryRepository.startFolderMonitoring()
    }
}
