// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.gradle

import org.gradle.api.provider.Property
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration

/**
 * Writes a Markdown table of test-suite results to the file at [path][Params.path].
 *
 * Shared across all test tasks so the header is written once
 *
 * Safe to use in the Configuration Cache due to it being in `/buildSrc/`
 */
abstract class TestSummaryService : BuildService<TestSummaryService.Params> {
    interface Params : BuildServiceParameters {
        /** The path to output to. Typically defined in CI by the `$GITHUB_STEP_SUMMARY` env var */
        val path: Property<String>
    }

    private val headerWritten = AtomicBoolean(false)

    /** Appends one row to the Markdown table; writes the header on the first call. */
    fun append(row: Row) {
        val prefix = if (headerWritten.compareAndSet(false, true)) Row.TABLE_HEADER else ""
        val toAppend = prefix + row.toMarkdown()
        Files.writeString(
            Paths.get(parameters.path.get()),
            toAppend,
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND,
        )
    }

    /** A row of the summary table. */
    data class Row(
        val suite: String,
        val result: String,
        val duration: Duration,
        val testCount: Long,
        val passed: Long,
        val failed: Long,
        val skipped: Long,
    ) {
        fun toMarkdown(): String {
            val seconds = (duration.inWholeMilliseconds % 60000) / 1000.0
            // both digits are zero-padded: 01m 05.220s
            val elapsed = "%02dm %06.3fs".format(duration.inWholeMinutes, seconds)
            return "| $suite | **$result** | $elapsed | " +
                "$testCount | $passed | $failed | $skipped |\n"
        }

        companion object {
            /** Written once before any rows. Column order here must match [Row]'s fields. */
            val TABLE_HEADER =
                """
                |## Test Results
                |
                || Suite | Result | Duration | Tests | Passed | Failed | Skipped |
                || ----- | ------ | -------- | ----- | ------ | ------ | ------- |
                |
                """.trimMargin()
        }
    }
}
