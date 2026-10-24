package dev.sajancodes.hardblock.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.sajancodes.hardblock.BuildConfig
import dev.sajancodes.hardblock.domain.update.ReleaseVersion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

data class GitHubRelease(val tag: String, val version: ReleaseVersion, val notes: String, val prerelease: Boolean,
    val apkUrl: String, val size: Long, val digest: String?)
data class UpdateState(val busy: Boolean = false, val release: GitHubRelease? = null, val message: String = "Installed ${BuildConfig.VERSION_NAME}",
    val downloaded: Boolean = false, val progress: Int = 0)

@Singleton
class GitHubUpdater @Inject constructor(@param:ApplicationContext private val context: Context) {
    private val mutable = MutableStateFlow(UpdateState())
    val state = mutable.asStateFlow()
    private val directory get() = File(context.cacheDir, "updates").apply { mkdirs() }
    private val candidate get() = File(directory, "candidate.apk")

    suspend fun check() = withContext(Dispatchers.IO) {
        if (mutable.value.busy) return@withContext
        mutable.value = UpdateState(busy = true, message = "Checking GitHub Releases…")
        try {
            val connection = connect("https://api.github.com/repos/sajancodes/hardblocker-app/releases?per_page=20")
            val payload = try { connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= 2_000_000) { "Release metadata is too large" }
                    output.write(buffer, 0, count)
                }
                output.toString("UTF-8")
            } } finally { connection.disconnect() }
            val releases = JSONArray(payload)
            val installed = requireNotNull(ReleaseVersion.parse(BuildConfig.VERSION_NAME))
            val newer = (0 until releases.length()).mapNotNull { i ->
                val r = releases.getJSONObject(i)
                if (r.optBoolean("draft")) return@mapNotNull null
                val tag = r.getString("tag_name")
                val version = ReleaseVersion.parse(tag) ?: return@mapNotNull null
                if (version <= installed) return@mapNotNull null
                val assets = r.getJSONArray("assets")
                val apk = (0 until assets.length()).map { assets.getJSONObject(it) }.firstOrNull { it.getString("name").endsWith(".apk", true) } ?: return@mapNotNull null
                GitHubRelease(tag, version, r.optString("body").take(2000), r.optBoolean("prerelease"), apk.getString("browser_download_url"), apk.getLong("size"),
                    apk.optString("digest").takeIf { it.startsWith("sha256:") }?.removePrefix("sha256:"))
            }.maxByOrNull { it.version }
            mutable.value = UpdateState(release = newer, message = if (newer == null) "You're up to date (${BuildConfig.VERSION_NAME})." else "${newer.tag} available${if (newer.prerelease) " · pre-release" else ""}")
        } catch (cancelled: CancellationException) { mutable.value = UpdateState(message = "Update check canceled"); throw cancelled }
        catch (error: Exception) { mutable.value = UpdateState(message = "Update check failed: ${error.message}. Try again later.") }
    }

    suspend fun download() = withContext(Dispatchers.IO) {
        val release = mutable.value.release ?: return@withContext
        if (mutable.value.busy) return@withContext
        mutable.value = mutable.value.copy(busy = true, downloaded = false, progress = 0, message = "Downloading ${release.tag}…")
        val partial = File(directory, "candidate.part")
        try {
            require(release.size in 1..100_000_000) { "Unexpected APK size" }
            val connection = connect(release.apkUrl)
            val digest = MessageDigest.getInstance("SHA-256")
            var bytes = 0L
            try {
                connection.inputStream.use { input -> partial.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        bytes += count; require(bytes <= 100_000_000) { "Download exceeded the size limit" }
                        output.write(buffer, 0, count); digest.update(buffer, 0, count)
                        val progress = ((bytes * 100) / release.size).toInt().coerceIn(0, 100)
                        if (progress != mutable.value.progress) mutable.value = mutable.value.copy(progress = progress)
                    }
                    output.fd.sync()
                } }
            } finally { connection.disconnect() }
            require(bytes == release.size) { "Download is incomplete" }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            require(release.digest == null || actual.equals(release.digest, true)) { "APK checksum mismatch" }
            validateCandidate(partial, release.version)
            if (candidate.exists()) check(candidate.delete())
            check(partial.renameTo(candidate)) { "Could not store the verified update" }
            mutable.value = mutable.value.copy(busy = false, downloaded = true, message = "APK verified. Android will ask you to confirm installation.")
        } catch (cancelled: CancellationException) { partial.delete(); mutable.value = mutable.value.copy(busy = false, message = "Download canceled"); throw cancelled }
        catch (error: Exception) { partial.delete(); mutable.value = mutable.value.copy(busy = false, downloaded = false, message = "Update rejected: ${error.message}") }
    }

    @Suppress("DEPRECATION")
    internal fun validateCandidate(file: File, expected: ReleaseVersion? = null) {
        val pm = context.packageManager
        val archive = requireNotNull(pm.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)) { "Invalid APK" }
        val installed = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        require(archive.packageName == context.packageName) { "APK belongs to another application" }
        require(archive.longVersionCode > installed.longVersionCode) { "APK is not newer than the installed app" }
        val archiveVersion = requireNotNull(ReleaseVersion.parse(archive.versionName.orEmpty())) { "Invalid APK version" }
        require(expected == null || archiveVersion == expected) { "APK version does not match the release" }
        fun fingerprints(info: android.content.pm.PackageInfo): Set<String> = info.signingInfo?.apkContentsSigners?.map {
            MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).joinToString("") { byte -> "%02x".format(byte) }
        }?.toSet().orEmpty()
        val actual = fingerprints(archive); val trusted = fingerprints(installed)
        require(actual.isNotEmpty() && actual == trusted) { "APK signing identity differs from this installation. Debug builds cannot install release updates." }
    }

    suspend fun installationIntent(): Intent? = withContext(Dispatchers.IO) {
        try {
            require(mutable.value.downloaded && !mutable.value.busy) { "Download and verify an update first" }
            validateCandidate(candidate, mutable.value.release?.version)
            if (!context.packageManager.canRequestPackageInstalls()) {
                mutable.value = mutable.value.copy(message = "Allow installs from HardBlock in Android Settings, then return and press Install update again.")
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri())
            } else {
                Intent(Intent.ACTION_VIEW).setDataAndType(FileProvider.getUriForFile(context, "${context.packageName}.updates", candidate), "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { mutable.value = mutable.value.copy(message = "Installation unavailable: ${error.message}"); null }
    }

    private fun connect(raw: String): HttpURLConnection {
        var next = raw
        repeat(6) {
            val uri = URI(next)
            require(uri.scheme == "https" && uri.userInfo == null && (uri.port == -1 || uri.port == 443) &&
                (uri.host in setOf("github.com", "api.github.com") || uri.host?.endsWith(".githubusercontent.com") == true)) { "Update host is not trusted" }
            val connection = uri.toURL().openConnection() as HttpURLConnection
            connection.connectTimeout = 20_000; connection.readTimeout = 30_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("User-Agent", "HardBlocker/${BuildConfig.VERSION_NAME}")
            val code = connection.responseCode
            if (code in 300..399) { next = uri.resolve(requireNotNull(connection.getHeaderField("Location"))).toString(); connection.disconnect() }
            else { if (code !in 200..299) { connection.disconnect(); error("GitHub returned HTTP $code") }; return connection }
        }
        error("Too many update redirects")
    }
}
