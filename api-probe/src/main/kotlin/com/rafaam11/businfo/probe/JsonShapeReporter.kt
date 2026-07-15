package com.rafaam11.businfo.probe

import com.google.gson.JsonElement

object JsonShapeReporter {
    private val sensitive = Regex("(?i)(service.?key|token|secret|vehicle.?no|plate)")

    fun render(root: JsonElement): String {
        val fields = sortedMapOf<String, String>()
        visit("$", root, fields)
        return fields.entries.joinToString("\n") { (path, value) -> "$path | $value" }
    }

    private fun visit(path: String, node: JsonElement, fields: MutableMap<String, String>) {
        when {
            node.isJsonObject -> node.asJsonObject.entrySet().forEach { (name, value) ->
                visit("$path.$name", value, fields)
            }
            node.isJsonArray -> node.asJsonArray.forEach { visit("$path[]", it, fields) }
            node.isJsonNull -> fields.putIfAbsent(path, "null | null")
            node.asJsonPrimitive.isBoolean -> fields.putIfAbsent(path, "boolean | ${node.asBoolean}")
            node.asJsonPrimitive.isNumber -> fields.putIfAbsent(path, "number | ${node.asNumber}")
            else -> {
                val sample = if (sensitive.containsMatchIn(path.substringAfterLast('.'))) {
                    "[redacted]"
                } else {
                    node.asString.take(80)
                }
                fields.putIfAbsent(path, "string | $sample")
            }
        }
    }
}
