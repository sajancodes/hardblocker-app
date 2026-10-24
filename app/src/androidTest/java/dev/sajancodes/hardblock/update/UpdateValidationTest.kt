package dev.sajancodes.hardblock.update

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File

class UpdateValidationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    @Test fun installedVersionCannotBeReinstalledAsAnUpdate() {
        val updater = GitHubUpdater(context)
        assertThrows(IllegalArgumentException::class.java) {
            updater.validateCandidate(File(context.applicationInfo.sourceDir))
        }
    }
    @Test fun corruptDownloadCannotReachInstaller() {
        val file = File(context.cacheDir, "invalid-update.apk")
        try {
            file.writeText("not an APK")
            assertThrows(IllegalArgumentException::class.java) { GitHubUpdater(context).validateCandidate(file) }
        } finally { file.delete() }
    }
}
