// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>
// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor

import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.NoteEditorFragment
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.compat.CompatHelper.Companion.getSerializableCompat
import com.ichi2.anki.noteeditor.NoteEditorMultimediaController.Companion.STATE_KEY_IMAGE_CACHE
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.notNullValue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NoteEditorMultimediaControllerTest : RobolectricTest() {
    @Test
    fun `controller is reachable from the hosted fragment`() {
        val noteEditor = openNoteEditor()
        assertThat(noteEditor.multimediaController, notNullValue())
    }

    @Test
    fun `onSaveInstanceState writes the imageCache key`() {
        val noteEditor = openNoteEditor()
        val out = Bundle()

        noteEditor.multimediaController.onSaveInstanceState(out)

        assertThat(out.containsKey(STATE_KEY_IMAGE_CACHE), equalTo(true))
    }

    @Test
    fun `restore then save round-trips the imageCache contents`() {
        val noteEditor = openNoteEditor()
        val seeded = hashMapOf("foo" to "<img src='foo.png'>")
        val saved = Bundle().apply { putSerializable(STATE_KEY_IMAGE_CACHE, seeded) }

        noteEditor.multimediaController.onRestoreInstanceState(saved)
        val out = Bundle().also { noteEditor.multimediaController.onSaveInstanceState(it) }

        val restored = out.getSerializableCompat<HashMap<String, String>>(STATE_KEY_IMAGE_CACHE)
        assertThat(restored, equalTo(seeded))
    }

    @Test
    fun `restore from a bundle without imageCache key keeps the cache empty`() {
        val noteEditor = openNoteEditor()

        noteEditor.multimediaController.onRestoreInstanceState(Bundle())
        val out = Bundle().also { noteEditor.multimediaController.onSaveInstanceState(it) }

        val restored = out.getSerializableCompat<HashMap<String, String>>(STATE_KEY_IMAGE_CACHE)
        assertThat(restored?.isEmpty(), equalTo(true))
    }

    private fun openNoteEditor(): NoteEditorFragment {
        ensureCollectionLoadIsSynchronous()
        return openNoteEditorWithArgs(NoteEditorLauncher.AddNote().toBundle())
    }
}
