// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.compose.ui.dialogs

import android.content.Context
import android.content.DialogInterface
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import com.ichi2.compose.ui.preview.AnkiDroidPreview
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DialogTypographyTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `text metrics match the View dialog`() = compareText()

    @Test
    @Config(qualifiers = "w411dp-h891dp-420dpi")
    fun `text metrics match at fractional density`() = compareText()

    @Test
    fun `large text metrics match the View dialog`() = compareText(fontScale = 2f)

    @Test
    fun `long paragraph line breaks match the View dialog`() =
        compareText(
            fontScale = 2f,
            message = "This item and its children will be permanently deleted. This action cannot be undone. ".repeat(8),
        )

    private fun compareText(
        fontScale: Float = 1f,
        message: String = "First line\nSecond line",
    ) {
        RuntimeEnvironment.setFontScale(fontScale)
        lateinit var context: Context
        compose.setContent {
            AnkiDroidPreview {
                context = LocalContext.current
                MessageDialog("Title", message, "Delete", {}, {})
            }
        }
        val textLayouts =
            listOf("Title", message, "Delete").map { text ->
                val results = mutableListOf<TextLayoutResult>()
                compose
                    .onNodeWithText(text, useUnmergedTree = true)
                    .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
                results.single()
            }
        val windowWidth =
            compose
                .onNode(isDialog())
                .fetchSemanticsNode()
                .boundsInRoot.width
                .toInt()
        lateinit var dialog: AlertDialog
        compose.runOnIdle {
            dialog =
                AlertDialog
                    .Builder(context)
                    .setTitle("Title")
                    .setMessage(message)
                    .setPositiveButton("Delete", null)
                    .setNegativeButton("Cancel", null)
                    .show()
            // Compare typesetting at the same width, independently of window sizing.
            dialog.window!!.setLayout(windowWidth, WindowManager.LayoutParams.WRAP_CONTENT)
        }
        try {
            compose.runOnIdle {
                val views =
                    listOf(
                        dialog.findViewById<TextView>(androidx.appcompat.R.id.alertTitle)!!,
                        dialog.findViewById<TextView>(android.R.id.message)!!,
                        dialog.getButton(DialogInterface.BUTTON_POSITIVE),
                    )
                views.zip(textLayouts).forEach { (view, text) ->
                    val layout = view.layout
                    assertEquals(layout.lineCount, text.lineCount, view.text.toString())
                    assertEquals(layout.height.toFloat(), text.size.height.toFloat(), 1f, "height: ${view.text}")
                    for (line in 0 until layout.lineCount) {
                        assertEquals(layout.getLineStart(line), text.getLineStart(line), "line start: ${view.text}")
                        assertEquals(layout.getLineEnd(line), text.getLineEnd(line), "line end: ${view.text}")
                        assertEquals(layout.getLineBaseline(line).toFloat(), text.getLineBaseline(line), 1f, "baseline: ${view.text}")
                        val width = text.getLineRight(line) - text.getLineLeft(line)
                        assertEquals(layout.getLineMax(line), width, 1f, "width: ${view.text}")
                    }
                }
            }
        } finally {
            compose.runOnIdle { dialog.dismiss() }
        }
    }
}
