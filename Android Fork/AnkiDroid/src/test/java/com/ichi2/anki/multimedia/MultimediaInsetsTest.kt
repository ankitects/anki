// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.multimedia

import android.Manifest.permission.RECORD_AUDIO
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.common.utils.dp
import com.ichi2.anki.databinding.ActivityMultimediaBinding
import com.ichi2.anki.multimediacard.fields.AudioRecordingField
import com.ichi2.anki.multimediacard.fields.TextField
import com.ichi2.anki.multimediacard.impl.MultimediaEditableNote
import com.ichi2.testutils.dispatchInsets
import com.ichi2.testutils.grantPermissions
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith

/** Edge-to-edge inset handling for [MultimediaActivity], applied by [MultimediaFragment]. */
@RunWith(AndroidJUnit4::class)
class MultimediaInsetsTest : RobolectricTest() {
    @Test
    fun `app bar draws behind the status bar, with its content inset`() =
        withMultimedia { activity ->
            activity.dispatchInsets()

            assertThat(
                "the root does not consume the top inset, so the app bar draws behind the status bar",
                activity.binding.root.paddingTop,
                equalTo(0),
            )
            assertThat(
                "app bar content is pushed clear of the status bar",
                activity.binding.appBar.paddingTop,
                equalTo(24.dp.toPx(targetContext)),
            )
        }

    @Test
    fun `app bar is padded past a side navigation bar and cutout`() =
        withMultimedia { activity ->
            // landscape with 3-button navigation: the navigation bar is a side inset and the
            // camera cutout is on the opposite side
            activity.dispatchInsets(navBarRight = 48.dp, cutoutLeft = 32.dp)

            assertThat(activity.binding.appBar.paddingLeft, equalTo(32.dp.toPx(targetContext)))
            assertThat(activity.binding.appBar.paddingRight, equalTo(48.dp.toPx(targetContext)))
        }

    @Test
    fun `the fragment clears a side navigation bar and cutout`() =
        withMultimedia { activity ->
            activity.dispatchInsets(navBarRight = 48.dp, cutoutLeft = 32.dp)

            assertThat(activity.fragmentRoot.paddingLeft, equalTo(32.dp.toPx(targetContext)))
            assertThat(activity.fragmentRoot.paddingRight, equalTo(48.dp.toPx(targetContext)))
        }

    @Test
    fun `the done button clears the navigation bar`() =
        withMultimedia { activity ->
            activity.dispatchInsets(navBarBottom = 48.dp)

            assertThat(activity.fragmentRoot.paddingBottom, equalTo(48.dp.toPx(targetContext)))
        }

    @Test
    fun `the done button clears rounded display corners larger than the navigation bar`() =
        withMultimedia { activity ->
            activity.dispatchInsets(navBarBottom = 24.dp, bottomCornerRadius = 48.dp)

            assertThat(activity.fragmentRoot.paddingBottom, equalTo(48.dp.toPx(targetContext)))
        }

    @Test
    fun `a side navigation bar clearing the corner removes the bottom buffer`() =
        withMultimedia { activity ->
            // the bar is wider than the corner radius, so no vertical buffer is needed
            activity.dispatchInsets(navBarRight = 48.dp, bottomCornerRadius = 34.dp)

            assertThat(activity.fragmentRoot.paddingBottom, equalTo(0))
        }

    private val MultimediaActivity.binding: ActivityMultimediaBinding
        get() = ActivityMultimediaBinding.bind(findViewById(R.id.root_layout))

    /** The hosted fragment's own root, which [MultimediaFragment] insets */
    private val MultimediaActivity.fragmentRoot: View
        get() = supportFragmentManager.findFragmentById(R.id.fragment_container)!!.requireView()

    private fun withMultimedia(block: (MultimediaActivity) -> Unit) {
        grantPermissions(RECORD_AUDIO)
        // the recorder reads the note's initial field values, so the note needs a frozen field
        val note =
            MultimediaEditableNote().apply {
                setNumFields(1)
                setField(0, TextField().apply { text = "Front of the card" })
                freezeInitialFieldValues()
            }
        val extra = MultimediaActivityExtra(index = 0, field = AudioRecordingField(), note = note)
        block(
            startActivityNormallyOpenCollectionWithIntent(
                MultimediaActivity::class.java,
                AudioRecordingFragment.getIntent(targetContext, extra),
            ),
        )
    }
}
