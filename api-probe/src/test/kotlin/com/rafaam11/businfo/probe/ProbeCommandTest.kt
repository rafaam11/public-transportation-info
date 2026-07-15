package com.rafaam11.businfo.probe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ProbeCommandTest {
    @Test
    fun parsesAllowlistedEndpointAndParameters() {
        val command = ProbeCommand.parse(arrayOf("getPos02", "--param", "routeId=123"))

        assertEquals("getPos02", command.endpoint)
        assertEquals(mapOf("routeId" to "123"), command.parameters)
    }

    @Test
    fun rejectsUnknownEndpoint() {
        assertThrows(IllegalArgumentException::class.java) {
            ProbeCommand.parse(arrayOf("anythingElse"))
        }
    }
}
