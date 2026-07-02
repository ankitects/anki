/*
 *  Copyright (c) 2023 Brayan Oliveira <brayandso.dev@gmail.com>
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
package com.ichi2.preferences

import android.content.Context
import android.content.res.TypedArray
import android.util.AttributeSet
import android.view.View
import android.widget.TextView
import androidx.core.content.res.getIntOrThrow
import androidx.core.content.withStyledAttributes
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.google.android.material.slider.Slider
import com.ichi2.anki.R
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.utils.getFormattedStringOrPlurals

/**
 * Similar to [androidx.preference.SeekBarPreference],
 * but with a material [Slider] instead of a SeekBar, and more customizable.
 *
 * Besides the default [Preference] attrs, the following XML attrs can be used to customize it:
 * * android:valueFrom (**required**): minimum value of the slider. It must be lower than `valueTo`
 * * android:valueTo (**required**): maximum value of the slider. It must be higher than `valueFrom`
 * * android:stepSize (*optional*): This value dictates whether the slider operates
 *       in continuous mode, or in discrete mode. If greater than 0 and evenly divides the range described by `valueFrom`
 *       and `valueTo`, the slider operates in discrete mode. If negative an IllegalArgumentException is thrown,
 *       or if greater than 0 but not a factor of the range described by `valueFrom` and `valueTo`.
 *       Its default value is 1.
 * * android:defaultValue (*optional*): Default value of the preference.
 *       If not set, the `valueFrom` value is going to be used.
 *       It must be between `valueFrom` and `valueTo`
 * * app:summaryFormat (*optional*):
 *       Format `string` or `plurals` to be used as template to display the value in the preference summary.
 *       There must be ONLY ONE placeholder, which will be replaced by the preference value.
 * * app:displayValue (*optional*): whether to show the current preference value on a TextView
 *       by the end of the preference
 * * app:displayFormat (*optional*): Format string to be used as template to display the value by
 *       the end of the slider. There must be ONLY ONE placeholder,
 *       which will be replaced by the preference value.
 *       `displayValue` is always true if a `displayFormat` is provided.
 */
@NeedsTest("onTouchListener is only called once")
class SliderPreference(
    context: Context,
    attrs: AttributeSet? = null,
) : Preference(context, attrs) {
    private var valueFrom: Int = 0
    private var valueTo: Int = 0
    private var stepSize: Float = 1F

    private var summaryFormatResource: Int? = null
    private var displayValue: Boolean = false
    private var displayFormat: String? = null

    // flyweight pattern: all listeners for an instance of the class as the same
    // We also need to avoid any method-level closures: this callback is unused
    // the second time `onBindViewHolder` is called
    private val onTouchListener =
        object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {}

            override fun onStopTrackingTouch(slider: Slider) {
                val sliderValue = slider.value.toInt()
                if (sliderValue != value && callChangeListener(sliderValue)) {
                    value = sliderValue
                }
            }
        }

    var value: Int = valueFrom
        set(value) {
            if (field == value) {
                return
            }
            if (value !in valueFrom..valueTo) {
                throw IllegalArgumentException("value $value should be between the min of $valueFrom and max of $valueTo")
            }
            field = value
            persistInt(value)
            notifyChanged()
        }

    init {
        layoutResource = R.layout.preference_slider

        context.withStyledAttributes(attrs, com.google.android.material.R.styleable.Slider) {
            valueFrom = getIntOrThrow(com.google.android.material.R.styleable.Slider_android_valueFrom)
            valueTo = getIntOrThrow(com.google.android.material.R.styleable.Slider_android_valueTo)
            stepSize = getFloat(com.google.android.material.R.styleable.Slider_android_stepSize, 1F)
        }

        context.withStyledAttributes(attrs, R.styleable.CustomPreference) {
            summaryFormatResource =
                getResourceId(R.styleable.CustomPreference_summaryFormat, 0)
                    .takeIf { it != 0 }
        }

        context.withStyledAttributes(attrs, R.styleable.SliderPreference) {
            displayFormat = getString(R.styleable.SliderPreference_displayFormat)
            displayValue = displayFormat != null ||
                getBoolean(R.styleable.SliderPreference_displayValue, false)
        }
    }

    override fun onGetDefaultValue(
        a: TypedArray,
        index: Int,
    ): Any = a.getInt(index, valueFrom)

    override fun onSetInitialValue(defaultValue: Any?) {
        value = getPersistedInt(defaultValue as Int? ?: valueFrom)
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        val slider = holder.findViewById(R.id.slider) as Slider
        slider.valueFrom = valueFrom.toFloat()
        slider.valueTo = valueTo.toFloat()
        slider.stepSize = stepSize
        slider.value = value.toFloat()

        // set the listener once: avoid any method-level closures
        slider.setOnSliderTouchListenerOnce(onTouchListener)

        val summaryView = holder.findViewById(android.R.id.summary) as TextView
        summaryFormatResource?.let {
            summaryView.text = context.getFormattedStringOrPlurals(it, slider.value.toInt())
            summaryView.visibility = View.VISIBLE
        }

        val displayValueTextView = holder.findViewById(R.id.value_display) as TextView
        if (displayValue) {
            displayValueTextView.text = displayFormat?.let { String.format(it, value) }
                ?: value.toString()
        } else {
            displayValueTextView.visibility = View.GONE
        }
    }

    /**
     * Sets the callback to be invoked when this preference is changed by the user
     * (but before the internal state has been updated) on the internal onPreferenceChangeListener,
     * returning true on it by default
     * @param onPreferenceChangeListener The callback to be invoked
     */
    fun setOnPreferenceChangeListener(onPreferenceChangeListener: (newValue: Int) -> Unit) {
        setOnPreferenceChangeListener { _, newValue ->
            if (newValue !is Int) return@setOnPreferenceChangeListener false
            onPreferenceChangeListener(newValue)
            true
        }
    }

    companion object {
        /**
         * [Slider] has no easy means of de-duplicating listeners
         * If a listener has already been added using this method. This does nothing
         * @see [Slider.addOnSliderTouchListener]
         *
         * @param listener A [Slider.OnSliderTouchListener].
         * Must not use method-level state, as this method does not replace the listener
         */
        private fun Slider.setOnSliderTouchListenerOnce(listener: Slider.OnSliderTouchListener) {
            if (this.getTag(R.id.tag_slider_listener_set) != null) return
            this.addOnSliderTouchListener(listener)
            this.setTag(R.id.tag_slider_listener_set, "set")
        }
    }
}
