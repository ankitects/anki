// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2021 Prateek Singh <prateeksingh3212@gmail.com>

@file:Suppress("UnstableApiUsage")

package com.ichi2.anki.lint.rules

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location.Handle
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.ichi2.anki.lint.utils.Constants
import org.w3c.dom.Element
import java.util.Locale

/**
 * Detector for preference's title whose length may be bigger than 41 character and for menu's title whose length may be bigger than 29 characters.
 * There is an error for each string which is longer than 41 character in English.
 * There is also an error for each string which does not have `maxLength` set to at most 41.
 *
 * Recall that `title` here is not the title of the preference screen, but the title of the preference entry. That is the text in bold that appears on a single line.
 */
class FixedPreferencesTitleLength :
    ResourceXmlDetector(),
    XmlScanner {
    companion object {
        private const val PREFERENCES_ID_TITLE_LENGTH = "FixedPreferencesTitleLength"
        private const val MENU_ID_TITLE_LENGTH = "FixedMenuTitleLength"

        private const val PREFERENCES_ID_MAX_LENGTH = "PreferencesTitleMaxLengthAttr"
        private const val MENU_ID_MAX_LENGTH = "MenuTitleMaxLengthAttr"

        private const val PREFERENCES_TITLE_MAX_LENGTH = 41
        private const val MENU_TITLE_MAX_LENGTH = 28

        private const val PREFERENCES_DESCRIPTION_TITLE_LENGTH =
            "Preference titles should be less than $PREFERENCES_TITLE_MAX_LENGTH characters"
        private const val MENU_DESCRIPTION_TITLE_LENGTH =
            "Preference titles should be less than $MENU_TITLE_MAX_LENGTH characters"

        private const val PREFERENCES_DESCRIPTION_MAX_LENGTH =
            "Preference titles should contain maxLength=\"$PREFERENCES_TITLE_MAX_LENGTH\" attribute"
        private const val MENU_DESCRIPTION_MAX_LENGTH =
            """Preference titles should contain maxLength="$MENU_TITLE_MAX_LENGTH" attribute"""

        // Around 42 (resp. 29) characters is a hard max on emulators in preferences (resp. menu), likely smaller in reality, so use a buffer
        private const val PREFERENCES_EXPLANATION_TITLE_LENGTH =
            "A preference title with more than $PREFERENCES_TITLE_MAX_LENGTH characters " +
                "may fail to display on smaller screens"
        private const val MENU_EXPLANATION_TITLE_LENGTH =
            "A menu title with more than $MENU_TITLE_MAX_LENGTH characters " +
                "may fail to display on smaller screens"

        // Read More: https://support.crowdin.com/file-formats/android-xml/
        private const val PREFERENCES_EXPLANATION_MAX_LENGTH =
            "Preference Title should contain maxLength attribute because " +
                "it fixes translated string length"
        private const val MENU_EXPLANATION_MAX_LENGTH =
            "Preference Title should contain maxLength attribute because " +
                "it fixes translated string length"

        private val implementation = Implementation(FixedPreferencesTitleLength::class.java, Scope.RESOURCE_FILE_SCOPE)
        val PREFERENCES_ISSUE_TITLE_LENGTH: Issue =
            Issue.create(
                PREFERENCES_ID_TITLE_LENGTH,
                PREFERENCES_DESCRIPTION_TITLE_LENGTH,
                PREFERENCES_EXPLANATION_TITLE_LENGTH,
                Constants.ANKI_XML_CATEGORY,
                Constants.ANKI_XML_PRIORITY,
                Constants.ANKI_XML_SEVERITY,
                implementation,
            )
        val MENU_ISSUE_TITLE_LENGTH: Issue =
            Issue.create(
                MENU_ID_TITLE_LENGTH,
                MENU_DESCRIPTION_TITLE_LENGTH,
                MENU_EXPLANATION_TITLE_LENGTH,
                Constants.ANKI_XML_CATEGORY,
                Constants.ANKI_XML_PRIORITY,
                Constants.ANKI_XML_SEVERITY,
                implementation,
            )

        val PREFERENCES_ISSUE_MAX_LENGTH: Issue =
            Issue.create(
                PREFERENCES_ID_MAX_LENGTH,
                PREFERENCES_DESCRIPTION_MAX_LENGTH,
                PREFERENCES_EXPLANATION_MAX_LENGTH,
                Constants.ANKI_XML_CATEGORY,
                Constants.ANKI_XML_PRIORITY,
                Constants.ANKI_XML_SEVERITY,
                implementation,
            )
        val MENU_ISSUE_MAX_LENGTH: Issue =
            Issue.create(
                MENU_ID_MAX_LENGTH,
                MENU_DESCRIPTION_MAX_LENGTH,
                MENU_EXPLANATION_MAX_LENGTH,
                Constants.ANKI_XML_CATEGORY,
                Constants.ANKI_XML_PRIORITY,
                Constants.ANKI_XML_SEVERITY,
                implementation,
            )
        private const val ATTR_TITLE = "android:title"
        private const val ATTR_NAME = "name"
        private const val ATTR_MAX_LENGTH = "maxLength"
    }

    /**
     * Titles of the resources in the xml/ folder.
     * I.e. after the end of [visitElement], it contains
     * "pref__delete_unused_media_files__title" from src/main/res/xml/manage_space
     */
    private val titlesOfPreferenceScreens: MutableSet<String> = HashSet()

    /**
     * Titles of the resources in the menu/ folder.
     */
    private val titlesOfMenuScreens: MutableSet<String> = HashSet()

    /**
     * String resources.
     * I.e. after the end of [visitElement], it'll map "pref__delete_unused_media_files__title" to the element
     * ```
     *     <string name="pref__delete_unused_media_files__title" maxLength="41" comment="Preference title"
     *         >Delete unused media files</string>
     * ```
     * of src/main/res/values/10-preferences.xml, and its handle.
     */
    private val stringResources: MutableMap<String, Handle> = HashMap()

    override fun getApplicableElements(): Collection<String>? = ALL

    override fun visitElement(
        context: XmlContext,
        element: Element,
    ) {
        if (element.hasAttribute(ATTR_TITLE)) {
            val folderName = context.file.parentFile.name
            val titlesToHandle =
                when (folderName) {
                    "xml" -> titlesOfPreferenceScreens
                    "menu" -> titlesOfMenuScreens
                    else -> return
                }
            // Add the `android:title`'s resource name (without "@string/") to `stringResources`
            // if the element has this attribute and the file belongs to src/main/res/xml.

            // Removing the "@string/" part.
            val titleAttribute = element.getAttribute(ATTR_TITLE)
            val stringName = titleAttribute.substringAfter("@string/", "").ifEmpty { return }
            // the entry `stringName` may already exists. Losing the first entry is not an issue, as it won't actually hide that there are issues.
            titlesToHandle.add(stringName)
            return
        }
        if ("values" == context.file.parentFile.name &&
            "string" == element.tagName
        ) {
            // Let's consider the `string`s tag of "src/main/res/values/*.xml"
            stringResources[element.getAttribute(ATTR_NAME)] = context.createLocationHandle(element).apply { clientData = element }
        }
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean =
        folderType == ResourceFolderType.XML || folderType == ResourceFolderType.VALUES || folderType == ResourceFolderType.MENU

    override fun afterCheckEachProject(context: Context) {
        checkFolder(
            context,
            titlesOfPreferenceScreens,
            PREFERENCES_ISSUE_MAX_LENGTH,
            PREFERENCES_ISSUE_MAX_LENGTH,
            PREFERENCES_ISSUE_TITLE_LENGTH,
            "Preference",
            PREFERENCES_TITLE_MAX_LENGTH,
        )
        checkFolder(
            context,
            titlesOfMenuScreens,
            MENU_ISSUE_MAX_LENGTH,
            MENU_ISSUE_MAX_LENGTH,
            MENU_ISSUE_TITLE_LENGTH,
            "Menu",
            MENU_TITLE_MAX_LENGTH,
        )
    }

    fun checkFolder(
        context: Context,
        titles: Set<String>,
        missingMaxLengthIssueIssue: Issue,
        wrongMaxLengthIssue: Issue,
        stringTooLongIssue: Issue,
        folder: String,
        maxLength: Int,
    ) {
        for (title in titles) {
            val stringHandle = stringResources[title] ?: throw IllegalArgumentException(title)
            val stringElement: Element = stringHandle.clientData as Element
            if (!stringElement.hasAttribute(ATTR_MAX_LENGTH)) {
                val message = String.format(Locale.ENGLISH, "$folder title '%s' is missing maxLength=\"%d\" attribute.", title, maxLength)
                context.report(missingMaxLengthIssueIssue, stringHandle.resolve(), message)
            } else if (stringElement.getAttribute(ATTR_MAX_LENGTH).toInt() > maxLength) {
                val message =
                    String.format(
                        Locale.ENGLISH,
                        "$folder title '%s' has maxLength=\"%s\". Its max length should be at most %d.",
                        title,
                        stringElement.getAttribute(ATTR_MAX_LENGTH),
                        maxLength,
                    )
                context.report(wrongMaxLengthIssue, stringHandle.resolve(), message)
            }
            if (stringElement.textContent.length > maxLength) {
                val message =
                    String.format(
                        Locale.ENGLISH,
                        "$folder title '%s' must be less than %d characters (currently %d).",
                        title,
                        maxLength,
                        stringElement.textContent.length,
                    )
                context.report(stringTooLongIssue, stringHandle.resolve(), message)
            }
        }
    }
}
