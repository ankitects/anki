// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2022 Brayan Oliveira <brayandso.dev@gmail.com>

package com.ichi2.anki.lint.rules

import com.android.tools.lint.checks.infrastructure.TestFiles
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import org.intellij.lang.annotations.Language
import org.junit.Test

class HardcodedPreferenceKeyTest {
    @Language("XML")
    private val invalidXmlFile =
        "" +
            "<PreferenceScreen xmlns:android=\"http://schemas.android.com/apk/res/android\" " +
            "    android:title=\"@string/pref_cat_advanced\"                                " +
            "    android:summary=\"@string/pref_cat_advanced_summ\"                         " +
            "    android:key=\"pref_screen_advanced\">                                      " +
            "        <EditTextPreference                                                    " +
            "            android:defaultValue=\"/sdcard/AnkiDroid\"                         " +
            "            android:key=\"deckPath\"                                           " +
            "            android:summary=\"@string/preference_summary_literal\"             " +
            "            android:title=\"@string/preference_literal\" />                    " +
            "        <com.ichi2.ui.ConfirmationPreference                                   " +
            "            android:key=\"force_full_sync\"                                    " +
            "            android:dependency=\"deckPath\"                                    " +
            "            android:title=\"@string/force_full_sync_title\"                    " +
            "            android:summary=\"@string/force_full_sync_summary\" />             " +
            "</PreferenceScreen>"

    @Language("XML")
    private val validXmlFile = """
        <PreferenceScreen xmlns:android="http://schemas.android.com/apk/res/android"
            xmlns:app="http://schemas.android.com/apk/res-auto"
            android:title="@string/pref_cat_general"
            android:key="@string/pref_general_screen_key">
            <Preference
                android:title="foo"
                android:summary="bar"/>
            <ListPreference
                android:defaultValue="@string/empty_string"
                android:key="@string/pref_language_key"
                android:icon="@drawable/ic_language_black_24dp"
                android:title="@string/language"
                app:useSimpleSummaryProvider="true"/>
            <ListPreference
                android:defaultValue="2"
                android:entries="@array/error_reporting_choice_labels"
                android:entryValues="@array/error_reporting_choice_values"
                android:key="@string/error_reporting_mode_key"
                android:title="@string/error_reporting_choice"
                app:useSimpleSummaryProvider="true"/>
        </PreferenceScreen>
    """

    @Test
    fun showsErrorForInvalidFile() {
        lint()
            .allowMissingSdk()
            .allowCompilationErrors()
            .files(TestFiles.xml("res/xml/invalidpreference.xml", invalidXmlFile))
            .issues(HardcodedPreferenceKey.ISSUE)
            .run()
            .expectErrorCount(4)
    }

    @Test
    fun showsNoErrorForValidFile() {
        lint()
            .allowMissingSdk()
            .allowCompilationErrors()
            .files(TestFiles.xml("res/xml/validpreference.xml", validXmlFile))
            .issues(HardcodedPreferenceKey.ISSUE)
            .run()
            .expectClean()
    }
}
