package com.example.player

import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.data.db.TrackEntity
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.roundToInt

/**
 * Connects lazily to PhotonPlaybackService via Media3 MediaController on first playback action,
 * keeping cold startup lightweight while preserving full background playback and system media controls.
 */
class PhotonPlayerController(private val context: Context) {

    private val audioManager: AudioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    val playbackState: StateFlow<PlaybackUiState> = PhotonPlaybackBridge.state
    val spectrumState: StateFlow<AudioSpectrumFrame> = PhotonPlaybackBridge.spectrumState

    private val pendingActions = mutableListOf<(PhotonPlaybackService) -> Unit>()

    // Cached configuration synced automatically when the service starts
    private var cachedCrossfadeSeconds: Int = 4
    private var cachedPlaybackSpeed: Float = 1.0f
    private var cachedSkipSilence: Boolean = false
    private var cachedPauseOnDisconnect: Boolean = true
    private var cachedHandleAudioFocus: Boolean = true
    private var cachedEqEnabled: Boolean = true
    private var cachedEqRatios: List<Float> = listOf(0.55f, 0.22f, -0.05f, 0.38f, 0.62f)
    private var cachedBassStrength: Int = 380
    private var cachedVirtualizerStrength: Int = 320

    fun connect() {
        if (controllerFuture != null) return
        val sessionToken = SessionToken(context, ComponentName(context, PhotonPlaybackService::class.java))
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                runCatching {
                    mediaController = future.get()
                    PhotonPlaybackBridge.serviceInstance?.let { service ->
                        applyCachedConfig(service)
                        val queued = pendingActions.toList()
                        pendingActions.clear()
                        queued.forEach { it(service) }
                    }
                }
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    private fun applyCachedConfig(service: PhotonPlaybackService) {
        service.setCrossfadeSeconds(cachedCrossfadeSeconds)
        service.updatePlaybackOptions(
            speed = cachedPlaybackSpeed,
            skipSilence = cachedSkipSilence,
            pauseOnDisconnect = cachedPauseOnDisconnect,
            audioFocus = cachedHandleAudioFocus
        )
        service.updateEqualizer(
            enabled = cachedEqEnabled,
            ratios = cachedEqRatios,
            bass = cachedBassStrength,
            virtualizer = cachedVirtualizerStrength
        )
    }

    private fun withService(block: (PhotonPlaybackService) -> Unit) {
        val service = PhotonPlaybackBridge.serviceInstance
        if (service != null) {
            block(service)
        } else {
            pendingActions.add(block)
            connect()
        }
    }

    fun playQueue(tracks: List<TrackEntity>, startIndex: Int) {
        withService { it.playQueue(tracks, startIndex) }
    }

    fun togglePlayPause() {
        withService { it.togglePlayPause() }
    }

    fun seekTo(positionMs: Long) {
        PhotonPlaybackBridge.serviceInstance?.seekTo(positionMs)
    }

    fun skipNext() {
        withService { it.skipNext() }
    }

    fun skipPrevious() {
        withService { it.skipPrevious() }
    }

    fun toggleShuffle() {
        withService { it.toggleShuffle() }
    }

    fun cycleRepeatMode() {
        withService { it.cycleRepeatMode() }
    }

    fun setCrossfadeSeconds(seconds: Int) {
        cachedCrossfadeSeconds = seconds.coerceIn(0, 12)
        PhotonPlaybackBridge.updateState { it.copy(crossfadeSeconds = cachedCrossfadeSeconds) }
        PhotonPlaybackBridge.serviceInstance?.setCrossfadeSeconds(cachedCrossfadeSeconds)
    }

    fun updatePlaybackOptions(
        speed: Float,
        skipSilence: Boolean,
        pauseOnDisconnect: Boolean,
        audioFocus: Boolean
    ) {
        cachedPlaybackSpeed = speed.coerceIn(0.5f, 2.0f)
        cachedSkipSilence = skipSilence
        cachedPauseOnDisconnect = pauseOnDisconnect
        cachedHandleAudioFocus = audioFocus
        PhotonPlaybackBridge.serviceInstance?.updatePlaybackOptions(
            speed = cachedPlaybackSpeed,
            skipSilence = cachedSkipSilence,
            pauseOnDisconnect = cachedPauseOnDisconnect,
            audioFocus = cachedHandleAudioFocus
        )
    }

    fun updateEqualizer(enabled: Boolean, ratios: List<Float>, bass: Int, virtualizer: Int) {
        cachedEqEnabled = enabled
        if (ratios.size == 5) cachedEqRatios = ratios
        cachedBassStrength = bass.coerceIn(0, 1000)
        cachedVirtualizerStrength = virtualizer.coerceIn(0, 1000)
        PhotonPlaybackBridge.serviceInstance?.updateEqualizer(
            enabled = cachedEqEnabled,
            ratios = cachedEqRatios,
            bass = cachedBassStrength,
            virtualizer = cachedVirtualizerStrength
        )
    }

    fun updateTrackInQueue(track: TrackEntity) {
        PhotonPlaybackBridge.serviceInstance?.updateTrackInQueue(track)
    }

    fun reorderQueue(tracks: List<TrackEntity>) {
        PhotonPlaybackBridge.serviceInstance?.reorderQueue(tracks)
    }

    fun enqueueNext(track: TrackEntity) {
        withService { it.enqueueNext(track) }
    }

    fun appendToQueue(tracks: List<TrackEntity>) {
        withService { it.appendToQueue(tracks) }
    }

    fun jumpToQueueIndex(index: Int) {
        withService { it.jumpToQueueIndex(index) }
    }

    fun startSleepTimerMinutes(minutes: Int) {
        withService { it.startSleepTimerMinutes(minutes) }
    }

    fun startSleepTimerEndOfTrack() {
        withService { it.startSleepTimerEndOfTrack() }
    }

    fun cancelSleepTimer() {
        PhotonPlaybackBridge.serviceInstance?.cancelSleepTimer()
    }

    // ---- Device Media Volume Gesture Support ----

    fun getVolumeFraction(): Float {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        return (current.toFloat() / max.toFloat()).coerceIn(0f, 1f)
    }

    fun setVolumeFraction(fraction: Float): Float {
        val clamped = fraction.coerceIn(0f, 1f)
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val target = (clamped * max).roundToInt().coerceIn(0, max)
        runCatching {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
        }
        return target.toFloat() / max.toFloat()
    }
}
