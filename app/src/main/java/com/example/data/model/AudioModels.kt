package com.example.data.model

enum class AudioFormat(val label: String) {
    MP3("MP3"),
    FLAC("FLAC"),
    WAV("WAV"),
    OPUS("OPUS"),
    AAC("AAC"),
    OGG("OGG"),
    OTHER("AUDIO");

    companion object {
        fun fromMimeOrName(mimeType: String?, fileName: String): AudioFormat {
            val lowerName = fileName.lowercase()
            val lowerMime = mimeType?.lowercase().orEmpty()
            return when {
                lowerName.endsWith(".flac") || lowerMime.contains("flac") -> FLAC
                lowerName.endsWith(".wav") || lowerMime.contains("wav") || lowerMime.contains("wave") -> WAV
                lowerName.endsWith(".opus") || lowerMime.contains("opus") -> OPUS
                lowerName.endsWith(".m4a") || lowerName.endsWith(".aac") ||
                    lowerMime.contains("mp4a") || lowerMime.contains("aac") || lowerMime.contains("audio/mp4") -> AAC
                lowerName.endsWith(".ogg") || lowerName.endsWith(".oga") ||
                    lowerMime.contains("vorbis") || lowerMime.contains("ogg") -> OGG
                lowerName.endsWith(".mp3") || lowerMime.contains("mpeg") || lowerMime.contains("mp3") -> MP3
                else -> OTHER
            }
        }
    }
}

enum class CodecQualityTier(val label: String) {
    UNCOMPRESSED("Uncompressed"),
    LOSSLESS("Lossless"),
    LOSSY("Lossy")
}

enum class OutputTransportType(val label: String) {
    BLUETOOTH_A2DP("Bluetooth A2DP"),
    BLUETOOTH_LE("Bluetooth LE Audio"),
    USB_DAC("USB-C DAC / Audio"),
    WIRED_HEADSET("Wired 3.5mm Headphones"),
    SPEAKER("Built-in Speaker"),
    OTHER("System Audio Output")
}

data class AudioSignalInfo(
    val codecDisplayName: String = "Standby",
    val mimeType: String = "",
    val qualityTier: CodecQualityTier = CodecQualityTier.LOSSLESS,
    val sourceBitDepthBits: Int = 16,
    val sourceSampleRateHz: Int = 44100,
    val channelCount: Int = 2,
    val liveBitrateKbps: Int = 0,
    val decoderName: String = "Waiting for playback",
    val isHardwareAccelerated: Boolean = false,
    val decoderDetected: Boolean = false,
    val decodedPcmBitDepthBits: Int = 16,
    val outputSampleRateHz: Int = 44100,
    val outputDeviceName: String = "Built-in Speaker",
    val outputTransport: OutputTransportType = OutputTransportType.SPEAKER,
    val supportedOutputCodecs: List<String> = listOf("PCM 16-bit"),
    val supportedSampleRatesHz: List<Int> = listOf(44100, 48000)
) {
    val channelLayoutLabel: String
        get() = when (channelCount) {
            1 -> "Mono 1.0"
            2 -> "Stereo 2.0"
            6 -> "Surround 5.1"
            8 -> "Surround 7.1"
            else -> "${channelCount}ch"
        }

    val decoderBadgeLabel: String
        get() = when {
            !decoderDetected -> "AUTO"
            isHardwareAccelerated -> "HW DSP"
            else -> "SW CPU"
        }
}

enum class SortOption(val label: String) {
    CUSTOM_ORDER("Custom"),
    ARTIST("Artist"),
    GENRE("Genre"),
    ALBUM("Album"),
    PUBLISHER("Publisher"),
    RELEASE_YEAR("Year"),
    TITLE("Title"),
    DATE_ADDED("Added")
}

enum class SortDirection {
    ASC, DESC
}

data class MetadataSuggestion(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val genre: String,
    val publisher: String,
    val releaseDate: String,
    val releaseYear: Int?,
    val trackNumber: Int? = null,
    val artworkUrl: String?,
    val source: String,
    val confidencePercent: Int = 80
)

enum class OutputRouteCategory(val label: String) {
    BLUETOOTH("Bluetooth"),
    WIRED_USB("Wired / USB DAC"),
    SPEAKER("Built-in Speaker");

    companion object {
        fun fromTransport(transport: OutputTransportType): OutputRouteCategory = when (transport) {
            OutputTransportType.BLUETOOTH_A2DP,
            OutputTransportType.BLUETOOTH_LE -> BLUETOOTH
            OutputTransportType.WIRED_HEADSET,
            OutputTransportType.USB_DAC -> WIRED_USB
            OutputTransportType.SPEAKER,
            OutputTransportType.OTHER -> SPEAKER
        }
    }
}

enum class ReverbSpacePreset(val label: String, val subtitle: String) {
    OFF("Off", "Pure dry studio signal"),
    SMALL_ROOM("Small Room", "Intimate warm reflections"),
    STUDIO("Studio Booth", "Tight acoustic clarity"),
    MEDIUM_HALL("Concert Hall", "Balanced spacious staging"),
    LARGE_HALL("Cathedral", "Deep cosmic resonance"),
    PLATE("Cosmic Plate", "Shimmering vintage plate")
}

enum class CrossfadeCurveShape(val label: String, val subtitle: String) {
    EQUAL_POWER("Equal Power", "Smooth constant-loudness cosine"),
    LINEAR("Linear", "Even straight fade transition"),
    S_CURVE("S-Curve", "Gentle start & finish blend"),
    FAST_CUT("Fast Cut", "Quick DJ-style energy handoff");

    fun computeGains(progress: Float): Pair<Float, Float> {
        val t = progress.coerceIn(0f, 1f)
        return when (this) {
            EQUAL_POWER -> {
                val outG = kotlin.math.cos(t * kotlin.math.PI / 2.0).toFloat()
                val inG = kotlin.math.sin(t * kotlin.math.PI / 2.0).toFloat()
                outG.coerceIn(0f, 1f) to inG.coerceIn(0f, 1f)
            }
            LINEAR -> (1f - t).coerceIn(0f, 1f) to t.coerceIn(0f, 1f)
            S_CURVE -> {
                val smooth = t * t * (3f - 2f * t)
                (1f - smooth).coerceIn(0f, 1f) to smooth.coerceIn(0f, 1f)
            }
            FAST_CUT -> {
                val outG = if (t < 0.72f) 1f - (t * 0.25f) else ((1f - t) / 0.28f) * 0.82f
                val inG = if (t < 0.28f) (t / 0.28f) * 0.82f else 0.82f + ((t - 0.28f) / 0.72f) * 0.18f
                outG.coerceIn(0f, 1f) to inG.coerceIn(0f, 1f)
            }
        }
    }
}

enum class WidgetThemeMode(val label: String) {
    APP_THEME("Match App Theme"),
    ADAPTIVE_ARTWORK("Adaptive Album Art")
}

enum class ReplayGainMode(val label: String, val subtitle: String) {
    OFF("Off", "Original mastered loudness"),
    TRACK("Track", "Normalize every song to target LUFS"),
    ALBUM("Album", "Preserve relative album dynamics")
}

enum class ReplayGainSource(val code: String, val label: String) {
    NONE("", "Unscanned"),
    TAG("TAG", "RG Tag"),
    LUFS_SCAN("LUFS_SCAN", "BS.1770 LUFS");

    companion object {
        fun fromCode(code: String?): ReplayGainSource = when (code?.uppercase()) {
            "TAG" -> TAG
            "LUFS_SCAN" -> LUFS_SCAN
            else -> NONE
        }
    }
}

data class ReplayGainResult(
    val trackGainDb: Float? = null,
    val albumGainDb: Float? = null,
    val trackPeak: Float? = null,
    val albumPeak: Float? = null,
    val integratedLufs: Float? = null,
    val source: ReplayGainSource = ReplayGainSource.NONE
) {
    val hasData: Boolean
        get() = trackGainDb != null || albumGainDb != null || integratedLufs != null
}

data class ReplayGainScanProgress(
    val isScanning: Boolean = false,
    val scannedCount: Int = 0,
    val totalCount: Int = 0,
    val currentTrackTitle: String = ""
)

data class EffectiveReplayGain(
    val appliedGainDb: Float = 0f,
    val rawTargetGainDb: Float = 0f,
    val measuredLufs: Float? = null,
    val peakLinear: Float? = null,
    val clippingPrevented: Boolean = false,
    val isUsingAlbumGain: Boolean = false,
    val source: ReplayGainSource = ReplayGainSource.NONE
)

object ReplayGainCalculator {
    /** ReplayGain 2.0 standard reference loudness is -18.0 LUFS. */
    const val RG2_REFERENCE_LUFS = -18.0f

    fun computeEffectiveGain(
        mode: ReplayGainMode,
        targetLufs: Float,
        rgPreAmpDb: Float,
        fallbackPreAmpDb: Float,
        preventClipping: Boolean,
        trackGainDb: Float?,
        albumGainDb: Float?,
        trackPeak: Float?,
        albumPeak: Float?,
        integratedLufs: Float?,
        sourceCode: String?
    ): EffectiveReplayGain {
        val source = ReplayGainSource.fromCode(sourceCode)
        val derivedLufs = integratedLufs ?: trackGainDb?.let { RG2_REFERENCE_LUFS - it }
        if (mode == ReplayGainMode.OFF) {
            return EffectiveReplayGain(
                appliedGainDb = 0f,
                rawTargetGainDb = 0f,
                measuredLufs = derivedLufs,
                peakLinear = trackPeak ?: albumPeak,
                clippingPrevented = false,
                isUsingAlbumGain = false,
                source = source
            )
        }

        val useAlbum = mode == ReplayGainMode.ALBUM && albumGainDb != null
        val activePeak = if (useAlbum) (albumPeak ?: trackPeak) else (trackPeak ?: albumPeak)
        val hasMeasuredData = (useAlbum && albumGainDb != null) || trackGainDb != null || integratedLufs != null

        val rawGain = when {
            useAlbum -> albumGainDb!! + (targetLufs - RG2_REFERENCE_LUFS) + rgPreAmpDb
            trackGainDb != null -> trackGainDb + (targetLufs - RG2_REFERENCE_LUFS) + rgPreAmpDb
            integratedLufs != null -> (targetLufs - integratedLufs) + rgPreAmpDb
            else -> fallbackPreAmpDb
        }.coerceIn(-15f, 15f)

        var applied = rawGain
        var clipped = false
        if (preventClipping && activePeak != null && activePeak > 0.001f) {
            // Keep peak ceiling at 0.988 (~ -0.1 dBFS) to prevent inter-sample clipping
            val maxSafeGainDb = (20.0 * kotlin.math.log10(0.988 / activePeak.toDouble())).toFloat()
            if (applied > maxSafeGainDb) {
                applied = maxSafeGainDb.coerceIn(-15f, 15f)
                clipped = true
            }
        }

        return EffectiveReplayGain(
            appliedGainDb = applied,
            rawTargetGainDb = rawGain,
            measuredLufs = derivedLufs,
            peakLinear = activePeak,
            clippingPrevented = clipped,
            isUsingAlbumGain = useAlbum,
            source = if (hasMeasuredData) source else ReplayGainSource.NONE
        )
    }

    fun linearPeakToDbfs(peak: Float?): String {
        if (peak == null || peak <= 0.00001f) return "—"
        val dbfs = (20.0 * kotlin.math.log10(peak.toDouble())).toFloat()
        return String.format("%+.1f dBFS", dbfs)
    }
}

data class StudioDspConfig(
    val preAmpDb: Float = 0f,
    val softLimiterEnabled: Boolean = true,
    val loudnessNormalizerEnabled: Boolean = false,
    val stereoBalance: Float = 0f, // -1f (Left) .. 0f (Center) .. +1f (Right)
    val monoAudioEnabled: Boolean = false,
    val crossfeedStrength: Float = 0f, // 0f .. 1f
    val reverbPreset: ReverbSpacePreset = ReverbSpacePreset.OFF,
    val trackGainOffsetDb: Float = 0f,
    val replayGainMode: ReplayGainMode = ReplayGainMode.TRACK,
    val replayGainTargetLufs: Float = -14f,
    val replayGainPreAmpDb: Float = 0f,
    val replayGainFallbackPreAmpDb: Float = 0f,
    val preventClipping: Boolean = true,
    val activeReplayGainDb: Float = 0f
)

data class EqualizerPreset(
    val name: String,
    val description: String,
    /** Normalized band gains in range [-1.0f, 1.0f] across 10 ISO bands (31Hz -> 16kHz). */
    val bandRatios: List<Float>,
    val bassBoost: Int = 0,
    val virtualizer: Int = 0,
    val isCustom: Boolean = false
)

object PhotonEqPresets {
    val canonical10BandFrequenciesHz: List<Int> = listOf(
        31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000
    )

    val default10BandRatios: List<Float> = listOf(
        0.58f, 0.50f, 0.30f, 0.14f, -0.04f, -0.02f, 0.18f, 0.38f, 0.54f, 0.62f
    )

    fun normalizeTo10Bands(raw: List<Float>): List<Float> {
        if (raw.size == 10) return raw.map { it.coerceIn(-1f, 1f) }
        if (raw.isEmpty()) return List(10) { 0f }
        if (raw.size == 1) return List(10) { raw[0].coerceIn(-1f, 1f) }
        return List(10) { idx ->
            val pos = (idx.toFloat() / 9f) * (raw.size - 1)
            val low = pos.toInt().coerceIn(0, raw.lastIndex)
            val high = (low + 1).coerceIn(0, raw.lastIndex)
            val frac = pos - low
            (raw[low] + (raw[high] - raw[low]) * frac).coerceIn(-1f, 1f)
        }
    }

    val builtIn: List<EqualizerPreset> = listOf(
        EqualizerPreset(
            "Flat",
            "Pure uncolored studio reference",
            listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
            0,
            0
        ),
        EqualizerPreset(
            "Photon Boost",
            "Luminous highs & punchy cosmic bass",
            listOf(0.58f, 0.50f, 0.30f, 0.14f, -0.04f, -0.02f, 0.18f, 0.38f, 0.54f, 0.62f),
            380,
            320
        ),
        EqualizerPreset(
            "Galaxy Bass",
            "Deep sub-bass gravity & warmth",
            listOf(0.86f, 0.78f, 0.58f, 0.36f, 0.12f, 0.04f, 0.08f, 0.14f, 0.22f, 0.26f),
            680,
            260
        ),
        EqualizerPreset(
            "Cosmic Vocal",
            "Crystal clear voice & mid presence",
            listOf(-0.20f, -0.12f, 0.04f, 0.24f, 0.48f, 0.60f, 0.54f, 0.40f, 0.22f, 0.14f),
            120,
            220
        ),
        EqualizerPreset(
            "Studio FLAC",
            "Refined audiophile clarity",
            listOf(0.18f, 0.16f, 0.10f, 0.06f, 0.08f, 0.12f, 0.20f, 0.28f, 0.34f, 0.36f),
            160,
            150
        ),
        EqualizerPreset(
            "Electronic",
            "Hyper-charged synth energy",
            listOf(0.72f, 0.62f, 0.38f, 0.12f, -0.18f, -0.12f, 0.22f, 0.46f, 0.64f, 0.72f),
            500,
            420
        ),
        EqualizerPreset(
            "Acoustic",
            "Natural organic resonance",
            listOf(0.22f, 0.24f, 0.18f, 0.16f, 0.20f, 0.24f, 0.30f, 0.28f, 0.22f, 0.18f),
            100,
            180
        ),
        EqualizerPreset(
            "Late Night",
            "Warm low-volume detail & gentle highs",
            listOf(0.42f, 0.36f, 0.22f, 0.10f, 0.02f, 0.08f, 0.12f, -0.05f, -0.16f, -0.22f),
            240,
            180
        )
    )
}

data class SleepTimerState(
    val isActive: Boolean = false,
    val remainingMs: Long = 0L,
    val endOfTrack: Boolean = false,
    val label: String = "Off"
)
