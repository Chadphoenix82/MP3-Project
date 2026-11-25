package com.example.player

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.MediaCodecList
import android.net.Uri
import android.os.Build
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.MainActivity
import com.example.data.db.TrackEntity
import com.example.data.model.AudioSignalInfo
import com.example.data.model.CodecQualityTier
import com.example.data.model.CrossfadeCurveShape
import com.example.data.model.PhotonEqPresets
import com.example.data.model.ReplayGainCalculator
import com.example.data.model.SleepTimerState
import com.example.data.model.StudioDspConfig
import com.example.widget.PhotonWidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class RepeatModeState {
    OFF, ALL, ONE
}

data class PlaybackUiState(
    val queue: List<TrackEntity> = emptyList(),
    val currentIndex: Int = -1,
    val currentTrack: TrackEntity? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatModeState = RepeatModeState.ALL,
    val crossfadeSeconds: Int = 4,
    val isCrossfading: Boolean = false,
    val playbackSpeed: Float = 1.0f,
    val playbackPitch: Float = 1.0f,
    val abLoopStartMs: Long? = null,
    val abLoopEndMs: Long? = null,
    val abLoopActive: Boolean = false,
    val sleepTimer: SleepTimerState = SleepTimerState(),
    val eqTelemetry: EqTelemetry = EqTelemetry(),
    val signalInfo: AudioSignalInfo = AudioSignalInfo()
)

/**
 * Process-local bridge allowing UI controllers and PhotonPlaybackService to stay tightly synchronized
 * for dual-player crossfade, hardware EQ attachment, sleep timer fade-out, and queue management.
 */
object PhotonPlaybackBridge {
    private val _state = MutableStateFlow(PlaybackUiState())
    val state: StateFlow<PlaybackUiState> = _state.asStateFlow()

    private val _spectrumState = MutableStateFlow(AudioSpectrumFrame())
    val spectrumState: StateFlow<AudioSpectrumFrame> = _spectrumState.asStateFlow()

    @Volatile
    var serviceInstance: PhotonPlaybackService? = null

    internal fun updateState(transform: (PlaybackUiState) -> PlaybackUiState) {
        _state.value = transform(_state.value)
    }

    internal fun updateSpectrum(frame: AudioSpectrumFrame) {
        _spectrumState.value = frame
    }
}

class PhotonPlaybackService : MediaSessionService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var playerA: ExoPlayer
    private var playerB: ExoPlayer? = null
    private var activePlayer: ExoPlayer? = null
    private var standbyPlayer: ExoPlayer? = null
    private val spectrumProcessors = mutableListOf<PhotonSpectrumAudioProcessor>()
    private val playerSignalSnapshots = mutableMapOf<ExoPlayer, AudioSignalInfo>()

    private var mediaSession: MediaSession? = null
    private val audioEffects = PhotonAudioEffects()
    private var outputMonitor: AudioOutputMonitor? = null
    private var lastEqEnabled: Boolean = true
    private var lastEqRatios: List<Float> = PhotonEqPresets.default10BandRatios
    private var lastBassStrength: Int = 380
    private var lastVirtualizerStrength: Int = 320
    private var lastStudioDsp: StudioDspConfig = StudioDspConfig()
    private var crossfadeCurve: CrossfadeCurveShape = CrossfadeCurveShape.EQUAL_POWER
    private var lastWidgetTickMs: Long = 0L

    private var positionTickerJob: Job? = null
    private var crossfadeJob: Job? = null
    private var sleepTimerJob: Job? = null

    private var queue: MutableList<TrackEntity> = mutableListOf()
    private var currentIndex: Int = -1
    private var shuffleEnabled: Boolean = false
    private var repeatMode: RepeatModeState = RepeatModeState.ALL
    private var crossfadeSeconds: Int = 4
    private var playbackSpeed: Float = 1.0f
    private var playbackPitch: Float = 1.0f
    private var abLoopStartMs: Long? = null
    private var abLoopEndMs: Long? = null
    private var skipSilenceEnabled: Boolean = false
    private var pauseOnAudioDisconnect: Boolean = true
    private var handleAudioFocus: Boolean = true
    private var isCrossfading: Boolean = false
    private var sleepEndOfTrack: Boolean = false

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            val active = activePlayer ?: return
            if (isPlaying) {
                attachEffectsToActivePlayer()
            }
            PhotonPlaybackBridge.updateState {
                it.copy(
                    isPlaying = active.isPlaying,
                    isBuffering = active.playbackState == Player.STATE_BUFFERING
                )
            }
            PhotonWidgetUpdater.requestUpdate(this@PhotonPlaybackService)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val active = activePlayer ?: return
            PhotonPlaybackBridge.updateState {
                it.copy(
                    isPlaying = active.isPlaying,
                    isBuffering = playbackState == Player.STATE_BUFFERING,
                    durationMs = active.duration.takeIf { d -> d > 0 } ?: it.currentTrack?.durationMs ?: 0L
                )
            }
            if (playbackState == Player.STATE_ENDED && !isCrossfading) {
                if (sleepEndOfTrack) {
                    completeSleepTimerPause()
                } else {
                    advanceToNextTrack(useQuickCrossfade = false)
                }
            }
        }
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        playerA = buildExoPlayer()
        activePlayer = playerA
        standbyPlayer = null
        playerA.addListener(playerListener)

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val sessionPendingIntent = PendingIntent.getActivity(
            this,
            1001,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, playerA)
            .setSessionActivity(sessionPendingIntent)
            .build()

        PhotonPlaybackBridge.serviceInstance = this
        outputMonitor = AudioOutputMonitor(this) { route ->
            PhotonPlaybackBridge.updateState { st ->
                st.copy(
                    signalInfo = st.signalInfo.copy(
                        outputDeviceName = route.deviceName,
                        outputTransport = route.transport,
                        supportedOutputCodecs = route.supportedCodecs,
                        supportedSampleRatesHz = route.supportedSampleRatesHz
                    )
                )
            }
        }.also { it.start() }
        startPositionTicker()
    }

    @OptIn(UnstableApi::class)
    private fun buildExoPlayer(): ExoPlayer {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        var builtPlayerRef: ExoPlayer? = null
        val processor = PhotonSpectrumAudioProcessor(
            isActivePlayer = { builtPlayerRef != null && builtPlayerRef === activePlayer },
            onSpectrumFrame = { frame -> PhotonPlaybackBridge.updateSpectrum(frame) },
            onPcmFormatConfigured = { pcmBits, outHz, channels ->
                PhotonPlaybackBridge.updateState { st ->
                    st.copy(
                        signalInfo = st.signalInfo.copy(
                            decodedPcmBitDepthBits = pcmBits,
                            outputSampleRateHz = outHz,
                            channelCount = channels.coerceAtLeast(1)
                        )
                    )
                }
            }
        ).apply {
            updateEqualizerProfile(
                enabled = lastEqEnabled,
                ratios = lastEqRatios,
                frequenciesHz = audioEffects.telemetry.bandFrequenciesHz,
                bassStrength = lastBassStrength
            )
            updateStudioDsp(effectiveStudioDsp())
        }
        spectrumProcessors.add(processor)

        val renderersFactory = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink {
                return DefaultAudioSink.Builder(context)
                    .setAudioProcessors(arrayOf(processor))
                    .build()
            }
        }

        val analyticsListener = object : AnalyticsListener {
            override fun onAudioDecoderInitialized(
                eventTime: AnalyticsListener.EventTime,
                decoderName: String,
                initializedTimestampMs: Long,
                initializationDurationMs: Long
            ) {
                val player = builtPlayerRef ?: return
                val isHw = isHardwareDecoder(decoderName)
                val cached = (playerSignalSnapshots[player] ?: AudioSignalInfo()).copy(
                    decoderName = decoderName,
                    isHardwareAccelerated = isHw,
                    decoderDetected = true
                )
                playerSignalSnapshots[player] = cached

                if (player !== activePlayer) return
                PhotonPlaybackBridge.updateState { st ->
                    st.copy(
                        signalInfo = st.signalInfo.copy(
                            decoderName = decoderName,
                            isHardwareAccelerated = isHw,
                            decoderDetected = true
                        )
                    )
                }
            }

            override fun onAudioInputFormatChanged(
                eventTime: AnalyticsListener.EventTime,
                format: Format,
                decoderReuseEvaluation: DecoderReuseEvaluation?
            ) {
                val player = builtPlayerRef ?: return
                val track = queue.getOrNull(currentIndex)
                val mime = format.sampleMimeType.orEmpty()
                val (codecName, tier) = classifyCodec(mime, track?.format)
                val bitDepth = resolveSourceBitDepth(format.pcmEncoding, track?.format)
                val liveKbps = when {
                    format.bitrate > 0 -> (format.bitrate / 1000).coerceAtLeast(1)
                    format.averageBitrate > 0 -> (format.averageBitrate / 1000).coerceAtLeast(1)
                    else -> track?.bitrateKbps ?: 0
                }
                val isRawPcm = mime.equals("audio/raw", ignoreCase = true)

                val existingSnap = playerSignalSnapshots[player] ?: AudioSignalInfo()
                val updatedSnap = existingSnap.copy(
                    codecDisplayName = codecName,
                    mimeType = mime.ifBlank { existingSnap.mimeType },
                    qualityTier = tier,
                    sourceBitDepthBits = bitDepth,
                    sourceSampleRateHz = format.sampleRate.takeIf { it > 0 } ?: track?.sampleRateHz ?: existingSnap.sourceSampleRateHz,
                    channelCount = format.channelCount.takeIf { it > 0 } ?: existingSnap.channelCount,
                    liveBitrateKbps = liveKbps,
                    decoderName = if (isRawPcm && !existingSnap.decoderDetected) "Direct PCM Pass-Through" else existingSnap.decoderName,
                    isHardwareAccelerated = if (isRawPcm && !existingSnap.decoderDetected) true else existingSnap.isHardwareAccelerated,
                    decoderDetected = if (isRawPcm) true else existingSnap.decoderDetected
                )
                playerSignalSnapshots[player] = updatedSnap

                if (player !== activePlayer) return
                PhotonPlaybackBridge.updateState { st ->
                    val currentSig = st.signalInfo
                    st.copy(
                        signalInfo = currentSig.copy(
                            codecDisplayName = updatedSnap.codecDisplayName,
                            mimeType = updatedSnap.mimeType.ifBlank { currentSig.mimeType },
                            qualityTier = updatedSnap.qualityTier,
                            sourceBitDepthBits = updatedSnap.sourceBitDepthBits,
                            sourceSampleRateHz = updatedSnap.sourceSampleRateHz,
                            channelCount = updatedSnap.channelCount,
                            liveBitrateKbps = updatedSnap.liveBitrateKbps,
                            decoderName = if (isRawPcm && !currentSig.decoderDetected) "Direct PCM Pass-Through" else currentSig.decoderName,
                            isHardwareAccelerated = if (isRawPcm && !currentSig.decoderDetected) true else currentSig.isHardwareAccelerated,
                            decoderDetected = if (isRawPcm) true else currentSig.decoderDetected
                        )
                    )
                }
            }
        }

        return ExoPlayer.Builder(this, renderersFactory)
            .setAudioAttributes(audioAttributes, handleAudioFocus)
            .setHandleAudioBecomingNoisy(pauseOnAudioDisconnect)
            .build()
            .also { player ->
                builtPlayerRef = player
                player.addAnalyticsListener(analyticsListener)
                player.playbackParameters = PlaybackParameters(playbackSpeed, playbackPitch)
                player.skipSilenceEnabled = skipSilenceEnabled
            }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        PhotonPlaybackBridge.serviceInstance = null
        outputMonitor?.stop()
        outputMonitor = null
        positionTickerJob?.cancel()
        crossfadeJob?.cancel()
        sleepTimerJob?.cancel()
        serviceScope.cancel()
        audioEffects.release()
        mediaSession?.release()
        mediaSession = null
        playerA.removeListener(playerListener)
        playerB?.removeListener(playerListener)
        playerA.release()
        playerB?.release()
        playerB = null
        super.onDestroy()
    }

    // ---- Playback Operations ----

    fun playQueue(tracks: List<TrackEntity>, startIndex: Int) {
        if (tracks.isEmpty()) return
        cancelCrossfade()
        queue = tracks.toMutableList()
        currentIndex = startIndex.coerceIn(0, queue.lastIndex)
        loadAndPlayOnActive(queue[currentIndex])
        publishQueueState()
    }

    fun updateTrackInQueue(updated: TrackEntity) {
        val idx = queue.indexOfFirst { it.id == updated.id }
        if (idx >= 0) {
            queue[idx] = updated
            if (idx == currentIndex) {
                syncProcessorsWithEqualizer()
                publishQueueState()
            } else {
                PhotonPlaybackBridge.updateState { it.copy(queue = queue.toList()) }
            }
        }
    }

    fun reorderQueue(newOrder: List<TrackEntity>) {
        val playingTrackId = queue.getOrNull(currentIndex)?.id
        queue = newOrder.toMutableList()
        if (playingTrackId != null) {
            val newIdx = queue.indexOfFirst { it.id == playingTrackId }
            if (newIdx >= 0) currentIndex = newIdx
        }
        publishQueueState()
    }

    fun enqueueNext(track: TrackEntity) {
        if (queue.isEmpty() || currentIndex !in queue.indices) {
            playQueue(listOf(track), 0)
            return
        }
        // Remove any duplicate occurrence strictly after currentIndex so it moves cleanly to next
        val upcomingIdx = queue.drop(currentIndex + 1).indexOfFirst { it.id == track.id }
        if (upcomingIdx >= 0) {
            queue.removeAt(currentIndex + 1 + upcomingIdx)
        }
        val insertAt = (currentIndex + 1).coerceAtMost(queue.size)
        queue.add(insertAt, track)
        publishQueueState()
    }

    fun appendToQueue(tracks: List<TrackEntity>) {
        if (tracks.isEmpty()) return
        if (queue.isEmpty() || currentIndex !in queue.indices) {
            playQueue(tracks, 0)
            return
        }
        val upcomingIds = queue.drop(currentIndex).map { it.id }.toMutableSet()
        var added = false
        for (track in tracks) {
            if (upcomingIds.add(track.id)) {
                queue.add(track)
                added = true
            }
        }
        if (added) {
            publishQueueState()
        }
    }

    fun jumpToQueueIndex(targetIndex: Int) {
        if (targetIndex !in queue.indices) return
        if (targetIndex == currentIndex) {
            togglePlayPause()
            return
        }
        if (crossfadeSeconds > 0 && (activePlayer?.isPlaying == true)) {
            startCrossfadeToIndex(targetIndex, durationMs = minOf(crossfadeSeconds * 1000L, 1200L))
        } else {
            cancelCrossfade()
            currentIndex = targetIndex
            loadAndPlayOnActive(queue[currentIndex])
            publishQueueState()
        }
    }

    fun removeFromQueue(index: Int) {
        if (index !in queue.indices) return
        if (queue.size <= 1) return
        val removingCurrent = index == currentIndex
        queue.removeAt(index)
        when {
            removingCurrent -> {
                currentIndex = index.coerceAtMost(queue.lastIndex)
                cancelCrossfade()
                loadAndPlayOnActive(queue[currentIndex])
            }
            index < currentIndex -> {
                currentIndex = (currentIndex - 1).coerceAtLeast(0)
            }
        }
        publishQueueState()
    }

    fun clearUpcomingQueue() {
        if (queue.isEmpty() || currentIndex !in queue.indices) return
        if (currentIndex < queue.lastIndex) {
            queue = queue.subList(0, currentIndex + 1).toMutableList()
            publishQueueState()
        }
    }

    fun setAbLoopStart() {
        val pos = activePlayer?.currentPosition?.coerceAtLeast(0L) ?: return
        abLoopStartMs = pos
        if (abLoopEndMs != null && abLoopEndMs!! <= pos + 500L) {
            abLoopEndMs = null
        }
        publishAbLoopState()
    }

    fun setAbLoopEnd() {
        val active = activePlayer ?: return
        val pos = active.currentPosition.coerceAtLeast(0L)
        val start = abLoopStartMs ?: 0L
        if (pos <= start + 500L) {
            val dur = active.duration.takeIf { it > 0L } ?: queue.getOrNull(currentIndex)?.durationMs ?: 0L
            abLoopEndMs = (start + 2000L).coerceAtMost(dur.coerceAtLeast(start + 1000L))
        } else {
            abLoopEndMs = pos
        }
        abLoopStartMs = start
        publishAbLoopState()
    }

    fun clearAbLoop() {
        abLoopStartMs = null
        abLoopEndMs = null
        publishAbLoopState()
    }

    private fun publishAbLoopState() {
        val start = abLoopStartMs
        val end = abLoopEndMs
        val active = start != null && end != null && end > start + 400L
        PhotonPlaybackBridge.updateState {
            it.copy(
                abLoopStartMs = start,
                abLoopEndMs = end,
                abLoopActive = active
            )
        }
    }

    fun togglePlayPause() {
        val active = activePlayer ?: return
        if (queue.isEmpty()) return
        if (active.playbackState == Player.STATE_IDLE && currentIndex in queue.indices) {
            loadAndPlayOnActive(queue[currentIndex])
            return
        }
        if (active.isPlaying) {
            active.pause()
        } else {
            active.volume = 1f
            active.play()
        }
    }

    fun seekTo(positionMs: Long) {
        activePlayer?.seekTo(positionMs.coerceAtLeast(0L))
        PhotonPlaybackBridge.updateState { it.copy(positionMs = positionMs.coerceAtLeast(0L)) }
    }

    fun skipNext() {
        if (queue.isEmpty()) return
        advanceToNextTrack(useQuickCrossfade = crossfadeSeconds > 0 && (activePlayer?.isPlaying == true))
    }

    fun skipPrevious() {
        if (queue.isEmpty()) return
        val active = activePlayer
        if (active != null && active.currentPosition > 3500L) {
            active.seekTo(0L)
            return
        }
        val prevIdx = computePreviousIndex() ?: return
        if (crossfadeSeconds > 0 && (active?.isPlaying == true)) {
            startCrossfadeToIndex(prevIdx, durationMs = minOf(crossfadeSeconds * 1000L, 1200L))
        } else {
            cancelCrossfade()
            currentIndex = prevIdx
            loadAndPlayOnActive(queue[currentIndex])
            publishQueueState()
        }
    }

    fun toggleShuffle() {
        shuffleEnabled = !shuffleEnabled
        PhotonPlaybackBridge.updateState { it.copy(shuffleEnabled = shuffleEnabled) }
        PhotonWidgetUpdater.requestUpdate(this)
    }

    fun cycleRepeatMode() {
        repeatMode = when (repeatMode) {
            RepeatModeState.OFF -> RepeatModeState.ALL
            RepeatModeState.ALL -> RepeatModeState.ONE
            RepeatModeState.ONE -> RepeatModeState.OFF
        }
        PhotonPlaybackBridge.updateState { it.copy(repeatMode = repeatMode) }
    }

    fun setCrossfadeSeconds(seconds: Int) {
        crossfadeSeconds = seconds.coerceIn(0, 12)
        PhotonPlaybackBridge.updateState { it.copy(crossfadeSeconds = crossfadeSeconds) }
    }

    fun setCrossfadeCurve(curve: CrossfadeCurveShape) {
        crossfadeCurve = curve
    }

    @OptIn(UnstableApi::class)
    fun updatePlaybackOptions(
        speed: Float,
        pitch: Float = playbackPitch,
        skipSilence: Boolean,
        pauseOnDisconnect: Boolean,
        audioFocus: Boolean
    ) {
        playbackSpeed = speed.coerceIn(0.5f, 2.0f)
        playbackPitch = pitch.coerceIn(0.5f, 1.5f)
        skipSilenceEnabled = skipSilence
        pauseOnAudioDisconnect = pauseOnDisconnect
        handleAudioFocus = audioFocus

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        val params = PlaybackParameters(playbackSpeed, playbackPitch)
        listOfNotNull(activePlayer, standbyPlayer).forEach { player ->
            player.playbackParameters = params
            player.skipSilenceEnabled = skipSilenceEnabled
            player.setHandleAudioBecomingNoisy(pauseOnAudioDisconnect)
            player.setAudioAttributes(audioAttributes, handleAudioFocus)
        }
        PhotonPlaybackBridge.updateState {
            it.copy(playbackSpeed = playbackSpeed, playbackPitch = playbackPitch)
        }
    }

    fun updateEqualizer(enabled: Boolean, ratios: List<Float>, bass: Int, virtualizer: Int) {
        lastEqEnabled = enabled
        lastEqRatios = PhotonEqPresets.normalizeTo10Bands(ratios)
        lastBassStrength = bass.coerceIn(0, 1000)
        lastVirtualizerStrength = virtualizer.coerceIn(0, 1000)
        audioEffects.updateSettings(enabled, lastEqRatios, lastBassStrength, lastVirtualizerStrength, effectiveStudioDsp())
        syncProcessorsWithEqualizer()
        PhotonPlaybackBridge.updateState { it.copy(eqTelemetry = audioEffects.telemetry) }
    }

    fun updateStudioDsp(config: StudioDspConfig) {
        lastStudioDsp = config
        val effective = effectiveStudioDsp()
        audioEffects.updateSettings(lastEqEnabled, lastEqRatios, lastBassStrength, lastVirtualizerStrength, effective)
        syncProcessorsWithEqualizer()
        PhotonPlaybackBridge.updateState { it.copy(eqTelemetry = audioEffects.telemetry) }
    }

    private fun effectiveStudioDsp(): StudioDspConfig {
        val currentTrack = queue.getOrNull(currentIndex)
        val currentTrackGain = currentTrack?.customGainDb ?: 0f
        val effectiveRg = ReplayGainCalculator.computeEffectiveGain(
            mode = lastStudioDsp.replayGainMode,
            targetLufs = lastStudioDsp.replayGainTargetLufs,
            rgPreAmpDb = lastStudioDsp.replayGainPreAmpDb,
            fallbackPreAmpDb = lastStudioDsp.replayGainFallbackPreAmpDb,
            preventClipping = lastStudioDsp.preventClipping,
            trackGainDb = currentTrack?.replayGainTrackDb,
            albumGainDb = currentTrack?.replayGainAlbumDb,
            trackPeak = currentTrack?.replayGainTrackPeak,
            albumPeak = currentTrack?.replayGainAlbumPeak,
            integratedLufs = currentTrack?.integratedLufs,
            sourceCode = currentTrack?.replayGainSource
        )
        return lastStudioDsp.copy(
            trackGainOffsetDb = currentTrackGain,
            activeReplayGainDb = effectiveRg.appliedGainDb
        )
    }

    private fun syncProcessorsWithEqualizer() {
        val freqs = audioEffects.telemetry.bandFrequenciesHz
        val effectiveDsp = effectiveStudioDsp()
        spectrumProcessors.forEach { processor ->
            processor.updateEqualizerProfile(
                enabled = lastEqEnabled,
                ratios = lastEqRatios,
                frequenciesHz = freqs,
                bassStrength = lastBassStrength
            )
            processor.updateStudioDsp(effectiveDsp)
        }
    }

    // ---- Sleep Timer ----

    fun startSleepTimerMinutes(minutes: Int) {
        sleepTimerJob?.cancel()
        sleepEndOfTrack = false
        val totalMs = minutes.coerceAtLeast(1) * 60_000L
        val endTime = System.currentTimeMillis() + totalMs

        sleepTimerJob = serviceScope.launch {
            while (isActive) {
                val remaining = (endTime - System.currentTimeMillis()).coerceAtLeast(0L)
                PhotonPlaybackBridge.updateState {
                    it.copy(
                        sleepTimer = SleepTimerState(
                            isActive = true,
                            remainingMs = remaining,
                            endOfTrack = false,
                            label = formatCountdown(remaining)
                        )
                    )
                }
                if (remaining in 1L..5000L) {
                    val fadeGain = (remaining.toFloat() / 5000f).coerceIn(0.05f, 1f)
                    activePlayer?.volume = fadeGain
                }
                if (remaining <= 0L) {
                    completeSleepTimerPause()
                    break
                }
                delay(500L)
            }
        }
    }

    fun startSleepTimerEndOfTrack() {
        sleepTimerJob?.cancel()
        sleepEndOfTrack = true
        PhotonPlaybackBridge.updateState {
            it.copy(
                sleepTimer = SleepTimerState(
                    isActive = true,
                    remainingMs = 0L,
                    endOfTrack = true,
                    label = "End of track"
                )
            )
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        sleepEndOfTrack = false
        activePlayer?.volume = 1f
        PhotonPlaybackBridge.updateState { it.copy(sleepTimer = SleepTimerState()) }
    }

    private fun completeSleepTimerPause() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        sleepEndOfTrack = false
        cancelCrossfade()
        activePlayer?.pause()
        activePlayer?.volume = 1f
        PhotonPlaybackBridge.updateState {
            it.copy(
                isPlaying = false,
                sleepTimer = SleepTimerState()
            )
        }
    }

    // ---- Internal Playback & Dual-Player Equal-Power Crossfade ----

    private fun loadAndPlayOnActive(track: TrackEntity) {
        val active = activePlayer ?: return
        clearAbLoop()
        playerSignalSnapshots.remove(active)
        seedSignalInfoFromTrack(track)
        active.stop()
        active.clearMediaItems()
        active.volume = 1f
        active.playbackParameters = PlaybackParameters(playbackSpeed, playbackPitch)
        active.setMediaItem(track.toMediaItem())
        active.prepare()
        active.play()
        syncProcessorsWithEqualizer()
        attachEffectsToActivePlayer()
    }

    private fun seedSignalInfoFromTrack(track: TrackEntity) {
        val (codecName, tier) = classifyCodec("", track.format)
        PhotonPlaybackBridge.updateState { st ->
            st.copy(
                signalInfo = st.signalInfo.copy(
                    codecDisplayName = codecName,
                    qualityTier = tier,
                    sourceBitDepthBits = if (track.format.equals("FLAC", ignoreCase = true) || track.format.equals("WAV", ignoreCase = true)) 16 else 16,
                    sourceSampleRateHz = track.sampleRateHz.coerceAtLeast(8000),
                    liveBitrateKbps = track.bitrateKbps,
                    decoderDetected = false,
                    decoderName = if (track.format.equals("WAV", ignoreCase = true)) "Direct PCM Pass-Through" else "Initializing decoder..."
                )
            )
        }
    }

    private fun advanceToNextTrack(useQuickCrossfade: Boolean) {
        val nextIdx = computeNextIndex()
        if (nextIdx == null) {
            activePlayer?.pause()
            PhotonPlaybackBridge.updateState { it.copy(isPlaying = false) }
            return
        }
        if (useQuickCrossfade) {
            startCrossfadeToIndex(nextIdx, durationMs = minOf(crossfadeSeconds * 1000L, 1200L))
        } else {
            cancelCrossfade()
            currentIndex = nextIdx
            loadAndPlayOnActive(queue[currentIndex])
            publishQueueState()
        }
    }

    @OptIn(UnstableApi::class)
    private fun startCrossfadeToIndex(targetIndex: Int, durationMs: Long) {
        if (isCrossfading || targetIndex !in queue.indices) return
        val outgoing = activePlayer ?: return
        val incoming = standbyPlayer ?: buildExoPlayer().also {
            playerB = it
            standbyPlayer = it
        }

        isCrossfading = true
        PhotonPlaybackBridge.updateState { it.copy(isCrossfading = true) }

        val nextTrack = queue[targetIndex]
        clearAbLoop()
        playerSignalSnapshots.remove(incoming)
        incoming.stop()
        incoming.clearMediaItems()
        incoming.volume = 0f
        incoming.playbackParameters = PlaybackParameters(playbackSpeed, playbackPitch)
        incoming.setMediaItem(nextTrack.toMediaItem())
        incoming.prepare()
        incoming.play()

        crossfadeJob?.cancel()
        crossfadeJob = serviceScope.launch {
            val safeDuration = durationMs.coerceIn(500L, 12_000L)
            val steps = (safeDuration / 40L).toInt().coerceAtLeast(10)
            val stepDelay = safeDuration / steps

            for (step in 1..steps) {
                val progress = step.toFloat() / steps.toFloat()
                val (outGain, inGain) = crossfadeCurve.computeGains(progress)
                outgoing.volume = outGain
                incoming.volume = inGain
                delay(stepDelay)
            }

            // Seamless handoff
            outgoing.removeListener(playerListener)
            outgoing.pause()
            outgoing.stop()
            outgoing.volume = 1f

            incoming.volume = 1f
            incoming.addListener(playerListener)

            activePlayer = incoming
            standbyPlayer = outgoing
            currentIndex = targetIndex
            isCrossfading = false

            seedSignalInfoFromTrack(nextTrack)
            playerSignalSnapshots[incoming]?.let { snap ->
                PhotonPlaybackBridge.updateState { st ->
                    st.copy(
                        signalInfo = st.signalInfo.copy(
                            codecDisplayName = snap.codecDisplayName.ifBlank { st.signalInfo.codecDisplayName },
                            mimeType = snap.mimeType.ifBlank { st.signalInfo.mimeType },
                            qualityTier = snap.qualityTier,
                            sourceBitDepthBits = snap.sourceBitDepthBits,
                            sourceSampleRateHz = snap.sourceSampleRateHz,
                            channelCount = snap.channelCount,
                            liveBitrateKbps = snap.liveBitrateKbps.takeIf { it > 0 } ?: st.signalInfo.liveBitrateKbps,
                            decoderName = if (snap.decoderDetected) snap.decoderName else st.signalInfo.decoderName,
                            isHardwareAccelerated = if (snap.decoderDetected) snap.isHardwareAccelerated else st.signalInfo.isHardwareAccelerated,
                            decoderDetected = snap.decoderDetected || st.signalInfo.decoderDetected
                        )
                    )
                }
            }

            mediaSession?.player = incoming
            attachEffectsToActivePlayer()
            publishQueueState()
        }
    }

    private fun cancelCrossfade() {
        crossfadeJob?.cancel()
        crossfadeJob = null
        if (isCrossfading) {
            standbyPlayer?.stop()
            standbyPlayer?.volume = 1f
            activePlayer?.volume = 1f
            isCrossfading = false
            PhotonPlaybackBridge.updateState { it.copy(isCrossfading = false) }
        }
    }

    private fun startPositionTicker() {
        positionTickerJob?.cancel()
        positionTickerJob = serviceScope.launch {
            while (isActive) {
                val active = activePlayer
                if (active != null && currentIndex in queue.indices) {
                    val pos = active.currentPosition.coerceAtLeast(0L)
                    val dur = active.duration.takeIf { it > 0L } ?: queue[currentIndex].durationMs
                    PhotonPlaybackBridge.updateState {
                        it.copy(
                            positionMs = pos,
                            durationMs = dur,
                            isPlaying = active.isPlaying
                        )
                    }

                    // Precision A-B Loop enforcement
                    val loopStart = abLoopStartMs
                    val loopEnd = abLoopEndMs
                    val isLoopingSegment = loopStart != null && loopEnd != null && loopEnd > loopStart + 400L
                    if (isLoopingSegment && active.isPlaying && pos >= loopEnd!!) {
                        active.seekTo(loopStart!!)
                        PhotonPlaybackBridge.updateState { it.copy(positionMs = loopStart) }
                    }

                    // Trigger automatic dual-player crossfade when approaching track end
                    val crossfadeMs = crossfadeSeconds * 1000L
                    if (
                        active.isPlaying &&
                        !isCrossfading &&
                        !isLoopingSegment &&
                        !sleepEndOfTrack &&
                        crossfadeMs > 0L &&
                        dur > crossfadeMs + 1500L &&
                        (dur - pos) in 200L..crossfadeMs
                    ) {
                        val nextIdx = computeNextIndex()
                        if (nextIdx != null) {
                            startCrossfadeToIndex(nextIdx, durationMs = (dur - pos).coerceAtLeast(800L))
                        }
                    }
                    if (!active.isPlaying) {
                        decaySpectrumWhenIdle()
                    } else {
                        val nowMs = System.currentTimeMillis()
                        if (nowMs - lastWidgetTickMs >= 2000L) {
                            lastWidgetTickMs = nowMs
                            PhotonWidgetUpdater.requestUpdate(this@PhotonPlaybackService)
                        }
                    }
                } else {
                    decaySpectrumWhenIdle()
                }
                delay(150L)
            }
        }
    }

    private fun decaySpectrumWhenIdle() {
        val current = PhotonPlaybackBridge.spectrumState.value
        if (current.peakLevel > 0.005f || current.isLiveAudio) {
            val decayedBars = current.bars.map { (it * 0.65f).let { v -> if (v < 0.01f) 0f else v } }
            val decayedBands = current.eqBandLevels.map { (it * 0.65f).let { v -> if (v < 0.01f) 0f else v } }
            val maxPeak = decayedBars.maxOrNull() ?: 0f
            PhotonPlaybackBridge.updateSpectrum(
                current.copy(
                    bars = decayedBars,
                    eqBandLevels = decayedBands,
                    bassEnergy = (current.bassEnergy * 0.6f).let { if (it < 0.01f) 0f else it },
                    peakLevel = maxPeak,
                    isLiveAudio = false
                )
            )
        }
    }

    private fun computeNextIndex(): Int? {
        if (queue.isEmpty()) return null
        if (repeatMode == RepeatModeState.ONE) return currentIndex.coerceIn(0, queue.lastIndex)
        if (shuffleEnabled && queue.size > 1) {
            val candidates = queue.indices.filter { it != currentIndex }
            return candidates.random()
        }
        return when {
            currentIndex + 1 < queue.size -> currentIndex + 1
            repeatMode == RepeatModeState.ALL -> 0
            else -> null
        }
    }

    private fun computePreviousIndex(): Int? {
        if (queue.isEmpty()) return null
        if (repeatMode == RepeatModeState.ONE) return currentIndex.coerceIn(0, queue.lastIndex)
        if (shuffleEnabled && queue.size > 1) {
            val candidates = queue.indices.filter { it != currentIndex }
            return candidates.random()
        }
        return when {
            currentIndex - 1 >= 0 -> currentIndex - 1
            repeatMode == RepeatModeState.ALL -> queue.lastIndex
            else -> 0
        }
    }

    @OptIn(UnstableApi::class)
    private fun attachEffectsToActivePlayer() {
        val sessionId = activePlayer?.audioSessionId ?: return
        if (sessionId != C.AUDIO_SESSION_ID_UNSET && sessionId > 0) {
            val telemetry = audioEffects.attachToSession(sessionId)
            syncProcessorsWithEqualizer()
            PhotonPlaybackBridge.updateState { it.copy(eqTelemetry = telemetry) }
        }
    }

    private fun publishQueueState() {
        val track = queue.getOrNull(currentIndex)
        val active = activePlayer
        PhotonPlaybackBridge.updateState {
            it.copy(
                queue = queue.toList(),
                currentIndex = currentIndex,
                currentTrack = track,
                isPlaying = active?.isPlaying == true,
                isCrossfading = isCrossfading,
                durationMs = active?.duration?.takeIf { d -> d > 0 } ?: track?.durationMs ?: 0L,
                positionMs = active?.currentPosition?.coerceAtLeast(0L) ?: 0L
            )
        }
        PhotonWidgetUpdater.requestUpdate(this)
    }

    private fun TrackEntity.toMediaItem(): MediaItem {
        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setGenre(genre)
        artworkUri?.takeIf { it.isNotBlank() }?.let {
            metadataBuilder.setArtworkUri(Uri.parse(it))
        }
        return MediaItem.Builder()
            .setMediaId(id.toString())
            .setUri(Uri.parse(uri))
            .setMediaMetadata(metadataBuilder.build())
            .build()
    }

    private fun isHardwareDecoder(decoderName: String): Boolean {
        val lower = decoderName.lowercase()
        if (lower.startsWith("c2.android.") ||
            lower.startsWith("omx.google.") ||
            lower.contains("ffmpeg") ||
            lower.contains("libflac") ||
            lower.contains("libopus") ||
            lower.contains("libvorbis") ||
            lower.contains(".sw.") ||
            lower.contains("software")
        ) {
            return false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val matched = runCatching {
                MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos
                    .firstOrNull { it.name.equals(decoderName, ignoreCase = true) }
            }.getOrNull()
            if (matched != null) {
                return matched.isHardwareAccelerated
            }
        }
        return lower.startsWith("c2.qti.") ||
            lower.startsWith("c2.exynos.") ||
            lower.startsWith("c2.mtk.") ||
            lower.startsWith("c2.sec.") ||
            lower.startsWith("omx.qcom.") ||
            lower.startsWith("omx.exynos.") ||
            lower.startsWith("omx.mtk.")
    }

    private fun classifyCodec(mimeType: String, trackFormat: String?): Pair<String, CodecQualityTier> {
        val lowerMime = mimeType.lowercase()
        val upperFmt = trackFormat?.uppercase().orEmpty()
        return when {
            lowerMime.contains("flac") || upperFmt == "FLAC" ->
                "FLAC (Free Lossless Audio)" to CodecQualityTier.LOSSLESS
            lowerMime == "audio/raw" || lowerMime.contains("wav") || upperFmt == "WAV" ->
                "WAV (Linear PCM)" to CodecQualityTier.UNCOMPRESSED
            lowerMime.contains("opus") || upperFmt == "OPUS" ->
                "Opus (Modern Low-Latency)" to CodecQualityTier.LOSSY
            lowerMime.contains("mp4a") || lowerMime.contains("aac") || upperFmt == "AAC" || upperFmt == "M4A" ->
                "AAC / M4A (MPEG-4 Audio)" to CodecQualityTier.LOSSY
            lowerMime.contains("vorbis") || lowerMime.contains("ogg") || upperFmt == "OGG" ->
                "Ogg Vorbis" to CodecQualityTier.LOSSY
            lowerMime.contains("mpeg") || lowerMime.contains("mp3") || upperFmt == "MP3" ->
                "MP3 (MPEG-1 Layer III)" to CodecQualityTier.LOSSY
            lowerMime.contains("alac") ->
                "ALAC (Apple Lossless)" to CodecQualityTier.LOSSLESS
            else ->
                (upperFmt.ifBlank { "Audio Stream" }) to CodecQualityTier.LOSSY
        }
    }

    private fun resolveSourceBitDepth(pcmEncoding: Int, trackFormat: String?): Int {
        return when (pcmEncoding) {
            C.ENCODING_PCM_8BIT -> 8
            C.ENCODING_PCM_16BIT -> 16
            C.ENCODING_PCM_24BIT, C.ENCODING_PCM_24BIT_BIG_ENDIAN -> 24
            C.ENCODING_PCM_32BIT, C.ENCODING_PCM_32BIT_BIG_ENDIAN, C.ENCODING_PCM_FLOAT -> 32
            else -> if (trackFormat.equals("FLAC", ignoreCase = true) || trackFormat.equals("WAV", ignoreCase = true)) 16 else 16
        }
    }

    private fun formatCountdown(ms: Long): String {
        val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
        val mins = totalSeconds / 60
        val secs = totalSeconds % 60
        return "%02d:%02d".format(mins, secs)
    }
}
