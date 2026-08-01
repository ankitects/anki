/*
 * Copyright (c) 2024 Ashish Yadav <mailtoashish693@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.multimedia

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import com.google.android.material.button.MaterialButton
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.R
import com.ichi2.anki.compat.CompatHelper.Companion.getSerializableCompat
import com.ichi2.anki.compat.CompatHelper.Companion.getSerializableExtraCompat
import com.ichi2.anki.databinding.ActivityMultimediaBinding
import com.ichi2.anki.multimediacard.IMultimediaEditableNote
import com.ichi2.anki.multimediacard.fields.IField
import com.ichi2.anki.snackbar.BaseSnackbarBuilderProvider
import com.ichi2.anki.snackbar.SnackbarBuilder
import com.ichi2.anki.startup.ensureStorageIsReady
import com.ichi2.themes.setTransparentStatusBar
import com.ichi2.utils.FragmentFactoryUtils
import dev.androidbroadcast.vbpd.viewBinding
import timber.log.Timber
import java.io.Serializable
import kotlin.reflect.KClass
import kotlin.reflect.jvm.jvmName

/**
 * Required information for multimedia activities.
 *
 * This combines three elements into a single unit: Index, IField and IMultimediaEditableNote.
 *  - `Int`: The index of the field within the multimedia note that the multimedia content is associated with.
 *  @see IField
 *  @see IMultimediaEditableNote
 */
// TODO: move it to a better data model (remove IField & IMultimediaEditableNote)
data class MultimediaActivityExtra(
    val index: Int,
    val field: IField,
    val note: IMultimediaEditableNote,
    val imageUri: String? = null,
) : Serializable

/**
 * Multimedia activity that allows users to attach media files to an input field in NoteEditor.
 */
class MultimediaActivity :
    AnkiActivity(R.layout.activity_multimedia),
    BaseSnackbarBuilderProvider {
    private val binding by viewBinding(ActivityMultimediaBinding::bind)

    private val Intent.multimediaArgsExtra: MultimediaActivityExtra?
        get() = extras?.getSerializableCompat(EXTRA_FRAGMENT_ARGS)

    private val Intent.mediaOptionsExtra: Serializable?
        get() = getSerializableExtraCompat(EXTRA_MEDIA_OPTIONS)

    override fun onCreate(savedInstanceState: Bundle?) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return
        }
        super.onCreate(savedInstanceState)
        if (!ensureStorageIsReady()) {
            return
        }
        setTransparentStatusBar()
        setSupportActionBar(binding.toolbar)

        // avoid recreating the fragment on configuration changes
        if (savedInstanceState != null) {
            return
        }

        val fragmentClassName =
            requireNotNull(intent.getStringExtra(EXTRA_FRAGMENT_NAME)) {
                "'$EXTRA_FRAGMENT_NAME' extra should be provided"
            }

        val fragment =
            FragmentFactoryUtils.instantiate<Fragment>(this, fragmentClassName).apply {
                arguments =
                    Bundle().apply {
                        putSerializable(EXTRA_FRAGMENT_ARGS, intent.multimediaArgsExtra)
                        putSerializable(EXTRA_MEDIA_OPTIONS, intent.mediaOptionsExtra)
                    }
            }

        supportFragmentManager.commit {
            replace(R.id.fragment_container, fragment)
        }

        binding.toolbar.setNavigationOnClickListener {
            Timber.d("MultimediaActivity:: Back pressed")
            onBackPressedDispatcher.onBackPressed()
        }
    }

    override val baseSnackbarBuilder: SnackbarBuilder = {
        // if action_done exists in a fragment, use that as the anchor view

        // This is a minor hack architecturally: AudioRecordingController should request that
        // the host fragment/activity opens a snackbar, so the activity doesn't need knowledge
        // of the layout of its hosted fragments

        // If this doesn't work, anchorView remains null
        anchorView = findViewById<MaterialButton>(R.id.action_done)
    }

    companion object {
        const val EXTRA_FRAGMENT_ARGS = "extra_fragment_args"
        const val EXTRA_FRAGMENT_NAME = "extra_fragment_name"

        /** used in case a fragment supports more than media operations **/
        const val EXTRA_MEDIA_OPTIONS = "extra_media_options"

        fun getIntent(
            context: Context,
            fragmentClass: KClass<out Fragment>,
            arguments: MultimediaActivityExtra? = null,
            mediaOptions: Serializable? = null,
        ): Intent =
            Intent(context, MultimediaActivity::class.java).apply {
                putExtra(EXTRA_FRAGMENT_ARGS, arguments)
                putExtra(EXTRA_FRAGMENT_NAME, fragmentClass.jvmName)
                putExtra(EXTRA_MEDIA_OPTIONS, mediaOptions)
            }
    }
}
