package com.example.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class AudioSpectrumFrame(
    val bars: List<Float> = List(28) { 0f },
    val eqBandLevels: List<Float> = List(5) { 0f },
    val eqCurvePoints: List<Float> = List(28) { 0.5f },
    val bandFrequenciesHz: List<Int> = listOf(60, 230, 910, 3600, 14000),
    val bassEnergy: Float = 0f,
    val peakLevel: Float = 0f,
    val isLiveAudio: Boolean = false
)

/**
 * Zero-permission Media3 ExoPlayer AudioProcessor that taps real-time decoded PCM audio samples
 * (MP3, FLAC, WAV), runs a 512-point Hann-windowed Radix-2 FFT, and shapes the live frequency
 * spectrum using the active Equalizer band frequencies, gains, and bass boost curve.
 */
@OptIn(UnstableApi::class)
class PhotonSpectrumAudioProcessor(
    private val isActivePlayer: () -> Boolean,
    private val onSpectrumFrame: (AudioSpectrumFrame) -> Unit,
    private val onPcmFormatConfigured: ((bitDepthBits: Int, sampleRateHz: Int, channelCount: Int) -> Unit)? = null
) : BaseAudioProcessor() {

    companion object {
        private const val FFT_SIZE = 512
        private const val BAR_COUNT = 28
        private const val MIN_EMIT_INTERVAL_MS = 33L
    }

    @Volatile private var eqEnabled: Boolean = true
    @Volatile private var eqRatios: FloatArray = floatArrayOf(0.55f, 0.22f, -0.05f, 0.38f, 0.62f)
    @Volatile private var eqFreqsHz: IntArray = intArrayOf(60, 230, 910, 3600, 14000)
    @Volatile private var bassBoostStrength: Int = 380

    private val ringBuffer = FloatArray(FFT_SIZE)
    private var ringWritePos = 0
    private var ringFilled = 0
    private var lastEmitMs = 0L

    private val hannWindow = FloatArray(FFT_SIZE) { i ->
        (0.5 * (1.0 - cos(2.0 * PI * i / (FFT_SIZE - 1)))).toFloat()
    }
    private val fftReal = FloatArray(FFT_SIZE)
    private val fftImag = FloatArray(FFT_SIZE)
    private val smoothedBars = FloatArray(BAR_COUNT)
    private val smoothedEqBands = FloatArray(5)

    // Precomputed logarithmic frequency edges for 28 studio spectrum bars (40Hz -> 16kHz)
    private val barEdgeFreqsHz = FloatArray(BAR_COUNT + 1) { i ->
        val minF = 40.0
        val maxF = 16000.0
        (minF * (maxF / minF).pow(i.toDouble() / BAR_COUNT.toDouble())).toFloat()
    }

    fun updateEqualizerProfile(
        enabled: Boolean,
        ratios: List<Float>,
        frequenciesHz: List<Int>,
        bassStrength: Int
    ) {
        eqEnabled = enabled
        if (ratios.size == 5) {
            eqRatios = FloatArray(5) { idx -> ratios[idx].coerceIn(-1f, 1f) }
        }
        if (frequenciesHz.size == 5) {
            eqFreqsHz = IntArray(5) { idx -> frequenciesHz[idx].coerceAtLeast(30) }
        }
        bassBoostStrength = bassStrength.coerceIn(0, 1000)
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val supported = inputAudioFormat.encoding == C.ENCODING_PCM_16BIT ||
            inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        if (!supported || inputAudioFormat.sampleRate <= 0 || inputAudioFormat.channelCount <= 0) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        val bitDepth = when (inputAudioFormat.encoding) {
            C.ENCODING_PCM_FLOAT -> 32
            C.ENCODING_PCM_24BIT -> 24
            C.ENCODING_PCM_32BIT -> 32
            C.ENCODING_PCM_8BIT -> 8
            else -> 16
        }
        if (isActivePlayer()) {
            onPcmFormatConfigured?.invoke(bitDepth, inputAudioFormat.sampleRate, inputAudioFormat.channelCount)
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining <= 0) return

        // Inspect PCM samples without consuming or altering the audio stream
        if (isActivePlayer()) {
            val dup = inputBuffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
            ingestPcm(dup, inputAudioFormat)
            val now = System.currentTimeMillis()
            if (ringFilled >= FFT_SIZE && now - lastEmitMs >= MIN_EMIT_INTERVAL_MS) {
                lastEmitMs = now
                computeAndEmitSpectrum(inputAudioFormat.sampleRate)
            }
        }

        // Exact zero-latency pass-through to output buffer
        val output = replaceOutputBuffer(remaining)
        output.put(inputBuffer)
        output.flip()
    }

    override fun onFlush() {
        ringWritePos = 0
        ringFilled = 0
    }

    override fun onReset() {
        ringWritePos = 0
        ringFilled = 0
        smoothedBars.fill(0f)
        smoothedEqBands.fill(0f)
    }

    private fun ingestPcm(buf: ByteBuffer, format: AudioProcessor.AudioFormat) {
        val channels = format.channelCount.coerceAtLeast(1)
        when (format.encoding) {
            C.ENCODING_PCM_16BIT -> {
                val frameBytes = channels * 2
                val totalFrames = buf.remaining() / frameBytes
                // Downsample stride when buffers are very large so we always capture the freshest tail
                val skipFrames = (totalFrames - FFT_SIZE).coerceAtLeast(0)
                if (skipFrames > 0) {
                    buf.position(buf.position() + skipFrames * frameBytes)
                }
                val framesToRead = buf.remaining() / frameBytes
                for (f in 0 until framesToRead) {
                    var mono = 0f
                    for (c in 0 until channels) {
                        mono += buf.short / 32768f
                    }
                    pushSample(mono / channels)
                }
            }
            C.ENCODING_PCM_FLOAT -> {
                val frameBytes = channels * 4
                val totalFrames = buf.remaining() / frameBytes
                val skipFrames = (totalFrames - FFT_SIZE).coerceAtLeast(0)
                if (skipFrames > 0) {
                    buf.position(buf.position() + skipFrames * frameBytes)
                }
                val framesToRead = buf.remaining() / frameBytes
                for (f in 0 until framesToRead) {
                    var mono = 0f
                    for (c in 0 until channels) {
                        mono += buf.float
                    }
                    pushSample((mono / channels).coerceIn(-1f, 1f))
                }
            }
        }
    }

    private fun pushSample(sample: Float) {
        ringBuffer[ringWritePos] = sample
        ringWritePos = (ringWritePos + 1) % FFT_SIZE
        if (ringFilled < FFT_SIZE) ringFilled++
    }

    private fun computeAndEmitSpectrum(sampleRateHz: Int) {
        // Copy chronologically ordered Hann-windowed samples into FFT buffers
        for (i in 0 until FFT_SIZE) {
            val idx = (ringWritePos + i) % FFT_SIZE
            fftReal[i] = ringBuffer[idx] * hannWindow[i]
            fftImag[i] = 0f
        }

        fftInPlace(fftReal, fftImag)

        val nyquistBins = FFT_SIZE / 2
        val binHz = sampleRateHz.toFloat() / FFT_SIZE.toFloat()
        val magnitudes = FloatArray(nyquistBins)
        for (k in 0 until nyquistBins) {
            val re = fftReal[k]
            val im = fftImag[k]
            // Normalize magnitude & apply gentle perceptual log compression
            val mag = sqrt(re * re + im * im) / (FFT_SIZE / 4f)
            val dbNorm = (log10(1f + mag * 18f) / log10(19f)).coerceIn(0f, 1f)
            magnitudes[k] = dbNorm
        }

        val currentEqRatios = eqRatios
        val currentEqFreqs = eqFreqsHz
        val activeEq = eqEnabled
        val bassBoostNorm = if (activeEq) bassBoostStrength / 1000f else 0f

        // Compute 28 logarithmic bars shaped by the Equalizer frequency response curve
        val curvePoints = FloatArray(BAR_COUNT)
        var maxPeak = 0f

        for (b in 0 until BAR_COUNT) {
            val fLow = barEdgeFreqsHz[b]
            val fHigh = barEdgeFreqsHz[b + 1]
            val fCenter = sqrt(fLow * fHigh)

            val startBin = (fLow / binHz).toInt().coerceIn(1, nyquistBins - 1)
            val endBin = (fHigh / binHz).toInt().coerceIn(startBin, nyquistBins - 1)

            var energy = 0f
            var count = 0
            for (k in startBin..endBin) {
                energy += magnitudes[k]
                count++
            }
            val rawLevel = if (count > 0) energy / count else 0f

            // Interpolate Equalizer gain at this bar's center frequency
            val eqRatio = if (activeEq) {
                interpolateEqRatioAtFreq(fCenter, currentEqFreqs, currentEqRatios)
            } else {
                0f
            }
            val subBassExtra = if (fCenter < 180f) bassBoostNorm * 0.35f * (1f - fCenter / 180f) else 0f
            val gainMultiplier = (1f + eqRatio * 0.55f + subBassExtra).coerceIn(0.32f, 1.75f)
            val shaped = (rawLevel * gainMultiplier).coerceIn(0f, 1f)

            // Smooth attack / decay
            val prev = smoothedBars[b]
            val alpha = if (shaped > prev) 0.62f else 0.24f
            val next = (prev + (shaped - prev) * alpha).coerceIn(0f, 1f)
            smoothedBars[b] = next
            if (next > maxPeak) maxPeak = next

            curvePoints[b] = ((eqRatio + 1f) * 0.5f).coerceIn(0.05f, 0.95f)
        }

        // Compute exact 5-band Equalizer live meters synced to the 5 EQ center frequencies
        for (band in 0 until 5) {
            val centerHz = currentEqFreqs[band].toFloat()
            val lowHz = if (band == 0) 25f else sqrt(currentEqFreqs[band - 1].toFloat() * centerHz)
            val highHz = if (band == 4) (sampleRateHz / 2f).coerceAtMost(18000f) else sqrt(centerHz * currentEqFreqs[band + 1].toFloat())

            val startBin = (lowHz / binHz).toInt().coerceIn(1, nyquistBins - 1)
            val endBin = (highHz / binHz).toInt().coerceIn(startBin, nyquistBins - 1)
            var sum = 0f
            var cnt = 0
            for (k in startBin..endBin) {
                sum += magnitudes[k]
                cnt++
            }
            val rawBand = if (cnt > 0) sum / cnt else 0f
            val ratio = if (activeEq) currentEqRatios[band] else 0f
            val bassBonus = if (band == 0) bassBoostNorm * 0.30f else 0f
            val shapedBand = (rawBand * (1f + ratio * 0.55f + bassBonus).coerceIn(0.35f, 1.75f)).coerceIn(0f, 1f)

            val prev = smoothedEqBands[band]
            val alpha = if (shapedBand > prev) 0.60f else 0.22f
            smoothedEqBands[band] = (prev + (shapedBand - prev) * alpha).coerceIn(0f, 1f)
        }

        val bassAvg = ((smoothedBars[0] + smoothedBars[1] + smoothedBars[2] + smoothedBars[3]) / 4f).coerceIn(0f, 1f)

        onSpectrumFrame(
            AudioSpectrumFrame(
                bars = smoothedBars.toList(),
                eqBandLevels = smoothedEqBands.toList(),
                eqCurvePoints = curvePoints.toList(),
                bandFrequenciesHz = currentEqFreqs.toList(),
                bassEnergy = bassAvg,
                peakLevel = maxPeak,
                isLiveAudio = true
            )
        )
    }

    private fun interpolateEqRatioAtFreq(freqHz: Float, freqs: IntArray, ratios: FloatArray): Float {
        if (freqHz <= freqs[0]) return ratios[0]
        if (freqHz >= freqs[4]) return ratios[4]
        val logF = ln(freqHz.toDouble())
        for (i in 0 until 4) {
            if (freqHz <= freqs[i + 1]) {
                val logLow = ln(freqs[i].toDouble())
                val logHigh = ln(freqs[i + 1].toDouble())
                val t = ((logF - logLow) / (logHigh - logLow).coerceAtLeast(0.001)).toFloat().coerceIn(0f, 1f)
                return ratios[i] + (ratios[i + 1] - ratios[i]) * t
            }
        }
        return ratios[4]
    }

    /**
     * Standard in-place iterative Radix-2 Cooley-Tukey FFT.
     */
    private fun fftInPlace(real: FloatArray, imag: FloatArray) {
        val n = real.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tr = real[i]; real[i] = real[j]; real[j] = tr
                val ti = imag[i]; imag[i] = imag[j]; imag[j] = ti
            }
        }

        var len = 2
        while (len <= n) {
            val angle = -2.0 * PI / len
            val wLenRe = cos(angle).toFloat()
            val wLenIm = sin(angle).toFloat()
            val half = len shr 1
            var i = 0
            while (i < n) {
                var wRe = 1f
                var wIm = 0f
                for (k in 0 until half) {
                    val uRe = real[i + k]
                    val uIm = imag[i + k]
                    val vRe = real[i + k + half] * wRe - imag[i + k + half] * wIm
                    val vIm = real[i + k + half] * wIm + imag[i + k + half] * wRe
                    real[i + k] = uRe + vRe
                    imag[i + k] = uIm + vIm
                    real[i + k + half] = uRe - vRe
                    imag[i + k + half] = uIm - vIm
                    val nextWRe = wRe * wLenRe - wIm * wLenIm
                    wIm = wRe * wLenIm + wIm * wLenRe
                    wRe = nextWRe
                }
                i += len
            }
            len = len shl 1
        }
    }
}
