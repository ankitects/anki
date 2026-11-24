// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.pages

import com.ichi2.anki.pages.PostRequestBridge.Companion.HEADER_SIZE
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.nio.ByteBuffer
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PostRequestBridgeTest {
    @Test
    fun `a request is sent to the handler and its response is returned`() =
        runTest {
            var receivedUri: PostRequestUri? = null
            var receivedBody: ByteArray? = null
            val bridge =
                bridge { uri, bytes ->
                    receivedUri = uri
                    receivedBody = bytes
                    "response".encodeToByteArray()
                }

            val reply = bridge.handleMessage(request(id = 7, uri = "/_anki/graphs", body = "request"))

            assertEquals(Reply(id = 7, status = 200, body = "response"), reply.parse())
            assertEquals("graphs", receivedUri?.backendMethodName)
            assertEquals("request", receivedBody?.decodeToString())
        }

    @Test
    fun `a failed request returns the error message with status 500`() =
        runTest {
            val bridge = bridge { _, _ -> throw IllegalArgumentException("unhandled method: foo") }

            val reply = bridge.handleMessage(request(id = 1, uri = "/_anki/foo"))

            assertEquals(Reply(id = 1, status = 500, body = "unhandled method: foo"), reply.parse())
        }

    @Test
    fun `a failed request without an error message returns the exception name with status 500`() =
        runTest {
            val bridge = bridge { _, _ -> throw IllegalStateException() }

            val reply = bridge.handleMessage(request(id = 1, uri = "/_anki/foo"))

            assertEquals(Reply(id = 1, status = 500, body = "java.lang.IllegalStateException"), reply.parse())
        }

    @Test
    fun `a request cancelled by its handler returns the error message with status 500`() =
        runTest {
            val bridge = bridge { _, _ -> throw CancellationException("timed out") }

            val reply = bridge.handleMessage(request(id = 1, uri = "/_anki/foo"))

            assertEquals(Reply(id = 1, status = 500, body = "timed out"), reply.parse())
        }

    @Test
    fun `a request isn't answered if its coroutine is cancelled`() =
        runTest {
            val bridge = bridge { _, _ -> awaitCancellation() }

            var reply: ByteArray? = null
            val job = launch { reply = bridge.handleMessage(request(id = 1, uri = "/_anki/foo")) }
            runCurrent()
            job.cancelAndJoin()

            assertNull(reply)
        }

    @Test
    fun `a request above the size limit returns status 413`() =
        runTest {
            val bridge = bridge { _, _ -> error("the handler must not be called") }
            val message = ByteBuffer.allocate(PostRequestBridge.MAX_MESSAGE_SIZE + 1).putInt(1).array()

            val reply = bridge.handleMessage(message)

            assertEquals(Reply(id = 1, status = 413, body = "Request too large"), reply.parse())
        }

    @Test
    fun `an invalid message is not handled`() =
        runTest {
            val bridge = bridge { _, _ -> error("the handler must not be called") }

            // shorter than the request ID and the URI length
            assertNull(bridge.handleMessage(byteArrayOf(0, 0, 0, 1)))
            // the URI is longer than the message
            assertNull(
                bridge.handleMessage(
                    ByteBuffer
                        .allocate(HEADER_SIZE)
                        .putInt(1)
                        .putShort(10)
                        .array(),
                ),
            )
        }

    private fun TestScope.bridge(handle: suspend (PostRequestUri, ByteArray) -> ByteArray) =
        PostRequestBridge(
            handler =
                object : PostRequestHandler {
                    override suspend fun handlePostRequest(
                        uri: PostRequestUri,
                        bytes: ByteArray,
                    ) = handle(uri, bytes)
                },
            scope = this,
        )

    /** A request, as `post-request-bridge.js` encodes it */
    private fun request(
        id: Int,
        uri: String,
        body: String = "",
    ): ByteArray {
        val uriBytes = uri.encodeToByteArray()
        val bodyBytes = body.encodeToByteArray()
        return ByteBuffer
            .allocate(HEADER_SIZE + uriBytes.size + bodyBytes.size)
            .putInt(id)
            .putShort(uriBytes.size.toShort())
            .put(uriBytes)
            .put(bodyBytes)
            .array()
    }

    private data class Reply(
        val id: Int,
        val status: Int,
        val body: String,
    )

    private fun ByteArray?.parse(): Reply {
        val buffer = ByteBuffer.wrap(assertNotNull(this))
        return Reply(
            id = buffer.int,
            status = buffer.short.toInt(),
            body = ByteArray(buffer.remaining()).also { buffer.get(it) }.decodeToString(),
        )
    }
}
