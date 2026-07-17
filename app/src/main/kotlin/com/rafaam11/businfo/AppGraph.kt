package com.rafaam11.businfo

import android.content.Context
import androidx.room.Room
import com.rafaam11.businfo.data.BusRepository
import com.rafaam11.businfo.data.DashboardRepository
import com.rafaam11.businfo.data.RouteGeometryRepository
import com.rafaam11.businfo.data.VehiclePositionRepository
import com.rafaam11.businfo.data.credential.SharedPreferencesCredentialStore
import com.rafaam11.businfo.data.local.BusDatabase
import com.rafaam11.businfo.data.local.MIGRATION_1_2
import com.rafaam11.businfo.data.local.MIGRATION_2_3
import com.rafaam11.businfo.data.local.RoomBusLocalDataSource
import com.rafaam11.businfo.data.remote.OkHttpDaeguBusRemoteDataSource
import com.rafaam11.businfo.data.remote.AccubusPreciseRemoteDataSource
import com.rafaam11.businfo.ui.map.MapAuthMonitor
import com.rafaam11.businfo.widget.CommuteWidgetRepository
import com.rafaam11.businfo.widget.CommuteWidgetUpdateNotifier
import com.rafaam11.businfo.widget.WidgetPreferenceStore
import java.time.Clock
import java.util.concurrent.TimeUnit
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

class AppGraph private constructor(context: Context) {
    private val clock = Clock.systemUTC()
    private val httpClient = OkHttpClient.Builder().callTimeout(10, TimeUnit.SECONDS).build()
    private val credentials = SharedPreferencesCredentialStore(context.applicationContext)
    private val remote = OkHttpDaeguBusRemoteDataSource(
        client = httpClient,
        baseUrl = "https://apis.data.go.kr/6270000/dbmsapi02/".toHttpUrl(),
        clock = clock,
    )
    val preciseVehiclePositionDataSource = AccubusPreciseRemoteDataSource(
        client = httpClient,
        baseUrl = "https://accubus.daegu.go.kr:8095/dbms_web_api/".toHttpUrl(),
        clock = clock,
    )

    private val database = Room.databaseBuilder(context.applicationContext, BusDatabase::class.java, "bus-info.db")
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
        .build()
    private val local = RoomBusLocalDataSource(database.dao())
    val widgetPreferences = WidgetPreferenceStore(context.applicationContext)

    val credentialRepository = BusRepository(credentials, remote)
    val dashboardRepository = DashboardRepository(
        credentials,
        remote,
        local,
        clock,
        CommuteWidgetUpdateNotifier(context.applicationContext),
    )
    val commuteWidgetRepository = CommuteWidgetRepository(
        dashboardRepository,
        widgetPreferences,
        clock,
    )
    val routeGeometryRepository = RouteGeometryRepository(credentials, remote, local, clock)
    val vehiclePositionRepository = VehiclePositionRepository(credentials, remote, local, clock)
    val mapAuthMonitor = MapAuthMonitor()

    companion object {
        @Volatile
        private var instance: AppGraph? = null

        fun get(context: Context): AppGraph = instance ?: synchronized(this) {
            instance ?: AppGraph(context.applicationContext).also { instance = it }
        }
    }
}
