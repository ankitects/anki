// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.utils

import android.widget.EditText
import androidx.test.espresso.matcher.ViewMatchers.assertThat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.EmptyApplicationCategory
import com.ichi2.testutils.AndroidTest
import com.ichi2.testutils.EmptyApplication
import com.ichi2.testutils.targetContext
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.nullValue
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
@Category(EmptyApplicationCategory::class)
class EditTextUtilsTest : AndroidTest {
    @Test
    fun `textAsIntOrNull test`() {
        fun textAsIntOrNull(value: String) =
            EditText(targetContext)
                .apply { setText(value) }
                .textAsIntOrNull()

        fun textAsIntOrNull(value: Long) = textAsIntOrNull(value.toString())

        fun textAsIntOrNull(value: Int) = textAsIntOrNull(value.toString())

        assertThat("1", textAsIntOrNull("1"), equalTo(1))
        assertThat("1.0 => null", textAsIntOrNull("1.0"), nullValue())
        assertThat("1.1 => null", textAsIntOrNull("1.1"), nullValue())
        assertThat("-1", textAsIntOrNull("-1"), equalTo(-1))
        assertThat("2147483647", textAsIntOrNull(Int.MAX_VALUE), equalTo(2147483647))
        assertThat("-2147483648", textAsIntOrNull(Int.MIN_VALUE), equalTo(-2147483648))

        assertThat("MIN_VALUE - 1 => null", textAsIntOrNull(Int.MIN_VALUE - 1L), nullValue())
        assertThat("MAX_VALUE + 1 => null", textAsIntOrNull(Int.MAX_VALUE + 1L), nullValue())

        assertThat("empty string => null", textAsIntOrNull(""), nullValue())
        assertThat("text => null", textAsIntOrNull("non-int text"), nullValue())
        assertThat("too long => null", textAsIntOrNull(Long.MAX_VALUE), nullValue())
    }
}
