// SPDX-License-Identifier: GPL-3.0-or-later
package com.ichi2.preferences

import android.annotation.SuppressLint
import android.view.LayoutInflater
import androidx.preference.PreferenceViewHolder
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.slider.Slider
import com.ichi2.anki.EmptyApplicationCategory
import com.ichi2.anki.databinding.PreferenceSliderBinding
import com.ichi2.testutils.AndroidTest
import com.ichi2.testutils.EmptyApplication
import com.ichi2.testutils.targetContext
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
@Category(EmptyApplicationCategory::class)
class SliderPreferenceTest : AndroidTest {
    @Suppress("DEPRECATION")
    @Test
    fun `onTouchListener is only called once`() {
        val context =
            targetContext.apply {
                setTheme(com.google.android.material.R.style.Theme_MaterialComponents_DayNight)
            }

        val attrs =
            Robolectric
                .buildAttributeSet()
                .addAttribute(android.R.attr.valueFrom, "0")
                .addAttribute(android.R.attr.valueTo, "100")
                .build()

        val sliderPreference = SliderPreference(context, attrs)

        val binding = PreferenceSliderBinding.inflate(LayoutInflater.from(context))

        val viewHolder = PreferenceViewHolder.createInstanceForTests(binding.root)

        // Bind the view holder twice to simulate view recycling and verify that
        // duplicate touch listeners are not added.
        sliderPreference.onBindViewHolder(viewHolder)
        sliderPreference.onBindViewHolder(viewHolder)

        assertEquals(1, binding.slider.touchListenersCount)
    }

    private val Slider.touchListenersCount: Int
        @SuppressLint("NewApi")
        get() {
            val baseSliderClass = this.javaClass.superclass!!
            val touchListenersField = baseSliderClass.getDeclaredField("touchListeners")
            touchListenersField.isAccessible = true
            val touchListeners = touchListenersField.get(this) as List<*>
            return touchListeners.size
        }
}
