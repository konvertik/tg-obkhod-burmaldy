package ru.mtproxymanager.data

import java.net.URLDecoder

class ProxyFormatException(message: String) : IllegalArgumentException(message)

enum class ProxyKind { MTPROTO, SOCKS5 }

data class ProxyCandidate(
    val kind: ProxyKind,
    val server: String,
    val port: Int,
    val secret: String = "",
    val username: String? = null,
    val password: String? = null,
) {
    val id: String
        get() = when (kind) {
            ProxyKind.MTPROTO -> "$server:$port:$secret"
            ProxyKind.SOCKS5 -> "socks:$server:$port:${username.orEmpty()}"
        }
}

data class ExtractResult(val candidates: List<ProxyCandidate>, val rejected: Int)

/**
 * Разбирает и проверяет ссылки на прокси Telegram: MTProto (tg://proxy, t.me/proxy) и SOCKS5 (tg://socks, t.me/socks).
 * Принимаются только hex-secret для MTProto. Данные из внешних источников не доверяются: каждая ссылка проверяется.
 */
object ProxyLinkParser {
    private const val MAX_LINK_LENGTH = 2048
    private const val MAX_EXTRACTED = 5000
    private const val HOST_PREFIX = """(?:tg://|https?://(?:t\.me|telegram\.me|telegram\.dog)/)"""

    private val LINK = Regex("""^$HOST_PREFIX(proxy|socks)\?(.+)$""", RegexOption.IGNORE_CASE)
    private val FIND = Regex("""$HOST_PREFIX(?:proxy|socks)\?[^\s"'<>]+""", RegexOption.IGNORE_CASE)
    private val IPV4 = Regex("""\d{1,3}(?:\.\d{1,3}){3}""")
    private val LABEL = Regex("""[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?""")
    private val HEX = Regex("""[0-9a-fA-F]+""")

    fun parse(raw: String): ProxyCandidate {
        val text = raw.trim().replace("&amp;", "&")
        if (text.isEmpty()) throw ProxyFormatException("Вставьте ссылку на прокси")
        if (text.length > MAX_LINK_LENGTH) throw ProxyFormatException("Ссылка слишком длинная")
        val match = LINK.matchEntire(text)
            ?: throw ProxyFormatException("Нужна ссылка вида t.me/proxy, tg://proxy, t.me/socks или tg://socks")
        val isSocks = match.groupValues[1].equals("socks", ignoreCase = true)
        val params = parseQuery(match.groupValues[2])
        val server = validateServer(params["server"].orEmpty())
        val port = params["port"]?.toIntOrNull()?.takeIf { it in 1..65535 }
            ?: throw ProxyFormatException("Некорректный порт")

        if (isSocks) {
            val user = params["user"]?.takeIf { it.isNotEmpty() }
            val pass = params["pass"]?.takeIf { it.isNotEmpty() }
            if ((user?.length ?: 0) > 255 || (pass?.length ?: 0) > 255) {
                throw ProxyFormatException("Слишком длинный логин или пароль")
            }
            return ProxyCandidate(ProxyKind.SOCKS5, server, port, username = user, password = pass)
        }

        val secret = params["secret"].orEmpty().lowercase()
        if (secret.length !in 32..512 || secret.length % 2 != 0 || !HEX.matches(secret)) {
            throw ProxyFormatException("Некорректный secret: ожидается hex-строка")
        }
        return ProxyCandidate(ProxyKind.MTPROTO, server, port, secret = secret)
    }

    /** Ищет все ссылки в произвольном тексте (HTML, JSON, plain text). Некорректные пропускаются и считаются. */
    fun extractAll(text: String): ExtractResult {
        val found = LinkedHashMap<String, ProxyCandidate>()
        var rejected = 0
        for (match in FIND.findAll(text.replace("&amp;", "&"))) {
            if (found.size >= MAX_EXTRACTED) break
            val raw = match.value.trimEnd('.', ',', ';', ')', ']')
            try {
                val candidate = parse(raw)
                found.putIfAbsent(candidate.id, candidate)
            } catch (e: ProxyFormatException) {
                rejected++
            }
        }
        return ExtractResult(found.values.toList(), rejected)
    }

    private fun validateServer(raw: String): String {
        val server = raw.trim().lowercase()
        if (server.isEmpty() || server.length > 253) throw ProxyFormatException("Некорректный адрес сервера")
        if (IPV4.matches(server)) {
            val octets = server.split('.').map { it.toInt() }
            if (octets.any { it > 255 }) throw ProxyFormatException("Некорректный IP-адрес сервера")
            if (octets[0] == 0 || octets[0] == 127 || octets[0] >= 224) {
                throw ProxyFormatException("Локальные и служебные адреса не поддерживаются")
            }
            return server
        }
        if (server == "localhost" || !server.split('.').all { LABEL.matches(it) }) {
            throw ProxyFormatException("Некорректный адрес сервера")
        }
        return server
    }

    private fun parseQuery(query: String): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        for (part in query.split('&')) {
            val idx = part.indexOf('=')
            if (idx <= 0) continue
            val name = part.substring(0, idx).lowercase()
            val value = try {
                URLDecoder.decode(part.substring(idx + 1), "UTF-8")
            } catch (e: IllegalArgumentException) {
                continue
            }
            result.putIfAbsent(name, value)
        }
        return result
    }
}
