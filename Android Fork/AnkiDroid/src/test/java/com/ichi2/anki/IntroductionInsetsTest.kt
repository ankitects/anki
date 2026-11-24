// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.Intent
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.common.utils.dp
import com.ichi2.anki.introduction.SetupCollectionFragment
import com.ichi2.testutils.dispatchInsets
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Edge-to-edge inset handling for [IntroductionActivity].
 *
 * The activity is a bare fragment host, so [SetupCollectionFragment] insets its own views. The top
 * gradient is decorative, so the fragment root only takes the bottom inset and the gradient reaches
 * the top and side edges. The side insets are taken by the content instead, so the
 * "Get Started"/"Sync from AnkiWeb" buttons clear the navigation bar and the rounded corners.
 */
@RunWith(AndroidJUnit4::class)
class IntroductionInsetsTest : RobolectricTest() {
    @Test
    fun `the gradient draws to the top and side edges`() =
        withIntroduction { activity ->
            activity.dispatchInsets(navBarRight = 48.dp, cutoutLeft = 32.dp)

            assertThat(
                "the root does not consume the top inset, so the gradient reaches the top edge",
                activity.root.paddingTop,
                equalTo(0),
            )
            assertThat("the gradient reaches the left edge", activity.root.paddingLeft, equalTo(0))
            assertThat("the gradient reaches the right edge", activity.root.paddingRight, equalTo(0))
        }

    @Test
    fun `content is padded past a side navigation bar and cutout`() =
        withIntroduction { activity ->
            // landscape with 3-button navigation: the navigation bar is a side inset and the
            // camera cutout is on the opposite side
            activity.dispatchInsets(navBarRight = 48.dp, cutoutLeft = 32.dp)

            assertThat(
                "content is padded past the cutout",
                activity.content.paddingLeft,
                equalTo(32.dp.toPx(targetContext)),
            )
            assertThat(
                "content is padded past the side navigation bar",
                activity.content.paddingRight,
                equalTo(48.dp.toPx(targetContext)),
            )
        }

    @Test
    fun `buttons clear the navigation bar`() =
        withIntroduction { activity ->
            activity.dispatchInsets(navBarBottom = 48.dp)

            assertThat(activity.root.paddingBottom, equalTo(48.dp.toPx(targetContext)))
        }

    @Test
    fun `buttons clear rounded display corners larger than the navigation bar`() =
        withIntroduction { activity ->
            activity.dispatchInsets(navBarBottom = 24.dp, bottomCornerRadius = 48.dp)

            assertThat(activity.root.paddingBottom, equalTo(48.dp.toPx(targetContext)))
        }

    @Test
    fun `a side navigation bar clearing the corner removes the bottom buffer`() =
        withIntroduction { activity ->
            // the bar is wider than the corner radius, so no vertical buffer is needed
            activity.dispatchInsets(navBarRight = 48.dp, bottomCornerRadius = 34.dp)

            assertThat(activity.root.paddingBottom, equalTo(0))
        }

    /** The root of [SetupCollectionFragment], which is full-bleed apart from the bottom inset */
    private val IntroductionActivity.root: View
        get() = supportFragmentManager.findFragmentById(R.id.fragment_container)!!.requireView()

    /** Everything except the gradient: the view taking the side insets */
    private val IntroductionActivity.content: View
        get() = findViewById(R.id.intro_content)

    private fun withIntroduction(block: (IntroductionActivity) -> Unit) =
        block(
            startActivityNormallyOpenCollectionWithIntent(
                IntroductionActivity::class.java,
                Intent(targetContext, IntroductionActivity::class.java),
            ),
        )
}
