package com.rafaam11.businfo.probe

import com.google.gson.JsonElement

internal object SensitiveNamePolicy {
    private val sensitive = Regex("(?i)(service.?key|token|secret|vehicle.?no|plate)")
    private val daeguVehicleNumber = Regex("(?i)^vhcNo\\d*$")
    private val serviceKeyAlias = Regex("(?i)service[._ -]?key")

    fun isSensitive(name: String): Boolean =
        sensitive.containsMatchIn(name) || daeguVehicleNumber.matches(name)

    fun isServiceKeyAlias(name: String): Boolean = serviceKeyAlias.matches(name)
}

object JsonShapeReporter {
    fun render(root: JsonElement): String {
        val fields = sortedMapOf<String, String>()
        visit("$", root, null, fields)
        return fields.entries.joinToString("\n") { (path, value) -> "$path | $value" }
    }

    private fun visit(
        path: String,
        node: JsonElement,
        fieldName: String?,
        fields: MutableMap<String, String>,
    ) {
        if (fieldName != null && SensitiveNamePolicy.isSensitive(fieldName)) {
            fields.putIfAbsent(path, "${nodeType(node)} | [redacted]")
            return
        }
        when {
            node.isJsonObject -> node.asJsonObject.entrySet().forEach { (name, value) ->
                visit("$path.$name", value, name, fields)
            }
            node.isJsonArray -> node.asJsonArray.forEach { visit("$path[]", it, null, fields) }
            node.isJsonNull -> fields.putIfAbsent(path, "null | null")
            node.asJsonPrimitive.isBoolean -> fields.putIfAbsent(path, "boolean | ${node.asBoolean}")
            node.asJsonPrimitive.isNumber -> fields.putIfAbsent(path, "number | ${node.asNumber}")
            else -> {
                fields.putIfAbsent(path, "string | ${node.asString.take(80)}")
            }
        }
    }

    private fun nodeType(node: JsonElement): String = when {
        node.isJsonObject -> "object"
        node.isJsonArray -> "array"
        node.isJsonNull -> "null"
        node.asJsonPrimitive.isBoolean -> "boolean"
        node.asJsonPrimitive.isNumber -> "number"
        else -> "string"
    }
}
