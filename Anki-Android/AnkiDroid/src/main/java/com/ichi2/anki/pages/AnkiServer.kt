/*
 *  Copyright (c) 2022 Mani <infinyte01@gmail.com>
 *  Copyright (c) 2022 Brayan Oliveira <brayandso.dev@gmail.com>
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

package com.ichi2.anki.pages

import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import java.io.ByteArrayInputStream

open class AnkiServer(
    private val postHandler: PostRequestHandler,
    port: Int = 0,
) : NanoHTTPD(LOCALHOST, port) {
    fun baseUrl(): String = "http://$LOCALHOST:$listeningPort/"

    // it's faster to serve local files without GZip. see 'page render' in logs
    // This also removes 'W/System: A resource failed to call end.'
    override fun useGzipWhenAccepted(r: Response?) = false

    override fun serve(session: IHTTPSession): Response =
        when (session.method) {
            Method.POST -> {
                val uri = session.uri
                Timber.d("POST: Requested %s", uri)
                val inputBytes = getSessionBytes(session)

                try {
                    val data = runBlocking { postHandler.handlePostRequest(PostRequestUri(uri), inputBytes) }
                    buildResponse(data)
                } catch (exception: Exception) {
                    Timber.w(exception, "buildResponse failure")
                    buildResponse(exception.localizedMessage?.encodeToByteArray(), status = Response.Status.INTERNAL_ERROR)
                }
            }
            Method.GET -> {
                Timber.d("Rejecting GET request to server %s", session.uri)
                newFixedLengthResponse(Response.Status.NOT_FOUND, null, null)
            }
            else -> {
                Timber.d("Ignored request of unhandled method %s, uri %s", session.method, session.uri)
                newFixedLengthResponse(null)
            }
        }

    private fun buildResponse(
        data: ByteArray?,
        mimeType: String = "application/binary",
        status: Response.IStatus = Response.Status.OK,
    ): Response =
        if (data == null) {
            newFixedLengthResponse(null)
        } else {
            newChunkedResponse(status, mimeType, ByteArrayInputStream(data))
        }

    companion object {
        const val LOCALHOST = "127.0.0.1"

        /** Common prefix used on Anki requests */
        const val ANKI_PREFIX = "/_anki/"
        const val ANKIDROID_PREFIX = "/ankidroid/"
        const val ANKIDROID_JS_PREFIX = "/jsapi/"

        fun getSessionBytes(session: IHTTPSession): ByteArray {
            val contentLength = session.headers["content-length"]!!.toInt()
            val bytes = ByteArray(contentLength)
            session.inputStream.read(bytes, 0, contentLength)
            return bytes
        }
    }
}
