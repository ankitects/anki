// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.reviewreminders

import android.app.Activity
import android.os.Build
import android.view.View
import android.view.WindowManager
import androidx.annotation.IdRes
import androidx.core.view.ViewCompat
import androidx.core.view.marginBottom
import androidx.core.view.marginRight
import androidx.core.view.marginTop
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.ichi2.anki.InitialActivity
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.common.utils.Dp
import com.ichi2.anki.common.utils.dp
import com.ichi2.anki.databinding.FragmentReminderTroubleshootingBinding
import com.ichi2.anki.databinding.FragmentScheduleRemindersBinding
import com.ichi2.anki.preferences.PreferencesActivity
import com.ichi2.anki.preferences.PreferencesFragment
import com.ichi2.anki.reviewreminders.ScheduleRemindersFragment.FragmentHost
import com.ichi2.anki.utils.ConfigAwareSingleFragmentActivity
import com.ichi2.testutils.windowInsetsOf
import com.ichi2.testutils.withWritePermissions
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Edge-to-edge inset handling for [ScheduleRemindersFragment] and [ReminderTroubleshootingFragment]
 * in the hosts where they fill the window ([FragmentHost.SETTINGS] and
 * [FragmentHost.STANDALONE_ACTIVITY]): the fragments apply the system bar insets themselves.
 *
 * [ReviewRemindersScreenshotTest] covers the study options hosts.
 */
@RunWith(AndroidJUnit4::class)
class ReviewRemindersInsetsTest : RobolectricTest() {
    /** The height of the status bar simulated by [windowInsetsOf] */
    private val statusBarHeight = 24.dp

    /** The size of the simulated navigation bar: its height, or its width when on the side of the screen */
    private val navigationBarSize = 48.dp

    /** The 'add reminder' button's margin, from the layout XML */
    private val fabMargin = 16.dp

    /** The list's bottom padding, from the layout XML: lets content scroll clear of the 'add reminder' button */
    private val listBottomPadding = 84.dp

    @Test
    fun `standalone host - toolbar content clears the status bar and cutout`() =
        withStandaloneScheduleReminders { activity, binding ->
            activity.dispatchInsets(cutoutLeft = 32.dp)

            assertThat(
                "toolbar content is pushed clear of the status bar",
                binding.appbar.paddingTop,
                equalTo(statusBarHeight.toPx(targetContext)),
            )
            assertThat(
                "toolbar content clears the cutout",
                binding.appbar.paddingLeft,
                equalTo(32.dp.toPx(targetContext)),
            )
        }

    @Test
    fun `standalone host - button and list content clear the navigation bar`() =
        withStandaloneScheduleReminders { activity, binding ->
            activity.dispatchInsets(navBarBottom = navigationBarSize)

            assertThat(
                "the 'add reminder' button rests above the navigation bar",
                binding.floatingActionButtonAdd.marginBottom,
                equalTo((fabMargin + navigationBarSize).toPx(targetContext)),
            )
            assertThat(
                "scrolled list content clears the navigation bar",
                binding.recyclerView.paddingBottom,
                equalTo((listBottomPadding + navigationBarSize).toPx(targetContext)),
            )
        }

    @Test
    fun `standalone host - content clears a side navigation bar`() =
        withStandaloneScheduleReminders { activity, binding ->
            // landscape with 3-button navigation: the navigation bar is a side inset
            activity.dispatchInsets(navBarRight = navigationBarSize)

            assertThat(
                "toolbar content clears the side navigation bar",
                binding.appbar.paddingRight,
                equalTo(navigationBarSize.toPx(targetContext)),
            )
            assertThat(
                "list content clears the side navigation bar",
                binding.recyclerView.paddingRight,
                equalTo(navigationBarSize.toPx(targetContext)),
            )
            assertThat(
                "the 'add reminder' button clears the side navigation bar",
                binding.floatingActionButtonAdd.marginRight,
                equalTo((fabMargin + navigationBarSize).toPx(targetContext)),
            )
        }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.R) // enableEdgeToEdge uses SHORT_EDGES below API 30
    fun `standalone host - the window renders into a display cutout`() =
        withStandaloneScheduleReminders { activity, _ ->
            assertThat(
                "the window renders into the cutout instead of being letterboxed",
                activity.window.attributes.layoutInDisplayCutoutMode,
                equalTo(WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS),
            )
        }

    @Test
    @Config(sdk = [Build.VERSION_CODES.Q])
    @Suppress("DEPRECATION") // systemUiVisibility: how WindowCompat un-fits the window below API 30
    fun `standalone host - the window stays edge to edge after pausing`() =
        withWritePermissions {
            InitialActivity.decideStorageIfUndecided(targetContext)
            val intent = ScheduleRemindersFragment.getIntent(targetContext, ReviewReminderScope.Global)
            ActivityScenario.launch<ConfigAwareSingleFragmentActivity>(intent).use { scenario ->
                advanceRobolectricLooper()
                scenario.onActivity { activity ->
                    assertThat(
                        "the window lays out edge to edge: scrolled content renders underneath the bottom bar",
                        activity.window.decorView.systemUiVisibility and DECOR_FITS_FLAGS,
                        equalTo(DECOR_FITS_FLAGS),
                    )
                }

                scenario.moveToState(Lifecycle.State.STARTED)
                advanceRobolectricLooper()
                scenario.onActivity { activity ->
                    assertThat(
                        "pausing leaves the window alone: the host renders every screen edge to edge",
                        activity.window.decorView.systemUiVisibility and DECOR_FITS_FLAGS,
                        equalTo(DECOR_FITS_FLAGS),
                    )
                }
            }
        }

    @Test
    fun `standalone host - troubleshooting content clears the system bars`() =
        withStandaloneTroubleshooting { activity, binding ->
            activity.dispatchInsets(navBarBottom = navigationBarSize)

            assertThat(
                "the toolbar is pushed clear of the status bar: a margin keeps the icon and title aligned",
                binding.troubleshootingToolbar.marginTop,
                equalTo(statusBarHeight.toPx(targetContext)),
            )
            assertThat(
                "the root is not padded: scrolling content renders underneath the bottom bar",
                binding.root.paddingBottom,
                equalTo(0),
            )
            assertThat(
                "the scroll view is not clipped: content renders into its padding while scrolling",
                binding.scrollView.clipToPadding,
                equalTo(false),
            )
            assertThat(
                "the end of the scrolled content clears the navigation bar",
                binding.scrollView.paddingBottom,
                equalTo(navigationBarSize.toPx(targetContext)),
            )
        }

    @Test
    fun `settings host - collapsible toolbar clears the status bar`() =
        withSettingsScheduleReminders { activity, binding ->
            val styleExpandedTitleMarginStart = binding.collapsingToolbarLayout.expandedTitleMarginStart
            activity.dispatchInsets(navBarBottom = navigationBarSize, cutoutLeft = 32.dp)

            assertThat(
                "the root does not pad itself; the app bar handles the inset",
                binding.root.paddingTop,
                equalTo(0),
            )
            assertThat(
                "the collapsible toolbar is pushed clear of the status bar",
                binding.toolbar.top,
                equalTo(statusBarHeight.toPx(targetContext)),
            )
            // the padding sits inside the collapsing layout, not on the app bar: the
            // collapsed content scrim covers the collapsing layout's bounds, and must
            // extend behind the cutout to match the rest of the toolbar
            assertThat(
                "toolbar content clears the cutout",
                binding.collapsingToolbarLayout.paddingLeft,
                equalTo(32.dp.toPx(targetContext)),
            )
            assertThat(
                "the app bar is not padded: it would push the scrim off the cutout",
                binding.appbar.paddingLeft,
                equalTo(0),
            )
            assertThat(
                "the expanded title clears the cutout: it ignores the layout's padding",
                binding.collapsingToolbarLayout.expandedTitleMarginStart,
                equalTo(styleExpandedTitleMarginStart + 32.dp.toPx(targetContext)),
            )
            assertThat(
                "the root's layout does not offset the list again: its padding handles the cutout",
                binding.recyclerView.left,
                equalTo(0),
            )
            assertThat(
                "the 'add reminder' button rests above the navigation bar",
                binding.floatingActionButtonAdd.marginBottom,
                equalTo((fabMargin + navigationBarSize).toPx(targetContext)),
            )
        }

    /**
     * Dispatches realistic system-bar insets, which Robolectric otherwise reports as zero.
     *
     * Dispatches at `android.R.id.content` rather than at the decor: for windows which have not
     * (yet) opted into edge-to-edge, Robolectric's decor and AppCompat's sub-decor consume the
     * insets before they reach the activity's views. Edge-to-edge devices deliver the insets to
     * the views, which is the behavior under test.
     */
    private fun Activity.dispatchInsets(
        navBarBottom: Dp = 0.dp,
        navBarRight: Dp = 0.dp,
        cutoutLeft: Dp = 0.dp,
    ) {
        val insets =
            with(targetContext) {
                windowInsetsOf(navBarBottom = navBarBottom, navBarRight = navBarRight, cutoutLeft = cutoutLeft)
            }
        ViewCompat.dispatchApplyWindowInsets(findViewById(android.R.id.content), insets)
        // relayout synchronously so the insets affect view positions before the test asserts
        val decor = window.decorView
        decor.measure(
            View.MeasureSpec.makeMeasureSpec(decor.width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(decor.height, View.MeasureSpec.EXACTLY),
        )
        decor.layout(0, 0, decor.width, decor.height)
    }

    /** Launches [ScheduleRemindersFragment] in its standalone activity */
    private fun withStandaloneScheduleReminders(block: (ConfigAwareSingleFragmentActivity, FragmentScheduleRemindersBinding) -> Unit) {
        val intent = ScheduleRemindersFragment.getIntent(targetContext, ReviewReminderScope.Global)
        ActivityScenario.launch<ConfigAwareSingleFragmentActivity>(intent).use { scenario ->
            advanceRobolectricLooper()
            scenario.onActivity { activity ->
                block(activity, FragmentScheduleRemindersBinding.bind(activity.fragment!!.requireView()))
            }
        }
    }

    /** Launches [ReminderTroubleshootingFragment] in the standalone activity */
    private fun withStandaloneTroubleshooting(block: (ConfigAwareSingleFragmentActivity, FragmentReminderTroubleshootingBinding) -> Unit) =
        withStandaloneScheduleReminders { activity, _ ->
            val view =
                activity.supportFragmentManager.showFragment(
                    R.id.fragment_container,
                    ReminderTroubleshootingFragment.newInstance(FragmentHost.STANDALONE_ACTIVITY),
                )
            block(activity, FragmentReminderTroubleshootingBinding.bind(view))
        }

    /** Launches [ScheduleRemindersFragment] hosted in the settings screen */
    private fun withSettingsScheduleReminders(block: (PreferencesActivity, FragmentScheduleRemindersBinding) -> Unit) {
        ActivityScenario.launch<PreferencesActivity>(PreferencesActivity.getIntent(targetContext)).use { scenario ->
            scenario.onActivity { activity ->
                val fm = (activity.fragment as PreferencesFragment).childFragmentManager
                val view =
                    fm.showFragment(
                        R.id.settings_container,
                        ScheduleRemindersFragment.newInstance(ReviewReminderScope.Global, FragmentHost.SETTINGS),
                    )
                block(activity, FragmentScheduleRemindersBinding.bind(view))
            }
        }
    }

    /** Replaces the contents of [containerId] with [fragment] and returns the fragment's laid-out view */
    private fun FragmentManager.showFragment(
        @IdRes containerId: Int,
        fragment: Fragment,
    ): View {
        commit { replace(containerId, fragment) }
        advanceRobolectricLooper()
        return findFragmentById(containerId)!!.requireView()
    }

    companion object {
        /**
         * The `systemUiVisibility` layout flags which `WindowCompat.setDecorFitsSystemWindows`
         * sets on API < 30 for a window which does not fit the system windows.
         */
        @Suppress("DEPRECATION")
        private const val DECOR_FITS_FLAGS =
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
    }
}
