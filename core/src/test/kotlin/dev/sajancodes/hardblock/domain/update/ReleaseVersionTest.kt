package dev.sajancodes.hardblock.domain.update

import org.junit.Assert.*
import org.junit.Test

class ReleaseVersionTest {
    private fun v(s: String) = requireNotNull(ReleaseVersion.parse(s))
    @Test fun numericVersionsDoNotSortLexically() = assertTrue(v("v0.10.0") > v("0.9.9"))
    @Test fun olderOrSameReleaseNeverOffersUpgrade() { assertFalse(v("0.2.0") > v("0.2.0")); assertFalse(v("0.1.9") > v("0.2.0")) }
    @Test fun stableReleaseFollowsItsPrerelease() = assertTrue(v("1.0.0") > v("1.0.0-beta.2"))
    @Test fun prereleaseNumericIdentifiersSortNumerically() { assertTrue(v("1.0.0-beta.10") > v("1.0.0-beta.2")); assertTrue(v("1.0.0-99999999999999999999999999") > v("1.0.0-2")) }
    @Test fun buildMetadataDoesNotChangeUpgradeOrder() = assertEquals(v("1.0.0+build1"), v("v1.0.0+build2"))
    @Test fun malformedOrOverflowingTagsAreRejected() { listOf("main", "v1.2", "1.2.3/../apk", "01.2.3", "1.2.3-beta.01", "1.2.3+build..1", "999999999999999.0.0").forEach { assertNull(ReleaseVersion.parse(it)) } }
}
