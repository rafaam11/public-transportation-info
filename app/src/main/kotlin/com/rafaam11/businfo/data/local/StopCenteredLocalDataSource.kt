package com.rafaam11.businfo.data.local

import com.rafaam11.businfo.domain.FavoriteStop
import com.rafaam11.businfo.domain.FavoriteStopId
import com.rafaam11.businfo.domain.FavoriteRemovalSnapshot
import com.rafaam11.businfo.domain.StopArrivalSnapshot
import com.rafaam11.businfo.domain.StopCatalogItem
import com.rafaam11.businfo.domain.WidgetBinding
import kotlinx.coroutines.flow.Flow

interface StopCenteredLocalDataSource {
    fun observeFavoriteStops(): Flow<List<FavoriteStop>>
    suspend fun favoriteStop(id: FavoriteStopId): FavoriteStop?
    suspend fun favoriteStopByStopId(stopId: String): FavoriteStop?
    suspend fun favoriteStopCount(): Int
    suspend fun saveFavoriteStop(stop: FavoriteStop)
    suspend fun removeFavoriteStop(id: FavoriteStopId): FavoriteRemovalSnapshot?
    suspend fun restoreFavoriteStop(snapshot: FavoriteRemovalSnapshot)

    suspend fun replaceStopCatalog(stops: List<StopCatalogItem>)
    suspend fun stops(): List<StopCatalogItem>
    suspend fun searchStops(query: String, limit: Int): List<StopCatalogItem>

    fun observeStopArrival(stopId: String): Flow<StopArrivalSnapshot?>
    suspend fun stopArrival(stopId: String): StopArrivalSnapshot?
    suspend fun saveStopArrival(snapshot: StopArrivalSnapshot)

    fun observeWidgetBinding(appWidgetId: Int): Flow<WidgetBinding?>
    suspend fun widgetBinding(appWidgetId: Int): WidgetBinding?
    suspend fun saveWidgetBinding(binding: WidgetBinding)
    suspend fun deleteWidgetBinding(appWidgetId: Int)
}
