/*
 * Copyright (c) 2015 Timothy Rae <perceptualchaos2@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.LocaleList
import android.os.Parcelable
import android.text.InputType
import android.util.AttributeSet
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.annotation.VisibleForTesting
import androidx.core.graphics.toColorInt
import com.google.android.material.color.MaterialColors
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.common.utils.annotation.KotlinCleanup
import com.ichi2.anki.servicelayer.NoteService
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.ui.FixedEditText
import com.ichi2.utils.ClipboardUtil.getDescription
import com.ichi2.utils.ClipboardUtil.getPlainText
import com.ichi2.utils.ClipboardUtil.getUri
import com.ichi2.utils.ClipboardUtil.hasMedia
import kotlinx.parcelize.Parcelize
import timber.log.Timber
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

class FieldEditText :
    FixedEditText,
    NoteService.NoteField {
    override var ord = 0
    private var origBackground: Drawable? = null
    private var selectionChangeListener: TextSelectionListener? = null
    private var pasteListener: PasteListener? = null

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    var clipboard: ClipboardManager? = null

    constructor(context: Context?) : super(context!!)
    constructor(context: Context?, attr: AttributeSet?) : super(context!!, attr)
    constructor(context: Context?, attrs: AttributeSet?, defStyle: Int) : super(context!!, attrs, defStyle)

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (shouldDisableExtendedTextUi()) {
            Timber.i("Disabling Extended Text UI")
            this.imeOptions = this.imeOptions or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        }
    }

    private fun shouldDisableExtendedTextUi(): Boolean = this.context.sharedPrefs().getBoolean("disableExtendedTextUi", false)

    @KotlinCleanup("Simplify")
    override val fieldText: String?
        get() {
            val text = text ?: return null
            return text.toString()
        }

    fun init() {
        try {
            clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        } catch (e: Exception) {
            Timber.w(e)
        }
        minimumWidth = 400
        origBackground = background
        // Fixes bug where new instances of this object have wrong colors, probably
        // from some reuse mechanic in Android.
        setDefaultStyle()

        val highlightColor =
            MaterialColors.getColor(
                context,
                R.attr.editTextHighlightColor,
                "#99CCFF".toColorInt(), // light blue color for fallback just-in-case
            )
        setHighlightColor(highlightColor)
    }

    fun setPasteListener(pasteListener: PasteListener) {
        this.pasteListener = pasteListener
    }

    override fun onSelectionChanged(
        selStart: Int,
        selEnd: Int,
    ) {
        if (selectionChangeListener != null) {
            try {
                selectionChangeListener!!.onSelectionChanged(selStart, selEnd)
            } catch (e: Exception) {
                Timber.w(e, "mSelectionChangeListener")
            }
        }
        super.onSelectionChanged(selStart, selEnd)
    }

    fun setHintLocale(locale: Locale) {
        Timber.d("Setting hint locale to '%s'", locale)
        imeHintLocales = LocaleList(locale)
    }

    /**
     * Modify the style of this view to represent a duplicate field.
     */
    fun setDupeStyle() {
        setBackgroundColor(MaterialColors.getColor(context, R.attr.duplicateColor, 0))
    }

    /**
     * Restore the default style of this view.
     */
    fun setDefaultStyle() {
        background = origBackground
    }

    fun setContent(
        content: String?,
        replaceNewLine: Boolean,
    ) {
        val text =
            if (content == null) {
                ""
            } else if (replaceNewLine) {
                content.replace("<br(\\s*/*)>".toRegex(), NEW_LINE)
            } else {
                content
            }
        setText(text)
    }

    override fun onSaveInstanceState(): Parcelable {
        val state = super.onSaveInstanceState()
        return SavedState(state, ord)
    }

    override fun onTextContextMenuItem(id: Int): Boolean {
        // The current function is called both by Ctrl+V and pasting from the context menu
        // It does not deal with drag and drop
        if (id == android.R.id.paste) {
            if (hasMedia(clipboard)) {
                return onPaste(getUri(clipboard), getDescription(clipboard))
            }
            return pastePlainText()
        }
        return super.onTextContextMenuItem(id)
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun pastePlainText(): Boolean {
        getPlainText(clipboard, context)?.let { pasted ->
            val start = min(selectionStart, selectionEnd)
            val end = max(selectionStart, selectionEnd)
            setText(
                text!!.substring(0, start) + pasted + text!!.substring(end),
            )
            setSelection(start + pasted.length)
            return true
        }
        return false
    }

    private fun onPaste(
        mediaUri: Uri?,
        description: ClipDescription?,
    ): Boolean =
        if (mediaUri == null) {
            false
        } else {
            try {
                pasteListener!!.onPaste(this, mediaUri, description)
            } catch (e: Exception) {
                Timber.w(e, "Failed to paste media")
                showSnackbar(context.getString(R.string.multimedia_editor_something_wrong))
                false
            }
        }

    override fun onRestoreInstanceState(state: Parcelable) {
        if (state !is SavedState) {
            super.onRestoreInstanceState(state)
            return
        }
        super.onRestoreInstanceState(state.superState)
        ord = state.ord
    }

    fun setCapitalize(value: Boolean) {
        val inputType = this.inputType
        this.inputType =
            if (value) {
                inputType or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            } else {
                inputType and InputType.TYPE_TEXT_FLAG_CAP_SENTENCES.inv()
            }
    }

    val isCapitalized: Boolean
        get() = this.inputType and InputType.TYPE_TEXT_FLAG_CAP_SENTENCES == InputType.TYPE_TEXT_FLAG_CAP_SENTENCES

    @Parcelize
    internal class SavedState(
        val state: Parcelable?,
        val ord: Int,
    ) : BaseSavedState(state)

    interface TextSelectionListener {
        fun onSelectionChanged(
            selStart: Int,
            selEnd: Int,
        )
    }

    fun interface PasteListener {
        fun onPaste(
            editText: EditText,
            uri: Uri?,
            description: ClipDescription?,
        ): Boolean
    }

    companion object {
        val NEW_LINE: String = System.getProperty("line.separator")!!
    }
}
