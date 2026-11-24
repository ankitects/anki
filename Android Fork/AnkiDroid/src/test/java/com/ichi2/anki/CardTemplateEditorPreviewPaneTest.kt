// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.Intent
import android.os.Bundle
import android.os.Looper
import androidx.fragment.app.Fragment
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.previewer.TemplatePreviewerFragment
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertIs
import kotlin.test.assertNotSame

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "xlarge")
class CardTemplateEditorPreviewPaneTest : RobolectricTest() {
    private lateinit var controller: ActivityController<CardTemplateEditor>

    private val editor: CardTemplateEditor
        get() = controller.get()

    private val previewPane: Fragment?
        get() = editor.supportFragmentManager.findFragmentById(R.id.fragment_container)

    @Test
    fun `preview is shown when the note type loads while stopped`() {
        launch()
        recreateFinishingLoadWhileStopped()
        assertIs<TemplatePreviewerFragment>(previewPane)
    }

    @Test
    fun `preview refreshes after typing just before leaving`() {
        launch()
        val previewBefore = previewPane
        editor.currentFragment!!
            .binding.editText
            .append("x")
        controller.pause().stop()
        ShadowLooper.idleMainLooper(5, TimeUnit.SECONDS)
        advanceRobolectricLooper()
        controller.start().resume().visible()
        advanceRobolectricLooper()
        assertIs<TemplatePreviewerFragment>(previewPane)
        assertNotSame(previewBefore, previewPane)
    }

    private fun launch() {
        val intent = CardTemplateEditor.getIntent(targetContext, col.notetypes.basicAndReversed.id)
        controller = startActivityControllerNormallyOpenCollectionWithIntent(CardTemplateEditor::class.java, intent)
        advanceRobolectricLooper()
    }

    private fun recreateFinishingLoadWhileStopped() {
        val intent = Intent(editor.intent)
        val saved = Bundle()
        controller
            .pause()
            .stop()
            .saveInstanceState(saved)
            .destroy()

        val realCollection = col
        val loadStarted = CountDownLatch(1)
        val releaseLoad = CountDownLatch(1)
        mockkObject(CollectionManager)
        every { CollectionManager.isOpenUnsafe() } returns false
        every { CollectionManager.getColUnsafe() } answers {
            if (Looper.myLooper() != Looper.getMainLooper()) {
                loadStarted.countDown()
                check(releaseLoad.await(15, TimeUnit.SECONDS)) { "Background load was not released" }
            }
            realCollection
        }
        try {
            controller = Robolectric.buildActivity(CardTemplateEditor::class.java, intent)
            saveControllerForCleanup(controller)
            controller.setup(saved)
            check(loadStarted.await(5, TimeUnit.SECONDS)) { "Collection load did not begin" }
            controller.pause().stop()
            releaseLoad.countDown()
            advanceRobolectricLooperUntil { editor.mainBinding.cardTemplateEditorPager.adapter != null }
            controller.start().resume().visible()
            advanceRobolectricLooper()
        } finally {
            releaseLoad.countDown()
            unmockkObject(CollectionManager)
            advanceRobolectricLooper()
        }
    }
}
