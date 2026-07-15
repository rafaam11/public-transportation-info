package com.rafaam11.businfo.probe

import com.google.gson.JsonParser

fun main(args: Array<String>) {
    val command = ProbeCommand.parse(args)
    val projectDir = SecretLoader.projectDir()
    val response = DaeguApiProbe(serviceKey = SecretLoader.daeguServiceKey(projectDir)).execute(command)
    require(response.statusCode in 200..299) { "HTTP ${response.statusCode}" }
    val shape = JsonShapeReporter.render(JsonParser.parseString(response.body))
    val report = ProbeReportWriter.write(command, response, shape, projectDir)
    println("Wrote sanitized report: $report")
}
