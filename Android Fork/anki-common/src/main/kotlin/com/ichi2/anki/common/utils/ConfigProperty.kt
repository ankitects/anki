// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.common.utils

import anki.config.ConfigKey
import com.ichi2.anki.libanki.Config
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/** A property delegate backed by [Config]. */
internal class ConfigProperty<T>(
    private val read: Config.() -> T,
    private val write: Config.(T) -> Unit,
) : ReadWriteProperty<Config, T> {
    override fun getValue(
        thisRef: Config,
        property: KProperty<*>,
    ): T = thisRef.read()

    override fun setValue(
        thisRef: Config,
        property: KProperty<*>,
        value: T,
    ) = thisRef.write(value)

    /**
     * Converts between the stored value and the property's exposed type.
     *
     * ```kotlin
     * var Config.showAudioButtons by configProperty(ConfigKey.Bool.HIDE_AUDIO_PLAY_BUTTONS).mapped(
     *     decode = { hideAudioButtons -> !hideAudioButtons },
     *     encode = { showAudioButtons -> !showAudioButtons },
     * )
     * ```
     */
    fun <R> mapped(
        decode: (T) -> R,
        encode: (R) -> T,
    ): ConfigProperty<R> =
        ConfigProperty(
            read = { decode(read()) },
            write = { write(encode(it)) },
        )
}

/**
 * Delegates to [Config.getBool] and [Config.setBool].
 *
 * ```kotlin
 * var Config.ignoreAccents by configProperty(ConfigKey.Bool.IGNORE_ACCENTS_IN_SEARCH)
 * ```
 */
internal fun configProperty(key: ConfigKey.Bool): ConfigProperty<Boolean> =
    ConfigProperty(read = { getBool(key) }, write = { setBool(key, it) })

/**
 * Delegates to [Config.getString] and [Config.setString].
 *
 * ```kotlin
 * var Config.customScheduling by configProperty(ConfigKey.String.CARD_STATE_CUSTOMIZER)
 * ```
 */
internal fun configProperty(key: ConfigKey.String): ConfigProperty<String> =
    ConfigProperty(read = { getString(key) }, write = { setString(key, it) })

/**
 * Delegates to [Config.get(key, default)][Config.get] and [Config.set] for a nullable JSON setting.
 *
 * An absent key returns [missingValue]; JSON `null` or an undecodable value returns `null`.
 * Other backend errors propagate. Assigning `null` stores JSON `null`; it does not remove the key.
 *
 * ```kotlin
 * var Config.fsrs by jsonConfigProperty("fsrs", missingValue = false)
 * ```
 */
internal inline fun <reified T> jsonConfigProperty(
    key: String,
    missingValue: T? = null,
): ConfigProperty<T?> = ConfigProperty(read = { get<T?>(key, missingValue) }, write = { set(key, it) })

/**
 * Uses [default] whenever a nullable property reads `null`, without writing the fallback to the collection.
 *
 * ```kotlin
 * // Read as 4 when missing, JSON null, or invalid for Int.
 * val Config.rollover by jsonConfigProperty<Int>("rollover").orDefault(4)
 * ```
 */
internal fun <T : Any> ConfigProperty<T?>.orDefault(default: T): ConfigProperty<T> = mapped(decode = { it ?: default }, encode = { it })
