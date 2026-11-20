package ru.mtproxymanager

import android.content.Context
import androidx.room.Room
import okhttp3.OkHttpClient
import ru.mtproxymanager.data.AppDatabase
import ru.mtproxymanager.data.MIGRATION_1_2
import ru.mtproxymanager.data.MIGRATION_2_3
import ru.mtproxymanager.data.ProxyRepository
import ru.mtproxymanager.data.SettingsStore
import ru.mtproxymanager.data.net.ProxyChecker
import ru.mtproxymanager.data.net.SourceFetcher
import java.util.concurrent.TimeUnit

class AppContainer(context: Context) {

    private val database: AppDatabase = Room.databaseBuilder(context, AppDatabase::class.java, "proxies.db")
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
        .build()

    val settings = SettingsStore(context)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .build()

    val repository = ProxyRepository(
        dao = database.proxyDao(),
        fetcher = SourceFetcher(httpClient),
        checker = ProxyChecker(),
        settings = settings,
    )
}
