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
}
