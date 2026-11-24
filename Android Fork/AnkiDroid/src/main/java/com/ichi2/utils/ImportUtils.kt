// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2018 Mike Hardy <mike@mikehardy.net>

package com.ichi2.utils

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Message
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.annotation.CheckResult
import androidx.appcompat.app.AlertDialog
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.common.android.appContext
import com.ichi2.anki.common.coroutines.applicationScope
import com.ichi2.anki.common.crashreporting.CrashReportService
import com.ichi2.anki.common.exception.ManuallyReportedException
import com.ichi2.anki.common.time.TimeManager
import com.ichi2.anki.compat.CompatHelper
import com.ichi2.anki.dialogs.DialogHandlerMessage
import com.ichi2.anki.dialogs.ImportDialog
import com.ichi2.anki.servicelayer.DebugInfoService
import com.ichi2.anki.showImportDialog
import com.ichi2.anki.ui.internationalization.sentenceCase
import com.ichi2.anki.utils.MimeTypeUtils
import com.ichi2.utils.IntentUtil.resolveMimeType
import kotlinx.coroutines.launch
import org.jetbrains.annotations.Contract
import timber.log.Timber
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale

object ImportUtils {
    // A filename should be shortened if over this threshold
    private const val FILE_NAME_SHORTENING_THRESHOLD = 100
    private const val EXTERNAL_STORAGE_PROVIDER_AUTHORITY = "com.android.externalstorage.documents"

    /** Resolve the source and select its importer without copying files or starting UI. */
    fun resolveImport(
        context: Context,
        intent: Intent,
    ): ImportResolution = FileImporter().resolveImport(context, intent)

    /**
     * Resolve the source and copy it to the cache synchronously on the calling thread.
     * Does not launch UI or store a package confirmation.
     *
     * @return The resolved source metadata and cached path, or details of a preparation failure.
     */
    fun prepareImport(
        context: Context,
        intent: Intent,
    ): ImportResult = FileImporter().prepareImport(context, intent)

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

    /**
     * [file]'s absolute path with only its filename Uri-encoded.
     */
    internal fun encodeFileName(file: File): String = File(file.parentFile, Uri.encode(file.name)).absolutePath

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

    @SuppressWarnings("WeakerAccess")
    open class FileImporter {
        fun resolveImport(
            context: Context,
            intent: Intent,
        ): ImportResolution =
            try {
                resolveImportInternal(context, intent)
            } catch (e: Exception) {
                Timber.w(e, "Could not resolve import source")
                ImportResolution.Failure(
                    ImportResult.Failure(
                        humanReadableMessage = context.getString(CommonString.import_error_handle_exception, e.localizedMessage),
                        exception = e,
                    ),
                )
            }

        private fun resolveImportInternal(
            context: Context,
            intent: Intent,
        ): ImportResolution {
            val uri =
                getDataUri(intent)
                    ?: return ImportResolution.Failure(ImportResult.Failure(context.getString(CommonString.import_log_no_apkg)))
            val intentMimeType = intent.resolveMimeType()
            val providerMimeType by lazy {
                try {
                    context.contentResolver.getType(uri)
                } catch (e: Exception) {
                    Timber.w(e, "Could not resolve import MIME type")
                    null
                }
            }
            val packageMimeType by lazy {
                val packageTypes = MimeTypeUtils.APKG_MIME_TYPES + MimeTypeUtils.COLPKG_MIME_TYPES
                intentMimeType.takeIf { it in packageTypes } ?: providerMimeType.takeIf { it in packageTypes }
            }
            val providedName =
                getFileNameFromContentProvider(context, uri)
                    ?: when {
                        packageMimeType in MimeTypeUtils.COLPKG_MIME_TYPES -> "unknown_filename.colpkg"
                        packageMimeType in MimeTypeUtils.APKG_MIME_TYPES || intentMimeType == "application/zip" -> "unknown_filename.apkg"
                        else -> return ImportResolution.Failure(
                            ImportResult.Failure(
                                context.getString(CommonString.import_error_content_provider, AnkiDroidApp.manualUrl + "#importing"),
                            ),
                        )
                    }
            val filename = validateFileName(providedName)
            val source = ImportResolution.Source(uri, filename)
            return when {
                // Package filenames and specific package MIME types take precedence over text hints.
                isCollectionPackage(filename) -> ImportResolution.CollectionPackage(source)
                isDeckPackage(filename) -> ImportResolution.DeckPackage(source)
                packageMimeType in MimeTypeUtils.COLPKG_MIME_TYPES -> ImportResolution.CollectionPackage(source)
                packageMimeType in MimeTypeUtils.APKG_MIME_TYPES -> ImportResolution.DeckPackage(source)
                getExtension(filename).lowercase() in listOf("csv", "tsv", "txt") ||
                    intentMimeType in MimeTypeUtils.CSV_TSV_MIME_TYPES || providerMimeType in MimeTypeUtils.CSV_TSV_MIME_TYPES ->
                    ImportResolution.Text(source)
                isAnkiDatabase(filename) ->
                    ImportResolution.Failure(ImportResult.Failure(context.getString(CommonString.import_error_load_imported_database)))
                else ->
                    ImportResolution.Failure(
                        ImportResult.Failure(context.getString(CommonString.import_error_not_apkg_extension, filename)),
                    )
            }
        }

        /** See [ImportUtils.prepareImport] for the preparation and result contract. */
        fun prepareImport(
            context: Context,
            intent: Intent,
        ): ImportResult =
            try {
                when (val resolution = resolveImport(context, intent)) {
                    is ImportResolution.Failure -> resolution.error
                    is ImportResolution.ResolvedFile -> cacheImport(context, resolution)
                }
            } catch (e: Exception) {
                CrashReportService.sendExceptionReport(e, "prepareImport")
                ImportResult.Failure(
                    humanReadableMessage = context.getString(CommonString.import_error_handle_exception, e.localizedMessage),
                    exception = e,
                )
            }

        fun getFileCachedCopy(
            context: Context,
            uri: Uri,
        ): String? {
            val filename = validateFileName(getFileNameFromContentProvider(context, uri) ?: return null)
            val tempFile =
                try {
                    context.cacheDir.withFileNameSafe(filename)
                } catch (_: SecurityException) {
                    // Do not log the exception: it may interpolate attacker-controlled paths (PII).
                    Timber.e("Path traversal detected in getFileCachedCopy")
                    return null
                } catch (_: IllegalArgumentException) {
                    // Do not log the exception: it may interpolate attacker-controlled paths (PII).
                    Timber.e("Path traversal detected in getFileCachedCopy")
                    return null
                }
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

        private fun cacheImport(
            context: Context,
            file: ImportResolution.ResolvedFile,
        ): ImportResult {
            // Copy to temporary file
            val checkFile =
                try {
                    context.cacheDir.withFileNameSafe(file.source.fileName)
                } catch (_: SecurityException) {
                    // Do not log the exception: it may interpolate attacker-controlled paths (PII).
                    Timber.e("Path traversal detected in cacheImport")
                    return ImportResult.Failure(context.getString(CommonString.import_error_handle_exception, "Invalid path"))
                } catch (_: IllegalArgumentException) {
                    // Do not log the exception: it may interpolate attacker-controlled paths (PII).
                    Timber.e("Path traversal detected in cacheImport")
                    return ImportResult.Failure(context.getString(CommonString.import_error_handle_exception, "Invalid path"))
                }
            // The package WebView/backend route expects encoded filenames on disk; CSV uses a raw path.
            val tempOutDir =
                when (file) {
                    is ImportResolution.Text -> checkFile.absolutePath
                    is ImportResolution.DeckPackage, is ImportResolution.CollectionPackage -> encodeFileName(checkFile)
                }

            copyFileToCache(context, file.source.uri, tempOutDir).asErrorDetails()?.let { details ->
                details.exceptionForReport?.let { CrashReportService.sendExceptionReport(it, "ImportUtils") }
                return ImportResult.Failure(
                    title = details.buildTitle(context),
                    humanReadableMessage = details.buildHumanReadableMessage(context),
                    exception = details.userFacingException,
                )
            }
            return ImportResult.Success(file, tempOutDir)
        }

        private fun isAnkiDatabase(filename: String?): Boolean = filename != null && hasExtension(filename, "anki2")

        /**
         * Uses the last path segment ([File.name]) so directory components cannot escape the
         * destination. Empty names, `.`, and `..` become `unnamed_file`. Leading dots on a real
         * filename are preserved (e.g. `.hidden.apkg`, `..apkg`).
         */
        @CheckResult
        private fun sanitizeFileName(fileName: String): String {
            val sanitized = File(fileName).name
            return if (sanitized.isEmpty() || sanitized == "." || sanitized == "..") {
                "unnamed_file"
            } else {
                sanitized
            }
        }

        private fun validateFileName(fileName: String): String {
            // #6137 - filenames can be too long when URLEncoded
            val sanitized = sanitizeFileName(fileName)
            return try {
                val encoded = URLEncoder.encode(sanitized, "UTF-8")
                if (encoded.length <= FILE_NAME_SHORTENING_THRESHOLD) {
                    Timber.d("No filename truncation necessary")
                    sanitized
                } else {
                    Timber.d("Filename was longer than %d, shortening", FILE_NAME_SHORTENING_THRESHOLD)
                    // take 90 instead of 100 so we don't get the extension
                    val substringLength = FILE_NAME_SHORTENING_THRESHOLD - 10
                    val shortenedFileName = encoded.take(substringLength) + "..." + getExtension(fileName)
                    Timber.d("Shortened filename")
                    // if we don't decode, % is double-encoded
                    sanitizeFileName(URLDecoder.decode(shortenedFileName, "UTF-8"))
                }
            } catch (e: Exception) {
                Timber.w(e, "Failed to shorten file")
                sanitizeFileName("file_${TimeManager.time.intTimeMS()}.${getExtension(fileName)}")
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
                            Timber.d("Resolved import filename from content provider: %s", filename)
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

                    appContext.copyToClipboard(TruncatedString.from(stringToCopy))
                }

            Timber.d("showImportUnsuccessfulDialog() message %s", failure.humanReadableMessage)
            val title = failure.title ?: activity.getString(CommonString.import_title_error)
            val dialog =
                AlertDialog.Builder(activity).show {
                    title(text = title)
                    message(text = failure.humanReadableMessage)
                    setCancelable(false)
                    positiveButton(CommonString.dialog_ok) {
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
                openImportSource(context, data)?.let { input ->
                    copyToCache(input, tempPath)
                } ?: run {
                    Timber.w("Content provider crashed")
                    CacheFileResult.ContentProviderCrashed
                }
            } catch (e: FileNotFoundException) {
                // The selected file may have been deleted since the provider returned its URI.
                Timber.w(e, "Import source file is unavailable")
                CacheFileResult.SourceFileUnavailable(e)
            } catch (e: IllegalStateException) {
                if (e.message?.contains("pending/trashed") == true) {
                    Timber.w(e, "Import source file is pending or trashed")
                    CacheFileResult.SourceFileUnavailable(e)
                } else {
                    Timber.w("Could not open import source")
                    CacheFileResult.Error(e)
                }
            } catch (e: IllegalArgumentException) {
                // "TermuxContentProvider requires `allow-external-apps` property to be set to `true` in `~/.termux/termux.properties` file."
                if (e.message?.contains("TermuxContentProvider") == true && e.message?.contains("allow-external-apps") == true) {
                    Timber.w(e, "Import source file access was refused by Termux")
                    CacheFileResult.SourceFileUnavailable(e)
                } else {
                    Timber.w("Could not open import source")
                    CacheFileResult.Error(e)
                }
            } catch (e: Exception) {
                Timber.w("Could not open import source")
                CacheFileResult.Error(e)
            }

        private fun openImportSource(
            context: Context,
            data: Uri,
        ): InputStream? =
            try {
                context.contentResolver.openInputStreamSafe(data)
            } catch (e: SecurityException) {
                openThroughMediaStore(context, data) ?: throw e
            }

        private fun openThroughMediaStore(
            context: Context,
            documentUri: Uri,
        ): InputStream? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
            if (documentUri.authority != EXTERNAL_STORAGE_PROVIDER_AUTHORITY) return null
            Timber.i("Storage provider could not open the import source, retrying through MediaStore")
            return try {
                MediaStore.getMediaUri(context, documentUri)?.let { context.contentResolver.openInputStreamSafe(it) }
            } catch (_: Exception) {
                Timber.w("Could not open import source through MediaStore")
                null
            }
        }

        /**
         * Returns [CacheFileResult.Error] for any [Exception] while copying or closing [input].
         */
        private fun copyToCache(
            input: InputStream,
            tempPath: String,
        ): CacheFileResult =
            try {
                input.use { CompatHelper.compat.copyFile(it, tempPath) }
                CacheFileResult.Success(tempPath)
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

            data class SourceFileUnavailable(
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
                    is SourceFileUnavailable ->
                        CacheErrorDetails(
                            exceptionForReport = null,
                            userFacingException = exception,
                        )
                    is ContentProviderCrashed ->
                        CacheErrorDetails(
                            exceptionForReport = ManuallyReportedException("Content provider crashed"),
                            userFacingException = null,
                        )
                }

            data class CacheErrorDetails(
                val exceptionForReport: Exception?,
                val userFacingException: Exception?,
            ) {
                fun buildTitle(context: Context) = context.getString(CommonString.import_error_copy_to_cache_title)

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
                        append(context.getString(CommonString.import_error_copy_to_cache_explanation, suggestedFixes))
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
                data = Bundle().apply { putString("importPath", importPath) }
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
                data = Bundle().apply { putString("importPath", importPath) }
                what = this@CollectionImportAdd.what
            }

        companion object {
            fun fromMessage(message: Message): CollectionImportAdd = CollectionImportAdd(message.data.getString("importPath")!!)
        }
    }
}

sealed class ImportResult {
    data class Success(
        val file: ImportResolution.ResolvedFile,
        val path: String,
    ) : ImportResult()

    data class Failure(
        val humanReadableMessage: String,
        val exception: Exception? = null,
        val title: String? = null,
    ) : ImportResult() {
        fun toDebugInfo(): String? = exception?.stackTraceToString()
    }
}
