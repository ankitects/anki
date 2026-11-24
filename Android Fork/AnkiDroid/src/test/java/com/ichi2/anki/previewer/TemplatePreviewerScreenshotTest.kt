// SPDX-License-Identifier: GPL-3.0-or-later
package com.ichi2.anki.previewer

import androidx.test.core.app.ActivityScenario
import com.ichi2.anki.NotetypeFile
import com.ichi2.anki.ScreenshotTest
import com.ichi2.anki.databinding.FragmentTemplatePreviewerContainerBinding
import com.ichi2.testutils.createTransientDirectory
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test

class TemplatePreviewerScreenshotTest : ScreenshotTest() {
    @Test
    fun baseState() =
        withTemplatePreviewer {
            captureScreen("base")
        }

    private fun withTemplatePreviewer(block: (CardViewerActivity) -> Unit) {
        val notetype = col.notetypes.basic
        val notetypeFile = NotetypeFile(createTransientDirectory(), notetype)
        val arguments =
            TemplatePreviewerArguments(
                notetypeFile = notetypeFile,
                fields = listOf("Front", "Back"),
                tags = emptyList(),
            )

        val intent = TemplatePreviewerPage.getIntent(targetContext, arguments)

        ActivityScenario.launch<CardViewerActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                val binding = FragmentTemplatePreviewerContainerBinding.bind(activity.fragment!!.requireView())
                val tabs = binding.tabLayout
                // Tabs are added after the background check for empty card fronts (Issue 21791).
                advanceRobolectricLooperUntil(lazyMessage = { "Template tabs did not load before capture" }) {
                    tabs.tabCount == notetype.templatesNames.size
                }
                assertThat("All template tabs are present before capture", tabs.tabCount, equalTo(notetype.templatesNames.size))
                assertThat("The initial template is selected before capture", tabs.selectedTabPosition, equalTo(arguments.ord))
                block(activity)
            }
        }
    }
}
