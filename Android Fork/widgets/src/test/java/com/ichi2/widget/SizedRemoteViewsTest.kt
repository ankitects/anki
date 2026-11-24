// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.widget

import android.annotation.SuppressLint
import android.appwidget.AppWidgetManager
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.widget.RemoteViews
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.widgets.R
import com.ichi2.testutils.AndroidTest
import com.ichi2.testutils.targetContext
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class SizedRemoteViewsTest : AndroidTest {
    @Test
    @Config(sdk = [Build.VERSION_CODES.R])
    fun `builds landscape and portrait before Android 12`() {
        assertEquals(LANDSCAPE_AND_PORTRAIT, builtSizes(sizeRange()))
    }

    @Test
    @SuppressLint("NewApi")
    fun `builds each reported size from Android 12`() {
        val sizes = arrayListOf(SizeF(262f, 102f), SizeF(349f, 220f), SizeF(175f, 220f))

        assertEquals(sizes, builtSizes(sizeRange().apply { putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, sizes) }))
    }

    @Test
    fun `builds landscape and portrait when Android 12 reports no sizes`() {
        assertEquals(LANDSCAPE_AND_PORTRAIT, builtSizes(sizeRange()))
    }

    private fun builtSizes(options: Bundle): List<SizeF> =
        buildList {
            sizedRemoteViews(options) { size ->
                add(size)
                RemoteViews(targetContext.packageName, R.layout.widget_add_note)
            }
        }

    private fun sizeRange() =
        Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 262)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 349)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 102)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 220)
        }

    companion object {
        private val LANDSCAPE_AND_PORTRAIT = listOf(SizeF(349f, 102f), SizeF(262f, 220f))
    }
}
