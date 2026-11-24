// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.gradle

object VersionCode {
    private const val MAX_BUMP = 100_000

    // Consume whole comments atomically so dots inside them cannot look like chained calls.
    private const val WHITESPACE_OR_COMMENTS = """(?>\s|//[^\r\n]*|/\*[\s\S]*?\*/)*"""

    // Ignore task property access such as versionCode.set(...).
    // Include chained calls across whitespace/comments so a numeric prefix cannot pass as a literal.
    private val assignment =
        Regex("""^\h*versionCode\b(?!\h*\.)\h*=?\s*([^\r\n]+(?:\R$WHITESPACE_OR_COMMENTS\.[^\r\n]*)*)""", RegexOption.MULTILINE)
    private val literal = Regex("""([1-9][0-9]*)\h*;?\h*(?://.*)?""")

    /**
     * Accepted formats, using digits only with no leading zeros:
     * - Groovy: `versionCode 22500202`
     * - Kotlin: `versionCode = 22500202`
     * - Whitespace: `    versionCode=22500202`
     * - Comment: `versionCode = 22500202 // beta`
     */
    fun read(buildScript: String): Int {
        val declaration = assignment.findAll(buildScript).singleOrNull()
        val number = declaration?.let { literal.matchEntire(it.groupValues[1].trim()) }
        return requireNotNull(number?.groupValues?.get(1)?.toIntOrNull()) {
            "Expected exactly one versionCode declaration with a positive integer literal (digits only, no leading zeros), e.g. versionCode = 22500202."
        }
    }

    /** Checks the increase in the base code, before the ABI prefix is added. */
    fun validate(
        previous: Int,
        code: Int?,
    ) {
        require(previous > 0 && code != null && code > 0) {
            "Previous and current versionCode must be positive: $previous -> $code"
        }
        val increase = code.toLong() - previous.toLong()
        require(increase in 0L..MAX_BUMP.toLong()) {
            "versionCode increase must be between 0 and $MAX_BUMP: " +
                "$previous -> $code (increase $increase)."
        }
    }
}
