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
package com.ichi2.anki.noteeditor

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Insets
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.AttributeSet
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.NoteEditorFragment
import com.ichi2.anki.R
import com.ichi2.anki.compat.CompatHelper
import com.ichi2.anki.preferences.sharedPrefs
import com.ichi2.utils.AndroidUiUtils.showSoftInput
import com.ichi2.utils.ViewGroupUtils
import com.ichi2.utils.ViewGroupUtils.getAllChildrenRecursive
import com.ichi2.utils.dp
import com.ichi2.utils.show
import com.ichi2.utils.title
import timber.log.Timber
import java.util.Objects
import kotlin.math.ceil

/**
 * Handles the toolbar inside [com.ichi2.anki.NoteEditorFragment]
 *
 * * Handles a number of buttons which arbitrarily format selected text, or insert an item at the cursor
 *    * Text is formatted as HTML
 *    * if a tag with an empty body is inserted, we want the cursor in the middle: `<b>|</b>`
 * * Handles the "default" buttons: [setupDefaultButtons], [displayFontSizeDialog], [displayInsertHeadingDialog]
 * * Handles custom buttons with arbitrary prefixes and suffixes: [customButtons]
 *    * Handles generating the 'icon' for these custom buttons: [createDrawableForString]
 *    * Handles CTRL+ the tag of the button: [onKeyUp]. Allows for Ctrl+1..9 shortcuts
 * * Handles adding a dynamic number of buttons and aligning them into rows: [insertItem]
 *    * And handles whether these should be stacked or scrollable: [shouldScrollToolbar]
 */
class Toolbar : FrameLayout {
    var formatListener: TextFormatListener? = null
    private val toolbar: LinearLayout
    private val toolbarLayout: LinearLayout

    /** A list of buttons, typically user-defined which modify text + selection */
    private val customButtons: MutableList<View> = ArrayList()
    private val rows: MutableList<LinearLayout> = ArrayList()

    private var stringPaint: Paint? = null

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)
    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int,
        defStyleRes: Int,
    ) : super(context, attrs, defStyleAttr, defStyleRes)

    init {
        LayoutInflater.from(context).inflate(R.layout.view_note_editor_toolbar, this, true)
        stringPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 24.dp.toPx(context).toFloat()
                color = Color.BLACK
                textAlign = Paint.Align.CENTER
            }
        toolbar = findViewById(R.id.editor_toolbar_internal)
        toolbarLayout = findViewById(R.id.toolbar_layout)
        setupDefaultButtons()
    }

    /** Sets up the "standard" buttons to insert bold, italics etc... */
    private fun setupDefaultButtons() {
        // sets up a button click to wrap text with the prefix/suffix. So "aa" becomes "<b>aa</b>"
        fun setupButtonWrappingText(
            @IdRes id: Int,
            prefix: String,
            suffix: String,
        ) = findViewById<View>(id).setOnClickListener {
            // Attempt to open keyboard for the currently focused view in the hosting Activity
            val activity = context as? Activity
            activity.showSoftInput()

            onFormat(TextWrapper(prefix, suffix))
        }

        setupButtonWrappingText(R.id.note_editor_toolbar_button_bold, "<b>", "</b>")
        setupButtonWrappingText(R.id.note_editor_toolbar_button_italic, "<i>", "</i>")
        setupButtonWrappingText(R.id.note_editor_toolbar_button_underline, "<u>", "</u>")
        setupButtonWrappingText(R.id.note_editor_toolbar_button_insert_mathjax, "\\(", "\\)")
        setupButtonWrappingText(R.id.note_editor_toolbar_button_horizontal_rule, "<hr>", "")
        findViewById<View>(R.id.note_editor_toolbar_button_font_size).setOnClickListener { displayFontSizeDialog() }
        findViewById<View>(R.id.note_editor_toolbar_button_title).setOnClickListener { displayInsertHeadingDialog() }
        findViewById<View>(R.id.note_editor_toolbar_button_insert_mathjax).setOnLongClickListener {
            displayInsertMathJaxEquationsDialog()
            true
        }

        val parentLayout = findViewById<LinearLayout>(R.id.editor_toolbar_internal)
        parentLayout.children.forEach { child ->
            CompatHelper.compat.setTooltipTextByContentDescription(child)
        }
    }

    /**
     * If a button is assigned a tag, Ctrl+Tag will invoke the button
     * Typically used for Ctrl + 1..9 with custom buttons
     */
    override fun onKeyUp(
        keyCode: Int,
        event: KeyEvent,
    ): Boolean {
        // hack to see if only CTRL is pressed - might not be perfect.
        // I'll avoid checking "function" here as it may be required to press Ctrl
        if (!event.isCtrlPressed || event.isAltPressed || event.isShiftPressed || event.isMetaPressed) {
            return false
        }
        val c: Char =
            try {
                event.getUnicodeChar(0).toChar()
            } catch (e: Exception) {
                Timber.w(e)
                return false
            }
        if (c == '\u0000') {
            return false
        }
        val expected = c.toString()
        for (v in getAllChildrenRecursive(this)) {
            if (Objects.equals(expected, v.tag)) {
                Timber.i("Handling Ctrl + %s", c)
                v.performClick()
                return true
            }
        }
        return super.onKeyUp(keyCode, event)
    }

    fun insertItem(
        @IdRes id: Int,
        @DrawableRes drawable: Int,
        block: () -> Unit,
    ): AppCompatImageButton {
        // we use the light theme here to ensure the tint is black on both
        // A null theme can be passed after colorControlNormal is defined (API 25)
        val themeContext: Context = ContextThemeWrapper(context, R.style.Theme_Light)
        val d = VectorDrawableCompat.create(context.resources, drawable, themeContext.theme)
        return insertItem(id, d, block)
    }

    fun insertItem(
        id: Int,
        drawable: Drawable?,
        formatter: TextFormatter,
    ): View = insertItem(id, drawable) { onFormat(formatter) }

    fun insertItem(
        @IdRes id: Int,
        drawable: Drawable?,
        block: () -> Unit,
    ): AppCompatImageButton {
        val context = context
        val button = AppCompatImageButton(context)
        button.id = id
        button.setImageDrawable(drawable)

        /*
            Style didn't work
            int buttonStyle = R.style.note_editor_toolbar_button;
            ContextThemeWrapper context = new ContextThemeWrapper(getContext(), buttonStyle);
            AppCompatImageButton button = new AppCompatImageButton(context, null, buttonStyle);
         */

        // apply style
        val background = TypedValue()
        context.theme.resolveAttribute(android.R.attr.selectableItemBackground, background, true)
        button.setBackgroundResource(background.resourceId)
        // Use layout size from R.style.note_editor_toolbar_button
        val buttonSize = 44.dp.toPx(context)
        val params = LinearLayout.LayoutParams(buttonSize, buttonSize)
        params.gravity = Gravity.CENTER
        button.layoutParams = params
        val twoDp = ceil((2 / context.resources.displayMetrics.density).toDouble()).toInt()
        button.setPaddingRelative(twoDp, twoDp, twoDp, twoDp)
        // end apply style
        val shouldScroll =
            AnkiDroidApp.instance
                .sharedPrefs()
                .getBoolean(NoteEditorFragment.PREF_NOTE_EDITOR_SCROLL_TOOLBAR, true)
        if (shouldScroll) {
            toolbar.addView(button, toolbar.childCount)
        } else {
            addViewToToolbar(button)
        }
        customButtons.add(button)
        button.setOnClickListener { block.invoke() }

        // Hack - items are truncated from the scrollview
        val v = findViewById<View>(R.id.toolbar_layout)
        val expectedWidth = getVisibleItemCount(toolbar) * 48.dp.toPx(context)
        val width = screenWidth
        val p = LayoutParams(v.layoutParams)
        p.gravity =
            Gravity.CENTER_VERTICAL or if (expectedWidth > width) Gravity.START else Gravity.CENTER_HORIZONTAL
        v.layoutParams = p
        return button
    }

    @Suppress("DEPRECATION")
    private val screenWidth: Int
        get() {
            val displayMetrics = DisplayMetrics()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val windowMetrics =
                    (context as Activity)
                        .windowManager
                        .currentWindowMetrics
                val insets: Insets =
                    windowMetrics.windowInsets.getInsetsIgnoringVisibility(
                        WindowInsets.Type.navigationBars()
                            or WindowInsets.Type.displayCutout(),
                    )
                displayMetrics.widthPixels = windowMetrics.bounds.width() - (insets.right + insets.left)
            } else {
                (context as Activity)
                    .windowManager
                    .defaultDisplay
                    .getMetrics(displayMetrics)
            }
            return displayMetrics.widthPixels
        }

    /** Clears all items added by [insertItem] */
    fun clearCustomItems() {
        for (v in customButtons) {
            (v.parent as ViewGroup).removeView(v)
        }
        customButtons.clear()
    }

    /**
     * Displays a dialog which allows the HTML size codes to be inserted around text (xx-small to xx-large)
     *
     * @see [R.array.html_size_codes]
     */
    @SuppressLint("CheckResult")
    private fun displayFontSizeDialog() {
        val results = resources.getStringArray(R.array.html_size_codes)

        // Might be better to add this as a fragment - let's see.
        AlertDialog.Builder(context).show {
            setItems(R.array.html_size_code_labels) { _, index ->
                val formatter =
                    TextWrapper(
                        prefix = "<span style=\"font-size:${results[index]}\">",
                        suffix = "</span>",
                    )
                onFormat(formatter)
            }
            title(R.string.menu_font_size)
        }
    }

    /**
     * Displays a dialog which allows `<h1>` to `<h6>` to be inserted
     */
    @SuppressLint("CheckResult")
    private fun displayInsertHeadingDialog() {
        val headingList = arrayOf("h1", "h2", "h3", "h4", "h5")
        AlertDialog.Builder(context).show {
            setItems(headingList) { _, index ->
                val charSequence = headingList[index]
                val formatter = TextWrapper(prefix = "<$charSequence>", suffix = "</$charSequence>")
                onFormat(formatter)
            }
            title(R.string.insert_heading)
        }
    }

    /**
     * Displays a dialog that allows the user to insert a MathJax equation in different formats.
     */
    private fun displayInsertMathJaxEquationsDialog() {
        data class MathJaxOption(
            val label: String,
            val prefix: String,
            val suffix: String,
        ) {
            fun toTextWrapper() = TextWrapper(prefix = this.prefix, suffix = this.suffix)
        }

        val mathjaxOptions =
            arrayOf(
                MathJaxOption(TR.editingMathjaxBlock(), prefix = "\\[", suffix = "\\]"),
                MathJaxOption(TR.editingMathjaxChemistry(), prefix = "\\( \\ce{", suffix = "} \\)"),
            )
        AlertDialog.Builder(context).show {
            setItems(mathjaxOptions.map(MathJaxOption::label).toTypedArray()) { _, index ->
                onFormat(mathjaxOptions[index].toTextWrapper())
            }
            title(R.string.insert_mathjax)
        }
    }

    /** Given a string [text], generates a [Drawable] which can be used as a button icon */
    fun createDrawableForString(text: String): Drawable {
        val baseline = -stringPaint!!.ascent()
        val size = (baseline + stringPaint!!.descent() + 0.5f).toInt()
        val image = createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(image)
        canvas.drawText(text, size / 2f, baseline, stringPaint!!)
        return image.toDrawable(resources)
    }

    /** Returns the number of top-level children of [layout] that are visible */
    private fun getVisibleItemCount(layout: LinearLayout): Int = ViewGroupUtils.getAllChildren(layout).count { it.isVisible }

    private fun addViewToToolbar(button: AppCompatImageButton) {
        val expectedWidth = getVisibleItemCount(toolbar) * 48.dp.toPx(context)
        val width = screenWidth
        if (expectedWidth <= width) {
            toolbar.addView(button, toolbar.childCount)
            return
        }
        var spaceLeft = false
        if (rows.isNotEmpty()) {
            val row = rows.last()
            val expectedRowWidth = getVisibleItemCount(row) * 48.dp.toPx(context)
            if (expectedRowWidth <= width) {
                row.addView(button, row.childCount)
                spaceLeft = true
            }
        }
        if (!spaceLeft) {
            val row = LinearLayout(context)
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            row.layoutParams = params
            row.orientation = LinearLayout.HORIZONTAL
            row.addView(button)
            rows.add(row)
            toolbarLayout.addView(rows.last())
        }
    }

    /**
     * If a [formatListener] is attached, supply it with the provided [TextFormatter] so that
     * the current selection of text can be formatted, and the selection can be changed.
     *
     * The listener determines the appropriate selection of text to be formatted and handles
     * selection changes
     */
    fun onFormat(formatter: TextFormatter) {
        formatListener?.performFormat(formatter)
    }

    fun setIconColor(
        @ColorInt color: Int,
    ) {
        ViewGroupUtils
            .getAllChildren(toolbar)
            .forEach { (it as AppCompatImageButton).setColorFilter(color) }
        stringPaint!!.color = color
    }

    /** @see performFormat */
    fun interface TextFormatListener {
        /**
         * A function which accepts a [TextFormatter] and performs some formatting, handling selection changes
         * In the note editor: this takes the [TextFormatter], determines the correct EditText and selection,
         * applies the [TextFormatter] to the selection, and ensures the selection is valid
         *
         * We use a [TextFormatter] to ensure that the selection is correct after the modification
         */
        fun performFormat(formatter: TextFormatter)
    }

    /**
     * A function which takes and returns a [StringFormat] structure
     * Providing a method of inserting text and knowledge of how the selection should change
     */
    fun interface TextFormatter {
        /**
         * A function which takes and returns a [StringFormat] structure
         * Providing a method of inserting text and knowledge of how the selection should change
         */
        fun format(s: String): StringFormat
    }

    /**
     * A [TextFormatter] which wraps the selected string with [prefix] and [suffix]
     * If there's no selected, the cursor is in the middle of the prefix and suffix
     * If there is text selected, the whole string is selected
     */
    class TextWrapper(
        private val prefix: String,
        private val suffix: String,
    ) : TextFormatter {
        override fun format(s: String): StringFormat =
            StringFormat(result = prefix + s + suffix).apply {
                if (s.isEmpty()) {
                    // if there's no selection: place the cursor between the start and end tag
                    selectionStart = prefix.length
                    selectionEnd = prefix.length
                } else {
                    // otherwise, wrap the newly formatted context
                    selectionStart = 0
                    selectionEnd = result.length
                }
            }
    }

    /**
     * Defines a string insertion, and the selection which should occur once the string is inserted
     *
     * @param result The string which should be inserted
     *
     * @param selectionStart
     * The number of characters inside [result] where the selection should start
     * For example: in {{c1::}}, we should set this to 6, to start after the ::
     *
     * @param selectionEnd
     * The number of character inside [result] where the selection should end
     * If the input was empty, we typically want this between the start and end tags
     * If not, at the end of the string
     */
    data class StringFormat(
        var result: String = "",
        var selectionStart: Int = 0,
        var selectionEnd: Int = 0,
    )
}
