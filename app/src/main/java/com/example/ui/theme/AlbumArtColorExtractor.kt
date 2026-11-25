package com.example.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

@Immutable
data class AdaptiveArtworkPalette(
    val primaryGlow: Color,
    val secondaryGlow: Color,
    val backdropTint: Color
)

/**
 * Lightweight zero-dependency on-device album art color sampler.
 * Decodes local or cached cover art to a tiny thumbnail on a background dispatcher,
 * finds vibrant hue clusters, and clamps lightness so dark-mode contrast always remains crisp.
 */
object AlbumArtColorExtractor {
    private val cache = ConcurrentHashMap<String, AdaptiveArtworkPalette>()

    suspend fun extractPalette(
        context: Context,
        artworkUri: String?,
        fallbackPrimary: Color,
        fallbackSecondary: Color,
        fallbackBackdrop: Color
    ): AdaptiveArtworkPalette {
        val uri = artworkUri?.trim()?.takeIf { it.isNotBlank() }
            ?: return AdaptiveArtworkPalette(fallbackPrimary, fallbackSecondary, fallbackBackdrop)

        cache[uri]?.let { return it }

        val extracted = withContext(Dispatchers.IO) {
            runCatching {
                val bitmap = loadSmallBitmap(context, uri) ?: return@runCatching null
                try {
                    computeVibrantPalette(bitmap, fallbackPrimary, fallbackSecondary, fallbackBackdrop)
                } finally {
                    bitmap.recycle()
                }
            }.getOrNull()
        }

        val resolved = extracted ?: AdaptiveArtworkPalette(fallbackPrimary, fallbackSecondary, fallbackBackdrop)
        if (extracted != null) {
            if (cache.size > 64) cache.clear()
            cache[uri] = resolved
        }
        return resolved
    }

    private fun loadSmallBitmap(context: Context, rawUri: String): Bitmap? {
        val opts = BitmapFactory.Options().apply {
            inSampleSize = 4
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return when {
            rawUri.startsWith("content://") -> {
                context.contentResolver.openInputStream(Uri.parse(rawUri))?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, opts)
                }
            }
            rawUri.startsWith("file://") -> {
                val path = Uri.parse(rawUri).path ?: return null
                BitmapFactory.decodeFile(path, opts)
            }
            rawUri.startsWith("/") -> {
                if (File(rawUri).exists()) BitmapFactory.decodeFile(rawUri, opts) else null
            }
            rawUri.startsWith("http://") || rawUri.startsWith("https://") -> {
                val conn = (URL(rawUri).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 2500
                    readTimeout = 2500
                }
                try {
                    conn.inputStream.use { stream ->
                        BitmapFactory.decodeStream(stream, null, opts)
                    }
                } finally {
                    conn.disconnect()
                }
            }
            else -> null
        }?.let { decoded ->
            if (decoded.width > 48 || decoded.height > 48) {
                val scaled = Bitmap.createScaledBitmap(decoded, 36, 36, true)
                if (scaled !== decoded) decoded.recycle()
                scaled
            } else {
                decoded
            }
        }
    }

    private fun computeVibrantPalette(
        bitmap: Bitmap,
        fallbackPrimary: Color,
        fallbackSecondary: Color,
        fallbackBackdrop: Color
    ): AdaptiveArtworkPalette {
        val bucketCount = 12 // 30-degree hue sectors
        val weights = FloatArray(bucketCount)
        val hueSums = FloatArray(bucketCount)
        val satSums = FloatArray(bucketCount)
        val valSums = FloatArray(bucketCount)
        val hsv = FloatArray(3)

        val w = bitmap.width
        val h = bitmap.height
        for (y in 0 until h step 2) {
            for (x in 0 until w step 2) {
                val pixel = bitmap.getPixel(x, y)
                val alpha = (pixel ushr 24) and 0xFF
                if (alpha < 180) continue
                android.graphics.Color.colorToHSV(pixel, hsv)
                val sat = hsv[1]
                val brightness = hsv[2]
                // Ignore near-black and near-white washed pixels
                if (brightness < 0.16f || sat < 0.18f) continue
                val bucket = ((hsv[0] / 30f).toInt()).coerceIn(0, bucketCount - 1)
                val score = (sat * sat) * (0.4f + brightness * 0.6f)
                weights[bucket] += score
                hueSums[bucket] += hsv[0] * score
                satSums[bucket] += sat * score
                valSums[bucket] += brightness * score
            }
        }

        val sortedBuckets = (0 until bucketCount)
            .filter { weights[it] > 0.05f }
            .sortedByDescending { weights[it] }

        if (sortedBuckets.isEmpty()) {
            return AdaptiveArtworkPalette(fallbackPrimary, fallbackSecondary, fallbackBackdrop)
        }

        val primaryIdx = sortedBuckets.first()
        val pHue = (hueSums[primaryIdx] / weights[primaryIdx]).coerceIn(0f, 359.9f)
        val pSat = (satSums[primaryIdx] / weights[primaryIdx]).coerceIn(0.55f, 0.92f)
        val pVal = (valSums[primaryIdx] / weights[primaryIdx]).coerceIn(0.78f, 0.98f)
        val primaryColor = Color(android.graphics.Color.HSVToColor(floatArrayOf(pHue, pSat, pVal)))

        val secondaryIdx = sortedBuckets.firstOrNull { candidate ->
            val diff = kotlin.math.abs(candidate - primaryIdx)
            val circularDiff = minOf(diff, bucketCount - diff)
            circularDiff >= 2
        }

        val secondaryColor = if (secondaryIdx != null) {
            val sHue = (hueSums[secondaryIdx] / weights[secondaryIdx]).coerceIn(0f, 359.9f)
            val sSat = (satSums[secondaryIdx] / weights[secondaryIdx]).coerceIn(0.48f, 0.88f)
            val sVal = (valSums[secondaryIdx] / weights[secondaryIdx]).coerceIn(0.72f, 0.95f)
            Color(android.graphics.Color.HSVToColor(floatArrayOf(sHue, sSat, sVal)))
        } else {
            val shiftedHue = (pHue + 42f) % 360f
            Color(android.graphics.Color.HSVToColor(floatArrayOf(shiftedHue, 0.68f, 0.90f)))
        }

        // Deep rich cosmic backdrop tint derived from primary hue (strictly dark for readability)
        val backdropTint = Color(android.graphics.Color.HSVToColor(floatArrayOf(pHue, 0.62f, 0.14f)))

        return AdaptiveArtworkPalette(
            primaryGlow = primaryColor,
            secondaryGlow = secondaryColor,
            backdropTint = backdropTint
        )
    }
}
