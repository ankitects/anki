/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.utils

import android.content.ContentResolver
import android.database.Cursor
import android.database.sqlite.SQLiteException
import android.net.Uri
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.EmptyApplicationCategory
import com.ichi2.testutils.EmptyApplication
import com.ichi2.utils.ContentResolverUtil.getFileName
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.core.IsEqual.equalTo
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class) // needs a URI instance
@Config(application = EmptyApplication::class)
@Category(EmptyApplicationCategory::class)
class ContentResolverUtilTest {
    @Test
    fun testViaQueryWorking() {
        val uri = "http://example.com/test.jpeg".toUri()
        val mock = mock(ContentResolver::class.java)

        setQueryReturning(mock, cursorReturning("filename_from_cursor.jpg"))

        val filename = getFileName(mock, uri)

        assertThat(filename, equalTo("filename_from_cursor.jpg"))
    }

    @Test
    fun testViaMimeType() {
        // #7748: Query can fail on some phones, so fall back to MIME
        // values obtained via: content://com.google.android.inputmethod.latin.fileprovider/content/tenor_gif/tenor_gif187746302992141903.gif
        val uri = mock(Uri::class.java)
        whenever(uri.scheme).thenReturn(ContentResolver.SCHEME_CONTENT)

        val mock = mock(ContentResolver::class.java)
        setQueryThrowing(
            mock,
            SQLiteException(
                "no such column: _display_name (code 1 SQLITE_ERROR[1]): , " +
                    "while compiling: SELECT _display_name FROM ClipboardImageTable WHERE (id=855) ORDER BY _data",
            ),
        )

        whenever(mock.getType(any())).thenReturn("image/gif")

        val filename = getFileName(mock, uri)

        // maybe we could do better here, but general guidance is to not parse the uri string
        assertThat(filename, equalTo("image.gif"))
    }

    private fun cursorReturning(
        @Suppress("SameParameterValue") value: String,
    ): Cursor {
        val cursor = mock(Cursor::class.java)
        whenever(cursor.getString(0)).thenReturn(value)
        return cursor
    }

    private fun setQueryReturning(
        mock: ContentResolver,
        cursorToReturn: Cursor?,
    ) {
        whenever(mock.query(any(), any(), any(), any(), any())).thenReturn(cursorToReturn)
    }

    private fun setQueryThrowing(
        mock: ContentResolver,
        ex: Throwable?,
    ) {
        whenever(mock.query(any(), any(), any(), any(), any())).thenThrow(ex)
    }
}
