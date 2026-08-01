//noinspection MissingCopyrightHeader #8659
package com.ichi2.anki.api

/**
 * Definitions of the basic model
 */
internal class BasicModel {
    companion object {
        @JvmField // required for API
        @Suppress("ktlint:standard:property-naming")
        var FIELDS = arrayOf("Front", "Back")

        // List of card names that will be used in AnkiDroid (one for each direction of learning)
        @JvmField // required for API
        val CARD_NAMES = arrayOf("Card 1")

        // Template for the question of each card
        @JvmField // required for API
        val QFMT = arrayOf("{{Front}}")

        @JvmField // required for API
        val AFMT =
            arrayOf(
                """{{FrontSide}}
        
        |<hr id="answer">
        
        |{{Back}}
                """.trimMargin(),
            )
    }
}
