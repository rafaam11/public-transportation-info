package com.rafaam11.businfo.ui.map

data class RoutePalette(val bodyColor: Int, val textColor: Int)

object RoutePaletteResolver {
    private val white = 0xFFFFFFFF.toInt()
    private val black = 0xFF131313.toInt()

    fun resolve(routeTypeCode: String?): RoutePalette = when (routeTypeCode) {
        "1" -> RoutePalette(0xFFFF4917.toInt(), white)
        "2" -> RoutePalette(0xFF5BD338.toInt(), black)
        "3" -> RoutePalette(0xFF2C78CF.toInt(), white)
        "4" -> RoutePalette(0xFFFFC000.toInt(), black)
        "5", "6", "7" -> RoutePalette(0xFF4330D6.toInt(), white)
        else -> RoutePalette(0xFF306FD9.toInt(), white)
    }
}
