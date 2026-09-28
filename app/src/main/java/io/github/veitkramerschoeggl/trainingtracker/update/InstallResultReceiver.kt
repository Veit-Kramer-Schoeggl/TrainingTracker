package io.github.veitkramerschoeggl.trainingtracker.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import io.github.veitkramerschoeggl.trainingtracker.TrainingTrackerApp

/**
 * Receives the status of the install session started by [UpdateManager]. Not exported: only the
 * system can call it, through the app's own PendingIntent.
 */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as TrainingTrackerApp
        app.container.updateManager.onInstallStatus(
            status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE),
            message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE),
            confirmation = intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java),
        )
    }
}
