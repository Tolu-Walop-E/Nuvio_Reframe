package com.nuvio.tv.data.trailer

import android.net.Uri
import android.util.Log
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
 * Trailer googlevideo playback.
 *
 * Adaptive YouTube URLs 403 a full-file GET and also 403 a 10 MB first chunk.
 * The extractor probe succeeds with a 1 KB `range=` query on OkHttp/IPv4;
 * playback has to do the same: same client, same DNS, small `range=` chunks,
 * never a duplicate range param or an HTTP Range header.
 */
@UnstableApi
class YoutubeChunkedDataSourceFactory(
    private val chunkSizeBytes: Long = CHUNK_SIZE
) : DataSource.Factory {

    companion object {
        private const val TAG = "YTChunkedDS"
        private const val CHUNK_SIZE = 512L * 1024
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
        return YoutubeChunkedDataSource(upstream, chunkSizeBytes)
    }

    private class YoutubeChunkedDataSource(
        private val upstream: DataSource,
        private val chunkSize: Long
    ) : DataSource {

        private var currentUri: Uri? = null
        private var isYouTubeStream = false
        private var totalContentLength = C.LENGTH_UNSET.toLong()
        private var currentChunkStart = 0L
        private var currentChunkEnd = 0L
        private var bytesReadInChunk = 0L
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

            return openNextChunk()
        }

        private fun openNextChunk(): Long {
            val spec = originalDataSpec ?: throw IllegalStateException("No DataSpec")
            val end = if (totalContentLength != C.LENGTH_UNSET.toLong()) {
                minOf(currentChunkStart + chunkSize - 1, currentChunkStart + totalContentLength - 1)
            } else {
                currentChunkStart + chunkSize - 1
            }
            currentChunkEnd = end

            val rangedUri = uriWithYoutubeRange(spec.uri, currentChunkStart, currentChunkEnd)
            val chunkedSpec = spec.buildUpon()
                .setUri(rangedUri)
                .setPosition(0)
                .setLength(C.LENGTH_UNSET.toLong())
                .setHttpRequestHeaders(emptyMap())
                .build()

            bytesReadInChunk = 0
            upstream.open(chunkedSpec)
            return if (totalContentLength != C.LENGTH_UNSET.toLong()) totalContentLength else C.LENGTH_UNSET.toLong()
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
        }
    }
}

private fun uriWithYoutubeRange(uri: Uri, start: Long, end: Long): Uri {
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
