// SPDX-License-Identifier: GPL-3.0-or-later
package com.ichi2.anki

/**
 * A single rule in a human-readable baseline profile (`baseline-prof.txt`):
 * either a [ClassRule] or a [MethodRule].
 *
 * ```
 * Lcom/ichi2/anki/DeckPicker;                 (class rule: no flags)
 * SPLcom/ichi2/anki/DeckPicker;->onResume()V  (method rule: flags S and P)
 * ```
 *
 * A class rule preloads the class; a method rule marks the method for
 * ahead-of-time compilation, with flags recording when it was observed
 * executing while the profile was generated.
 *
 * See https://developer.android.com/topic/performance/baselineprofiles/manually-create-measure#manually-define-rules
 */
sealed interface BaselineProfileEntry {
    /** JVM class descriptor, e.g. `Lcom/ichi2/anki/DeckPicker;`. */
    val classDescriptor: String

    /** [classDescriptor] as a source-style name: `com.ichi2.anki.DeckPicker`. */
    val className: String
        get() = classDescriptor.removePrefix("L").removeSuffix(";").replace('/', '.')

    /** Preloads the class at install time. */
    data class ClassRule(
        override val classDescriptor: String,
    ) : BaselineProfileEntry {
        override fun toString() = classDescriptor
    }

    /**
     * Marks the method for ahead-of-time compilation.
     *
     * @param flags when the method was observed executing; never empty
     * @param methodSignature method name and JVM type signature, e.g. `onResume()V`
     */
    data class MethodRule(
        val flags: Set<Flag>,
        override val classDescriptor: String,
        val methodSignature: String,
    ) : BaselineProfileEntry {
        init {
            require(flags.isNotEmpty()) { "method rule requires at least one flag: '$this'" }
        }

        /** Method name without its type signature: `onResume`. */
        val methodName: String get() = methodSignature.substringBefore('(')

        override fun toString(): String {
            // Flag.entries, not flags: emit in the format's canonical H, S, P order.
            val flagString = Flag.entries.filter { it in flags }.joinToString("") { it.char.toString() }
            return "$flagString$classDescriptor$METHOD_SEPARATOR$methodSignature"
        }
    }

    /** When a method was observed executing during profile generation. */
    enum class Flag(
        val char: Char,
    ) {
        /** Executed enough times to be considered 'hot'. */
        HOT('H'),

        /** Executed during app startup; AOT-compiled for it. */
        STARTUP('S'),

        /** Executed after startup (e.g. scrolling, animation). */
        POST_STARTUP('P'),
        ;

        companion object {
            fun fromChar(char: Char): Flag? = entries.firstOrNull { it.char == char }
        }
    }

    companion object {
        private const val METHOD_SEPARATOR = "->"

        fun parseProfile(lines: List<String>): List<BaselineProfileEntry> =
            lines
                .filter { it.isNotBlank() && !it.startsWith('#') }
                .map { parse(it) }

        fun parse(line: String): BaselineProfileEntry {
            val descriptorStart = line.indexOfFirst { Flag.fromChar(it) == null }
            require(descriptorStart >= 0 && line[descriptorStart] == 'L') { "malformed rule: '$line'" }
            val flags = line.take(descriptorStart).mapTo(mutableSetOf()) { Flag.fromChar(it)!! }
            val descriptor = line.substring(descriptorStart)

            if (METHOD_SEPARATOR in descriptor) {
                return MethodRule(
                    flags = flags,
                    classDescriptor = descriptor.substringBefore(METHOD_SEPARATOR),
                    methodSignature = descriptor.substringAfter(METHOD_SEPARATOR),
                )
            }
            // Flags record when a *method* executed; a flagged class rule is malformed.
            require(flags.isEmpty()) { "malformed rule: '$line'" }
            return ClassRule(descriptor)
        }
    }
}
