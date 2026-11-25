package com.example.player

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import kotlin.math.roundToInt

data class EqTelemetry(
    val attachedSessionId: Int = 0,
    val hardwareActive: Boolean = false,
    val bandFrequenciesHz: List<Int> = listOf(60, 230, 910, 3600, 14000),
    val minDb: Int = -12,
    val maxDb: Int = 12
)

/**
 * Wraps real Android AudioEffect DSP (Equalizer, BassBoost, Virtualizer) bound to
 * ExoPlayer's live audioSessionId, safely handling re-attachment during dual-player crossfades.
 */
class PhotonAudioEffects {
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null

    private var currentSessionId: Int = 0
    private var enabled: Boolean = true
    private var currentRatios: List<Float> = listOf(0.55f, 0.22f, -0.05f, 0.38f, 0.62f)
    private var currentBassStrength: Int = 380
    private var currentVirtualizerStrength: Int = 320

    var telemetry: EqTelemetry = EqTelemetry()
        private set

    fun attachToSession(audioSessionId: Int): EqTelemetry {
        if (audioSessionId <= 0 || audioSessionId == currentSessionId) {
            applyCurrentSettings()
            return telemetry
        }

        release()
        currentSessionId = audioSessionId

        var hwActive = false
        val detectedFreqs = mutableListOf<Int>()
        var minDb = -12
        var maxDb = 12

        runCatching {
            val eq = Equalizer(0, audioSessionId)
            equalizer = eq
            val bands = eq.numberOfBands.toInt().coerceAtLeast(1)
            val range = eq.bandLevelRange
            if (range != null && range.size >= 2) {
                minDb = (range[0] / 100).coerceIn(-15, -6)
                maxDb = (range[1] / 100).coerceIn(6, 15)
            }
            for (b in 0 until bands) {
                detectedFreqs.add((eq.getCenterFreq(b.toShort()) / 1000).coerceAtLeast(30))
            }
            hwActive = true
        }

        runCatching {
            bassBoost = BassBoost(0, audioSessionId)
        }

        runCatching {
            virtualizer = Virtualizer(0, audioSessionId)
        }

        telemetry = EqTelemetry(
            attachedSessionId = audioSessionId,
            hardwareActive = hwActive,
            bandFrequenciesHz = if (detectedFreqs.size == 5) detectedFreqs else listOf(60, 230, 910, 3600, 14000),
            minDb = minDb,
            maxDb = maxDb
        )

        applyCurrentSettings()
        return telemetry
    }

    fun updateSettings(
        isEnabled: Boolean,
        bandRatios: List<Float>,
        bassStrength: Int,
        virtualizerStrength: Int
    ) {
        enabled = isEnabled
        if (bandRatios.size == 5) {
            currentRatios = bandRatios
        }
        currentBassStrength = bassStrength.coerceIn(0, 1000)
        currentVirtualizerStrength = virtualizerStrength.coerceIn(0, 1000)
        applyCurrentSettings()
    }

    private fun applyCurrentSettings() {
        runCatching {
            equalizer?.let { eq ->
                eq.enabled = enabled
                val numBands = eq.numberOfBands.toInt()
                val range = eq.bandLevelRange
                val minLevel = range?.getOrNull(0)?.toInt() ?: -1200
                val maxLevel = range?.getOrNull(1)?.toInt() ?: 1200

                for (band in 0 until numBands) {
                    val ratioIndex = if (numBands <= 1) 0 else {
                        ((band.toFloat() / (numBands - 1)) * (currentRatios.size - 1)).roundToInt()
                            .coerceIn(0, currentRatios.lastIndex)
                    }
                    val ratio = currentRatios[ratioIndex].coerceIn(-1f, 1f)
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
    }

    fun release() {
        runCatching { equalizer?.release() }
        runCatching { bassBoost?.release() }
        runCatching { virtualizer?.release() }
        equalizer = null
        bassBoost = null
        virtualizer = null
        currentSessionId = 0
    }
}
