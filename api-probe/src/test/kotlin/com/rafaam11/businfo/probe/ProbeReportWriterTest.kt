package com.rafaam11.businfo.probe

import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProbeReportWriterTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun allowedEndpointReportStaysInsideProbeReportsDirectory() {
        val projectDir = temporaryFolder.root.toPath()
        val report = ProbeReportWriter.write(
            ProbeCommand.parse(arrayOf("getPos02")),
            ProbeResponse(200, "{}", "sanitized"),
            "$.items | array | []",
            projectDir,
        )

        val reportsRoot = projectDir.resolve(".local/api-probe/reports").toAbsolutePath().normalize()
        assertTrue(report.toAbsolutePath().normalize().startsWith(reportsRoot))
    }
}
