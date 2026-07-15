package com.rafaam11.businfo.probe

import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files

class SecretLoaderTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun rejectsAbsentLocalProperties() {
        assertThrows(IllegalArgumentException::class.java) {
            SecretLoader.daeguServiceKey(temporaryFolder.root.toPath())
        }
    }

    @Test
    fun rejectsBlankServiceKeyProperty() {
        Files.writeString(
            temporaryFolder.root.toPath().resolve("local.properties"),
            "DAEGU_BUS_SERVICE_KEY=   ",
        )

        assertThrows(IllegalArgumentException::class.java) {
            SecretLoader.daeguServiceKey(temporaryFolder.root.toPath())
        }
    }
}
