/*
 *  Copyright (c) 2024 voczi <dev@voczi.com>
 *  Copyright (c) 2024 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.pages

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.testutils.HamcrestUtils.containsInAnyOrder
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.empty
import org.hamcrest.Matchers.not
import org.junit.Test
import org.junit.runner.RunWith
import java.io.InputStreamReader

@RunWith(AndroidJUnit4::class)
class PostRequestHandlerTest : RobolectricTest() {
    @Test
    fun `All backend typescript functions should be handled`() {
        assertThat(
            "Mapping exists for every TS backend function call",
            typescriptFunctionsUsedByBackend,
            // this matcher asserts equality in everything but order, no extras, nothing missing
            containsInAnyOrder((collectionMethods + uiMethods).keys),
        )
    }

    /**
     * Auto-generated list of all typescript funcs created & packaged during backend build
     */
    private val typescriptFunctionsUsedByBackend: List<String> =
        InputStreamReader(targetContext.assets.open("backend/ts_funcs.txt")).use {
            it.readLines().also { lines ->
                assertThat("Stored Typescript functions", lines, not(empty()))
            }
        }
}
