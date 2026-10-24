package dev.sajancodes.hardblock.enforcement

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.telecom.TelecomManager
import android.view.accessibility.AccessibilityManager
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.sajancodes.hardblock.data.database.HardBlockDatabase
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.net.toUri

@Singleton
class ProtectedPackagesResolver @Inject constructor(@param:ApplicationContext private val context: Context, private val db: HardBlockDatabase) {
    @Suppress("DEPRECATION")
    suspend fun resolve(): Set<String> {
        val pm = context.packageManager
        val result = mutableSetOf(context.packageName, "android", "com.android.systemui", "com.android.settings",
            "com.android.permissioncontroller", "com.google.android.permissioncontroller", "com.android.packageinstaller",
            "com.google.android.packageinstaller", "com.android.emergency", "com.google.android.dialer", "com.android.phone",
            "com.android.server.telecom", "com.android.dialer", "com.google.android.apps.authenticator2", "com.azure.authenticator",
            "com.authy.authy", "org.fedorahosted.freeotp")
        listOf(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), Intent(android.provider.Settings.ACTION_SETTINGS),
            Intent(Intent.ACTION_DIAL, "tel:".toUri()), Intent(Intent.ACTION_VIEW).setDataAndType("content://dev.sajancodes.hardblock/unused".toUri(), "application/vnd.android.package-archive")
        ).forEach { intent -> pm.queryIntentActivities(intent, 0).forEach { result += it.activityInfo.packageName } }
        context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage?.let(result::add)
        context.getSystemService(AccessibilityManager::class.java).getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .forEach { result += it.resolveInfo.serviceInfo.packageName }
        result += db.policyDao().protectedApps()
        return result
    }
    suspend fun validate(packages: Set<String>, requireLaunchable: Boolean = false) {
        require(packages.intersect(resolve()).isEmpty()) { "Essential or protected applications cannot be blocked" }
        if (requireLaunchable) require(packages.all { context.packageManager.getLaunchIntentForPackage(it) != null }) { "Select an installed launchable application" }
    }
}
