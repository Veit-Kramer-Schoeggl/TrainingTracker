package io.github.veitkramerschoeggl.trainingtracker.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.content.pm.SigningInfo
import android.os.Build
import android.provider.Settings
import androidx.core.net.toUri
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface UpdateState {
    data object Idle : UpdateState
    data class Available(val manifest: UpdateManifest) : UpdateState
    data class Downloading(val manifest: UpdateManifest, val progress: Float?) : UpdateState
    data class Installing(val manifest: UpdateManifest) : UpdateState

    /** Android wants the user to confirm the installation; the UI has to launch [intent]. */
    data class ConfirmationRequired(val manifest: UpdateManifest, val intent: Intent) : UpdateState
    data class Failed(val manifest: UpdateManifest, val message: String) : UpdateState
}

sealed interface UpdateCheckResult {
    data class Available(val manifest: UpdateManifest) : UpdateCheckResult
    data object UpToDate : UpdateCheckResult
    data class Error(val message: String) : UpdateCheckResult
}

private class UpdateException(message: String) : Exception(message)

/**
 * Self-update without an app store: reads `update.json` of the latest GitHub release, downloads
 * the APK, checks hash, package and signing key, and hands it to Android's PackageInstaller.
 * Android only accepts it when it is signed with the same key and has a higher versionCode — the
 * app's data (database, settings) stays untouched by the update.
 */
class UpdateManager(
    private val context: Context,
    private val manifestUrl: String,
    private val currentVersionCode: Int,
    private val userAgent: String,
    /** Debug builds may use a local http:// test server. */
    private val allowCleartext: Boolean,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    val isEnabled: Boolean get() = manifestUrl.isNotBlank()

    private val downloadDir get() = File(context.cacheDir, "updates")

    suspend fun check(): UpdateCheckResult = withContext(Dispatchers.IO) {
        if (!isEnabled) return@withContext UpdateCheckResult.UpToDate
        try {
            val manifest = fetchManifest() ?: return@withContext UpdateCheckResult.UpToDate
            if (manifest.versionCode <= currentVersionCode || manifest.minSdk > Build.VERSION.SDK_INT) {
                return@withContext UpdateCheckResult.UpToDate
            }
            _state.update { current ->
                // Never interrupt a download or installation that is already running.
                if (current is UpdateState.Idle || current is UpdateState.Available || current is UpdateState.Failed) {
                    UpdateState.Available(manifest)
                } else {
                    current
                }
            }
            UpdateCheckResult.Available(manifest)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            UpdateCheckResult.Error("Update-Prüfung fehlgeschlagen – bitte Internetverbindung prüfen.")
        }
    }

    /** Whether the user allowed this app to install apps ("Install unknown apps"). */
    fun canInstallPackages(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun installPermissionSettingsIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri())

    fun downloadAndInstall() {
        val manifest = when (val current = state.value) {
            is UpdateState.Available -> current.manifest
            is UpdateState.Failed -> current.manifest
            else -> return
        }
        _state.value = UpdateState.Downloading(manifest, progress = null)
        scope.launch(Dispatchers.IO) {
            try {
                val apk = download(manifest)
                verify(apk)
                _state.value = UpdateState.Installing(manifest)
                install(apk)
            } catch (e: CancellationException) {
                throw e
            } catch (e: UpdateException) {
                _state.value = UpdateState.Failed(manifest, e.message.orEmpty())
            } catch (e: Exception) {
                _state.value = UpdateState.Failed(manifest, "Download fehlgeschlagen – bitte Internetverbindung prüfen.")
            }
        }
    }

    /** "Später": hide the notice until the next check. */
    fun dismiss() {
        _state.update { if (it is UpdateState.Available || it is UpdateState.Failed) UpdateState.Idle else it }
    }

    /** The UI has launched the system's confirmation dialog. */
    fun onConfirmationShown() {
        _state.update { if (it is UpdateState.ConfirmationRequired) UpdateState.Installing(it.manifest) else it }
    }

    /** Result of the install session, delivered by [InstallResultReceiver]. */
    fun onInstallStatus(status: Int, message: String?, confirmation: Intent?) {
        val manifest = when (val current = state.value) {
            is UpdateState.Installing -> current.manifest
            is UpdateState.ConfirmationRequired -> current.manifest
            else -> return
        }
        _state.value = when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION ->
                if (confirmation != null) UpdateState.ConfirmationRequired(manifest, confirmation) else return
            // Normally the process is already replaced by the new version at this point.
            PackageInstaller.STATUS_SUCCESS -> UpdateState.Idle
            // Cancelled in the system dialog, or stopped by Google Play Protect.
            PackageInstaller.STATUS_FAILURE_ABORTED -> UpdateState.Failed(manifest, "Die Installation wurde abgebrochen.")
            else -> UpdateState.Failed(manifest, installFailureMessage(status, message))
        }
    }

    /** Removes downloaded APKs (after an update or an aborted attempt). */
    fun cleanUpDownloads() {
        downloadDir.deleteRecursively()
    }

    private fun connect(url: String): HttpURLConnection {
        val parsed = URL(url)
        if (parsed.protocol != "https" && !(allowCleartext && parsed.protocol == "http")) {
            throw UpdateException("Unsichere Update-Adresse: $url")
        }
        return (parsed.openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", userAgent)
        }
    }

    /** Null when there is no release yet. */
    private fun fetchManifest(): UpdateManifest? {
        val connection = connect(manifestUrl)
        try {
            return when (connection.responseCode) {
                HttpURLConnection.HTTP_OK -> UpdateManifest.parse(connection.inputStream.bufferedReader().use { it.readText() })
                HttpURLConnection.HTTP_NOT_FOUND -> null
                else -> throw IOException("HTTP ${connection.responseCode}")
            }
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun download(manifest: UpdateManifest): File {
        cleanUpDownloads()
        downloadDir.mkdirs()
        val file = File(downloadDir, "update-${manifest.versionCode}.apk")
        val connection = connect(manifest.apkUrl)
        try {
            if (connection.responseCode !in 200..299) throw IOException("HTTP ${connection.responseCode}")
            val length = connection.contentLengthLong.takeIf { it > 0 }
            val digest = MessageDigest.getInstance("SHA-256")
            connection.inputStream.use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    var lastPercent = -1
                    while (true) {
                        kotlin.coroutines.coroutineContext.ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        digest.update(buffer, 0, n)
                        done += n
                        if (length != null) {
                            val percent = (done * 100 / length).toInt()
                            if (percent != lastPercent) {
                                lastPercent = percent
                                _state.value = UpdateState.Downloading(manifest, percent / 100f)
                            }
                        }
                    }
                }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (manifest.sha256 != null && actual != manifest.sha256) {
                throw UpdateException("Die Datei wurde beim Herunterladen beschädigt. Bitte erneut versuchen.")
            }
            return file
        } finally {
            connection.disconnect()
        }
    }

    /** Checks before handing the APK to Android, for clear error messages. */
    private fun verify(apk: File) {
        val pm = context.packageManager
        val flags = PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
        val archive = pm.getPackageArchiveInfo(apk.path, flags)
            ?: throw UpdateException("Die heruntergeladene Datei ist keine gültige App.")
        if (archive.packageName != context.packageName) {
            throw UpdateException("Die heruntergeladene Datei gehört zu einer anderen App.")
        }
        if (archive.longVersionCode <= currentVersionCode) {
            throw UpdateException("Die heruntergeladene Version ist nicht neuer als die installierte.")
        }
        val installed = pm.getPackageInfo(context.packageName, flags).signingInfo?.certificates().orEmpty()
        val update = archive.signingInfo?.certificates().orEmpty()
        if (installed.isNotEmpty() && update.isNotEmpty() && installed.none { it in update }) {
            throw UpdateException("Das Update ist mit einem anderen Schlüssel signiert und kann nicht installiert werden.")
        }
    }

    private fun SigningInfo.certificates(): Set<Signature> =
        (if (hasMultipleSigners()) apkContentsSigners else signingCertificateHistory).orEmpty().toSet()

    private fun install(apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
            setInstallReason(PackageManager.INSTALL_REASON_USER)
            setPackageSource(PackageInstaller.PACKAGE_SOURCE_DOWNLOADED_FILE)
            // Once the app has installed itself, later updates may need no extra confirmation.
            setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val sessionId = installer.createSession(params)
        try {
            installer.openSession(sessionId).use { session ->
                apk.inputStream().use { input ->
                    session.openWrite("update.apk", 0, apk.length()).use { output ->
                        input.copyTo(output)
                        session.fsync(output)
                    }
                }
                val callback = PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    Intent(context, InstallResultReceiver::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                )
                session.commit(callback.intentSender)
            }
        } catch (e: Exception) {
            installer.abandonSession(sessionId)
            throw UpdateException("Die Installation konnte nicht gestartet werden.")
        }
    }

    private fun installFailureMessage(status: Int, message: String?): String = when (status) {
        PackageInstaller.STATUS_FAILURE_CONFLICT ->
            "Das Update passt nicht zur installierten App (anderer Signaturschlüssel)."
        PackageInstaller.STATUS_FAILURE_STORAGE -> "Nicht genug Speicherplatz für das Update."
        PackageInstaller.STATUS_FAILURE_INVALID -> "Die Update-Datei ist ungültig."
        PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "Das Update ist mit diesem Gerät nicht kompatibel."
        PackageInstaller.STATUS_FAILURE_BLOCKED -> "Die Installation wurde vom System blockiert."
        else -> "Installation fehlgeschlagen" + (message?.let { ": $it" } ?: ".")
    }
}
