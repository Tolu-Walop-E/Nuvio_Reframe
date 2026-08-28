package com.nuvio.tv.data.trailer

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.nuvio.tv.core.network.IPv4FirstDns
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Trailer googlevideo playback on one HTTP request.
 *
 * Re-opening a new `range=` chunk every 512 KB 403s after ~11.5 MB (the Knight
 * trailer freeze). YouTube allows the first connection and rejects later ones
 * without an n-sig refresh. A trailer is short enough to finish on the first GET.
 */
@UnstableApi
class YoutubeChunkedDataSourceFactory : DataSource.Factory {

    companion object {
        private const val PLAYBACK_USER_AGENT =
            "com.google.ios.youtube/20.10.1 (iPhone16,2; U; CPU iOS 17_4 like Mac OS X)"

        private val playbackClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .dns(IPv4FirstDns())
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .retryOnConnectionFailure(true)
                .build()
        }
    }

    override fun createDataSource(): DataSource {
        val upstream = OkHttpDataSource.Factory(playbackClient)
            .setUserAgent(PLAYBACK_USER_AGENT)
            .setDefaultRequestProperties(
                mapOf(
                    "Origin" to "https://www.youtube.com",
                    "Referer" to "https://www.youtube.com/",
                    "Accept-Language" to "en-US,en;q=0.9"
                )
            )
            .createDataSource()
        return YoutubeSingleRequestDataSource(upstream)
    }

    private class YoutubeSingleRequestDataSource(
        private val upstream: DataSource
    ) : DataSource {

        override fun addTransferListener(transferListener: TransferListener) {
            upstream.addTransferListener(transferListener)
        }

        override fun open(dataSpec: DataSpec): Long {
            val uri = dataSpec.uri
            if (!uri.host.orEmpty().contains("googlevideo.com")) {
                return upstream.open(dataSpec)
            }
            val playUri = singleRequestUri(uri)
            val openSpec = dataSpec.buildUpon()
                .setUri(playUri)
                .setPosition(0)
                .setLength(C.LENGTH_UNSET.toLong())
                .setHttpRequestHeaders(emptyMap())
                .build()
            return upstream.open(openSpec)
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            return upstream.read(buffer, offset, length)
        }

        override fun getUri(): Uri? = upstream.uri

        override fun close() {
            upstream.close()
        }
    }
}

/** Prefer one `range=0-(clen-1)` so YouTube knows the full object; never stack extra ranges. */
private fun singleRequestUri(uri: Uri): Uri {
    val clen = uri.getQueryParameter("clen")?.toLongOrNull()
    val builder = uri.buildUpon().clearQuery()
    for (name in uri.queryParameterNames) {
        if (name.equals("range", ignoreCase = true)) continue
        for (value in uri.getQueryParameters(name)) {
            builder.appendQueryParameter(name, value)
        }
    }
    if (clen != null && clen > 0) {
        builder.appendQueryParameter("range", "0-${clen - 1}")
    }
    return builder.build()
}
