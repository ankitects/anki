// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.dialogs

import android.graphics.Rect
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import com.google.testing.junit.testparameterinjector.TestParameter
import com.ichi2.anki.ScreenshotTest
import com.ichi2.anki.ScreenshotTest.DeviceConfig.DESKTOP
import com.ichi2.anki.common.utils.dp
import com.ichi2.anki.databinding.DialogChangeNoteTypeBinding
import com.ichi2.anki.databinding.DialogFieldsBinding
import com.ichi2.anki.databinding.DialogTemplatesBinding
import com.ichi2.anki.databinding.ViewTabLayoutIconOnStartBinding
import com.ichi2.anki.dialogs.ChangeNoteTypeViewModel.Tab
import com.ichi2.anki.withCardBrowser
import com.ichi2.testutils.simulateSystemBars
import com.ichi2.testutils.windowInsetsOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.RuntimeEnvironment

@ScreenshotTest.IncludeDevices(DESKTOP)
class ChangeNoteTypeDialogScreenshotTest : ScreenshotTest() {
    enum class LargeTextScreen(
        val qualifiers: String,
    ) {
        NARROW_PORTRAIT("+w320dp-h568dp-port"),
        LANDSCAPE("+land"),
    }

    @Test
    fun portrait() =
        captureChangeNoteType(
            "portrait",
            with(targetContext) { windowInsetsOf(navBarBottom = 48.dp) },
        ) { dialog -> assertTabsFillWidth(dialog) }

    @Test
    fun gestureNavigation() =
        captureChangeNoteType(
            "gesture_navigation",
            with(targetContext) { windowInsetsOf(navBarBottom = 24.dp) },
        )

    @Test
    fun portraitCutout() =
        captureChangeNoteType(
            "portrait_cutout",
            with(targetContext) { windowInsetsOf(navBarBottom = 48.dp, cutoutTop = 40.dp) },
        )

    @Test
    fun landscape() {
        RuntimeEnvironment.setQualifiers("+land")
        captureChangeNoteType(
            "landscape",
            with(targetContext) { windowInsetsOf(navBarRight = 48.dp, cutoutLeft = 32.dp) },
        ) { dialog -> assertTabsFillWidth(dialog) }
    }

    @Test
    fun mappingsFitViewport(
        @TestParameter tab: Tab,
    ) = captureChangeNoteType(
        "${tab.name.lowercase()}_fit_viewport",
        with(targetContext) { windowInsetsOf(navBarBottom = 48.dp) },
        tab = tab,
    ) { dialog ->
        val (scrollView, _) = dialog.mappingViews(tab)
        assertFalse("The mappings fit without scrolling", scrollView.canScrollVertically(1))
        scrollMappingsToBottom(dialog, tab, requireScrolling = false)
    }

    @Test
    fun longNoteTypeNamesWithLargeText(
        @TestParameter screen: LargeTextScreen,
    ) {
        RuntimeEnvironment.setQualifiers(screen.qualifiers)
        RuntimeEnvironment.setFontScale(2f)
        captureChangeNoteType(
            "long_note_type_names_large_text_${screen.name.lowercase()}",
            with(targetContext) { windowInsetsOf(navBarBottom = 48.dp) },
            prepareCollection = {
                val noteType = col.notetypes.byName("Basic")!!
                noteType.name = "A very long note type name that should leave room for mapping fields and templates"
                for (number in 3..10) {
                    col.notetypes.addFieldModChanged(noteType, col.notetypes.newField("Field $number"))
                }
                col.notetypes.save(noteType)
            },
        ) { dialog ->
            val binding = dialog.binding
            assertTrue("The mappings have a viewport", binding.changeNoteTypePager.height > 0)
            scrollMappingsToBottom(dialog, requireScrolling = false)
        }
    }

    @Test
    fun templatesTabWithLargeText() {
        RuntimeEnvironment.setQualifiers("+w320dp-h568dp-port")
        RuntimeEnvironment.setFontScale(2f)
        captureChangeNoteType(
            "templates_tab_large_text",
            with(targetContext) { windowInsetsOf(navBarBottom = 48.dp) },
            tab = Tab.Templates,
        ) { dialog ->
            val binding = dialog.binding
            val root = dialog.requireView()
            val originalWidth = root.layoutParams.width
            // Expanding and shrinking the same view must restore the appropriate tab widths.
            root.updateLayoutParams { width = root.width * 2 }
            advanceRobolectricLooper()
            assertTabsFillWidth(dialog)
            root.updateLayoutParams { width = originalWidth }
            advanceRobolectricLooper()
            val tab = binding.changeNoteTypeTabLayout.getTabAt(Tab.Templates.position)!!
            val tabBinding = ViewTabLayoutIconOnStartBinding.bind(tab.customView!!)
            val icon = tabBinding.tabIcon
            val visibleBounds = Rect()
            assertTrue("The selected tab icon is visible", icon.getGlobalVisibleRect(visibleBounds))
            assertEquals("The selected tab icon is fully visible", icon.width, visibleBounds.width())
            val text = tabBinding.tabText
            assertEquals("The selected tab label fits", 0, text.layout.getEllipsisCount(0))
        }
    }

    @Test
    fun manyMappingsWithLargeTextInLandscape(
        @TestParameter tab: Tab,
    ) {
        RuntimeEnvironment.setQualifiers("+land")
        RuntimeEnvironment.setFontScale(2f)
        captureManyMappings("many_${tab.name.lowercase()}_large_text_landscape", tab)
    }

    @Test
    fun manyMappingsScrolledToBottom(
        @TestParameter tab: Tab,
    ) = captureManyMappings("many_${tab.name.lowercase()}_scrolled_to_bottom", tab)

    private fun captureManyMappings(
        name: String,
        tab: Tab,
    ) {
        val noteType = col.notetypes.byName("Basic")!!
        when (tab) {
            Tab.Fields -> {
                for (number in 3..30) {
                    val name = if (number == 30) "A long final field name that wraps onto several lines of text" else "Field $number"
                    col.notetypes.addFieldModChanged(noteType, col.notetypes.newField(name))
                }
            }
            Tab.Templates -> {
                for (number in 2..30) {
                    val name = if (number == 30) "A long final template name that wraps onto several lines of text" else "Card $number"
                    val template =
                        col.notetypes.newTemplate(name).apply {
                            qfmt = "{{Front}}"
                            afmt = "{{Back}}"
                        }
                    col.notetypes.addTemplateModChanged(noteType, template)
                }
            }
        }
        col.notetypes.save(noteType)

        captureChangeNoteType(
            name,
            with(targetContext) { windowInsetsOf(navBarBottom = 48.dp) },
            tab = tab,
        ) { dialog ->
            scrollMappingsToBottom(dialog, tab)
        }
    }

    private val ChangeNoteTypeDialog.binding
        get() = DialogChangeNoteTypeBinding.bind(requireView())

    private fun ChangeNoteTypeDialog.mappingViews(tab: Tab): Pair<ScrollView, ViewGroup> =
        when (tab) {
            Tab.Fields -> {
                val view =
                    childFragmentManager.fragments
                        .filterIsInstance<ChangeNoteTypeDialog.SelectFieldsFragment>()
                        .single()
                        .requireView()
                val binding = DialogFieldsBinding.bind(view)
                binding.root to binding.fieldsContainer
            }
            Tab.Templates -> {
                val view =
                    childFragmentManager.fragments
                        .filterIsInstance<ChangeNoteTypeDialog.SelectTemplateFragment>()
                        .single()
                        .requireView()
                val binding = DialogTemplatesBinding.bind(view)
                binding.root to binding.templatesContainer
            }
        }

    private fun assertTabsFillWidth(dialog: ChangeNoteTypeDialog) {
        val tabs = dialog.binding.changeNoteTypeTabLayout
        val widths = (0 until tabs.tabCount).map { tabs.getTabAt(it)!!.view.width }
        assertEquals("The tabs fill the available width", tabs.width - tabs.paddingLeft - tabs.paddingRight, widths.sum())
        assertTrue("The tabs have equal widths", widths.max() - widths.min() <= 1)
    }

    private fun scrollMappingsToBottom(
        dialog: ChangeNoteTypeDialog,
        tab: Tab = Tab.Fields,
        requireScrolling: Boolean = true,
    ) {
        val (scrollView, mappings) = dialog.mappingViews(tab)
        val canScroll = scrollView.canScrollVertically(1)
        if (requireScrolling) {
            assertTrue("Mappings extend below the viewport", canScroll)
        }
        val lastRow = mappings.getChildAt(mappings.childCount - 1) as ViewGroup
        val label = lastRow.getChildAt(1) as TextView
        val labelFits = label.height <= scrollView.height - scrollView.paddingTop - scrollView.paddingBottom
        if (!labelFits) {
            val labelBounds = Rect()
            label.getDrawingRect(labelBounds)
            scrollView.offsetDescendantRectToMyCoords(label, labelBounds)
            scrollView.scrollTo(0, labelBounds.top - scrollView.paddingTop)
            advanceRobolectricLooper()
            assertLineVisible(label, 0)
        }
        scrollView.scrollTo(0, scrollView.getChildAt(0).bottom)
        advanceRobolectricLooper()
        if (canScroll) {
            assertTrue("The mappings have scrolled", scrollView.scrollY > 0)
        }

        val visibleBounds = Rect()
        assertTrue("The last mapping label is visible", label.getGlobalVisibleRect(visibleBounds))
        if (labelFits) {
            assertEquals("The last mapping label is fully visible", label.height, visibleBounds.height())
        } else {
            // A tall label must scroll, but its final line must remain reachable.
            assertLineVisible(label, label.layout.lineCount - 1)
        }
    }

    private fun assertLineVisible(
        label: TextView,
        line: Int,
    ) {
        val visibleBounds = Rect()
        assertTrue("The mapping label is visible", label.getGlobalVisibleRect(visibleBounds))
        val location = IntArray(2)
        label.getLocationOnScreen(location)
        val lineTop = location[1] + label.totalPaddingTop + label.layout.getLineTop(line)
        val lineBottom = location[1] + label.totalPaddingTop + label.layout.getLineBottom(line)
        assertTrue(
            "Line $line ($lineTop..$lineBottom) is fully visible within $visibleBounds",
            lineTop >= visibleBounds.top && lineBottom <= visibleBounds.bottom,
        )
    }

    private fun captureChangeNoteType(
        name: String,
        insets: WindowInsetsCompat,
        tab: Tab = Tab.Fields,
        prepareCollection: () -> Unit = {},
        beforeCapture: (ChangeNoteTypeDialog) -> Unit = {},
    ) = withCardBrowser(noteCount = 1) { browser ->
        prepareCollection()
        val dialog = ChangeNoteTypeDialog.newInstance(col.findNotes(""))
        dialog.showNow(browser.supportFragmentManager, "change_note_type")
        advanceRobolectricLooper()
        try {
            dialog.binding.changeNoteTypePager.setCurrentItem(tab.position, false)
            advanceRobolectricLooper()
            dialog.requireDialog().window!!.simulateSystemBars(insets)
            beforeCapture(dialog)
            captureScreen(name)
        } finally {
            dialog.dismissNow()
        }
    }
}
