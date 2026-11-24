// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.previewer

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import androidx.appcompat.R as AppCompatR
import com.google.android.material.R as MaterialR

class PreviewerHelpersTest {
    private val colors =
        mapOf(
            android.R.attr.colorBackground to 0xFF000001.toInt(),
            android.R.attr.textColor to 0xFF000002.toInt(),
            AppCompatR.attr.colorPrimary to 0xFF000003.toInt(),
            MaterialR.attr.colorOnPrimary to 0xFF000004.toInt(),
            MaterialR.attr.colorSecondary to 0xFF000005.toInt(),
            MaterialR.attr.colorOnSecondary to 0xFF000006.toInt(),
            MaterialR.attr.colorSurface to 0xFF000009.toInt(),
            MaterialR.attr.colorOnSurface to 0xFF00000A.toInt(),
            MaterialR.attr.colorOnBackground to 0xFF00000B.toInt(),
        )

    private fun resolveColor(attr: Int): Int = colors.getValue(attr)

    @Test
    fun `day mode targets plain root`() {
        val css = rootColorsCss(nightMode = false, ::resolveColor)
        assertTrue(css.startsWith(":root {"), css)
    }

    @Test
    fun `night mode targets night-mode root`() {
        val css = rootColorsCss(nightMode = true, ::resolveColor)
        assertTrue(css.startsWith(":root[class*=night-mode] {"), css)
    }

    @Test
    fun `variables are resolved from the matching theme attributes`() {
        val expected =
            mapOf(
                "--canvas" to "#000001",
                "--fg" to "#000002",
                "--ankidroid-color-primary" to "#000003",
                "--ankidroid-color-on-primary" to "#000004",
                "--ankidroid-color-secondary" to "#000005",
                "--ankidroid-color-on-secondary" to "#000006",
                "--ankidroid-color-surface" to "#000009",
                "--ankidroid-color-on-surface" to "#00000A",
                "--ankidroid-color-background" to "#000001",
                "--ankidroid-color-on-background" to "#00000B",
            )

        val css = rootColorsCss(nightMode = false, ::resolveColor)

        assertEquals(expected, parseVariables(css))
    }

    private fun parseVariables(css: String): Map<String, String> =
        Regex("""(--[\w-]+):\s*(#[0-9A-F]{6});""")
            .findAll(css)
            .associate { it.groupValues[1] to it.groupValues[2] }
}
