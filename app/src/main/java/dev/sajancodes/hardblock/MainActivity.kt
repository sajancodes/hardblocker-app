package dev.sajancodes.hardblock

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.viewModels
import dagger.hilt.android.AndroidEntryPoint
import dev.sajancodes.hardblock.ui.HardBlockApp
import dev.sajancodes.hardblock.ui.dashboard.DashboardViewModel

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val model: DashboardViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent { HardBlockApp(model, ::openUsageSettings) }
    }
    private fun openUsageSettings(): Boolean = try {
        startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        true
    } catch (_: ActivityNotFoundException) { false }
}
