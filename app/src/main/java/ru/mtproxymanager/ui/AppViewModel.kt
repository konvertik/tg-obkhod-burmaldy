package ru.mtproxymanager.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import ru.mtproxymanager.MtProxyApplication
import ru.mtproxymanager.data.ProxyEntity
import ru.mtproxymanager.data.ProxyFormatException
import ru.mtproxymanager.data.ProxySelector
import ru.mtproxymanager.data.ProxyStatus
import ru.mtproxymanager.data.ThemeMode
import ru.mtproxymanager.data.UNKNOWN_COUNTRY
import ru.mtproxymanager.data.net.SourceResult
import ru.mtproxymanager.telegram.OpenResult
import ru.mtproxymanager.telegram.TelegramLauncher
import ru.mtproxymanager.work.BackgroundWork

enum class SortMode(val label: String) {
    PING("По пингу"),
    AVAILABILITY("По доступности"),
    COUNTRY("По стране"),
}

data class AppUiState(
    val proxies: List<ProxyEntity> = emptyList(),
    val sortMode: SortMode = SortMode.PING,
    val checkingIds: Set<String> = emptySet(),
    val selectedId: String? = null,
    val isRefreshing: Boolean = false,
    val lastReport: List<SourceResult> = emptyList(),
    val lastCheckedAt: Long = 0L,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val sources: List<String> = emptyList(),
    val refreshHours: Int = 6,
    val preferFakeTls: Boolean = true,
    val lastRefreshAt: Long = 0L,
)

private data class TransientState(
    val sort: SortMode,
    val checking: Set<String>,
    val selected: String?,
    val refreshing: Boolean,
    val report: List<SourceResult>,
)

private data class Prefs(
    val theme: ThemeMode,
    val sources: List<String>,
    val hours: Int,
    val preferFakeTls: Boolean,
    val lastRefreshAt: Long,
)

private const val STALE_CHECK_MS = 5 * 60 * 1000L
private const val HOUR_MS = 60 * 60 * 1000L

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as MtProxyApplication).container
    private val repository = container.repository
    private val settings = container.settings

    private val sortMode = MutableStateFlow(SortMode.PING)
    private val checkingIds = MutableStateFlow<Set<String>>(emptySet())
    private val selectedId = MutableStateFlow<String?>(null)
    private val isRefreshing = MutableStateFlow(false)
    private val lastReport = MutableStateFlow<List<SourceResult>>(emptyList())
    private val messageFlow = MutableSharedFlow<String>(extraBufferCapacity = 8)

    val messages: SharedFlow<String> = messageFlow.asSharedFlow()

    private val transient = combine(sortMode, checkingIds, selectedId, isRefreshing, lastReport) { sort, checking, selected, refreshing, report ->
        TransientState(sort, checking, selected, refreshing, report)
    }

    private val prefs = combine(
        settings.theme, settings.sources, settings.refreshHours, settings.preferFakeTls, settings.lastRefreshAt,
    ) { theme, sources, hours, fakeTls, lastRefresh ->
        Prefs(theme, sources, hours, fakeTls, lastRefresh)
    }

    val uiState: StateFlow<AppUiState> = combine(repository.observeProxies(), transient, prefs) { proxies, t, p ->
        AppUiState(
            proxies = sortProxies(proxies, t.sort),
            sortMode = t.sort,
            checkingIds = t.checking,
            selectedId = t.selected,
            isRefreshing = t.refreshing,
            lastReport = t.report,
            lastCheckedAt = proxies.maxOfOrNull { it.checkedAt } ?: 0L,
            theme = p.theme,
            sources = p.sources,
            refreshHours = p.hours,
            preferFakeTls = p.preferFakeTls,
            lastRefreshAt = p.lastRefreshAt,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppUiState())

    fun onAppResumed() {
        viewModelScope.launch {
            val list = repository.snapshot()
            val now = System.currentTimeMillis()
            val refreshIntervalMs = settings.refreshHours.first() * HOUR_MS
            val lastRefresh = settings.lastRefreshAt.first()
            val lastCheck = list.maxOfOrNull { it.checkedAt } ?: 0L
            when {
                list.isEmpty() || now - lastRefresh > refreshIntervalMs -> refreshSources()
                now - lastCheck > STALE_CHECK_MS -> recheck(list)
            }
        }
    }

    fun setSort(mode: SortMode) {
        sortMode.value = mode
    }

    fun select(id: String) {
        selectedId.update { if (it == id) null else id }
    }

    fun selectBest() {
        viewModelScope.launch {
            val best = ProxySelector.pickBest(repository.snapshot(), uiState.value.preferFakeTls)
            if (best == null) {
                messageFlow.emit("Нет доступных прокси. Проверьте список или обновите источники")
            } else {
                selectedId.value = best.id
                messageFlow.emit("Выбран ${best.server}:${best.port}, пинг ${best.pingMs} мс")
            }
        }
    }

    fun recheckAll() {
        viewModelScope.launch { recheck(repository.snapshot()) }
    }

    fun refreshSources() {
        viewModelScope.launch {
            isRefreshing.value = true
            try {
                val report = repository.refreshSources()
                lastReport.value = report
                messageFlow.emit(summarize(report))
            } finally {
                isRefreshing.value = false
            }
        }
    }

    fun toggleFavorite(proxy: ProxyEntity) {
        viewModelScope.launch { repository.setFavorite(proxy.id, !proxy.isFavorite) }
    }

    fun connect(proxy: ProxyEntity) {
        viewModelScope.launch {
            val message = when (TelegramLauncher.open(getApplication(), proxy)) {
                OpenResult.TELEGRAM ->
                    "Ссылка передана в Telegram. Подтвердите включение прокси в самом Telegram: приложение не получает подтверждение подключения"
                OpenResult.BROWSER -> "Telegram не найден. Открыта веб-ссылка t.me/proxy"
                OpenResult.FAILED -> "Не удалось открыть Telegram или браузер"
            }
            messageFlow.emit(message)
        }
    }

    fun addProxy(link: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                val entity = repository.addManual(link)
                onResult(null)
                recheck(listOf(entity))
            } catch (e: ProxyFormatException) {
                onResult(e.message ?: "Некорректная ссылка")
            }
        }
    }

    fun addSource(raw: String, onResult: (String?) -> Unit) {
        val url = raw.trim()
        val parsed = url.toHttpUrlOrNull()
        val current = uiState.value.sources
        when {
            parsed == null || parsed.scheme != "https" -> onResult("Нужна ссылка, начинающаяся с https://")
            url in current -> onResult("Этот источник уже добавлен")
            else -> viewModelScope.launch {
                settings.setSources(current + url)
                onResult(null)
            }
        }
    }

    fun removeSource(url: String) {
        viewModelScope.launch {
            settings.setSources(uiState.value.sources - url)
            refreshSources()
        }
    }

    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch { settings.setTheme(mode) }
    }

    fun setPreferFakeTls(enabled: Boolean) {
        viewModelScope.launch { settings.setPreferFakeTls(enabled) }
    }

    fun setRefreshHours(hours: Int) {
        viewModelScope.launch {
            settings.setRefreshHours(hours)
            BackgroundWork.schedule(getApplication(), hours)
        }
    }

    private fun recheck(list: List<ProxyEntity>) {
        for (item in list) {
            viewModelScope.launch {
                checkingIds.update { it + item.id }
                try {
                    repository.checkOne(item)
                } finally {
                    checkingIds.update { it - item.id }
                }
            }
        }
    }

    private fun summarize(report: List<SourceResult>): String {
        if (report.isEmpty()) return "Источники не заданы. Добавьте ссылки в настройках"
        val okCount = report.count { it is SourceResult.Ok }
        val failed = report.size - okCount
        val found = report.filterIsInstance<SourceResult.Ok>().sumOf { it.candidates.size }
        return if (failed == 0) {
            "Списки обновлены: найдено $found ссылок"
        } else {
            "Найдено $found ссылок. Источников с ошибкой: $failed"
        }
    }

    private fun sortProxies(list: List<ProxyEntity>, mode: SortMode): List<ProxyEntity> = when (mode) {
        SortMode.PING -> list.sortedWith(compareBy<ProxyEntity>({ it.pingOrMax() }, { it.server }))
        SortMode.AVAILABILITY -> list.sortedWith(
            compareBy<ProxyEntity>({ availabilityRank(it.status) }, { it.pingOrMax() }),
        )
        SortMode.COUNTRY -> list.sortedWith(
            compareBy<ProxyEntity>({ it.country == UNKNOWN_COUNTRY }, { it.country }, { it.pingOrMax() }),
        )
    }

    private fun ProxyEntity.pingOrMax(): Int = pingMs ?: Int.MAX_VALUE

    private fun availabilityRank(status: ProxyStatus): Int = when (status) {
        ProxyStatus.ONLINE -> 0
        ProxyStatus.PARTIAL -> 1
        ProxyStatus.UNKNOWN -> 2
        ProxyStatus.OFFLINE -> 3
    }
}
