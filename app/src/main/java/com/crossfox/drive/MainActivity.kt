package com.crossfox.drive

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.NotificationManagerCompat

class MainActivity : ComponentActivity() {
    private val listenerEnabled = mutableStateOf(false)
    private val homeSignal = mutableIntStateOf(0)
    private lateinit var repo: SettingsRepo

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = SettingsRepo(applicationContext)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = C.red, background = C.bg, surface = C.panel)) {
                CrossfoxApp(repo, listenerEnabled.value, homeSignal.intValue, this)
            }
        }
    }

    /** Se llama al pulsar Inicio estando ya en el launcher (singleTask). */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        homeSignal.intValue++
    }

    override fun onResume() {
        super.onResume()
        listenerEnabled.value = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
    }
}
