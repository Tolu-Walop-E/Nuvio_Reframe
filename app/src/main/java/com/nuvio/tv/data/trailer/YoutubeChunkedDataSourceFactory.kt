package com.nuvio.tv.data.trailer

import android.net.Uri
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.nuvio.tv.core.network.IPv4FirstDns
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Trailer googlevideo playback on one HTTP request.
 *
 * Re-opening a new `range=` chunk every 512 KB 403s after ~11.5 MB (the Knight
 * trailer freeze). A single `range=0-(clen-1)` 403s on open. One unbounded GET
 * (no `range=` query, no HTTP Range header) keeps the first connection alive
 * for the whole trailer.
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
            val playUri = unboundedPlaybackUri(uri)
            val openSpec = dataSpec.buildUpon()
                .setUri(playUri)
                .setPosition(0)
                .setLength(C.LENGTH_UNSET.toLong())
                .setHttpRequestHeaders(emptyMap())
                .build()
            return try {
                val opened = upstream.open(openSpec)
                Log.i(TAG, "open-ok bytes=$opened host=${playUri.host}")
                opened
            } catch (e: HttpDataSource.InvalidResponseCodeException) {
                Log.w(TAG, "open-fail code=${e.responseCode} host=${playUri.host}")
                throw e
            }
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

private const val TAG = "YTChunkedDS"

/** Strip `range=` so this GET is the only googlevideo request for the stream. */
internal fun unboundedPlaybackUri(uri: Uri): Uri {
    val builder = uri.buildUpon().clearQuery()
    for (name in uri.queryParameterNames) {
        if (name.equals("range", ignoreCase = true)) continue
        for (value in uri.getQueryParameters(name)) {
            builder.appendQueryParameter(name, value)
        }
    }
    return builder.build()
}
