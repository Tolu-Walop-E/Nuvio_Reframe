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
 * Trailer googlevideo playback via YouTube `range=` query chunks.
 *
 * A full-file GET and `range=0-(clen-1)` both 403 on open. 512 KB chunks play,
 * but YouTube 403s the 24th request (~11.5 MB) and the trailer freezes.
 * Prefer the largest chunk that still opens so a trailer finishes in fewer
 * reconnects; fall back to 512 KB if a bigger range is rejected.
 */
@UnstableApi
class YoutubeChunkedDataSourceFactory : DataSource.Factory {

    companion object {
        private const val TAG = "YTChunkedDS"
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
        return YoutubeChunkedDataSource(upstream)
    }

    private class YoutubeChunkedDataSource(
        private val upstream: DataSource
    ) : DataSource {

        private var currentUri: Uri? = null
        private var isYouTubeStream = false
        private var totalContentLength = C.LENGTH_UNSET.toLong()
        private var currentChunkStart = 0L
        private var currentChunkEnd = 0L
        private var bytesReadInChunk = 0L
        private var establishedChunkSize = 0L
        private var originalDataSpec: DataSpec? = null

        override fun addTransferListener(transferListener: TransferListener) {
            upstream.addTransferListener(transferListener)
        }

        override fun open(dataSpec: DataSpec): Long {
            val uri = dataSpec.uri
            val host = uri.host.orEmpty()
            isYouTubeStream = host.contains("googlevideo.com")

            if (!isYouTubeStream) {
                return upstream.open(dataSpec)
            }

            originalDataSpec = dataSpec
            currentChunkStart = dataSpec.position
            totalContentLength = dataSpec.length
            establishedChunkSize = 0L
            return openNextChunk()
        }

        private fun openNextChunk(): Long {
            val spec = originalDataSpec ?: throw IllegalStateException("No DataSpec")
            val sizes = chunkSizesToTry()
            var lastError: Exception? = null
            for (size in sizes) {
                val end = currentChunkStart + size - 1
                try {
                    openRange(spec, currentChunkStart, end)
                    establishedChunkSize = size
                    currentChunkEnd = end
                    bytesReadInChunk = 0
                    Log.i(TAG, "chunk-open $currentChunkStart-$end size=$size")
                    return if (totalContentLength != C.LENGTH_UNSET.toLong()) {
                        totalContentLength
                    } else {
                        C.LENGTH_UNSET.toLong()
                    }
                } catch (e: HttpDataSource.InvalidResponseCodeException) {
                    Log.w(TAG, "chunk-403 $currentChunkStart-$end size=$size code=${e.responseCode}")
                    runCatching { upstream.close() }
                    lastError = e
                    if (e.responseCode != 403 && e.responseCode != 416) throw e
                }
            }
            throw lastError ?: IllegalStateException("No YouTube range opened")
        }

        private fun chunkSizesToTry(): List<Long> {
            if (establishedChunkSize <= 0L) return CHUNK_SIZES.toList()
            return buildList {
                add(establishedChunkSize)
                for (size in CHUNK_SIZES) {
                    if (size < establishedChunkSize) add(size)
                }
            }
        }

        private fun openRange(spec: DataSpec, start: Long, end: Long) {
            val rangedUri = uriWithYoutubeRange(spec.uri, start, end)
            val chunkedSpec = spec.buildUpon()
                .setUri(rangedUri)
                .setPosition(0)
                .setLength(C.LENGTH_UNSET.toLong())
                .setHttpRequestHeaders(emptyMap())
                .build()
            upstream.open(chunkedSpec)
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (!isYouTubeStream) {
                return upstream.read(buffer, offset, length)
            }

            val bytesRead = upstream.read(buffer, offset, length)
            if (bytesRead == C.RESULT_END_OF_INPUT) {
                val chunkBytesReceived = bytesReadInChunk
                upstream.close()

                if (chunkBytesReceived < (currentChunkEnd - currentChunkStart + 1)) {
                    return C.RESULT_END_OF_INPUT
                }

                currentChunkStart += chunkBytesReceived
                if (totalContentLength != C.LENGTH_UNSET.toLong()) {
                    totalContentLength -= chunkBytesReceived
                    if (totalContentLength <= 0) {
                        return C.RESULT_END_OF_INPUT
                    }
                }

                return try {
                    openNextChunk()
                    upstream.read(buffer, offset, length)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to open next chunk at $currentChunkStart: ${e.message}")
                    C.RESULT_END_OF_INPUT
                }
            }

            bytesReadInChunk += bytesRead
            return bytesRead
        }

        override fun getUri(): Uri? = upstream.uri ?: currentUri

        override fun close() {
            upstream.close()
            currentUri = null
            originalDataSpec = null
            establishedChunkSize = 0L
        }
    }
}

internal val CHUNK_SIZES = longArrayOf(
    2L * 1024 * 1024,
    1024L * 1024,
    512L * 1024
)

internal fun uriWithYoutubeRange(uri: Uri, start: Long, end: Long): Uri {
    val builder = uri.buildUpon().clearQuery()
    for (name in uri.queryParameterNames) {
        if (name.equals("range", ignoreCase = true)) continue
        for (value in uri.getQueryParameters(name)) {
            builder.appendQueryParameter(name, value)
        }
    }
    builder.appendQueryParameter("range", "$start-$end")
    return builder.build()
}
