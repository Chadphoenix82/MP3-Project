package com.example.player

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.example.data.model.ReplayGainCalculator
import com.example.data.model.ReplayGainResult
import com.example.data.model.ReplayGainSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.tan

/**
 * Dual-stage ReplayGain 2.0 & ITU-R BS.1770-4 LUFS Loudness Analyzer:
 * 1) Fast zero-decode embedded metadata parser for ID3v2 TXXX, Vorbis Comments (FLAC/OGG/Opus),
 *    R128_TRACK_GAIN / R128_ALBUM_GAIN (Q7.8 fixed point), and MP4/M4A iTunes freeform atoms.
 * 2) Direct PCM WAV + universal MediaExtractor/MediaCodec decoder feeding a true ITU-R BS.1770-4
 *    K-weighting biquad cascade (head-shelf + RLB high-pass) with 400ms 75%-overlap absolute
 *    (-70 LUFS) and relative (-10 LU) gated loudness measurement plus sample peak detection.
 */
class ReplayGainAnalyzer(private val context: Context) {

    /**
     * Fast header/tag scan without decoding audio PCM. Safe to call during library import.
     */
    suspend fun readEmbeddedReplayGainTags(uriString: String): ReplayGainResult = withContext(Dispatchers.IO) {
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return@withContext ReplayGainResult()
        val headerBytes = readHeaderBytes(uri, maxBytes = 196 * 1024) ?: return@withContext ReplayGainResult()
        parseReplayGainTagsFromBytes(headerBytes)
    }

    /**
     * Full loudness analysis: checks embedded ReplayGain metadata tags first (unless [forceAudioScan]
     * is true), then falls back to real ITU-R BS.1770-4 K-weighted LUFS + Peak PCM scanning.
     */
    suspend fun analyzeTrack(
        uriString: String,
        forceAudioScan: Boolean = false
    ): ReplayGainResult = withContext(Dispatchers.IO) {
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return@withContext ReplayGainResult()

        // 1. Check embedded ReplayGain metadata tags first (instant, exact if author-tagged)
        val tagResult = readEmbeddedReplayGainTags(uriString)
        if (!forceAudioScan && tagResult.trackGainDb != null) {
            val lufs = ReplayGainCalculator.RG2_REFERENCE_LUFS - tagResult.trackGainDb
            return@withContext tagResult.copy(
                integratedLufs = lufs,
                source = ReplayGainSource.TAG
            )
        }

        // 2. Decode PCM & run ITU-R BS.1770-4 K-weighted LUFS + peak scan
        val pcmResult = analyzeWavDirect(uri) ?: analyzeWithMediaCodec(uri)
        if (pcmResult != null) {
            // Preserve any embedded album tag if present while enriching with measured track LUFS/peak
            return@withContext pcmResult.copy(
                albumGainDb = tagResult.albumGainDb ?: pcmResult.albumGainDb,
                albumPeak = tagResult.albumPeak ?: pcmResult.albumPeak
            )
        }

        // Fall back to whatever tag data existed if decoder couldn't open the URI
        tagResult
    }

    // ---- Stage 1: Embedded ReplayGain Tag Parsing (ID3v2 / Vorbis / Opus / MP4) ----

    private fun readHeaderBytes(uri: Uri, maxBytes: Int): ByteArray? {
        return runCatching {
            val stream: InputStream? = if (uri.scheme == "file" && uri.path != null) {
                File(uri.path!!).takeIf { it.exists() }?.inputStream()
            } else {
                context.contentResolver.openInputStream(uri)
            }
            stream?.use { input ->
                val out = ByteArray(maxBytes)
                var total = 0
                while (total < maxBytes) {
                    val read = input.read(out, total, maxBytes - total)
                    if (read <= 0) break
                    total += read
                }
                if (total == maxBytes) out else out.copyOf(total)
            }
        }.getOrNull()
    }

    private fun parseReplayGainTagsFromBytes(bytes: ByteArray): ReplayGainResult {
        if (bytes.size < 16) return ReplayGainResult()

        var trackGain: Float? = null
        var albumGain: Float? = null
        var trackPeak: Float? = null
        var albumPeak: Float? = null

        fun ingestTag(rawKey: String, rawVal: String) {
            val key = rawKey.trim().uppercase()
            val value = rawVal.trim()
            if (value.isEmpty()) return
            when (key) {
                "REPLAYGAIN_TRACK_GAIN", "RG_TRACK_GAIN" -> {
                    parseDbValue(value)?.let { trackGain = it }
                }
                "REPLAYGAIN_ALBUM_GAIN", "RG_ALBUM_GAIN" -> {
                    parseDbValue(value)?.let { albumGain = it }
                }
                "REPLAYGAIN_TRACK_PEAK", "RG_TRACK_PEAK" -> {
                    parsePeakValue(value)?.let { trackPeak = it }
                }
                "REPLAYGAIN_ALBUM_PEAK", "RG_ALBUM_PEAK" -> {
                    parsePeakValue(value)?.let { albumPeak = it }
                }
                "R128_TRACK_GAIN" -> {
                    // RFC 7845 Opus R128 Q7.8 fixed-point relative to -23 LUFS -> convert to -18 LUFS RG2
                    value.toIntOrNull()?.let { q78 ->
                        trackGain = ((q78 / 256.0) + 5.0).toFloat().coerceIn(-24f, 24f)
                    }
                }
                "R128_ALBUM_GAIN" -> {
                    value.toIntOrNull()?.let { q78 ->
                        albumGain = ((q78 / 256.0) + 5.0).toFloat().coerceIn(-24f, 24f)
                    }
                }
            }
        }

        // A. Structured ID3v2 TXXX parser
        if (bytes[0] == 'I'.code.toByte() && bytes[1] == 'D'.code.toByte() && bytes[2] == '3'.code.toByte()) {
            val majorVer = bytes[3].toInt() and 0xFF
            val tagSize = decodeSynchsafe(bytes, 6)
            val endPos = (10 + tagSize).coerceAtMost(bytes.size)
            var pos = 10
            while (pos + 10 <= endPos) {
                val frameId = String(bytes, pos, 4, Charsets.ISO_8859_1)
                if (frameId[0].code == 0) break
                val frameSize = if (majorVer >= 4) {
                    decodeSynchsafe(bytes, pos + 4)
                } else {
                    decodeBigEndianInt(bytes, pos + 4)
                }
                if (frameSize <= 0 || pos + 10 + frameSize > endPos) break
                if (frameId == "TXXX" && frameSize > 2) {
                    val encoding = bytes[pos + 10].toInt() and 0xFF
                    val payload = bytes.copyOfRange(pos + 11, pos + 10 + frameSize)
                    val (desc, value) = splitId3Txxx(payload, encoding)
                    if (desc.isNotBlank() && value.isNotBlank()) {
                        ingestTag(desc, value)
                    }
                }
                pos += 10 + frameSize
            }
        }

        // B. Vorbis Comment & MP4/APE ASCII key=value scan (covers FLAC, OGG, Opus, APEv2, M4A)
        if (trackGain == null && albumGain == null) {
            val ascii = String(bytes, Charsets.ISO_8859_1)
            val regex = Regex(
                """(?i)(REPLAYGAIN_TRACK_GAIN|REPLAYGAIN_ALBUM_GAIN|REPLAYGAIN_TRACK_PEAK|REPLAYGAIN_ALBUM_PEAK|R128_TRACK_GAIN|R128_ALBUM_GAIN)[=\u0000:\s]{1,24}([+-]?\d+(?:\.\d+)?(?:\s*dB)?)"""
            )
            regex.findAll(ascii).forEach { match ->
                ingestTag(match.groupValues[1], match.groupValues[2])
            }
        }

        val hasAny = trackGain != null || albumGain != null
        return ReplayGainResult(
            trackGainDb = trackGain,
            albumGainDb = albumGain,
            trackPeak = trackPeak,
            albumPeak = albumPeak,
            integratedLufs = trackGain?.let { ReplayGainCalculator.RG2_REFERENCE_LUFS - it },
            source = if (hasAny) ReplayGainSource.TAG else ReplayGainSource.NONE
        )
    }

    private fun splitId3Txxx(payload: ByteArray, encoding: Int): Pair<String, String> {
        return when (encoding) {
            1, 2 -> {
                val charset = if (encoding == 1) Charsets.UTF_16 else Charsets.UTF_16BE
                var splitIdx = -1
                var i = 0
                while (i + 1 < payload.size) {
                    if (payload[i].toInt() == 0 && payload[i + 1].toInt() == 0) {
                        splitIdx = i
                        break
                    }
                    i += 2
                }
                if (splitIdx < 0) return "" to ""
                val key = String(payload, 0, splitIdx, charset).trim('\u0000', '\uFEFF')
                val value = String(payload, splitIdx + 2, payload.size - splitIdx - 2, charset).trim('\u0000', '\uFEFF')
                key to value
            }
            else -> {
                val charset: Charset = if (encoding == 3) Charsets.UTF_8 else Charsets.ISO_8859_1
                val splitIdx = payload.indexOf(0)
                if (splitIdx < 0) return "" to ""
                val key = String(payload, 0, splitIdx, charset).trim('\u0000')
                val value = String(payload, splitIdx + 1, payload.size - splitIdx - 1, charset).trim('\u0000')
                key to value
            }
        }
    }

    private fun decodeSynchsafe(bytes: ByteArray, offset: Int): Int {
        return ((bytes[offset].toInt() and 0x7F) shl 21) or
            ((bytes[offset + 1].toInt() and 0x7F) shl 14) or
            ((bytes[offset + 2].toInt() and 0x7F) shl 7) or
            (bytes[offset + 3].toInt() and 0x7F)
    }

    private fun decodeBigEndianInt(bytes: ByteArray, offset: Int): Int {
        return ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)
    }

    private fun parseDbValue(raw: String): Float? {
        val cleaned = raw.lowercase().replace("db", "").trim()
        return cleaned.toFloatOrNull()?.coerceIn(-24f, 24f)
    }

    private fun parsePeakValue(raw: String): Float? {
        return raw.trim().toFloatOrNull()?.takeIf { it > 0.0001f && it < 4f }
    }

    // ---- Stage 2A: Direct Fast-Path RIFF/WAV PCM Scanner ----

    private fun analyzeWavDirect(uri: Uri): ReplayGainResult? {
        return runCatching {
            val stream: InputStream = if (uri.scheme == "file" && uri.path != null) {
                File(uri.path!!).takeIf { it.exists() }?.inputStream() ?: return null
            } else {
                context.contentResolver.openInputStream(uri) ?: return null
            }

            stream.use { input ->
                val riff = ByteArray(12)
                if (readFully(input, riff) < 12) return null
                if (String(riff, 0, 4, Charsets.ISO_8859_1) != "RIFF" ||
                    String(riff, 8, 4, Charsets.ISO_8859_1) != "WAVE"
                ) {
                    return null
                }

                var audioFormat = 0
                var channels = 0
                var sampleRate = 0
                var bitsPerSample = 0
                var dataSize = 0L

                val chunkHeader = ByteArray(8)
                while (readFully(input, chunkHeader) == 8) {
                    val id = String(chunkHeader, 0, 4, Charsets.ISO_8859_1)
                    val size = ByteBuffer.wrap(chunkHeader, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL
                    if (id == "fmt ") {
                        val fmtBytes = ByteArray(size.toInt().coerceIn(16, 128))
                        if (readFully(input, fmtBytes) < 16) return null
                        val bb = ByteBuffer.wrap(fmtBytes).order(ByteOrder.LITTLE_ENDIAN)
                        audioFormat = bb.short.toInt() and 0xFFFF
                        channels = bb.short.toInt() and 0xFFFF
                        sampleRate = bb.int
                        bb.int // byteRate
                        bb.short // blockAlign
                        bitsPerSample = bb.short.toInt() and 0xFFFF
                        val extra = size - fmtBytes.size
                        if (extra > 0) skipFully(input, extra)
                    } else if (id == "data") {
                        dataSize = size
                        break
                    } else {
                        skipFully(input, size + (size and 1L))
                    }
                }

                val isPcm = audioFormat == 1 && (bitsPerSample == 16 || bitsPerSample == 24)
                val isFloat = audioFormat == 3 && bitsPerSample == 32
                if ((!isPcm && !isFloat) || channels !in 1..8 || sampleRate < 8000 || dataSize <= 0L) {
                    return null
                }

                val meter = Bs1770LoudnessMeter(sampleRateHz = sampleRate, channels = channels)
                val bytesPerSample = bitsPerSample / 8
                val frameBytes = channels * bytesPerSample
                val maxFramesToScan = (sampleRate * 90L) // Up to 90s of PCM WAV
                val totalFrames = (dataSize / frameBytes).coerceAtMost(maxFramesToScan)
                val buffer = ByteArray(frameBytes * 2048)
                var framesRead = 0L

                while (framesRead < totalFrames) {
                    val toReadBytes = minOf(buffer.size.toLong(), (totalFrames - framesRead) * frameBytes).toInt()
                    val n = input.read(buffer, 0, toReadBytes)
                    if (n <= 0) break
                    val usableFrames = n / frameBytes
                    val bb = ByteBuffer.wrap(buffer, 0, usableFrames * frameBytes).order(ByteOrder.LITTLE_ENDIAN)
                    when {
                        isPcm && bitsPerSample == 16 -> meter.processPcm16(bb, usableFrames)
                        isPcm && bitsPerSample == 24 -> meter.processPcm24(buffer, usableFrames)
                        isFloat -> meter.processPcmFloat(bb, usableFrames)
                    }
                    framesRead += usableFrames
                }

                meter.finalizeResult()
            }
        }.getOrNull()
    }

    private fun readFully(input: InputStream, dest: ByteArray): Int {
        var total = 0
        while (total < dest.size) {
            val n = input.read(dest, total, dest.size - total)
            if (n <= 0) break
            total += n
        }
        return total
    }

    private fun skipFully(input: InputStream, count: Long) {
        var remaining = count
        while (remaining > 0L) {
            val skipped = input.skip(remaining)
            if (skipped <= 0L) {
                if (input.read() < 0) break
                remaining--
            } else {
                remaining -= skipped
            }
        }
    }

    // ---- Stage 2B: Universal MediaExtractor + MediaCodec ITU-R BS.1770-4 Scanner ----

    private fun analyzeWithMediaCodec(uri: Uri): ReplayGainResult? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            if (uri.scheme == "file" && uri.path != null) {
                extractor.setDataSource(uri.path!!)
            } else {
                extractor.setDataSource(context, uri, null)
            }

            var audioTrackIndex = -1
            var inputFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    inputFormat = fmt
                    break
                }
            }
            if (audioTrackIndex < 0 || inputFormat == null) return null

            extractor.selectTrack(audioTrackIndex)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: return null
            var sampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceIn(1, 8)
            val durationUs = if (inputFormat.containsKey(MediaFormat.KEY_DURATION)) {
                inputFormat.getLong(MediaFormat.KEY_DURATION)
            } else {
                0L
            }

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(inputFormat, null, null, 0)
            codec.start()

            var meter = Bs1770LoudnessMeter(sampleRateHz = sampleRate, channels = channels)
            val bufferInfo = MediaCodec.BufferInfo()

            // For tracks <= 75s, scan end-to-end; for longer tracks, sample 5 evenly-spaced 10s windows (~50s total)
            val windowPositionsUs: List<Pair<Long, Long>> = if (durationUs > 75_000_000L) {
                val windowLenUs = 10_000_000L
                val usableSpan = (durationUs - windowLenUs).coerceAtLeast(1L)
                List(5) { idx ->
                    val startUs = (usableSpan * idx) / 4L
                    startUs to (startUs + windowLenUs)
                }
            } else {
                listOf(0L to Long.MAX_VALUE)
            }

            for ((windowStartUs, windowEndUs) in windowPositionsUs) {
                if (windowStartUs > 0L) {
                    extractor.seekTo(windowStartUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                    codec.flush()
                    meter.resetWindowBoundary()
                }

                var inputDone = false
                var outputDone = false
                var idleIterations = 0

                while (!outputDone && idleIterations < 150) {
                    if (!inputDone) {
                        val inIdx = codec.dequeueInputBuffer(5_000L)
                        if (inIdx >= 0) {
                            val inBuf = codec.getInputBuffer(inIdx)
                            if (inBuf != null) {
                                val sampleSize = extractor.readSampleData(inBuf, 0)
                                val sampleTimeUs = extractor.sampleTime
                                if (sampleSize < 0 || sampleTimeUs > windowEndUs) {
                                    codec.queueInputBuffer(
                                        inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                    )
                                    inputDone = true
                                } else {
                                    codec.queueInputBuffer(inIdx, 0, sampleSize, sampleTimeUs, 0)
                                    extractor.advance()
                                }
                            }
                        }
                    }

                    val outIdx = codec.dequeueOutputBuffer(bufferInfo, 5_000L)
                    when {
                        outIdx >= 0 -> {
                            idleIterations = 0
                            val outBuf = codec.getOutputBuffer(outIdx)
                            if (outBuf != null && bufferInfo.size > 0) {
                                outBuf.position(bufferInfo.offset)
                                outBuf.limit(bufferInfo.offset + bufferInfo.size)
                                val slice = outBuf.slice().order(ByteOrder.LITTLE_ENDIAN)
                                val frames = bufferInfo.size / (channels * 2)
                                if (frames > 0) {
                                    meter.processPcm16(slice, frames)
                                }
                            }
                            codec.releaseOutputBuffer(outIdx, false)
                            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                                outputDone = true
                            }
                        }
                        outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val newFmt = codec.outputFormat
                            val newRate = if (newFmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                                newFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            } else sampleRate
                            val newCh = if (newFmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                                newFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceIn(1, 8)
                            } else channels
                            if (newRate != sampleRate || newCh != channels) {
                                sampleRate = newRate
                                channels = newCh
                                meter = Bs1770LoudnessMeter(sampleRateHz = sampleRate, channels = channels)
                            }
                        }
                        else -> idleIterations++
                    }
                }
            }

            return meter.finalizeResult()
        } catch (_: Exception) {
            return null
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    /**
     * Exact ITU-R BS.1770-4 K-weighted integrated loudness (LUFS) & sample-peak meter.
     * Supports arbitrary sample rates (44.1kHz, 48kHz, 88.2kHz, 96kHz+) and 1..8 channels.
     */
    private class Bs1770LoudnessMeter(
        private val sampleRateHz: Int,
        private val channels: Int
    ) {
        // Stage 1: Acoustic head-effect high-shelf pre-filter (+4 dB @ ~1682 Hz)
        private val s1b0: Double
        private val s1b1: Double
        private val s1b2: Double
        private val s1a1: Double
        private val s1a2: Double

        // Stage 2: RLB 2nd-order high-pass weighting filter (~38.14 Hz)
        private val s2b0: Double
        private val s2b1: Double
        private val s2b2: Double
        private val s2a1: Double
        private val s2a2: Double

        // Per-channel biquad delay states (Direct Form II Transposed)
        private val s1z1 = DoubleArray(channels)
        private val s1z2 = DoubleArray(channels)
        private val s2z1 = DoubleArray(channels)
        private val s2z2 = DoubleArray(channels)

        // ITU-R BS.1770 channel weighting gains (L, R, C = 1.0; LFE = 0.0; Surrounds = 1.41)
        private val channelWeights = DoubleArray(channels) { ch ->
            when {
                channels >= 5 && ch == 3 -> 0.0 // LFE excluded
                channels >= 5 && ch >= 4 -> 1.41
                else -> 1.0
            }
        }

        // 100ms sub-block accumulators (4 sub-blocks = 400ms ITU-R BS.1770 gating block, 75% overlap)
        private val subBlockLength = (sampleRateHz / 10).coerceAtLeast(256)
        private val subBlockAccum = DoubleArray(channels)
        private val subBlockHistory = Array(4) { DoubleArray(channels) }
        private var subBlockFrames = 0
        private var subBlockCount = 0

        private val blockEnergies = ArrayList<Double>(512)
        private var maxSamplePeak = 0.0
        private var totalProcessedFrames = 0L

        init {
            val fs = sampleRateHz.toDouble().coerceAtLeast(8000.0)

            // Stage 1 High-Shelf
            val f0Shelf = 1681.974450955533
            val gShelf = 3.999843853973347
            val qShelf = 0.7071752369554196
            val k1 = tan(PI * f0Shelf / fs)
            val vh = 10.0.pow(gShelf / 20.0)
            val vb = vh.pow(0.4996667741545416)
            val a01 = 1.0 + k1 / qShelf + k1 * k1
            s1b0 = (vh + vb * k1 / qShelf + k1 * k1) / a01
            s1b1 = 2.0 * (k1 * k1 - vh) / a01
            s1b2 = (vh - vb * k1 / qShelf + k1 * k1) / a01
            s1a1 = 2.0 * (k1 * k1 - 1.0) / a01
            s1a2 = (1.0 - k1 / qShelf + k1 * k1) / a01

            // Stage 2 RLB High-Pass
            val f0Hp = 38.13547087602444
            val qHp = 0.5003270373238773
            val k2 = tan(PI * f0Hp / fs)
            val a02 = 1.0 + k2 / qHp + k2 * k2
            s2b0 = 1.0 / a02
            s2b1 = -2.0 / a02
            s2b2 = 1.0 / a02
            s2a1 = 2.0 * (k2 * k2 - 1.0) / a02
            s2a2 = (1.0 - k2 / qHp + k2 * k2) / a02
        }

        fun resetWindowBoundary() {
            s1z1.fill(0.0)
            s1z2.fill(0.0)
            s2z1.fill(0.0)
            s2z2.fill(0.0)
            subBlockAccum.fill(0.0)
            subBlockFrames = 0
            subBlockCount = 0
        }

        fun processPcm16(buf: ByteBuffer, frames: Int) {
            for (f in 0 until frames) {
                for (c in 0 until channels) {
                    val sample = buf.short.toDouble() / 32768.0
                    ingestChannelSample(c, sample)
                }
                advanceFrame()
            }
        }

        fun processPcm24(bytes: ByteArray, frames: Int) {
            var idx = 0
            for (f in 0 until frames) {
                for (c in 0 until channels) {
                    val b0 = bytes[idx].toInt() and 0xFF
                    val b1 = bytes[idx + 1].toInt() and 0xFF
                    val b2 = bytes[idx + 2].toInt()
                    val int24 = (b2 shl 16) or (b1 shl 8) or b0
                    ingestChannelSample(c, int24.toDouble() / 8388608.0)
                    idx += 3
                }
                advanceFrame()
            }
        }

        fun processPcmFloat(buf: ByteBuffer, frames: Int) {
            for (f in 0 until frames) {
                for (c in 0 until channels) {
                    val sample = buf.float.toDouble().coerceIn(-2.0, 2.0)
                    ingestChannelSample(c, sample)
                }
                advanceFrame()
            }
        }

        private fun ingestChannelSample(c: Int, x: Double) {
            val absX = abs(x)
            if (absX > maxSamplePeak) maxSamplePeak = absX

            // Stage 1 K-weighting high-shelf
            val y1 = s1b0 * x + s1z1[c]
            s1z1[c] = s1b1 * x - s1a1 * y1 + s1z2[c]
            s1z2[c] = s1b2 * x - s1a2 * y1

            // Stage 2 RLB high-pass
            val y2 = s2b0 * y1 + s2z1[c]
            s2z1[c] = s2b1 * y1 - s2a1 * y2 + s2z2[c]
            s2z2[c] = s2b2 * y1 - s2a2 * y2

            subBlockAccum[c] += y2 * y2
        }

        private fun advanceFrame() {
            totalProcessedFrames++
            subBlockFrames++
            if (subBlockFrames >= subBlockLength) {
                val slot = subBlockCount % 4
                for (c in 0 until channels) {
                    subBlockHistory[slot][c] = subBlockAccum[c] / subBlockFrames.toDouble()
                    subBlockAccum[c] = 0.0
                }
                subBlockFrames = 0
                subBlockCount++

                if (subBlockCount >= 4) {
                    var weightedEnergy = 0.0
                    for (c in 0 until channels) {
                        val avg4 = (
                            subBlockHistory[0][c] +
                                subBlockHistory[1][c] +
                                subBlockHistory[2][c] +
                                subBlockHistory[3][c]
                            ) * 0.25
                        weightedEnergy += channelWeights[c] * avg4
                    }
                    if (weightedEnergy > 1e-12) {
                        blockEnergies.add(weightedEnergy)
                    }
                }
            }
        }

        fun finalizeResult(): ReplayGainResult? {
            if (totalProcessedFrames < 256L || maxSamplePeak < 1e-5) return null

            // Handle ultra-short clips (< 400ms) using partial accumulated energy
            if (blockEnergies.isEmpty() && subBlockFrames > 0) {
                var weightedEnergy = 0.0
                for (c in 0 until channels) {
                    weightedEnergy += channelWeights[c] * (subBlockAccum[c] / subBlockFrames.toDouble())
                }
                if (weightedEnergy > 1e-12) {
                    blockEnergies.add(weightedEnergy)
                }
            }

            if (blockEnergies.isEmpty()) return null

            // Step 1: Absolute gate at -70.0 LUFS
            val absGated = blockEnergies.filter { energy ->
                val lufs = -0.691 + 10.0 * log10(energy)
                lufs > -70.0
            }
            if (absGated.isEmpty()) return null

            // Step 2: Relative gate at (ungated average - 10.0 LU)
            val ungatedMean = absGated.average()
            val relThresholdLufs = -0.691 + 10.0 * log10(ungatedMean) - 10.0
            val relGated = absGated.filter { energy ->
                (-0.691 + 10.0 * log10(energy)) > relThresholdLufs
            }
            val finalMean = if (relGated.isNotEmpty()) relGated.average() else ungatedMean
            if (finalMean <= 1e-12) return null

            val integratedLufs = (-0.691 + 10.0 * log10(finalMean)).toFloat().coerceIn(-42f, -2f)
            val trackGainDb = (ReplayGainCalculator.RG2_REFERENCE_LUFS - integratedLufs).coerceIn(-18f, 18f)
            val peak = maxSamplePeak.toFloat().coerceIn(0.001f, 2.0f)

            return ReplayGainResult(
                trackGainDb = trackGainDb,
                trackPeak = peak,
                integratedLufs = integratedLufs,
                source = ReplayGainSource.LUFS_SCAN
            )
        }
    }
}
