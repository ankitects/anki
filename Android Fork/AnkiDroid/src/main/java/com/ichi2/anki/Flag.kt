// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.Context
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import anki.search.SearchNode
import anki.search.searchNode
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.Flag.Companion.queryDisplayNames
import com.ichi2.anki.common.utils.ext.flagLabels
import com.ichi2.anki.common.utils.ext.getStringOrNull
import com.ichi2.anki.ui.internationalization.sentenceCase
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.json.JSONObject

@Serializable(with = FlagSerializer::class)
enum class Flag(
    val code: Int,
    /**
     * A Unique ID representing this flag in a menu.
     */
    @IdRes val id: Int,
    /**
     * Flag drawn to represents this flag.
     */
    @DrawableRes val drawableRes: Int,
    /**
     * Color for the background of cards with this flag in the card browser.
     */
    @ColorRes val browserColorRes: Int?,
) {
    NONE(0, R.id.flag_none, R.drawable.ic_flag_transparent, browserColorRes = null),
    RED(1, R.id.flag_red, R.drawable.ic_flag_red, R.color.flag_red),
    ORANGE(
        2,
        R.id.flag_orange,
        R.drawable.ic_flag_orange,
        R.color.flag_orange,
    ),
    GREEN(3, R.id.flag_green, R.drawable.ic_flag_green, R.color.flag_green),
    BLUE(4, R.id.flag_blue, R.drawable.ic_flag_blue, R.color.flag_blue),
    PINK(5, R.id.flag_pink, R.drawable.ic_flag_pink, R.color.flag_pink),
    TURQUOISE(
        6,
        R.id.flag_turquoise,
        R.drawable.ic_flag_turquoise,
        R.color.flag_turquoise,
    ),
    PURPLE(
        7,
        R.id.flag_purple,
        R.drawable.ic_flag_purple,
        R.color.flag_purple,
    ),
    ;

    /**
     * Retrieves the name associated with the flag. This may be user-defined
     *
     * @see queryDisplayNames - more efficient
     */
    private fun displayName(
        labels: FlagLabels,
        context: Context,
    ): String {
        // NONE may not be renamed
        if (this == NONE) return defaultDisplayName(context)
        return labels.getLabel(this) ?: defaultDisplayName(context)
    }

    private fun defaultDisplayName(context: Context): String =
        when (this) {
            NONE -> with(context) { TR.sentenceCase.noFlag }
            RED -> TR.actionsFlagRed()
            ORANGE -> TR.actionsFlagOrange()
            GREEN -> TR.actionsFlagGreen()
            BLUE -> TR.actionsFlagBlue()
            PINK -> TR.actionsFlagPink()
            TURQUOISE -> TR.actionsFlagTurquoise()
            PURPLE -> TR.actionsFlagPurple()
        }

    /**
     * Renames the flag
     *
     * @param newName The new name for the flag.
     */
    suspend fun rename(newName: String) {
        val labels = FlagLabels.loadFromColConfig()
        labels.updateName(this, newName)
    }

    /**
     * Creates a [SearchNode.Flag] for use in a [SearchNode].
     *
     * Prefer [toSearchNode] for simple [SearchNode] creation.
     *
     * ```kotlin
     * val red = Flag.RED
     * val node = searchNode { flag = red.toSearchValue() }
     * ```
     */
    fun toSearchValue(): SearchNode.Flag =
        when (this) {
            // The protobuf value does NOT correspond to the value in the DB
            // SearchNode.Flag.FLAG_RED == 2; Flag.RED.code == 1
            NONE -> SearchNode.Flag.FLAG_NONE
            RED -> SearchNode.Flag.FLAG_RED
            ORANGE -> SearchNode.Flag.FLAG_ORANGE
            GREEN -> SearchNode.Flag.FLAG_GREEN
            BLUE -> SearchNode.Flag.FLAG_BLUE
            PINK -> SearchNode.Flag.FLAG_PINK
            TURQUOISE -> SearchNode.Flag.FLAG_TURQUOISE
            PURPLE -> SearchNode.Flag.FLAG_PURPLE
        }

    /**
     * Creates a [SearchNode] for building a search string.
     *
     * Use [toSearchValue] when generating a complex `SearchNode`
     *
     * ```kotlin
     * val searchNode = Flag.RED.toSearchNode()
     * val searchString = col.buildSearchString(listOf(searchNode))
     * ```
     */
    fun toSearchNode(): SearchNode = searchNode { flag = toSearchValue() }

    companion object {
        val MAX_CODE: Int = entries.maxOf { it.code }
        val MIN_CODE: Int = entries.minOf { it.code }

        fun fromCode(code: Int) = Flag.entries.first { it.code == code }

        /**
         * @return A mapping from each [Flag] to its display name (optionally user-defined)
         */
        suspend fun queryDisplayNames(context: Context): Map<Flag, String> {
            // load user-defined flag labels from the collection
            val labels = FlagLabels.loadFromColConfig()
            // either map to user-provided name, or translated name
            return Flag.entries.associateWith { it.displayName(labels, context) }
        }
    }
}

/**
 * User-defined labels for flags. Stored in the collection optionally as `{ "1": "Redd" }`
 * [Flag.NONE] does not have a label
 */
@JvmInline
private value class FlagLabels(
    val value: JSONObject,
) {
    /**
     * @return the user-defined label for the provided flag, or null if undefined
     * This is not supported for [Flag.NONE] and is validated outside this method
     */
    fun getLabel(flag: Flag): String? = value.getStringOrNull(flag.code.toString())

    suspend fun updateName(
        flag: Flag,
        newName: String,
    ) {
        value.put(flag.code.toString(), newName)
        withCol {
            config.flagLabels = value
        }
    }

    companion object {
        suspend fun loadFromColConfig() = FlagLabels(withCol { config.flagLabels })
    }
}

/** Serializer for [Flag] */
object FlagSerializer : KSerializer<Flag> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("Flag", PrimitiveKind.INT)

    override fun serialize(
        encoder: Encoder,
        value: Flag,
    ) {
        encoder.encodeInt(value.code)
    }

    override fun deserialize(decoder: Decoder): Flag {
        val code = decoder.decodeInt()
        return Flag.fromCode(code)
    }
}
