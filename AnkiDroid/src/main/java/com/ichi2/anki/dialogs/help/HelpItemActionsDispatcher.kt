/*
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
package com.ichi2.anki.dialogs.help

import androidx.annotation.StringRes
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.R
import com.ichi2.anki.common.android.AdaptionUtil
import com.ichi2.anki.common.crashreporting.CrashReportService
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.utils.IntentUtil

/**
 * An abstraction for the actions that can be done when the user selects a menu item in the
 * Help/Support menus. Used for testing.
 */
interface HelpItemActionsDispatcher {
    fun onOpenUrl(url: String)

    fun onOpenUrlResource(
        @StringRes url: Int,
    )

    fun onRate()

    fun onSendReport()
}

class AnkiActivityHelpActionsDispatcher(
    private val ankiActivity: AnkiActivity,
) : HelpItemActionsDispatcher {
    override fun onOpenUrl(url: String) {
        ankiActivity.openUrl(url)
    }

    override fun onOpenUrlResource(
        @StringRes url: Int,
    ) {
        ankiActivity.openUrl(url)
    }

    override fun onRate() {
        IntentUtil.tryOpenIntent(
            ankiActivity,
            AnkiDroidApp.getMarketIntent(ankiActivity),
        )
    }

    override fun onSendReport() {
        if (AdaptionUtil.isUserATestClient) {
            ankiActivity.showSnackbar(ankiActivity.getString(R.string.user_is_a_robot))
        } else {
            val wasReportSent = CrashReportService.sendReport(ankiActivity)
            if (!wasReportSent) {
                ankiActivity.showSnackbar(ankiActivity.getString(R.string.help_dialog_exception_report_debounce))
                return
            }
            ankiActivity.showSnackbar(ankiActivity.getString(R.string.help_dialog_exception_report_sent))
        }
    }
}
