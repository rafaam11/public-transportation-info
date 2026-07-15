package com.rafaam11.businfo.probe

data class ProbeCommand(val endpoint: String, val parameters: Map<String, String>) {
    companion object {
        private val allowed = setOf("getBasic02", "getBs02", "getLink02", "getRealtime02", "getPos02")

        fun parse(args: Array<String>): ProbeCommand {
            require(args.isNotEmpty()) { "Endpoint is required" }
            require(args.first() in allowed) { "Endpoint is not allowlisted" }
            val values = linkedMapOf<String, String>()
            var index = 1
            while (index < args.size) {
                require(args[index] == "--param" && index + 1 < args.size) { "Use --param name=value" }
                val pair = args[index + 1].split('=', limit = 2)
                require(pair.size == 2 && pair[0].isNotBlank()) { "Parameter must be name=value" }
                require(pair[0] != "serviceKey") { "Service key must come from ignored local.properties" }
                values[pair[0]] = pair[1]
                index += 2
            }
            return ProbeCommand(args.first(), values)
        }
    }
}
