/*
 * Copyright (c) 2018 Mike Hardy <mike@mikehardy.net>
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

package com.ichi2.utils

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Message
import android.provider.OpenableColumns
import androidx.annotation.CheckResult
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.R
import com.ichi2.anki.common.android.appContext
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.common.coroutines.applicationScope
import com.ichi2.anki.common.crashreporting.CrashReportService
import com.ichi2.anki.common.exception.ManuallyReportedException
import com.ichi2.anki.common.time.TimeManager
import com.ichi2.anki.compat.CompatHelper
import com.ichi2.anki.dialogs.DialogHandler
import com.ichi2.anki.dialogs.DialogHandlerMessage
import com.ichi2.anki.dialogs.ImportDialog
import com.ichi2.anki.onSelectedCsvForImport
import com.ichi2.anki.servicelayer.DebugInfoService
import com.ichi2.anki.showImportDialog
import com.ichi2.anki.ui.internationalization.sentenceCase
import kotlinx.coroutines.launch
import org.jetbrains.annotations.Contract
import timber.log.Timber
import java.io.File
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale

object ImportUtils {
    // A filename should be shortened if over this threshold
    private const val FILE_NAME_SHORTENING_THRESHOLD = 100

    /**
     * This code is used in multiple places to handle package imports
     *
     * @param context for use in resource resolution and path finding
     * @param intent contains the file to import
     * @return null if successful, otherwise error message
     */
    fun handleFileImport(
        context: Context,
        intent: Intent,
    ): ImportResult = FileImporter().handleFileImport(context, intent)

    /**
     * Makes a cached copy of the file selected on [intent] and returns its path
     */
    fun getFileCachedCopy(
        context: Context,
        intent: Intent,
    ): String? = FileImporter().getFileCachedCopy(context, intent)

    fun getFileCachedCopy(
        context: Context,
        uri: Uri,
    ): String? = FileImporter().getFileCachedCopy(context, uri)

    fun showImportUnsuccessfulDialog(
        activity: Activity,
        failure: ImportResult.Failure,
        exitActivity: Boolean,
    ) {
        FileImporter().showImportUnsuccessfulDialog(activity, failure, exitActivity)
    }

    @SuppressLint("LocaleRootUsage")
    fun isCollectionPackage(filename: String?): Boolean =
        filename != null && (filename.lowercase(Locale.ROOT).endsWith(".colpkg") || "collection.apkg" == filename)

    /** @return Whether the file is either a deck, or a collection package */
    @Contract("null -> false")
    fun isValidPackageName(filename: String?): Boolean = FileImporter.isDeckPackage(filename) || isCollectionPackage(filename)

    /**
     * Whether importUtils can handle the given intent
     * Caused by #6312 - A launcher was sending ACTION_VIEW instead of ACTION_MAIN
     */
    fun isInvalidViewIntent(intent: Intent): Boolean = intent.data == null && intent.clipData == null

    fun isFileAValidDeck(fileName: String): Boolean =
        FileImporter.hasExtension(fileName, "apkg") || FileImporter.hasExtension(fileName, "colpkg")

    fun isValidTextOrDataFile(
        context: Context,
        uri: Uri,
    ): Boolean {
        val mimeType = context.contentResolver.getType(uri)
        return mimeType in
            listOf(
                "text/plain",
                "text/comma-separated-values",
                "text/tab-separated-values",
                "text/csv",
                "text/tsv",
            )
    }

    @SuppressWarnings("WeakerAccess")
    open class FileImporter {
        /**
         * This code is used in multiple places to handle package imports
         *
         * @param context for use in resource resolution and path finding
         * @param intent contains the file to import
         * @return null if successful, otherwise error message
         */
        fun handleFileImport(
            context: Context,
            intent: Intent,
        ): ImportResult {
            // This intent is used for opening apkg package files
            // We want to go immediately to DeckPicker, clearing any history in the process
            Timber.i("IntentHandler/ User requested to view a file")
            val extras = if (intent.extras == null) "none" else intent.extras!!.keySet().joinToString(", ")
            Timber.i("Intent: %s. Data: %s", intent, extras)
            return try {
                handleFileImportInternal(context, intent)
            } catch (e: Exception) {
                CrashReportService.sendExceptionReport(e, "handleFileImport")
                Timber.e(e, "failed to handle import intent")
                ImportResult.Failure(
                    humanReadableMessage = context.getString(R.string.import_error_handle_exception, e.localizedMessage),
                    exception = e,
                )
            }
        }

        private fun handleFileImportInternal(
            context: Context,
            intent: Intent,
        ): ImportResult {
            val importPathUri = getDataUri(intent)
            return if (importPathUri != null) {
                handleContentProviderFile(context, importPathUri, intent)
            } else {
                ImportResult.Failure(context.getString(R.string.import_error_handle_exception))
            }
        }

        fun getFileCachedCopy(
            context: Context,
            uri: Uri,
        ): String? {
            val filename = validateFileName(getFileNameFromContentProvider(context, uri) ?: return null)
            val tempFile = File(context.cacheDir, filename)
            return when (val result = copyFileToCache(context, uri, tempFile.absolutePath)) {
                is CacheFileResult.Success -> result.path
                else -> null
            }
        }

        /**
         * Makes a cached copy of the file selected on [intent] and returns its path
         */
        fun getFileCachedCopy(
            context: Context,
            intent: Intent,
        ): String? {
            val uri = getDataUri(intent) ?: return null
            return getFileCachedCopy(context, uri)
        }

        fun handleContentProviderFile(
            context: Context,
            importPathUri: Uri,
            intent: Intent? = null,
        ): ImportResult {
            // Note: intent.getData() can be null. Use data instead.
            if (!isValidImportType(context, importPathUri)) {
                return ImportResult.Failure(context.getString(R.string.import_log_no_apkg))
            }
            // Get the original filename from the content provider URI
            var filename = getFileNameFromContentProvider(context, importPathUri)
            // Hack to fix bug where ContentResolver not returning filename correctly
            if (filename == null) {
                when (intent?.type) {
                    "application/apkg", "application/zip" -> {
                        // Set a dummy filename if MIME type provided or is a valid zip file
                        filename = "unknown_filename.apkg"
                        Timber.w("Could not retrieve filename from ContentProvider, but was valid zip file so we try to continue")
                    }

                    else -> {
                        Timber.e("Could not retrieve filename from ContentProvider")
                        CrashReportService.sendExceptionReport(
                            RuntimeException("Could not import apkg from ContentProvider"),
                            "IntentHandler.java",
                            "apkg import failed; mime type ${intent?.type}",
                        )
                        return ImportResult.Failure(
                            AnkiDroidApp.appResources.getString(
                                R.string.import_error_content_provider,
                                AnkiDroidApp.manualUrl + "#importing",
                            ),
                        )
                    }
                }
            }
            if (isValidTextOrDataFile(context, importPathUri)) {
                (context as Activity).onSelectedCsvForImport(intent!!)
                return ImportResult.Success
            } else if (!isValidPackageName(filename)) {
                return if (isAnkiDatabase(filename)) {
                    // .anki2 files aren't supported by Anki Desktop, we should eventually support them, because we can
                    // but for now, show a "nice" error.
                    ImportResult.Failure(context.resources.getString(R.string.import_error_load_imported_database))
                } else {
                    // Don't import if file doesn't have an Anki package extension
                    ImportResult.Failure(context.resources.getString(R.string.import_error_not_apkg_extension, filename))
                }
            }

            // Copy to temporary file
            filename = validateFileName(filename)
            val tempOutDir: String = Uri.fromFile(File(context.cacheDir, filename)).encodedPath!!

            copyFileToCache(context, importPathUri, tempOutDir).asErrorDetails()?.let { details ->
                CrashReportService.sendExceptionReport(details.exceptionForReport, "ImportUtils")
                return ImportResult.Failure(
                    title = details.buildTitle(context),
                    humanReadableMessage = details.buildHumanReadableMessage(context),
                    exception = details.userFacingException,
                )
            }
            sendShowImportFileDialogMsg(tempOutDir)
            return ImportResult.Success
        }

        fun isValidImportType(
            context: Context,
            importPathUri: Uri,
        ): Boolean {
            val fileName = getFileNameFromContentProvider(context, importPathUri)
            return when {
                isDeckPackage(fileName) -> true
                isCollectionPackage(fileName) -> true
                isValidTextOrDataFile(context, importPathUri) -> true
                else -> false
            }
        }

        private fun isAnkiDatabase(filename: String?): Boolean = filename != null && hasExtension(filename, "anki2")

        @NeedsTest("Add test for the fallback, ensure the fallback filename \"file_<timestamp>.<ext>\" is produced when decoding fails")
        private fun validateFileName(fileName: String): String {
            // #6137 - filenames can be too long when URLEncoded
            return try {
                val encoded = URLEncoder.encode(fileName, "UTF-8")
                if (encoded.length <= FILE_NAME_SHORTENING_THRESHOLD) {
                    Timber.d("No filename truncation necessary")
                    fileName
                } else {
                    Timber.d("Filename was longer than %d, shortening", FILE_NAME_SHORTENING_THRESHOLD)
                    // take 90 instead of 100 so we don't get the extension
                    val substringLength = FILE_NAME_SHORTENING_THRESHOLD - 10
                    val shortenedFileName = encoded.take(substringLength) + "..." + getExtension(fileName)
                    Timber.d("Shortened filename '%s' to '%s'", fileName, shortenedFileName)
                    // if we don't decode, % is double-encoded
                    URLDecoder.decode(shortenedFileName, "UTF-8")
                }
            } catch (e: Exception) {
                Timber.w(e, "Failed to shorten file: %s", fileName)
                "file_${TimeManager.time.intTimeMS()}.${getExtension(fileName)}"
            }
        }

        @CheckResult
        private fun getExtension(fileName: String): String {
            val file = Uri.fromFile(File(fileName))
            return AssetHelper.getFileExtensionFromFilePath(file.toString())
        }

        protected open fun getFileNameFromContentProvider(
            context: Context,
            data: Uri,
        ): String? {
            var filename: String? = null
            try {
                context.contentResolver
                    .query(
                        data,
                        arrayOf(OpenableColumns.DISPLAY_NAME),
                        null,
                        null,
                        null,
                    ).use { cursor ->
                        if (cursor != null && cursor.moveToFirst()) {
                            filename = cursor.getString(0)
                            Timber.d("handleFileImport() Importing from content provider: %s", filename)
                        }
                    }
            } catch (e: Exception) {
                Timber.w(e, "Error querying content provider")
                filename = null // Set filename to null in case of an exception
            }
            return filename
        }

        fun showImportUnsuccessfulDialog(
            activity: Activity,
            failure: ImportResult.Failure,
            exitActivity: Boolean,
        ) {
            // Use applicationScope: IntentHandler calls this and does not have a lifecycleScope
            fun copyDebugInfo(debugInfo: String) =
                applicationScope.launch {
                    Timber.i("copying debug info to clipboard")
                    val stringToCopy =
                        buildString {
                            appendLine(debugInfo)
                            appendLine()
                            appendLine(DebugInfoService.getDebugInfo(activity))
                        }

                    appContext.copyToClipboard(stringToCopy)
                }

            Timber.d("showImportUnsuccessfulDialog() message %s", failure.humanReadableMessage)
            val title = failure.title ?: activity.getString(R.string.import_title_error)
            val dialog =
                AlertDialog.Builder(activity).show {
                    title(text = title)
                    message(text = failure.humanReadableMessage)
                    setCancelable(false)
                    positiveButton(R.string.dialog_ok) {
                        if (exitActivity) {
                            activity.finish()
                        }
                    }
                    if (failure.toDebugInfo() != null) {
                        negativeButton(text = with(activity) { TR.sentenceCase.copyDebugInfo })
                    }
                }
            // 'copy' should not close the dialog
            failure.toDebugInfo()?.let { debugInfo ->
                dialog.negativeButton.setOnClickListener {
                    copyDebugInfo(debugInfo)
                }
            }
        }

        /**
         * Copy the data from the intent to a temporary file
         * @param data intent from which to get input stream
         * @param tempPath temporary path to store the cached file
         * @return see [CacheFileResult]
         */
        protected open fun copyFileToCache(
            context: Context,
            data: Uri,
            tempPath: String,
        ): CacheFileResult =
            try {
                context.contentResolver.openInputStreamSafe(data)?.use { input ->
                    CompatHelper.compat.copyFile(input, tempPath)
                    CacheFileResult.Success(tempPath)
                } ?: run {
                    Timber.w("Content provider crashed")
                    CacheFileResult.ContentProviderCrashed
                }
            } catch (e: Exception) {
                Timber.w("Could not copy file to %s", tempPath)
                CacheFileResult.Error(e)
            }

        sealed class CacheFileResult {
            data class Success(
                val path: String,
            ) : CacheFileResult()

            data class Error(
                val exception: Exception,
            ) : CacheFileResult()

            data object ContentProviderCrashed : CacheFileResult()

            fun asErrorDetails(): CacheErrorDetails? =
                when (this) {
                    is Success -> null
                    is Error ->
                        CacheErrorDetails(
                            exceptionForReport = exception,
                            userFacingException = exception,
                        )
                    is ContentProviderCrashed ->
                        CacheErrorDetails(
                            exceptionForReport = ManuallyReportedException("Content provider crashed"),
                            userFacingException = null,
                        )
                }

            data class CacheErrorDetails(
                val exceptionForReport: Exception,
                val userFacingException: Exception?,
            ) {
                fun buildTitle(context: Context) = context.getString(R.string.import_error_copy_to_cache_title)

                fun buildHumanReadableMessage(context: Context) =
                    buildString {
                        // "oaz: Cello error 2."
                        if (userFacingException != null) {
                            appendLine(userFacingException.message)
                            appendLine()
                        }

                        // "This is often caused by the file provider, try these fixes:"
                        val suggestedFixes =
                            buildString {
                                // build a bulleted list of suggested fixes
                                // TODO: "Open the file using your device’s file browser app" -
                                //  convert it, and others to actions
                                for (line in context.resources.getStringArray(R.array.import_cache_error_resolutions)) {
                                    appendLine("\u2022 $line")
                                }
                            }
                        append(context.getString(R.string.import_error_copy_to_cache_explanation, suggestedFixes))
                    }
            }
        }

        companion object {
            fun getDataUri(intent: Intent): Uri? {
                if (intent.data == null) {
                    Timber.i("No intent data. Attempting to read clip data.")
                    if (intent.clipData == null || intent.clipData!!.itemCount == 0) {
                        return null
                    }
                    return intent.clipData?.getItemAt(0)?.uri
                }
                // If Uri is of scheme which is supported by ContentResolver, read the contents
                val intentUriScheme = intent.data!!.scheme
                return when (intentUriScheme) {
                    ContentResolver.SCHEME_CONTENT,
                    ContentResolver.SCHEME_FILE,
                    ContentResolver.SCHEME_ANDROID_RESOURCE,
                    -> {
                        Timber.i("Attempting to read data from intent.")
                        intent.data
                    }
                    else -> null
                }
            }

            /**
             * Send a Message to AnkiDroidApp so that the DialogMessageHandler shows the Import apkg dialog.
             * @param importPath path of to apkg file which will be imported
             */
            private fun sendShowImportFileDialogMsg(importPath: String) {
                // Get the filename from the path
                val filename = File(importPath).name

                val dialogMessage =
                    if (isCollectionPackage(filename)) {
                        CollectionImportReplace(importPath)
                    } else {
                        CollectionImportAdd(importPath)
                    }
                // Store the message in AnkiDroidApp message holder, which is loaded later in AnkiActivity.onResume
                DialogHandler.storeMessage(dialogMessage.toMessage())
            }

            @SuppressLint("LocaleRootUsage")
            internal fun isDeckPackage(filename: String?): Boolean =
                filename != null && filename.lowercase(Locale.ROOT).endsWith(".apkg") && "collection.apkg" != filename

            @SuppressLint("LocaleRootUsage")
            fun hasExtension(
                filename: String,
                extension: String?,
            ): Boolean {
                val fileParts = filename.split("\\.".toRegex()).toTypedArray()
                if (fileParts.size < 2) {
                    return false
                }
                val extensionSegment = fileParts[fileParts.size - 1]
                // either "apkg", or "apkg (1)".
                // COULD_BE_BETTE: accepts .apkgaa"
                return extensionSegment.lowercase(Locale.ROOT).startsWith(extension!!)
            }
        }
    }

    /** Show confirmation dialog asking to confirm import with replace when file called "collection.apkg" */
    class CollectionImportReplace(
        private val importPath: String,
    ) : DialogHandlerMessage(
            which = WhichDialogHandler.MSG_SHOW_COLLECTION_IMPORT_REPLACE_DIALOG,
            analyticName = "ImportReplaceDialog",
        ) {
        override fun handleAsyncMessage(activity: AnkiActivity) {
            // Handle import of collection package APKG
            activity.showImportDialog(ImportDialog.Type.DIALOG_IMPORT_REPLACE_CONFIRM, importPath)
        }

        override fun toMessage(): Message =
            Message.obtain().apply {
                data = bundleOf("importPath" to importPath)
                what = this@CollectionImportReplace.what
            }

        companion object {
            fun fromMessage(message: Message): CollectionImportReplace = CollectionImportReplace(message.data.getString("importPath")!!)
        }
    }

    /** Show confirmation dialog asking to confirm import with add */
    class CollectionImportAdd(
        private val importPath: String,
    ) : DialogHandlerMessage(
            WhichDialogHandler.MSG_SHOW_COLLECTION_IMPORT_ADD_DIALOG,
            "ImportAddDialog",
        ) {
        override fun handleAsyncMessage(activity: AnkiActivity) {
            // Handle import of deck package APKG
            activity.showImportDialog(ImportDialog.Type.DIALOG_IMPORT_ADD_CONFIRM, importPath)
        }

        override fun toMessage(): Message =
            Message.obtain().apply {
                data = bundleOf("importPath" to importPath)
                what = this@CollectionImportAdd.what
            }

        companion object {
            fun fromMessage(message: Message): CollectionImportAdd = CollectionImportAdd(message.data.getString("importPath")!!)
        }
    }
}

sealed class ImportResult {
    data object Success : ImportResult()

    data class Failure(
        val humanReadableMessage: String,
        val exception: Exception? = null,
        val title: String? = null,
    ) : ImportResult() {
        fun toDebugInfo(): String? = exception?.stackTraceToString()
    }
}
