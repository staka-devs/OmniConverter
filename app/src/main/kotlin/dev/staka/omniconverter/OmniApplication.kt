package dev.staka.omniconverter

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.work.Configuration
import dev.staka.omniconverter.presentation.OmniApp

class OmniApplication : Application(), Configuration.Provider {
    val container by lazy { AppContainer(this) }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(container.workerFactory).build()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val useCases = (application as OmniApplication).container.useCases
        setContent { OmniApp(useCases) }
    }
}
