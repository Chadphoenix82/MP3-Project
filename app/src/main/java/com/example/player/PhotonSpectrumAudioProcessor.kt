package com.example.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import com.example.data.model.PhotonEqPresets
import com.example.data.model.StudioDspConfig
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

data class AudioSpectrumFrame(
    val bars: List<Float> = List(28) { 0f },
    val eqBandLevels: List<Float> = List(10) { 0f },
    val eqCurvePoints: List<Float> = List(28) { 0.5f },
    val bandFrequenciesHz: List<Int> = PhotonEqPresets.canonical10BandFrequenciesHz,
    val bassEnergy: Float = 0f,
    val peakLevel: Float = 0f,
    val isLiveAudio: Boolean = false
)

/**
 * Zero-permission Media3 ExoPlayer AudioProcessor that performs:
 * 1) Real-time PCM Studio Mastering DSP (Pre-Amp + Per-Track Gain Trim, Adaptive RMS Loudness
 *    Normalizer, Bauer Headphone Crossfeed, Stereo L/R Balance & Mono Downmix, and tanh Soft-Clip Limiter).
 * 2) 512-point Hann-windowed Radix-2 FFT computing a 28-bar live spectrum and 10-band ISO EQ meters.
 *
 * When all PCM DSP modifiers are neutral/off, runs as an exact zero-latency pass-through.
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
        private const val EQ_BANDS = 10
        private const val MIN_EMIT_INTERVAL_MS = 33L
        private const val TARGET_RMS = 0.21f
    }

    @Volatile private var eqEnabled: Boolean = true
    @Volatile private var eqRatios: FloatArray = PhotonEqPresets.default10BandRatios.toFloatArray()
    @Volatile private var eqFreqsHz: IntArray = PhotonEqPresets.canonical10BandFrequenciesHz.toIntArray()
    @Volatile private var bassBoostStrength: Int = 380
    @Volatile private var studioDsp: StudioDspConfig = StudioDspConfig()

    // Real-time DSP filter states
    private var agcGain: Float = 1f
    private var smoothedRms: Float = 0.16f
    private var crossfeedLpLeft: Float = 0f
    private var crossfeedLpRight: Float = 0f

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
    private val smoothedEqBands = FloatArray(EQ_BANDS)

    // Precomputed logarithmic frequency edges for 28 studio spectrum bars (31Hz -> 16kHz)
    private val barEdgeFreqsHz = FloatArray(BAR_COUNT + 1) { i ->
        val minF = 31.0
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
        val norm = PhotonEqPresets.normalizeTo10Bands(ratios)
        eqRatios = FloatArray(EQ_BANDS) { idx -> norm[idx] }
        if (frequenciesHz.size == EQ_BANDS) {
            eqFreqsHz = IntArray(EQ_BANDS) { idx -> frequenciesHz[idx].coerceAtLeast(25) }
        }
        bassBoostStrength = bassStrength.coerceIn(0, 1000)
    }

    fun updateStudioDsp(config: StudioDspConfig) {
        studioDsp = config
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

        val dsp = studioDsp
        val totalGainDb = (dsp.preAmpDb + dsp.trackGainOffsetDb).coerceIn(-18f, 18f)
        val needsPcmDsp = abs(totalGainDb) > 0.05f ||
            dsp.loudnessNormalizerEnabled ||
            dsp.monoAudioEnabled ||
            abs(dsp.stereoBalance) > 0.01f ||
            dsp.crossfeedStrength > 0.01f ||
            (dsp.softLimiterEnabled && totalGainDb > 0.1f)

        val output = replaceOutputBuffer(remaining)

        if (!needsPcmDsp) {
            if (isActivePlayer()) {
                val dup = inputBuffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
                ingestPcm(dup, inputAudioFormat)
            }
            output.put(inputBuffer)
            output.flip()
        } else {
            processPcmBlock(inputBuffer, output, inputAudioFormat, dsp, totalGainDb)
            output.flip()
        }

        if (isActivePlayer()) {
            val now = System.currentTimeMillis()
            if (ringFilled >= FFT_SIZE && now - lastEmitMs >= MIN_EMIT_INTERVAL_MS) {
                lastEmitMs = now
                computeAndEmitSpectrum(inputAudioFormat.sampleRate)
            }
        }
    }

    private fun processPcmBlock(
        input: ByteBuffer,
        output: ByteBuffer,
        format: AudioProcessor.AudioFormat,
        dsp: StudioDspConfig,
        totalGainDb: Float
    ) {
        val channels = format.channelCount.coerceAtLeast(1)
        val sampleRate = format.sampleRate.coerceAtLeast(8000).toFloat()
        val baseGain = 10.0.pow(totalGainDb / 20.0).toFloat()

        // Stereo L/R constant-power balance attenuation
        val bal = dsp.stereoBalance.coerceIn(-1f, 1f)
        val leftBalGain = if (bal > 0f) (1f - bal).coerceIn(0f, 1f) else 1f
        val rightBalGain = if (bal < 0f) (1f + bal).coerceIn(0f, 1f) else 1f

        // Bauer crossfeed 1-pole low-pass coefficient (~700Hz acoustic head-shadow filter)
        val cf = dsp.crossfeedStrength.coerceIn(0f, 1f)
        val rc = 1f / (2f * PI.toFloat() * 700f)
        val dt = 1f / sampleRate
        val lpAlpha = (dt / (rc + dt)).coerceIn(0.01f, 0.5f)
        val directMix = 1f - cf * 0.24f
        val crossMix = cf * 0.30f

        val captureSpectrum = isActivePlayer()
        val is16Bit = format.encoding == C.ENCODING_PCM_16BIT
        val bytesPerSample = if (is16Bit) 2 else 4
        val frameBytes = channels * bytesPerSample
        val totalFrames = input.remaining() / frameBytes
        val spectrumStartFrame = (totalFrames - FFT_SIZE).coerceAtLeast(0)

        var sumSquares = 0.0
        var sampleCount = 0

        val currentAgc = if (dsp.loudnessNormalizerEnabled) agcGain else 1f
        val combinedGain = baseGain * currentAgc

        for (f in 0 until totalFrames) {
            if (channels == 2) {
                var l = readSample(input, is16Bit)
                var r = readSample(input, is16Bit)

                if (dsp.loudnessNormalizerEnabled) {
                    val m = (l + r) * 0.5f
                    sumSquares += (m * m).toDouble()
                    sampleCount++
                }

                l *= combinedGain
                r *= combinedGain

                if (dsp.monoAudioEnabled) {
                    val mono = (l + r) * 0.5f
                    l = mono
                    r = mono
                } else {
                    if (cf > 0.01f) {
                        crossfeedLpLeft += lpAlpha * (l - crossfeedLpLeft)
                        crossfeedLpRight += lpAlpha * (r - crossfeedLpRight)
                        val newL = l * directMix + crossfeedLpRight * crossMix
                        val newR = r * directMix + crossfeedLpLeft * crossMix
                        l = newL
                        r = newR
                    }
                    l *= leftBalGain
                    r *= rightBalGain
                }

                l = applySoftLimiter(l, dsp.softLimiterEnabled)
                r = applySoftLimiter(r, dsp.softLimiterEnabled)

                writeSample(output, l, is16Bit)
                writeSample(output, r, is16Bit)

                if (captureSpectrum && f >= spectrumStartFrame) {
                    pushSample((l + r) * 0.5f)
                }
            } else {
                var monoSum = 0f
                for (c in 0 until channels) {
                    var s = readSample(input, is16Bit)
                    if (dsp.loudnessNormalizerEnabled) {
                        sumSquares += (s * s).toDouble()
                        sampleCount++
                    }
                    s = applySoftLimiter(s * combinedGain, dsp.softLimiterEnabled)
                    writeSample(output, s, is16Bit)
                    monoSum += s
                }
                if (captureSpectrum && f >= spectrumStartFrame) {
                    pushSample(monoSum / channels)
                }
            }
        }

        // Update adaptive RMS Loudness Normalizer envelope smoothly between audio blocks
        if (dsp.loudnessNormalizerEnabled && sampleCount > 0) {
            val blockRms = sqrt(sumSquares / sampleCount).toFloat()
            if (blockRms > 0.008f) {
                smoothedRms = smoothedRms * 0.85f + blockRms * 0.15f
                val targetGain = (TARGET_RMS / smoothedRms.coerceAtLeast(0.035f)).coerceIn(0.55f, 2.25f)
                val adaptAlpha = if (targetGain < agcGain) 0.22f else 0.06f
                agcGain = (agcGain + (targetGain - agcGain) * adaptAlpha).coerceIn(0.55f, 2.25f)
            }
        } else {
            agcGain = 1f
        }
    }

    private fun readSample(buf: ByteBuffer, is16Bit: Boolean): Float {
        return if (is16Bit) buf.short / 32768f else buf.float
    }

    private fun writeSample(buf: ByteBuffer, sample: Float, is16Bit: Boolean) {
        val clamped = sample.coerceIn(-1f, 1f)
        if (is16Bit) {
            val s16 = (clamped * 32767f).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            buf.putShort(s16)
        } else {
            buf.putFloat(clamped)
        }
    }

    private fun applySoftLimiter(sample: Float, enabled: Boolean): Float {
        if (!enabled) return sample.coerceIn(-1f, 1f)
        val absVal = abs(sample)
        if (absVal <= 0.78f) return sample
        val sign = if (sample >= 0f) 1f else -1f
        val excess = (absVal - 0.78f) / 0.22f
        val limited = 0.78f + 0.205f * tanh(excess.toDouble()).toFloat()
        return sign * limited.coerceAtMost(0.985f)
    }

    override fun onFlush() {
        ringWritePos = 0
        ringFilled = 0
        crossfeedLpLeft = 0f
        crossfeedLpRight = 0f
    }

    override fun onReset() {
        ringWritePos = 0
        ringFilled = 0
        agcGain = 1f
        smoothedRms = 0.16f
        crossfeedLpLeft = 0f
        crossfeedLpRight = 0f
        smoothedBars.fill(0f)
        smoothedEqBands.fill(0f)
    }

    private fun ingestPcm(buf: ByteBuffer, format: AudioProcessor.AudioFormat) {
        val channels = format.channelCount.coerceAtLeast(1)
        when (format.encoding) {
            C.ENCODING_PCM_16BIT -> {
                val frameBytes = channels * 2
                val totalFrames = buf.remaining() / frameBytes
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
            val mag = sqrt(re * re + im * im) / (FFT_SIZE / 4f)
            val dbNorm = (log10(1f + mag * 18f) / log10(19f)).coerceIn(0f, 1f)
            magnitudes[k] = dbNorm
        }

        val currentEqRatios = eqRatios
        val currentEqFreqs = eqFreqsHz
        val activeEq = eqEnabled
        val bassBoostNorm = if (activeEq) bassBoostStrength / 1000f else 0f

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

            val eqRatio = if (activeEq) {
                interpolateEqRatioAtFreq(fCenter, currentEqFreqs, currentEqRatios)
            } else {
                0f
            }
            val subBassExtra = if (fCenter < 180f) bassBoostNorm * 0.35f * (1f - fCenter / 180f) else 0f
            val gainMultiplier = (1f + eqRatio * 0.55f + subBassExtra).coerceIn(0.32f, 1.75f)
            val shaped = (rawLevel * gainMultiplier).coerceIn(0f, 1f)

            val prev = smoothedBars[b]
            val alpha = if (shaped > prev) 0.62f else 0.24f
            val next = (prev + (shaped - prev) * alpha).coerceIn(0f, 1f)
            smoothedBars[b] = next
            if (next > maxPeak) maxPeak = next

            curvePoints[b] = ((eqRatio + 1f) * 0.5f).coerceIn(0.05f, 0.95f)
        }

        // Compute exact 10-band Equalizer live meters synced to the 10 ISO center frequencies
        for (band in 0 until EQ_BANDS) {
            val centerHz = currentEqFreqs[band].toFloat()
            val lowHz = if (band == 0) 20f else sqrt(currentEqFreqs[band - 1].toFloat() * centerHz)
            val highHz = if (band == EQ_BANDS - 1) {
                (sampleRateHz / 2f).coerceAtMost(20000f)
            } else {
                sqrt(centerHz * currentEqFreqs[band + 1].toFloat())
            }

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
            val bassBonus = if (band <= 1) bassBoostNorm * 0.28f else 0f
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
        val last = minOf(freqs.lastIndex, ratios.lastIndex)
        if (last <= 0) return ratios.firstOrNull() ?: 0f
        if (freqHz <= freqs[0]) return ratios[0]
        if (freqHz >= freqs[last]) return ratios[last]
        val logF = ln(freqHz.toDouble())
        for (i in 0 until last) {
            if (freqHz <= freqs[i + 1]) {
                val logLow = ln(freqs[i].toDouble())
                val logHigh = ln(freqs[i + 1].toDouble())
                val t = ((logF - logLow) / (logHigh - logLow).coerceAtLeast(0.001)).toFloat().coerceIn(0f, 1f)
                return ratios[i] + (ratios[i + 1] - ratios[i]) * t
            }
        }
        return ratios[last]
    }

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
