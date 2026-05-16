/*
 * Copyright (c) 2020 lukstbit <52494258+lukstbit@users.noreply.github.com>
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
@file:Suppress("UnstableApiUsage")

package com.ichi2.anki.lint

import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.client.api.Vendor
import com.android.tools.lint.detector.api.CURRENT_API
import com.android.tools.lint.detector.api.Issue
import com.ichi2.anki.lint.rules.AvoidAlertDialogUsage
import com.ichi2.anki.lint.rules.DirectCalendarInstanceUsage
import com.ichi2.anki.lint.rules.DirectDateInstantiation
import com.ichi2.anki.lint.rules.DirectGregorianInstantiation
import com.ichi2.anki.lint.rules.DirectSnackbarMakeUsage
import com.ichi2.anki.lint.rules.DirectSystemCurrentTimeMillisUsage
import com.ichi2.anki.lint.rules.DirectSystemTimeInstantiation
import com.ichi2.anki.lint.rules.DirectToastMakeTextUsage
import com.ichi2.anki.lint.rules.DuplicateCrowdInStrings
import com.ichi2.anki.lint.rules.FixedPreferencesTitleLength
import com.ichi2.anki.lint.rules.HardcodedPreferenceKey
import com.ichi2.anki.lint.rules.InvalidStringFormatDetector
import com.ichi2.anki.lint.rules.JUnitNullAssertionDetector
import com.ichi2.anki.lint.rules.LayoutPrefixDetector
import com.ichi2.anki.lint.rules.LicenseHeaderExists
import com.ichi2.anki.lint.rules.LocaleRootDetector
import com.ichi2.anki.lint.rules.NonPositionalFormatSubstitutions
import com.ichi2.anki.lint.rules.OpenInputStreamSafeDetector
import com.ichi2.anki.lint.rules.PrintStackTraceUsage
import com.ichi2.anki.lint.rules.SentenceCaseConventions
import com.ichi2.anki.lint.rules.TranslationTypo
import com.ichi2.anki.lint.rules.VariableNamingDetector

class IssueRegistry : IssueRegistry() {
    // Keep this list lexicographically ordered.
    override val issues: List<Issue>
        get() {
            // Keep this list lexicographically ordered.
            return listOf(
                LayoutPrefixDetector.ISSUE,
                DirectCalendarInstanceUsage.ISSUE,
                DirectDateInstantiation.ISSUE,
                DirectGregorianInstantiation.ISSUE,
                DirectSnackbarMakeUsage.ISSUE,
                DirectSystemCurrentTimeMillisUsage.ISSUE,
                DirectSystemTimeInstantiation.ISSUE,
                DirectToastMakeTextUsage.ISSUE,
                DuplicateCrowdInStrings.ISSUE,
                HardcodedPreferenceKey.ISSUE,
                JUnitNullAssertionDetector.ISSUE,
                LicenseHeaderExists.ISSUE,
                LocaleRootDetector.ISSUE,
                PrintStackTraceUsage.ISSUE,
                NonPositionalFormatSubstitutions.ISSUE,
                OpenInputStreamSafeDetector.ISSUE,
                SentenceCaseConventions.ISSUE,
                TranslationTypo.ISSUE,
                FixedPreferencesTitleLength.PREFERENCES_ISSUE_MAX_LENGTH,
                FixedPreferencesTitleLength.MENU_ISSUE_MAX_LENGTH,
                FixedPreferencesTitleLength.PREFERENCES_ISSUE_TITLE_LENGTH,
                FixedPreferencesTitleLength.MENU_ISSUE_TITLE_LENGTH,
                VariableNamingDetector.ISSUE,
                InvalidStringFormatDetector.ISSUE,
                AvoidAlertDialogUsage.ISSUE,
            )
        }
    override val api: Int
        get() = CURRENT_API
    override val vendor: Vendor
        get() =
            Vendor(
                "AnkiDroid",
                "com.ichi2.anki:lint-rules",
                "https://github.com/ankidroid/Anki-Android/issues",
                "https://github.com/ankidroid/Anki-Android",
            )
}
