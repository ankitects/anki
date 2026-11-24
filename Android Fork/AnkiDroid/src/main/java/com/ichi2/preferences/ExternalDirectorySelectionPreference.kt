// SPDX-FileCopyrightText: 2024 David Allison <davidallisongithub@gmail.com>
// SPDX-FileCopyrightText: 2026 Shaan Narendran <shaannaren06@gmail.com>
// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.preferences

import android.content.Context
import android.graphics.Color
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.AttributeSet
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.preference.ListPreference
import androidx.preference.ListPreferenceDialogFragmentCompat
import com.ichi2.anki.CommonString
import com.ichi2.anki.common.crashreporting.runCatchingWithLog
import com.ichi2.anki.common.utils.android.showThemedToast
import com.ichi2.anki.startup.getDefaultAnkiDroidDirectory
import com.ichi2.anki.utils.ext.containsFile
import com.ichi2.utils.input
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.show
import timber.log.Timber
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import com.ichi2.anki.common.android.R as CommonR

/**
 * Lets the user pick the AnkiDroid directory from a list of detected storage
 * locations, so an SD card can be chosen without knowing its path.
 * A custom path can still be typed in via the dialog.
 */
class ExternalDirectorySelectionPreference(
    context: Context,
    attrs: AttributeSet?,
) : ListPreference(context, attrs),
    DialogFragmentProvider {
    init {
        summaryProvider =
            SummaryProvider<ListPreference> { pref ->
                pref.value.takeUnless { it.isNullOrEmpty() } ?: context.getString(CommonString.pref_directory_not_set)
            }
    }

    /** The default AnkiDroid directory, or null if storage is unavailable. */
    private val defaultAnkiDir: File?
        get() =
            try {
                getDefaultAnkiDroidDirectory(context)
            } catch (e: Exception) {
                Timber.w(e, "Could not access default AnkiDroid directory")
                null
            }

    /**
     * Builds the candidate paths to show: the current value, the default
     * directory, and anything found by scanning storage.
     */
    private fun loadDirectories(): List<String> =
        buildList {
            if (value?.isNotEmpty() == true) {
                add(File(value))
            }
            defaultAnkiDir?.let { add(it) }
            addAll(getScannedDirectories())
        }.map { it.absolutePath }
            .distinct()

    private fun isValidAnkiDir(dir: File): Boolean {
        try {
            if (!dir.isDirectory || dir.name.startsWith(".")) return false
            if (IGNORED_DIRECTORIES.contains(dir.name.lowercase())) return false
            if (dir.name.startsWith("AnkiDroid", ignoreCase = true)) return true
            return dir.containsFile("collection.anki2")
        } catch (e: Exception) {
            Timber.w(e, "Could not access directory")
            return false
        }
    }

    /**
     * Scans the external storage roots for AnkiDroid directories.
     * A root with none, or which cannot be listed, is offered as a new AnkiDroid location instead.
     */
    private fun getScannedDirectories(): List<File> {
        val storageRoots =
            runCatchingWithLog("get storage roots") {
                context.getExternalFilesDirs(null)
            }.getOrNull()
                .orEmpty()
                .filterNotNull()

        fun File.getValidAnkiSubfolders(): List<File>? {
            val subFolders = listFiles() ?: return null
            return subFolders.filter { isValidAnkiDir(it) }.ifEmpty { null }
        }

        return storageRoots.flatMap { root ->
            when (val ankiFolders = root.getValidAnkiSubfolders()) {
                // If no anki directories are found, we can list this as it is likely an SD card
                null -> listOf(File(root, "AnkiDroid"))
                else -> ankiFolders
            }
        }
    }

    // TODO: Possibly move loadDirectories() to a background thread if ANR occurs
    override fun makeDialogFragment(): DialogFragment {
        val paths = loadDirectories()
        entries = paths.map(::toDisplayLabel).toTypedArray()
        entryValues = paths.toTypedArray()
        return FullWidthListPreferenceDialogFragment()
    }

    /** Builds the display label for a path, graying out its storage root. */
    private fun toDisplayLabel(path: String): CharSequence {
        // Split at "/Android/" so the root is gray and the rest keeps the normal color
        // Eg: "/storage/emulated/0" is gray, "/Android/data/com.ichi2.anki" is not
        val androidIndex = path.indexOf("/Android/")
        if (androidIndex == -1) return path
        val displayString = "${path.take(androidIndex)}\n${path.substring(androidIndex)}"
        return SpannableString(displayString).apply {
            setSpan(ForegroundColorSpan(Color.GRAY), 0, androidIndex, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    companion object {
        private val IGNORED_DIRECTORIES = setOf("collection.media", "backups", "cache", "code_cache")
    }
}

/** List dialog stretched to full width, with a neutral button to enter a custom path. */
class FullWidthListPreferenceDialogFragment : ListPreferenceDialogFragmentCompat() {
    override fun onPrepareDialogBuilder(builder: AlertDialog.Builder) {
        super.onPrepareDialogBuilder(builder)
        builder.setNeutralButton(CommonString.pref_custom_path) { _, _ -> showCustomPathInput() }
    }

    private fun showCustomPathInput() {
        val context = requireContext()
        val pref = (preference as? ExternalDirectorySelectionPreference) ?: return
        AlertDialog
            .Builder(context)
            .show {
                setTitle(CommonString.pref_enter_custom_path)
                setView(CommonR.layout.dialog_generic_text_input)
                positiveButton(android.R.string.ok)
                negativeButton(android.R.string.cancel)
            }.input(
                prefill = pref.value ?: "",
                allowEmpty = false,
            ) { dialog, text ->
                try {
                    // normalize before saving so "a//b/" and "a/b" become one entry
                    val pathObj = Paths.get(text.toString().trim()).toAbsolutePath().normalize()
                    val newPath = pathObj.toString()
                    Files.createDirectories(pathObj)
                    if (!Files.isWritable(pathObj)) {
                        showThemedToast(
                            context,
                            context.getString(CommonString.pref_directory_not_writable),
                            true,
                        )
                        return@input
                    }
                    dialog.dismiss()
                    if (pref.callChangeListener(newPath)) {
                        pref.value = newPath
                    }
                } catch (e: Exception) {
                    Timber.w(e, "Failed to set custom path")
                    AlertDialog.Builder(context).show {
                        setTitle(context.getString(CommonString.could_not_create_dir, text.toString()))
                        setMessage(e.stackTraceToString())
                        positiveButton(android.R.string.ok)
                    }
                }
            }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
    }
}
