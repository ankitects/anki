// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.analytics

import org.apache.commons.io.input.BoundedReader
import java.io.Reader

/**
 * Reads the main thread's Java/Kotlin frames from an Android ANR dump.
 *
 * Preserves native-method markers, but omits native backtraces, lock details and other threads.
 * Bounds both the input scanned and the captured frames; returns null if no frames are recognized.
 */
internal fun Reader.readAnrMainThreadStackTrace(): Array<StackTraceElement>? {
    val lines = BoundedReader(this, MAX_TRACE_SCAN_CHARS).buffered().lineSequence()
    val frames = mutableListOf<StackTraceElement>()
    var inMainThread = false
    var frameChars = 0
    for (line in lines) {
        if (!inMainThread) {
            inMainThread = line.startsWith("\"main\" ")
            continue
        }
        if (line.isBlank() || line.startsWith('"') || line.startsWith("-----")) break
        val frame = line.toAnrStackTraceElement() ?: continue
        if (frameChars + line.length > MAX_STACK_TRACE_CHARS) break
        frames.add(frame)
        frameChars += line.length
        if (frames.size == MAX_STACK_TRACE_FRAMES) break
    }
    return frames.takeIf { it.isNotEmpty() }?.toTypedArray()
}

private fun String.toAnrStackTraceElement(): StackTraceElement? {
    val match = STACK_FRAME.matchEntire(this) ?: return null
    val (className, methodName, source) = match.destructured
    return when {
        source.equals("Native method", ignoreCase = true) -> StackTraceElement(className, methodName, null, -2)
        source == "Unknown Source" || source == "Unresolved" -> StackTraceElement(className, methodName, null, -1)
        else ->
            StackTraceElement(
                className,
                methodName,
                source.substringBeforeLast(':').ifEmpty { null },
                source.substringAfterLast(':', "").toIntOrNull() ?: -1,
            )
    }
}

private val STACK_FRAME = Regex("""^\s+at (.+)\.([^.()]+)\(([^()]*)\)$""")
private const val MAX_TRACE_SCAN_CHARS = 256 * 1024
private const val MAX_STACK_TRACE_CHARS = 16 * 1024
private const val MAX_STACK_TRACE_FRAMES = 64
