/*
 * Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>
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
package com.ichi2.anki.tags

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.dialogs.utils.AnKingTags
import com.ichi2.testutils.ensureOpsExecuted
import kotlinx.coroutines.flow.first
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsInAnyOrder
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasItem
import org.hamcrest.Matchers.hasSize
import org.hamcrest.Matchers.not
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.system.measureTimeMillis
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Integration tests for [ManageTagsViewModel] with a real collection */
@RunWith(AndroidJUnit4::class)
class ManageTagsViewModelTest : RobolectricTest() {
    @Test
    fun `refreshTags loads empty collection`() =
        runTest {
            withViewModel {
                assertIs<ManageTagsState.Content>(state.value)
                assertThat(loadedState.visibleNodes, hasSize(0))
            }
        }

    @Test
    fun `refreshTags loads tags from notes`() =
        runTest {
            addTags("science")
            withViewModel {
                assertThat(loadedState.visibleTagNames, equalTo(listOf("science")))
            }
        }

    @Test
    fun `refreshTags loads hierarchical tags`() =
        runTest {
            addTags("science::biology", "science::chemistry")
            withViewModel {
                // "science" parent is collapsed by default, so only it should be visible
                assertThat(loadedState.visibleNodes, hasSize(1))
                assertThat(loadedState.visibleNodes[0].fullTagName, equalTo("science"))
                assertThat(loadedState.visibleNodes[0].hasChildren, equalTo(true))
            }
        }

    @Test
    fun `refreshTags loads multiple tags from multiple notes`() =
        runTest {
            addTags("math")
            addTags("history")
            withViewModel {
                assertThat(loadedState.visibleNodes, hasSize(2))
                assertThat(
                    loadedState.visibleTagNames,
                    containsInAnyOrder("history", "math"),
                )
            }
        }

    @Test
    fun `refreshTags preserves search query`() =
        runTest {
            addTags("science", "history")
            withViewModel {
                filter("sci")
                refreshTags()
                assertThat(loadedState.searchQuery, equalTo("sci"))
                assertThat(loadedState.visibleTagNames, equalTo(listOf("science")))
            }
        }

    @Test
    fun `initial state is Loaded after construction`() =
        runTest {
            withViewModel {
                assertIs<ManageTagsState.Content>(state.value)
            }
        }

    @Test
    fun `filter narrows visible tags`() =
        runTest {
            addTags("science", "history", "math")
            withViewModel {
                filter("sci")
                assertThat(loadedState.visibleTagNames, equalTo(listOf("science")))
                assertThat(loadedState.searchQuery, equalTo("sci"))
            }
        }

    @Test
    fun `filter with empty query shows all tags`() =
        runTest {
            addTags("science", "history")
            withViewModel {
                filter("sci")
                assertThat(loadedState.visibleNodes, hasSize(1))

                filter("")
                assertThat(loadedState.visibleNodes, hasSize(2))
            }
        }

    @Test
    fun `filter shows ancestors of matching nested tags`() =
        runTest {
            addTags("science::biology", "history")
            withViewModel {
                filter("bio")
                // "science" (ancestor) + "science::biology" (match)
                assertThat(loadedState.visibleNodes, hasSize(2))
                assertThat(loadedState.visibleNodes[0].fullTagName, equalTo("science"))
                assertThat(loadedState.visibleNodes[1].fullTagName, equalTo("science::biology"))
            }
        }

    @Test
    fun `filter with no matches returns empty`() =
        runTest {
            addTags("science")
            withViewModel {
                filter("zzz")
                assertThat(loadedState.visibleNodes, hasSize(0))
            }
        }

    @Test
    fun `toggleCollapsed expands a collapsed tag`() =
        runTest {
            addTags("science::biology", "science::chemistry")
            withViewModel {
                assertThat(loadedState.visibleNodes, hasSize(1))

                toggleCollapsed("science")

                assertThat(loadedState.visibleNodes, hasSize(3))
                assertThat(loadedState.visibleNodes[0].fullTagName, equalTo("science"))
                assertThat(loadedState.visibleNodes[0].collapsed, equalTo(false))
                assertThat(loadedState.visibleNodes[1].fullTagName, equalTo("science::biology"))
                assertThat(loadedState.visibleNodes[2].fullTagName, equalTo("science::chemistry"))
            }
        }

    @Test
    fun `toggleCollapsed collapses an expanded tag`() =
        runTest {
            addTags("science::biology")
            withViewModel {
                toggleCollapsed("science")
                assertThat(loadedState.visibleNodes, hasSize(2))

                toggleCollapsed("science")
                assertThat(loadedState.visibleTagNames, equalTo(listOf("science")))
                assertThat(loadedState.visibleNodes[0].collapsed, equalTo(true))
            }
        }

    @Test
    fun `toggleCollapsed on unknown tag sends UnexpectedError`() =
        runTest {
            addTags("science")
            withViewModel {
                toggleCollapsed("nonexistent")
                val event = events.first()
                assertIs<UserMessage.UnexpectedError>(event.message)
            }
        }

    @Test
    fun `removeTag removes a single tag`() =
        runTest {
            addTags("science", "history")
            withViewModel {
                removeTag("science")
            }
            checkCollectionTags { tags ->
                assertThat(tags, not(hasItem("science")))
                assertThat(tags, hasItem("history"))
            }
        }

    @Test
    fun `removeTag removes tag and children`() =
        runTest {
            addTags("science::biology", "science::chemistry", "history")
            withViewModel {
                removeTag("science")
            }
            checkCollectionTags { tags ->
                assertThat(tags, not(hasItem("science::biology")))
                assertThat(tags, not(hasItem("science::chemistry")))
                assertThat(tags, hasItem("history"))
            }
        }

    @Test
    fun `renameTag updates tag name`() =
        runTest {
            addTags("science")
            withViewModel {
                renameTag("science", "physics")
            }
            checkCollectionTags { tags ->
                assertThat(tags, hasItem("physics"))
                assertThat(tags, not(hasItem("science")))
            }
        }

    @Test
    fun `renameTag updates hierarchical tags`() =
        runTest {
            addTags("science::biology")
            withViewModel {
                renameTag("science", "studies")
            }
            checkCollectionTags { tags ->
                assertThat(tags, hasItem("studies::biology"))
                assertThat(tags, not(hasItem("science::biology")))
            }
        }

    @Test
    fun `clearUnusedTags removes tags not on any note`() =
        runTest {
            addTags("used")
            addUnusedTag("unused")
            withViewModel {
                assertThat(loadedState.visibleTagNames, hasItem("unused"))

                clearUnusedTags()

                assertThat(loadedState.visibleTagNames, equalTo(listOf("used")))
            }
        }

    @Test
    fun `clearUnusedTags sends event with count`() =
        runTest {
            addTags("used")
            addUnusedTag("unused")
            withViewModel {
                clearUnusedTags()
                val event = events.first()
                val message = assertIs<UserMessage.ClearedUnusedTags>(event.message)
                assertThat(message.count, equalTo(1))
            }
        }

    @Test
    fun `removeTag sends event with affected note count`() =
        runTest {
            // add 'science to 2 notes - ensure that the return value is the notes affected
            addTags("science")
            addTags("science")
            withViewModel {
                removeTag("science")
                val event = events.first()
                val message = assertIs<UserMessage.TagRemoved>(event.message)
                assertThat(message.notesAffected, equalTo(2))
            }
        }

    @Test
    fun `renameTag sends event with affected note count`() =
        runTest {
            // add 'science to 2 notes - ensure that the return value is the notes affected
            addTags("science")
            addTags("science")
            withViewModel {
                renameTag("science", "physics")
                val event = events.first()
                val message = assertIs<UserMessage.TagRenamed>(event.message)
                assertThat(message.notesAffected, equalTo(2))
            }
        }

    @Test
    fun `removeTag fires opChanges`() =
        runTest {
            addTags("science")
            ensureOpsExecuted(1) {
                withViewModel { removeTag("science") }
            }
        }

    @Test
    fun `renameTag fires opChanges`() =
        runTest {
            addTags("science")
            ensureOpsExecuted(1) {
                withViewModel { renameTag("science", "physics") }
            }
        }

    @Test
    fun `clearUnusedTags fires opChanges`() =
        runTest {
            addTags("used")
            addUnusedTag("unused")
            ensureOpsExecuted(1) {
                withViewModel { clearUnusedTags() }
            }
        }

    @Test
    @MediumTest
    fun `toggleCollapsed performance with AnKing tags`() =
        runTest {
            val expected = 1.seconds

            fun ManageTagsViewModel.toggleOnOff(tag: String) {
                toggleCollapsed(tag)
                toggleCollapsed(tag)
            }

            val tags = setupAnKing()

            withViewModel {
                val hugeTag = "#AK_Step1_v11" // 10k+ child tags

                // warm up
                toggleOnOff(hugeTag)

                val iterations = 1
                val elapsed =
                    measureTimeMillis {
                        repeat(iterations) {
                            toggleOnOff(hugeTag)
                        }
                    }

                // ~55ms on my M1
                // ~342ms on a Ubuntu CI runner
                val avgMs = (elapsed.toDouble() / (iterations * 2)).milliseconds
                println("toggleCollapsed: ${tags.size} tags, avg ${avgMs}ms over ${iterations * 2} toggles")
                assertTrue(avgMs < expected, "toggleCollapsed took ${avgMs}ms on average, expected < $expected")
            }
        }

    private suspend fun withViewModel(block: suspend ManageTagsViewModel.() -> Unit) = ManageTagsViewModel().block()

    /** Helper abstracting [com.ichi2.anki.libanki.Tags.all] */
    private fun checkCollectionTags(block: (List<String>) -> Unit) = block(col.tags.all())

    /** Adds 17k tags to a note */
    private fun setupAnKing(): List<String> =
        AnKingTags.value.also { tags ->
            addBasicNote().update { tags.forEach { addTag(it) } }
        }

    private fun addTags(vararg tags: String) = addBasicNote().update { tags.forEach { addTag(it) } }

    /** Adds a tag to the collection cache without it being on any note */
    private fun addUnusedTag(
        @Suppress("SameParameterValue") tag: String,
    ) {
        addTags(tag).update { removeTag(tag) }
    }
}

private val ManageTagsViewModel.loadedState: ManageTagsState.Content
    get() = state.value as ManageTagsState.Content
