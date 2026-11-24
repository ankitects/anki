// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.cardviewer

import android.content.res.Configuration
import android.webkit.WebView
import androidx.core.content.edit
import androidx.core.view.children
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.R
import com.ichi2.anki.ReviewerTest
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.browser.IdsFile
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.previewer.CardViewerActivity
import com.ichi2.anki.previewer.PreviewerFragment
import com.ichi2.anki.previewer.stdHtml
import com.ichi2.anki.workarounds.SafeWebViewLayout
import com.ichi2.testutils.createTransientDirectory
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import kotlin.test.assertEquals

/** Issue 20776: MathJax needs text and em-based layout to scale together. */
@RunWith(AndroidJUnit4::class)
class CardTextScaleTest : RobolectricTest() {
    override fun getCollectionStorageMode() = CollectionStorageMode.IN_MEMORY_WITH_MEDIA

    @Test
    fun `legacy cards preserve system font scale independently of card and image zoom`() {
        val card = addBasicNote("\\(\\sqrt{a^2}\\)").firstCard()
        val preferences = targetContext.sharedPrefs()
        preferences.edit {
            putInt("cardZoom", 80)
            putInt("imageZoom", 120)
        }
        for (fontScale in listOf(0.85f, 1f, 1.3f)) {
            val context = contextWithFontScale(fontScale)
            val renderer = AndroidCardRenderContext.createInstance(context, col, TypeAnswer.createInstance(preferences))
            val html = renderer.renderCard(col, card, SingleCardSide.FRONT).html

            assertEquals(fontScale.toDouble(), cssZoom(html, "html"), 0.000001)
            assertEquals(0.8, cssZoom(html, "body"))
            assertEquals(1.2, cssZoom(html, "img"))
        }
    }

    @Test
    fun `shared reviewer and previewer HTML preserves system font scale`() {
        for (fontScale in listOf(0.85f, 1f, 1.3f)) {
            val html = stdHtml(contextWithFontScale(fontScale))

            assertEquals(fontScale.toDouble(), cssZoom(html, "html"), 0.000001)
        }
    }

    @Test
    fun `legacy reviewer disables text-only zoom`() {
        addBasicNote()
        val reviewer = ReviewerTest.startReviewer(this)

        assertEquals(100, reviewer.webView!!.settings.textZoom)
    }

    @Test
    fun `previewer disables text-only zoom including after WebView recreation`() {
        val note = addBasicNote()
        val intent =
            PreviewerFragment.getIntent(
                targetContext,
                IdsFile(createTransientDirectory(), note.cardIds(col), IdsFile.Purpose.PREVIEW),
                0,
            )
        Robolectric.buildActivity(CardViewerActivity::class.java, intent).use { controller ->
            controller.setup()
            val activity = controller.get()
            val layout = activity.findViewById<SafeWebViewLayout>(R.id.web_view_layout)
            val webView = layout.children.filterIsInstance<WebView>().single()
            assertEquals(100, webView.settings.textZoom)

            webView.settings.textZoom = 130
            (activity.fragment as PreviewerFragment).onWebViewRecreated()
            assertEquals(100, webView.settings.textZoom)
        }
    }

    private fun contextWithFontScale(fontScale: Float) =
        targetContext.createConfigurationContext(
            Configuration(targetContext.resources.configuration).apply { this.fontScale = fontScale },
        )

    /** A missing zoom rule leaves the element at its default scale. */
    private fun cssZoom(
        html: String,
        selector: String,
    ): Double =
        Regex("""\b$selector\s*\{\s*zoom:\s*([\d.]+)""")
            .find(html)
            ?.groupValues
            ?.get(1)
            ?.toDouble() ?: 1.0
}
