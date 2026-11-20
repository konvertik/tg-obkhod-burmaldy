package ru.mtproxymanager.data

object ProxySelector {
    private const val FAVORITE_BONUS_MS = 30
    private const val FAKE_TLS_BONUS_MS = 60

    /**
     * Лучший доступный прокси: наименьший пинг с бонусами.
     * При [preferFakeTls] FakeTLS-прокси получают приоритет, потому что лучше проходят через блокировки.
     */
    fun pickBest(proxies: List<ProxyEntity>, preferFakeTls: Boolean): ProxyEntity? =
        proxies
            .filter { it.status == ProxyStatus.ONLINE }
            .minByOrNull { effectivePing(it, preferFakeTls) }

    private fun effectivePing(proxy: ProxyEntity, preferFakeTls: Boolean): Int {
        var score = proxy.pingMs ?: Int.MAX_VALUE
        if (proxy.isFavorite) score -= FAVORITE_BONUS_MS
        if (preferFakeTls && proxy.isFakeTls) score -= FAKE_TLS_BONUS_MS
        return score
    }
}
