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
 *
 *  This file incorporates work covered by the following copyright and
 *  permission notice:
 *
 *     This file is part of FairEmail.
 *
 *     FairEmail is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     FairEmail is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with FairEmail.  If not, see <http://www.gnu.org/licenses/>.
 *
 *     Copyright 2018-2020 by Marcel Bokhorst (M66B)
 *
 * Source: https://github.com/M66B/FairEmail/blob/75fe7d0ec92a9874a98c22b61eeb8e6a8906a9ea/app/src/main/java/eu/faircode/email/FixedTextView.java
 *
 */
package com.ichi2.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Build
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.appcompat.widget.AppCompatTextView
import timber.log.Timber

open class FixedTextView : AppCompatTextView {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int,
    ) {
        try {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        } catch (ex: Throwable) {
            Timber.w(ex)
            /*
            java.lang.ArrayIndexOutOfBoundsException: length=...; index=...
                at android.text.TextLine.measure(TextLine.java:316)
                at android.text.TextLine.metrics(TextLine.java:271)
                at android.text.Layout.measurePara(Layout.java:2056)
                at android.text.Layout.getDesiredWidth(Layout.java:164)
                at android.widget.TextView.onMeasure(TextView.java:8291)
                at androidx.appcompat.widget.AppCompatTextView.onMeasure(SourceFile:554)
                at android.view.View.measure(View.java:22360)
             */
            setMeasuredDimension(0, 0)
        }
    }

    override fun onPreDraw(): Boolean =
        try {
            super.onPreDraw()
        } catch (ex: Throwable) {
            Timber.w(ex)
            /*
                java.lang.ArrayIndexOutOfBoundsException: length=54; index=54
                at android.text.TextLine.measure(TextLine.java:316)
                at android.text.TextLine.metrics(TextLine.java:271)
                at android.text.Layout.getLineExtent(Layout.java:1374)
                at android.text.Layout.getLineStartPos(Layout.java:700)
                at android.text.Layout.getHorizontal(Layout.java:1175)
                at android.text.Layout.getHorizontal(Layout.java:1144)
                at android.text.Layout.getPrimaryHorizontal(Layout.java:1115)
                at android.widget.TextView.bringPointIntoView(TextView.java:8944)
                at android.widget.TextView.onPreDraw(TextView.java:6475)
             */
            true
        }

    override fun onDraw(canvas: Canvas) {
        try {
            super.onDraw(canvas)
        } catch (ex: Throwable) {
            Timber.w(ex)
            /*
            java.lang.ArrayIndexOutOfBoundsException: length=74; index=74
                at android.text.TextLine.draw(TextLine.java:241)
                at android.text.Layout.drawText(Layout.java:545)
                at android.text.Layout.draw(Layout.java:289)
                at android.widget.TextView.onDraw(TextView.java:6972)
                at android.view.View.draw(View.java:19380)
             */
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        // https://issuetracker.google.com/issues/37068143
        if (event.actionMasked == MotionEvent.ACTION_DOWN && Build.VERSION.RELEASE == "6.0" && hasSelection()) {
            // Remove selection
            val text = text
            setText(null)
            setText(text)
        }
        return super.dispatchTouchEvent(event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean =
        try {
            super.onTouchEvent(event)
        } catch (ex: Throwable) {
            Timber.w(ex)
            false
            /*
            java.lang.IllegalArgumentException
                at com.android.internal.util.Preconditions.checkArgument(Preconditions.java:33)
                at android.widget.SelectionActionModeHelper$TextClassificationHelper.init(SelectionActionModeHelper.java:640)
                at android.widget.SelectionActionModeHelper.resetTextClassificationHelper(SelectionActionModeHelper.java:203)
                at android.widget.SelectionActionModeHelper.invalidateActionModeAsync(SelectionActionModeHelper.java:104)
                at android.widget.Editor.invalidateActionModeAsync(Editor.java:2028)
                at android.widget.Editor.showFloatingToolbar(Editor.java:1419)
                at android.widget.Editor.updateFloatingToolbarVisibility(Editor.java:1397)
                at android.widget.Editor.onTouchEvent(Editor.java:1367)
                at android.widget.TextView.onTouchEvent(TextView.java:9701)
             */
        }

    override fun onFocusChanged(
        focused: Boolean,
        direction: Int,
        previouslyFocusedRect: Rect?,
    ) {
        try {
            super.onFocusChanged(focused, direction, previouslyFocusedRect)
        } catch (ex: Throwable) {
            /*
            java.lang.ClassCastException: android.text.SpannedString cannot be cast to android.text.Spannable
              at android.widget.Editor.onFocusChanged(Editor.java:1058)
              at android.widget.TextView.onFocusChanged(TextView.java:9262)
              at android.view.View.handleFocusGainInternal(View.java:5388)
              at android.view.View.requestFocusNoSearch(View.java:8131)
              at android.view.View.requestFocus(View.java:8110)
              at android.view.View.requestFocus(View.java:8077)
              at android.view.View.requestFocus(View.java:8056)
              at android.view.View.onTouchEvent(View.java:10359)
              at android.widget.TextView.onTouchEvent(TextView.java:9580)
              at android.view.View.dispatchTouchEvent(View.java:8981)
             */
            Timber.w(ex)
        }
    }

    override fun performLongClick(): Boolean =
        try {
            super.performLongClick()
        } catch (ex: Throwable) {
            /*
                java.lang.IllegalStateException: Drag shadow dimensions must be positive
                    at android.view.View.startDragAndDrop(View.java:27316)
                    at android.widget.Editor.startDragAndDrop(Editor.java:1340)
                    at android.widget.Editor.performLongClick(Editor.java:1374)
                    at android.widget.TextView.performLongClick(TextView.java:13544)
                    at android.view.View.performLongClick(View.java:7928)
                    at android.view.View$CheckForLongPress.run(View.java:29321)

                java.lang.NullPointerException: Attempt to invoke virtual method 'int android.widget.Editor$SelectionModifierCursorController.getMinTouchOffset()' on a null object reference
                    at android.widget.Editor.touchPositionIsInSelection(Unknown:36)
                    at android.widget.Editor.performLongClick(Unknown:72)
                    at android.widget.TextView.performLongClick(Unknown:24)
             */
            Timber.w(ex)
            false
        }

    override fun onKeyDown(
        keyCode: Int,
        event: KeyEvent,
    ): Boolean =
        try {
            super.onKeyDown(keyCode, event)
        } catch (ex: Throwable) {
            /*
                java.lang.IllegalArgumentException
                  at com.android.internal.util.Preconditions.checkArgument(Preconditions.java:33)
                  at android.widget.SelectionActionModeHelper$TextClassificationHelper.init(SelectionActionModeHelper.java:641)
                  at android.widget.SelectionActionModeHelper.resetTextClassificationHelper(SelectionActionModeHelper.java:204)
                  at android.widget.SelectionActionModeHelper.startActionModeAsync(SelectionActionModeHelper.java:88)
                  at android.widget.Editor.startSelectionActionModeAsync(Editor.java:2021)
                  at android.widget.Editor.refreshTextActionMode(Editor.java:1966)
                  at android.widget.TextView.spanChange(TextView.java:9525)
                  at android.widget.TextView$ChangeWatcher.onSpanChanged(TextView.java:11973)
                  at android.text.SpannableStringBuilder.sendSpanChanged(SpannableStringBuilder.java:1292)
                  at android.text.SpannableStringBuilder.setSpan(SpannableStringBuilder.java:748)
                  at android.text.SpannableStringBuilder.setSpan(SpannableStringBuilder.java:672)
                  at android.text.Selection.extendSelection(Selection.java:102)
                  at android.text.Selection.extendLeft(Selection.java:324)
                  at android.text.method.ArrowKeyMovementMethod.left(ArrowKeyMovementMethod.java:72)
                  at android.text.method.BaseMovementMethod.handleMovementKey(BaseMovementMethod.java:165)
                  at android.text.method.ArrowKeyMovementMethod.handleMovementKey(ArrowKeyMovementMethod.java:65)
                  at android.text.method.BaseMovementMethod.onKeyDown(BaseMovementMethod.java:42)
                  at android.widget.TextView.doKeyDown(TextView.java:7367)
                  at android.widget.TextView.onKeyDown(TextView.java:7117)
                  at android.view.KeyEvent.dispatch(KeyEvent.java:2707)
             */
            Timber.w(ex)
            false
        }

    override fun setText(
        text: CharSequence?,
        type: BufferType,
    ) {
        try {
            super.setText(text, type)
        } catch (ex: Throwable) {
            Timber.w(ex)
            /*
                java.lang.IndexOutOfBoundsException:
                  at android.text.PackedIntVector.getValue (PackedIntVector.java:71)
                  at android.text.DynamicLayout.getLineTop (DynamicLayout.java:602)
                  at android.text.Layout.getLineBottom (Layout.java:1260)
                  at android.widget.TextView.invalidateRegion (TextView.java:5379)
                  at android.widget.TextView.invalidateCursor (TextView.java:5348)
                  at android.widget.TextView.spanChange (TextView.java:8351)
                  at android.widget.TextView$ChangeWatcher.onSpanAdded (TextView.java:10550)
                  at android.text.SpannableStringInternal.sendSpanAdded (SpannableStringInternal.java:315)
                  at android.text.SpannableStringInternal.setSpan (SpannableStringInternal.java:138)
                  at android.text.SpannableString.setSpan (SpannableString.java:46)
                  at android.text.Selection.setSelection (Selection.java:76)
                  at android.text.Selection.setSelection (Selection.java:87)
                  at android.text.method.ArrowKeyMovementMethod.initialize (ArrowKeyMovementMethod.java:336)
                  at android.widget.TextView.setText (TextView.java:4555)
                  at android.widget.TextView.setText (TextView.java:4424)
                  at android.widget.TextView.setText (TextView.java:4379)
             */
        }
    }
}
