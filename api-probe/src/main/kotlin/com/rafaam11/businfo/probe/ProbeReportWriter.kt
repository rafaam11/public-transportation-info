package com.rafaam11.businfo.probe

import java.nio.file.Files
import java.nio.file.Path

object ProbeReportWriter {
    fun write(
        command: ProbeCommand,
        response: ProbeResponse,
        shape: String,
        projectDir: Path,
    ): Path {
        val root = projectDir.resolve(".local/api-probe")
        val raw = root.resolve("raw/${command.endpoint}.json")
        val report = root.resolve("reports/${command.endpoint}.md")
        Files.createDirectories(raw.parent)
        Files.createDirectories(report.parent)
        Files.writeString(raw, response.body)
        Files.writeString(
            report,
            "# ${command.endpoint}\n\n- HTTP: ${response.statusCode}\n" +
                "- Request: `${response.requestSummary}`\n\n```text\n$shape\n```\n",
        )
        return report
    }
}
