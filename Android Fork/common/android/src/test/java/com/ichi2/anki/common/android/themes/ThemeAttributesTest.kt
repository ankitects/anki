// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.common.android.themes

import android.content.Context
import android.util.TypedValue
import androidx.test.core.app.ApplicationProvider
import com.ichi2.anki.common.android.R
import com.ichi2.anki.settings.enums.DayTheme
import com.ichi2.anki.settings.enums.NightTheme
import com.ichi2.anki.settings.enums.themedContext
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import com.google.android.material.R as MaterialR

/**
 * Every theme attribute declared by `:common:android` must have a value in every app theme.
 *
 * An attribute that a theme doesn't set fails to resolve where it's used: Plain and E-ink inherit
 * Light, and Black inherits Dark, but Light and Dark don't share a parent.
 */
@RunWith(RobolectricTestRunner::class)
class ThemeAttributesTest {
    private val appContext: Context get() = ApplicationProvider.getApplicationContext()

    /** Set by the styles applied to each answer button, not by the themes. */
    private val styleAttributes = setOf("answerButtonBackground")

    @Test
    fun `every theme attribute resolves in every app theme`() {
        // nonTransitiveRClass: this R only declares the attributes defined in :common:android
        val attributes = R.attr::class.java.fields.filter { it.name !in styleAttributes }

        val unresolved =
            (DayTheme.entries + NightTheme.entries).flatMap { theme ->
                val themed = theme.themedContext(appContext).theme
                attributes
                    .filter { attribute ->
                        val value = TypedValue()
                        val resolved = themed.resolveAttribute(attribute.getInt(null), value, true)
                        !resolved || value.type == TypedValue.TYPE_ATTRIBUTE || value.type == TypedValue.TYPE_NULL
                    }.map { "${theme.name}: ${it.name}" }
            }

        assertEquals(emptyList(), unresolved, "theme attributes without a value")
    }

    /**
     * Material 3 falls back to its baseline purple scheme for a role a theme doesn't set.
     * Dynamic colors and theme overlays replace these roles, so they must be complete and opaque.
     */
    @Test
    fun `every app theme sets every Material color role`() {
        val issues =
            (DayTheme.entries + NightTheme.entries).flatMap { theme ->
                val themed = theme.themedContext(appContext)
                materialColorRoles.mapNotNull { (name, attribute) ->
                    val value = TypedValue()
                    if (!themed.theme.resolveAttribute(attribute, value, true)) return@mapNotNull "${theme.name}: $name is unset"
                    val source = value.resourceId.takeIf { it != 0 }?.let { appContext.resources.getResourceEntryName(it) }
                    when {
                        source != null && materialBaselinePrefixes.any { source.startsWith(it) } ->
                            "${theme.name}: $name uses Material's $source"
                        value.type !in TypedValue.TYPE_FIRST_COLOR_INT..TypedValue.TYPE_LAST_COLOR_INT ->
                            "${theme.name}: $name is not a color"
                        value.data ushr 24 != 0xFF -> "${theme.name}: $name is translucent"
                        else -> null
                    }
                }
            }

        assertEquals(emptyList(), issues, "Material color roles")
    }

    private val materialBaselinePrefixes = listOf("m3_", "material_dynamic_")

    private val materialColorRoles: List<Pair<String, Int>> =
        listOf(
            "colorPrimary" to androidx.appcompat.R.attr.colorPrimary,
            "colorOnPrimary" to MaterialR.attr.colorOnPrimary,
            "colorPrimaryContainer" to MaterialR.attr.colorPrimaryContainer,
            "colorOnPrimaryContainer" to MaterialR.attr.colorOnPrimaryContainer,
            "colorPrimaryInverse" to MaterialR.attr.colorPrimaryInverse,
            "colorPrimaryFixed" to MaterialR.attr.colorPrimaryFixed,
            "colorPrimaryFixedDim" to MaterialR.attr.colorPrimaryFixedDim,
            "colorOnPrimaryFixed" to MaterialR.attr.colorOnPrimaryFixed,
            "colorOnPrimaryFixedVariant" to MaterialR.attr.colorOnPrimaryFixedVariant,
            "colorSecondary" to MaterialR.attr.colorSecondary,
            "colorOnSecondary" to MaterialR.attr.colorOnSecondary,
            "colorSecondaryContainer" to MaterialR.attr.colorSecondaryContainer,
            "colorOnSecondaryContainer" to MaterialR.attr.colorOnSecondaryContainer,
            "colorSecondaryFixed" to MaterialR.attr.colorSecondaryFixed,
            "colorSecondaryFixedDim" to MaterialR.attr.colorSecondaryFixedDim,
            "colorOnSecondaryFixed" to MaterialR.attr.colorOnSecondaryFixed,
            "colorOnSecondaryFixedVariant" to MaterialR.attr.colorOnSecondaryFixedVariant,
            "colorTertiary" to MaterialR.attr.colorTertiary,
            "colorOnTertiary" to MaterialR.attr.colorOnTertiary,
            "colorTertiaryContainer" to MaterialR.attr.colorTertiaryContainer,
            "colorOnTertiaryContainer" to MaterialR.attr.colorOnTertiaryContainer,
            "colorTertiaryFixed" to MaterialR.attr.colorTertiaryFixed,
            "colorTertiaryFixedDim" to MaterialR.attr.colorTertiaryFixedDim,
            "colorOnTertiaryFixed" to MaterialR.attr.colorOnTertiaryFixed,
            "colorOnTertiaryFixedVariant" to MaterialR.attr.colorOnTertiaryFixedVariant,
            "colorError" to androidx.appcompat.R.attr.colorError,
            "colorOnError" to MaterialR.attr.colorOnError,
            "colorErrorContainer" to MaterialR.attr.colorErrorContainer,
            "colorOnErrorContainer" to MaterialR.attr.colorOnErrorContainer,
            "android:colorBackground" to android.R.attr.colorBackground,
            "colorOnBackground" to MaterialR.attr.colorOnBackground,
            "colorSurface" to MaterialR.attr.colorSurface,
            "colorOnSurface" to MaterialR.attr.colorOnSurface,
            "colorSurfaceVariant" to MaterialR.attr.colorSurfaceVariant,
            "colorOnSurfaceVariant" to MaterialR.attr.colorOnSurfaceVariant,
            "colorSurfaceInverse" to MaterialR.attr.colorSurfaceInverse,
            "colorOnSurfaceInverse" to MaterialR.attr.colorOnSurfaceInverse,
            "colorSurfaceDim" to MaterialR.attr.colorSurfaceDim,
            "colorSurfaceBright" to MaterialR.attr.colorSurfaceBright,
            "colorSurfaceContainerLowest" to MaterialR.attr.colorSurfaceContainerLowest,
            "colorSurfaceContainerLow" to MaterialR.attr.colorSurfaceContainerLow,
            "colorSurfaceContainer" to MaterialR.attr.colorSurfaceContainer,
            "colorSurfaceContainerHigh" to MaterialR.attr.colorSurfaceContainerHigh,
            "colorSurfaceContainerHighest" to MaterialR.attr.colorSurfaceContainerHighest,
            "colorOutline" to MaterialR.attr.colorOutline,
            "colorOutlineVariant" to MaterialR.attr.colorOutlineVariant,
        )
}
