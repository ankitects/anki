/*
 *  Copyright (c) 2023 Brayan Oliveira <brayandso.dev@gmail.com>
 *  Copyright (c) 2023 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki

import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.utils.AssetHelper.guessMimeType
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.nio.file.Paths
import kotlin.io.path.pathString

private const val RANGE_HEADER = "Range"
private const val MATHJAX_PATH_PREFIX = "/_anki/js/vendor/mathjax"

class ViewerResourceHandler(
    context: Context,
) {
    private val assetManager = context.assets
    private val mediaDir = CollectionHelper.getMediaDirectory(context)

    fun shouldInterceptRequest(request: WebResourceRequest): WebResourceResponse? {
        val url = request.url
        val path = url.path

        if (request.method != "GET" || path == null) {
            return null
        }
        if (path == "/favicon.ico") {
            return WebResourceResponse(null, null, ByteArrayInputStream(ByteArray(0)))
        }

        try {
            if (path.startsWith(MATHJAX_PATH_PREFIX)) {
                val mathjaxAssetPath =
                    Paths
                        .get(
                            "backend/js/vendor/mathjax",
                            path.removePrefix(MATHJAX_PATH_PREFIX),
                        ).pathString
                val inputStream = assetManager.open(mathjaxAssetPath)
                return WebResourceResponse(guessMimeType(path), null, inputStream)
            }

            val file = File(mediaDir, path)
            if (!file.exists()) {
                return null
            }
            request.requestHeaders[RANGE_HEADER]?.let { range ->
                return handlePartialContent(file, range)
            }
            val inputStream = FileInputStream(file)
            val mimeType = guessMimeType(path)
            return WebResourceResponse(mimeType, null, inputStream)
        } catch (e: Exception) {
            Timber.d("File not found")
            return null
        }
    }

    @NeedsTest("seeking audio - 16513")
    private fun handlePartialContent(
        file: File,
        range: String,
    ): WebResourceResponse {
        val rangeHeader = RangeHeader.from(range, defaultEnd = file.length() - 1)

        val mimeType = guessMimeType(file.path)
        val (start, end) = rangeHeader
        val responseHeaders =
            mapOf(
                "Content-Range" to "bytes $start-$end/${file.length()}",
                "Accept-Ranges" to "bytes",
            )
        // WARN: WebResourceResponse appears to handle truncating the stream internally
        // This is NOT the same as NanoHTTPD

        // sending a truncated stream caused:
        // -> `net::ERR_FAILED`

        // returning a 'full' input stream with the provided header
        // returns a 'correct' Content-Length (example below)
        //
        // Content-Range: bytes 2916352-2931180/2931181
        // Content-Length: 14829
        // The above needs more investigation
        val fileStream = FileInputStream(file)
        return WebResourceResponse(
            mimeType,
            null,
            206,
            "Partial Content",
            responseHeaders,
            fileStream,
        )
    }
}

/**
 * Handles the "range" header in a HTTP Request
 */
data class RangeHeader(
    val start: Long,
    val end: Long,
) {
    companion object {
        fun from(
            range: String,
            defaultEnd: Long,
        ): RangeHeader {
            val numbers = range.substring("bytes=".length).split('-')
            val unspecifiedEnd = numbers.getOrNull(1).isNullOrEmpty()
            return RangeHeader(
                start = numbers[0].toLong(),
                end = if (unspecifiedEnd) defaultEnd else numbers[1].toLong(),
            )
        }
    }
}
