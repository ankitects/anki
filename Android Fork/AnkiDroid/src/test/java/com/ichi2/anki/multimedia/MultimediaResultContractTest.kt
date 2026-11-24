// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>
// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.multimedia

import android.app.Activity
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.multimediacard.fields.ImageField
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.instanceOf
import org.hamcrest.Matchers.nullValue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MultimediaResultContractTest {
    private val contract = MultimediaResultContract()

    @Test
    fun `parseResult returns null when intent is null`() {
        assertThat(contract.parseResult(Activity.RESULT_OK, null), nullValue())
    }

    @Test
    fun `parseResult returns null when field index extra is missing`() {
        assertThat(contract.parseResult(Activity.RESULT_OK, Intent()), nullValue())
    }

    @Test
    fun `parseResult returns Cancelled when result code is canceled`() {
        val intent =
            Intent().apply {
                putExtra(MultimediaResultContract.EXTRA_FIELD_INDEX, 3)
            }

        val result = contract.parseResult(Activity.RESULT_CANCELED, intent)

        assertThat(result, instanceOf(MultimediaResult.Cancelled::class.java))
        assertThat(result!!.fieldIndex, equalTo(3))
    }

    @Test
    fun `parseResult returns Success when result code is ok and field is present`() {
        val field = ImageField()
        val intent =
            Intent().apply {
                putExtra(MultimediaResultContract.EXTRA_FIELD_INDEX, 1)
                putExtra(MultimediaResultContract.EXTRA_FIELD, field)
            }

        val result = contract.parseResult(Activity.RESULT_OK, intent)

        assertThat(result, instanceOf(MultimediaResult.Success::class.java))
        val success = result as MultimediaResult.Success
        assertThat(success.fieldIndex, equalTo(1))
        assertThat(success.field, instanceOf(ImageField::class.java))
    }

    @Test
    fun `parseResult returns null when result code is ok but field is missing`() {
        val intent =
            Intent().apply {
                putExtra(MultimediaResultContract.EXTRA_FIELD_INDEX, 1)
            }

        assertThat(contract.parseResult(Activity.RESULT_OK, intent), nullValue())
    }

    @Test
    fun `parseResult returns null for an unknown result code`() {
        val intent =
            Intent().apply {
                putExtra(MultimediaResultContract.EXTRA_FIELD_INDEX, 0)
            }

        assertThat(contract.parseResult(Activity.RESULT_FIRST_USER, intent), nullValue())
    }
}
