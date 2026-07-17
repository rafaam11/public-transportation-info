package com.rafaam11.businfo.ui.map

import org.junit.Assert.assertEquals
import org.junit.Test

class RoutePaletteTest {
    @Test
    fun officialTypesUseWebsiteColors() {
        assertEquals(RoutePalette(0xFFFF4917.toInt(), 0xFFFFFFFF.toInt()), RoutePaletteResolver.resolve("1"))
        assertEquals(RoutePalette(0xFF5BD338.toInt(), 0xFF131313.toInt()), RoutePaletteResolver.resolve("2"))
        assertEquals(RoutePalette(0xFF2C78CF.toInt(), 0xFFFFFFFF.toInt()), RoutePaletteResolver.resolve("3"))
        assertEquals(RoutePalette(0xFFFFC000.toInt(), 0xFF131313.toInt()), RoutePaletteResolver.resolve("4"))
        assertEquals(RoutePalette(0xFF4330D6.toInt(), 0xFFFFFFFF.toInt()), RoutePaletteResolver.resolve("5"))
        assertEquals(RoutePalette(0xFF4330D6.toInt(), 0xFFFFFFFF.toInt()), RoutePaletteResolver.resolve("6"))
        assertEquals(RoutePalette(0xFF4330D6.toInt(), 0xFFFFFFFF.toInt()), RoutePaletteResolver.resolve("7"))
    }

    @Test
    fun unknownTypeUsesDaeguBlue() {
        val fallback = RoutePalette(0xFF306FD9.toInt(), 0xFFFFFFFF.toInt())

        assertEquals(fallback, RoutePaletteResolver.resolve("G"))
        assertEquals(fallback, RoutePaletteResolver.resolve(null))
    }
}
