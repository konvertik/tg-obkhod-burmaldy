package ru.mtproxymanager.data.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import ru.mtproxymanager.data.ProxyEntity
import ru.mtproxymanager.data.ProxyKind
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

data class CheckResult(val pingMs: Int?, val protocolOk: Boolean)

/**
 * Двухэтапная проверка: TCP-подключение (пинг), затем проверка протокола через прокси до Telegram.
 * Прокси считается рабочим только если протокол подтверждён.
 */
class ProxyChecker(
    private val tcpTimeoutMs: Int = 3_000,
    private val probeTimeoutMs: Int = 6_000,
    concurrency: Int = 12,
) {
    private val semaphore = Semaphore(concurrency)

    suspend fun check(proxy: ProxyEntity): CheckResult = semaphore.withPermit {
        val ping = tcpPing(proxy.server, proxy.port)
        CheckResult(pingMs = ping, protocolOk = ping != null && verifyProtocol(proxy))
    }

    private suspend fun tcpPing(server: String, port: Int): Int? =
        withTimeoutOrNull(tcpTimeoutMs + 3_000L) {
            withContext(Dispatchers.IO) {
                try {
                    val address = InetSocketAddress(server, port)
                    Socket().use { socket ->
                        val started = System.nanoTime()
                        socket.connect(address, tcpTimeoutMs)
                        ((System.nanoTime() - started) / 1_000_000L).toInt().coerceAtLeast(1)
                    }
                } catch (e: IOException) {
                    null
                } catch (e: IllegalArgumentException) {
                    null
                }
            }
        }

    private suspend fun verifyProtocol(proxy: ProxyEntity): Boolean =
        withTimeoutOrNull(probeTimeoutMs + 3_000L) {
            withContext(Dispatchers.IO) {
                when (proxy.proxyKind) {
                    ProxyKind.MTPROTO -> MtProtoProbe.verify(proxy.server, proxy.port, proxy.secret, probeTimeoutMs)
                    ProxyKind.SOCKS5 -> Socks5Probe.verify(
                        proxy.server, proxy.port, proxy.username, proxy.password, probeTimeoutMs,
                    )
                }
            }
        } ?: false
}
