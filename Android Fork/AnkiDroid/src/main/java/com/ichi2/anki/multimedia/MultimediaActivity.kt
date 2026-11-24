// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2024 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.multimedia

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.R
import com.ichi2.anki.compat.CompatHelper.Companion.getSerializableCompat
import com.ichi2.anki.compat.CompatHelper.Companion.getSerializableExtraCompat
import com.ichi2.anki.databinding.ActivityMultimediaBinding
import com.ichi2.anki.multimediacard.IMultimediaEditableNote
import com.ichi2.anki.multimediacard.fields.IField
import com.ichi2.anki.settings.enums.NightTheme
import com.ichi2.anki.snackbar.BaseSnackbarBuilderProvider
import com.ichi2.anki.snackbar.SnackbarBuilder
import com.ichi2.anki.startup.ensureStorageIsReady
import com.ichi2.compose.theme.AnkiDroidTheme
import com.ichi2.compose.ui.components.HeroToolbarActionButton
import com.ichi2.themes.Themes
import com.ichi2.utils.FragmentFactoryUtils
import dev.androidbroadcast.vbpd.viewBinding
import timber.log.Timber
import java.io.File
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

    internal var isImageEditorBusy by mutableStateOf(false)

    override val analyticsScreenName: String
        get() = intent.getStringExtra(EXTRA_FRAGMENT_NAME)?.substringAfterLast('.') ?: super.analyticsScreenName

    private val Intent.multimediaArgsFile: File?
        get() = extras?.getSerializableCompat(EXTRA_FRAGMENT_ARGS)

    private val Intent.mediaOptionsExtra: Serializable?
        get() = getSerializableExtraCompat(EXTRA_MEDIA_OPTIONS)

    override fun onCreate(savedInstanceState: Bundle?) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return
        }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle =
                SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) {
                    Themes.currentTheme is NightTheme
                },
        )
        if (!ensureStorageIsReady()) {
            return
        }
        setupEdgeToEdge()
        setSupportActionBar(binding.toolbar)
        setupToolbarActions()

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
                        putSerializable(EXTRA_FRAGMENT_ARGS, intent.multimediaArgsFile)
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

    private fun setupToolbarActions() {
        binding.toolbarActions.isVisible = intent.getStringExtra(EXTRA_FRAGMENT_NAME) == MultimediaImageFragment::class.jvmName
        if (!binding.toolbarActions.isVisible) return

        binding.toolbarActions.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                AnkiDroidTheme {
                    HeroToolbarActionButton(enabled = !isImageEditorBusy, onClick = {
                        val fragment = supportFragmentManager.findFragmentById(R.id.fragment_container) as? MultimediaImageFragment
                        fragment?.onDone()
                    })
                }
            }
        }
    }

    /**
     * Applies edge-to-edge insets for the app bar. The hosted fragment insets its own root,
     * see [MultimediaFragment].
     */
    private fun setupEdgeToEdge() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.appBar) { view, insets ->
            val bars =
                insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
                )
            view.updatePadding(left = bars.left, top = bars.top, right = bars.right)
            insets
        }
    }

    override val baseSnackbarBuilder: SnackbarBuilder = {
        // Keep error messages above the editor's bottom controls.
        anchorView = findViewById<View>(R.id.image_actions) ?: findViewById<View>(R.id.action_done)
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
                putExtra(EXTRA_FRAGMENT_ARGS, arguments?.let { MultimediaArgsStorage.create(context).save(it) })
                putExtra(EXTRA_FRAGMENT_NAME, fragmentClass.jvmName)
                putExtra(EXTRA_MEDIA_OPTIONS, mediaOptions)
            }
    }
}
