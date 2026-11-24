// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.lint.rules

import com.android.tools.lint.checks.infrastructure.LintDetectorTest.kotlin
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import org.junit.Test

class PreferenceKeyNamingDetectorTest {
    @Test
    fun `reports constants in preference calls`() {
        lint()
            .allowMissingSdk()
            .files(
                sharedPreferences,
                editExtension,
                preferenceUtils,
                preferenceExtensions,
                kotlin(
                    """
                    package test
                    import android.content.SharedPreferences
                    import androidx.core.content.edit
                    import com.ichi2.anki.preferences.get
                    import com.ichi2.preferences.getOrSetString

                    const val LAST_DECK = "lastDeck"
                    object Keys {
                        val SYNC_KEY = "sync"
                    }
                    class Screen {
                        companion object {
                            const val THEME = "theme"
                        }
                        fun use(prefs: SharedPreferences) {
                            prefs.getBoolean(LAST_DECK, false)
                            prefs.getString(Keys.SYNC_KEY, null)
                            prefs.contains(Screen.THEME)
                            prefs.edit().putBoolean(LAST_DECK, true).apply()
                            prefs.edit { remove(THEME) }
                            prefs.get(LAST_DECK)
                            prefs.getOrSetString(Keys.SYNC_KEY) { "value" }
                        }
                    }
                    """,
                ).indented(),
            ).issues(PreferenceKeyNamingDetector.ISSUE)
            .run()
            .expectErrorCount(7)
            .expectContains("Preference key constants must start with PREF_: LAST_DECK")
            .expectContains("Preference key constants must start with PREF_: SYNC_KEY")
            .expectContains("Preference key constants must start with PREF_: THEME")
    }

    @Test
    fun `reports string constants passed to the booleanPref delegate`() {
        lint()
            .allowMissingSdk()
            .files(
                sharedPreferences,
                prefsRepository,
                kotlin(
                    """
                    package test
                    import com.ichi2.anki.settings.PrefsRepository

                    class Repository(prefs: PrefsRepository) {
                        var whiteboard by prefs.booleanPref(WHITEBOARD_KEY, false)
                        var voice by prefs.booleanPref(PREF_VOICE, false)
                        var fromResource by prefs.booleanPref(KEY_RES_ID, false)

                        companion object {
                            private const val WHITEBOARD_KEY = "whiteboard"
                            private const val PREF_VOICE = "voice"
                            private const val KEY_RES_ID = 1
                        }
                    }
                    """,
                ).indented(),
            ).issues(PreferenceKeyNamingDetector.ISSUE)
            .run()
            .expectErrorCount(1)
            .expectContains("Preference key constants must start with PREF_: WHITEBOARD_KEY")
    }

    @Test
    fun `allows compliant constants and keys that are not constants`() {
        lint()
            .allowMissingSdk()
            .files(
                sharedPreferences,
                kotlin(
                    """
                    package test
                    import android.content.SharedPreferences

                    const val PREF_LAST_DECK = "lastDeck"
                    object Keys {
                        val PREF_SYNC = "sync"
                    }
                    class Screen {
                        companion object {
                            val dynamicKey = "deck_" + System.nanoTime()
                            var mutableKey = "mutable"
                        }
                        val instanceKey = "instance"
                        fun use(prefs: SharedPreferences, key: String) {
                            prefs.getBoolean(PREF_LAST_DECK, false)
                            prefs.getString(Keys.PREF_SYNC, null)
                            prefs.getString(dynamicKey, null)
                            prefs.getString(mutableKey, null)
                            prefs.getString(instanceKey, null)
                            prefs.getString(key, null)
                            prefs.getString("literal", null)
                        }
                    }
                    """,
                ).indented(),
            ).issues(PreferenceKeyNamingDetector.ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun `ignores values and unrelated methods with the same name`() {
        lint()
            .allowMissingSdk()
            .files(
                sharedPreferences,
                kotlin(
                    """
                    package test
                    import android.content.SharedPreferences

                    const val PREF_THEME = "theme"
                    const val DARK = "dark"
                    fun write(prefs: SharedPreferences) {
                        prefs.edit().putString(PREF_THEME, DARK).apply()
                    }
                    class Bundle {
                        fun getBoolean(key: String, defaultValue: Boolean) = defaultValue
                        fun read() = getBoolean(DARK, false)
                    }
                    """,
                ).indented(),
            ).issues(PreferenceKeyNamingDetector.ISSUE)
            .run()
            .expectClean()
    }

    companion object {
        private val sharedPreferences =
            kotlin(
                """
                package android.content
                interface SharedPreferences {
                    fun getBoolean(key: String, defValue: Boolean): Boolean
                    fun getString(key: String, defValue: String?): String?
                    fun contains(key: String): Boolean
                    fun edit(): Editor
                    interface Editor {
                        fun putBoolean(key: String, value: Boolean): Editor
                        fun putString(key: String, value: String?): Editor
                        fun remove(key: String): Editor
                        fun apply()
                    }
                }
                """,
            ).indented()
        private val editExtension =
            kotlin(
                """
                @file:JvmName("SharedPreferencesKt")
                package androidx.core.content
                import android.content.SharedPreferences
                fun SharedPreferences.edit(action: SharedPreferences.Editor.() -> Unit) {
                    edit().action()
                }
                """,
            ).indented()
        private val preferenceUtils =
            kotlin(
                """
                @file:JvmName("PreferenceUtilsKt")
                package com.ichi2.anki.preferences
                import android.content.SharedPreferences
                fun SharedPreferences.get(key: String): Any? = null
                """,
            ).indented()
        private val preferenceExtensions =
            kotlin(
                """
                @file:JvmName("PreferenceExtensionsKt")
                package com.ichi2.preferences
                import android.content.SharedPreferences
                fun SharedPreferences.getOrSetString(key: String, supplier: () -> String): String = supplier()
                """,
            ).indented()
        private val prefsRepository =
            kotlin(
                """
                package com.ichi2.anki.settings
                import kotlin.properties.ReadWriteProperty
                open class PrefsRepository {
                    fun booleanPref(key: String, defaultValue: Boolean): ReadWriteProperty<Any?, Boolean> = TODO()
                    fun booleanPref(keyResId: Int, defaultValue: Boolean): ReadWriteProperty<Any?, Boolean> = TODO()
                }
                """,
            ).indented()
    }
}
