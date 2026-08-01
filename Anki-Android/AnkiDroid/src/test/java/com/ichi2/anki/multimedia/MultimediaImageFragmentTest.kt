// SPDX-FileCopyrightText: 2026 David Allison <davidallisongithub@gmail.com>
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>
// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.multimedia

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.multimedia.MultimediaActivity.Companion.EXTRA_MEDIA_OPTIONS
import com.ichi2.testutils.launchFragmentInContainer
import com.ichi2.testutils.withFragment
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.notNullValue
import org.hamcrest.Matchers.nullValue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** A picked or shared `file://` must only resolve to a file inside our own cache. */
@RunWith(AndroidJUnit4::class)
class MultimediaImageFragmentTest : RobolectricTest() {
    @Test
    fun `file uri into app-private storage is rejected`() =
        withImageFragment {
            @SuppressLint("SdCardPath")
            val collection = File("/data/data/com.ichi2.anki/files/AnkiDroid/collection.anki2")
            assertThat(resolveUriToFile(Uri.fromFile(collection)), nullValue())
        }

    @Test
    fun `file uri escaping the cache via traversal is rejected`() =
        withImageFragment {
            val escape = File(targetContext.cacheDir, "temp-photos/../../files/AnkiDroid/collection.anki2")
            assertThat(resolveUriToFile(Uri.fromFile(escape)), nullValue())
        }

    @Test
    fun `file uri for internalized media is resolved`() =
        withImageFragment {
            val cached = File(targetContext.cacheDir, "temp-photos/internalized.jpg")
            assertThat(resolveUriToFile(Uri.fromFile(cached)), notNullValue())
        }

    @Test
    fun `file uri for a shared image staged in the cache is resolved`() =
        withImageFragment {
            val shared = File(targetContext.cacheDir, "PXL_20260527_043648181.jpg")
            assertThat(resolveUriToFile(Uri.fromFile(shared)), notNullValue())
        }

    @Test
    fun `a picked file uri is not trusted`() {
        val intent = Intent().setData(Uri.fromFile(File("/storage/emulated/0/secret.txt")))
        assertThat(PickedImage(intent).trustedUri, nullValue())
    }

    @Test
    fun `a picked content uri is trusted`() {
        val content = "content://media/external/images/media/1".toUri()
        assertThat(PickedImage(Intent().setData(content)).trustedUri, equalTo(content))
    }

    private fun withImageFragment(block: MultimediaImageFragment.() -> Unit) =
        launchFragmentInContainer<MultimediaImageFragment>(
            bundleOf(EXTRA_MEDIA_OPTIONS to MultimediaImageFragment.ImageOptions.GALLERY),
        ).use { it.withFragment(block) }
}
