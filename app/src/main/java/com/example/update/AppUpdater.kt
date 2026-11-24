package com.example.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.example.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object GitHubUpdaterConfig {
    const val REPO_OWNER = "mummytee"
    const val REPO_NAME = "A-Tutor"
    const val LATEST_RELEASE_API_URL =
        "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases/latest"
}

@Serializable
data class GitHubReleaseAsset(
    val name: String = "",
    @SerialName("browser_download_url") val browserDownloadUrl: String = "",
    val size: Long = 0L,
    @SerialName("content_type") val contentType: String = ""
)

@Serializable
data class GitHubReleaseDto(
    @SerialName("tag_name") val tagName: String = "",
    val name: String = "",
    val body: String = "",
    @SerialName("html_url") val htmlUrl: String = "",
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<GitHubReleaseAsset> = emptyList()
)

data class AvailableAppUpdate(
    val tagName: String,
    val versionName: String,
    val releaseTitle: String,
    val changelogMarkdown: String,
    val apkDownloadUrl: String,
    val apkFileName: String,
    val apkSizeBytes: Long,
    val releasePageUrl: String
)

sealed interface AppUpdateUiState {
    data object Idle : AppUpdateUiState
    data object Checking : AppUpdateUiState
    data object UpToDate : AppUpdateUiState
    data class UpdateAvailable(val update: AvailableAppUpdate) : AppUpdateUiState
    data class Downloading(
        val update: AvailableAppUpdate,
        val downloadedBytes: Long,
        val totalBytes: Long
    ) : AppUpdateUiState {
        val progress: Float?
            get() = if (totalBytes > 0L) (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else null
    }

    data class NeedsInstallPermission(
        val update: AvailableAppUpdate,
        val cachedApkPath: String
    ) : AppUpdateUiState

    data class ReadyToInstall(
        val update: AvailableAppUpdate,
        val cachedApkPath: String
    ) : AppUpdateUiState

    data class Error(
        val message: String,
        val pendingUpdate: AvailableAppUpdate? = null
    ) : AppUpdateUiState
}

object AppVersionComparator {
    fun normalize(rawVersion: String): String {
        return rawVersion.trim()
            .removePrefix("v")
            .removePrefix("V")
            .substringBefore("+")
            .substringBefore("-")
            .trim()
    }

    fun parseSegments(rawVersion: String): List<Int> {
        val clean = normalize(rawVersion)
        if (clean.isEmpty()) return emptyList()
        return clean.split(".").map { part ->
            part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
        }
    }

    fun compare(remoteTagOrVersion: String, localVersion: String): Int {
        val remote = parseSegments(remoteTagOrVersion)
        val local = parseSegments(localVersion)
        val maxLen = maxOf(remote.size, local.size)
        for (i in 0 until maxLen) {
            val r = remote.getOrElse(i) { 0 }
            val l = local.getOrElse(i) { 0 }
            if (r != l) return r.compareTo(l)
        }
        return 0
    }

    fun isRemoteNewer(remoteTagOrVersion: String, localVersion: String): Boolean {
        return compare(remoteTagOrVersion, localVersion) > 0
    }
}

class GitHubAppUpdater(
    private val context: Context,
    private val latestReleaseApiUrl: String = GitHubUpdaterConfig.LATEST_RELEASE_API_URL
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val _state = MutableStateFlow<AppUpdateUiState>(AppUpdateUiState.Idle)
    val state: StateFlow<AppUpdateUiState> = _state.asStateFlow()

    private val _showDialog = MutableStateFlow(false)
    val showDialog: StateFlow<Boolean> = _showDialog.asStateFlow()

    suspend fun checkForUpdates(manual: Boolean) {
        if (_state.value is AppUpdateUiState.Checking ||
            _state.value is AppUpdateUiState.Downloading
        ) {
            if (manual) _showDialog.value = true
            return
        }

        _state.value = AppUpdateUiState.Checking
        try {
            val release = fetchLatestRelease()
            if (release.draft) {
                _state.value = if (manual) AppUpdateUiState.UpToDate else AppUpdateUiState.Idle
                return
            }

            val remoteVersion = AppVersionComparator.normalize(release.tagName)
            val localVersion = BuildConfig.VERSION_NAME
            val apkAsset = release.assets.firstOrNull {
                it.name.endsWith(".apk", ignoreCase = true) && it.browserDownloadUrl.isNotBlank()
            } ?: release.assets.firstOrNull {
                it.contentType.equals("application/vnd.android.package-archive", ignoreCase = true) &&
                    it.browserDownloadUrl.isNotBlank()
            }

            if (apkAsset != null && AppVersionComparator.isRemoteNewer(remoteVersion, localVersion)) {
                val available = AvailableAppUpdate(
                    tagName = release.tagName.ifBlank { "v$remoteVersion" },
                    versionName = remoteVersion,
                    releaseTitle = release.name.ifBlank { "A-Tutor ${release.tagName}" },
                    changelogMarkdown = release.body.ifBlank {
                        "A newer version ($remoteVersion) of A-Tutor is ready to install."
                    },
                    apkDownloadUrl = apkAsset.browserDownloadUrl,
                    apkFileName = apkAsset.name.ifBlank { "A-Tutor-update.apk" },
                    apkSizeBytes = apkAsset.size,
                    releasePageUrl = release.htmlUrl
                )
                _state.value = AppUpdateUiState.UpdateAvailable(available)
                _showDialog.value = true
            } else {
                _state.value = if (manual) AppUpdateUiState.UpToDate else AppUpdateUiState.Idle
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (manual) {
                _state.value = AppUpdateUiState.Error(
                    message = e.localizedMessage ?: "Unable to check GitHub for updates."
                )
            } else {
                // Avoid disrupting app startup when offline, rate-limited, or repository is private
                _state.value = AppUpdateUiState.Idle
            }
        }
    }

    suspend fun downloadAndInstall(update: AvailableAppUpdate) {
        if (_state.value is AppUpdateUiState.Downloading) return
        _showDialog.value = true

        try {
            val cachedApk = downloadApkToCache(update)
            triggerInstallFromFile(update, cachedApk)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.value = AppUpdateUiState.Error(
                message = e.localizedMessage ?: "Failed to download APK update.",
                pendingUpdate = update
            )
        }
    }

    fun retryInstallWithCachedApk(update: AvailableAppUpdate, cachedApkPath: String) {
        val file = File(cachedApkPath)
        if (!file.exists()) {
            _state.value = AppUpdateUiState.UpdateAvailable(update)
            return
        }
        triggerInstallFromFile(update, file)
    }

    fun openInstallUnknownAppsSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val settingsIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(settingsIntent)
        }
    }

    fun canRequestPackageInstalls(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()
    }

    fun dismissDialog() {
        _showDialog.value = false
        if (_state.value is AppUpdateUiState.Error) {
            val pending = (_state.value as AppUpdateUiState.Error).pendingUpdate
            _state.value = if (pending != null) {
                AppUpdateUiState.UpdateAvailable(pending)
            } else {
                AppUpdateUiState.Idle
            }
        }
    }

    fun reopenDialog() {
        _showDialog.value = true
    }

    private fun triggerInstallFromFile(update: AvailableAppUpdate, apkFile: File) {
        if (!canRequestPackageInstalls()) {
            _state.value = AppUpdateUiState.NeedsInstallPermission(update, apkFile.absolutePath)
            return
        }

        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        _state.value = AppUpdateUiState.ReadyToInstall(update, apkFile.absolutePath)
        context.startActivity(installIntent)
    }

    private suspend fun fetchLatestRelease(): GitHubReleaseDto = withContext(Dispatchers.IO) {
        val conn = (URL(latestReleaseApiUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "A-Tutor-Android/${BuildConfig.VERSION_NAME}")
        }
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val hint = when (code) {
                    403 -> "GitHub API rate limit reached. Try again later."
                    404 -> "No published GitHub release found (or repository is private)."
                    else -> "GitHub release check failed (HTTP $code)."
                }
                throw IOException(hint)
            }
            json.decodeFromString(GitHubReleaseDto.serializer(), body)
        } finally {
            conn.disconnect()
        }
    }

    private suspend fun downloadApkToCache(update: AvailableAppUpdate): File = withContext(Dispatchers.IO) {
        val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
        updatesDir.listFiles()?.forEach { existing ->
            if (existing.isFile && existing.name.endsWith(".apk", ignoreCase = true)) {
                existing.delete()
            }
        }
        val safeName = update.apkFileName.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank {
            "A-Tutor-update.apk"
        }
        val targetFile = File(updatesDir, safeName)

        var currentUrl = update.apkDownloadUrl
        var redirects = 0
        var conn: HttpURLConnection? = null
        try {
            while (true) {
                currentCoroutineContext().ensureActive()
                val nextConn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    instanceFollowRedirects = true
                    connectTimeout = 15_000
                    readTimeout = 30_000
                    setRequestProperty("Accept", "application/octet-stream")
                    setRequestProperty("User-Agent", "A-Tutor-Android/${BuildConfig.VERSION_NAME}")
                }
                val code = nextConn.responseCode
                if (code in 300..399 && redirects < 5) {
                    val location = nextConn.getHeaderField("Location")
                    nextConn.disconnect()
                    if (location.isNullOrBlank()) {
                        throw IOException("Redirect location missing while downloading APK.")
                    }
                    currentUrl = URL(URL(currentUrl), location).toString()
                    redirects++
                    continue
                }
                conn = nextConn
                break
            }

            val activeConn = checkNotNull(conn)
            val code = activeConn.responseCode
            if (code !in 200..299) {
                throw IOException("Failed to download APK (HTTP $code).")
            }

            val contentLength = activeConn.contentLengthLong.takeIf { it > 0L } ?: update.apkSizeBytes
            var downloaded = 0L
            var lastReportedBytes = 0L
            _state.value = AppUpdateUiState.Downloading(update, 0L, contentLength)

            activeConn.inputStream.use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (downloaded - lastReportedBytes >= 65_536L || downloaded == contentLength) {
                            lastReportedBytes = downloaded
                            _state.value = AppUpdateUiState.Downloading(update, downloaded, contentLength)
                        }
                    }
                    output.flush()
                }
            }
            _state.value = AppUpdateUiState.Downloading(update, downloaded, contentLength.coerceAtLeast(downloaded))
            targetFile
        } finally {
            conn?.disconnect()
        }
    }
}
