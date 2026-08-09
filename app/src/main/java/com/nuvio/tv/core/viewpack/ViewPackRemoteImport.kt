package com.nuvio.tv.core.viewpack

import com.nuvio.tv.core.network.IPv4FirstDns
import java.net.URI
import java.net.URLDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Resolves Studio publish links / deep links into pack JSON and applies them.
 * Mirrors addon install: public HTTPS (or LAN http) URL → fetch → parse.
 */
object ViewPackRemoteImport {
    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(IPv4FirstDns())
            .build()
    }

    sealed interface PackPayload {
        data class Json(val text: String) : PackPayload
        data class RemoteUrl(val url: String) : PackPayload
    }

    /**
     * Clipboard / deep-link text → raw JSON or a fetchable http(s) URL.
     * Accepts:
     * - raw `.view.json`
     * - `https://…` / `http://…` pack URLs
     * - `nuvio://viewpack?url=https%3A%2F%2F…`
     */
    fun resolvePayload(text: String): PackPayload? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null

        extractViewPackDeepLinkUrl(trimmed)?.let { return PackPayload.RemoteUrl(it) }

        val lower = trimmed.lowercase()
        if (lower.startsWith("https://") || lower.startsWith("http://")) {
            return PackPayload.RemoteUrl(trimmed)
        }

        if (trimmed.startsWith("{")) {
            return PackPayload.Json(trimmed)
        }

        return null
    }

    /** Deep-link query `url` must be http(s). Returns null if missing/invalid. */
    fun extractViewPackDeepLinkUrl(deepLink: String, httpsOnly: Boolean = false): String? {
        val parsed = runCatching { URI(deepLink.trim()) }.getOrNull() ?: return null
        if (!parsed.scheme.equals("nuvio", ignoreCase = true)) return null
        val host = parsed.host?.lowercase().orEmpty()
        if (host != "viewpack" && host != "view-pack") return null

        val url = queryParameters(parsed)["url"]?.trim().orEmpty()
        if (url.isBlank()) return null
        val lower = url.lowercase()
        val ok = if (httpsOnly) {
            lower.startsWith("https://")
        } else {
            lower.startsWith("https://") || lower.startsWith("http://")
        }
        return url.takeIf { ok }
    }

    suspend fun fetchPackJson(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).get().build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("HTTP ${response.code}")
            }
            response.body?.string()?.takeIf { it.isNotBlank() }
                ?: error("Empty response")
        }
    }

    /**
     * Resolve clipboard/deep-link text, fetch if needed, parse, return serialized pack JSON
     * ready for [com.nuvio.tv.data.local.LayoutPreferenceDataStore.setActiveViewPackJson].
     */
    suspend fun loadAndSerialize(text: String): Pair<String, String> {
        val payload = resolvePayload(text)
            ?: error("Not a view pack JSON or publish URL")
        val json = when (payload) {
            is PackPayload.Json -> payload.text
            is PackPayload.RemoteUrl -> fetchPackJson(payload.url)
        }
        val pack = parseViewPackJson(json)
        return pack.name to serializeViewPackJson(pack)
    }

    private fun queryParameters(parsedUrl: URI): Map<String, String> {
        return parsedUrl.rawQuery
            ?.split("&")
            .orEmpty()
            .mapNotNull { pair ->
                val index = pair.indexOf("=")
                if (index < 0) return@mapNotNull null
                val key = decode(pair.substring(0, index)).trim()
                val value = decode(pair.substring(index + 1)).trim()
                if (key.isBlank() || value.isBlank()) null else key to value
            }
            .toMap()
    }

    private fun decode(value: String): String {
        return runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)
    }
}
