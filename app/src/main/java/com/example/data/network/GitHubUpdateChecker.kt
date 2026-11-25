package com.example.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.BuildConfig
import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class GitHubReleaseAssetDto(
    val name: String = "",
    @Json(name = "browser_download_url") val browserDownloadUrl: String = "",
    val size: Long = 0L
)

data class GitHubReleaseDto(
    @Json(name = "tag_name") val tagName: String = "",
    val name: String? = null,
    @Json(name = "html_url") val htmlUrl: String = "",
    val body: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    @Json(name = "published_at") val publishedAt: String? = null,
    val assets: List<GitHubReleaseAssetDto> = emptyList()
)

data class AppUpdateInfo(
    val currentVersion: String,
    val latestTag: String,
    val releaseTitle: String,
    val releaseNotes: String,
    val downloadPageUrl: String,
    val apkDownloadUrl: String? = null,
    val apkFileName: String? = null,
    val apkSizeBytes: Long? = null
)

sealed class UpdateCheckOutcome {
    data object Offline : UpdateCheckOutcome()
    data class UpToDate(val currentVersion: String) : UpdateCheckOutcome()
    data class UpdateAvailable(val update: AppUpdateInfo) : UpdateCheckOutcome()
    data class Unavailable(val reason: String) : UpdateCheckOutcome()
}

/**
 * Privacy-first GitHub release checker. Checks local ConnectivityManager status first,
 * sends zero device/user telemetry, and queries GitHub's public Releases API to notify
 * the user with a direct link to the GitHub download page when a newer release is published.
 */
class GitHubUpdateChecker(private val context: Context) {

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val releaseAdapter = moshi.adapter(GitHubReleaseDto::class.java)

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    fun isOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    suspend fun checkForUpdate(repoSlug: String): UpdateCheckOutcome = withContext(Dispatchers.IO) {
        if (!isOnline()) return@withContext UpdateCheckOutcome.Offline

        val sanitizedSlug = repoSlug.trim().removePrefix("https://github.com/").trim('/')
        if (!sanitizedSlug.contains("/")) {
            return@withContext UpdateCheckOutcome.Unavailable("Set a valid GitHub owner/repo")
        }

        val currentVersion = BuildConfig.VERSION_NAME
        val url = "https://api.github.com/repos/$sanitizedSlug/releases/latest"
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "PhotonAudio/$currentVersion")
            .build()

        runCatching {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext UpdateCheckOutcome.Unavailable("No published release found for $sanitizedSlug")
                }
                val body = response.body?.string().orEmpty()
                val dto = releaseAdapter.fromJson(body)
                    ?: return@withContext UpdateCheckOutcome.Unavailable("Could not parse release metadata")

                if (dto.draft || dto.tagName.isBlank() || dto.htmlUrl.isBlank()) {
                    return@withContext UpdateCheckOutcome.UpToDate(currentVersion)
                }

                if (isNewerVersion(remoteTag = dto.tagName, localVersion = currentVersion)) {
                    val apkAsset = dto.assets.firstOrNull { asset ->
                        asset.name.endsWith(".apk", ignoreCase = true) &&
                            asset.browserDownloadUrl.startsWith("https://", ignoreCase = true)
                    }
                    UpdateCheckOutcome.UpdateAvailable(
                        AppUpdateInfo(
                            currentVersion = currentVersion,
                            latestTag = dto.tagName,
                            releaseTitle = dto.name?.takeIf { it.isNotBlank() } ?: "Photon Audio ${dto.tagName}",
                            releaseNotes = dto.body?.trim().orEmpty()
                                .ifBlank { "A new version of Photon Audio is ready on GitHub." },
                            downloadPageUrl = dto.htmlUrl,
                            apkDownloadUrl = apkAsset?.browserDownloadUrl,
                            apkFileName = apkAsset?.name,
                            apkSizeBytes = apkAsset?.size?.takeIf { it > 0L }
                        )
                    )
                } else {
                    UpdateCheckOutcome.UpToDate(currentVersion)
                }
            }
        }.getOrElse {
            UpdateCheckOutcome.Unavailable("Could not reach GitHub right now")
        }
    }

    companion object {
        fun isNewerVersion(remoteTag: String, localVersion: String): Boolean {
            val remoteParts = parseNumericVersion(remoteTag)
            val localParts = parseNumericVersion(localVersion)
            val maxLen = maxOf(remoteParts.size, localParts.size)
            for (i in 0 until maxLen) {
                val r = remoteParts.getOrElse(i) { 0 }
                val l = localParts.getOrElse(i) { 0 }
                if (r > l) return true
                if (r < l) return false
            }
            return false
        }

        private fun parseNumericVersion(raw: String): List<Int> {
            return raw.trim()
                .removePrefix("v")
                .removePrefix("V")
                .substringBefore("-")
                .split(".")
                .map { part -> part.filter { it.isDigit() }.toIntOrNull() ?: 0 }
        }
    }
}
