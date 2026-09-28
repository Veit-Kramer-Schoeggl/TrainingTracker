package io.github.veitkramerschoeggl.trainingtracker

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.veitkramerschoeggl.trainingtracker.ui.MainRoute
import java.util.Locale

class MainActivity : ComponentActivity() {

    /** The app is German only — system dialogs (date picker) follow, regardless of the phone's language. */
    override fun attachBaseContext(newBase: Context) {
        val config = Configuration(newBase.resources.configuration).apply { setLocale(Locale.forLanguageTag("de-AT")) }
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent { MainRoute() }
    }
}
