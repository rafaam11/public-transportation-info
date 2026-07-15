package com.rafaam11.businfo

import org.junit.Assert.assertEquals
import org.junit.Test

class FoundationContractTest {
    @Test
    fun productionPackageIsStable() {
        assertEquals("com.rafaam11.businfo", BuildConfig.APPLICATION_ID)
    }
}
