package com.rafaam11.businfo

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherIconContractTest {
    private val sourceRoot = File(System.getProperty("user.dir").orEmpty()).let { cwd ->
        if (File(cwd, "src/main").isDirectory) cwd else File(cwd, "app")
    }

    @Test
    fun manifestAndAdaptiveIconResourcesExposeColorAndMonochromeBusIdentity() {
        val manifest = File(sourceRoot, "src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android:icon=\"@mipmap/ic_launcher\""))
        assertTrue(manifest.contains("android:roundIcon=\"@mipmap/ic_launcher_round\""))

        val v26Icon = File(sourceRoot, "src/main/res/mipmap-anydpi-v26/ic_launcher.xml")
        val v26RoundIcon = File(sourceRoot, "src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml")
        val v33Icon = File(sourceRoot, "src/main/res/mipmap-anydpi-v33/ic_launcher.xml")
        val v33RoundIcon = File(sourceRoot, "src/main/res/mipmap-anydpi-v33/ic_launcher_round.xml")

        listOf(v26Icon, v26RoundIcon).forEach { icon ->
            assertTrue(icon.isFile)
            val xml = icon.readText()
            assertTrue(xml.contains("android:drawable=\"@drawable/ic_launcher_background\""))
            assertTrue(xml.contains("android:drawable=\"@drawable/ic_launcher_foreground\""))
        }
        listOf(v33Icon, v33RoundIcon).forEach { icon ->
            assertTrue(icon.isFile)
            val xml = icon.readText()
            assertTrue(xml.contains("android:drawable=\"@drawable/ic_launcher_background\""))
            assertTrue(xml.contains("android:drawable=\"@drawable/ic_launcher_foreground\""))
            assertTrue(xml.contains("android:drawable=\"@drawable/ic_launcher_monochrome\""))
        }

        val foreground = File(sourceRoot, "src/main/res/drawable/ic_launcher_foreground.xml")
        assertTrue(foreground.isFile)
        val foregroundXml = foreground.readText()
        assertTrue(foregroundXml.contains("#FFFF4917"))
        assertTrue(foregroundXml.contains("#FF5BD338"))
        assertTrue(foregroundXml.contains("#FFFFC000"))
    }
}
