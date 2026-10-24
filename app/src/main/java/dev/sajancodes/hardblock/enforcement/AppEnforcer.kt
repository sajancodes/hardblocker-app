package dev.sajancodes.hardblock.enforcement

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.sajancodes.hardblock.admin.AppDeviceAdminReceiver
import dev.sajancodes.hardblock.domain.policy.BlockReason
import dev.sajancodes.hardblock.service.ProtectionService
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import java.lang.ref.WeakReference

enum class EnforcementMode { STANDARD, DEVICE_OWNER }
interface AppEnforcer {
    suspend fun block(packageName: String)
    suspend fun unblock(packageName: String)
    suspend fun isBlocked(packageName: String): Boolean
}
object ProtectionBridge {
    val blocks = ConcurrentHashMap<String, BlockReason>()
    @Volatile private var reference = WeakReference<ProtectionService>(null)
    var service: ProtectionService?
        get() = reference.get()
        set(value) { reference = WeakReference(value) }
}
class StandardEnforcer @Inject constructor() : AppEnforcer {
    override suspend fun block(packageName: String) { check(ProtectionBridge.service != null) { "Accessibility protection is disconnected" } }
    override suspend fun unblock(packageName: String) { ProtectionBridge.blocks.remove(packageName) }
    override suspend fun isBlocked(packageName: String) = ProtectionBridge.service != null && ProtectionBridge.blocks.containsKey(packageName)
}
class DeviceOwnerEnforcer @Inject constructor(@param:ApplicationContext private val context: Context) : AppEnforcer {
    private val manager get() = context.getSystemService(DevicePolicyManager::class.java)
    private val admin get() = ComponentName(context, AppDeviceAdminReceiver::class.java)
    fun isOwner() = manager.isDeviceOwnerApp(context.packageName)
    fun protectCommitments(active: Boolean) {
        if (!isOwner()) return
        manager.setUninstallBlocked(admin, context.packageName, active)
        if (active) manager.addUserRestriction(admin, android.os.UserManager.DISALLOW_CONFIG_DATE_TIME)
        else manager.clearUserRestriction(admin, android.os.UserManager.DISALLOW_CONFIG_DATE_TIME)
    }
    override suspend fun block(packageName: String) {
        check(isOwner()) { "Device Owner is unavailable" }
        if (!installed(packageName)) return // Retain the policy for a later reinstall.
        check(manager.setPackagesSuspended(admin, arrayOf(packageName), true).isEmpty()) { "Android refused package suspension" }
    }
    override suspend fun unblock(packageName: String) {
        check(isOwner()) { "Device Owner is unavailable" }
        if (!installed(packageName)) return
        check(manager.setPackagesSuspended(admin, arrayOf(packageName), false).isEmpty()) { "Android refused package restoration" }
    }
    override suspend fun isBlocked(packageName: String): Boolean = isOwner() && manager.isPackageSuspended(admin, packageName)
    private fun installed(pkg: String): Boolean = try { context.packageManager.getApplicationInfo(pkg, 0); true }
        catch (_: android.content.pm.PackageManager.NameNotFoundException) { false }
}
