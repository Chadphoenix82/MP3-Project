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

data class EqualizerPreset(
    val name: String,
    val description: String,
    /** Normalized band gains in range [-1.0f, 1.0f] across 5 canonical frequency bands (60Hz, 230Hz, 910Hz, 3.6kHz, 14kHz). */
    val bandRatios: List<Float>,
    val bassBoost: Int = 0,
    val virtualizer: Int = 0
)

object PhotonEqPresets {
    val builtIn: List<EqualizerPreset> = listOf(
        EqualizerPreset("Flat", "Pure uncolored studio reference", listOf(0f, 0f, 0f, 0f, 0f), 0, 0),
        EqualizerPreset("Photon Boost", "Luminous highs & punchy cosmic bass", listOf(0.55f, 0.22f, -0.05f, 0.38f, 0.62f), 380, 320),
        EqualizerPreset("Galaxy Bass", "Deep sub-bass gravity & warmth", listOf(0.82f, 0.52f, 0.08f, 0.12f, 0.25f), 680, 260),
        EqualizerPreset("Cosmic Vocal", "Crystal clear voice & mid presence", listOf(-0.15f, 0.10f, 0.58f, 0.48f, 0.18f), 120, 220),
        EqualizerPreset("Studio FLAC", "Refined audiophile clarity", listOf(0.18f, 0.08f, 0.12f, 0.28f, 0.35f), 160, 150),
        EqualizerPreset("Electronic", "Hyper-charged synth energy", listOf(0.65f, 0.30f, -0.18f, 0.42f, 0.70f), 500, 420),
        EqualizerPreset("Acoustic", "Natural organic resonance", listOf(0.24f, 0.18f, 0.22f, 0.30f, 0.20f), 100, 180)
    )
}

data class SleepTimerState(
    val isActive: Boolean = false,
    val remainingMs: Long = 0L,
    val endOfTrack: Boolean = false,
    val label: String = "Off"
)
