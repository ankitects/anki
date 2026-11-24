// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.utils.ext

import android.os.Parcel
import androidx.test.espresso.matcher.ViewMatchers.assertThat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.EmptyApplicationCategory
import com.ichi2.anki.utils.ext.ParcelableUtilsTest.UnderTest
import com.ichi2.anki.utils.ext.ParcelableUtilsTest.UnderTest.Companion.write
import com.ichi2.anki.utils.ext.ParcelableUtilsTest.UnderTest.User
import com.ichi2.testutils.EmptyApplication
import kotlinx.parcelize.Parceler
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasSize
import org.hamcrest.Matchers.nullValue
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.Serializable

@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
@Category(EmptyApplicationCategory::class)
class ParcelableUtilsTest {
    @Test
    fun `serializableList - valid`() {
        val withData =
            UnderTest().apply {
                list =
                    listOf(
                        User(1, "david"),
                        null,
                        User(2, "dave"),
                    )
            }

        val clonedList = withData.cloneAsParcel().list!!

        assertThat(clonedList, hasSize(3))
        clonedList[0]!!.let {
            assertThat(it.id, equalTo(1))
            assertThat(it.name, equalTo("david"))
        }
        assertThat(clonedList[1], nullValue())
        clonedList[2]!!.let {
            assertThat(it.id, equalTo(2))
            assertThat(it.name, equalTo("dave"))
        }
    }

    @Test
    fun `serializableList - null list`() {
        val withData =
            UnderTest().apply {
                list = null
            }

        assertThat(withData.cloneAsParcel().list, nullValue())
    }

    class UnderTest {
        var list: List<User?>? = null

        data class User(
            val id: Int,
            val name: String,
        ) : Serializable

        companion object : Parceler<UnderTest> {
            override fun create(parcel: Parcel): UnderTest =
                UnderTest().apply {
                    list = parcel.readSerializableList<User>()
                }

            override fun UnderTest.write(
                parcel: Parcel,
                flags: Int,
            ) {
                parcel.writeSerializableList(list)
            }
        }
    }
}

fun UnderTest.cloneAsParcel(): UnderTest {
    val parcel = Parcel.obtain()
    this.write(parcel, 0)
    parcel.setDataPosition(0)
    return UnderTest.create(parcel).apply {
        parcel.recycle()
    }
}
