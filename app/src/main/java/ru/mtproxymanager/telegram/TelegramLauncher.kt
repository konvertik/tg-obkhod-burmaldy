package ru.mtproxymanager.telegram

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import ru.mtproxymanager.data.ProxyEntity
import ru.mtproxymanager.data.ProxyKind
import java.net.URLEncoder

enum class OpenResult { TELEGRAM, BROWSER, FAILED }

/**
 * Передаёт прокси в Telegram через официальные deep links (tg://proxy и tg://socks).
 * Telegram не возвращает результат, поэтому успешный запуск не означает подключение.
 */
object TelegramLauncher {

    fun deepLink(proxy: ProxyEntity): String = build("tg://", proxy)

    fun webLink(proxy: ProxyEntity): String = build("https://t.me/", proxy)

    fun open(context: Context, proxy: ProxyEntity): OpenResult {
        if (startView(context, deepLink(proxy))) return OpenResult.TELEGRAM
        if (startView(context, webLink(proxy))) return OpenResult.BROWSER
        return OpenResult.FAILED
    }

    private fun build(base: String, proxy: ProxyEntity): String = when (proxy.proxyKind) {
        ProxyKind.MTPROTO ->
            "${base}proxy?server=${proxy.server}&port=${proxy.port}&secret=${proxy.secret}"
        ProxyKind.SOCKS5 -> {
            val auth = listOfNotNull(
                proxy.username?.let { "user=${encode(it)}" },
                proxy.password?.let { "pass=${encode(it)}" },
            ).joinToString("") { "&$it" }
            "${base}socks?server=${proxy.server}&port=${proxy.port}$auth"
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun startView(context: Context, link: String): Boolean = try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}
