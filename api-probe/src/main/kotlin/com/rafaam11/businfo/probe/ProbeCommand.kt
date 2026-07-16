package com.rafaam11.businfo.probe

import java.util.Collections

class ProbeCommand(endpoint: String, parameters: Map<String, String>) {
    val endpoint: String = endpoint
    val parameters: Map<String, String> = Collections.unmodifiableMap(LinkedHashMap(parameters))

    init {
        require(this.endpoint in allowed) { "Endpoint is not allowlisted" }
        this.parameters.forEach { (name, value) ->
            require(name.isNotBlank() && value.isNotBlank()) { "Parameter must have a nonblank name and value" }
            require(!SensitiveNamePolicy.isServiceKeyAlias(name)) {
                "Service key must come from ignored local.properties"
            }
        }
    }

    companion object {
        private val allowed = setOf("getBasic02", "getBs02", "getLink02", "getRealtime02", "getPos02")

        fun parse(args: Array<String>): ProbeCommand {
            require(args.isNotEmpty()) { "Endpoint is required" }
            val values = linkedMapOf<String, String>()
            var index = 1
            while (index < args.size) {
                require(args[index] == "--param" && index + 1 < args.size) { "Use --param name=value" }
                val pair = args[index + 1].split('=', limit = 2)
                require(pair.size == 2) { "Parameter must be name=value" }
                values[pair[0]] = pair[1]
                index += 2
            }
            return ProbeCommand(args.first(), values)
        }
    }
}
