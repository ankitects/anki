/*
 * Copyright (c) 2013 Bibek Shrestha <bibekshrestha@gmail.com>
 * Copyright (c) 2013 Zaur Molotnikov <qutorial@gmail.com>
 * Copyright (c) 2013 Nicolas Raoul <nicolas.raoul@gmail.com>
 * Copyright (c) 2013 Flavio Lerda <flerda@gmail.com>
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

package com.ichi2.anki.multimediacard.fields

import androidx.annotation.CheckResult
import androidx.annotation.VisibleForTesting
import com.ichi2.anki.common.utils.annotation.KotlinCleanup
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.requireMediaFolder
import org.jsoup.Jsoup
import timber.log.Timber
import java.io.File

/**
 * Field with an image.
 */
@KotlinCleanup("convert properties to single-line overrides")
class ImageField :
    FieldBase(),
    IField {
    @get:JvmName("getImagePath_unused")
    var extraImageFileRef: File? = null
    private var _name: String? = null

    override val type: EFieldType = EFieldType.IMAGE

    override val isModified: Boolean
        get() = thisModified

    override var mediaFile: File?
        get() = extraImageFileRef
        set(value) {
            extraImageFileRef = value
            thisModified = true
        }

    override var text: String? = null

    override var hasTemporaryMedia: Boolean = false

    override var name: String?
        get() = _name
        set(value) {
            _name = value
        }

    override val formattedValue: String
        get() {
            val file = mediaFile!!
            return formatImageFileName(file)
        }

    override fun setFormattedString(
        col: Collection,
        value: String,
    ) {
        extraImageFileRef = getImageFullPath(col, value)
    }

    companion object {
        @VisibleForTesting
        fun formatImageFileName(file: File): String =
            if (file.exists()) {
                """<img src="${file.name}">"""
            } else {
                ""
            }

        @VisibleForTesting
        fun getImageFullPath(
            col: Collection,
            value: String,
        ): File? {
            val path = parseImageSrcFromHtml(value)
            return if (path.isNotEmpty()) {
                File(col.requireMediaFolder(), path)
            } else {
                null
            }
        }

        @VisibleForTesting
        @CheckResult
        fun parseImageSrcFromHtml(html: String): String {
            return try {
                val doc = Jsoup.parseBodyFragment(html)
                val image = doc.selectFirst("img[src]") ?: return ""
                image.attr("src")
            } catch (e: Exception) {
                Timber.w(e)
                ""
            }
        }
    }
}
