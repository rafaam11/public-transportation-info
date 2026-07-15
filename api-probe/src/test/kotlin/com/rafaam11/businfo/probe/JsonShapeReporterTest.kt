package com.rafaam11.businfo.probe

import com.google.gson.JsonParser
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonShapeReporterTest {
    @Test
    fun reportsPathsAndMasksVehicleNumbers() {
        val json = JsonParser.parseString("""{"items":[{"lat":35.8,"vehicleNo":"1234"}]}""")

        val report = JsonShapeReporter.render(json)

        assertTrue(report.contains("$.items[].lat | number | 35.8"))
        assertTrue(report.contains("$.items[].vehicleNo | string | [redacted]"))
        assertFalse(report.contains("1234"))
    }

    @Test
    fun masksSensitiveNumericFields() {
        val json = JsonParser.parseString("""{"vehicleNo":1234}""")

        val report = JsonShapeReporter.render(json)

        assertTrue(report.contains("$.vehicleNo | number | [redacted]"))
        assertFalse(report.contains("1234"))
    }

    @Test
    fun masksSensitiveBooleanFields() {
        val json = JsonParser.parseString("""{"secret":true}""")

        val report = JsonShapeReporter.render(json)

        assertTrue(report.contains("$.secret | boolean | [redacted]"))
        assertFalse(report.contains("true"))
    }

    @Test
    fun masksSensitiveNullFields() {
        val json = JsonParser.parseString("""{"token":null}""")

        val report = JsonShapeReporter.render(json)

        assertTrue(report.contains("$.token | null | [redacted]"))
    }

    @Test
    fun doesNotTraverseSensitiveContainers() {
        val json = JsonParser.parseString("""{"secret":{"nested":"do-not-render"}}""")

        val report = JsonShapeReporter.render(json)

        assertTrue(report.contains("$.secret | object | [redacted]"))
        assertFalse(report.contains("do-not-render"))
        assertFalse(report.contains("nested"))
    }
}
