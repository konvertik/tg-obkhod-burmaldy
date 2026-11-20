package ru.mtproxymanager.data

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.mtproxymanager.data.net.ProxyChecker
import ru.mtproxymanager.data.net.SourceFetcher
import ru.mtproxymanager.data.net.SourceResult

class ProxyRepository(
    private val dao: ProxyDao,
    private val fetcher: SourceFetcher,
    private val checker: ProxyChecker,
    private val settings: SettingsStore,
) {
    fun observeProxies(): Flow<List<ProxyEntity>> = dao.observeAll()

    suspend fun snapshot(): List<ProxyEntity> = dao.getAll()

    /**
     * Загружает все источники параллельно. Если ни один не ответил, текущий список сохраняется.
     * Прокси, исчезнувшие из успешно загруженного источника, удаляются, кроме избранных и добавленных вручную.
     */
    suspend fun refreshSources(): List<SourceResult> {
        val urls = settings.sources.first()
        val results = coroutineScope { urls.map { url -> async { fetcher.fetch(url) } }.awaitAll() }
        settings.setLastRefreshAt(System.currentTimeMillis())
        val okResults = results.filterIsInstance<SourceResult.Ok>()

        if (okResults.isNotEmpty()) {
            val okUrls = okResults.map { it.url }.toSet()
            val configured = urls.toSet()
            val fetchedIds = HashSet<String>()
            val now = System.currentTimeMillis()
            val incoming = ArrayList<ProxyEntity>()
            for (ok in okResults) {
                for (candidate in ok.candidates) {
                    if (fetchedIds.add(candidate.id)) incoming += candidate.toEntity(ok.url, now)
                }
            }

            val stale = dao.getAll()
                .filter { e ->
                    !e.isFavorite && e.source != MANUAL_SOURCE &&
                        ((e.source in okUrls && e.id !in fetchedIds) || e.source !in configured)
                }
                .map { it.id }
            if (stale.isNotEmpty()) dao.deleteByIds(stale)
            dao.insertIgnore(incoming)
        }

        checkAll(dao.getAll())
        return results
    }

    /** Добавляет прокси вручную. Бросает [ProxyFormatException], если ссылка некорректна или уже есть в списке. */
    suspend fun addManual(link: String): ProxyEntity {
        val candidate = ProxyLinkParser.parse(link)
        val entity = candidate.toEntity(MANUAL_SOURCE, System.currentTimeMillis())
        if (dao.insertIgnore(listOf(entity)).first() == -1L) {
            throw ProxyFormatException("Этот прокси уже есть в списке")
        }
        return entity
    }

    suspend fun setFavorite(id: String, favorite: Boolean) = dao.setFavorite(id, favorite)

    suspend fun checkOne(proxy: ProxyEntity): Boolean {
        val result = checker.check(proxy)
        val failCount = if (result.protocolOk) 0 else (proxy.failCount ?: 0) + 1
        dao.updateCheck(proxy.id, result.pingMs, result.protocolOk, System.currentTimeMillis(), failCount)
        return result.protocolOk
    }

    /**
     * Проверяет все прокси и удаляет те, что не подтвердили протокол [MAX_FAILED_CHECKS] раз подряд.
     * Избранные и добавленные вручную не удаляются. Если ни один прокси не ответил (скорее всего, нет сети),
     * удаления не происходит.
     */
    suspend fun checkAll(proxies: List<ProxyEntity>) {
        val results = coroutineScope { proxies.map { proxy -> async { checkOne(proxy) } }.awaitAll() }
        if (results.none { it }) return
        val dead = dao.getAll()
            .filter { e ->
                !e.isFavorite && e.source != MANUAL_SOURCE && (e.failCount ?: 0) >= MAX_FAILED_CHECKS
            }
            .map { it.id }
        if (dead.isNotEmpty()) dao.deleteByIds(dead)
    }

    private fun ProxyCandidate.toEntity(source: String, now: Long) = ProxyEntity(
        id = id,
        server = server,
        port = port,
        secret = secret,
        country = UNKNOWN_COUNTRY,
        source = source,
        isFavorite = false,
        pingMs = null,
        checkedAt = 0L,
        addedAt = now,
        kind = kind.name,
        protocolOk = null,
        username = username,
        password = password,
        failCount = 0,
    )

    private companion object {
        const val MAX_FAILED_CHECKS = 3
    }
}
