package dev.sajancodes.hardblock.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.sajancodes.hardblock.MainActivity
import org.junit.Rule
import org.junit.Test

class DashboardSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun dashboardExplainsTrackingOnlyAndPermissionFlow() {
        compose.onNodeWithText("Make room for\nwhat matters.").assertIsDisplayed()
        compose.onNodeWithText("Protection").performClick()
        compose.onNodeWithText("02 / Usage Access").assertIsDisplayed()
        compose.onNodeWithText("Open Usage Access settings").assertIsDisplayed()
    }

    @Test fun applicationsAreSearchable() {
        compose.onNodeWithText("Applications").performClick()
        compose.onNodeWithText("Search apps or packages").performTextInput("a-package-that-does-not-exist")
        compose.waitUntil(15_000) { compose.onAllNodesWithText("No matching applications.").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("No matching applications.").assertIsDisplayed()
    }
}
