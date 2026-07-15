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

    @Test
    fun rejectsUnknownEndpointThroughDirectConstruction() {
        assertThrows(IllegalArgumentException::class.java) {
            ProbeCommand("../anythingElse", emptyMap())
        }
    }

    @Test
    fun rejectsBlankParameterValueThroughDirectConstruction() {
        assertThrows(IllegalArgumentException::class.java) {
            ProbeCommand("getPos02", mapOf("routeId" to "   "))
        }
    }

    @Test
    fun rejectsServiceKeyAliasCaseInsensitively() {
        assertThrows(IllegalArgumentException::class.java) {
            ProbeCommand.parse(arrayOf("getPos02", "--param", "Service-Key=not-allowed"))
        }
    }

    @Test
    fun snapshotsValidatedParametersAgainstCallerMutation() {
        val parameters = mutableMapOf("routeId" to "123")
        val command = ProbeCommand("getPos02", parameters)

        parameters["token"] = ""

        assertEquals(mapOf("routeId" to "123"), command.parameters)
    }
}
