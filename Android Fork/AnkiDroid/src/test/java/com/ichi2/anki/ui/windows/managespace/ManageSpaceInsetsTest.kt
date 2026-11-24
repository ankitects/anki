// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui.windows.managespace

import android.content.Intent
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.common.utils.dp
import com.ichi2.anki.databinding.FragmentSettingsBinding
import com.ichi2.testutils.dispatchInsets
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith

/** Edge-to-edge inset handling for [ManageSpaceActivity] */
@RunWith(AndroidJUnit4::class)
class ManageSpaceInsetsTest : RobolectricTest() {
    @Test
    fun `the page is padded past a side navigation bar and cutout`() =
        withManageSpace { activity ->
            // landscape with 3-button navigation: the navigation bar is a side inset and the
            // camera cutout is on the opposite side
            activity.dispatchInsets(navBarRight = 48.dp, cutoutLeft = 32.dp)

            assertThat(activity.binding.root.paddingLeft, equalTo(32.dp.toPx(targetContext)))
            assertThat(activity.binding.root.paddingRight, equalTo(48.dp.toPx(targetContext)))
        }

    @Test
    fun `the list scrolls under the navigation bar but its last row rests above it`() =
        withManageSpace { activity ->
            activity.dispatchInsets(navBarBottom = 48.dp)

            assertThat(activity.preferenceList.paddingBottom, equalTo(48.dp.toPx(targetContext)))
        }

    @Test
    fun `a side navigation bar clearing the corner removes the bottom buffer`() =
        withManageSpace { activity ->
            activity.dispatchInsets(navBarRight = 48.dp, bottomCornerRadius = 34.dp)

            assertThat(activity.preferenceList.paddingBottom, equalTo(0))
        }

    private val ManageSpaceActivity.manageSpaceFragment: ManageSpaceFragment
        get() = fragment as ManageSpaceFragment

    private val ManageSpaceActivity.binding: FragmentSettingsBinding
        get() = FragmentSettingsBinding.bind(manageSpaceFragment.requireView())

    private val ManageSpaceActivity.preferenceList: RecyclerView get() = manageSpaceFragment.listView

    private fun withManageSpace(block: (ManageSpaceActivity) -> Unit) {
        val activity =
            startActivityNormallyOpenCollectionWithIntent(
                ManageSpaceActivity::class.java,
                Intent(targetContext, ManageSpaceActivity::class.java),
            )
        advanceRobolectricLooper()
        block(activity)
    }
}
