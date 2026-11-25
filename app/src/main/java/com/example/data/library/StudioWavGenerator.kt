package com.example.data.library

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin
import kotlin.random.Random

enum class StudioWavSampleType(
    val displayName: String,
    val subtitle: String,
    val fileName: String,
    val durationSec: Int
) {
    SINE_SWEEP(
        displayName = "Log Sine Sweep",
        subtitle = "20Hz → 18kHz acoustic response check",
        fileName = "Photon Lab - Log Sine Sweep 20Hz-18kHz.wav",
        durationSec = 18
    ),
    PINK_NOISE(
        displayName = "Studio Pink Noise",
        subtitle = "-3 dB/oct reference room & EQ noise",
        fileName = "Photon Lab - Studio Pink Noise Reference.wav",
        durationSec = 20
    ),
    CALIBRATION_1KHZ(
        displayName = "1kHz / 440Hz Test Tones",
        subtitle = "Alternating -14 dBFS reference calibration tones",
        fileName = "Photon Lab - Calibration Tone 1kHz & 440Hz.wav",
        durationSec = 16
    ),
    SYNTH_PAD_STEM(
        displayName = "Cosmic Synth Stem",
        subtitle = "Analog stereo shimmer pad + arpeggio",
        fileName = "Photon Lab - Cosmic Synth Stem.wav",
        durationSec = 24
    ),
    DRUM_BASS_STEM(
        displayName = "Sub-Bass & Drum Stem",
        subtitle = "Punchy 55Hz kick + crisp hi-hat groove",
        fileName = "Photon Lab - Sub-Bass & Drum Stem.wav",
        durationSec = 20
    )
}

/**
 * Generates genuine, studio-grade 44.1kHz 16-bit stereo PCM WAV audio files into the
 * app's monitored music folder so users can immediately hear real audio, calibrate EQ/ReplayGain,
 * test crossfades, and import studio test stems directly into the library.
 */
class StudioWavGenerator(private val context: Context) {

    data class SynthTrackSpec(
        val fileName: String,
        val chordFreqsHz: List<Double>,
        val arpFreqsHz: List<Double>,
        val durationSec: Int = 24
    )

    private val defaultSpecs = listOf(
        SynthTrackSpec(
            fileName = "Daft Punk - Voyager.wav",
            chordFreqsHz = listOf(146.83, 220.0, 293.66, 349.23),
            arpFreqsHz = listOf(293.66, 349.23, 440.0, 523.25, 440.0, 349.23)
        ),
        SynthTrackSpec(
            fileName = "M83 - Midnight City.wav",
            chordFreqsHz = listOf(130.81, 196.0, 246.94, 329.63),
            arpFreqsHz = listOf(329.63, 392.0, 493.88, 659.25, 493.88, 392.0)
        ),
        SynthTrackSpec(
            fileName = "Tycho - Awake.wav",
            chordFreqsHz = listOf(110.0, 164.81, 220.0, 261.63),
            arpFreqsHz = listOf(220.0, 261.63, 329.63, 392.0, 329.63, 261.63)
        )
    )

    private val dropTestSpecs = listOf(
        SynthTrackSpec(
            fileName = "Porter Robinson - Shelter.wav",
            chordFreqsHz = listOf(174.61, 220.0, 261.63, 329.63),
            arpFreqsHz = listOf(349.23, 440.0, 523.25, 659.25, 523.25, 440.0)
        ),
        SynthTrackSpec(
            fileName = "Hans Zimmer - Interstellar Cornfield Chase.wav",
            chordFreqsHz = listOf(110.0, 164.81, 220.0, 329.63),
            arpFreqsHz = listOf(440.0, 493.88, 523.25, 659.25, 523.25, 493.88)
        ),
        SynthTrackSpec(
            fileName = "Kavinsky - Nightcall.wav",
            chordFreqsHz = listOf(123.47, 185.0, 246.94, 293.66),
            arpFreqsHz = listOf(246.94, 293.66, 369.99, 440.0, 369.99, 293.66)
        )
    )

    fun getWatchedAppMusicDir(): File {
        return File(context.filesDir, "PhotonWatchedMusic").apply { mkdirs() }
    }

    suspend fun generateStarterPack(): List<File> = withContext(Dispatchers.IO) {
        val dir = getWatchedAppMusicDir()
        defaultSpecs.map { spec ->
            val outFile = File(dir, spec.fileName)
            if (!outFile.exists()) {
                writeSynthesizedWav(outFile, spec)
            }
            outFile
        }
    }

    suspend fun dropNextTestWavIntoWatchedFolder(): File = withContext(Dispatchers.IO) {
        val dir = getWatchedAppMusicDir()
        val chosen = dropTestSpecs.firstOrNull { !File(dir, it.fileName).exists() }
            ?: SynthTrackSpec(
                fileName = "Odesza - A Moment Apart (${System.currentTimeMillis() % 1000}).wav",
                chordFreqsHz = listOf(164.81, 246.94, 329.63, 392.0),
                arpFreqsHz = listOf(329.63, 392.0, 493.88, 587.33)
            )
        val outFile = File(dir, chosen.fileName)
        writeSynthesizedWav(outFile, chosen)
        outFile
    }

    suspend fun generateStudioSample(type: StudioWavSampleType): File = withContext(Dispatchers.IO) {
        val dir = getWatchedAppMusicDir()
        val outFile = File(dir, type.fileName)
        when (type) {
            StudioWavSampleType.SYNTH_PAD_STEM -> writeSynthesizedWav(
                outFile,
                SynthTrackSpec(
                    fileName = type.fileName,
                    chordFreqsHz = listOf(130.81, 196.0, 246.94, 293.66),
                    arpFreqsHz = listOf(261.63, 329.63, 392.0, 493.88, 587.33, 493.88),
                    durationSec = type.durationSec
                )
            )
            StudioWavSampleType.SINE_SWEEP -> writeSineSweepWav(outFile, type.durationSec)
            StudioWavSampleType.PINK_NOISE -> writePinkNoiseWav(outFile, type.durationSec)
            StudioWavSampleType.CALIBRATION_1KHZ -> writeCalibrationToneWav(outFile, type.durationSec)
            StudioWavSampleType.DRUM_BASS_STEM -> writeDrumBassStemWav(outFile, type.durationSec)
        }
        outFile
    }

    private fun writeWavContainer(
        file: File,
        durationSec: Int,
        sampleProducer: (sampleIdx: Int, sampleRate: Int, durationSec: Int) -> Pair<Double, Double>
    ) {
        val sampleRate = 44100
        val channels = 2
        val bitsPerSample = 16
        val totalSamples = sampleRate * durationSec
        val dataSize = totalSamples * channels * (bitsPerSample / 8)
        val tmpFile = File(file.parentFile, "${file.name}.tmp")

        FileOutputStream(tmpFile).use { fos ->
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
                put("RIFF".toByteArray(Charsets.US_ASCII))
                putInt(36 + dataSize)
                put("WAVE".toByteArray(Charsets.US_ASCII))
                put("fmt ".toByteArray(Charsets.US_ASCII))
                putInt(16)
                putShort(1)
                putShort(channels.toShort())
                putInt(sampleRate)
                putInt(sampleRate * channels * (bitsPerSample / 8))
                putShort((channels * (bitsPerSample / 8)).toShort())
                putShort(bitsPerSample.toShort())
                put("data".toByteArray(Charsets.US_ASCII))
                putInt(dataSize)
            }
            fos.write(header.array())

            val chunkFrames = 4096
            val pcmBuffer = ByteArray(chunkFrames * 4)
            var frameIndex = 0
            while (frameIndex < totalSamples) {
                val count = minOf(chunkFrames, totalSamples - frameIndex)
                var bytePos = 0
                for (i in 0 until count) {
                    val (lRaw, rRaw) = sampleProducer(frameIndex + i, sampleRate, durationSec)
                    val sL = (lRaw.coerceIn(-0.95, 0.95) * 32767.0).toInt().toShort()
                    val sR = (rRaw.coerceIn(-0.95, 0.95) * 32767.0).toInt().toShort()
                    pcmBuffer[bytePos++] = (sL.toInt() and 0xFF).toByte()
                    pcmBuffer[bytePos++] = ((sL.toInt() shr 8) and 0xFF).toByte()
                    pcmBuffer[bytePos++] = (sR.toInt() and 0xFF).toByte()
                    pcmBuffer[bytePos++] = ((sR.toInt() shr 8) and 0xFF).toByte()
                }
                fos.write(pcmBuffer, 0, count * 4)
                frameIndex += count
            }
        }
        if (file.exists()) file.delete()
        tmpFile.renameTo(file)
    }

    private fun writeSineSweepWav(file: File, durationSec: Int) {
        val f0 = 20.0
        val f1 = 18000.0
        val k = ln(f1 / f0) / durationSec.toDouble()
        writeWavContainer(file, durationSec) { idx, sr, dur ->
            val t = idx.toDouble() / sr.toDouble()
            val env = fadeEnvelope(t, dur.toDouble(), 0.25)
            val phase = 2.0 * PI * f0 * (exp(k * t) - 1.0) / k
            val sample = sin(phase) * 0.45 * env
            sample to sample
        }
    }

    private fun writePinkNoiseWav(file: File, durationSec: Int) {
        val rng = Random(42)
        // Paul Kellet's refined pink noise filter state (L/R uncorrelated stereo)
        var b0L = 0.0; var b1L = 0.0; var b2L = 0.0; var b3L = 0.0; var b4L = 0.0; var b5L = 0.0; var b6L = 0.0
        var b0R = 0.0; var b1R = 0.0; var b2R = 0.0; var b3R = 0.0; var b4R = 0.0; var b5R = 0.0; var b6R = 0.0
        writeWavContainer(file, durationSec) { idx, sr, dur ->
            val t = idx.toDouble() / sr.toDouble()
            val env = fadeEnvelope(t, dur.toDouble(), 0.4)
            val wL = rng.nextDouble(-1.0, 1.0)
            b0L = 0.99886 * b0L + wL * 0.0555179
            b1L = 0.99332 * b1L + wL * 0.0750759
            b2L = 0.96900 * b2L + wL * 0.1538520
            b3L = 0.86650 * b3L + wL * 0.3104856
            b4L = 0.55000 * b4L + wL * 0.5329522
            b5L = -0.7616 * b5L - wL * 0.0168980
            val pL = (b0L + b1L + b2L + b3L + b4L + b5L + b6L + wL * 0.5362) * 0.065 * env
            b6L = wL * 0.115926

            val wR = rng.nextDouble(-1.0, 1.0)
            b0R = 0.99886 * b0R + wR * 0.0555179
            b1R = 0.99332 * b1R + wR * 0.0750759
            b2R = 0.96900 * b2R + wR * 0.1538520
            b3R = 0.86650 * b3R + wR * 0.3104856
            b4R = 0.55000 * b4R + wR * 0.5329522
            b5R = -0.7616 * b5R - wR * 0.0168980
            val pR = (b0R + b1R + b2R + b3R + b4R + b5R + b6R + wR * 0.5362) * 0.065 * env
            b6R = wR * 0.115926
            pL to pR
        }
    }

    private fun writeCalibrationToneWav(file: File, durationSec: Int) {
        writeWavContainer(file, durationSec) { idx, sr, dur ->
            val t = idx.toDouble() / sr.toDouble()
            val env = fadeEnvelope(t, dur.toDouble(), 0.2)
            val freq = if ((t.toInt() / 4) % 2 == 0) 1000.0 else 440.0
            val amp = 0.20 // ~ -14 dBFS
            val s = sin(2.0 * PI * freq * t) * amp * env
            s to s
        }
    }

    private fun writeDrumBassStemWav(file: File, durationSec: Int) {
        val rng = Random(1337)
        val beatSec = 0.5 // 120 BPM
        writeWavContainer(file, durationSec) { idx, sr, dur ->
            val t = idx.toDouble() / sr.toDouble()
            val env = fadeEnvelope(t, dur.toDouble(), 0.4)
            val beatIdx = (t / beatSec).toInt()
            val beatPhase = (t % beatSec) / beatSec
            val kick = if (beatPhase < 0.32) {
                val kEnv = 1.0 - beatPhase / 0.32
                val kFreq = 45.0 + 105.0 * kEnv * kEnv
                sin(2.0 * PI * kFreq * (beatPhase * beatSec)) * 0.48 * kEnv
            } else 0.0

            val subBassFreq = when (beatIdx % 8) {
                0, 1, 2, 3 -> 55.0
                4, 5 -> 65.41
                else -> 49.0
            }
            val subBass = sin(2.0 * PI * subBassFreq * t) * 0.22

            val hatPhase = (t % (beatSec / 2.0)) / (beatSec / 2.0)
            val hat = if (hatPhase < 0.08) {
                val hEnv = 1.0 - hatPhase / 0.08
                rng.nextDouble(-1.0, 1.0) * 0.16 * hEnv * hEnv
            } else 0.0

            val snare = if (beatIdx % 2 == 1 && beatPhase < 0.18) {
                val sEnv = 1.0 - beatPhase / 0.18
                (rng.nextDouble(-1.0, 1.0) * 0.22 + sin(2.0 * PI * 190.0 * t) * 0.14) * sEnv * sEnv
            } else 0.0

            val left = (kick + subBass + snare * 0.95 + hat * 0.7) * env
            val right = (kick + subBass + snare * 1.05 + hat * 1.2) * env
            left to right
        }
    }

    private fun writeSynthesizedWav(file: File, spec: SynthTrackSpec) {
        val arpNoteDurationSec = 0.375
        writeWavContainer(file, spec.durationSec) { sampleIdx, sampleRate, durSec ->
            val t = sampleIdx.toDouble() / sampleRate.toDouble()
            val env = fadeEnvelope(t, durSec.toDouble(), 1.2)

            var left = 0.0
            var right = 0.0
            val lfo = 0.72 + 0.28 * sin(2.0 * PI * 0.22 * t)
            spec.chordFreqsHz.forEachIndexed { idx, freq ->
                val detune = 1.0 + (idx - 1.5) * 0.0022
                left += sin(2.0 * PI * freq * t) * 0.14
                right += sin(2.0 * PI * freq * detune * t) * 0.14
                left += sin(2.0 * PI * freq * 2.0 * t) * 0.035 * lfo
                right += sin(2.0 * PI * freq * 2.0 * detune * t) * 0.035 * lfo
            }

            val arpPos = t / arpNoteDurationSec
            val arpIdx = arpPos.toInt() % spec.arpFreqsHz.size
            val arpPhase = arpPos - arpPos.toInt()
            val arpEnv = (1.0 - arpPhase) * (1.0 - arpPhase)
            val arpFreq = spec.arpFreqsHz[arpIdx]
            val arpSignal = (sin(2.0 * PI * arpFreq * t) + 0.35 * sin(2.0 * PI * arpFreq * 2.0 * t)) * 0.14 * arpEnv

            val beatPhase = (t % 0.75) / 0.75
            val kick = if (beatPhase < 0.22) {
                val kEnv = (1.0 - beatPhase / 0.22)
                val kFreq = 48.0 + 75.0 * kEnv * kEnv
                sin(2.0 * PI * kFreq * (beatPhase * 0.75)) * 0.22 * kEnv
            } else 0.0

            val pan = 0.5 + 0.25 * sin(2.0 * PI * 0.4 * t)
            val finalL = (left + arpSignal * (1.0 - pan) + kick) * env
            val finalR = (right + arpSignal * pan + kick) * env
            finalL to finalR
        }
    }

    private fun fadeEnvelope(t: Double, durationSec: Double, edgeSec: Double): Double = when {
        t < edgeSec -> (t / edgeSec).coerceIn(0.0, 1.0)
        t > durationSec - edgeSec -> ((durationSec - t) / edgeSec).coerceIn(0.0, 1.0)
        else -> 1.0
    }
}
