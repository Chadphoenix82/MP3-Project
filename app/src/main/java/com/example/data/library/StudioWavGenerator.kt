package com.example.data.library

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

/**
 * Generates genuine, studio-grade 44.1kHz 16-bit stereo PCM WAV audio files into the
 * app's monitored music folder so users can immediately hear real audio, test the
 * real hardware Graphic Equalizer, Crossfade engine, Sleep Timer, Folder Monitor,
 * and Web Metadata Auto-Complete without needing pre-loaded files on an emulator.
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
            chordFreqsHz = listOf(146.83, 220.0, 293.66, 349.23), // Dm7 cosmic pad
            arpFreqsHz = listOf(293.66, 349.23, 440.0, 523.25, 440.0, 349.23)
        ),
        SynthTrackSpec(
            fileName = "M83 - Midnight City.wav",
            chordFreqsHz = listOf(130.81, 196.0, 246.94, 329.63), // Cmaj7 astral shimmer
            arpFreqsHz = listOf(329.63, 392.0, 493.88, 659.25, 493.88, 392.0)
        ),
        SynthTrackSpec(
            fileName = "Tycho - Awake.wav",
            chordFreqsHz = listOf(110.0, 164.81, 220.0, 261.63), // Am7 nebula drift
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

    private fun writeSynthesizedWav(file: File, spec: SynthTrackSpec) {
        val sampleRate = 44100
        val channels = 2
        val bitsPerSample = 16
        val totalSamples = sampleRate * spec.durationSec
        val dataSize = totalSamples * channels * (bitsPerSample / 8)
        val tmpFile = File(file.parentFile, "${file.name}.tmp")

        FileOutputStream(tmpFile).use { fos ->
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
                put("RIFF".toByteArray(Charsets.US_ASCII))
                putInt(36 + dataSize)
                put("WAVE".toByteArray(Charsets.US_ASCII))
                put("fmt ".toByteArray(Charsets.US_ASCII))
                putInt(16) // PCM chunk size
                putShort(1) // PCM format
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
            val arpNoteDurationSec = 0.375

            while (frameIndex < totalSamples) {
                val count = minOf(chunkFrames, totalSamples - frameIndex)
                var bytePos = 0
                for (i in 0 until count) {
                    val sampleIdx = frameIndex + i
                    val t = sampleIdx.toDouble() / sampleRate.toDouble()

                    // Smooth fade in & fade out envelope
                    val env = when {
                        t < 1.2 -> t / 1.2
                        t > spec.durationSec - 1.5 -> ((spec.durationSec - t) / 1.5).coerceAtLeast(0.0)
                        else -> 1.0
                    }

                    // Rich harmonic sub-bass + warm stereo pad
                    var left = 0.0
                    var right = 0.0
                    val lfo = 0.72 + 0.28 * sin(2.0 * PI * 0.22 * t)
                    spec.chordFreqsHz.forEachIndexed { idx, freq ->
                        val detune = 1.0 + (idx - 1.5) * 0.0022
                        left += sin(2.0 * PI * freq * t) * 0.14
                        right += sin(2.0 * PI * freq * detune * t) * 0.14
                        // Subtle 2nd harmonic shimmer for EQ treble responsiveness
                        left += sin(2.0 * PI * freq * 2.0 * t) * 0.035 * lfo
                        right += sin(2.0 * PI * freq * 2.0 * detune * t) * 0.035 * lfo
                    }

                    // Rhythmic crystalline photon arpeggio
                    val arpPos = t / arpNoteDurationSec
                    val arpIdx = arpPos.toInt() % spec.arpFreqsHz.size
                    val arpPhase = arpPos - arpPos.toInt()
                    val arpEnv = (1.0 - arpPhase) * (1.0 - arpPhase)
                    val arpFreq = spec.arpFreqsHz[arpIdx]
                    val arpSignal = (sin(2.0 * PI * arpFreq * t) + 0.35 * sin(2.0 * PI * arpFreq * 2.0 * t)) * 0.14 * arpEnv

                    // Gentle kick pulse every 0.75s so Bass Boost & 60Hz EQ fader are clearly audible
                    val beatPhase = (t % 0.75) / 0.75
                    val kick = if (beatPhase < 0.22) {
                        val kEnv = (1.0 - beatPhase / 0.22)
                        val kFreq = 48.0 + 75.0 * kEnv * kEnv
                        sin(2.0 * PI * kFreq * (beatPhase * 0.75)) * 0.22 * kEnv
                    } else 0.0

                    val pan = 0.5 + 0.25 * sin(2.0 * PI * 0.4 * t)
                    val finalL = ((left + arpSignal * (1.0 - pan) + kick) * env).coerceIn(-0.95, 0.95)
                    val finalR = ((right + arpSignal * pan + kick) * env).coerceIn(-0.95, 0.95)

                    val sL = (finalL * 32767.0).toInt().toShort()
                    val sR = (finalR * 32767.0).toInt().toShort()

                    pcmBuffer[bytePos++] = (sL.toInt() and 0xFF).toByte()
                    pcmBuffer[bytePos++] = ((sL.toInt() shr 8) and 0xFF).toByte()
                    pcmBuffer[bytePos++] = (sR.toInt() and 0xFF).toByte()
                    pcmBuffer[bytePos++] = ((sR.toInt() shr 8) and 0xFF).toByte()
                }
                fos.write(pcmBuffer, 0, count * 4)
                frameIndex += count
            }
        }

        // Atomic rename so FileObserver CLOSE_WRITE / MOVED_TO catches complete file cleanly
        if (file.exists()) file.delete()
        tmpFile.renameTo(file)
    }
}
