package ru.mtproxymanager.data.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import ru.mtproxymanager.data.ExtractResult
import ru.mtproxymanager.data.ProxyCandidate
import ru.mtproxymanager.data.ProxyLinkParser
import java.io.IOException

sealed interface SourceResult {
    val url: String

    data class Ok(override val url: String, val candidates: List<ProxyCandidate>, val rejected: Int) : SourceResult
    data class Failed(override val url: String, val reason: String) : SourceResult
}

class SourceFetcher(private val client: OkHttpClient) {

    suspend fun fetch(url: String): SourceResult = withContext(Dispatchers.IO) {
        val httpUrl = url.toHttpUrlOrNull()
        if (httpUrl == null || httpUrl.scheme != "https") {
            return@withContext SourceResult.Failed(url, "нужен адрес https://")
        }
        val request = Request.Builder()
            .url(httpUrl)
            .header("User-Agent", "MTProxyManager/1.0")
            .header("Accept", "text/plain, text/html, application/json, */*")
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@use SourceResult.Failed(url, "сервер ответил кодом ${response.code}")
                }
                val body = response.body ?: return@use SourceResult.Failed(url, "пустой ответ")
                if (body.contentLength() > MAX_BYTES) {
                    return@use SourceResult.Failed(url, "слишком большой файл")
                }
                val source = body.source()
                source.request(MAX_BYTES + 1)
                if (source.buffer.size > MAX_BYTES) {
                    return@use SourceResult.Failed(url, "слишком большой файл")
                }
                val extracted: ExtractResult = ProxyLinkParser.extractAll(source.buffer.readUtf8())
                if (extracted.candidates.isEmpty()) {
                    SourceResult.Failed(url, "в ответе не найдено ссылок на прокси")
                } else {
                    SourceResult.Ok(url, extracted.candidates, extracted.rejected)
                }
            }
        } catch (e: IOException) {
            SourceResult.Failed(url, "нет соединения или истёк таймаут")
        }
    }

    private companion object {
        const val MAX_BYTES = 2L * 1024 * 1024
    }
}
