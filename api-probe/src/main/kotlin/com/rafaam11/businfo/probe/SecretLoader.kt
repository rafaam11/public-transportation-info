package com.rafaam11.businfo.probe

import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

object SecretLoader {
    fun projectDir(start: Path = Path.of(System.getProperty("user.dir")).toAbsolutePath()): Path {
        var current: Path? = start
        while (current != null) {
            if (Files.exists(current.resolve("settings.gradle.kts"))) return current
            current = current.parent
        }
        error("Project root containing settings.gradle.kts was not found")
    }

    fun daeguServiceKey(projectDir: Path = projectDir()): String {
        val file = projectDir.resolve("local.properties")
        require(Files.exists(file)) { "Create ignored local.properties with DAEGU_BUS_SERVICE_KEY" }
        val properties = Properties().apply {
            Files.newInputStream(file).use { load(it) }
        }
        val serviceKey = requireNotNull(properties.getProperty("DAEGU_BUS_SERVICE_KEY")) {
            "DAEGU_BUS_SERVICE_KEY is missing"
        }.trim()
        require(serviceKey.isNotEmpty()) { "DAEGU_BUS_SERVICE_KEY is blank" }
        return serviceKey
    }
}
