// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.workarounds

import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.multimedia.MultimediaActivity.Companion.EXTRA_MEDIA_OPTIONS
import com.ichi2.anki.multimedia.MultimediaImageFragment
import com.ichi2.testutils.launchFragmentInContainer
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.notNullValue
import org.hamcrest.Matchers.sameInstance
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/**
 * [SafeWebViewLayout.onRenderProcessGone] requires API 26+; Robolectric's default SDK (targetSdk) satisfies that.
 */
@RunWith(AndroidJUnit4::class)
class SafeWebViewLayoutTest : RobolectricTest() {
    @Test
    fun `onRenderProcessGone with stale WebView destroys stale instance only`() {
        withMultimediaWebView { layout, fragment ->
            val currentWebView = layout.getChildAt(0) as WebView
            val staleWebView = WebView(fragment.requireContext())

            layout.onRenderProcessGone(staleWebView)

            assertThat(
                "stale WebView is destroyed",
                shadowOf(staleWebView).wasDestroyCalled(),
                equalTo(true),
            )
            assertThat(
                "current WebView is still attached",
                layout.getChildAt(0),
                sameInstance(currentWebView),
            )
            assertThat("current WebView is not destroyed", shadowOf(currentWebView).wasDestroyCalled(), equalTo(false))
        }
    }

    @Test
    fun `destroy after early-return onRenderProcessGone does not destroy WebView twice`() {
        val layout = DestroyCountingSafeWebViewLayout(targetContext)
        val webView = layout.getChildAt(0) as WebView

        layout.onRenderProcessGone(webView)
        assertThat(shadowOf(webView).wasDestroyCalled(), equalTo(true))
        assertThat(layout.webViewDestroyCallCount, equalTo(1))

        layout.destroy()
        layout.destroy()

        assertThat(
            "destroy does not invoke WebView.destroy again",
            layout.webViewDestroyCallCount,
            equalTo(1),
        )
    }

    @Test
    fun `onRenderProcessGone skips recreation when layout is not attached to window`() {
        withMultimediaWebView { layout, fragment ->
            val webView = layout.getChildAt(0) as WebView
            val parent = layout.parent as ViewGroup
            parent.removeView(layout)
            assertThat(fragment.view, notNullValue())
            assertThat(layout.isAttachedToWindow, equalTo(false))

            layout.onRenderProcessGone(webView)

            assertThat(shadowOf(webView).wasDestroyCalled(), equalTo(true))
            assertThat(webView.parent, equalTo(null))
            assertThat(layout.childCount, equalTo(0))

            val blockedUrl = "https://blocked.example/"
            layout.loadUrl(blockedUrl)
            assertThat(
                "loadUrl no-ops while WebView is destroyed",
                shadowOf(webView).lastLoadedUrl,
                equalTo(null),
            )
        }
    }

    @Test
    fun `reattaching after intentional destroy does not recover inner WebView`() {
        withMultimediaWebView { layout, _ ->
            val parent = layout.parent as ViewGroup
            val deadWebView = layout.getChildAt(0) as WebView
            layout.destroy()
            assertThat("destroy removes the dead WebView", layout.childCount, equalTo(0))

            parent.removeView(layout)
            parent.addView(layout)
            assertThat("intentional destroy is terminal", layout.childCount, equalTo(0))

            val blockedUrl = "https://blocked-after-intentional-destroy.example/"
            layout.loadUrl(blockedUrl)
            assertThat(
                shadowOf(deadWebView).lastLoadedUrl,
                equalTo("about:blank"),
            )
        }
    }

    @Test
    fun `destroy after crash cleanup prevents recovery on reattach`() {
        withMultimediaWebView { layout, _ ->
            val webView = layout.getChildAt(0) as WebView
            val parent = layout.parent as ViewGroup
            parent.removeView(layout)
            assertThat(layout.isAttachedToWindow, equalTo(false))

            layout.onRenderProcessGone(webView)
            assertThat(shadowOf(webView).wasDestroyCalled(), equalTo(true))
            assertThat(layout.childCount, equalTo(0))

            layout.destroy()
            parent.addView(layout)

            assertThat(layout.isAttachedToWindow, equalTo(true))
            assertThat("owner teardown must prevent crash recovery", layout.childCount, equalTo(0))
        }
    }

    @Test
    fun `recovery is skipped when orphaned layout reattaches outside fragment view`() {
        launchFragmentInContainer<SafeWebViewLayoutHostFragment>().use { scenario ->
            lateinit var layout: SafeWebViewLayout
            lateinit var staleFragmentRoot: ViewGroup
            lateinit var deadWebView: WebView

            scenario.onFragment { fragment ->
                staleFragmentRoot = fragment.requireView() as ViewGroup
                layout = SafeWebViewLayout(fragment.requireContext())
                staleFragmentRoot.addView(layout)
                deadWebView = layout.getChildAt(0) as WebView
                staleFragmentRoot.removeView(layout)
                layout.onRenderProcessGone(deadWebView)
                assertThat(layout.childCount, equalTo(0))
            }

            scenario.recreate()

            scenario.onFragment { fragment ->
                val currentFragmentRoot = fragment.requireView()
                staleFragmentRoot.addView(
                    layout,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT,
                    ),
                )
                (currentFragmentRoot.parent as ViewGroup).addView(staleFragmentRoot)

                assertThat(layout.childCount, equalTo(0))
                layout.loadUrl("https://blocked.example/")
                assertThat(
                    "loadUrl no-ops when recovery is skipped for orphaned layout",
                    shadowOf(deadWebView).lastLoadedUrl,
                    equalTo(null),
                )
                assertThat(layout.childCount, equalTo(0))
            }
        }
    }

    @Test
    fun `reattaching layout after skipped recreation recovers inner WebView`() {
        withMultimediaWebView { layout, _ ->
            val webView = layout.getChildAt(0) as WebView
            val parent = layout.parent as ViewGroup
            parent.removeView(layout)
            layout.onRenderProcessGone(webView)
            assertThat(layout.childCount, equalTo(0))

            parent.addView(layout)
            assertThat(layout.childCount, equalTo(1))

            val recoveryUrl = "https://recovery.example/"
            layout.loadUrl(recoveryUrl)
            val recoveredWebView = layout.getChildAt(0) as WebView
            assertThat(
                "loadUrl works again after reattachment recovery",
                shadowOf(recoveredWebView).lastLoadedUrl,
                equalTo(recoveryUrl),
            )
        }
    }

    private fun withMultimediaWebView(block: (SafeWebViewLayout, MultimediaImageFragment) -> Unit) {
        launchFragmentInContainer<MultimediaImageFragment>(
            Bundle().apply { putSerializable(EXTRA_MEDIA_OPTIONS, MultimediaImageFragment.ImageOptions.GALLERY) },
        ).use { scenario ->
            scenario.onFragment { fragment ->
                block(fragment.binding.multimediaWebView, fragment)
            }
        }
    }
}
