// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.cardviewer

import com.ichi2.anki.libanki.SoundOrVideoTag
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CompletionHandler
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.InternalForInheritanceCoroutinesApi
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import kotlin.coroutines.CoroutineContext
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@InternalForInheritanceCoroutinesApi
class VideoPlayerTest {
    @Test
    fun `stops audio playback when paused`() {
        val v = VideoPlayer { JavascriptEvaluator { } }

        val m = MockContinuation()
        v.playVideo(m, SoundOrVideoTag("a.mp4"))

        assertNull(m.result)
        v.onVideoPaused()

        val result = assertNotNull(m.result)
        assertThat("failure", result.isFailure)
        val exception = result.exceptionOrNull() as? MediaException
        assertThat("Audio is stopped", exception != null && exception.continuationBehavior == MediaErrorBehavior.STOP_MEDIA)
    }

    // TODO: use a mock - couldn't get mockk working here
    @InternalForInheritanceCoroutinesApi
    class MockContinuation : CancellableContinuation<Unit> {
        var result: Result<Unit>? = null

        override val context: CoroutineContext
            get() = TODO("Not yet implemented")
        override val isActive: Boolean
            get() = TODO("Not yet implemented")
        override val isCancelled: Boolean
            get() = TODO("Not yet implemented")
        override val isCompleted: Boolean
            get() = TODO("Not yet implemented")

        override fun cancel(cause: Throwable?): Boolean {
            TODO("Not yet implemented")
        }

        @InternalCoroutinesApi
        override fun completeResume(token: Any) {
            TODO("Not yet implemented")
        }

        @InternalCoroutinesApi
        override fun initCancellability() {
            TODO("Not yet implemented")
        }

        override fun invokeOnCancellation(handler: CompletionHandler) {
            TODO("Not yet implemented")
        }

        @InternalCoroutinesApi
        override fun tryResumeWithException(exception: Throwable): Any? {
            TODO("Not yet implemented")
        }

        @ExperimentalCoroutinesApi
        override fun CoroutineDispatcher.resumeUndispatchedWithException(exception: Throwable) {
            TODO("Not yet implemented")
        }

        @ExperimentalCoroutinesApi
        override fun CoroutineDispatcher.resumeUndispatched(value: Unit) {
            TODO("Not yet implemented")
        }

        @InternalCoroutinesApi
        override fun <R : Unit> tryResume(
            value: R,
            idempotent: Any?,
            onCancellation: ((cause: Throwable, value: R, context: CoroutineContext) -> Unit)?,
        ): Any? {
            TODO("Not yet implemented")
        }

        @InternalCoroutinesApi
        override fun tryResume(
            value: Unit,
            idempotent: Any?,
        ): Any? {
            TODO("Not yet implemented")
        }

        @Deprecated(
            "Use the overload that also accepts the `value` and the coroutine context in lambda",
            replaceWith = ReplaceWith("resume(value) { cause, _, _ -> onCancellation(cause) }"),
            level = DeprecationLevel.WARNING,
        )
        @ExperimentalCoroutinesApi
        override fun resume(
            value: Unit,
            onCancellation: ((cause: Throwable) -> Unit)?,
        ) {
            TODO("Not yet implemented")
        }

        override fun <R : Unit> resume(
            value: R,
            onCancellation: ((cause: Throwable, value: R, context: CoroutineContext) -> Unit)?,
        ) {
            TODO("Not yet implemented")
        }

        override fun resumeWith(result: Result<Unit>) {
            this.result = result
        }
    }
}
