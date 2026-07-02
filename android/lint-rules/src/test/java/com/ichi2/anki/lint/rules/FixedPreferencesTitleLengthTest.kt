// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2021 Prateek Singh <prateeksingh3212@gmail.com>

package com.ichi2.anki.lint.rules

import com.android.tools.lint.checks.infrastructure.TestFiles
import com.android.tools.lint.checks.infrastructure.TestLintTask
import org.intellij.lang.annotations.Language
import org.junit.Test

class FixedPreferencesTitleLengthTest {
    companion object {
        @Language("XML")
        val strings1XmlValid = """<resources>
    <string name="app_name" maxLength="41">app_name</string>
    <string name="pref_cat_general_summ">pref_cat_general_summ</string>
    <string name="button_sync" maxLength="28">button_sync</string>
    <string name="sync_account" maxLength="41">sync_account</string>
</resources>"""

        @Language("XML")
        val strings10XmlValid = """<resources>
    <string name="sync_account_summ_logged_out">sync_account_summ</string>
    <string name="sync_fetch_missing_media_summ">sync_fetch_missing</string>
    <string name="checkbox_title" maxLength="41">Less than limit</string>
    <string name="number_title" maxLength="41">number_title</string>
</resources>"""

        @Language("XML")
        val strings10XmlInvalid = """<resources>
<!--This preference's title should have a maxLength attribute-->
    <string name="app_name">app_name</string>
    <string name="pref_cat_general_summ">pref_cat_general_summ</string>
<!--Its maxLength attribute is greater than 41-->
    <string name="button_sync" maxLength="55">button_sync</string>
<!--This is correct because it contains maxLength=41 and character length is less than 42-->
    <string name="sync_account" maxLength="41">sync_account</string>
</resources>"""

        @Language("XML")
        val strings1XmlInvalid = """<resources>
    <string name="sync_account_summ_logged_out">sync_account_summ</string>
    <string name="sync_fetch_missing_media_summ">sync_fetch_missing</string>
<!--The string is 44 characters long, more than 41.-->
    <string name="checkbox_title" maxLength="41">This String contains character more than limit</string>
<!--The string contains unicode character -->
    <string name="number_title" maxLength="41">This String contains a £.</string>
</resources>"""

        @Language("XML")
        val preferenceString =
            """<PreferenceScreen xmlns:android="http://schemas.android.com/apk/res/android"
    android:title="@string/app_name"
    android:summary="@string/pref_cat_general_summ" >
    <PreferenceCategory android:title="@string/button_sync" >
        <Preference
            android:dialogTitle="@string/sync_account"
            android:key="syncAccount"
            android:summary="@string/sync_account_summ_logged_out"
            android:title="@string/sync_account" >
            <intent
                android:targetClass="com.ichi2.anki.MyAccount"
                android:targetPackage="com.ichi2.anki" />
        </Preference>
        <com.ichi2.preferences.IncrementerNumberRangePreference
            android:defaultValue="true"
            android:key="unicode"
            android:summary="@string/unicode"
            android:title = "@string/number_title" />
        <CheckBoxPreference
            android:defaultValue="true"
            android:key="syncFetchesMedia"
            android:summary="@string/sync_fetch_missing_media_summ"
            android:title = "@string/checkbox_title" />
    </PreferenceCategory>
</PreferenceScreen>"""
    }

    @Language("XML")
    val preferenceWithHardcodedTitle = """
        <PreferenceScreen xmlns:android="http://schemas.android.com/apk/res/android"
            android:title="Dev options"
            android:key="@string/pref_dev_options_screen_key">
            <SwitchPreferenceCompat
                android:title="New congrats screen"
                android:key="@string/new_congrats_screen_pref_key"
                android:defaultValue="false"/>
        </PreferenceScreen>"""

    @Test
    fun showsErrorForInvalidFile() {
        TestLintTask
            .lint()
            .allowMissingSdk()
            .allowCompilationErrors()
            .files(
                TestFiles.xml("res/xml/preference_general_invalid.xml", preferenceString),
                TestFiles.xml("res/values/10-preferences.xml", strings10XmlInvalid),
                TestFiles.xml("res/values/01-core.xml", strings1XmlInvalid),
            ).issues(
                FixedPreferencesTitleLength.PREFERENCES_ISSUE_TITLE_LENGTH,
                FixedPreferencesTitleLength.PREFERENCES_ISSUE_MAX_LENGTH,
            ).run()
            .expectErrorCount(3)
            .expect(
                """res/values/01-core.xml:5: Error: Preference title 'checkbox_title' must be less than 41 characters (currently 46). [FixedPreferencesTitleLength]
    <string name="checkbox_title" maxLength="41">This String contains character more than limit</string>
    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
res/values/10-preferences.xml:3: Error: Preference title 'app_name' is missing maxLength="41" attribute. [PreferencesTitleMaxLengthAttr]
    <string name="app_name">app_name</string>
    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
res/values/10-preferences.xml:6: Error: Preference title 'button_sync' has maxLength="55". Its max length should be at most 41. [PreferencesTitleMaxLengthAttr]
    <string name="button_sync" maxLength="55">button_sync</string>
    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
3 errors, 0 warnings""",
            )
    }

    @Test
    fun showsNoErrorForValidFile() {
        TestLintTask
            .lint()
            .allowMissingSdk()
            .allowCompilationErrors()
            .files(
                TestFiles.xml("res/xml/preference_general_valid.xml", preferenceString),
                TestFiles.xml("res/values/10-preferences.xml", strings10XmlValid),
                TestFiles.xml("res/values/01-core.xml", strings1XmlValid),
            ).issues(
                FixedPreferencesTitleLength.PREFERENCES_ISSUE_MAX_LENGTH,
                FixedPreferencesTitleLength.PREFERENCES_ISSUE_TITLE_LENGTH,
            ).run()
            .expectClean()
    }

    @Test
    fun hardcodedTitleIsNotFlagged() {
        TestLintTask
            .lint()
            .allowMissingSdk()
            .allowCompilationErrors()
            .files(
                TestFiles.xml("res/xml/preference_general_valid.xml", preferenceWithHardcodedTitle),
                TestFiles.xml("res/values/01-core.xml", strings1XmlValid),
            ).issues(
                FixedPreferencesTitleLength.PREFERENCES_ISSUE_MAX_LENGTH,
                FixedPreferencesTitleLength.PREFERENCES_ISSUE_TITLE_LENGTH,
            ).run()
            .expectClean()
    }
}
