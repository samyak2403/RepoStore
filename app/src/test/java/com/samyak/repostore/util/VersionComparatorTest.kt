package com.samyak.repostore.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionComparatorTest {

    @Test
    fun isNewerVersion_withReleaseSuffix_detectsProperPrecedence() {
        // Same semantic version with -release suffix should be treated as equal (not newer)
        assertFalse(VersionComparator.isNewerVersion("1.2.0", "v1.2.0-release"))
        assertFalse(VersionComparator.isNewerVersion("v1.2.0-release", "1.2.0"))

        // Incremented version with -release is newer
        assertTrue(VersionComparator.isNewerVersion("1.2.0", "v1.2.1-release"))
        assertFalse(VersionComparator.isNewerVersion("1.2.1", "v1.2.0-release"))
    }

    @Test
    fun isNewerVersion_withBuildMetadata_stripsBuildInfo() {
        // Build metadata (+build4) does not change semantic precedence
        assertFalse(VersionComparator.isNewerVersion("1.2.0", "v1.2.0+build4"))
        assertFalse(VersionComparator.isNewerVersion("1.2.0+build4", "1.2.0"))

        // But higher base version with build metadata is newer
        assertTrue(VersionComparator.isNewerVersion("1.1.0", "v1.2.0+build4"))
        assertFalse(VersionComparator.isNewerVersion("1.2.0+build4", "1.1.0"))
    }

    @Test
    fun isNewerVersion_withFlavorSuffixes_treatedAsBaseVersion() {
        // -fdroid, -universal, -arm64-v8a, -debug are packaging flavors, not pre-releases
        assertFalse(VersionComparator.isNewerVersion("1.2.0", "v1.2.0-fdroid"))
        assertFalse(VersionComparator.isNewerVersion("1.2.0", "v1.2.0-universal"))
        assertFalse(VersionComparator.isNewerVersion("1.2.0", "v1.2.0-arm64-v8a"))
        assertFalse(VersionComparator.isNewerVersion("1.2.0", "v1.2.0-debug"))

        // Higher version with flavor suffix is newer
        assertTrue(VersionComparator.isNewerVersion("1.2.0", "v1.3.0-fdroid"))
        assertTrue(VersionComparator.isNewerVersion("1.2.0", "v1.2.1-universal"))
    }

    @Test
    fun isNewerVersion_withPreReleaseSuffixes_distinguishesFromStable() {
        // Stable release outranks pre-release of the same core version
        assertTrue(VersionComparator.isNewerVersion("1.2.0-beta1", "1.2.0"))
        assertTrue(VersionComparator.isNewerVersion("1.2.0-rc1", "1.2.0"))
        assertFalse(VersionComparator.isNewerVersion("1.2.0", "1.2.0-beta1"))

        // Pre-release progression
        assertTrue(VersionComparator.isNewerVersion("1.2.0-beta1", "1.2.0-beta2"))
        assertTrue(VersionComparator.isNewerVersion("1.2.0-alpha", "1.2.0-beta"))
        assertTrue(VersionComparator.isNewerVersion("1.2.0-beta", "1.2.0-rc1"))
    }

    @Test
    fun isNewerVersion_withReleasePrefix() {
        assertTrue(VersionComparator.isNewerVersion("1.1.9", "release-1.2.0"))
        assertFalse(VersionComparator.isNewerVersion("1.2.0", "release-1.2.0"))
    }
}

