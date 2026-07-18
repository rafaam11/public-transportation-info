package com.rafaam11.businfo.data

import com.rafaam11.businfo.data.local.StopCenteredLocalDataSource
import com.rafaam11.businfo.domain.FavoriteStop
import com.rafaam11.businfo.domain.FavoriteStopId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class SaveFavoriteResult { Saved, AlreadyExists, LimitReached }

interface FavoriteStopRepository {
    fun observeFavorites(): Flow<List<FavoriteStop>>
    suspend fun favorite(id: FavoriteStopId): FavoriteStop?
    suspend fun save(stop: FavoriteStop): SaveFavoriteResult
    suspend fun delete(id: FavoriteStopId)
}

class DefaultFavoriteStopRepository(
    private val local: StopCenteredLocalDataSource,
    private val maximumFavorites: Int = 20,
) : FavoriteStopRepository {
    private val writeMutex = Mutex()

    override fun observeFavorites(): Flow<List<FavoriteStop>> = local.observeFavoriteStops()
    override suspend fun favorite(id: FavoriteStopId): FavoriteStop? = local.favoriteStop(id)

    override suspend fun save(stop: FavoriteStop): SaveFavoriteResult = writeMutex.withLock {
        val existing = local.favoriteStopByStopId(stop.stopId)
        if (existing != null && existing.id != stop.id) return@withLock SaveFavoriteResult.AlreadyExists
        if (existing == null && local.favoriteStopCount() >= maximumFavorites) {
            return@withLock SaveFavoriteResult.LimitReached
        }
        local.saveFavoriteStop(stop)
        SaveFavoriteResult.Saved
    }

    override suspend fun delete(id: FavoriteStopId) = writeMutex.withLock { local.deleteFavoriteStop(id) }
}
