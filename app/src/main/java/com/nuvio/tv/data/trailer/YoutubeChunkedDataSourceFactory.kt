package com.nuvio.tv.data.trailer

import android.net.Uri
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.nuvio.tv.core.network.IPv4FirstDns
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import kotlin.math.min

/**
 * Trailer googlevideo playback via YouTube `range=` query chunks.
 *
 * Video accepts 2 MB ranges. Short audio uses 1 MiB ranges so the extractor can
 * validate the exact requests that playback will make before selecting a
 * separate adaptive track.
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
        private var resourceLength = C.LENGTH_UNSET.toLong()
        private var currentChunkStart = 0L
        private var currentChunkEnd = 0L
        private var bytesReadInChunk = 0L
        private var establishedChunkSize = 0L
        private var originalDataSpec: DataSpec? = null
        private var streamOpened = false

        override fun addTransferListener(transferListener: TransferListener) {
            upstream.addTransferListener(transferListener)
        }

        override fun open(dataSpec: DataSpec): Long {
            val uri = dataSpec.uri
            isYouTubeStream = uri.host.orEmpty().contains("googlevideo.com")
            if (!isYouTubeStream) {
                return upstream.open(dataSpec)
            }

            originalDataSpec = dataSpec
            currentUri = uri
            currentChunkStart = dataSpec.position
            establishedChunkSize = 0L
            streamOpened = false
            resourceLength = youtubeContentLength(uri)

            if (resourceLength != C.LENGTH_UNSET.toLong() && currentChunkStart >= resourceLength) {
                Log.i(TAG, "open-eof position=$currentChunkStart clen=$resourceLength")
                throw DataSourceException(PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE)
            }

            return try {
                openNextChunk()
                streamOpened = true
                // The wrapper owns chunk boundaries. Advertising clen here makes
                // ProgressiveMediaPeriod reopen a second source at the first
                // rejected range after read() already returned EOF.
                C.LENGTH_UNSET.toLong()
            } catch (e: HttpDataSource.InvalidResponseCodeException) {
                if (dataSpec.position > 0L && (e.responseCode == 403 || e.responseCode == 416)) {
                    Log.i(TAG, "open-eof-403 position=${dataSpec.position} code=${e.responseCode}")
                    throw DataSourceException(PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE)
                }
                throw e
            }
        }

        private fun openNextChunk() {
            val spec = originalDataSpec ?: throw IllegalStateException("No DataSpec")
            val remaining = remainingBytes()
            val sizes = youtubeChunkSizes(
                remaining = remaining,
                establishedChunkSize = establishedChunkSize,
                shortResource = resourceLength in 1L..YOUTUBE_SHORT_RESOURCE_MAX
            )
            if (sizes.isEmpty()) {
                throw DataSourceException(PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE)
            }
            var lastError: Exception? = null
            for (size in sizes) {
                val end = currentChunkStart + size - 1
                val ranges = if (
                    currentChunkStart > 0L &&
                    resourceLength in 1L..YOUTUBE_SHORT_RESOURCE_MAX
                ) {
                    listOf(end, null)
                } else {
                    listOf(end)
                }
                for (rangeEnd in ranges) {
                    for (cdnUri in youtubeCdnCandidates(spec.uri)) {
                        try {
                            val openedLength = openRange(
                                spec,
                                cdnUri,
                                currentChunkStart,
                                rangeEnd
                            )
                            establishedChunkSize = size
                            currentChunkEnd = if (
                                openedLength != C.LENGTH_UNSET.toLong() &&
                                openedLength > 0L
                            ) {
                                currentChunkStart + openedLength - 1
                            } else {
                                end
                            }
                            bytesReadInChunk = 0
                            Log.i(
                                TAG,
                                "chunk-open $currentChunkStart-${rangeEnd ?: ""} " +
                                    "size=$size host=${cdnUri.host}"
                            )
                            return
                        } catch (e: HttpDataSource.InvalidResponseCodeException) {
                            Log.w(
                                TAG,
                                "chunk-403 $currentChunkStart-${rangeEnd ?: ""} " +
                                    "size=$size host=${cdnUri.host} code=${e.responseCode}"
                            )
                            runCatching { upstream.close() }
                            lastError = e
                            if (e.responseCode != 403 && e.responseCode != 416) throw e
                        }
                    }
                }
            }
            throw lastError ?: IllegalStateException("No YouTube range opened")
        }

        private fun remainingBytes(): Long {
            if (resourceLength == C.LENGTH_UNSET.toLong()) return Long.MAX_VALUE
            return maxOf(0L, resourceLength - currentChunkStart)
        }

        private fun openRange(
            spec: DataSpec,
            baseUri: Uri,
            start: Long,
            end: Long?
        ): Long {
            val rangedUri = uriWithYoutubeRange(baseUri, start, end)
            val chunkedSpec = spec.buildUpon()
                .setUri(rangedUri)
                .setPosition(0)
                .setLength(C.LENGTH_UNSET.toLong())
                .setHttpRequestHeaders(emptyMap())
                .build()
            return upstream.open(chunkedSpec)
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (!isYouTubeStream) {
                return upstream.read(buffer, offset, length)
            }
            if (!streamOpened) {
                return C.RESULT_END_OF_INPUT
            }

            val bytesRead = upstream.read(buffer, offset, length)
            if (bytesRead == C.RESULT_END_OF_INPUT) {
                val chunkBytesReceived = bytesReadInChunk
                upstream.close()
                streamOpened = false

                if (chunkBytesReceived < (currentChunkEnd - currentChunkStart + 1)) {
                    return C.RESULT_END_OF_INPUT
                }

                if (resourceLength in 1L..YOUTUBE_SHORT_RESOURCE_MAX) {
                    Log.i(
                        TAG,
                        "short-track-eof bytes=$chunkBytesReceived clen=$resourceLength"
                    )
                    return C.RESULT_END_OF_INPUT
                }

                currentChunkStart += chunkBytesReceived
                if (resourceLength != C.LENGTH_UNSET.toLong() && currentChunkStart >= resourceLength) {
                    return C.RESULT_END_OF_INPUT
                }

                return try {
                    openNextChunk()
                    streamOpened = true
                    upstream.read(buffer, offset, length)
                } catch (e: Exception) {
                    Log.i(TAG, "chunk-eof at $currentChunkStart: ${e.message}")
                    C.RESULT_END_OF_INPUT
                }
            }

            bytesReadInChunk += bytesRead
            return bytesRead
        }

        override fun getUri(): Uri? = upstream.uri ?: currentUri

        override fun close() {
            runCatching { upstream.close() }
            currentUri = null
            originalDataSpec = null
            establishedChunkSize = 0L
            streamOpened = false
        }
    }
}

internal const val YOUTUBE_SHORT_RESOURCE_MAX = 4L * 1024 * 1024
internal const val YOUTUBE_SHORT_RESOURCE_CHUNK = 1024L * 1024
internal const val YOUTUBE_DEFAULT_CHUNK = 2L * 1024 * 1024

internal val CHUNK_SIZES = longArrayOf(
    YOUTUBE_DEFAULT_CHUNK,
    1024L * 1024,
    512L * 1024
)

internal fun youtubeContentLength(uri: Uri): Long {
    val clen = uri.getQueryParameter("clen")?.toLongOrNull()
    return if (clen != null && clen > 0L) clen else C.LENGTH_UNSET.toLong()
}

internal fun youtubeCdnCandidates(uri: Uri): List<Uri> {
    val host = uri.host ?: return listOf(uri)
    val servers = uri.getQueryParameter("mn")
        ?.split(",")
        ?.map { it.trim() }
        ?.filter { it.isNotBlank() }
        .orEmpty()
    if (servers.size < 2) return listOf(uri)

    val candidates = mutableListOf(uri)
    servers.forEachIndexed { index, server ->
        val alternateHost = host
            .replaceFirst(Regex("^rr\\d+---"), "rr${index + 1}---")
            .replaceFirst(Regex("sn-[a-z0-9]+-[a-z0-9]+"), server)
        if (alternateHost != host) {
            val authority = if (uri.port != -1) "$alternateHost:${uri.port}" else alternateHost
            candidates += uri.buildUpon().authority(authority).build()
        }
    }
    return candidates.distinctBy { it.host.orEmpty() }
}

internal fun youtubeChunkSizes(
    remaining: Long,
    establishedChunkSize: Long,
    shortResource: Boolean = false
): List<Long> {
    if (remaining <= 0L) return emptyList()
    if (shortResource && establishedChunkSize <= 0L) {
        return listOf(min(YOUTUBE_SHORT_RESOURCE_CHUNK, remaining))
    }
    val base = if (establishedChunkSize > 0L) {
        listOf(establishedChunkSize) + CHUNK_SIZES.filter { it < establishedChunkSize }
    } else {
        CHUNK_SIZES.toList()
    }
    return base.map { min(it, remaining) }.filter { it > 0L }.distinct()
}

internal fun uriWithYoutubeRange(uri: Uri, start: Long, end: Long?): Uri {
    val builder = uri.buildUpon().clearQuery()
    for (name in uri.queryParameterNames) {
        if (name.equals("range", ignoreCase = true)) continue
        for (value in uri.getQueryParameters(name)) {
            builder.appendQueryParameter(name, value)
        }
    }
    builder.appendQueryParameter("range", if (end == null) "$start-" else "$start-$end")
    return builder.build()
}
