// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.gradle

/**
 * The stable name Codecov uses to associate uploads from one module's unit tests.
 *
 * @see CodecovCarryforwardFlags
 */
@JvmInline
internal value class CodecovFlag(
    private val modulePath: String,
) {
    val name: String get() = modulePath.removePrefix(":").replace(':', '_') + "-test"

    init {
        val flagName = name
        require(flagName.length in 1..45 && flagName.matches(Regex("[a-zA-Z0-9_.-]+"))) {
            "Invalid Codecov flag '$flagName'. Use 1 to 45 letters, digits, underscores, hyphens, or dots."
        }
        check('_' !in modulePath && !flagName.startsWith('.')) {
            "Invalid AnkiDroid module path '$modulePath'. " +
                "Underscores are reserved for nested module separators, and leading dots hide coverage reports from CI."
        }
    }
}

/**
 * Keeps Codecov's carryforward rules in sync with the modules covered by the app's unit tests.
 *
 * CI tags each module's unit test coverage upload with a stable label called a flag. Carryforward lets
 * Codecov reuse an earlier report for a flag when the current commit has no upload for it.
 * Removing a retired module's flag prevents Codecov from counting its old hits on code that
 * still exists in other modules. See [Codecov's carryforward guide](https://docs.codecov.com/docs/carryforward-flags).
 */
internal object CodecovCarryforwardFlags {
    /**
     * Checks the generated `flags:` block in `.codecov.yml` for the expected format and order.
     * Ignores blank lines and standalone comments and prints a replacement block on mismatch.
     */
    fun verify(
        config: String,
        activeFlags: Collection<CodecovFlag>,
    ) {
        val lines = config.lines().filterNot { it.isBlank() || it.trimStart().startsWith('#') }
        val flagsHeader = lines.indexOf("flags:")
        val flagsBlock =
            if (flagsHeader >= 0) {
                lines.drop(flagsHeader + 1).takeWhile { it.startsWith(' ') }
            } else {
                emptyList()
            }
        val expected =
            activeFlags.sortedBy { it.name }.mapIndexed { index, flag ->
                val rule = if (index == 0) "&unit {carryforward: true}" else "*unit"
                "  ${flag.name}: $rule"
            }
        check(flagsBlock == expected) {
            "Replace the flags section in .codecov.yml with:\nflags:\n${expected.joinToString("\n")}"
        }
    }
}
