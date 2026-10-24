package dev.sajancodes.hardblock.debug

import android.app.admin.DevicePolicyManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkManager
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import dev.sajancodes.hardblock.enforcement.ProtectionBridge
import dev.sajancodes.hardblock.scheduler.WorkerDependencies
import dev.sajancodes.hardblock.ui.dashboard.DashboardViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Exists only in debug APKs, with no normal navigation entry. Launch using ADB. */
@AndroidEntryPoint
class DebugActivity : ComponentActivity() {
    private val model: DashboardViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val state by model.state.collectAsStateWithLifecycle()
            var database by remember { mutableStateOf("Loading") }
            LaunchedEffect(Unit) {
                model.refresh()
                database = withContext(Dispatchers.IO) {
                    val db = EntryPointAccessors.fromApplication(applicationContext, WorkerDependencies::class.java).database()
                    val records = db.policyDao().all()
                    val history = db.policyDao().history()
                    val jobs = WorkManager.getInstance(applicationContext).getWorkInfosForUniqueWork("protection-watchdog").get()
                    "Database schema: ${db.openHelper.readableDatabase.version}\nPolicy rows: ${records.size}\nLast event: ${history.firstOrNull()?.event}\nJobs: ${jobs.joinToString { it.state.name }}"
                }
            }
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.statusBarsPadding().padding(24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Internal diagnostics", style = MaterialTheme.typography.headlineMedium)
                        Text("Foreground: ${state.protection.foreground ?: state.snapshot?.foregroundPackage}")
                        Text("Mode: ${state.protection.mode}\nState: ${state.protection.state}\nDevice Owner: ${getSystemService(DevicePolicyManager::class.java).isDeviceOwnerApp(packageName)}")
                        Text("Usage Access: ${state.accessGranted}\nAccessibility: ${ProtectionBridge.service != null}\n${state.protection.detail}")
                        Text(database)
                        state.policies.forEach { p -> Text("${p.id}\nExpires: ${p.expiresAt}\nUsage: ${state.policyUsage[p.id]}\nLimit: ${p.dailyLimit}\nPackages: ${p.packages}") }
                        state.error?.let { Text(it) }
                    }
                }
            }
        }
    }
}
