package com.rafaam11.businfo.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionTest {
    @Test fun `parses version with v prefix`() {
        assertEquals(AppVersion(0, 6, 0), AppVersion.parse("v0.6.0"))
    }

    @Test fun `parses version without prefix`() {
        assertEquals(AppVersion(0, 5, 0), AppVersion.parse("0.5.0"))
    }

    @Test fun `rejects malformed version`() {
        assertNull(AppVersion.parse("not-a-version"))
        assertNull(AppVersion.parse("v1.2"))
        assertNull(AppVersion.parse(""))
    }

    @Test fun `compares by major then minor then patch`() {
        assertTrue(AppVersion(1, 0, 0) > AppVersion(0, 9, 9))
        assertTrue(AppVersion(0, 6, 0) > AppVersion(0, 5, 9))
        assertTrue(AppVersion(0, 5, 1) > AppVersion(0, 5, 0))
        assertEquals(AppVersion(0, 5, 0), AppVersion(0, 5, 0))
    }
}
