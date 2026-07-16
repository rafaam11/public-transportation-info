package com.rafaam11.businfo

import android.content.Context
import com.rafaam11.businfo.data.BusRepository
import com.rafaam11.businfo.data.credential.SharedPreferencesCredentialStore
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

    val repository = BusRepository(credentials, remote, Clock.systemUTC())
}
