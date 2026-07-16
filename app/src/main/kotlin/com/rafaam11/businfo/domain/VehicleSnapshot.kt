package com.rafaam11.businfo.domain

import java.time.Instant

data class VehicleSnapshot(
    val routeId: String,
    val routeNo: String,
    val moveDirection: String,
    val stopId: String?,
    val stopSequence: Int?,
    val latitude: Double,
    val longitude: Double,
    val arrivalState: String?,
    val busTypeCode2: String?,
    val busTypeCode3: String?,
)

@ConsistentCopyVisibility
data class VehicleBatch private constructor(val vehicles: List<VehicleSnapshot>, val fetchedAt: Instant) {
    companion object {
        fun from(unsorted: Collection<VehicleSnapshot>, fetchedAt: Instant) = VehicleBatch(
            unsorted.sortedWith(compareBy<VehicleSnapshot>({ it.moveDirection }, { it.stopSequence ?: Int.MAX_VALUE })),
            fetchedAt,
        )
    }
}
