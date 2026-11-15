package dev.oni.dreaminstall.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateVersionTest {

    @Test
    fun comparesDottedVersions() {
        assertTrue(isNewer("1.4.0", "1.3.0"))
        assertTrue(isNewer("1.10", "1.9"))
        assertFalse(isNewer("1.3.0", "1.4.0"))
        assertFalse(isNewer("1.4", "1.4.0"))
    }

    @Test
    fun ignoresTheLeadingV() {
        assertEquals(0, compareVersions("v1.4.0", "1.4.0"))
        assertTrue(isNewer("V2.0.0", "v1.9.9"))
    }

    @Test
    fun missingSegmentsCountAsZero() {
        assertEquals(0, compareVersions("1.4", "1.4.0"))
        assertTrue(isNewer("1.4.1", "1.4"))
        assertFalse(isNewer("1.4", "1.4.1"))
    }
}
