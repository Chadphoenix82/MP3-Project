package com.example.player

import android.media.audiofx.BassBoost
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.PresetReverb
import android.media.audiofx.Virtualizer
import android.os.Build
import androidx.annotation.RequiresApi
import com.example.data.model.PhotonEqPresets
import com.example.data.model.ReverbSpacePreset
import com.example.data.model.StudioDspConfig
import kotlin.math.roundToInt

data class EqTelemetry(
    val attachedSessionId: Int = 0,
    val hardwareActive: Boolean = false,
    val engineLabel: String = "10-Band Dynamics DSP",
    val bandFrequenciesHz: List<Int> = PhotonEqPresets.canonical10BandFrequenciesHz,
    val minDb: Int = -12,
    val maxDb: Int = 12
)

/**
 * Wraps real Android AudioEffect DSP bound to ExoPlayer's live audioSessionId:
 * - API 28+: 10-band DynamicsProcessing post-EQ + hardware soft limiter
 * - API 24-27 (or fallback): Android graphic Equalizer mapped cleanly from 10-band curve
 * - BassBoost, Virtualizer, PresetReverb (acoustic room spaces), and LoudnessEnhancer
 */
class PhotonAudioEffects {
    private var dynamicsProcessing: DynamicsProcessing? = null
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var presetReverb: PresetReverb? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null

    private var currentSessionId: Int = 0
    private var enabled: Boolean = true
    private var currentRatios: List<Float> = PhotonEqPresets.default10BandRatios
    private var currentBassStrength: Int = 380
    private var currentVirtualizerStrength: Int = 320
    private var currentDsp: StudioDspConfig = StudioDspConfig()

    var telemetry: EqTelemetry = EqTelemetry()
        private set

    fun attachToSession(audioSessionId: Int, forceRebind: Boolean = false): EqTelemetry {
        if (audioSessionId <= 0 || (!forceRebind && audioSessionId == currentSessionId)) {
            applyCurrentSettings()
            return telemetry
        }

        release()
        currentSessionId = audioSessionId

        var hwActive = false
        var engineLabel = "10-Band Dynamics DSP"
        var minDb = -12
        var maxDb = 12

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val attachedDynamics = attachDynamicsProcessing(audioSessionId)
            if (attachedDynamics) {
                hwActive = true
                engineLabel = "10-Band Dynamics DSP"
            }
        }

        if (!hwActive) {
            runCatching {
                val eq = Equalizer(0, audioSessionId)
                equalizer = eq
                val range = eq.bandLevelRange
                if (range != null && range.size >= 2) {
                    minDb = (range[0] / 100).coerceIn(-15, -6)
                    maxDb = (range[1] / 100).coerceIn(6, 15)
                }
                hwActive = true
                engineLabel = "${eq.numberOfBands}-Band Graphic DSP"
            }
        }

        runCatching {
            bassBoost = BassBoost(0, audioSessionId)
        }

        runCatching {
            virtualizer = Virtualizer(0, audioSessionId)
        }

        runCatching {
            presetReverb = PresetReverb(0, audioSessionId)
        }

        runCatching {
            loudnessEnhancer = LoudnessEnhancer(audioSessionId)
        }

        telemetry = EqTelemetry(
            attachedSessionId = audioSessionId,
            hardwareActive = hwActive,
            engineLabel = if (hwActive) engineLabel else "10-Band Studio PCM DSP",
            bandFrequenciesHz = PhotonEqPresets.canonical10BandFrequenciesHz,
            minDb = minDb,
            maxDb = maxDb
        )

        applyCurrentSettings()
        return telemetry
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun attachDynamicsProcessing(audioSessionId: Int): Boolean {
        return runCatching {
            val config = DynamicsProcessing.Config.Builder(
                DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                2,
                false,
                0,
                false,
                0,
                true,
                10,
                true
            ).build()
            val dp = DynamicsProcessing(0, audioSessionId, config)
            dynamicsProcessing = dp
            true
        }.getOrDefault(false)
    }

    fun updateSettings(
        isEnabled: Boolean,
        bandRatios: List<Float>,
        bassStrength: Int,
        virtualizerStrength: Int,
        dspConfig: StudioDspConfig = currentDsp
    ) {
        enabled = isEnabled
        currentRatios = PhotonEqPresets.normalizeTo10Bands(bandRatios)
        currentBassStrength = bassStrength.coerceIn(0, 1000)
        currentVirtualizerStrength = virtualizerStrength.coerceIn(0, 1000)
        currentDsp = dspConfig
        applyCurrentSettings()
    }

    private fun applyCurrentSettings() {
        val ratios = PhotonEqPresets.normalizeTo10Bands(currentRatios)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching {
                dynamicsProcessing?.let { dp ->
                    val shouldEnable = enabled || currentDsp.softLimiterEnabled
                    dp.enabled = shouldEnable
                    val postEq = DynamicsProcessing.Eq(true, enabled, 10)
                    val freqs = PhotonEqPresets.canonical10BandFrequenciesHz
                    for (band in 0 until 10) {
                        val cutoff = freqs[band].toFloat()
                        val gainDb = if (enabled) (ratios[band] * 12f).coerceIn(-12f, 12f) else 0f
                        postEq.setBand(band, DynamicsProcessing.EqBand(true, cutoff, gainDb))
                    }
                    dp.setPostEqAllChannelsTo(postEq)

                    val limiter = DynamicsProcessing.Limiter(
                        true,
                        currentDsp.softLimiterEnabled,
                        0,
                        2.5f,
                        55f,
                        4.0f,
                        -1.5f,
                        0f
                    )
                    dp.setLimiterAllChannelsTo(limiter)
                }
            }
        }

        runCatching {
            equalizer?.let { eq ->
                eq.enabled = enabled
                val numBands = eq.numberOfBands.toInt()
                val range = eq.bandLevelRange
                val minLevel = range?.getOrNull(0)?.toInt() ?: -1200
                val maxLevel = range?.getOrNull(1)?.toInt() ?: 1200

                for (band in 0 until numBands) {
                    val ratioIndex = if (numBands <= 1) 0 else {
                        ((band.toFloat() / (numBands - 1)) * (ratios.size - 1)).roundToInt()
                            .coerceIn(0, ratios.lastIndex)
                    }
                    val ratio = ratios[ratioIndex].coerceIn(-1f, 1f)
                    val targetMillibels = if (ratio >= 0f) {
                        (ratio * maxLevel).roundToInt()
                    } else {
                        (-ratio * minLevel).roundToInt()
                    }.coerceIn(minLevel, maxLevel).toShort()
                    eq.setBandLevel(band.toShort(), targetMillibels)
                }
            }
        }

        runCatching {
            bassBoost?.let { bb ->
                bb.enabled = enabled && currentBassStrength > 0
                if (bb.strengthSupported) {
                    bb.setStrength(currentBassStrength.toShort())
                }
            }
        }

        runCatching {
            virtualizer?.let { virt ->
                virt.enabled = enabled && currentVirtualizerStrength > 0
                if (virt.strengthSupported) {
                    virt.setStrength(currentVirtualizerStrength.toShort())
                }
            }
        }

        runCatching {
            presetReverb?.let { reverb ->
                val nativePreset = when (currentDsp.reverbPreset) {
                    ReverbSpacePreset.OFF -> PresetReverb.PRESET_NONE
                    ReverbSpacePreset.SMALL_ROOM -> PresetReverb.PRESET_SMALLROOM
                    ReverbSpacePreset.STUDIO -> PresetReverb.PRESET_MEDIUMROOM
                    ReverbSpacePreset.MEDIUM_HALL -> PresetReverb.PRESET_MEDIUMHALL
                    ReverbSpacePreset.LARGE_HALL -> PresetReverb.PRESET_LARGEHALL
                    ReverbSpacePreset.PLATE -> PresetReverb.PRESET_PLATE
                }
                val active = currentDsp.reverbPreset != ReverbSpacePreset.OFF
                reverb.preset = nativePreset
                reverb.enabled = active
            }
        }

        runCatching {
            loudnessEnhancer?.let { le ->
                val active = currentDsp.loudnessNormalizerEnabled
                le.enabled = active
                if (active) {
                    // Subtle 1.8 dB headroom clarity lift paired with PCM RMS normalizer
                    le.setTargetGain(180)
                }
            }
        }
    }

    fun release() {
        runCatching { dynamicsProcessing?.release() }
        runCatching { equalizer?.release() }
        runCatching { bassBoost?.release() }
        runCatching { virtualizer?.release() }
        runCatching { presetReverb?.release() }
        runCatching { loudnessEnhancer?.release() }
        dynamicsProcessing = null
        equalizer = null
        bassBoost = null
        virtualizer = null
        presetReverb = null
        loudnessEnhancer = null
        currentSessionId = 0
    }
}
