// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
package net.ankiweb.rsdroid

import androidx.test.ext.junit.runners.AndroidJUnit4
import net.ankiweb.rsdroid.ankiutil.InstrumentedTest
import org.hamcrest.CoreMatchers
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackendTranslationsTest : InstrumentedTest() {
    private fun withoutIsolation(s: String): String = s.replace("\u2068", "").replace("\u2069", "")

    @Test
    fun ensureI18nWorks() {
        var b = memoryBackend
        assertThat(
            withoutIsolation(b.tr.mediaCheckTrashCount(5, 10)),
            CoreMatchers.equalTo("Trash folder: 5 files, 10MB"),
        )
        assertThat(
            withoutIsolation(b.tr.mediaCheckTrashCount(5, 10.0)),
            CoreMatchers.equalTo("Trash folder: 5 files, 10MB"),
        )
        assertThat(
            withoutIsolation(b.tr.mediaCheckTrashCount(5, "foo")),
            CoreMatchers.equalTo("Trash folder: 5 files, fooMB"),
        )
        b = BackendFactory.getBackend(mutableListOf("fr"))
        assertThat(
            withoutIsolation(b.tr.mediaCheckTrashCount(5, 10)),
            CoreMatchers.equalTo("Corbeille : 5 fichiers, 10 Mo"),
        )
    }
}
