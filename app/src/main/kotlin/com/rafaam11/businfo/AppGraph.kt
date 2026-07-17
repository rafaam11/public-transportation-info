package com.rafaam11.businfo

import android.content.Context
import androidx.room.Room
import com.rafaam11.businfo.data.BusRepository
import com.rafaam11.businfo.data.DashboardRepository
import com.rafaam11.businfo.data.credential.SharedPreferencesCredentialStore
import com.rafaam11.businfo.data.local.BusDatabase
import com.rafaam11.businfo.data.local.MIGRATION_1_2
import com.rafaam11.businfo.data.local.RoomBusLocalDataSource
import com.rafaam11.businfo.data.remote.OkHttpDaeguBusRemoteDataSource
import java.time.Clock
import java.util.concurrent.TimeUnit
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

class AppGraph(context: Context) {
    private val credentials = SharedPreferencesCredentialStore(context.applicationContext)
    private val remote = OkHttpDaeguBusRemoteDataSource(
        client = OkHttpClient.Builder().callTimeout(10, TimeUnit.SECONDS).build(),
        baseUrl = "https://apis.data.go.kr/6270000/dbmsapi02/".toHttpUrl(),
        clock = Clock.systemUTC(),
    )

    private val database = Room.databaseBuilder(context.applicationContext, BusDatabase::class.java, "bus-info.db")
        .addMigrations(MIGRATION_1_2)
        .build()
    private val local = RoomBusLocalDataSource(database.dao())

    val credentialRepository = BusRepository(credentials, remote)
    val dashboardRepository = DashboardRepository(credentials, remote, local, Clock.systemUTC())
}
