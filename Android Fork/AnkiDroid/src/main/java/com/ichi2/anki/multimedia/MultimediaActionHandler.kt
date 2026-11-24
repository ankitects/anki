// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.multimedia

import android.content.Context
import android.content.Intent
import com.ichi2.anki.multimedia.MultimediaBottomSheet.MultimediaAction
import com.ichi2.anki.multimediacard.fields.AudioRecordingField
import com.ichi2.anki.multimediacard.fields.IField
import com.ichi2.anki.multimediacard.fields.ImageField
import com.ichi2.anki.multimediacard.fields.MediaClipField

/**
 * Binds a [MultimediaAction] to the field type it produces and the intent that
 * launches the corresponding capture/selection screen.
 */
sealed interface MultimediaActionHandler {
    /** The [MultimediaAction] this handler responds to. */
    val action: MultimediaAction

    /** Creates the [IField] that will hold the captured/selected media. */
    fun createField(): IField

    /** Builds the [Intent] that launches the capture/selection screen. */
    fun buildIntent(
        context: Context,
        extra: MultimediaActivityExtra,
    ): Intent

    object ImageFile : MultimediaActionHandler {
        override val action = MultimediaAction.SELECT_IMAGE_FILE

        override fun createField(): IField = ImageField()

        override fun buildIntent(
            context: Context,
            extra: MultimediaActivityExtra,
        ): Intent =
            MultimediaImageFragment.getIntent(
                context,
                extra,
                MultimediaImageFragment.ImageOptions.GALLERY,
            )
    }

    object Camera : MultimediaActionHandler {
        override val action = MultimediaAction.OPEN_CAMERA

        override fun createField(): IField = ImageField()

        override fun buildIntent(
            context: Context,
            extra: MultimediaActivityExtra,
        ): Intent =
            MultimediaImageFragment.getIntent(
                context,
                extra,
                MultimediaImageFragment.ImageOptions.CAMERA,
            )
    }

    object Drawing : MultimediaActionHandler {
        override val action = MultimediaAction.OPEN_DRAWING

        override fun createField(): IField = ImageField()

        override fun buildIntent(
            context: Context,
            extra: MultimediaActivityExtra,
        ): Intent =
            MultimediaImageFragment.getIntent(
                context,
                extra,
                MultimediaImageFragment.ImageOptions.DRAWING,
            )
    }

    object AudioFile : MultimediaActionHandler {
        override val action = MultimediaAction.SELECT_AUDIO_FILE

        override fun createField(): IField = MediaClipField()

        override fun buildIntent(
            context: Context,
            extra: MultimediaActivityExtra,
        ): Intent =
            AudioVideoFragment.getIntent(
                context,
                extra,
                AudioVideoFragment.MediaOption.AUDIO_CLIP,
            )
    }

    object VideoFile : MultimediaActionHandler {
        override val action = MultimediaAction.SELECT_VIDEO_FILE

        override fun createField(): IField = MediaClipField()

        override fun buildIntent(
            context: Context,
            extra: MultimediaActivityExtra,
        ): Intent =
            AudioVideoFragment.getIntent(
                context,
                extra,
                AudioVideoFragment.MediaOption.VIDEO_CLIP,
            )
    }

    object AudioRecording : MultimediaActionHandler {
        override val action = MultimediaAction.SELECT_AUDIO_RECORDING

        override fun createField(): IField = AudioRecordingField()

        override fun buildIntent(
            context: Context,
            extra: MultimediaActivityExtra,
        ): Intent = AudioRecordingFragment.getIntent(context, extra)
    }

    companion object {
        /** All registered handlers, exhaustively covering every [MultimediaAction]. */
        private val all: List<MultimediaActionHandler> =
            listOf(ImageFile, Camera, Drawing, AudioFile, VideoFile, AudioRecording)

        /** Returns the handler for [action]; throws if no handler is registered. */
        fun forAction(action: MultimediaAction): MultimediaActionHandler =
            all.firstOrNull { it.action == action }
                ?: error("No MultimediaActionHandler registered for $action")
    }
}
