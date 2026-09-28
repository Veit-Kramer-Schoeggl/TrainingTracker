package io.github.veitkramerschoeggl.trainingtracker

import android.app.Application
import android.content.Context
import io.github.veitkramerschoeggl.trainingtracker.data.SettingsRepository
import io.github.veitkramerschoeggl.trainingtracker.data.TrainingRepository
import io.github.veitkramerschoeggl.trainingtracker.data.db.TrainingDatabase
import io.github.veitkramerschoeggl.trainingtracker.data.settingsDataStore
import io.github.veitkramerschoeggl.trainingtracker.excel.ExcelFiles
import io.github.veitkramerschoeggl.trainingtracker.update.UpdateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

class TrainingTrackerApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Creates the app-wide singletons (manual dependency injection). */
class AppContainer(context: Context) {
    private val appScope = CoroutineScope(SupervisorJob())

    val database = TrainingDatabase.create(context)
    val trainingRepository = TrainingRepository(database.setDao())
    val settingsRepository = SettingsRepository(context.settingsDataStore)
    val excelFiles = ExcelFiles(context.contentResolver)
    val updateManager = UpdateManager(
        context = context,
        manifestUrl = BuildConfig.UPDATE_MANIFEST_URL,
        currentVersionCode = BuildConfig.VERSION_CODE,
        userAgent = "Pull-ups/${BuildConfig.VERSION_NAME} (Android)",
        allowCleartext = BuildConfig.DEBUG,
        scope = appScope,
    )
}
