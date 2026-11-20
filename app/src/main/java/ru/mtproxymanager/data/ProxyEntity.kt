package ru.mtproxymanager.data

import androidx.room.Entity
import androidx.room.PrimaryKey

const val UNKNOWN_COUNTRY = "Неизвестно"
const val MANUAL_SOURCE = "Вручную"

enum class ProxyStatus { UNKNOWN, ONLINE, PARTIAL, OFFLINE }

@Entity(tableName = "proxies")
data class ProxyEntity(
    @PrimaryKey val id: String,
    val server: String,
    val port: Int,
    val secret: String,
    val country: String,
    val source: String,
    val isFavorite: Boolean,
    val pingMs: Int?,
    val checkedAt: Long,
    val addedAt: Long,
    /** Имя [ProxyKind]. Null у записей версии 1 означает MTProto. */
    val kind: String?,
    val protocolOk: Boolean?,
    val username: String?,
    val password: String?,
    /** Сколько проверок подряд прокси не подтвердил протокол. Null у старых записей означает 0. */
    val failCount: Int? = null,
) {
    val proxyKind: ProxyKind
        get() = if (kind == ProxyKind.SOCKS5.name) ProxyKind.SOCKS5 else ProxyKind.MTPROTO

    /** ONLINE только когда протокол подтверждён ответом дата-центра Telegram. */
    val status: ProxyStatus
        get() = when {
            checkedAt == 0L -> ProxyStatus.UNKNOWN
            pingMs == null -> ProxyStatus.OFFLINE
            protocolOk == true -> ProxyStatus.ONLINE
            else -> ProxyStatus.PARTIAL
        }

    /** FakeTLS-секрет (префикс ee) маскируется под HTTPS и лучше переживает DPI-блокировки. */
    val isFakeTls: Boolean
        get() = proxyKind == ProxyKind.MTPROTO && secret.startsWith("ee")

    fun typeLabel(): String = when {
        proxyKind == ProxyKind.SOCKS5 -> "SOCKS5"
        isFakeTls -> "FakeTLS"
        else -> "MTProto"
    }

    fun maskedSecret(): String = when {
        proxyKind == ProxyKind.SOCKS5 -> if (password.isNullOrEmpty()) "без пароля" else "с паролем"
        secret.length > 8 -> secret.take(4) + "…" + secret.takeLast(4)
        else -> "••••"
    }
}
