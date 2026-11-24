package com.malluwayne.batbloom.update

import androidx.core.content.edit
import androidx.core.net.toUri

import android.app.DownloadManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Batbloom's GitHub repo: updates come from its Releases. This is the app's only use of the internet. */
const val GITHUB_REPO = "MalluGitWayne/batbloom"
const val GITHUB_URL = "https://github.com/$GITHUB_REPO"

data class Release(val version: String, val apkUrl: String?, val pageUrl: String, val notes: String)

object Updates {
    fun appVersion(context: Context): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0"

    private fun http(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 15_000
        c.setRequestProperty("Accept", "application/vnd.github+json")
        c.setRequestProperty("User-Agent", "Batbloom-Android")
        try {
            if (c.responseCode !in 200..299) throw IOException("GitHub answered ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    /** Latest release (public GitHub API, no account). Null when it can't be reached. */
    suspend fun latest(): Release? = withContext(Dispatchers.IO) {
        runCatching {
            val j = JSONObject(http("https://api.github.com/repos/$GITHUB_REPO/releases/latest"))
            val assets = j.optJSONArray("assets")
            val apk = (0 until (assets?.length() ?: 0)).map { assets!!.getJSONObject(it) }
                .firstOrNull { it.optString("name").endsWith(".apk") }?.optString("browser_download_url")
            Release(j.getString("tag_name").removePrefix("v"), apk, j.optString("html_url", "$GITHUB_URL/releases"), j.optString("body"))
        }.getOrNull()
    }

    /** True when version [a] ("1.10") is newer than [b] ("1.9"). */
    fun isNewer(a: String, b: String): Boolean {
        val x = a.split('.').map { it.toIntOrNull() ?: 0 }
        val y = b.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = x.getOrElse(i) { 0 } - y.getOrElse(i) { 0 }
            if (d != 0) return d > 0
        }
        return false
    }

    /** A quiet check at most once a day, remembered so Settings and the Focus screen can say an update is ready. */
    suspend fun dailyCheck(context: Context) {
        val sp = context.getSharedPreferences("updates", Context.MODE_PRIVATE)
        if (System.currentTimeMillis() - sp.getLong("checked", 0) < 24 * 3600_000L) return
        latest()?.let { remember(context, it) }
    }

    fun remember(context: Context, r: Release) {
        context.getSharedPreferences("updates", Context.MODE_PRIVATE).edit {
            putLong("checked", System.currentTimeMillis()).putString("latest", r.version)
        }
        known = known(context)
    }

    private fun known(context: Context): String? =
        context.getSharedPreferences("updates", Context.MODE_PRIVATE).getString("latest", null)
            ?.takeIf { isNewer(it, appVersion(context)) }

    /** Newer version found by the last check, or null when up to date. */
    var known by mutableStateOf<String?>(null)
        private set

    fun load(context: Context) { known = known(context) }
}

/**
 * Downloads the APK of a Batbloom release into the app's own folder, checks it (same app, newer, same signing key),
 * hands it to Android's installer and deletes it.
 */
object SelfUpdate {
    private const val FILE = "update.apk"

    sealed interface State {
        data object Idle : State
        data class Downloading(val done: Long, val total: Long) : State
        data object Installing : State
        data class Failed(val message: String) : State
    }

    var state by mutableStateOf<State>(State.Idle)
    /** Android's "Update this app?" screen; opened by the visible screen (background starts are often blocked). */
    var confirm by mutableStateOf<Intent?>(null)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    fun canInstall(context: Context) = context.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(context: Context) = context.startActivity(
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, ("package:${context.packageName}").toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )

    fun trusted(url: String?) = url != null && url.startsWith("$GITHUB_URL/releases/download/") && url.endsWith(".apk")

    fun start(context: Context, url: String, version: String) {
        if (state is State.Downloading) return
        job?.cancel()
        confirm = null
        val app = context.applicationContext
        job = scope.launch {
            var error: String? = null
            for (n in 1..3) {
                withContext(Dispatchers.Main) { state = State.Downloading(0, 0) }
                error = run(app, url, version)
                if (error == null || !error.startsWith("Download")) break
            }
            withContext(Dispatchers.Main) { state = if (error == null) State.Installing else State.Failed(error) }
        }
    }

    fun cancel() { job?.cancel(); state = State.Idle }

    private suspend fun run(context: Context, url: String, version: String): String? = withContext(Dispatchers.IO) {
        if (!trusted(url)) return@withContext "Not a Batbloom release"
        val file = File(context.getExternalFilesDir(null) ?: context.cacheDir, FILE)
        try {
            download(context, url, version, file)
            check(context, file)?.let { return@withContext it }
            install(context, file)
            null
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: java.net.UnknownHostException) {
            "No internet connection"
        } catch (e: Exception) {
            e.message ?: "Update failed"
        } finally {
            file.delete()
        }
    }

    private suspend fun download(context: Context, url: String, version: String, file: File) {
        val dm = context.getSystemService(DownloadManager::class.java)
        file.delete()
        val id = dm.enqueue(
            DownloadManager.Request(url.toUri())
                .setTitle("Batbloom $version")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                .setDestinationInExternalFilesDir(context, null, file.name),
        )
        var last = -1L
        var lastChange = System.currentTimeMillis()
        try {
            while (true) {
                kotlin.coroutines.coroutineContext.ensureActive()
                dm.query(DownloadManager.Query().setFilterById(id)).use { c ->
                    if (c == null || !c.moveToFirst()) throw IOException("Download stopped")
                    val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    val done = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    withContext(Dispatchers.Main) { state = State.Downloading(maxOf(0, done), total) }
                    val now = System.currentTimeMillis()
                    if (done != last) { last = done; lastChange = now }
                    else if (status == DownloadManager.STATUS_RUNNING && now - lastChange > 30_000) throw IOException("Download stalled")
                    when (status) {
                        DownloadManager.STATUS_SUCCESSFUL -> return
                        DownloadManager.STATUS_FAILED -> throw IOException("Download failed")
                    }
                }
                delay(400)
            }
        } catch (e: Exception) {
            dm.remove(id)
            throw e
        }
    }

    private fun check(context: Context, file: File): String? {
        val pm = context.packageManager
        // Signing details exist from Android 9; older versions skip the key check below.
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else 0
        val apk = pm.getPackageArchiveInfo(file.path, flags)
            ?: return "The download is damaged. Try again."
        if (apk.packageName != context.packageName) return "Not a Batbloom APK"
        val mine = pm.getPackageInfo(context.packageName, flags)
        val newer = if (Build.VERSION.SDK_INT >= 28) apk.longVersionCode > mine.longVersionCode
        else @Suppress("DEPRECATION") (apk.versionCode > mine.versionCode)
        if (!newer) return "You already have this version"
        if (Build.VERSION.SDK_INT >= 28) {
            fun sha(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }
            val theirs = apk.signingInfo?.apkContentsSigners?.map { sha(it.toByteArray()) }?.toSet()
            val ours = mine.signingInfo?.apkContentsSigners?.map { sha(it.toByteArray()) }?.toSet()
            if (theirs != null && ours != null && theirs != ours) {
                return "This APK has a different signing key. Uninstall Batbloom once and install it from GitHub."
            }
        }
        return null
    }

    private fun install(context: Context, file: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(file.length())
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        try {
            installer.openSession(id).use { session ->
                session.openWrite("batbloom.apk", 0, file.length()).use { out ->
                    file.inputStream().use { it.copyTo(out) }
                    session.fsync(out)
                }
                val result = PendingIntent.getBroadcast(
                    context, id, Intent(context, InstallReceiver::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                )
                session.commit(result.intentSender)
            }
        } catch (e: Exception) {
            runCatching { installer.abandonSession(id) }
            throw IOException("Install failed" + (e.message?.let { ": $it" } ?: ""), e)
        }
    }
}

class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val screen = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_INTENT)
                SelfUpdate.confirm = screen?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            PackageInstaller.STATUS_SUCCESS, PackageInstaller.STATUS_FAILURE_ABORTED -> SelfUpdate.state = SelfUpdate.State.Idle
            else -> SelfUpdate.state = SelfUpdate.State.Failed(
                "Update failed" + (intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)?.let { ": $it" } ?: ""),
            )
        }
    }
}
