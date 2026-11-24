// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.multimediacard.fields

/**
 * Implementation of Audio Recording field type
 */

class AudioRecordingField : AudioField() {
    override val type: EFieldType = EFieldType.AUDIO_RECORDING

    override val isModified: Boolean
        get() = thisModified

    override var name: String? = null
}
