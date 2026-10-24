package dev.sajancodes.hardblock.ui

import android.content.pm.PackageManager
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.compose.*
import dev.sajancodes.hardblock.data.repository.InstalledApp
import dev.sajancodes.hardblock.ui.dashboard.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import dev.sajancodes.hardblock.enforcement.ProtectionBridge

private val Accent = Color(0xFFC5EDAB)
private val Muted = Color(0xFF9DA7AC)
private val Background = Color(0xFF0B0E11)
private val Surface = Color(0xFF171C20)

@Composable
fun HardBlockApp(model: DashboardViewModel, openUsageSettings: () -> Boolean) {
    val state by model.state.collectAsStateWithLifecycle()
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) { model.refresh(); delay(30_000) }
        }
    }
    MaterialTheme(colorScheme = darkColorScheme(primary = Accent, secondaryContainer = Color(0xFF283525),
        onSecondaryContainer = Accent, background = Background, surface = Surface, onSurface = Color(0xFFE7EBE8))) {
        val nav = rememberNavController()
        Scaffold(containerColor = Background, bottomBar = {
            NavigationBar(containerColor = Background) {
                val entry by nav.currentBackStackEntryAsState()
                listOf("dashboard" to "Overview", "apps" to "Applications", "commitments" to "Commitments", "protection" to "Protection").forEach { (route, title) ->
                    NavigationBarItem(selected = entry?.destination?.route == route, onClick = {
                        nav.navigate(route) { popUpTo(nav.graph.startDestinationId) { saveState = true }; launchSingleTop = true; restoreState = true }
                    }, icon = { Text(when(route) { "dashboard" -> "◷"; "apps" -> "▦"; else -> "◇" }, fontSize = 24.sp) }, label = { Text(title) })
                }
            }
        }) { padding ->
            NavHost(navController = nav, startDestination = "dashboard", modifier = Modifier.padding(padding)) {
                composable("dashboard") { Dashboard(state, model::refresh) { nav.navigate("protection") } }
                composable("apps") { AppsScreen(state, model) }
                composable("commitments") { CommitmentsScreen(state, model) }
                composable("protection") { ProtectionScreen(state, model::acknowledge, openUsageSettings, model::refresh) }
            }
        }
        state.message?.let { message -> AlertDialog(onDismissRequest = model::clearMessage,
            title = { Text("Commitment update") }, text = { Text(message) },
            confirmButton = { TextButton(onClick = model::clearMessage) { Text("OK") } }) }
    }
}

@Composable
private fun PageTitle(eyebrow: String, title: String) {
    Text(eyebrow.uppercase(), color = Accent, fontSize = 11.sp, letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(10.dp))
    Text(title, fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun Dashboard(state: DashboardState, refresh: () -> Unit, setup: () -> Unit) {
    val apps = remember(state.apps, state.snapshot) { state.apps.sortedByDescending { state.snapshot?.usedMillis?.get(it.packageName) ?: 0L } }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { PageTitle("HARDBLOCK / YOUR TIME, INTENTIONALLY", "Make room for\nwhat matters.") }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(24.dp)) {
                    Text("TODAY’S APP USAGE", color = Muted, fontSize = 11.sp, letterSpacing = 1.5.sp)
                    Spacer(Modifier.height(16.dp))
                    Text(if (state.accessGranted && state.snapshot != null) duration(apps.sumOf { state.snapshot.usedMillis[it.packageName] ?: 0L }) else "—",
                        fontSize = 48.sp, fontWeight = FontWeight.Light, color = Accent)
                    Spacer(Modifier.height(12.dp))
                    Text("Launchable apps · foreground time", color = Muted, fontSize = 13.sp)
                    state.snapshot?.let { snapshot ->
                        Text("Resets ${formatInstant(snapshot.window.end)}", color = Muted, fontSize = 12.sp)
                    }
                }
            }
        }
        item {
            Card(onClick = setup, colors = CardDefaults.cardColors(containerColor = Surface)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (!state.accessGranted) "Connect Usage Access →" else "${state.protection.mode} protection →", color = Accent)
                    Text(if (!state.accessGranted) "Allow HardBlock to read foreground usage on this device." else state.protection.detail, color = Muted, fontSize = 13.sp)
                }
            }
        }
        state.error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Most used today", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                TextButton(onClick = refresh) { Text("Refresh") }
            }
        }
        if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (state.accessGranted) {
            items(apps.filter { (state.snapshot?.usedMillis?.get(it.packageName) ?: 0L) > 0 }.take(8), key = { it.packageName }) { app ->
                AppRow(app, state.snapshot?.usedMillis?.get(app.packageName))
            }
            if (!state.loading && apps.none { (state.snapshot?.usedMillis?.get(it.packageName) ?: 0L) > 0 }) item {
                Text("No foreground usage found today. Use an app, then return and refresh.", color = Muted)
            }
        }
        item { Text("PRIVATE BY DESIGN\nNo account. No analytics. Usage stays on this device.", color = Muted, fontSize = 12.sp, lineHeight = 20.sp) }
    }
}

@Composable
private fun AppsScreen(state: DashboardState, model: DashboardViewModel) {
    var query by remember { mutableStateOf("") }
    val apps = remember(state.apps, query) { state.apps.filter { it.label.contains(query, true) || it.packageName.contains(query, true) } }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { PageTitle("ON THIS DEVICE", "Applications") }
        item { Text("Create restrictions in Commitments. Protect your authenticator before activating a policy; essential apps cannot be blocked.", color = Muted) }
        item { OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Search apps or packages") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
        if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        items(apps, key = { it.packageName }) { app ->
            Column {
                AppRow(app, if (state.accessGranted) state.snapshot?.usedMillis?.get(app.packageName) ?: 0L else null, showPackage = true,
                    restriction = if (app.packageName in state.protectedPackages) "Essential · never blocked" else state.policies.firstOrNull { app.packageName in it.packages }?.let(::policySummary) ?: "No restriction")
                if (app.packageName !in state.protectedPackages) TextButton(onClick = { model.protect(app.packageName) }, enabled = !state.saving) { Text("Protect as essential / authenticator") }
            }
        }
        if (!state.loading && apps.isEmpty()) item { Text("No matching applications.", color = Muted) }
    }
}

@Composable
private fun AppRow(app: InstalledApp, used: Long?, showPackage: Boolean = false, restriction: String = "No restriction") {
    Row(Modifier.fillMaxWidth().background(Surface, RoundedCornerShape(16.dp)).padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AppIcon(app)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(app.label, fontWeight = FontWeight.Medium)
            if (showPackage) Text(app.packageName, color = Muted, fontSize = 10.sp)
            Text(restriction, color = Muted, fontSize = 12.sp)
        }
        Text(used?.let(::duration) ?: "—", color = Accent, fontSize = 14.sp)
    }
}

@Composable
private fun AppIcon(app: InstalledApp) {
    val context = LocalContext.current
    val icon by produceState<ImageBitmap?>(null, app.packageName) {
        value = withContext(Dispatchers.IO) {
            try { context.packageManager.getApplicationIcon(app.packageName).toBitmap(96, 96).asImageBitmap() }
            catch (_: PackageManager.NameNotFoundException) { null }
        }
    }
    if (icon != null) Image(icon!!, contentDescription = null, modifier = Modifier.size(40.dp))
    else Box(Modifier.size(40.dp).background(Background, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) { Text(app.label.take(1), color = Accent) }
}

@Composable
private fun ProtectionScreen(state: DashboardState, acknowledge: () -> Unit, openSettings: () -> Boolean, refresh: () -> Unit) {
    val context = LocalContext.current
    var showConsent by remember { mutableStateOf(false) }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh() }
    var unavailable by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        item { PageTitle("UNDERSTAND YOUR PROTECTION", "A deliberate\ncommitment.") }
        item {
            Text("01 / Local by default", color = Accent, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text("HardBlock reads app usage locally to calculate daily foreground time. It has no network permission and sends no app names, installed-app lists or usage history to a server.", color = Muted)
        }
        item {
            Text("02 / Usage Access", color = Accent, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(if (state.accessGranted) "Usage Access is granted. HardBlock checks the actual permission whenever this screen resumes." else "Usage Access is off. Open Android Settings, choose HardBlock, enable access, then return. If access is revoked, usage tracking is interrupted.", color = Muted)
            Spacer(Modifier.height(12.dp))
            Button(onClick = { unavailable = !openSettings() }, modifier = Modifier.fillMaxWidth()) { Text(if (state.accessGranted) "Review Usage Access" else "Open Usage Access settings") }
            if (unavailable) Text("This device does not provide a Usage Access settings screen.", color = MaterialTheme.colorScheme.error)
            TextButton(onClick = refresh) { Text("Check permission again") }
        }
        item {
            Text("03 / Know the limits", color = Accent, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text("Standard Mode can ultimately be bypassed through Android system settings. Stronger enforcement will require a separate, voluntary Device Owner provisioning flow.", color = Muted)
            Spacer(Modifier.height(12.dp))
            Text(state.protection.detail, color = Muted)
            Spacer(Modifier.height(12.dp))
            Text("Accessibility protection observes the foreground app package and displays a blocking screen. It does not read screen content, typed text or passwords. Everything stays on this device. Enabling this service is voluntary and can be undone in Android Settings.", color = Muted)
            Button(onClick = { showConsent = true }, modifier = Modifier.fillMaxWidth()) { Text(if (ProtectionBridge.service != null) "Review Accessibility protection" else "Enable Accessibility protection") }
            if (android.os.Build.VERSION.SDK_INT >= 33) TextButton(onClick = { notifications.launch(android.Manifest.permission.POST_NOTIFICATIONS) }) { Text("Allow protection notifications") }
        }
        item {
            Text("EXTREME MODE", color = Accent, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text("Extreme Mode gives this app stronger device-management privileges. It requires special Android provisioning and cannot be enabled through a normal permission dialog.", color = Muted)
            Text("Current mode: ${state.protection.mode}. Device Owner is detected automatically. Standard Mode remains available when it is absent.", color = Muted)
            Spacer(Modifier.height(8.dp))
            Text("Developer/test setup: use a separate, unprovisioned test device with no accounts or other owner. Install the APK, then run the following on your computer. Do not erase your personal device for setup.", color = Muted)
            androidx.compose.foundation.text.selection.SelectionContainer { Text("adb shell dpm set-device-owner dev.sajancodes.hardblock/.admin.AppDeviceAdminReceiver", color = Accent, fontSize = 13.sp) }
            Text("Verify with adb shell dpm list-owners. Then reopen HardBlock. Device Owner uses Android package suspension. Usage Access and Accessibility remain required for timely daily-limit and schedule monitoring. This app never wipes or resets devices.", color = Muted)
            TextButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, "https://developer.android.com/work/dpc/dedicated-devices".toUri())) }.onFailure { unavailable = true } }) { Text("Official Android provisioning documentation") }
        }
        item {
            Text("THE COMMITMENT PRINCIPLE", fontSize = 11.sp, letterSpacing = 1.5.sp, color = Accent)
            Spacer(Modifier.height(8.dp))
            Text("Stricter immediately.\nWeaker only when eligible.", fontSize = 24.sp, lineHeight = 32.sp)
            Spacer(Modifier.height(12.dp))
            Text("Every activation requires explicit confirmation of restrictions, expiration, reset time and enforcement strength. Relaxation requests never apply automatically.", color = Muted)
        }
        item {
            if (state.acknowledged) Text("Limitations acknowledged", color = Accent)
            else OutlinedButton(onClick = acknowledge, modifier = Modifier.fillMaxWidth()) { Text("I understand the current limitations") }
        }
    }
    if (showConsent) AlertDialog(onDismissRequest = { showConsent = false }, title = { Text("Enable voluntary app blocking?") },
        text = { Text("HardBlock will receive window-change package names and cover restricted apps with a blocking screen. It cannot read screen content or typed text and does not send data anywhere. Standard Mode can be bypassed in Android Settings. Continue only if you consent.") },
        confirmButton = { TextButton(onClick = {
            showConsent = false
            try { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) } catch (_: android.content.ActivityNotFoundException) { unavailable = true }
        }) { Text("I consent · open Settings") } }, dismissButton = { TextButton(onClick = { showConsent = false }) { Text("Cancel") } })
}

internal fun duration(millis: Long): String {
    val seconds = millis.coerceAtLeast(0) / 1000
    return when {
        seconds >= 3600 -> "${seconds / 3600}h ${seconds % 3600 / 60}m"
        seconds >= 60 -> "${seconds / 60}m ${seconds % 60}s"
        else -> "${seconds}s"
    }
}

private fun formatInstant(millis: Long): String = DateTimeFormatter.ofPattern("EEE, HH:mm z")
    .format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
